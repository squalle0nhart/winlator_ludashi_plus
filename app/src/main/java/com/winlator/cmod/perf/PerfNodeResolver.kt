package com.winlator.cmod.perf

import java.io.File
import java.util.Locale

/** Discovers real performance-control nodes on the current SoC without writing to them. */
object PerfNodeResolver {
    data class CpuCoreNodes(
        val index: Int,
        val governor: String?,
        val minFreq: String?,
        val maxFreq: String?,
        val online: String?,
    )

    data class GpuNodes(
        val devfreqGovernor: String?,
        val minClock: String?,
        val maxClock: String?,
        val forceClkOn: String?,
        val forceBusOn: String?,
    )

    data class ThermalTrips(
        val firstTripC: Int?,
        val topTripC: Int?,
        val watchedTempPaths: List<String>,
    )

    private var cpuCache: List<CpuCoreNodes>? = null
    private var gpuCache: GpuNodes? = null
    private var thermalModeCache: List<String>? = null
    private var thermalTripsCache: ThermalTrips? = null
    private var fanCache: List<String>? = null

    private val THERMAL_WATCH_TOKENS = arrayOf(
        "cpu", "gpu", "soc", "cluster", "tsens", "mtktscpu", "mtktsgpu",
        "g3d", "kgsl", "mali", "apu", "silicon", "big", "little", "cpuss", "gpuss",
    )
    private val GPU_NODE_TOKENS = arrayOf(
        "gpu", "mali", "g3d", "kgsl", "panfrost", "pvr", "powervr", "xclipse", "sgpu",
    )

    private fun existing(path: String): String? = if (File(path).exists()) path else null

    fun cpuCores(): List<CpuCoreNodes> {
        cpuCache?.let { return it }
        val count = Runtime.getRuntime().availableProcessors()
        return ArrayList<CpuCoreNodes>(count).apply {
            for (index in 0 until count) {
                val base = "/sys/devices/system/cpu/cpu$index"
                val cpufreq = "$base/cpufreq"
                add(
                    CpuCoreNodes(
                        index,
                        existing("$cpufreq/scaling_governor"),
                        existing("$cpufreq/scaling_min_freq"),
                        existing("$cpufreq/scaling_max_freq"),
                        existing("$base/online"),
                    ),
                )
            }
        }.also { cpuCache = it }
    }

    fun gpu(): GpuNodes {
        gpuCache?.let { return it }
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        val kgslDevfreq = "$kgsl/devfreq"
        var governor = existing("$kgslDevfreq/governor")
        var minClock = existing("$kgsl/min_clock_mhz")
        var maxClock = existing("$kgsl/max_clock_mhz")
        val forceClkOn = existing("$kgsl/force_clk_on")
        val forceBusOn = existing("$kgsl/force_bus_on")
        if (governor == null || minClock == null || maxClock == null) {
            findDevfreqGpuDir()?.let { gpuDir ->
                if (governor == null) governor = existing("$gpuDir/governor")
                if (minClock == null) minClock = existing("$gpuDir/min_freq")
                if (maxClock == null) maxClock = existing("$gpuDir/max_freq")
            }
        }
        return GpuNodes(governor, minClock, maxClock, forceClkOn, forceBusOn)
            .also { gpuCache = it }
    }

    private fun findDevfreqGpuDir(): String? {
        val roots = arrayOf(File("/sys/class/devfreq"), File("/sys/devices/virtual/devfreq"))
        roots.forEach { root ->
            if (!root.isDirectory) return@forEach
            root.listFiles(File::isDirectory)?.forEach { node ->
                val name = node.path.lowercase(Locale.US)
                if (GPU_NODE_TOKENS.any { name.contains(it) } && File(node, "governor").exists()) {
                    return node.path
                }
            }
        }
        return null
    }

    fun thermalZoneModes(): List<String> {
        thermalModeCache?.let { return it }
        val result = LinkedHashSet<String>()
        arrayOf(File("/sys/class/thermal"), File("/sys/devices/virtual/thermal")).forEach { dir ->
            dir.listFiles { _, name -> name.startsWith("thermal_zone") }?.forEach { zone ->
                val mode = File(zone, "mode")
                if (zone.isDirectory && mode.exists()) result.add(mode.path)
            }
        }
        return result.toList().also { thermalModeCache = it }
    }

    fun fanNodes(): List<String> {
        fanCache?.let { return it }
        val result = LinkedHashSet<String>()
        File("/sys/class/hwmon").listFiles(File::isDirectory)?.forEach { chip ->
            chip.listFiles { _, name ->
                name.matches(Regex("pwm\\d+")) ||
                    name.matches(Regex("pwm\\d+_enable")) ||
                    name.matches(Regex("fan\\d+_target"))
            }?.forEach { result.add(it.path) }
        }
        File("/sys/class/thermal").listFiles { _, name ->
            name.startsWith("cooling_device")
        }?.forEach { device ->
            val type = try {
                File(device, "type").readText().trim().lowercase(Locale.US)
            } catch (_: Exception) {
                ""
            }
            if (type.contains("fan")) existing("${device.path}/cur_state")?.let(result::add)
        }
        return result.toList().also { fanCache = it }
    }

    fun thermalTrips(): ThermalTrips {
        thermalTripsCache?.let { return it }
        val watchedTemps = LinkedHashSet<String>()
        val trips = ArrayList<Int>()
        arrayOf(File("/sys/class/thermal"), File("/sys/devices/virtual/thermal")).forEach { dir ->
            dir.listFiles { _, name -> name.startsWith("thermal_zone") }?.forEach { zone ->
                if (!zone.isDirectory) return@forEach
                val type = readLine(File(zone, "type"))?.lowercase(Locale.US) ?: return@forEach
                if (THERMAL_WATCH_TOKENS.none { type.contains(it) }) return@forEach
                File(zone, "temp").takeIf(File::exists)?.let { watchedTemps.add(it.path) }
                for (index in 0..15) {
                    val raw = readLine(File(zone, "trip_point_${index}_temp"))
                        ?.trim()?.toIntOrNull() ?: continue
                    val celsius = if (raw > 1000) (raw + 500) / 1000 else raw
                    if (celsius in 40..200) trips.add(celsius)
                }
            }
        }
        return ThermalTrips(trips.minOrNull(), trips.maxOrNull(), watchedTemps.toList())
            .also { thermalTripsCache = it }
    }

    fun readHottestWatchedC(): Int? {
        var hottest: Int? = null
        thermalTrips().watchedTempPaths.forEach { path ->
            val raw = readLine(File(path))?.trim()?.toIntOrNull() ?: return@forEach
            val celsius = if (raw > 1000) (raw + 500) / 1000 else raw
            if (celsius in 1..200) hottest = maxOf(hottest ?: celsius, celsius)
        }
        if (hottest == null) {
            arrayOf(File("/sys/class/thermal"), File("/sys/devices/virtual/thermal")).forEach { dir ->
                dir.listFiles { _, name -> name.startsWith("thermal_zone") }?.forEach zoneLoop@ { zone ->
                    val raw = readLine(File(zone, "temp"))?.trim()?.toIntOrNull() ?: return@zoneLoop
                    val celsius = if (raw > 1000) (raw + 500) / 1000 else raw
                    if (celsius in 1..200) hottest = maxOf(hottest ?: celsius, celsius)
                }
            }
        }
        return hottest
    }

    fun allKnownNodes(): List<String> = buildList {
        cpuCores().forEach { core ->
            addAll(listOfNotNull(core.governor, core.minFreq, core.maxFreq, core.online))
        }
        gpu().let { gpu ->
            addAll(
                listOfNotNull(
                    gpu.devfreqGovernor,
                    gpu.minClock,
                    gpu.maxClock,
                    gpu.forceClkOn,
                    gpu.forceBusOn,
                ),
            )
        }
        addAll(thermalZoneModes())
        addAll(fanNodes())
    }

    private fun readLine(file: File): String? = try {
        if (file.exists()) file.bufferedReader().use { it.readLine() } else null
    } catch (_: Exception) {
        null
    }
}
