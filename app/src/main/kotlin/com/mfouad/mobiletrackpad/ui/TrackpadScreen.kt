package com.mfouad.mobiletrackpad.ui

import android.app.Activity
import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
import com.mfouad.mobiletrackpad.input.MotionEventFrames

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

    ImmersiveMode()
    PauseWhenNotVisible()
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
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Deliver touch as it arrives instead of once per frame.
                        view.requestUnbufferedDispatch(ev)
                    }
                    controller.onTouchFrames(MotionEventFrames.from(ev))
                    true
                },
        )
        val hostName = hosts.firstOrNull { it.address == state.hostAddress }?.name ?: state.hostAddress.orEmpty()
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
                    if (state.isConnected) {
                        stringResource(R.string.trackpad_connected, hostName)
                    } else {
                        stringResource(R.string.trackpad_not_connected)
                    },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
        }
        Text(
            text = stringResource(R.string.trackpad_hint),
            color = SubtleText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
        )
        Spacer(Modifier)
    }
}

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
                if (event == Lifecycle.Event.ON_STOP) controller.pauseTrackpad()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.pauseTrackpad()
        }
    }
}
