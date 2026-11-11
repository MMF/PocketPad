package com.mfouad.mobiletrackpad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.bluetooth.BluetoothEnv
import com.mfouad.mobiletrackpad.bluetooth.CompatibilityChecker

private const val PAGE_WELCOME = 0
private const val PAGE_PERMISSIONS = 1
private const val PAGE_CHECK = 2
private const val PAGE_PAIRING = 3

/** First-run guide: what the app is, permissions, a compatibility check, and pairing order. */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val env = remember { BluetoothEnv(context) }
    val checker = remember { CompatibilityChecker(context.applicationContext) }
    val report by checker.report.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(PAGE_WELCOME) }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionVersion++ }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val (title, body) =
                when (page) {
                    PAGE_WELCOME -> R.string.onboarding_title_welcome to R.string.onboarding_body_welcome
                    PAGE_PERMISSIONS -> R.string.onboarding_title_permissions to R.string.onboarding_body_permissions
                    PAGE_CHECK -> R.string.onboarding_title_check to R.string.onboarding_body_check
                    else -> R.string.onboarding_title_pairing to R.string.onboarding_body_pairing
                }
            Text(
                stringResource(title),
                style = MaterialTheme.typography.headlineMedium,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)

            when (page) {
                PAGE_PERMISSIONS -> {
                    // Re-read after the permission dialog returns.
                    val granted = remember(permissionVersion) { env.hasConnectPermission() }
                    if (granted) {
                        Text(
                            stringResource(R.string.onboarding_permissions_granted),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Button(onClick = { permissionLauncher.launch(BluetoothEnv.runtimePermissions()) }) {
                            Text(stringResource(R.string.action_grant_permission))
                        }
                    }
                }

                PAGE_CHECK -> {
                    OutlinedButton(onClick = checker::run) { Text(stringResource(R.string.onboarding_run_check)) }
                    report?.let { r ->
                        CheckRow(R.string.check_adapter, r.adapter)
                        CheckRow(R.string.check_enabled, r.enabled)
                        CheckRow(R.string.check_permission, r.permission)
                        CheckRow(R.string.check_profile, r.hidDeviceService)
                    }
                }
            }

            Spacer(Modifier.padding(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (page > PAGE_WELCOME) {
                    TextButton(onClick = { page-- }) { Text(stringResource(R.string.action_back)) }
                } else {
                    Spacer(Modifier)
                }
                if (page < PAGE_PAIRING) {
                    Button(onClick = { page++ }) { Text(stringResource(R.string.action_next)) }
                } else {
                    Button(onClick = onDone) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: Int,
    result: CompatibilityChecker.Result,
) {
    val text =
        when (result) {
            CompatibilityChecker.Result.OK -> R.string.check_ok
            CompatibilityChecker.Result.FAILED -> R.string.check_failed
            CompatibilityChecker.Result.PENDING -> R.string.check_pending
            CompatibilityChecker.Result.SKIPPED -> R.string.check_skipped
        }
    val color =
        if (result ==
            CompatibilityChecker.Result.FAILED
        ) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onBackground
        }
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Text(stringResource(text), color = color)
    }
}
