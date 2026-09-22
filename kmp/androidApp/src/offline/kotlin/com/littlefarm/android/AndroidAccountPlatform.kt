package com.littlefarm.android

import android.app.Activity
import com.littlefarm.account.AccountPlatform
import org.json.JSONArray
import org.json.JSONObject

/** Intentionally unavailable, never a simulated login or a simulated cloud save. */
fun createAndroidAccountPlatform(activity: Activity): AccountPlatform = object : AccountPlatform {
    private val message = "ยังไม่ได้เชื่อม Firebase เล่นแบบ Guest ได้ตามปกติ ดูขั้นตอนใน androidApp/FIREBASE-SETUP.md"

    override fun availability(): String = JSONObject()
        .put("providers", JSONArray())
        .put("message", message)
        .toString()

    override fun request(operation: String, payload: String, completion: (String) -> Unit) {
        completion(JSONObject().put("ok", false).put("code", "unavailable").put("message", message).toString())
    }
}
