package com.winlator.cmod.perf

import android.util.Log

/** Applies root-only controls through snapshot-before-write and exact per-toggle restore. */
object PerfRootApplier {
    private const val TAG = "PerfRootApplier"

    const val KEY_CPU_GOVERNOR = "rootCpuGovernorPerf"
    const val KEY_CPU_FREQ_LOCK = "rootCpuFreqLockMax"
    const val KEY_CORES_ONLINE = "rootAllCoresOnline"
    const val KEY_GPU_CLOCK_LOCK = "rootGpuMaxClockLock"
    const val KEY_THERMAL_DISABLE = "rootThermalDisable"
    const val KEY_FAN_MAX = "rootFanMax"

    val ROOT_KEYS = listOf(
        KEY_CPU_GOVERNOR,
        KEY_CPU_FREQ_LOCK,
        KEY_CORES_ONLINE,
        KEY_GPU_CLOCK_LOCK,
        KEY_THERMAL_DISABLE,
        KEY_FAN_MAX,
    )
    private val HARNESS_GATED = setOf(KEY_THERMAL_DISABLE, KEY_FAN_MAX)

    fun isHarnessGated(key: String): Boolean = key in HARNESS_GATED

    fun apply(key: String, on: Boolean) {
        synchronized(PerfRevertRegistry) {
            when (key) {
                KEY_CPU_GOVERNOR -> applyCpuGovernorPerformance(on)
                KEY_CPU_FREQ_LOCK -> applyCpuFreqLockMax(on)
                KEY_CORES_ONLINE -> applyAllCoresOnline(on)
                KEY_GPU_CLOCK_LOCK -> applyGpuMaxClockLock(on)
                KEY_THERMAL_DISABLE -> applyThermalDisable(on)
                KEY_FAN_MAX -> applyFanMax(on)
                else -> Log.w(TAG, "Unknown control key: $key")
            }
        }
    }

    private inline fun guarded(key: String, body: () -> Unit) {
        if (!RootManager.isGranted) return
        if (isHarnessGated(key) && !PerfRevertRegistry.harnessProven.value) return
        try {
            body()
        } catch (t: Throwable) {
            Log.w(TAG, "Applying $key failed", t)
        }
    }

    private fun applyCpuGovernorPerformance(on: Boolean) = guarded(KEY_CPU_GOVERNOR) {
        val nodes = PerfNodeResolver.cpuCores().mapNotNull { it.governor }
        if (on) nodes.forEach { PerfRevertRegistry.applyWrite(it, "performance") }
        else PerfRevertRegistry.revertNodes(nodes)
    }

    private fun applyCpuFreqLockMax(on: Boolean) = guarded(KEY_CPU_FREQ_LOCK) {
        val cores = PerfNodeResolver.cpuCores()
        if (on) {
            cores.forEach { core ->
                val minNode = core.minFreq ?: return@forEach
                val maxNode = core.maxFreq ?: return@forEach
                RootManager.readNode(maxNode)?.let { PerfRevertRegistry.applyWrite(minNode, it) }
            }
        } else {
            PerfRevertRegistry.revertNodes(cores.mapNotNull { it.minFreq })
        }
    }

    private fun applyAllCoresOnline(on: Boolean) = guarded(KEY_CORES_ONLINE) {
        val nodes = PerfNodeResolver.cpuCores().mapNotNull { it.online }
        if (on) nodes.forEach { PerfRevertRegistry.applyWrite(it, "1") }
        else PerfRevertRegistry.revertNodes(nodes)
    }

    private fun applyGpuMaxClockLock(on: Boolean) = guarded(KEY_GPU_CLOCK_LOCK) {
        val gpu = PerfNodeResolver.gpu()
        val touched = listOfNotNull(gpu.minClock, gpu.forceClkOn, gpu.forceBusOn)
        if (on) {
            gpu.maxClock?.let { maxNode ->
                RootManager.readNode(maxNode)?.let { maxValue ->
                    gpu.minClock?.let { PerfRevertRegistry.applyWrite(it, maxValue) }
                }
            }
            gpu.forceClkOn?.let { PerfRevertRegistry.applyWrite(it, "1") }
            gpu.forceBusOn?.let { PerfRevertRegistry.applyWrite(it, "1") }
        } else {
            PerfRevertRegistry.revertNodes(touched)
        }
    }

    private fun applyThermalDisable(on: Boolean) = guarded(KEY_THERMAL_DISABLE) {
        val nodes = PerfNodeResolver.thermalZoneModes()
        if (on) nodes.forEach { PerfRevertRegistry.applyWrite(it, "disabled") }
        else PerfRevertRegistry.revertNodes(nodes)
    }

    private fun applyFanMax(on: Boolean) = guarded(KEY_FAN_MAX) {
        val nodes = PerfNodeResolver.fanNodes()
        if (on) nodes.forEach { PerfRevertRegistry.applyWrite(it, fanMaxValueFor(it)) }
        else PerfRevertRegistry.revertNodes(nodes)
    }

    private fun fanMaxValueFor(path: String): String = when {
        path.endsWith("_enable") -> "1"
        path.contains("/pwm") -> "255"
        path.endsWith("cur_state") ->
            RootManager.readNode(path.removeSuffix("cur_state") + "max_state") ?: "1"
        path.endsWith("_target") -> "255"
        else -> "1"
    }

    fun freeMemoryNow(): Boolean =
        RootManager.isGranted && RootManager.writeNode("/proc/sys/vm/drop_caches", "3")

    fun applyEffective(effective: Map<String, Boolean>) {
        if (!RootManager.isGranted) return
        synchronized(PerfRevertRegistry) {
            ROOT_KEYS.forEach { apply(it, effective[it] ?: false) }
        }
    }
}
