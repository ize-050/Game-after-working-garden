package com.littlefarm.account

/** Native SDK boundary. Tokens stay in the platform SDK; only public account/save JSON crosses it. */
interface AccountPlatform {
    fun availability(): String
    fun request(operation: String, payload: String, completion: (String) -> Unit)
}

/** Namespaced local caches. Never store OAuth credentials in this store. */
interface AccountKeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

class MemoryAccountStore : AccountKeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** Guest/preview builds must never pretend a remote account or upload succeeded. */
class UnavailableAccountPlatform : AccountPlatform {
    override fun availability() = """{"providers":[],"message":"ยังไม่ได้เชื่อม Firebase — เล่นแบบ Guest และบันทึกในเครื่องได้"}"""
    override fun request(operation: String, payload: String, completion: (String) -> Unit) {
        completion("""{"ok":false,"code":"unavailable"}""")
    }
}
