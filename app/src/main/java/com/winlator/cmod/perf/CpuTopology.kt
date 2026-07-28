package com.winlator.cmod.perf

import com.winlator.cmod.core.CPUStatus

/** Detects the highest-frequency CPU cluster for the non-root "Prefer Big Cores" control. */
object CpuTopology {
    fun bigCoreIndices(maxFreqs: IntArray): List<Int> {
        val top = maxFreqs.filter { it > 0 }.maxOrNull() ?: return emptyList()
        return maxFreqs.indices.filter { maxFreqs[it] == top }
    }

    fun bigCoreCpuList(maxFreqs: IntArray): String =
        bigCoreIndices(maxFreqs).joinToString(",")

    fun detectBigCoreCpuList(): String {
        val count = Runtime.getRuntime().availableProcessors()
        if (count <= 0) return ""
        return bigCoreCpuList(IntArray(count) { CPUStatus.getMaxClockSpeed(it).toInt() })
    }
}
