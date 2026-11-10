package com.mfouad.mobiletrackpad.engine

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import com.mfouad.mobiletrackpad.gesture.Cancellable
import com.mfouad.mobiletrackpad.gesture.Scheduler
import java.util.concurrent.Executor

/**
 * The single engine thread. It owns the report sender, the session state machine and HID
 * callbacks (it is the executor passed to `registerApp`), so none of them need locks.
 *
 * An idle looper sleeps; nothing here polls.
 */
class Engine {
    private val thread = HandlerThread("hid-engine", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    val executor: Executor = Executor { handler.post(it) }

    val scheduler: Scheduler =
        object : Scheduler {
            override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()

            override fun schedule(
                delayNanos: Long,
                task: () -> Unit,
            ): Cancellable {
                val runnable = Runnable { task() }
                handler.postDelayed(runnable, ceilMillis(delayNanos))
                return Cancellable { handler.removeCallbacks(runnable) }
            }
        }

    fun post(block: () -> Unit) {
        handler.post(block)
    }

    val isEngineThread: Boolean get() = Thread.currentThread() == thread

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        fun ceilMillis(nanos: Long): Long = if (nanos <= 0) 0 else (nanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
    }
}
