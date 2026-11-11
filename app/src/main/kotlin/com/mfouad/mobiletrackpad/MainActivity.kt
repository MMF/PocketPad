package com.mfouad.mobiletrackpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mfouad.mobiletrackpad.ui.ConnectionScreen
import com.mfouad.mobiletrackpad.ui.DiagnosticsScreen
import com.mfouad.mobiletrackpad.ui.TestScreen
import com.mfouad.mobiletrackpad.ui.TrackpadScreen
import com.mfouad.mobiletrackpad.ui.stopSession
import com.mfouad.mobiletrackpad.ui.theme.TrackPadTheme

private enum class Screen { CONNECTION, TRACKPAD, TEST, DIAGNOSTICS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackPadTheme {
                var screen by rememberSaveable { mutableScreen() }
                BackHandler(enabled = screen != Screen.CONNECTION) { screen = Screen.CONNECTION }
                when (screen) {
                    Screen.CONNECTION -> {
                        ConnectionScreen(
                            onOpenTrackpad = { screen = Screen.TRACKPAD },
                            onOpenTest = { screen = Screen.TEST },
                            onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                        )
                    }

                    Screen.TRACKPAD -> {
                        val context = LocalContext.current
                        TrackpadScreen(
                            onBack = { screen = Screen.CONNECTION },
                            onStop = {
                                stopSession(context, AppGraph.controller.state.value)
                                screen = Screen.CONNECTION
                            },
                        )
                    }

                    Screen.TEST -> {
                        TestScreen(onBack = { screen = Screen.CONNECTION })
                    }

                    Screen.DIAGNOSTICS -> {
                        DiagnosticsScreen(onBack = { screen = Screen.CONNECTION })
                    }
                }
            }
        }
    }

    private fun mutableScreen() = androidx.compose.runtime.mutableStateOf(Screen.CONNECTION)
}
