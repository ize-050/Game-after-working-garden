# Android accounts: configuration and verification

## Current state

The Android native adapter is prepared, but no Firebase project or OAuth credentials have been created. **Login and cloud save are unavailable until real configuration is supplied.** The default build selects `src/offline/kotlin`, excludes Firebase dependencies, and continues to use the local Guest game. It does not simulate a successful account or upload.

The enabled adapter uses Credential Manager for Google, Firebase's browser OAuth flow for Apple, and server-confirmed Firestore reads and transactions. Authentication tokens remain inside the native SDK / operation memory; only public account identity and validated garden save JSON cross the KMP bridge.

The Guest-only Gradle configuration was checked successfully with `:androidApp:tasks --offline` (no Firebase dependencies downloaded). Enabling `-Plittlefarm.firebase=true` without the JSON was also checked: it stops with the intended missing-configuration message before resolving Firebase dependencies. These validate build-script configuration, **not** Android source compilation or runtime behavior.

An Android SDK, sufficient disk space, and a compatible Android Studio are still needed to compile and launch either Android variant. The enabled variant has **not** been compiled against Android/Firebase SDKs or tested with real accounts on this machine.

## Create the project and enable Google

1. Create a Firebase project in the [Firebase console](https://console.firebase.google.com/). Analytics is not needed for this account implementation. Register an Android app with package name `com.littlefarm.game`, or first choose the final application ID and update the Gradle `applicationId` to match.
2. Add the SHA-1 and SHA-256 certificate fingerprints for every signing certificate used to test or distribute the app. Once the Android SDK is installed, run `./scripts/gradle.sh :androidApp:signingReport` from the KMP root for local signing information. For Play distribution, also register the Play App Signing certificate.
3. Enable **Authentication → Sign-in method → Google**, select a support email, and finish the provider setup. Google consent-screen configuration / test-user access must permit the accounts being tested.
4. Download the updated, genuine `google-services.json` and put it in `androidApp/google-services.json`. It must match the application ID and contain the Google **Web client ID** used as `default_web_client_id`; an Android OAuth client ID is not a replacement. This file is locally ignored by Git. Never put a service-account JSON, OAuth client secret, or Apple `.p8` key in the app.
5. Create the **default Cloud Firestore database** in production/locked mode and choose its region deliberately. Publish the repository's reviewed security rules before testing; do not enable blanket read/write access or leave test-mode rules in place. The adapter expects `players/{uid}` and `accountDeletions/{uid}`.
6. Enable the Firebase build explicitly:

   ```sh
   ./scripts/gradle.sh -Plittlefarm.firebase=true :androidApp:assembleDebug
   ```

   Alternatively, add `littlefarm.firebase=true` to your local Gradle user properties, then sync Android Studio. The build fails clearly if the JSON is missing. Without this property, the Guest-only variant remains selected even when a JSON file exists.

Follow the official [Firebase Android setup](https://firebase.google.com/docs/android/setup) and [Google authentication setup](https://firebase.google.com/docs/auth/android/google-signin) when creating the actual project. This code uses the current [Credential Manager button flow](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), not deprecated GoogleSignIn APIs.

## Enable Apple separately

Apple on Android uses a browser redirect, so the iOS capability alone is insufficient. Complete the [Firebase Apple-on-Android setup](https://firebase.google.com/docs/auth/android/apple):

1. In the Apple Developer account, configure Sign in with Apple, its associated **Service ID**, domain, and the Firebase return URL shown by your actual project (`https://<your-project-id>.firebaseapp.com/__/auth/handler`). These are deliberate placeholders, not existing credentials.
2. Configure Firebase's Apple provider with that Service ID and the Apple Team ID / Key ID / private key in the **Firebase console only**. Do not send or commit the private key to this repository. Register the Android signing fingerprints in Firebase.
3. Complete any required Apple private-email-relay setup if the project sends authentication emails. Obtain explicit consent before linking an anonymized Apple identity to another provider; this implementation does not automatically merge accounts by email or link Google and Apple accounts.
4. Build with both flags after configuration is ready:

   ```sh
   ./scripts/gradle.sh -Plittlefarm.firebase=true -Plittlefarm.appleSignIn=true :androidApp:assembleDebug
   ```

Only the Google provider is advertised by default in a Firebase-enabled build with a valid Web client ID. Apple remains hidden until the second flag is enabled. Firebase's pending OAuth result is consumed on restore after Activity recreation. Google and Apple sign-ins are distinct Firebase identities unless deliberately linked elsewhere; choosing either provider does not automatically give the same garden.

## Save and account-deletion behavior

- Cloud load explicitly uses `Source.SERVER`; a stale SDK cache is not presented as a fresh cloud backup. Offline gameplay/cache handling belongs to `GardenSession`. Firestore has a memory-only cache to avoid an extra persistent copy across signed-in users.
- Save writes are online Firestore transactions. The current revision must equal the caller's `expectedRevision` (absent document = 0), then it increments by one. A conflict returns `conflict` without overwriting the other device. Payloads are valid game saves no larger than 200,000 UTF-8 bytes; maximum revision is 2,000,000,000. See [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).
- The bridge checks the actual Firebase UID before operations and again after asynchronous results. Every read/write must additionally pass the deployed server security rules; hiding another UID in the UI is not access control.
- Deletion first obtains a fresh Google/Apple credential and reauthenticates the **same UID**. It transactionally creates an immutable `accountDeletions/{uid}` marker and deletes `players/{uid}`; an existing marker / missing player document is safe to retry. Security rules must prohibit all subsequent player writes for that UID, including attempts from another device.
- After that transaction, an Apple credential's access token is revoked via Firebase, then the Firebase Auth user is deleted. Every failure after the destructive transaction is dispatched returns `deletion_incomplete`, because remote state may have changed even if its response was lost. The common session must keep that account locked and offer deletion retry, not recreate its cloud garden.
- This is **not an atomic transaction across Firestore, Apple, and Firebase Auth**. Process termination or network loss can leave deletion pending; test retry and recovery. The tiny UID deletion marker is intentionally retained to prevent resurrection, contains only a server timestamp, and needs a documented production retention/cleanup policy. For production, add a privileged server-side deletion/reconciliation workflow that cannot be bypassed by old clients and addresses other data stores when they are added.
- UID ownership and revision checks do not make client-uploaded coins trustworthy. This is a private, noncompetitive farming save, not an authoritative paid-currency backend. Do not reuse this payload as the source of truth for purchases, trading, or premium balances.

## Required live checks before calling this ready

- Build both Guest-only and Firebase-enabled Android APKs; confirm Guest does not need credentials or a network.
- Google first sign-in, returning sign-in, account chooser cancellation, incorrect certificate setup, and offline failure.
- Apple first/returning login, cancellation, Activity recreation during browser redirect, and missing provider configuration.
- Distinct accounts cannot read or write each other's documents; use real rule tests plus emulator/device checks.
- Guest import and an already-existing cloud garden require the shared conflict choice; no automatic balance merging.
- Two devices writing the same revision result in one committed version and one conflict, not silent last-writer-wins.
- Offline load never reports an old cache as server-confirmed; reconnect does not overwrite a newer revision.
- Deletion with wrong reauth identity performs no deletion. Successful deletion removes the player document and Auth user; an old client cannot recreate the save. Retry after each partial step, after app termination, and after uncertain network responses.

No real Firebase app, database, provider, billing configuration, rule deployment, or account deletion was performed while preparing these files.
