package com.mfouad.mobiletrackpad.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * System Bluetooth broadcasts the session cares about while it is active: the adapter turning
 * off, and bonds being created or removed. Callbacks arrive on the main thread; the receiver
 * of the callbacks hops to its own thread.
 */
class BluetoothBroadcasts(
    private val context: Context,
    private val onAdapterTurningOff: () -> Unit,
    private val onBondChanged: (address: String, bonded: Boolean) -> Unit,
) {
    private var registered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (st == BluetoothAdapter.STATE_TURNING_OFF ||
                            st == BluetoothAdapter.STATE_OFF
                        ) {
                            onAdapterTurningOff()
                        }
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = intent.bluetoothDevice() ?: return
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                            BluetoothDevice.BOND_BONDED -> onBondChanged(device.address, true)
                            BluetoothDevice.BOND_NONE -> onBondChanged(device.address, false)
                        }
                    }
                }
            }
        }

    fun register() {
        if (registered) return
        val filter =
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}

private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
