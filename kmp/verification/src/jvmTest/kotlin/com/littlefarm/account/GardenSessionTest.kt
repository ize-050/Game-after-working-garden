package com.littlefarm.account

import com.littlefarm.game.GameSaveCodec
import com.littlefarm.game.GameState
import com.littlefarm.platform.SaveStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Fakes exercise ownership/sync policy, not real OAuth, Firestore rules or native SDK integration. */
class GardenSessionTest {
    private val initial = GameState.initial(1_000_000)
    private class Guest(var raw: String? = null) : SaveStore {
        var fail = false
        override fun read() = raw
        override fun write(value: String) { check(!fail); raw = value }
    }
    private class Disk : AccountKeyValueStore {
        val data = mutableMapOf<String, String>()
        var fail = false
        var failRemove = false
        override fun read(key: String) = data[key]
        override fun write(key: String, value: String) { check(!fail); data[key] = value }
        override fun remove(key: String) { check(!failRemove); data.remove(key) }
    }
    private class Cloud : AccountPlatform {
        data class Save(val payload: String, val revision: Int)
        val saves = mutableMapOf<String, Save>()
        var user: String? = null
        var nextUser = "alice"
        var failOperation: String? = null
        var errorCode = "network"
        var beforeCommit: (() -> Unit)? = null
        var hold: String? = null
        var held: (() -> Unit)? = null
        var writes = 0
        val requests = mutableListOf<Pair<String, String?>>()
        val deleting = mutableSetOf<String>()
        override fun availability() = """{"providers":["google","apple"],"message":null}"""
        private fun userJson(): JsonElement = user?.let { buildJsonObject {
            put("uid", it); put("displayName", "ชาวสวน $it"); put("provider", "google")
        } } ?: JsonNull
        private fun saveJson(save: Save?): JsonElement = save?.let { buildJsonObject {
            put("payload", it.payload); put("revision", it.revision)
        } } ?: JsonNull
        override fun request(operation: String, payload: String, completion: (String) -> Unit) {
            val p = Json.parseToJsonElement(payload).jsonObject
            val uid = p["uid"]?.jsonPrimitive?.content
            requests += operation to uid
            fun fail(code: String) = completion(buildJsonObject { put("ok", false); put("code", code) }.toString())
            fun perform() {
                if (operation == failOperation) { fail(errorCode); return }
                if (operation !in setOf("restore", "signIn") && (uid == null || uid != user)) { fail("unauthenticated"); return }
                val result = buildJsonObject {
                    put("ok", true)
                    when (operation) {
                        "restore" -> put("user", userJson())
                        "signIn" -> { user = nextUser; put("user", userJson()) }
                        "signOut" -> { user = null }
                        "loadSave" -> {
                            if (uid in deleting) { fail("deletion_incomplete"); return }
                            put("save", saveJson(saves[uid]))
                        }
                        "commitSave" -> {
                            beforeCommit?.also { beforeCommit = null; it() }
                            if (uid in deleting) { fail("deletion_incomplete"); return }
                            val expected = p.getValue("expectedRevision").jsonPrimitive.int
                            if ((saves[uid]?.revision ?: 0) != expected) { fail("conflict"); return }
                            val save = Save(p.getValue("payload").jsonPrimitive.content, expected + 1)
                            saves[uid!!] = save; writes++
                            put("save", saveJson(save))
                        }
                        "deleteAccount" -> { deleting += uid!!; saves.remove(uid); user = null }
                        else -> error(operation)
                    }
                }
                completion(result.toString())
            }
            if (hold == operation) held = { hold = null; perform() } else perform()
        }
    }
    private fun session(guest: Guest = Guest(), disk: Disk = Disk(), cloud: AccountPlatform = Cloud()) =
        GardenSession(guest, disk, cloud) { 1_000_000L }
    private fun encoded(coins: Int) = GameSaveCodec.encode(initial.copy(coins = coins))

    @Test fun guestOnlyDoesNotPretendToSignInOrUpload() = runBlocking {
        val guest = Guest(encoded(131))
        val s = session(guest, cloud = UnavailableAccountPlatform())
        s.restoreAccount(); s.signIn("google"); s.sync()
        assertNull(s.snapshot.value.account)
        assertEquals(SyncStatus.UNCONFIGURED, s.snapshot.value.status)
        assertEquals(131, s.snapshot.value.game.coins)
        assertEquals(encoded(131), guest.raw)
    }

    @Test fun firstLoginImportsGuestWithoutDeletingGuest() = runBlocking {
        val guest = Guest(encoded(131)); val cloud = Cloud(); val disk = Disk()
        val s = session(guest, disk, cloud)
        s.signIn("google")
        assertEquals("alice", s.snapshot.value.account?.uid)
        assertEquals(SyncStatus.SYNCED, s.snapshot.value.status)
        assertEquals(encoded(131), cloud.saves["alice"]?.payload)
        assertEquals(encoded(131), guest.raw)
        assertEquals(1, cloud.writes)
        assertTrue(disk.data.keys.contains("account_save_v1_alice"))
    }

    @Test fun existingCloudAndGuestRequireChoice() = runBlocking {
        val cloud = Cloud().apply { saves["alice"] = Cloud.Save(encoded(900), 5) }
        val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google")
        assertEquals(SyncStatus.CONFLICT, s.snapshot.value.status)
        assertEquals(131, s.snapshot.value.conflict?.local?.coins)
        assertEquals(900, s.snapshot.value.conflict?.cloud?.coins)
        assertFalse(s.save(initial.copy(coins = 999)))
        assertEquals(0, cloud.writes)
        s.chooseCloud()
        assertEquals(900, s.snapshot.value.game.coins)
        assertEquals(SyncStatus.SYNCED, s.snapshot.value.status)
    }

    @Test fun newInstallLoadsExistingCloudWithoutReplacingItWithInitialFarm() = runBlocking {
        val cloud = Cloud().apply { saves["alice"] = Cloud.Save(encoded(900), 5) }
        val s = session(cloud = cloud)
        s.signIn("google")
        assertEquals(900, s.snapshot.value.game.coins)
        assertNull(s.snapshot.value.conflict)
        assertEquals(0, cloud.writes)
    }

    @Test fun logoutAndAnotherLoginDoNotMixOwners() = runBlocking {
        val cloud = Cloud(); val guest = Guest(encoded(131)); val disk = Disk()
        val s = session(guest, disk, cloud)
        s.signIn("google")
        s.save(initial.copy(coins = 200))
        s.signOut()
        assertEquals(131, s.snapshot.value.game.coins)
        cloud.nextUser = "bob"
        s.signIn("google")
        assertEquals(131, s.snapshot.value.game.coins)
        assertEquals(encoded(131), cloud.saves["bob"]?.payload)
        s.signOut(); cloud.nextUser = "alice"; s.signIn("google")
        assertEquals(200, s.snapshot.value.game.coins)
        assertEquals(encoded(200), cloud.saves["alice"]?.payload)
        assertEquals(encoded(131), guest.raw)
    }

    @Test fun offlineProgressPersistsPerAccountAndRetriesAfterRestart() = runBlocking {
        val disk = Disk(); val cloud = Cloud(); val guest = Guest(encoded(131))
        val s = session(guest, disk, cloud)
        s.signIn("google"); s.save(initial.copy(coins = 300))
        cloud.failOperation = "loadSave"
        s.sync()
        assertEquals(SyncStatus.ERROR, s.snapshot.value.status)
        assertEquals(encoded(131), cloud.saves["alice"]?.payload)
        cloud.failOperation = null
        val reopened = session(guest, disk, cloud)
        reopened.restoreAccount()
        assertEquals(300, reopened.snapshot.value.game.coins)
        assertEquals(encoded(300), cloud.saves["alice"]?.payload)
    }

    @Test fun twoDeviceEditsConflictInsteadOfLastWriterWins() = runBlocking {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google"); s.save(initial.copy(coins = 200))
        cloud.saves["alice"] = Cloud.Save(encoded(500), 2)
        s.sync()
        assertEquals(SyncStatus.CONFLICT, s.snapshot.value.status)
        assertEquals(encoded(500), cloud.saves["alice"]?.payload)
        s.chooseLocal()
        assertEquals(encoded(200), cloud.saves["alice"]?.payload)
        assertEquals(3, cloud.saves["alice"]?.revision)
    }

    @Test fun choosingCloudRefusesToSilentlyAcceptChangedPreview() = runBlocking {
        val cloud = Cloud().apply { saves["alice"] = Cloud.Save(encoded(500), 2) }
        val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google")
        cloud.saves["alice"] = Cloud.Save(encoded(700), 3)
        s.chooseCloud()
        assertEquals(131, s.snapshot.value.game.coins)
        assertEquals(700, s.snapshot.value.conflict?.cloud?.coins)
        s.chooseCloud()
        assertEquals(700, s.snapshot.value.game.coins)
    }

    @Test fun compareAndSetDetectsRaceAfterReadingCloud() = runBlocking {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google"); s.save(initial.copy(coins = 200))
        cloud.beforeCommit = { cloud.saves["alice"] = Cloud.Save(encoded(700), 2) }
        s.sync()
        assertEquals(SyncStatus.CONFLICT, s.snapshot.value.status)
        assertEquals(700, s.snapshot.value.conflict?.cloud?.coins)
        assertEquals(encoded(700), cloud.saves["alice"]?.payload)
    }

    @Test fun corruptCloudIsNeverLoadedOrOverwritten() = runBlocking {
        val cloud = Cloud().apply { saves["alice"] = Cloud.Save("not json", 1) }
        val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google")
        assertEquals(131, s.snapshot.value.game.coins)
        assertEquals(SyncStatus.ERROR, s.snapshot.value.status)
        assertEquals("not json", cloud.saves["alice"]?.payload)
        assertEquals(0, cloud.writes)
    }

    @Test fun providerCancellationLeavesGuestUntouched() = runBlocking {
        val cloud = Cloud().apply { failOperation = "signIn"; errorCode = "cancelled" }
        val guest = Guest(encoded(131)); val s = session(guest, cloud = cloud)
        s.signIn("google")
        assertNull(s.snapshot.value.account)
        assertEquals(encoded(131), guest.raw)
        assertEquals(0, cloud.writes)
    }

    @Test fun busyAccountOperationRejectsGameWritesAndDuplicateCalls() = runBlocking {
        val cloud = Cloud().apply { hold = "signIn" }; val s = session(cloud = cloud)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.signIn("google") }
        assertTrue(s.snapshot.value.busy)
        assertFalse(s.save(initial.copy(coins = 500)))
        s.signIn("apple")
        assertEquals(1, cloud.requests.count { it.first == "signIn" })
        cloud.held!!.invoke(); job.join()
        assertFalse(s.snapshot.value.busy)
    }

    @Test fun deletionSuccessRemovesAccountCacheButRetainsGuest() = runBlocking {
        val cloud = Cloud(); val disk = Disk(); val guest = Guest(encoded(131))
        val s = session(guest, disk, cloud)
        s.signIn("google"); s.save(initial.copy(coins = 500)); s.sync(); s.deleteAccount()
        assertNull(s.snapshot.value.account)
        assertEquals(131, s.snapshot.value.game.coins)
        assertEquals(encoded(131), guest.raw)
        assertFalse(disk.data.containsKey("account_save_v1_alice"))
        assertNull(cloud.saves["alice"])
        assertTrue("alice" in cloud.deleting)
    }

    @Test fun partialDeletionLocksWritesAcrossRestartUntilRetry() = runBlocking {
        val cloud = Cloud(); val disk = Disk(); val guest = Guest(encoded(131))
        val s = session(guest, disk, cloud)
        s.signIn("google")
        cloud.failOperation = "deleteAccount"; cloud.errorCode = "deletion_incomplete"
        s.deleteAccount()
        assertTrue(s.snapshot.value.deletionPending)
        assertFalse(s.save(initial.copy(coins = 800)))
        val writes = cloud.writes; s.sync(); assertEquals(writes, cloud.writes)
        val reopened = session(guest, disk, cloud); reopened.restoreAccount()
        assertTrue(reopened.snapshot.value.deletionPending)
        cloud.failOperation = null; reopened.deleteAccount()
        assertNull(reopened.snapshot.value.account)
    }

    @Test fun cancelledDeleteBeforeDestructiveWorkUnlocksPlay() = runBlocking<Unit> {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google")
        cloud.failOperation = "deleteAccount"; cloud.errorCode = "cancelled"
        s.deleteAccount()
        assertFalse(s.snapshot.value.deletionPending)
        assertTrue(s.save(initial.copy(coins = 500)))
        assertNotNull(cloud.saves["alice"])
    }

    @Test fun tombstoneOnAnotherDevicePreventsResurrectingFarm() = runBlocking {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google"); s.save(initial.copy(coins = 500))
        cloud.deleting += "alice"; cloud.saves.remove("alice")
        s.sync()
        assertTrue(s.snapshot.value.deletionPending)
        assertNull(cloud.saves["alice"])
        assertFalse(s.save(initial.copy(coins = 999)))
    }

    @Test fun localFailureRetainsMemoryForRetryAndBlocksLogout() = runBlocking {
        val disk = Disk(); val cloud = Cloud(); val s = session(Guest(encoded(131)), disk, cloud)
        s.signIn("google"); disk.fail = true
        assertFalse(s.save(initial.copy(coins = 500)))
        assertTrue(s.snapshot.value.saveFailed)
        assertEquals(500, s.snapshot.value.game.coins)
        s.signOut(); assertNotNull(s.snapshot.value.account)
        disk.fail = false
        assertTrue(s.save(s.snapshot.value.game))
        s.sync()
        assertEquals(encoded(500), cloud.saves["alice"]?.payload)
    }

    @Test fun missingPreviouslyExistingCloudIsNotAutomaticallyRecreated() = runBlocking {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google"); cloud.saves.clear()
        s.save(initial.copy(coins = 500)); s.sync()
        assertEquals(SyncStatus.ERROR, s.snapshot.value.status)
        assertNull(cloud.saves["alice"])
    }

    @Test fun sdkUserSwitchCannotUploadUnderPreviousOwner() = runBlocking {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google"); s.save(initial.copy(coins = 500))
        cloud.user = "bob"; s.sync()
        assertEquals(SyncStatus.ERROR, s.snapshot.value.status)
        assertEquals(encoded(131), cloud.saves["alice"]?.payload)
        assertNull(cloud.saves["bob"])
    }

    @Test fun restoreWithoutLocalAccountCacheDoesNotImportUnrelatedGuest() = runBlocking {
        val cloud = Cloud().apply { user = "alice"; saves["alice"] = Cloud.Save(encoded(900), 5) }
        val guest = Guest(encoded(131)); val s = session(guest, cloud = cloud)
        s.restoreAccount()
        assertEquals(900, s.snapshot.value.game.coins)
        assertNull(s.snapshot.value.conflict)
        assertEquals(encoded(131), guest.raw)
    }

    @Test fun cancellationDoesNotLeavePhantomSyncOrLoseACommit() = runBlocking {
        val cloud = Cloud(); val s = session(Guest(encoded(131)), cloud = cloud)
        s.signIn("google"); s.save(initial.copy(coins = 500)); cloud.hold = "commitSave"
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.sync() }
        assertEquals(SyncStatus.SYNCING, s.snapshot.value.status)
        job.cancel(); job.join()
        assertEquals(SyncStatus.PENDING, s.snapshot.value.status)
        assertFalse(s.snapshot.value.busy)
        cloud.held!!.invoke() // Native callback/commit can finish after coroutine cancellation.
        s.sync()
        assertEquals(SyncStatus.SYNCED, s.snapshot.value.status)
        assertEquals(2, cloud.saves["alice"]?.revision)
        assertEquals(500, s.snapshot.value.game.coins)
    }

    @Test fun corruptAccountCacheDoesNotTrapAuthenticatedUserOrOverwriteData() = runBlocking {
        val disk = Disk().apply { data["account_save_v1_alice"] = "damaged cache" }
        val cloud = Cloud(); val s = session(Guest(encoded(131)), disk, cloud)
        s.signIn("google")
        assertEquals("alice", s.snapshot.value.account?.uid)
        assertTrue(s.snapshot.value.loadWarning)
        assertFalse(s.save(initial.copy(coins = 500)))
        s.signOut()
        assertNull(s.snapshot.value.account)
        assertEquals("damaged cache", disk.data["account_save_v1_alice"])
        assertEquals(0, cloud.writes)
    }

    @Test fun delayedNativeSignInCanBeRecoveredWithoutCreatingAnotherLogin() = runBlocking {
        val cloud = Cloud().apply { user = "alice"; saves["alice"] = Cloud.Save(encoded(500), 1) }
        val s = session(cloud = cloud)
        s.signIn("google")
        assertEquals("alice", s.snapshot.value.account?.uid)
        assertEquals(500, s.snapshot.value.game.coins)
        assertFalse(cloud.requests.any { it.first == "signIn" })
    }

    @Test fun confirmedRemoteDeletionReturnsGuestEvenIfLocalCleanupFails() = runBlocking {
        val disk = Disk(); val cloud = Cloud(); val s = session(Guest(encoded(131)), disk, cloud)
        s.signIn("google"); disk.failRemove = true; s.deleteAccount()
        assertNull(s.snapshot.value.account)
        assertNull(cloud.user)
        assertNull(cloud.saves["alice"])
        assertEquals(131, s.snapshot.value.game.coins)
        assertTrue(s.snapshot.value.message!!.contains("ล้างสำเนาในเครื่องไม่ครบ"))
    }
}
