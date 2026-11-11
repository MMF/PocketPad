package com.mfouad.mobiletrackpad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mfouad.mobiletrackpad.bluetooth.HidSessionController
import com.mfouad.mobiletrackpad.hid.HostProfiles
import com.mfouad.mobiletrackpad.session.Phase
import com.mfouad.mobiletrackpad.ui.TRACKPAD_SURFACE_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Milestone 4 and 5 features through the real UI, against the debug-only simulated computer.
 * No Bluetooth link is involved.
 */
@RunWith(AndroidJUnit4::class)
class FeaturesTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val controller get() = AppGraph.controller
    private val store get() = AppGraph.settingsStore
    private var baseline = 0
    private val newReports get() = recordedReports.drop(baseline)

    private fun click(id: Int) {
        rule.onNodeWithText(rule.string(id)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun clickText(text: String) {
        rule.onNodeWithText(text).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun resetAll() {
        store.resetSettings()
        HostProfiles.ids.forEach(store::resetProfile)
        AppGraph.hostStore.setProfile(HidSessionController.SIMULATED_ADDRESS, HostProfiles.CUSTOM)
        Thread.sleep(300)
    }

    @Before
    fun start() {
        rule.passOnboardingIfShown()
        controller.stop()
        rule.waitFor("idle") { !controller.state.value.isActive }
        resetAll()
    }

    @After
    fun stop() {
        controller.stop()
        resetAll()
    }

    private fun startSimulatedAndOpenTrackpad() {
        click(R.string.action_simulate)
        rule.waitFor("connected") { controller.state.value.phase == Phase.CONNECTED && recordedReports.size >= 3 }
        click(R.string.action_open_trackpad)
        rule.onNodeWithTag(TRACKPAD_SURFACE_TAG).assertExists()
        baseline = recordedReports.size
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        rule.onNodeWithTag(TRACKPAD_SURFACE_TAG).performTouchInput(block)
        rule.waitForIdle()
    }

    private fun setting(
        update: (com.mfouad.mobiletrackpad.settings.Settings) -> com.mfouad.mobiletrackpad.settings.Settings,
    ) {
        store.update(update)
        rule.waitFor("setting saved") {
            runBlocking { store.settings.first() } ==
                update(runBlocking { store.settings.first() })
        }
    }

    // --- onboarding -----------------------------------------------------------------------

    @Test
    fun setupGuideWalksThroughAllPagesAndRunsTheCheck() {
        click(R.string.action_settings)
        click(R.string.settings_setup_guide)
        assertTrue(rule.exists(rule.string(R.string.onboarding_title_welcome)))
        click(R.string.action_next)
        assertTrue(rule.exists(rule.string(R.string.onboarding_title_permissions)))
        click(R.string.action_next)
        click(R.string.onboarding_run_check)
        rule.waitFor("check result") {
            !rule.exists(rule.string(R.string.check_pending)) &&
                rule.exists(rule.string(R.string.check_adapter))
        }
        click(R.string.action_next)
        assertTrue(rule.exists(rule.string(R.string.onboarding_title_pairing)))
        click(R.string.action_done)
        assertTrue(rule.exists(rule.string(R.string.action_enable)))
    }

    // --- settings ----------------------------------------------------------------------------

    @Test
    fun reverseScrollSettingIsSavedAndApplied() {
        click(R.string.action_settings)
        click(R.string.settings_reverse_scroll)
        rule.waitFor("saved") { runBlocking { store.settings.first().reverseScroll } }
        rule.activity.onBackPressedDispatcher.let { rule.runOnUiThread { it.onBackPressed() } }
        startSimulatedAndOpenTrackpad()
        touch {
            down(0, center)
            down(1, center + Offset(60.dp.toPx(), 0f))
            repeat(10) {
                updatePointerBy(0, Offset(0f, -10.dp.toPx()))
                updatePointerBy(1, Offset(0f, -10.dp.toPx()))
                move()
            }
            advanceEventTime(200)
            move()
            up(0)
            up(1)
        }
        rule.settleReports()
        // Fingers up normally scroll wheel up (+5); reversed it is -5.
        assertEquals(-5, newReports.filter { it.id == 2 }.sumOf { it.payload[5].toInt() })
    }

    // --- shortcuts by profile ---------------------------------------------------------------

    private fun pinchOut() =
        touch {
            down(0, center - Offset(30.dp.toPx(), 0f))
            down(1, center + Offset(30.dp.toPx(), 0f))
            repeat(12) {
                updatePointerBy(0, Offset(-8.dp.toPx(), 0f))
                updatePointerBy(1, Offset(8.dp.toPx(), 0f))
                move()
            }
            up(0)
            up(1)
        }

    private fun swipe3(dx: Float) =
        touch {
            down(0, center)
            down(1, center + Offset(40.dp.toPx(), 0f))
            down(2, center + Offset(80.dp.toPx(), 0f))
            repeat(10) {
                (0..2).forEach { id -> updatePointerBy(id, Offset(dx / 10, 0f)) }
                move()
            }
            up(0)
            up(1)
            up(2)
        }

    @Test
    fun pinchOutWithWindowsProfileSendsControlPlusWheelUp() {
        AppGraph.hostStore.setProfile(HidSessionController.SIMULATED_ADDRESS, HostProfiles.WINDOWS)
        startSimulatedAndOpenTrackpad()
        rule.waitFor("profile") { controller.input.activeProfile.value.id == HostProfiles.WINDOWS }
        pinchOut()
        rule.settleReports()
        val hexes = newReports.map(::hex)
        assertTrue("1: 01 00 00 00 00 00 00 00" in hexes, "Ctrl pressed: $hexes")
        assertTrue(newReports.filter { it.id == 2 }.sumOf { it.payload[5].toInt() } >= 1, "wheel up: $hexes")
        assertEquals("1: 00 00 00 00 00 00 00 00", hexes.last { it.startsWith("1:") }, "Ctrl released")
        assertTrue(newReports.none { it.id == 2 && it.payload[0].toInt() != 0 }, "no clicks")
    }

    @Test
    fun threeFingerSwipeLeftWithMacProfileMovesToTheNextSpace() {
        AppGraph.hostStore.setProfile(HidSessionController.SIMULATED_ADDRESS, HostProfiles.MACOS)
        startSimulatedAndOpenTrackpad()
        rule.waitFor("profile") { controller.input.activeProfile.value.id == HostProfiles.MACOS }
        swipe3(dx = -150f * rule.density.density)
        rule.settleReports()
        assertEquals(
            listOf(
                "1: 01 00 00 00 00 00 00 00",
                "1: 01 00 4F 00 00 00 00 00",
                "1: 01 00 00 00 00 00 00 00",
                "1: 00 00 00 00 00 00 00 00",
            ),
            newReports.map(::hex),
        )
    }

    // --- mapping editor ----------------------------------------------------------------------

    @Test
    fun mappingEditorChangeIsSavedAndUsed() {
        click(R.string.action_settings)
        click(R.string.settings_mappings)
        clickText(HostProfiles.default(HostProfiles.CUSTOM).name)
        click(R.string.slot_swipe_right)
        click(R.string.action_type_media)
        click(R.string.media_play_pause)
        rule.onNodeWithText(rule.string(R.string.action_save)).performClick()
        rule.waitFor("profile saved") {
            runBlocking { store.profiles.first() }
                .first { it.id == HostProfiles.CUSTOM }
                .actionFor(com.mfouad.mobiletrackpad.hid.GestureSlot.SWIPE3_RIGHT) ==
                com.mfouad.mobiletrackpad.hid.Action
                    .Consumer(com.mfouad.mobiletrackpad.hid.ConsumerUsage.PLAY_PAUSE)
        }
        // Editor -> profile list -> settings -> main screen.
        repeat(3) {
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.waitFor("main screen") { rule.exists(rule.string(R.string.action_simulate)) }
        startSimulatedAndOpenTrackpad()
        swipe3(dx = 150f * rule.density.density)
        rule.settleReports()
        assertEquals(listOf("3: CD 00", "3: 00 00"), newReports.map(::hex))
    }

    // --- trackpad extras ------------------------------------------------------------------------

    @Test
    fun onScreenButtonsHoldUntilReleased() {
        setting { it.copy(onScreenButtons = true) }
        startSimulatedAndOpenTrackpad()
        rule.onNodeWithContentDescription(rule.string(R.string.trackpad_button_secondary)).performTouchInput {
            down(center)
            advanceEventTime(300)
        }
        rule.waitFor("right button down") { newReports.any { hex(it) == "2: 02 00 00 00 00 00 00" } }
        rule.onNodeWithContentDescription(rule.string(R.string.trackpad_button_secondary)).performTouchInput { up() }
        rule.waitFor("released") { newReports.lastOrNull()?.let(::hex) == "2: 00 00 00 00 00 00 00" }
    }

    @Test
    fun dragLockHoldsAfterLiftAndBannerReleasesIt() {
        setting { it.copy(dragLock = true) }
        startSimulatedAndOpenTrackpad()
        touch {
            down(center)
            advanceEventTime(60)
            up()
            advanceEventTime(120)
            down(center)
            repeat(5) { moveBy(Offset(10.dp.toPx(), 0f)) }
            up()
        }
        rule.waitFor("banner") { rule.exists(rule.string(R.string.trackpad_drag_locked)) }
        rule.settleReports()
        assertEquals(1, newReports.last { it.id == 2 }.payload[0].toInt(), "button still held after lifting")
        rule.onNodeWithText(rule.string(R.string.trackpad_release)).performClick()
        rule.waitFor("released") { newReports.last { it.id == 2 }.payload[0].toInt() == 0 }
        rule.waitFor("banner gone") { !rule.exists(rule.string(R.string.trackpad_drag_locked)) }
    }
}
