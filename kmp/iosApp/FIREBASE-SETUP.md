# iOS account adapter — activation checklist

The native implementation is in `Sources/NativeAccountPlatform.swift`, compiled into the LittleFarm target. The default project intentionally has **no Firebase/Google SDK package references**. Guest builds do not download them; the account screen reports that sign-in is not configured. No Firebase project, provider credentials, live database, billing, or provisioning has been created by this change.

## Activate when a Firebase project is available

1. Free disk space before resolving SDKs or installing Simulator runtimes. Open `iosApp.xcodeproj` and choose a real, stable bundle identifier and signing Team. Register **that same identifier** as an Apple app in Firebase. The current `com.littlefarm.game` is a placeholder, not a registered service identifier.
2. In Xcode **File → Add Package Dependencies**, add the official repositories below. Choose supported releases compatible with the selected Xcode/iOS target, and commit the resulting `Package.resolved` after the enabled build has been verified. This setup intentionally does not guess/pin untested SDK versions.
   - `https://github.com/firebase/firebase-ios-sdk.git`: add **FirebaseCore**, **FirebaseAuth**, **FirebaseFirestore** to target **LittleFarm**.
   - `https://github.com/google/GoogleSignIn-iOS`: add **GoogleSignIn** to target **LittleFarm**.
   - `-ObjC` is already in Other Linker Flags. All four modules must be linked for the native implementation to activate; otherwise the Guest fallback remains explicit.
3. Download the app's real `GoogleService-Info.plist` from Firebase after enabling Google as an Authentication provider. Add it to the Xcode project with **LittleFarm target membership** and **Copy Bundle Resources**. Do not add a service-account JSON/private key to the app. Client config is not an authorization boundary; Firestore Rules are mandatory.
4. Copy `REVERSED_CLIENT_ID` from that plist into the target's **GOOGLE_REVERSED_CLIENT_ID** user-defined build setting for Debug and Release. `Info.plist` already references this setting in `CFBundleURLTypes`; SwiftUI forwards callback URLs to Google Sign-In. The adapter disables Google sign-in if the scheme or client ID is missing/mismatched.
5. In Firebase, create a Cloud Firestore database and deploy the repository's owner-only rules **before enabling real accounts**. Do not use test/open rules. The app reads/writes only `players/{firebaseUID}` and `accountDeletions/{firebaseUID}`. Use the same Firebase project for the future Android app to share UID/cloud data.
6. For Apple, configure Sign in with Apple for the app identifier in Apple Developer, enable Apple's provider in Firebase, and configure the service ID/team/key needed by Firebase's OAuth flow and token revocation. Private `.p8` keys belong in the provider's secure setup, **never this repository or app bundle**.
7. Enable **Sign in with Apple** under Xcode **Signing & Capabilities**, set `CODE_SIGN_ENTITLEMENTS` to `LittleFarm.entitlements` (or merge its single entitlement into Xcode's generated entitlement file), and set **LITTLEFARM_APPLE_SIGN_IN_ENABLED = YES** for the configurations where signing/provisioning is ready. This is intentionally **NO** and unattached for unsigned Guest builds. The flag declares setup readiness; it does not create/check the Apple portal capability remotely.

## Implemented behavior

- SDK-managed Firebase session restoration, native Google sign-in, native Apple sign-in with a cryptographically random nonce and SHA-256 binding, native sign-out. The bridge returns UID/display name/provider only, never provider tokens or raw error details.
- Sign-out succeeds if the SDK session is already absent, allowing shared state to recover from a remotely deleted account. It never signs out an unexpectedly different UID; that mismatch must be reconciled explicitly in the shared session.
- Main-thread Kotlin completions and serialized native requests. Cloud requests verify the caller's UID against Firebase's current user before starting, inside transactions, and at completion. Server reads do not claim that an offline cache is the latest cloud save.
- `players/{uid}` contains `payload` (the encoded game save), integer `revision`, and server `updatedAt`. Commit transactions compare `expectedRevision`, reject stale writes, and return the committed revision. Saves are capped at 200,000 UTF-8 bytes and revisions at 2,000,000,000. Full game-schema validation is in shared Kotlin; this storage is **not authoritative anti-cheat or a paid-currency ledger**.
- Deletion always asks for fresh provider authentication. For Apple it also revokes the newly obtained authorization code. A transaction creates a durable `accountDeletions/{uid}` tombstone and deletes the player's save, then deletes Firebase Auth's user. Rules must deny player reads/creates/updates whenever the tombstone exists, even on another device. Tombstones cannot be modified/deleted by clients. They prevent stale-token clients from recreating deleted data.
- Account deletion spans separate services and is not atomic. A local persistent guard and the shared session's deletion state block further saves after a partial failure. The bridge returns `deletion_incomplete`, never success, and a retry reauthenticates and tolerates an existing tombstone/missing save. If Auth deletion succeeded remotely but its response was lost and no Firebase user remains, trusted administrative verification/cleanup may be needed; do not silently recreate the account or remove its tombstone.
- Google and Apple identities are **not automatically merged by this adapter**. Guest-to-account garden migration/conflict choices are shared Kotlin behavior. Linking additional providers to one account requires a separate explicit-consent flow, especially for Apple private-relay users.

## Verification required before claiming live login/cloud support

This prepared implementation has not been compiled against downloaded Firebase/Google SDKs, signed, launched in a Simulator/device, or tested against a Firebase project. Guest Kotlin compilation and Swift parser checks are not SDK integration tests.

After activation, verify both provider happy/cancel/error paths on a device; returning-user restoration; wrong-account reauthentication; one account on two devices; two different accounts on one device; concurrent revision conflict; offline save/sync recovery; app restart during conflict; sign-out during sync; missing/denied rules; deletion cancellation; deletion retries after each network failure; stale second-device writes after a tombstone; Apple revocation; reinstall/cloud restore; and privacy/account-deletion disclosures. Rebuild the full `.app` after linking the updated shared framework.

## Official references used

- [Firebase Apple SDK setup](https://firebase.google.com/docs/ios/setup)
- [Firebase Google sign-in](https://firebase.google.com/docs/auth/ios/google-signin)
- [Firebase Apple sign-in, nonce, reauthentication and revocation](https://firebase.google.com/docs/auth/ios/apple)
- [Firestore transactions and retries](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Firestore memory cache](https://firebase.google.com/docs/reference/swift/firebasefirestore/api/reference/Classes/MemoryCacheSettings)
