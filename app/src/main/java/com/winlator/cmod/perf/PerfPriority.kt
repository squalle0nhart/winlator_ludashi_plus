package com.winlator.cmod.perf

import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Raises guest CPU-worker and relevant app threads without ever lowering an already-higher priority.
 * Every changed nice value is captured and restored exactly.
 */
object PerfPriority {
    private const val TAG = "PerfPriority"

    private val BOOST_LADDER = intArrayOf(
        Process.THREAD_PRIORITY_URGENT_DISPLAY,
        Process.THREAD_PRIORITY_DISPLAY,
        Process.THREAD_PRIORITY_FOREGROUND,
    )
    private val APP_THREAD_TOKENS = arrayOf("audio", "worker", "render", "present", "vk", "gl")
    private val originalNice = ConcurrentHashMap<Int, Int>()

    fun boost(guestRootPid: Int): Int {
        var count = 0
        collectAppThreadTids().forEach { if (boostTid(it)) count++ }
        if (guestRootPid > 0) collectGuestTids(guestRootPid).forEach { if (boostTid(it)) count++ }
        Log.d(TAG, "Moved $count thread(s), guestRootPid=$guestRootPid")
        return count
    }

    fun restore(): Int {
        var count = 0
        originalNice.forEach { (tid, nice) ->
            try {
                Process.setThreadPriority(tid, nice)
                count++
            } catch (_: Throwable) {
            }
        }
        originalNice.clear()
        Log.d(TAG, "Restored $count thread(s)")
        return count
    }

    private fun boostTid(tid: Int): Boolean {
        val current = try {
            Process.getThreadPriority(tid)
        } catch (_: Throwable) {
            return false
        }
        for (target in BOOST_LADDER) {
            if (target >= current) continue
            try {
                originalNice.putIfAbsent(tid, current)
                Process.setThreadPriority(tid, target)
                val after = try {
                    Process.getThreadPriority(tid)
                } catch (_: Throwable) {
                    target
                }
                if (after < current) return true
                if (after == current) originalNice.remove(tid)
            } catch (_: Throwable) {
                originalNice.remove(tid)
            }
        }
        return false
    }

    private fun collectAppThreadTids(): List<Int> {
        val tasks = File("/proc/self/task").listFiles() ?: return emptyList()
        val result = ArrayList<Int>()
        tasks.forEach { task ->
            val tid = task.name.toIntOrNull() ?: return@forEach
            val name = try {
                File(task, "comm").readText().trim().lowercase()
            } catch (_: Exception) {
                return@forEach
            }
            if (APP_THREAD_TOKENS.any { name.contains(it) }) result.add(tid)
        }
        return result
    }

    private fun collectGuestTids(rootPid: Int): List<Int> {
        val procDirs = File("/proc").listFiles { file ->
            file.isDirectory && file.name.toIntOrNull() != null
        } ?: return emptyList()
        val parents = HashMap<Int, Int>()
        val pids = ArrayList<Int>()
        procDirs.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            val stat = try {
                File(dir, "stat").readText()
            } catch (_: Exception) {
                return@forEach
            }
            val rightParen = stat.lastIndexOf(')')
            if (rightParen < 0 || rightParen + 2 >= stat.length) return@forEach
            val fields = stat.substring(rightParen + 2).trim().split(" ")
            val parent = fields.getOrNull(1)?.toIntOrNull() ?: return@forEach
            parents[pid] = parent
            pids.add(pid)
        }

        val subtree = hashSetOf(rootPid)
        var changed = true
        while (changed) {
            changed = false
            pids.forEach { pid ->
                if (pid !in subtree && parents[pid] in subtree) {
                    subtree.add(pid)
                    changed = true
                }
            }
        }

        val result = ArrayList<Int>()
        subtree.forEach { pid ->
            File("/proc/$pid/task").listFiles()?.forEach { task ->
                task.name.toIntOrNull()?.let(result::add)
            }
        }
        return result
    }
}
