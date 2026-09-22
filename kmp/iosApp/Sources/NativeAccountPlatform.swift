import Foundation
import Shared
import UIKit

#if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
import AuthenticationServices
import CryptoKit
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import GoogleSignIn
import Security
#endif

/// The only provider/Firestore boundary. Tokens never cross the Kotlin JSON bridge.
/// Without the optional SDKs and app configuration, Guest remains a real offline game.
final class NativeAccountPlatform: NSObject, AccountPlatform {
    private typealias Reply = ([String: Any]) -> Void
    private var pending: [() -> Void] = []
    private var isProcessing = false
    private var providers: [String] = []
    private var unavailableMessage = "ยังไม่ได้เชื่อม Firebase สำหรับ iOS เล่นแบบ Guest ได้ตามปกติ"

    #if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
    private var database: Firestore?
    private var appleRequest: AppleCredentialRequest?
    private let deletionKey = "little_farm_native_pending_deletion_v1"
    #endif

    override init() {
        super.init()
        #if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
        configureIfReady()
        #endif
    }

    func availability() -> String {
        Self.json([
            "providers": providers,
            "message": providers.isEmpty ? unavailableMessage as Any : NSNull()
        ])
    }

    func handle(url: URL) {
        #if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
        if providers.contains("google") { _ = GIDSignIn.sharedInstance.handle(url) }
        #endif
    }

    func request(operation: String, payload: String, completion: @escaping (String) -> Void) {
        // Serial requests prevent sign-out/account switches during an in-flight save/delete.
        DispatchQueue.main.async {
            self.pending.append {
                var delivered = false
                let reply: Reply = { response in
                    DispatchQueue.main.async {
                        guard !delivered else { return }
                        delivered = true
                        completion(Self.json(response))
                        self.isProcessing = false
                        self.processNext()
                    }
                }
                guard payload.utf8.count <= 450_000,
                      let data = payload.data(using: .utf8),
                      let args = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
                    reply(Self.failure("invalid_data")); return
                }
                #if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
                self.perform(operation: operation, args: args, reply: reply)
                #else
                if operation == "restore" {
                    reply(["ok": true, "user": NSNull()])
                } else {
                    reply(Self.failure("unavailable"))
                }
                #endif
            }
            self.processNext()
        }
    }

    private func processNext() {
        guard !isProcessing, !pending.isEmpty else { return }
        isProcessing = true
        pending.removeFirst()()
    }

    private static func json(_ object: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: data, encoding: .utf8) else {
            return "{\"ok\":false,\"code\":\"invalid_data\"}"
        }
        return text
    }

    private static func failure(_ code: String) -> [String: Any] { ["ok": false, "code": code] }
}

#if canImport(FirebaseCore) && canImport(FirebaseAuth) && canImport(FirebaseFirestore) && canImport(GoogleSignIn)
private extension NativeAccountPlatform {
    func configureIfReady() {
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path),
              options.bundleID == Bundle.main.bundleIdentifier,
              let projectID = options.projectID, !projectID.isEmpty,
              let apiKey = options.apiKey, !apiKey.isEmpty else {
            unavailableMessage = "ยังไม่มี GoogleService-Info.plist ที่ตรงกับแอปนี้ เล่นแบบ Guest ได้ก่อน"
            return
        }
        if FirebaseApp.app() == nil { FirebaseApp.configure(options: options) }
        guard FirebaseApp.app()?.options.googleAppID == options.googleAppID else {
            unavailableMessage = "การตั้งค่า Firebase ไม่ตรงกับแอปนี้ กรุณาตรวจสอบโปรเจกต์"
            return
        }
        let db = Firestore.firestore()
        let settings = FirestoreSettings()
        // GameSession owns UID-separated offline saves; never treat a Firestore cache as cloud truth.
        settings.cacheSettings = MemoryCacheSettings()
        db.settings = settings
        database = db

        let plist = NSDictionary(contentsOfFile: path)
        let expectedScheme = plist?["REVERSED_CLIENT_ID"] as? String
        let schemes = (Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? [])
            .flatMap { $0["CFBundleURLSchemes"] as? [String] ?? [] }
        if let clientID = options.clientID, !clientID.isEmpty,
           let expectedScheme, schemes.contains(expectedScheme) {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            providers.append("google")
        }
        // Enabled only after Sign in with Apple capability/provisioning + Firebase provider setup.
        let appleEnabled = Bundle.main.object(forInfoDictionaryKey: "LittleFarmAppleSignInEnabled") as? String
        if appleEnabled == "YES" { providers.append("apple") }
        if providers.isEmpty {
            unavailableMessage = "Firebase พร้อมแล้ว แต่ยังไม่ได้ตั้งค่า Google URL scheme หรือ Sign in with Apple"
        }
    }

    func perform(operation: String, args: [String: Any], reply: @escaping Reply) {
        guard database != nil else {
            reply(operation == "restore" ? ["ok": true, "user": NSNull()] : Self.failure("unavailable"))
            return
        }
        switch operation {
        case "restore":
            reply(["ok": true, "user": Auth.auth().currentUser.map { Self.userJSON($0) as Any } ?? NSNull()])
        case "signIn":
            guard let provider = args["provider"] as? String, providers.contains(provider) else {
                reply(Self.failure("unavailable")); return
            }
            // The shared layer must explicitly sign out before switching identities.
            guard Auth.auth().currentUser == nil else {
                reply(Self.failure("permission")); return
            }
            obtainCredential(provider: provider) { credential, _, error in
                guard Auth.auth().currentUser == nil else { reply(Self.failure("unauthenticated")); return }
                if let error { reply(Self.failure(Self.errorCode(error))); return }
                guard let credential else { reply(Self.failure("invalid_data")); return }
                Auth.auth().signIn(with: credential) { result, error in
                    DispatchQueue.main.async {
                        if let error { reply(Self.failure(Self.errorCode(error))); return }
                        guard let user = result?.user, Auth.auth().currentUser?.uid == user.uid else {
                            reply(Self.failure("unauthenticated")); return
                        }
                        reply(["ok": true, "user": Self.userJSON(user)])
                    }
                }
            }
        case "signOut":
            guard let uid = Self.validUID(args["uid"]) else { reply(Self.failure("invalid_data")); return }
            // A remotely deleted/expired SDK session may already be absent. Clearing only the
            // caller's shared snapshot is safe; do not touch a possibly unrelated provider session.
            guard let current = Auth.auth().currentUser else { reply(["ok": true]); return }
            guard current.uid == uid else { reply(Self.failure("unauthenticated")); return }
            do {
                guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
                try Auth.auth().signOut()
                GIDSignIn.sharedInstance.signOut()
                reply(["ok": true])
            } catch { reply(Self.failure(Self.errorCode(error))) }
        case "loadSave":
            guard let uid = checkedUID(args, reply: reply), !deletionPending(uid, reply: reply) else { return }
            loadSave(uid: uid, reply: reply)
        case "commitSave":
            guard let uid = checkedUID(args, reply: reply), !deletionPending(uid, reply: reply),
                  let revision = Self.revision(args["expectedRevision"]),
                  let payload = args["payload"] as? String, Self.validPayload(payload) else {
                // checkedUID/deletionPending already returned a reply when applicable; delivery is once-only.
                reply(Self.failure("invalid_data")); return
            }
            commitSave(uid: uid, expectedRevision: revision, payload: payload, reply: reply)
        case "deleteAccount":
            guard let uid = checkedUID(args, reply: reply) else { return }
            deleteAccount(uid: uid, reply: reply)
        default: reply(Self.failure("invalid_data"))
        }
    }

    func checkedUID(_ args: [String: Any], reply: Reply) -> String? {
        guard let uid = Self.validUID(args["uid"]) else {
            reply(Self.failure("invalid_data")); return nil
        }
        guard Auth.auth().currentUser?.uid == uid else {
            reply(Self.failure("unauthenticated")); return nil
        }
        return uid
    }

    static func validUID(_ value: Any?) -> String? {
        guard let uid = value as? String, !uid.isEmpty, !uid.contains("/"), uid.utf8.count <= 128 else { return nil }
        return uid
    }

    func deletionPending(_ uid: String, reply: Reply) -> Bool {
        guard UserDefaults.standard.string(forKey: deletionKey) == uid else { return false }
        reply(Self.failure("deletion_incomplete"))
        return true
    }

    static func userJSON(_ user: User) -> [String: Any] {
        let provider = user.providerData.contains { $0.providerID == "apple.com" } ? "apple" : "google"
        return ["uid": user.uid, "displayName": user.displayName ?? "ชาวสวน", "provider": provider]
    }

    static func revision(_ value: Any?) -> Int? {
        guard let number = value as? NSNumber,
              CFGetTypeID(number) != CFBooleanGetTypeID(),
              number.doubleValue.isFinite,
              number.doubleValue.rounded(.towardZero) == number.doubleValue,
              number.doubleValue >= 0, number.doubleValue <= 2_000_000_000 else { return nil }
        return number.intValue
    }

    static func validPayload(_ payload: String) -> Bool {
        guard !payload.isEmpty, payload.utf8.count <= 200_000,
              let data = payload.data(using: .utf8),
              (try? JSONSerialization.jsonObject(with: data)) is [String: Any] else { return false }
        return true // Shared GameSaveCodec validates the full game schema before use.
    }

    func loadSave(uid: String, reply: @escaping Reply) {
        guard Auth.auth().currentUser?.uid == uid, let database else { reply(Self.failure("unauthenticated")); return }
        database.collection("accountDeletions").document(uid).getDocument(source: .server) { marker, error in
            DispatchQueue.main.async {
                guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
                if let error { reply(Self.failure(Self.errorCode(error))); return }
                guard let marker else { reply(Self.failure("invalid_data")); return }
                guard !marker.exists else { reply(Self.failure("deletion_incomplete")); return }
                database.collection("players").document(uid).getDocument(source: .server) { snapshot, error in
                    DispatchQueue.main.async {
                        guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
                        if let error { reply(Self.failure(Self.errorCode(error))); return }
                        guard let snapshot else { reply(Self.failure("invalid_data")); return }
                        guard snapshot.exists else { reply(["ok": true, "save": NSNull()]); return }
                        guard let data = snapshot.data(), let payload = data["payload"] as? String,
                              Self.validPayload(payload), let revision = Self.revision(data["revision"]), revision > 0 else {
                            reply(Self.failure("invalid_data")); return
                        }
                        reply(["ok": true, "save": ["payload": payload, "revision": revision]])
                    }
                }
            }
        }
    }

    func commitSave(uid: String, expectedRevision: Int, payload: String, reply: @escaping Reply) {
        guard Auth.auth().currentUser?.uid == uid, let database else { reply(Self.failure("unauthenticated")); return }
        guard expectedRevision < 2_000_000_000 else { reply(Self.failure("invalid_data")); return }
        let reference = database.collection("players").document(uid)
        let deletionReference = database.collection("accountDeletions").document(uid)
        database.runTransaction({ transaction, errorPointer -> Any? in
            guard Auth.auth().currentUser?.uid == uid else {
                errorPointer?.pointee = Self.bridgeError("unauthenticated"); return nil
            }
            do {
                let marker = try transaction.getDocument(deletionReference)
                guard !marker.exists else {
                    errorPointer?.pointee = Self.bridgeError("deletion_incomplete"); return nil
                }
                let snapshot = try transaction.getDocument(reference)
                let revision: Int
                if snapshot.exists {
                    guard let stored = Self.revision(snapshot.data()?["revision"]), stored > 0 else {
                        errorPointer?.pointee = Self.bridgeError("invalid_data"); return nil
                    }
                    revision = stored
                } else { revision = 0 }
                guard revision == expectedRevision else {
                    errorPointer?.pointee = Self.bridgeError("conflict"); return nil
                }
                guard Auth.auth().currentUser?.uid == uid else {
                    errorPointer?.pointee = Self.bridgeError("unauthenticated"); return nil
                }
                let next = revision + 1
                transaction.setData([
                    "payload": payload, "revision": next, "updatedAt": FieldValue.serverTimestamp()
                ], forDocument: reference)
                return next
            } catch {
                errorPointer?.pointee = error as NSError
                return nil
            }
        }) { result, error in
            DispatchQueue.main.async {
                guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
                if let error { reply(Self.failure(Self.errorCode(error))); return }
                guard let revision = Self.revision(result), revision == expectedRevision + 1 else {
                    reply(Self.failure("invalid_data")); return
                }
                reply(["ok": true, "save": ["payload": payload, "revision": revision]])
            }
        }
    }

    func obtainCredential(provider: String, completion: @escaping (AuthCredential?, String?, Error?) -> Void) {
        guard let presenter = Self.presentingController() else {
            completion(nil, nil, Self.bridgeError("unavailable")); return
        }
        if provider == "google" {
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                DispatchQueue.main.async {
                    if let error { completion(nil, nil, error); return }
                    guard let user = result?.user, let token = user.idToken?.tokenString else {
                        completion(nil, nil, Self.bridgeError("invalid_data")); return
                    }
                    completion(GoogleAuthProvider.credential(withIDToken: token, accessToken: user.accessToken.tokenString), nil, nil)
                }
            }
        } else if provider == "apple" {
            guard let window = presenter.view.window else {
                completion(nil, nil, Self.bridgeError("unavailable")); return
            }
            let request = AppleCredentialRequest(window: window) { [weak self] credential, code, error in
                DispatchQueue.main.async {
                    self?.appleRequest = nil
                    completion(credential, code, error)
                }
            }
            appleRequest = request // ASAuthorizationController does not retain its delegate.
            request.start()
        } else { completion(nil, nil, Self.bridgeError("unavailable")) }
    }

    func deleteAccount(uid: String, reply: @escaping Reply) {
        guard let user = Auth.auth().currentUser, user.uid == uid else { reply(Self.failure("unauthenticated")); return }
        let provider = user.providerData.contains { $0.providerID == "apple.com" } ? "apple" : "google"
        guard providers.contains(provider) else { reply(Self.failure("unavailable")); return }
        obtainCredential(provider: provider) { credential, authorizationCode, error in
            guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
            if let error { reply(Self.failure(Self.errorCode(error))); return }
            guard let credential else { reply(Self.failure("reauth_required")); return }
            user.reauthenticate(with: credential) { _, error in
                DispatchQueue.main.async {
                    guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
                    if let error { reply(Self.failure(Self.errorCode(error))); return }
                    self.beginDeletion(uid: uid, user: user, provider: provider, authorizationCode: authorizationCode, reply: reply)
                }
            }
        }
    }

    func beginDeletion(uid: String, user: User, provider: String, authorizationCode: String?, reply: @escaping Reply) {
        guard Auth.auth().currentUser?.uid == uid else { reply(Self.failure("unauthenticated")); return }
        if provider == "apple", authorizationCode == nil { reply(Self.failure("reauth_required")); return }
        // Persistent guard makes a failed multi-service deletion retry-only, even after relaunch.
        UserDefaults.standard.set(uid, forKey: deletionKey)
        let deleteCloud: () -> Void = {
            guard Auth.auth().currentUser?.uid == uid, let database = self.database else {
                reply(Self.failure("deletion_incomplete")); return
            }
            let reference = database.collection("players").document(uid)
            let deletionReference = database.collection("accountDeletions").document(uid)
            // A transaction cannot queue a successful-looking offline delete. Missing data is retry-safe.
            database.runTransaction({ transaction, errorPointer -> Any? in
                guard Auth.auth().currentUser?.uid == uid else {
                    errorPointer?.pointee = Self.bridgeError("unauthenticated"); return nil
                }
                do {
                    let marker = try transaction.getDocument(deletionReference)
                    if !marker.exists {
                        // Read before writes on the first attempt only. Rules deliberately forbid
                        // reading player data once a durable deletion marker already exists.
                        _ = try transaction.getDocument(reference)
                        transaction.setData(["requestedAt": FieldValue.serverTimestamp()], forDocument: deletionReference)
                    }
                    transaction.deleteDocument(reference)
                    return true
                } catch { errorPointer?.pointee = error as NSError; return nil }
            }) { _, error in
                DispatchQueue.main.async {
                    guard error == nil, Auth.auth().currentUser?.uid == uid else {
                        reply(Self.failure("deletion_incomplete")); return
                    }
                    user.delete { error in
                        DispatchQueue.main.async {
                            guard error == nil else { reply(Self.failure("deletion_incomplete")); return }
                            // Deletion clears currentUser; a different UID must never be signed out here.
                            guard Auth.auth().currentUser == nil else { reply(Self.failure("deletion_incomplete")); return }
                            GIDSignIn.sharedInstance.signOut()
                            UserDefaults.standard.removeObject(forKey: self.deletionKey)
                            reply(["ok": true])
                        }
                    }
                }
            }
        }
        if provider == "apple", let authorizationCode {
            Auth.auth().revokeToken(withAuthorizationCode: authorizationCode) { error in
                DispatchQueue.main.async {
                    guard error == nil, Auth.auth().currentUser?.uid == uid else {
                        reply(Self.failure("deletion_incomplete")); return
                    }
                    deleteCloud()
                }
            }
        } else { deleteCloud() }
    }

    static func presentingController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let root = scenes.filter({ $0.activationState == .foregroundActive })
            .flatMap({ $0.windows }).first(where: { $0.isKeyWindow })?.rootViewController else { return nil }
        var presenter = root
        while let presented = presenter.presentedViewController { presenter = presented }
        return presenter
    }

    fileprivate static func bridgeError(_ code: String) -> NSError {
        NSError(domain: "LittleFarmAccount", code: 1, userInfo: ["bridgeCode": code])
    }

    static func errorCode(_ error: Error) -> String {
        let error = error as NSError
        if error.domain == "LittleFarmAccount", let code = error.userInfo["bridgeCode"] as? String { return code }
        if error.domain == ASAuthorizationError.errorDomain, error.code == ASAuthorizationError.canceled.rawValue { return "cancelled" }
        if error.domain == kGIDSignInErrorDomain, error.code == GIDSignInErrorCode.canceled.rawValue { return "cancelled" }
        if error.domain == NSURLErrorDomain { return "network" }
        if error.domain == AuthErrorDomain {
            switch error.code {
            case AuthErrorCode.networkError.rawValue: return "network"
            case AuthErrorCode.requiresRecentLogin.rawValue, AuthErrorCode.userMismatch.rawValue: return "reauth_required"
            case AuthErrorCode.userNotFound.rawValue, AuthErrorCode.userDisabled.rawValue,
                 AuthErrorCode.invalidUserToken.rawValue, AuthErrorCode.userTokenExpired.rawValue: return "unauthenticated"
            case AuthErrorCode.operationNotAllowed.rawValue, AuthErrorCode.invalidAPIKey.rawValue: return "unavailable"
            default: return "unknown"
            }
        }
        if error.domain == FirestoreErrorDomain {
            switch error.code {
            case FirestoreErrorCode.unavailable.rawValue, FirestoreErrorCode.deadlineExceeded.rawValue: return "network"
            case FirestoreErrorCode.unauthenticated.rawValue: return "unauthenticated"
            case FirestoreErrorCode.permissionDenied.rawValue: return "permission"
            case FirestoreErrorCode.invalidArgument.rawValue: return "invalid_data"
            default: return "unknown"
            }
        }
        return "unknown"
    }
}

private final class AppleCredentialRequest: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let window: UIWindow
    private var completion: ((AuthCredential?, String?, Error?) -> Void)?
    private var nonce: String?
    private var controller: ASAuthorizationController?

    init(window: UIWindow, completion: @escaping (AuthCredential?, String?, Error?) -> Void) {
        self.window = window
        self.completion = completion
        super.init()
    }

    func start() {
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            finish(nil, nil, NativeAccountPlatform.bridgeError("unknown")); return
        }
        let nonce = bytes.map { String(format: "%02x", $0) }.joined()
        self.nonce = nonce
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        let controller = ASAuthorizationController(authorizationRequests: [request])
        self.controller = controller
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor { window }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let apple = authorization.credential as? ASAuthorizationAppleIDCredential,
              let nonce, let tokenData = apple.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            finish(nil, nil, NativeAccountPlatform.bridgeError("invalid_data")); return
        }
        let credential = OAuthProvider.appleCredential(withIDToken: token, rawNonce: nonce, fullName: apple.fullName)
        let code = apple.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        finish(credential, code, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(nil, nil, error)
    }

    private func finish(_ credential: AuthCredential?, _ code: String?, _ error: Error?) {
        let callback = completion
        completion = nil
        nonce = nil
        controller = nil
        callback?(credential, code, error)
    }
}
#endif
