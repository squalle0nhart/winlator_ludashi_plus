package com.winlator.cmod.perf

/** Side-effect-free shell command builders used by the privileged performance-control path. */
object PerfCmd {
    fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    fun writeCmd(path: String, value: String): String =
        "printf %s ${shellQuote(value)} > ${shellQuote(path)}"

    fun readCmd(path: String): String = "cat ${shellQuote(path)}"

    fun normalizeRead(raw: String): String = raw.trim()
}
