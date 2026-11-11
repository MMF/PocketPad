package com.mfouad.pocketpad.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mfouad.pocketpad.hid.HostProfile
import com.mfouad.pocketpad.hid.HostProfiles
import com.mfouad.pocketpad.hid.ProfileCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Settings, edited profiles and the "session was active" marker, in DataStore. */
class SettingsStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = store.data.map(::read)

    /** All profiles: the built-ins, replaced by the user's edited copy where one exists. */
    val profiles: Flow<List<HostProfile>> =
        store.data.map { p ->
            HostProfiles.defaults.map { default ->
                p[profileKey(default.id)]?.let { ProfileCodec.decode(default.id, it, default.name) } ?: default
            }
        }

    /** True if a non-simulated session was active when the process last ran. */
    val sessionWasActive: Flow<Boolean> = store.data.map { it[SESSION_ACTIVE] ?: false }

    fun update(transform: (Settings) -> Settings) =
        scope.launch {
            store.edit { p ->
                val n = transform(read(p))
                p[POINTER_SPEED] = n.pointerSpeed
                p[ACCELERATION] = n.acceleration
                p[SCROLL_SPEED] = n.scrollSpeed
                p[REVERSE_SCROLL] = n.reverseScroll
                p[INERTIA] = n.scrollInertia
                p[TAP_TO_CLICK] = n.tapToClick
                p[DRAG_LOCK] = n.dragLock
                p[HAPTICS] = n.haptics
                p[ON_SCREEN_BUTTONS] = n.onScreenButtons
                p[LEFT_HANDED] = n.leftHanded
                p[BATTERY_SAVER] = n.batterySaver
                p[ONBOARDING_DONE] = n.onboardingDone
            }
        }

    /** Resets every setting to its default. Onboarding stays completed; profiles are untouched. */
    fun resetSettings() = update { Settings(onboardingDone = it.onboardingDone) }

    fun saveProfile(profile: HostProfile) =
        scope.launch {
            store.edit {
                it[profileKey(profile.id)] =
                    ProfileCodec.encode(profile)
            }
        }

    fun resetProfile(id: String) = scope.launch { store.edit { it.remove(profileKey(id)) } }

    fun setSessionActive(active: Boolean) = scope.launch { store.edit { it[SESSION_ACTIVE] = active } }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            pointerSpeed = p[POINTER_SPEED] ?: d.pointerSpeed,
            acceleration = p[ACCELERATION] ?: d.acceleration,
            scrollSpeed = p[SCROLL_SPEED] ?: d.scrollSpeed,
            reverseScroll = p[REVERSE_SCROLL] ?: d.reverseScroll,
            scrollInertia = p[INERTIA] ?: d.scrollInertia,
            tapToClick = p[TAP_TO_CLICK] ?: d.tapToClick,
            dragLock = p[DRAG_LOCK] ?: d.dragLock,
            haptics = p[HAPTICS] ?: d.haptics,
            onScreenButtons = p[ON_SCREEN_BUTTONS] ?: d.onScreenButtons,
            leftHanded = p[LEFT_HANDED] ?: d.leftHanded,
            batterySaver = p[BATTERY_SAVER] ?: d.batterySaver,
            onboardingDone = p[ONBOARDING_DONE] ?: d.onboardingDone,
        )
    }

    private fun profileKey(id: String) = stringPreferencesKey("profile_$id")

    private companion object {
        val POINTER_SPEED = floatPreferencesKey("pointer_speed")
        val ACCELERATION = floatPreferencesKey("acceleration")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        val REVERSE_SCROLL = booleanPreferencesKey("reverse_scroll")
        val INERTIA = booleanPreferencesKey("scroll_inertia")
        val TAP_TO_CLICK = booleanPreferencesKey("tap_to_click")
        val DRAG_LOCK = booleanPreferencesKey("drag_lock")
        val HAPTICS = booleanPreferencesKey("haptics")
        val ON_SCREEN_BUTTONS = booleanPreferencesKey("on_screen_buttons")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
    }
}
