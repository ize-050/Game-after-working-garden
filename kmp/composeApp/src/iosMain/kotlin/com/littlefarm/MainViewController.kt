@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.littlefarm

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import com.littlefarm.notifications.HarvestNotificationPlatform
import com.littlefarm.notifications.HarvestReminderController
import com.littlefarm.notifications.UnavailableHarvestNotificationPlatform
import com.littlefarm.account.AccountPlatform
import com.littlefarm.account.GardenSession
import com.littlefarm.account.UnavailableAccountPlatform
import com.littlefarm.platform.IosAccountStore
import com.littlefarm.platform.IosSaveStore
import com.littlefarm.platformfeedback.createIosFeedback
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification

fun MainViewController(accountPlatform: AccountPlatform = UnavailableAccountPlatform(),
    notificationPlatform: HarvestNotificationPlatform = UnavailableHarvestNotificationPlatform()) = ComposeUIViewController {
    val guestStore = remember { IosSaveStore() }
    val feedback = remember { createIosFeedback() }
    DisposableEffect(feedback) { onDispose { feedback.close() } }
    val reminders = remember(notificationPlatform) { HarvestReminderController(IosAccountStore(), notificationPlatform) }
    DisposableEffect(reminders) {
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            reminders.refreshPermission()
        }
        onDispose { center.removeObserver(token); reminders.close() }
    }
    val session = remember(accountPlatform) {
        GardenSession(guestStore = guestStore, accountStore = IosAccountStore(), platform = accountPlatform)
    }
    App(saveStore = guestStore, session = session, feedback = feedback, reminders = reminders)
}
