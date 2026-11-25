package com.eventuath.gexrdp_kvm.data

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("gex_settings", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0.2f, 3.0f)).apply()

    var wheelInverted: Boolean
        get() = prefs.getBoolean(KEY_WHEEL_INVERTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WHEEL_INVERTED, value).apply()

    var maxFps: Int
        get() = prefs.getInt(KEY_MAX_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_MAX_FPS, value.coerceIn(10, 60)).apply()

    var lastServerId: String?
        get() = prefs.getString(KEY_LAST_SERVER, null)
        set(value) = prefs.edit().putString(KEY_LAST_SERVER, value).apply()

    var autoEnterSingleVm: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENTER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENTER, value).apply()

    private companion object {
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_WHEEL_INVERTED = "wheel_inverted"
        const val KEY_MAX_FPS = "max_fps"
        const val KEY_LAST_SERVER = "last_server"
        const val KEY_AUTO_ENTER = "auto_enter_single_vm"
    }
}
