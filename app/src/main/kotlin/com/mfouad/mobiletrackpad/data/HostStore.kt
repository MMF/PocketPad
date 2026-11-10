package com.mfouad.mobiletrackpad.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.hostDataStore: DataStore<Preferences> by preferencesDataStore(name = "hosts")

/** What the app knows about paired computers, beyond what Android's bond list says. */
data class HostRecords(
    /** Hosts that bonded or connected while the HID app was registered. */
    val hidPaired: Set<String> = emptySet(),
    /** Hosts that sent a virtual cable unplug: the pairing must be redone. */
    val needsRepair: Set<String> = emptySet(),
    val lastHost: String? = null,
)

/** Per-host records in DataStore. Writes run on [scope]; callers never block. */
class HostStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = context.applicationContext.hostDataStore

    val records: Flow<HostRecords> =
        store.data.map { p ->
            HostRecords(p[HID_PAIRED].orEmpty(), p[NEEDS_REPAIR].orEmpty(), p[LAST_HOST])
        }

    fun markHidPaired(address: String) =
        update { p ->
            p[HID_PAIRED] = p[HID_PAIRED].orEmpty() + address
            p[NEEDS_REPAIR] = p[NEEDS_REPAIR].orEmpty() - address
        }

    fun markNeedsRepair(address: String) =
        update { p ->
            p[NEEDS_REPAIR] = p[NEEDS_REPAIR].orEmpty() + address
            p[HID_PAIRED] = p[HID_PAIRED].orEmpty() - address
        }

    /** The pairing was removed on the phone: start fresh for this host. */
    fun forget(address: String) =
        update { p ->
            p[HID_PAIRED] = p[HID_PAIRED].orEmpty() - address
            p[NEEDS_REPAIR] = p[NEEDS_REPAIR].orEmpty() - address
            if (p[LAST_HOST] == address) p.remove(LAST_HOST)
        }

    fun setLastHost(address: String) = update { p -> p[LAST_HOST] = address }

    private fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { store.edit(block) }
    }

    private companion object {
        val HID_PAIRED = stringSetPreferencesKey("hid_paired")
        val NEEDS_REPAIR = stringSetPreferencesKey("needs_repair")
        val LAST_HOST = stringPreferencesKey("last_host")
    }
}
