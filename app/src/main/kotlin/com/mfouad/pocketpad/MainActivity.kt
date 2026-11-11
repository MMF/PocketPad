package com.mfouad.pocketpad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.pocketpad.ui.ConnectionScreen
import com.mfouad.pocketpad.ui.DiagnosticsScreen
import com.mfouad.pocketpad.ui.OnboardingScreen
import com.mfouad.pocketpad.ui.ProfilesScreen
import com.mfouad.pocketpad.ui.SettingsScreen
import com.mfouad.pocketpad.ui.TestScreen
import com.mfouad.pocketpad.ui.TrackpadScreen
import com.mfouad.pocketpad.ui.stopSession
import com.mfouad.pocketpad.ui.theme.TrackPadTheme

private enum class Screen { CONNECTION, ONBOARDING, TRACKPAD, TEST, DIAGNOSTICS, SETTINGS, PROFILES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackPadTheme {
                // Null until the stored settings are read, so onboarding never flashes by.
                val settings by AppGraph.settingsStore.settings.collectAsStateWithLifecycle(null)
                val loaded = settings
                if (loaded == null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                } else {
                    var screen by rememberSaveable {
                        mutableStateOf(if (loaded.onboardingDone) Screen.CONNECTION else Screen.ONBOARDING)
                    }
                    Navigation(screen) { screen = it }
                }
            }
        }
    }
}

@Composable
private fun Navigation(
    screen: Screen,
    go: (Screen) -> Unit,
) {
    val context = LocalContext.current
    BackHandler(enabled = screen != Screen.CONNECTION && screen != Screen.ONBOARDING) {
        go(if (screen == Screen.PROFILES) Screen.SETTINGS else Screen.CONNECTION)
    }
    when (screen) {
        Screen.ONBOARDING -> {
            OnboardingScreen(onDone = {
                AppGraph.settingsStore.update { it.copy(onboardingDone = true) }
                go(Screen.CONNECTION)
            })
        }

        Screen.CONNECTION -> {
            ConnectionScreen(
                onOpenTrackpad = { go(Screen.TRACKPAD) },
                onOpenTest = { go(Screen.TEST) },
                onOpenDiagnostics = { go(Screen.DIAGNOSTICS) },
                onOpenSettings = { go(Screen.SETTINGS) },
            )
        }

        Screen.TRACKPAD -> {
            TrackpadScreen(
                onBack = { go(Screen.CONNECTION) },
                onStop = {
                    stopSession(context, AppGraph.controller.state.value)
                    go(Screen.CONNECTION)
                },
            )
        }

        Screen.TEST -> {
            TestScreen(onBack = { go(Screen.CONNECTION) })
        }

        Screen.DIAGNOSTICS -> {
            DiagnosticsScreen(onBack = { go(Screen.CONNECTION) })
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                onBack = { go(Screen.CONNECTION) },
                onOpenMappings = { go(Screen.PROFILES) },
                onOpenSetupGuide = { go(Screen.ONBOARDING) },
            )
        }

        Screen.PROFILES -> {
            ProfilesScreen(onBack = { go(Screen.SETTINGS) })
        }
    }
}
