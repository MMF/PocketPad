package com.mfouad.mobiletrackpad

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mfouad.mobiletrackpad.hid.HidReport

typealias ActivityRule<A> = AndroidComposeTestRule<ActivityScenarioRule<A>, A>

/** Reports recorded by the debug-only simulated computer. */
val recordedReports: List<HidReport>
    get() = (AppGraph.controller.simulatedRecorder as? RecordingOutputSink)?.reports?.toList().orEmpty()

fun hex(r: HidReport) = "${r.id}: ${r.toHex()}"

fun <A : ComponentActivity> ActivityRule<A>.string(id: Int): String = activity.getString(id)

fun <A : ComponentActivity> ActivityRule<A>.exists(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

fun <A : ComponentActivity> ActivityRule<A>.waitFor(
    what: String,
    timeoutMs: Long = 5_000,
    condition: () -> Boolean,
) {
    try {
        waitUntil(timeoutMs, condition)
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out waiting for $what. Reports: ${recordedReports.takeLast(12).map(::hex)}", e)
    }
}

/** A fresh install starts with onboarding; step through it to reach the main screen. */
fun <A : ComponentActivity> ActivityRule<A>.passOnboardingIfShown() {
    waitForIdle()
    if (!exists(string(R.string.onboarding_title_welcome))) return
    repeat(3) {
        onNodeWithText(string(R.string.action_next)).performClick()
        waitForIdle()
    }
    onNodeWithText(string(R.string.action_done)).performClick()
    waitFor("main screen") { exists(string(R.string.action_enable)) || exists(string(R.string.action_stop)) }
}

/** Waits until no new report has arrived for a short while. */
fun <A : ComponentActivity> ActivityRule<A>.settleReports() {
    var last = -1
    waitFor("output to settle") {
        val n = recordedReports.size
        val stable = n == last
        last = n
        if (!stable) Thread.sleep(300)
        stable
    }
}
