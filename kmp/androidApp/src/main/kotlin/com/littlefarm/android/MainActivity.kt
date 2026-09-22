package com.littlefarm.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.littlefarm.App
import com.littlefarm.account.GardenSession
import com.littlefarm.platform.AndroidAccountStore
import com.littlefarm.platform.AndroidSaveStore
import com.littlefarm.feedback.GameFeedbackController
import com.littlefarm.platformfeedback.createAndroidFeedback
import com.littlefarm.notifications.HarvestReminderController

class MainActivity : ComponentActivity() {
    private var feedback: GameFeedbackController? = null
    private var reminders: HarvestReminderController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val saveStore = AndroidSaveStore(applicationContext)
        val gameFeedback = createAndroidFeedback(applicationContext)
        feedback = gameFeedback
        val harvestReminders = HarvestReminderController(AndroidAccountStore(applicationContext), AndroidHarvestNotifications(this))
        reminders = harvestReminders
        val session = GardenSession(
            guestStore = saveStore,
            accountStore = AndroidAccountStore(applicationContext),
            platform = createAndroidAccountPlatform(this),
        )
        setContent { App(saveStore, session = session, feedback = gameFeedback, reminders = harvestReminders) }
    }

    override fun onResume() {
        super.onResume()
        reminders?.refreshPermission()
    }

    override fun onStart() {
        super.onStart()
        feedback?.setActive(true)
    }

    override fun onStop() {
        feedback?.setActive(false)
        super.onStop()
    }

    override fun onDestroy() {
        feedback?.close()
        feedback = null
        reminders?.close()
        reminders = null
        super.onDestroy()
    }
}
