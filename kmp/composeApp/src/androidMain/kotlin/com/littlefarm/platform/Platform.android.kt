package com.littlefarm.platform

import android.content.Context
import com.littlefarm.account.AccountKeyValueStore

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

class AndroidSaveStore(context: Context) : SaveStore {
    private val preferences = context.getSharedPreferences("little_farm_v1", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("save", null)
    override fun write(value: String) {
        check(preferences.edit().putString("save", value).commit()) { "Unable to save the farm" }
    }
}

/** Account metadata and UID-scoped save caches; authentication tokens stay in the native SDK. */
class AndroidAccountStore(context: Context) : AccountKeyValueStore {
    private val preferences = context.getSharedPreferences("little_farm_accounts_v1", Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) { "Unable to save account data" }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) { "Unable to remove account data" }
    }
}
