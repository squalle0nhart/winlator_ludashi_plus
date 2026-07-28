package com.winlator.cmod.perf

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared global defaults for app settings, shortcut overrides, and the live in-game surface. */
object PerformanceSettings {
    const val KEY_SUSTAINED = "sustainedPerfMode"
    const val KEY_PRIORITY = "perfPriorityBoost"
    const val KEY_BIG_CORES = "preferBigCores"

    private const val PREFS = "perf_prefs"
    private val NON_ROOT_KEYS = listOf(KEY_SUSTAINED, KEY_PRIORITY, KEY_BIG_CORES)
    val ALL_PERF_KEYS: List<String> = NON_ROOT_KEYS + PerfRootApplier.ROOT_KEYS

    private var appContext: Context? = null
    private val defaults = LinkedHashMap<String, MutableStateFlow<Boolean>>()

    val sustainedPerfMode: StateFlow<Boolean> get() = flowFor(KEY_SUSTAINED).asStateFlow()
    val perfPriorityBoost: StateFlow<Boolean> get() = flowFor(KEY_PRIORITY).asStateFlow()
    val preferBigCores: StateFlow<Boolean> get() = flowFor(KEY_BIG_CORES).asStateFlow()
    val rootState: StateFlow<RootManager.RootState> get() = RootManager.state
    val watchdogEnabled: StateFlow<Boolean> get() = TempWatchdog.enabled

    fun init(context: Context) {
        appContext = context.applicationContext
        ALL_PERF_KEYS.forEach(::flowFor)
    }

    fun globalDefault(key: String): Boolean = flowFor(key).value

    fun globalDefaultFlow(key: String): StateFlow<Boolean> = flowFor(key).asStateFlow()

    fun setGlobalDefault(key: String, value: Boolean) {
        flowFor(key).value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(prefKey(key), value)?.apply()
    }

    fun setSustainedPerfMode(value: Boolean) = setGlobalDefault(KEY_SUSTAINED, value)
    fun setPerfPriorityBoost(value: Boolean) = setGlobalDefault(KEY_PRIORITY, value)
    fun setPreferBigCores(value: Boolean) = setGlobalDefault(KEY_BIG_CORES, value)
    fun rootDefaultValue(key: String): Boolean = globalDefault(key)
    fun setRootDefault(key: String, value: Boolean) = setGlobalDefault(key, value)

    private fun flowFor(key: String): MutableStateFlow<Boolean> =
        defaults.getOrPut(key) {
            MutableStateFlow(
                appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    ?.getBoolean(prefKey(key), false) ?: false,
            )
        }

    private fun prefKey(key: String): String = "global_$key"
}
