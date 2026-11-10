package com.mfouad.mobiletrackpad.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * In-memory diagnostics log. Off by default. Entries never leave the app unless the user copies
 * or shares them. Thread-safe: written from the engine thread, read from the UI.
 */
class DiagnosticsLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    enum class Category(
        val tag: String,
    ) {
        SESSION("SES"),
        BLUETOOTH("BT"),
        REPORT("RPT"),
        GESTURE("GST"),
    }

    data class Entry(
        val timeMillis: Long,
        val category: Category,
        val message: String,
    )

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private var droppedCount = 0L

    private val enabledFlow = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = enabledFlow.asStateFlow()

    private val versionFlow = MutableStateFlow(0L)

    /** Increments whenever entries change; the UI re-reads [snapshot] when it changes. */
    val version: StateFlow<Long> = versionFlow.asStateFlow()

    val isEnabled: Boolean get() = enabledFlow.value

    fun setEnabled(on: Boolean) {
        enabledFlow.value = on
    }

    /** Adds an entry if logging is enabled. [message] is only built when it will be kept. */
    fun log(
        category: Category,
        message: () -> String,
    ) {
        if (!enabledFlow.value) return
        val entry = Entry(wallClockMillis(), category, message())
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > capacity) {
                entries.removeFirst()
                droppedCount++
            }
        }
        versionFlow.value = versionFlow.value + 1
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            droppedCount = 0
        }
        versionFlow.value = versionFlow.value + 1
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    /** Plain-text export. [header] lines (device, app version, state) come first. */
    fun export(header: List<String>): String {
        val (list, dropped) = synchronized(lock) { entries.toList() to droppedCount }
        return buildString {
            header.forEach { appendLine(it) }
            if (dropped > 0) appendLine("($dropped older entries were dropped)")
            appendLine()
            list.forEach { appendLine(format(it)) }
        }
    }

    fun format(e: Entry): String =
        "${TIME.format(Instant.ofEpochMilli(e.timeMillis).atZone(zone))} [${e.category.tag}] ${e.message}"

    companion object {
        const val DEFAULT_CAPACITY = 2000
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}
