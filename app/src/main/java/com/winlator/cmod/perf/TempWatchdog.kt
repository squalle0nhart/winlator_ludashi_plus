package com.winlator.cmod.perf

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Device-trip-anchored thermal failsafe. Two consecutive over-limit readings restore every captured
 * privileged value, even when the user disabled the device's normal thermal throttling.
 */
object TempWatchdog {
    private const val TAG = "TempWatchdog"
    private const val PREFS = "perf_prefs"
    private const val KEY_ENABLED = "temp_watchdog_enabled"
    private const val KEY_MODE = "temp_watchdog_mode"
    private const val KEY_MANUAL = "temp_watchdog_manual_c"
    private const val BALANCED_MARGIN = 10
    private const val AGGRESSIVE_MARGIN = 3
    private const val POLL_MS = 3_000L
    private const val TRIP_COUNT = 2

    const val FALLBACK_CEILING_C = 85
    const val MANUAL_MIN_C = 60
    const val MANUAL_MAX_C = 125

    enum class ThresholdMode {
        CONSERVATIVE,
        BALANCED,
        AGGRESSIVE,
        MANUAL,
    }

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _mode = MutableStateFlow(ThresholdMode.BALANCED)
    val mode: StateFlow<ThresholdMode> = _mode.asStateFlow()
    private val _manualCeilingC = MutableStateFlow(FALLBACK_CEILING_C)
    val manualCeilingC: StateFlow<Int> = _manualCeilingC.asStateFlow()
    private val _ceilingC = MutableStateFlow(FALLBACK_CEILING_C)
    val ceilingC: StateFlow<Int> = _ceilingC.asStateFlow()
    private val _lastTempC = MutableStateFlow(0)
    val lastTempC: StateFlow<Int> = _lastTempC.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var appContext: Context? = null
    private var trips: PerfNodeResolver.ThermalTrips? = null

    val firstTripC: Int? get() = trips?.firstTripC
    val topTripC: Int? get() = trips?.topTripC
    val hasDeviceTrips: Boolean get() = trips?.topTripC != null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = preferences.getBoolean(KEY_ENABLED, true)
        _mode.value = try {
            ThresholdMode.valueOf(
                preferences.getString(KEY_MODE, ThresholdMode.BALANCED.name)
                    ?: ThresholdMode.BALANCED.name,
            )
        } catch (_: Exception) {
            ThresholdMode.BALANCED
        }
        trips = PerfNodeResolver.thermalTrips()
        val savedManual = preferences.getInt(KEY_MANUAL, -1)
        _manualCeilingC.value =
            if (savedManual > 0) savedManual else resolvedCeilingFor(ThresholdMode.BALANCED)
        recomputeCeiling()
    }

    fun setWatchdogEnabled(value: Boolean) {
        _enabled.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    fun setMode(value: ThresholdMode) {
        _mode.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_MODE, value.name)?.apply()
        recomputeCeiling()
    }

    fun setManualCeilingC(value: Int) {
        val upper = maxOf(MANUAL_MIN_C, topTripC ?: MANUAL_MAX_C)
        val capped = value.coerceIn(MANUAL_MIN_C, upper)
        _manualCeilingC.value = capped
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putInt(KEY_MANUAL, capped)?.apply()
        if (_mode.value == ThresholdMode.MANUAL) recomputeCeiling()
    }

    fun resolvedCeilingFor(value: ThresholdMode): Int {
        val first = trips?.firstTripC
        val top = trips?.topTripC
        return when (value) {
            ThresholdMode.CONSERVATIVE -> first ?: FALLBACK_CEILING_C
            ThresholdMode.BALANCED -> top?.minus(BALANCED_MARGIN) ?: FALLBACK_CEILING_C
            ThresholdMode.AGGRESSIVE -> top?.minus(AGGRESSIVE_MARGIN) ?: FALLBACK_CEILING_C
            ThresholdMode.MANUAL ->
                _manualCeilingC.value.coerceIn(
                    MANUAL_MIN_C,
                    maxOf(MANUAL_MIN_C, top ?: MANUAL_MAX_C),
                )
        }
    }

    private fun recomputeCeiling() {
        _ceilingC.value = resolvedCeilingFor(_mode.value)
    }

    fun start(context: Context) {
        if (appContext == null) init(context)
        if (job?.isActive == true) return
        var consecutiveTrips = 0
        job = scope.launch {
            while (isActive) {
                if (_enabled.value) {
                    val temperature = PerfNodeResolver.readHottestWatchedC()
                    if (temperature != null) {
                        _lastTempC.value = temperature
                        if (temperature >= _ceilingC.value) {
                            consecutiveTrips++
                            if (consecutiveTrips >= TRIP_COUNT) {
                                Log.w(
                                    TAG,
                                    "$temperature°C >= ${_ceilingC.value}°C; reverting performance state",
                                )
                                try {
                                    PerfRevertRegistry.revertAll()
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Watchdog revert failed", t)
                                }
                                consecutiveTrips = 0
                            }
                        } else {
                            consecutiveTrips = 0
                        }
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _lastTempC.value = 0
    }
}
