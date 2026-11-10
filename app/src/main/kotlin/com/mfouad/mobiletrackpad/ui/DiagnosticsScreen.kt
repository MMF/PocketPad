package com.mfouad.mobiletrackpad.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfouad.mobiletrackpad.AppGraph
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.diagnostics.DiagnosticsLog
import com.mfouad.mobiletrackpad.hid.HidDescriptor
import com.mfouad.mobiletrackpad.session.SessionState
import kotlinx.coroutines.delay

private const val UI_REFRESH_MILLIS = 250L

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val log = AppGraph.log
    val controller = AppGraph.controller
    val enabled by log.enabled.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf(log.snapshot()) }
    val listState = rememberLazyListState()

    // Re-read at most every 250 ms so a burst of reports does not flood the UI.
    LaunchedEffect(log) {
        // StateFlow is conflated, so updates during the delay collapse into one.
        log.version.collect {
            entries = log.snapshot()
            delay(UI_REFRESH_MILLIS)
        }
    }
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(
                stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diagnostics_enable), modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = log::setEnabled)
            }
            Text(stringResource(R.string.diagnostics_privacy), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.diagnostics_state, state.phase.name),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.diagnostics_mode, stringResource(state.protocolMode.labelRes())),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copy(context, exportText(context, log, state)) }) {
                    Text(stringResource(R.string.diagnostics_copy))
                }
                OutlinedButton(onClick = { share(context, exportText(context, log, state)) }) {
                    Text(stringResource(R.string.diagnostics_share))
                }
                OutlinedButton(onClick = log::clear) { Text(stringResource(R.string.diagnostics_clear)) }
            }
            if (entries.isEmpty()) {
                Text(stringResource(R.string.diagnostics_empty))
            } else {
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(state = listState) {
                        items(entries) { e ->
                            Text(
                                log.format(e),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun exportText(
    context: Context,
    log: DiagnosticsLog,
    state: SessionState,
): String {
    val version =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    return log.export(
        listOf(
            "Mobile TrackPad diagnostics",
            "App version: $version",
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Descriptor: ${HidDescriptor.bytes().size} bytes",
            "State: ${state.phase}, protocol ${state.protocolMode}, problem ${state.problem ?: "none"}",
            "LED state from host: 0x%02X".format(AppGraph.controller.ledState),
        ),
    )
}

private fun copy(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostics_share_subject), text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ shows its own confirmation.
        Toast.makeText(context, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun share(
    context: Context,
    text: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_share_subject))
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
