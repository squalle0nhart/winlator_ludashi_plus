package com.winlator.cmod.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfBuildersTest {
    @Test
    fun shellCommandsQuoteValuesAndPaths() {
        assertEquals("'a'\\''b'", PerfCmd.shellQuote("a'b"))
        assertEquals("cat '/sys/example node'", PerfCmd.readCmd("/sys/example node"))
        assertEquals(
            "printf %s 'performance' > '/sys/example node'",
            PerfCmd.writeCmd("/sys/example node", "performance"),
        )
    }

    @Test
    fun bigCoreDetectionSupportsPrimeAndClusterLayouts() {
        assertEquals("7", CpuTopology.bigCoreCpuList(intArrayOf(1800, 1800, 1800, 1800, 2500, 2500, 2500, 3200)))
        assertEquals("4,5,6,7", CpuTopology.bigCoreCpuList(intArrayOf(1800, 1800, 1800, 1800, 2800, 2800, 2800, 2800)))
        assertTrue(CpuTopology.bigCoreIndices(intArrayOf(0, 0, 0, 0)).isEmpty())
    }
}
