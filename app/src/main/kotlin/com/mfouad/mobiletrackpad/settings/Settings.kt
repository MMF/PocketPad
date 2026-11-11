package com.mfouad.mobiletrackpad.settings

import com.mfouad.mobiletrackpad.gesture.GestureConfig
import com.mfouad.mobiletrackpad.hid.PointerSettings
import com.mfouad.mobiletrackpad.hid.ReportSender

/** User settings. Pure data; stored by SettingsStore. */
data class Settings(
    /** Pointer speed multiplier, [MIN_SPEED]..[MAX_SPEED]. */
    val pointerSpeed: Float = 1f,
    /** Extra speed for fast strokes, 0..1. Off by default: hosts add their own acceleration. */
    val acceleration: Float = 0f,
    /** Scroll speed multiplier, [MIN_SPEED]..[MAX_SPEED]. */
    val scrollSpeed: Float = 1f,
    /** Invert scrolling relative to the host's own direction setting. */
    val reverseScroll: Boolean = false,
    val scrollInertia: Boolean = true,
    val tapToClick: Boolean = true,
    val dragLock: Boolean = false,
    val haptics: Boolean = true,
    val onScreenButtons: Boolean = false,
    val leftHanded: Boolean = false,
    val batterySaver: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    /** Haptics are off while battery saving is on. */
    val effectiveHaptics: Boolean get() = haptics && !batterySaver

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
    }
}

/** How settings become recognizer, mapper and sender configuration. */
object SettingsMapping {
    /** Send interval while battery saving: about 90 reports per second instead of 125. */
    const val BATTERY_SAVER_INTERVAL_NANOS = 11_000_000L

    /** Finger travel per wheel detent at scroll speed 1, in dp. */
    private const val DP_PER_DETENT = 20f

    fun gestureConfig(
        s: Settings,
        pxPerDp: Float,
    ): GestureConfig =
        GestureConfig.forDensity(pxPerDp).copy(
            tapToClick = s.tapToClick,
            dragLock = s.dragLock,
            inertia = s.scrollInertia,
            inertiaTickNanos = if (s.batterySaver) BATTERY_SAVER_INTERVAL_NANOS else GestureConfig.DEFAULT_INERTIA_TICK,
        )

    fun pointerSettings(
        s: Settings,
        pxPerDp: Float,
    ): PointerSettings =
        PointerSettings.forDensity(pxPerDp).let { base ->
            base.copy(
                countsPerPx = base.countsPerPx * s.pointerSpeed.coerceIn(Settings.MIN_SPEED, Settings.MAX_SPEED),
                acceleration = s.acceleration.coerceIn(0f, 1f),
                pxPerDetent = DP_PER_DETENT * pxPerDp / s.scrollSpeed.coerceIn(Settings.MIN_SPEED, Settings.MAX_SPEED),
                reverseScroll = s.reverseScroll,
            )
        }

    fun sendIntervalNanos(s: Settings): Long =
        if (s.batterySaver) BATTERY_SAVER_INTERVAL_NANOS else ReportSender.DEFAULT_INTERVAL_NANOS
}
