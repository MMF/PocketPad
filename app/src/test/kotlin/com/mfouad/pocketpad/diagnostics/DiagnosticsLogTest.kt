package com.mfouad.pocketpad.diagnostics

import com.mfouad.pocketpad.diagnostics.DiagnosticsLog.Category
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsLogTest {
    private var now = 1_762_000_000_000L
    private val log = DiagnosticsLog(capacity = 3, wallClockMillis = { now }, zone = ZoneOffset.UTC)

    @Test
    fun offByDefaultAndDoesNotBuildMessages() {
        var built = false
        log.log(Category.REPORT) {
            built = true
            "x"
        }
        assertEquals(emptyList(), log.snapshot())
        assertEquals(false, built)
    }

    @Test
    fun keepsMostRecentEntriesUpToCapacity() {
        log.setEnabled(true)
        (1..5).forEach { i -> log.log(Category.BLUETOOTH) { "e$i" } }
        assertEquals(listOf("e3", "e4", "e5"), log.snapshot().map { it.message })
    }

    @Test
    fun exportFormatsTimestampsAndNotesDroppedEntries() {
        log.setEnabled(true)
        (1..4).forEach { i ->
            now += 1
            log.log(Category.REPORT) { "R2 00 0$i" }
        }
        val text = log.export(listOf("PocketPad diagnostics"))
        val lines = text.lines()
        assertEquals("PocketPad diagnostics", lines[0])
        assertEquals("(1 older entries were dropped)", lines[1])
        assertTrue(lines.contains("2025-11-01 12:26:40.002 [RPT] R2 00 02"), text)
    }

    @Test
    fun clearEmptiesAndBumpsVersion() {
        log.setEnabled(true)
        log.log(Category.SESSION) { "a" }
        val v = log.version.value
        log.clear()
        assertEquals(emptyList(), log.snapshot())
        assertTrue(log.version.value > v)
    }
}
