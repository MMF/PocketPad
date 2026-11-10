package com.mfouad.mobiletrackpad

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.mfouad.mobiletrackpad.bluetooth.HidSessionController
import com.mfouad.mobiletrackpad.data.HostStore
import com.mfouad.mobiletrackpad.diagnostics.DiagnosticsLog
import com.mfouad.mobiletrackpad.service.HidSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TrackPadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        HidSessionService.createChannel(this)
    }
}

/** Process-wide objects. The session outlives activities; the service ties it to user intent. */
object AppGraph {
    // Holds only the application context, which lives as long as the process.
    @SuppressLint("StaticFieldLeak")
    lateinit var controller: HidSessionController
        private set
    lateinit var hostStore: HostStore
        private set
    val log = DiagnosticsLog()

    fun init(context: Context) {
        if (::controller.isInitialized) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        hostStore = HostStore(context.applicationContext, scope)
        controller = HidSessionController(context.applicationContext, log, hostStore)
    }
}
