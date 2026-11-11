package com.mfouad.pocketpad.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeSchedulerTest {
    @Test
    fun runsTasksInDueOrderAndAdvancesTime() {
        val s = FakeScheduler()
        val log = mutableListOf<String>()
        s.schedule(20) { log += "b@${s.nowNanos()}" }
        s.schedule(10) { log += "a@${s.nowNanos()}" }
        s.advanceBy(15)
        assertEquals(listOf("a@10"), log)
        s.advanceBy(10)
        assertEquals(listOf("a@10", "b@20"), log)
        assertEquals(25, s.nowNanos())
    }

    @Test
    fun cancelledTasksDoNotRun() {
        val s = FakeScheduler()
        var ran = false
        s.schedule(5) { ran = true }.cancel()
        s.advanceBy(10)
        assertEquals(false, ran)
        assertEquals(0, s.pendingCount)
    }

    @Test
    fun tasksScheduledFromTasksRunWhenDue() {
        val s = FakeScheduler()
        val log = mutableListOf<Long>()
        s.schedule(5) {
            log += s.nowNanos()
            s.schedule(5) { log += s.nowNanos() }
        }
        s.advanceBy(100)
        assertEquals(listOf(5L, 10L), log)
    }
}
