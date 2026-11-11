package com.mfouad.pocketpad.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quick checks for onboarding: Bluetooth hardware, Bluetooth on, permission, and whether Android
 * hands out its Bluetooth HID device service. It does not register, so it cannot prove that a
 * computer will accept the phone.
 */
class CompatibilityChecker(
    private val context: Context,
) {
    enum class Result { OK, FAILED, PENDING, SKIPPED }

    data class Report(
        val adapter: Result,
        val enabled: Result,
        val permission: Result,
        val hidDeviceService: Result,
    )

    private val reportFlow = MutableStateFlow<Report?>(null)
    val report: StateFlow<Report?> = reportFlow.asStateFlow()

    private val env = BluetoothEnv(context)
    private val main = Handler(Looper.getMainLooper())

    // Only reached after hasConnectPermission() is true.
    @SuppressLint("MissingPermission")
    fun run() {
        val adapter = env.adapter
        val hasAdapter = adapter != null
        val enabled = hasAdapter && adapter.isEnabled
        val permitted = env.hasConnectPermission()
        val base =
            Report(
                adapter = ok(hasAdapter),
                enabled = if (hasAdapter) ok(enabled) else Result.SKIPPED,
                permission = ok(permitted),
                hidDeviceService = Result.SKIPPED,
            )
        if (adapter == null || !enabled || !permitted) {
            reportFlow.value = base
            return
        }
        reportFlow.value = base.copy(hidDeviceService = Result.PENDING)
        var done = false
        val listener =
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(
                    profile: Int,
                    proxy: BluetoothProfile,
                ) {
                    if (done) return
                    done = true
                    adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
                    reportFlow.value = base.copy(hidDeviceService = Result.OK)
                }

                override fun onServiceDisconnected(profile: Int) = Unit
            }
        if (!adapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)) {
            reportFlow.value = base.copy(hidDeviceService = Result.FAILED)
            return
        }
        main.postDelayed({
            if (!done) {
                done = true
                reportFlow.value = base.copy(hidDeviceService = Result.FAILED)
            }
        }, TIMEOUT_MS)
    }

    private fun ok(b: Boolean) = if (b) Result.OK else Result.FAILED

    private companion object {
        const val TIMEOUT_MS = 3_000L
    }
}
