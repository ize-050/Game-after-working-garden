package com.littlefarm

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import com.littlefarm.account.AccountPlatform
import com.littlefarm.account.GardenSession
import com.littlefarm.account.UnavailableAccountPlatform
import com.littlefarm.platform.IosAccountStore
import com.littlefarm.platform.IosSaveStore
import com.littlefarm.platformfeedback.createIosFeedback

fun MainViewController(accountPlatform: AccountPlatform = UnavailableAccountPlatform()) = ComposeUIViewController {
    val guestStore = remember { IosSaveStore() }
    val feedback = remember { createIosFeedback() }
    DisposableEffect(feedback) { onDispose { feedback.close() } }
    val session = remember(accountPlatform) {
        GardenSession(guestStore = guestStore, accountStore = IosAccountStore(), platform = accountPlatform)
    }
    App(saveStore = guestStore, session = session, feedback = feedback)
}
