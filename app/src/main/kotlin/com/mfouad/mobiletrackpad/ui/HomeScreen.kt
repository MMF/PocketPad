package com.mfouad.mobiletrackpad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mfouad.mobiletrackpad.R
import com.mfouad.mobiletrackpad.ui.theme.TrackPadTheme

/** Placeholder start screen for Milestone 1. Replaced by onboarding and connection screens. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(R.string.home_status_scaffold), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    TrackPadTheme(darkTheme = true) { HomeScreen() }
}
