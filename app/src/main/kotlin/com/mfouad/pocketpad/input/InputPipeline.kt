package com.mfouad.pocketpad.input

import com.mfouad.pocketpad.diagnostics.DiagnosticsLog
import com.mfouad.pocketpad.diagnostics.DiagnosticsLog.Category
import com.mfouad.pocketpad.engine.Engine
import com.mfouad.pocketpad.gesture.GestureConfig
import com.mfouad.pocketpad.gesture.GestureEvent
import com.mfouad.pocketpad.gesture.GestureRecognizer
import com.mfouad.pocketpad.gesture.PointerFrame
import com.mfouad.pocketpad.hid.ActionCodec
import com.mfouad.pocketpad.hid.HostProfile
import com.mfouad.pocketpad.hid.HostProfiles
import com.mfouad.pocketpad.hid.MouseButton
import com.mfouad.pocketpad.hid.PointerSettings
import com.mfouad.pocketpad.hid.ReportSender
import com.mfouad.pocketpad.hid.TrackpadMapper
import com.mfouad.pocketpad.session.TestAction
import com.mfouad.pocketpad.session.TestInput
import com.mfouad.pocketpad.settings.Settings
import com.mfouad.pocketpad.settings.SettingsMapping
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything between touch input and the report sender: gesture recognition, mapping through
 * the active host profile, settings, the test inputs and on-screen buttons.
 *
 * Public methods may be called from any thread; work runs on the engine thread. Input is
 * ignored while no host is connected, so nothing is queued or replayed.
 */
class InputPipeline(
    private val engine: Engine,
    private val sender: ReportSender,
    private val log: DiagnosticsLog,
    private val pxPerDp: Float,
) {
    private val testInput = TestInput(sender, engine.scheduler, log)

    private val feedbackFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Emits on each click the user caused (tap, multi-finger tap, on-screen button), for haptics. */
    val clickFeedback: SharedFlow<Unit> = feedbackFlow.asSharedFlow()

    private val dragLockFlow = MutableStateFlow(false)

    /** True while drag lock is holding the left button. */
    val dragLocked: StateFlow<Boolean> = dragLockFlow.asStateFlow()

    private val activeProfileFlow = MutableStateFlow(HostProfiles.default(HostProfiles.CUSTOM))

    /** The profile used for the connected (or last) host. */
    val activeProfile: StateFlow<HostProfile> = activeProfileFlow.asStateFlow()

    private var connected = false
    private var hostAddress: String? = null
    private var profiles: List<HostProfile> = HostProfiles.defaults
    private var assignments: Map<String, String> = emptyMap()

    private val mapper =
        TrackpadMapper(
            sender,
            PointerSettings.forDensity(pxPerDp),
            onShortcut = { recognizer.stopInertia() },
            onShortcutResult = { slot, action, accepted ->
                log.log(Category.GESTURE) {
                    "$slot -> ${ActionCodec.encode(
                        action,
                    )}${if (accepted) "" else " (not sent: unavailable or queue full)"}"
                }
            },
        )

    private val recognizer: GestureRecognizer =
        GestureRecognizer(GestureConfig.forDensity(pxPerDp), engine.scheduler, ::onGesture).apply {
            onModeChange = { change -> log.log(Category.GESTURE) { "mode $change" } }
        }

    // --- any thread -------------------------------------------------------------------------

    fun onTouchFrames(frames: List<PointerFrame>) =
        engine.post {
            if (connected) {
                frames.forEach(recognizer::onFrame)
                dragLockFlow.value = recognizer.isDragLocked
            }
        }

    /** The touch surface stopped being active: cancel gestures and inertia, release everything. */
    fun pause() =
        engine.post {
            recognizer.cancel()
            testInput.cancel()
            sender.releaseAll()
            dragLockFlow.value = false
        }

    /** Releases a drag-locked button (the obvious way out of drag lock). */
    fun releaseDragLock() =
        engine.post {
            recognizer.cancel()
            dragLockFlow.value = false
        }

    fun pressButton(button: MouseButton) =
        engine.post {
            if (connected) {
                recognizer.stopInertia()
                sender.press(button)
                feedbackFlow.tryEmit(Unit)
            }
        }

    fun releaseButton(button: MouseButton) = engine.post { if (connected) sender.release(button) }

    fun runTest(action: TestAction) = engine.post { if (connected) testInput.run(action) }

    /** Releases everything held by the test screen. */
    fun releaseAll() =
        engine.post {
            testInput.cancel()
            sender.releaseAll()
        }

    fun applySettings(s: Settings) =
        engine.post {
            recognizer.config = SettingsMapping.gestureConfig(s, pxPerDp)
            mapper.settings = SettingsMapping.pointerSettings(s, pxPerDp)
            sender.intervalNanos = SettingsMapping.sendIntervalNanos(s)
            if (!s.dragLock && recognizer.isDragLocked) {
                recognizer.cancel()
                dragLockFlow.value = false
            }
        }

    fun setProfiles(
        all: List<HostProfile>,
        hostAssignments: Map<String, String>,
    ) = engine.post {
        profiles = all
        assignments = hostAssignments
        updateActiveProfile()
    }

    // --- engine thread only -------------------------------------------------------------------

    /** Called by the session controller on the engine thread when the link changes. */
    fun onLinkChanged(
        isConnected: Boolean,
        address: String?,
    ) {
        connected = isConnected
        if (address != null) hostAddress = address
        if (!isConnected) {
            recognizer.cancel()
            testInput.cancel()
            dragLockFlow.value = false
        }
        updateActiveProfile()
    }

    private fun updateActiveProfile() {
        val id = hostAddress?.let { assignments[it] } ?: HostProfiles.CUSTOM
        val profile = profiles.firstOrNull { it.id == id } ?: HostProfiles.default(id)
        mapper.profile = profile
        activeProfileFlow.value = profile
    }

    private fun onGesture(e: GestureEvent) {
        // Continuous events are too frequent to log; the reports and shortcuts they produce are logged.
        when (e) {
            is GestureEvent.Move, is GestureEvent.Scroll, is GestureEvent.Pinch, is GestureEvent.Rotate -> Unit
            else -> log.log(Category.GESTURE) { e.toString() }
        }
        if (e is GestureEvent.ButtonDown) feedbackFlow.tryEmit(Unit)
        mapper.onEvent(e)
    }
}
