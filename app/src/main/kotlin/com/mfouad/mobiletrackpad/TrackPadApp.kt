package com.mfouad.mobiletrackpad

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.mfouad.mobiletrackpad.bluetooth.HidSessionController
import com.mfouad.mobiletrackpad.data.HostStore
import com.mfouad.mobiletrackpad.diagnostics.DiagnosticsLog
import com.mfouad.mobiletrackpad.service.HidSessionService
import com.mfouad.mobiletrackpad.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    lateinit var settingsStore: SettingsStore
        private set
    val log = DiagnosticsLog()

    /**
     * Whether the previous process was stopped by the user from Android's Task Manager while a
     * session was active (API 30+). Read once at startup, before this run marks a new session.
     */
    private val stoppedByUserFlow = MutableStateFlow(false)
    val stoppedByUserLastTime: StateFlow<Boolean> = stoppedByUserFlow.asStateFlow()

    /** The user has read the Task Manager message. */
    fun dismissStoppedByUser() {
        stoppedByUserFlow.value = false
    }

    fun init(context: Context) {
        if (::controller.isInitialized) return
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        hostStore = HostStore(app, scope)
        settingsStore = SettingsStore(app, scope)
        controller = HidSessionController(app, log, hostStore)

        scope.launch { settingsStore.settings.collect { controller.input.applySettings(it) } }
        scope.launch {
            combine(settingsStore.profiles, hostStore.records) { profiles, records -> profiles to records.profiles }
                .collect { (profiles, assignments) -> controller.input.setProfiles(profiles, assignments) }
        }
        scope.launch {
            // Read last run's marker before this run's session state overwrites it.
            stoppedByUserFlow.value = settingsStore.sessionWasActive.first() && ExitReasons.lastExitWasUserStop(app)
            controller.state
                .map { it.isActive && !it.simulated }
                .distinctUntilChanged()
                .collect { settingsStore.setSessionActive(it) }
        }
    }
}
