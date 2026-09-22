package com.littlefarm.platform

import com.littlefarm.account.AccountKeyValueStore
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000).toLong()

class IosSaveStore : SaveStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(): String? = defaults.stringForKey("little_farm_v1")
    override fun write(value: String) { defaults.setObject(value, forKey = "little_farm_v1") }
}

/** Only save data and account metadata belong here. Provider tokens stay in the native SDK. */
class IosAccountStore : AccountKeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun namespaced(key: String) = "little_farm_account_v1.$key"
    override fun read(key: String): String? = defaults.stringForKey(namespaced(key))
    override fun write(key: String, value: String) { defaults.setObject(value, forKey = namespaced(key)) }
    override fun remove(key: String) { defaults.removeObjectForKey(namespaced(key)) }
}
