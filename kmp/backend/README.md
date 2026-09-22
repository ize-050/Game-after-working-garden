# Personal cloud-save backend preparation

No real Firebase project is selected or deployed. `firebase.json` contains only rules and a loopback emulator configuration. There is deliberately no default project alias. Start with [../FIREBASE-SETUP.md](../FIREBASE-SETUP.md).

## Contract

- Native Firebase Auth verifies Google/Apple credentials. The shared game never receives tokens.
- Own document only: `players/{uid}` has `payload` (GameSaveCodec string, <=200000 UTF-8 bytes), `revision` (integer 1..2000000000), and `updatedAt` (server timestamp).
- First commit expects revision 0 and creates revision 1. Subsequent commits use a Firestore transaction, compare the expected revision, and increment it by one. Native adapters reject stale transactions; shared UI asks before conflict replacement.
- Missing cloud data is not treated as a blank replacement if the local record previously knew an existing revision.
- `accountDeletions/{uid}` contains only server `requestedAt`. A transaction creates the immutable marker and deletes the player document. Players cannot read/recreate/update their farm after the marker exists. The marker is checked with `existsAfter` for writes so a batch cannot create both a deleted account marker and a new farm.
- Rules deny cross-user reads/writes, collection listing, unexpected fields, wrong revisions, oversized payloads and client timestamps. All other collections are denied.
- These rules **do not parse game JSON or validate earned coins**. They provide private backup ownership, not anti-cheat or purchase authority. Premium currency requires separate trusted server logic.

## Run rule tests locally (after freeing disk space)

Requires a supported Node.js (22+ recommended), Java 21, and the dependencies listed in `package.json`. Package major versions were checked against the official npm registry; nothing was installed during preparation. There is no lockfile yet: generate and review it when installing, then use `npm ci` in CI.

```sh
cd /Users/ize/Desktop/old_system/after-hours-garden/kmp/backend
npm install
npm test
```

The test command uses `--project demo-little-farm`, a non-production demo identifier, and loopback port 8087. It starts only the Firestore emulator. It must not be changed to point to production for testing. Tests clear only emulator data between cases.

Prepared tests cover Guest denial, owner-only access, cross-owner denial, no listing, exact revisions, allowed fields, byte-size caps (including Thai), timestamps, deletion/tombstone behavior and resurrection prevention. Syntax was checked with Node; **emulator tests have not been executed on this machine**. In-memory Kotlin/UI tests do not substitute for running these server rules.

## Deployment (requires an explicitly chosen real project)

After emulator tests pass, use the Firebase CLI authenticated as the project owner, verify the target Project ID against the console, and deploy **only Firestore rules**. Do not run a broad deployment or set open/test rules. No deploy command has been run for this task.

Client Firebase configuration/API keys are not administrative credentials. Do not put service-account files or OAuth/Apple private keys in this folder or app bundle.

## Deletion / production readiness

Firestore cleanup and Auth deletion cannot be atomic together. Native adapters reauthenticate first, use the deletion marker, revoke Apple's token where relevant, then delete the Auth user. Errors after a destructive request return `deletion_incomplete`; the shared app persists a retry lock. Cancellation before destructive work unlocks play. Logout retains a pending deletion record, so returning to the same UID cannot silently upload it.

The minimal UID marker intentionally remains after account deletion to block old-token/old-device resurrection. Before production, establish retention and an idempotent privileged cleanup/reconciliation process after Auth deletion/token expiry is confirmed. The client cannot erase that marker. Also handle uncertain remote success and failed local cleanup, and disclose relevant retention in the privacy policy. This repository does not yet implement an administrative deletion worker or operational monitoring.

Official references: [Rules tests](https://firebase.google.com/docs/rules/unit-tests), [Fields and validation](https://firebase.google.com/docs/firestore/security/rules-fields), [Transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).
