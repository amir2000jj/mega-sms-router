package com.megasmsrouter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Non-secret app state. Forwarding is OFF on first install. */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("msr_prefs", Context.MODE_PRIVATE)
    private val _forwarding = MutableStateFlow(sp.getBoolean(KEY_FORWARDING, false))
    val forwardingEnabled: StateFlow<Boolean> = _forwarding

    fun isForwardingEnabled(): Boolean = sp.getBoolean(KEY_FORWARDING, false)

    fun setForwarding(on: Boolean) {
        sp.edit().putBoolean(KEY_FORWARDING, on).apply()
        _forwarding.value = on
    }

    private companion object {
        const val KEY_FORWARDING = "forwarding_enabled"
    }
}
