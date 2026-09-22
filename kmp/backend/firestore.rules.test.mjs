import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { after, before, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs, serverTimestamp, writeBatch, runTransaction } from 'firebase/firestore';

// Only the demo emulator project is used. No production account or credentials required.
let env;
const farm = (revision = 1) => ({ payload: '{"example":"personal save"}', revision, updatedAt: serverTimestamp() });
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-little-farm', firestore: {
    host: '127.0.0.1', port: 8087,
    rules: await readFile(new URL('./firestore.rules', import.meta.url), 'utf8')
  } });
});
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env?.cleanup(); });

test('guest cannot read or create a cloud farm', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, 'players/alice')));
  await assertFails(setDoc(doc(db, 'players/alice'), farm()));
});
test('owner can create/read/update but cannot list farms', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(doc(db, 'players/alice'), farm()));
  await assertSucceeds(getDoc(doc(db, 'players/alice')));
  await assertSucceeds(setDoc(doc(db, 'players/alice'), farm(2)));
  await assertFails(getDocs(collection(db, 'players')));
});
test('one player cannot read, write or delete another player', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  await assertSucceeds(setDoc(doc(alice, 'players/alice'), farm()));
  await assertFails(getDoc(doc(bob, 'players/alice')));
  await assertFails(setDoc(doc(bob, 'players/alice'), farm(2)));
  await assertFails(deleteDoc(doc(bob, 'players/alice')));
  await assertFails(setDoc(doc(bob, 'accountDeletions/alice'), { requestedAt: serverTimestamp() }));
});
test('revision must start at one and advance exactly once', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const ref = doc(db, 'players/alice');
  await assertFails(setDoc(ref, farm(9)));
  await assertSucceeds(setDoc(ref, farm()));
  await assertFails(setDoc(ref, farm()));
  await assertFails(setDoc(ref, farm(3)));
  await assertSucceeds(setDoc(ref, farm(2)));
});
test('unexpected fields, oversized payload and client timestamps fail', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const ref = doc(db, 'players/alice');
  await assertFails(setDoc(ref, { ...farm(), premiumGems: 999 }));
  await assertFails(setDoc(ref, { ...farm(), payload: 'x'.repeat(200001) }));
  await assertFails(setDoc(ref, { ...farm(), payload: 'ก'.repeat(66667) })); // 200001 UTF-8 bytes.
  await assertFails(setDoc(ref, { ...farm(), updatedAt: 12345 }));
  await assertFails(setDoc(ref, { ...farm(), revision: '1' }));
});
test('deletion is atomic and old devices cannot resurrect or read a deleted farm', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const ref = doc(db, 'players/alice');
  const marker = doc(db, 'accountDeletions/alice');
  await assertSucceeds(setDoc(ref, farm()));
  await assertFails(deleteDoc(ref));
  const batch = writeBatch(db);
  batch.set(marker, { requestedAt: serverTimestamp() }); batch.delete(ref);
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(marker));
  await assertFails(setDoc(ref, farm()));
  await assertFails(getDoc(ref));
  await assertFails(deleteDoc(marker));
  await assertFails(setDoc(marker, { requestedAt: serverTimestamp() }));
  await assertSucceeds(deleteDoc(ref)); // Retry after partial Auth deletion is allowed.
});
test('cannot create a save and deletion marker together', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const batch = writeBatch(db);
  batch.set(doc(db, 'accountDeletions/alice'), { requestedAt: serverTimestamp() });
  batch.set(doc(db, 'players/alice'), farm());
  await assertFails(batch.commit());
});

test('concurrent transactions with the same expected revision produce one save and one conflict', { timeout: 30000 }, async () => {
  const firstDevice = env.authenticatedContext('alice').firestore();
  const secondDevice = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(doc(firstDevice, 'players/alice'), farm()));
  const expectedRevision = 1;
  let initialReaders = 0;
  let releaseInitialReads;
  const bothInitialReads = new Promise(resolve => { releaseInitialReads = resolve; });

  const commitFromDevice = (db, device) => {
    let firstAttempt = true;
    return runTransaction(db, async transaction => {
      const marker = await transaction.get(doc(db, 'accountDeletions/alice'));
      assert.equal(marker.exists(), false);
      const ref = doc(db, 'players/alice');
      const snapshot = await transaction.get(ref);
      const revision = snapshot.exists() ? snapshot.data().revision : 0;
      // Force both first attempts to read revision 1 before either attempts its write.
      // Do not wait again when Firestore reruns a losing transaction's callback.
      if (firstAttempt) {
        firstAttempt = false;
        initialReaders += 1;
        if (initialReaders === 2) releaseInitialReads();
        await bothInitialReads;
      }
      // Keep the caller's expected revision fixed and recheck it on EVERY invocation.
      if (revision !== expectedRevision) {
        throw Object.assign(new Error('Cloud save changed'), { code: 'conflict' });
      }
      transaction.set(ref, { ...farm(expectedRevision + 1), payload: JSON.stringify({ device }) });
      return device;
    });
  };

  const results = await Promise.allSettled([
    commitFromDevice(firstDevice, 'first'),
    commitFromDevice(secondDevice, 'second'),
  ]);
  const successes = results.filter(result => result.status === 'fulfilled');
  const failures = results.filter(result => result.status === 'rejected');
  assert.equal(successes.length, 1);
  assert.equal(failures.length, 1);
  assert.equal(failures[0].reason.code, 'conflict');
  const stored = (await getDoc(doc(firstDevice, 'players/alice'))).data();
  assert.equal(stored.revision, 2);
  assert.equal(stored.payload, JSON.stringify({ device: successes[0].value }));
});

test('native-style deletion transaction can retry without reading the tombstoned player', async () => {
  const db = env.authenticatedContext('alice').firestore();
  const ref = doc(db, 'players/alice');
  const marker = doc(db, 'accountDeletions/alice');
  await assertSucceeds(setDoc(ref, farm()));
  let playerReads = 0;
  const deleteFarm = () => runTransaction(db, async transaction => {
    const deletion = await transaction.get(marker);
    if (!deletion.exists()) {
      // iOS reads the player only on the first deletion; Android can omit this read entirely.
      await transaction.get(ref);
      playerReads += 1;
      transaction.set(marker, { requestedAt: serverTimestamp() });
    }
    transaction.delete(ref);
  });

  await assertSucceeds(deleteFarm());
  const readsBeforeRetry = playerReads;
  assert.equal(readsBeforeRetry, 1);
  await assertFails(getDoc(ref)); // A retry that read this document would be denied.
  await assertSucceeds(deleteFarm());
  await assertSucceeds(deleteFarm());
  assert.equal(playerReads, readsBeforeRetry);
  assert.equal((await getDoc(marker)).exists(), true);
  await env.withSecurityRulesDisabled(async context => {
    assert.equal((await getDoc(doc(context.firestore(), 'players/alice'))).exists(), false);
  });
});
