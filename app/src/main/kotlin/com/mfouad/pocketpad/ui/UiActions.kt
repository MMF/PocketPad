package com.mfouad.pocketpad.ui

/** What the buttons on a problem card do. */
class ProblemActions(
    val retry: () -> Unit,
    val grantPermission: () -> Unit,
    val turnOnBluetooth: () -> Unit,
    val openBluetoothSettings: () -> Unit,
    val openAppSettings: () -> Unit,
)

/** Callbacks for the computer cards. */
class HostActions(
    val connect: (String) -> Unit,
    val disconnect: () -> Unit,
    val refresh: () -> Unit,
)
