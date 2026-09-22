package com.littlefarm.android

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.littlefarm.account.AccountPlatform
import com.littlefarm.game.GameSaveCodec
import java.lang.ref.WeakReference
import java.security.SecureRandom
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_PAYLOAD_BYTES = 200_000
private const val MAX_REVISION = 2_000_000_000L

private class AccountFailure(val code: String, override val message: String) : Exception(message)

/** One native SDK instance per process; shared JSON never contains an authentication token. */
private class FirebaseServices(context: Context) {
    private val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?: FirebaseApp.initializeApp(context)
        ?: throw AccountFailure("unavailable", "ไม่พบ Firebase configuration")
    val auth: FirebaseAuth = FirebaseAuth.getInstance(app)
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(app).apply {
        // GardenSession owns the UID-scoped offline cache; cloud reads must reach the server.
        firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings)
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }
    val credentialManager = CredentialManager.create(context)
    val requestMutex = Mutex()
    val webClientId: String? = context.resources
        .getIdentifier("default_web_client_id", "string", context.packageName)
        .takeIf { it != 0 }
        ?.let(context::getString)
        ?.takeIf { it.endsWith(".apps.googleusercontent.com") }
}

private var processServices: FirebaseServices? = null

fun createAndroidAccountPlatform(activity: Activity): AccountPlatform {
    val services = runCatching {
        processServices ?: FirebaseServices(activity.applicationContext).also { processServices = it }
    }.getOrNull()
    return AndroidFirebaseAccountPlatform(activity, services)
}

private class AndroidFirebaseAccountPlatform(
    activity: Activity,
    private val services: FirebaseServices?,
) : AccountPlatform {
    private val activityRef = WeakReference(activity)
    // Requests finish independently of Activity recreation. restore also consumes Firebase's pending OAuth result.
    private val scope = MainScope()
    private var inFlight = false

    override fun availability(): String {
        val providers = buildList {
            if (services?.webClientId != null) add("google")
            if (services != null && BuildConfig.APPLE_SIGN_IN_ENABLED) add("apple")
        }
        return JSONObject().put("providers", JSONArray(providers)).put(
            "message",
            if (providers.isEmpty()) "ยังไม่ได้ตั้งค่า Firebase / Google OAuth สำหรับ Android" else JSONObject.NULL,
        ).toString()
    }

    override fun request(operation: String, payload: String, completion: (String) -> Unit) {
        scope.launch {
            if (inFlight) {
                completion(failure("unknown", "กำลังดำเนินการบัญชีอยู่ กรุณารอสักครู่").toString())
                return@launch
            }
            inFlight = true
            val response = try {
                val native = services ?: throw AccountFailure("unavailable", "ยังไม่ได้ตั้งค่า Firebase")
                native.requestMutex.withLock { perform(native, operation, JSONObject(payload)) }
            } catch (error: Exception) {
                errorResponse(error)
            }
            inFlight = false
            completion(response.toString())
        }
    }

    private suspend fun perform(native: FirebaseServices, operation: String, request: JSONObject): JSONObject {
        val auth = native.auth
        return when (operation) {
            "restore" -> {
                val pending = auth.pendingAuthResult
                val user = if (pending != null) pending.await().user else auth.currentUser
                if (user != null) requireCurrent(native, user.uid)
                else if (auth.currentUser != null) throw changedUser()
                success().put("user", user?.toPublicJson() ?: JSONObject.NULL)
            }
            "signIn" -> {
                if (auth.currentUser != null) {
                    throw AccountFailure("permission", "กรุณาออกจากบัญชีเดิมก่อนเปลี่ยนบัญชี")
                }
                val result = when (request.opt("provider") as? String) {
                    "google" -> {
                        val credential = googleCredential(native)
                        if (auth.currentUser != null) throw changedUser()
                        auth.signInWithCredential(credential).await()
                    }
                    "apple" -> {
                        requireAppleEnabled()
                        (auth.pendingAuthResult ?: auth.startActivityForSignInWithProvider(
                            requireActivity(), appleProvider(),
                        )).await()
                    }
                    else -> throw AccountFailure("unavailable", "ยังไม่รองรับผู้ให้บริการนี้")
                }
                val user = result.user ?: throw changedUser()
                requireCurrent(native, user.uid)
                success().put("user", user.toPublicJson())
            }
            "signOut" -> {
                val uid = requestedUid(request)
                // An already-cleared SDK session is a successful, retry-safe logout.
                // Never sign out a different user if the caller is holding stale account state.
                if (auth.currentUser != null) {
                    requireCurrent(native, uid)
                    auth.signOut()
                }
                // Firebase has signed out even if a credential provider cannot clear its chooser state.
                val cleared = runCatching {
                    native.credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }.isSuccess
                if (auth.currentUser != null) throw changedUser()
                success().apply {
                    if (!cleared) put("message", "ออกจากบัญชีแล้ว แต่อาจต้องเลือกบัญชี Google เองในครั้งถัดไป")
                }
            }
            "loadSave" -> {
                val uid = requestedUid(request)
                requireCurrent(native, uid)
                val marker = native.firestore.collection("accountDeletions").document(uid).get(Source.SERVER).await()
                requireCurrent(native, uid)
                if (marker.exists()) throw deletionPending()
                val snapshot = native.firestore.collection("players").document(uid).get(Source.SERVER).await()
                requireCurrent(native, uid)
                success().put("save", snapshot.cloudSaveJson() ?: JSONObject.NULL)
            }
            "commitSave" -> commitSave(native, request)
            "deleteAccount" -> deleteAccount(native, requestedUid(request))
            else -> throw AccountFailure("invalid_data", "ไม่รู้จักคำสั่งบัญชีนี้")
        }
    }

    private suspend fun commitSave(native: FirebaseServices, request: JSONObject): JSONObject {
        val uid = requestedUid(request)
        requireCurrent(native, uid)
        val expected = when (val raw = request.opt("expectedRevision")) {
            is Int -> raw.toLong()
            is Long -> raw
            else -> throw invalidData()
        }.takeIf { it in 0L until MAX_REVISION } ?: throw invalidData()
        val payload = request.opt("payload") as? String ?: throw invalidData()
        validateSave(payload)
        val reference = native.firestore.collection("players").document(uid)
        val markerReference = native.firestore.collection("accountDeletions").document(uid)
        val revision = native.firestore.runTransaction { transaction ->
            requireCurrent(native, uid)
            if (transaction.get(markerReference).exists()) throw deletionPending()
            val snapshot = transaction.get(reference)
            val existing = snapshot.cloudSaveJson()
            val currentRevision = existing?.getLong("revision") ?: 0L
            if (currentRevision != expected) {
                throw AccountFailure("conflict", "สวนบนคลาวด์มีเวอร์ชันใหม่ กรุณาเลือกสวนก่อนซิงก์อีกครั้ง")
            }
            requireCurrent(native, uid)
            val next = expected + 1L
            transaction.set(reference, mapOf(
                "payload" to payload,
                "revision" to next,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
            next
        }.await()
        requireCurrent(native, uid)
        return success().put("save", JSONObject().put("payload", payload).put("revision", revision))
    }

    private suspend fun deleteAccount(native: FirebaseServices, uid: String): JSONObject {
        val user = requireCurrent(native, uid)
        // Always require a fresh credential. Picking another Google/Apple identity does not delete this one.
        val provider = user.providerData.map { it.providerId }
        val appleAccessToken = when {
            "apple.com" in provider -> {
                requireAppleEnabled()
                val result = user.startActivityForReauthenticateWithProvider(requireActivity(), appleProvider()).await()
                requireCurrent(native, uid)
                if (result.user?.uid != uid) throw changedUser()
                (result.credential as? OAuthCredential)?.accessToken?.takeIf { it.isNotBlank() }
                    ?: throw AccountFailure("reauth_required", "ไม่ได้รับสิทธิ์ Apple สำหรับเพิกถอน กรุณายืนยันบัญชีอีกครั้ง")
            }
            "google.com" in provider -> {
                val credential = googleCredential(native)
                requireCurrent(native, uid)
                user.reauthenticate(credential).await()
                requireCurrent(native, uid)
                null
            }
            else -> throw AccountFailure("reauth_required", "ต้องยืนยันตัวตนกับผู้ให้บริการเดิมก่อนลบบัญชี")
        }
        requireCurrent(native, uid)

        // From this point the operation may have reached the server even when the reply is lost.
        // The permanent UID tombstone prevents an old/offline device from recreating the deleted save.
        try {
            val reference = native.firestore.collection("players").document(uid)
            val markerReference = native.firestore.collection("accountDeletions").document(uid)
            native.firestore.runTransaction { transaction ->
                requireCurrent(native, uid)
                val marker = transaction.get(markerReference)
                if (!marker.exists()) {
                    transaction.set(markerReference, mapOf("requestedAt" to FieldValue.serverTimestamp()))
                }
                transaction.delete(reference)
                true
            }.await()
            requireCurrent(native, uid)
            if (appleAccessToken != null) {
                native.auth.revokeAccessToken(appleAccessToken).await()
                requireCurrent(native, uid)
            }
            user.delete().await()
            if (native.auth.currentUser?.uid == uid) native.auth.signOut()
            if (native.auth.currentUser != null) throw changedUser()
            runCatching { native.credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            if (native.auth.currentUser != null) throw changedUser()
            return success()
        } catch (_: Exception) {
            throw AccountFailure(
                "deletion_incomplete",
                "การลบบัญชีอาจสำเร็จบางส่วนแล้ว กรุณาเชื่อมต่ออินเทอร์เน็ตและลองลบบัญชีเดิมอีกครั้ง ห้ามอัปโหลดสวนนี้ใหม่",
            )
        }
    }

    private suspend fun googleCredential(native: FirebaseServices): com.google.firebase.auth.AuthCredential {
        val clientId = native.webClientId ?: throw AccountFailure("unavailable", "ยังไม่ได้ตั้งค่า Google Web Client ID")
        val nonceBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val base64Flags = android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
        val nonce = android.util.Base64.encodeToString(nonceBytes, base64Flags)
        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
        val response = native.credentialManager.getCredential(
            context = MutableContextWrapper(requireActivity()),
            request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = response.credential as? CustomCredential ?: throw invalidData()
        // Both Google option flows use this outer type. SIWG is a data-bundle subtype,
        // not a different response.type (GoogleIdTokenCredential API reference).
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw invalidData()
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        // Match this UI request first; Firebase subsequently verifies the token's signature and audience.
        // Parsing the nonce is not treated as authentication and no token is stored in the shared save.
        val claimsPart = token.split('.').takeIf { it.size == 3 }?.get(1) ?: throw invalidData()
        val claims = JSONObject(String(android.util.Base64.decode(claimsPart, base64Flags), Charsets.UTF_8))
        if (claims.optString("nonce") != nonce) throw invalidData()
        return GoogleAuthProvider.getCredential(token, null)
    }

    private fun appleProvider(): OAuthProvider = OAuthProvider.newBuilder("apple.com")
        .setScopes(listOf("email", "name"))
        .build()

    private fun requireAppleEnabled() {
        if (!BuildConfig.APPLE_SIGN_IN_ENABLED) {
            throw AccountFailure("unavailable", "ยังไม่ได้เปิดใช้ Apple Sign In สำหรับ Android")
        }
    }

    private fun requireActivity(): Activity = activityRef.get()
        ?.takeUnless { it.isFinishing || it.isDestroyed }
        ?: throw AccountFailure("cancelled", "หน้าต่างเข้าสู่ระบบถูกปิด กรุณาลองใหม่")

    private fun requireCurrent(native: FirebaseServices, uid: String): FirebaseUser = native.auth.currentUser
        ?.takeIf { it.uid == uid }
        ?: throw changedUser()

    private fun requestedUid(request: JSONObject): String = (request.opt("uid") as? String)
        ?.takeIf { it.isNotBlank() && it.length <= 128 && '/' !in it }
        ?: throw invalidData()

    private fun FirebaseUser.toPublicJson(): JSONObject = JSONObject()
        .put("uid", uid)
        .put("displayName", displayName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        .put("provider", when {
            providerData.any { it.providerId == "google.com" } -> "google"
            providerData.any { it.providerId == "apple.com" } -> "apple"
            else -> "unknown"
        })

    private fun DocumentSnapshot.cloudSaveJson(): JSONObject? {
        if (!exists()) return null
        val payload = get("payload") as? String ?: throw invalidData()
        val revision = (get("revision") as? Long)?.takeIf { it in 1..MAX_REVISION } ?: throw invalidData()
        validateSave(payload)
        return JSONObject().put("payload", payload).put("revision", revision)
    }

    private fun validateSave(payload: String) {
        if (payload.toByteArray(Charsets.UTF_8).size !in 1..MAX_PAYLOAD_BYTES || GameSaveCodec.decode(payload) == null) {
            throw invalidData()
        }
    }

    private fun errorResponse(error: Exception): JSONObject {
        // Firestore can wrap an exception thrown inside a transaction; preserve our conflict/deletion code.
        val known = generateSequence<Throwable>(error) { it.cause }.take(10).filterIsInstance<AccountFailure>().firstOrNull()
        if (known != null) return failure(known.code, known.message)
        val code = when (error) {
            is GetCredentialCancellationException -> "cancelled"
            is NoCredentialException -> "unavailable"
            is FirebaseNetworkException -> "network"
            is FirebaseAuthRecentLoginRequiredException -> "reauth_required"
            is FirebaseAuthInvalidUserException -> "unauthenticated"
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED -> "network"
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "unauthenticated"
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "permission"
                FirebaseFirestoreException.Code.INVALID_ARGUMENT -> "invalid_data"
                else -> "unknown"
            }
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_WEB_CONTEXT_CANCELED" -> "cancelled"
                "ERROR_OPERATION_NOT_ALLOWED", "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED" -> "unavailable"
                "ERROR_USER_MISMATCH", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_TOKEN_EXPIRED" -> "unauthenticated"
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "permission"
                else -> "unknown"
            }
            is org.json.JSONException, is IllegalArgumentException -> "invalid_data"
            else -> "unknown"
        }
        val message = when (code) {
            "cancelled" -> "ยกเลิกการเข้าสู่ระบบแล้ว"
            "network" -> "เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ สวนในเครื่องยังอยู่ กรุณาลองใหม่เมื่อมีอินเทอร์เน็ต"
            "unauthenticated" -> "บัญชีเปลี่ยนหรือหมดอายุ กรุณาเข้าสู่ระบบบัญชีเดิมอีกครั้ง"
            "permission" -> "บัญชีนี้ไม่มีสิทธิ์ดำเนินการ กรุณาตรวจผู้ให้บริการที่ใช้เข้าสู่ระบบและกฎ Firestore"
            "reauth_required" -> "กรุณายืนยันตัวตนใหม่ก่อนลบบัญชี"
            "invalid_data" -> "ข้อมูลบัญชีหรือสวนไม่ถูกต้อง ยังไม่มีการเขียนทับเซฟในเครื่อง"
            "unavailable" -> "ยังไม่พร้อมเข้าสู่ระบบ กรุณาตรวจ Firebase configuration และบัญชีบนอุปกรณ์"
            else -> "ดำเนินการบัญชีไม่สำเร็จ กรุณาลองใหม่"
        }
        return failure(code, message)
    }

    private fun success(): JSONObject = JSONObject().put("ok", true)
    private fun failure(code: String, message: String): JSONObject = JSONObject()
        .put("ok", false).put("code", code).put("message", message)
    private fun changedUser() = AccountFailure("unauthenticated", "บัญชีปัจจุบันเปลี่ยนแล้ว กรุณาเปิดหน้าบัญชีใหม่")
    private fun invalidData() = AccountFailure("invalid_data", "ข้อมูลสวนหรือคำสั่งไม่ถูกต้อง")
    private fun deletionPending() = AccountFailure("deletion_incomplete", "บัญชีนี้เริ่มลบแล้ว ต้องลบบัญชีให้เสร็จก่อน")
}
