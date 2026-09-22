package com.littlefarm.android

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.littlefarm.notifications.HarvestNotificationPlatform
import org.json.JSONObject

/** Runtime permission is requested only by the user's notification settings toggle. */
class AndroidHarvestNotifications(private val activity: ComponentActivity) : HarvestNotificationPlatform {
    private var permissionReply: ((String) -> Unit)? = null
    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionReply?.invoke(permissionJson())
        permissionReply = null
    }

    override fun request(operation: String, payload: String, completion: (String) -> Unit) {
        activity.runOnUiThread {
            try {
                when (operation) {
                    "permission" -> completion(permissionJson())
                    "requestPermission" -> {
                        HarvestAlarmScheduler.createChannel(activity)
                        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            if (permissionReply != null) { completion("""{"ok":false,"permission":"UNAVAILABLE"}"""); return@runOnUiThread }
                            permissionReply = completion
                            HarvestAlarmScheduler.preferences(activity).edit().putBoolean("permission_asked", true).apply()
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else completion(permissionJson())
                    }
                    "cancel" -> { HarvestAlarmScheduler.cancel(activity); completion("""{"ok":true}""") }
                    "schedule" -> {
                        require(payload.length < 1000)
                        val args = JSONObject(payload)
                        HarvestAlarmScheduler.schedule(activity, args.getLong("fireAtMillis"), args.getInt("cropCount"))
                        completion("""{"ok":true}""")
                    }
                    else -> completion("""{"ok":false}""")
                }
            } catch (_: Exception) { completion("""{"ok":false,"permission":"UNAVAILABLE"}""") }
        }
    }

    private fun permissionJson(): String {
        val permission = when {
            HarvestAlarmScheduler.allowed(activity) -> "AUTHORIZED"
            Build.VERSION.SDK_INT >= 33 && !HarvestAlarmScheduler.preferences(activity).getBoolean("permission_asked", false) -> "UNKNOWN"
            else -> "DENIED"
        }
        return JSONObject().put("ok", true).put("permission", permission).toString()
    }
}

/** A single inexact PendingIntent survives normal process death; no exact-alarm permission is used. */
internal object HarvestAlarmScheduler {
    private const val ACTION = "com.littlefarm.game.HARVEST_READY"
    private const val CHANNEL = "little_farm_harvest_v1"
    private const val REQUEST = 7081
    private const val NOTIFICATION = 7081
    private const val FINGERPRINT = "fingerprint"

    fun preferences(context: Context) = context.getSharedPreferences("little_farm_harvest_alarm_v1", Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!manager(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || manager(context).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, "ผักพร้อมเก็บเกี่ยว", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "แจ้งครั้งเดียวเมื่อผักรอบที่กำลังรอพร้อมเก็บ ไม่แจ้งซ้ำรายแปลง"
                setShowBadge(false)
            }
        )
    }

    private fun pending(context: Context, fingerprint: String? = null): PendingIntent {
        val intent = Intent(context, HarvestAlarmReceiver::class.java).setAction(ACTION)
        if (fingerprint != null) intent.putExtra(FINGERPRINT, fingerprint)
        return PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, fireAt: Long, count: Int) {
        require(fireAt > System.currentTimeMillis() && count in 1..1000)
        check(allowed(context))
        createChannel(context)
        val fingerprint = "$fireAt:$count"
        check(preferences(context).edit().putLong("fireAt", fireAt).putInt("count", count).putString(FINGERPRINT, fingerprint).commit())
        try {
            // AlarmManager.set is inexact and may be delayed by Doze/battery restrictions.
            alarms(context).set(AlarmManager.RTC_WAKEUP, fireAt, pending(context, fingerprint))
        } catch (error: Exception) { cancel(context); throw error }
    }

    fun cancel(context: Context) {
        alarms(context).cancel(pending(context))
        manager(context).cancel(NOTIFICATION)
        check(preferences(context).edit().remove("fireAt").remove("count").remove(FINGERPRINT).commit())
    }

    fun receive(context: Context, intent: Intent) {
        val prefs = preferences(context)
        val fireAt = prefs.getLong("fireAt", 0)
        val count = prefs.getInt("count", 0)
        val expected = prefs.getString(FINGERPRINT, null) ?: return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restore only still-future reminders. Do not replay an already-due alert on boot.
            if (fireAt > System.currentTimeMillis() && allowed(context)) schedule(context, fireAt, count)
            else cancel(context)
            return
        }
        if (intent.action != ACTION || intent.getStringExtra(FINGERPRINT) != expected) return
        if (!allowed(context)) { cancel(context); return }
        // Consume metadata before delivery, so a repeated broadcast cannot duplicate the alert.
        if (!prefs.edit().remove("fireAt").remove("count").remove(FINGERPRINT).commit()) return
        createChannel(context)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(context, REQUEST, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val notification = builder.setSmallIcon(R.drawable.ic_harvest_notification)
            .setContentTitle("ผักในสวนพร้อมเก็บแล้ว 🌱")
            .setContentText("ผักที่รอไว้ $count แปลงพร้อมแล้ว แวะกลับมาเมื่อสะดวกนะ")
            .setStyle(Notification.BigTextStyle().bigText("ผักที่รอไว้ $count แปลงพร้อมแล้ว แวะกลับมาเมื่อสะดวกนะ ไม่มีผักเหี่ยว"))
            .setContentIntent(contentIntent).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).build()
        manager(context).notify(NOTIFICATION, notification)
    }
}

class HarvestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // OS permission can change between scheduling and delivery; never crash a background receiver.
        runCatching { HarvestAlarmScheduler.receive(context, intent) }
    }
}
