package com.mfouad.mobiletrackpad.ui

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.data.HostRecords
import com.mfouad.mobiletrackpad.hid.MouseButton
import com.mfouad.mobiletrackpad.input.MotionEventFrames
import com.mfouad.mobiletrackpad.session.Phase
import com.mfouad.mobiletrackpad.settings.Settings

const val TRACKPAD_SURFACE_TAG = "trackpad-surface"

private val ConnectedColor = Color(0xFF4CAF50)
private val DisconnectedColor = Color(0xFFF44336)
private val SubtleText = Color(0xFF9E9E9E)

/**
 * Full-screen, true-black touch surface. While it is visible and connected the screen stays
 * on; when it stops being visible (navigation, Home, screen off) control pauses and everything
 * held is released. The Bluetooth session itself continues.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackpadScreen(
    onBack: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = AppGraph.controller
    val state by controller.state.collectAsStateWithLifecycle()
    val hosts by controller.bondedHosts.collectAsStateWithLifecycle()
    val view = LocalView.current
    val settings by AppGraph.settingsStore.settings.collectAsStateWithLifecycle(Settings())
    val dragLocked by controller.input.dragLocked.collectAsStateWithLifecycle()
    val reconnect by controller.reconnect.collectAsStateWithLifecycle()

    var touched by rememberSaveable { mutableStateOf(false) }
    val records by AppGraph.hostStore.records.collectAsStateWithLifecycle(HostRecords())

    ImmersiveMode()
    PauseWhenNotVisible()
    BatterySaverWindow(settings.batterySaver)
    LaunchedEffect(settings.effectiveHaptics) {
        if (settings.effectiveHaptics) {
            controller.input.clickFeedback.collect { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
        }
    }
    DisposableEffect(state.isConnected) {
        view.keepScreenOn = state.isConnected
        onDispose { view.keepScreenOn = false }
    }

    val surfaceDescription = stringResource(R.string.trackpad_surface_description)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .systemGestureExclusion()
                .testTag(TRACKPAD_SURFACE_TAG)
                .semantics { contentDescription = surfaceDescription }
                .pointerInteropFilter { ev ->
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                        touched = true
                        // Deliver touch as it arrives instead of once per frame.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.requestUnbufferedDispatch(ev)
                    }
                    controller.input.onTouchFrames(MotionEventFrames.from(ev))
                    true
                },
        )
        val hostName = hostDisplayName(state, hosts)
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (state.isConnected) ConnectedColor else DisconnectedColor, CircleShape),
            )
            Text(
                text =
                    when {
                        state.isConnected -> stringResource(R.string.trackpad_connected, hostName)
                        reconnect != null -> stringResource(R.string.trackpad_reconnecting)
                        else -> stringResource(R.string.trackpad_not_connected)
                    },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
        }
        if (!state.isConnected) {
            DisconnectedPanel(
                reconnecting = reconnect != null,
                canReconnect = state.phase == Phase.REGISTERED && records.lastHost != null && !state.simulated,
                onReconnect = { records.lastHost?.let(controller::connect) },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dragLocked) DragLockBanner(onRelease = controller.input::releaseDragLock)
            // The hint helps until the first touch, then gets out of the way.
            AnimatedVisibility(
                visible = !touched && state.isConnected,
                enter = EnterTransition.None,
                exit = if (settings.batterySaver) ExitTransition.None else fadeOut(),
            ) {
                Text(
                    text = stringResource(R.string.trackpad_hint),
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            if (settings.onScreenButtons) MouseButtons(leftHanded = settings.leftHanded)
        }
    }
}

/** Shown over the surface when no computer is connected, so a black screen is never a mystery. */
@Composable
private fun DisconnectedPanel(
    reconnecting: Boolean,
    canReconnect: Boolean,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(32.dp)
            .background(PanelColor, RoundedCornerShape(16.dp))
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (reconnecting) R.string.trackpad_reconnecting else R.string.trackpad_disconnected_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (reconnecting) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Text(
                stringResource(R.string.trackpad_disconnected_body),
                color = SubtleText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canReconnect &&
                !reconnecting
            ) {
                Button(onClick = onReconnect) { Text(stringResource(R.string.action_reconnect)) }
            }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}

private val PanelColor = Color(0xFF1E1E1E)

@Composable
private fun DragLockBanner(onRelease: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BannerColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.trackpad_drag_locked), color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = onRelease) { Text(stringResource(R.string.trackpad_release)) }
    }
}

/**
 * Optional on-screen buttons. Pressing holds the button until the finger lifts, so they can
 * also be used for dragging together with the touch surface.
 */
@Composable
private fun MouseButtons(leftHanded: Boolean) {
    val first = if (leftHanded) MouseButton.SECONDARY else MouseButton.PRIMARY
    val second = if (leftHanded) MouseButton.PRIMARY else MouseButton.SECONDARY
    Row(Modifier.fillMaxWidth().height(BUTTON_HEIGHT), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HoldButton(first, Modifier.weight(1f))
        HoldButton(second, Modifier.weight(1f))
    }
}

@Composable
private fun HoldButton(
    button: MouseButton,
    modifier: Modifier,
) {
    val input = AppGraph.controller.input
    val label =
        stringResource(
            if (button ==
                MouseButton.PRIMARY
            ) {
                R.string.trackpad_button_primary
            } else {
                R.string.trackpad_button_secondary
            },
        )
    Box(
        modifier
            .fillMaxHeight()
            .background(ButtonColor, RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = label
                role = Role.Button
            }.pointerInput(button) {
                detectTapGestures(onPress = {
                    input.pressButton(button)
                    tryAwaitRelease()
                    input.releaseButton(button)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SubtleText)
    }
}

/** Battery saving: dim this window and prefer a standard refresh rate while the trackpad shows. */
@Composable
private fun BatterySaverWindow(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val original = window?.attributes?.let { it.screenBrightness to it.preferredRefreshRate }
        if (enabled && window != null) {
            window.attributes =
                window.attributes.apply {
                    screenBrightness = DIMMED_BRIGHTNESS
                    preferredRefreshRate = STANDARD_REFRESH_RATE
                }
        }
        onDispose {
            if (window != null && original != null) {
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = original.first
                        preferredRefreshRate = original.second
                    }
            }
        }
    }
}

private const val DIMMED_BRIGHTNESS = 0.1f
private const val STANDARD_REFRESH_RATE = 60f
private val BUTTON_HEIGHT = 72.dp
private val BannerColor = Color(0xFF37474F)
private val ButtonColor = Color(0xFF1C1C1C)

/** Hides the system bars while the trackpad is shown; a swipe from the edge reveals them briefly. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun PauseWhenNotVisible() {
    val controller = AppGraph.controller
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) controller.input.pause()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.input.pause()
        }
    }
}
