package com.mfouad.mobiletrackpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.session.TestAction

/**
 * Fixed test inputs for checking a real connection. Leaving the screen, or the activity
 * stopping (screen off, app switch), releases anything held.
 */
@Composable
fun TestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = AppGraph.controller
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) controller.input.releaseAll()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.input.releaseAll()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(
                stringResource(R.string.test_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.test_intro))
            if (!state.isConnected) {
                Text(stringResource(R.string.test_not_connected), color = MaterialTheme.colorScheme.error)
            } else {
                Text(stringResource(state.protocolMode.labelRes()), style = MaterialTheme.typography.bodyMedium)
            }
            val enabled = state.isConnected
            listOf(
                TestAction.SQUARE to R.string.test_square,
                TestAction.LEFT_CLICK to R.string.test_left_click,
                TestAction.RIGHT_CLICK to R.string.test_right_click,
                TestAction.SCROLL_UP to R.string.test_scroll_up,
                TestAction.SCROLL_DOWN to R.string.test_scroll_down,
                TestAction.VOLUME_UP to R.string.test_volume_up,
                TestAction.VOLUME_DOWN to R.string.test_volume_down,
            ).forEach { (action, label) ->
                Button(
                    onClick = { controller.input.runTest(action) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(label))
                }
            }
            OutlinedButton(
                onClick = { controller.input.runTest(TestAction.HOLD_LEFT) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.test_hold_left)) }
            OutlinedButton(
                onClick = { controller.input.runTest(TestAction.RELEASE_LEFT) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.test_release_left)) }
            Text(stringResource(R.string.test_hold_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
