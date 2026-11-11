package com.mfouad.pocketpad.settings

import com.mfouad.pocketpad.gesture.GestureConfig
import com.mfouad.pocketpad.hid.ReportSender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsMappingTest {
    private val density = 2f

    @Test
    fun defaultsMatchTheDocumentedBaseline() {
        val p = SettingsMapping.pointerSettings(Settings(), density)
        assertEquals(0.5f, p.countsPerPx) // 1 count per dp
        assertEquals(40f, p.pxPerDetent) // 20 dp per detent
        assertEquals(0f, p.acceleration)
        assertFalse(p.reverseScroll)
        assertEquals(ReportSender.DEFAULT_INTERVAL_NANOS, SettingsMapping.sendIntervalNanos(Settings()))
        val g = SettingsMapping.gestureConfig(Settings(), density)
        assertTrue(g.tapToClick && g.inertia && !g.dragLock)
    }

    @Test
    fun speedsScaleAndAreClamped() {
        val p = SettingsMapping.pointerSettings(Settings(pointerSpeed = 2f, scrollSpeed = 2f), density)
        assertEquals(1f, p.countsPerPx)
        assertEquals(20f, p.pxPerDetent)
        val extreme = SettingsMapping.pointerSettings(Settings(pointerSpeed = 50f, scrollSpeed = 0f), density)
        assertEquals(0.5f * Settings.MAX_SPEED, extreme.countsPerPx)
        assertEquals(40f / Settings.MIN_SPEED, extreme.pxPerDetent)
    }

    @Test
    fun togglesReachTheRecognizer() {
        val g =
            SettingsMapping.gestureConfig(
                Settings(tapToClick = false, dragLock = true, scrollInertia = false),
                density,
            )
        assertFalse(g.tapToClick)
        assertTrue(g.dragLock)
        assertFalse(g.inertia)
    }

    @Test
    fun batterySaverLowersReportRateAndDisablesHaptics() {
        val s = Settings(batterySaver = true, haptics = true)
        assertEquals(SettingsMapping.BATTERY_SAVER_INTERVAL_NANOS, SettingsMapping.sendIntervalNanos(s))
        assertEquals(
            SettingsMapping.BATTERY_SAVER_INTERVAL_NANOS,
            SettingsMapping.gestureConfig(s, density).inertiaTickNanos,
        )
        assertFalse(s.effectiveHaptics)
        assertEquals(
            GestureConfig.DEFAULT_INERTIA_TICK,
            SettingsMapping.gestureConfig(Settings(), density).inertiaTickNanos,
        )
    }
}
