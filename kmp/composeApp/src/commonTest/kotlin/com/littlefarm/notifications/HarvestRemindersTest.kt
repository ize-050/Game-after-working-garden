package com.littlefarm.notifications

import com.littlefarm.account.AccountKeyValueStore
import com.littlefarm.account.MemoryAccountStore
import com.littlefarm.game.CropType
import com.littlefarm.game.GameState
import com.littlefarm.game.Plot
import com.littlefarm.game.PlotStage
import kotlinx.serialization.json.*
import kotlin.test.*

class HarvestRemindersTest {
    private val now = 1_000_000L
    private fun garden() = GameState.initial(now).copy(plots = listOf(
        Plot(PlotStage.GROWING, CropType.LETTUCE, now + 120_000),
        Plot(PlotStage.GROWING, CropType.PUMPKIN, now + 1_200_000),
        Plot(PlotStage.GROWING, CropType.RADISH, now),
        Plot(PlotStage.PLANTED, CropType.CARROT), Plot(), Plot(),
    ))

    @Test fun groupsOnlyFutureWateredCropsAtTheLastReadyTime() {
        assertEquals(HarvestReminderPlan(now + 1_200_000, 2), HarvestReminderPlanner.plan(garden(), now))
    }

    @Test fun demoTimeSubtractsFromRealDeliveryAndAlreadyReadyCropsDoNotReplay() {
        assertEquals(HarvestReminderPlan(now + 900_000, 1), HarvestReminderPlanner.plan(garden().copy(demoOffsetMillis = 300_000), now))
        assertNull(HarvestReminderPlanner.plan(garden().copy(demoOffsetMillis = Long.MAX_VALUE), now))
        assertNull(HarvestReminderPlanner.plan(garden(), now + 1_200_000))
    }

    @Test fun launchNeverPromptsAndDisabledNeverSchedules() {
        val native = FakePlatform()
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        assertEquals(0, native.prompts)
        assertNull(native.pending)
        assertFalse(controller.snapshot.value.enabled)
    }

    @Test fun explicitOptInRequestsPermissionAndCommitsDevicePreferenceBeforeScheduling() {
        val store = MemoryAccountStore()
        store.write("unrelated-game", "untouched")
        val native = FakePlatform()
        val controller = HarvestReminderController(store, native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        assertEquals(1, native.prompts)
        assertEquals("true", store.read(HarvestReminderController.PREFERENCE_KEY))
        assertEquals(HarvestReminderPlan(now + 1_200_000, 2), native.pending)
        assertEquals(2, controller.snapshot.value.scheduledCount)
        assertEquals("untouched", store.read("unrelated-game"))
    }

    @Test fun deniedPermissionDoesNotPretendNotificationsAreEnabled() {
        val native = FakePlatform().apply { requestedPermission = "DENIED" }
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        assertFalse(controller.snapshot.value.enabled)
        assertEquals(HarvestPermission.DENIED, controller.snapshot.value.permission)
        assertNull(native.pending)
        assertNotNull(controller.snapshot.value.message)
    }

    @Test fun repeatedStateDoesNotRescheduleAndChangesReplaceSingleAggregate() {
        val native = FakePlatform()
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        val schedules = native.schedules
        repeat(3) { controller.updateGarden("guest", garden()) }
        assertEquals(schedules, native.schedules)
        controller.updateGarden("guest", garden().copy(demoOffsetMillis = 300_000))
        assertEquals(HarvestReminderPlan(now + 900_000, 1), native.pending)
        assertEquals(schedules + 1, native.schedules)
    }

    @Test fun accountChangeConflictResetAndDisableCancelPreviousGarden() {
        val native = FakePlatform()
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        val before = native.cancels
        controller.updateGarden("different-uid", garden())
        assertTrue(native.cancels > before)
        controller.updateGarden("different-uid", garden(), blocked = true)
        assertNull(native.pending)
        controller.updateGarden("different-uid", garden())
        assertNotNull(native.pending)
        controller.clearGarden()
        assertNull(native.pending)
        controller.updateGarden("guest", garden())
        controller.setEnabled(false)
        assertNull(native.pending)
        assertEquals(0, controller.snapshot.value.scheduledCount)
    }

    @Test fun permissionRevokedInSystemSettingsCancelsWithoutPromptingAgain() {
        val native = FakePlatform()
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        native.currentPermission = "DENIED"
        controller.refreshPermission()
        assertNull(native.pending)
        assertEquals(1, native.prompts)
        assertEquals(HarvestPermission.DENIED, controller.snapshot.value.permission)
    }

    @Test fun preferenceSurvivesRelaunchButNoPermissionDialogOrPastDueReplay() {
        val store = MemoryAccountStore()
        val native = FakePlatform()
        val first = HarvestReminderController(store, native) { now }
        first.updateGarden("guest", garden())
        first.setEnabled(true)
        first.close()
        assertNotNull(native.pending) // App shutdown is not user opt-out.
        val reopened = HarvestReminderController(store, native) { now + 1_200_000 }
        reopened.updateGarden("guest", garden())
        assertTrue(reopened.snapshot.value.enabled)
        assertEquals(1, native.prompts)
        assertNull(native.pending)
    }

    @Test fun storageFailureDoesNotEnableAndScheduleFailureIsVisible() {
        val store = object : AccountKeyValueStore {
            override fun read(key: String): String? = null
            override fun write(key: String, value: String) { error("disk full") }
            override fun remove(key: String) = Unit
        }
        val native = FakePlatform()
        val controller = HarvestReminderController(store, native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        assertFalse(controller.snapshot.value.enabled)
        assertNull(native.pending)
        assertNotNull(controller.snapshot.value.message)

        native.failSchedule = true
        val other = HarvestReminderController(MemoryAccountStore(), native) { now }
        other.updateGarden("guest", garden())
        other.setEnabled(true)
        assertEquals(0, other.snapshot.value.scheduledCount)
        assertNotNull(other.snapshot.value.message)
    }

    @Test fun delayedPermissionReplyCannotUndoSubsequentOptOut() {
        val native = FakePlatform().apply { delayPermission = true }
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        assertTrue(controller.snapshot.value.busy)
        controller.setEnabled(false)
        native.replyPermission?.invoke("""{"ok":true,"permission":"AUTHORIZED"}""")
        assertFalse(controller.snapshot.value.enabled)
        assertFalse(controller.snapshot.value.busy)
        assertNull(native.pending)
    }

    @Test fun unavailablePreviewNeverClaimsRealNotificationSupport() {
        val controller = HarvestReminderController(MemoryAccountStore(), UnavailableHarvestNotificationPlatform()) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        assertEquals(HarvestPermission.UNAVAILABLE, controller.snapshot.value.permission)
        assertFalse(controller.snapshot.value.enabled)
        assertEquals(0, controller.snapshot.value.scheduledCount)
    }

    @Test fun delayedScheduleCompletionCannotResurrectCancelledStatus() {
        val native = FakePlatform().apply { delaySchedule = true }
        val controller = HarvestReminderController(MemoryAccountStore(), native) { now }
        controller.updateGarden("guest", garden())
        controller.setEnabled(true)
        assertNotNull(native.pending)
        controller.clearGarden()
        native.replySchedule?.invoke("""{"ok":true}""")
        assertNull(native.pending)
        assertEquals(0, controller.snapshot.value.scheduledCount)
    }

    private class FakePlatform : HarvestNotificationPlatform {
        var currentPermission = "UNKNOWN"
        var requestedPermission = "AUTHORIZED"
        var prompts = 0
        var schedules = 0
        var cancels = 0
        var pending: HarvestReminderPlan? = null
        var failSchedule = false
        var delayPermission = false
        var replyPermission: ((String) -> Unit)? = null
        var delaySchedule = false
        var replySchedule: ((String) -> Unit)? = null
        override fun request(operation: String, payload: String, completion: (String) -> Unit) {
            when (operation) {
                "permission" -> completion("""{"ok":true,"permission":"$currentPermission"}""")
                "requestPermission" -> {
                    prompts++
                    if (delayPermission) { replyPermission = completion; return }
                    currentPermission = requestedPermission
                    completion("""{"ok":true,"permission":"$currentPermission"}""")
                }
                "cancel" -> { cancels++; pending = null; completion("""{"ok":true}""") }
                "schedule" -> {
                    schedules++
                    if (failSchedule) { completion("""{"ok":false}"""); return }
                    val args = Json.parseToJsonElement(payload).jsonObject
                    pending = HarvestReminderPlan(args.getValue("fireAtMillis").jsonPrimitive.long, args.getValue("cropCount").jsonPrimitive.int)
                    if (delaySchedule) replySchedule = completion else completion("""{"ok":true}""")
                }
            }
        }
    }
}
