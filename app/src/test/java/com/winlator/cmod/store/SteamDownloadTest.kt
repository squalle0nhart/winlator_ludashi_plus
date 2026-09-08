package com.winlator.cmod.store

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SteamDownloadTest {
    @Test
    fun `progress sums depots and preserves resume floor and monotonic counters`() {
        val progress = SteamDepotDownloader.Progress(120L, 200L)
        assertEquals(120L to 200L, progress.update(1, 50L, 0.5f))
        assertEquals(150L to 200L, progress.update(2, 100L, 0.5f))
        assertEquals(150L to 200L, progress.update(1, 20L, 0.2f))
        assertEquals(250L to 250L, progress.update(1, 150L, 1f))
        val unknown = SteamDepotDownloader.Progress(0L, 0L)
        assertEquals(50L to 100L, unknown.update(1, 50L, 0.5f))
        assertEquals(150L to 300L, unknown.update(2, 100L, 0.5f))
    }

    @Test
    fun `pending engine future releases on cancel pause and failure`() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            for (pause in listOf(false, true)) {
                val cancelled = AtomicBoolean(false)
                val paused = AtomicBoolean(false)
                val waiting = CountDownLatch(1)
                val completion = object : CompletableFuture<Void>() {
                    override fun get(timeout: Long, unit: TimeUnit): Void? {
                        waiting.countDown()
                        return super.get(timeout, unit)
                    }
                }
                val task = worker.submit {
                    SteamDepotDownloader.awaitDownload(completion, cancelled, paused, AtomicReference(null))
                }
                assertTrue(waiting.await(3L, TimeUnit.SECONDS))
                if (pause) paused.set(true) else cancelled.set(true)
                task.get(3L, TimeUnit.SECONDS)
                assertFalse(completion.isDone)
            }
            val error = IllegalStateException("depot unavailable")
            try {
                SteamDepotDownloader.awaitDownload(
                    CompletableFuture(), AtomicBoolean(false), AtomicBoolean(false), AtomicReference(error),
                )
                fail("Callback failure must propagate even if the engine future stays pending")
            } catch (actual: IllegalStateException) { assertSame(error, actual) }
        } finally { worker.shutdownNow() }
    }

    @Test
    fun `library and depot filters include current Lossless Scaling and Windows content`() {
        fun config(os: String = "", language: String = "", lowViolence: String = "") = KeyValue("config").apply {
            children.add(KeyValue("oslist", os))
            children.add(KeyValue("language", language))
            children.add(KeyValue("lowviolence", lowViolence))
        }
        assertTrue(SteamRepository.isLibraryApp(993090, "application"))
        assertFalse(SteamRepository.isLibraryApp(1, "tool"))
        assertTrue(SteamRepository.isSelectedDepot(1, 2, config()))
        assertTrue(SteamRepository.isSelectedDepot(1, 2, config("linux, windows", "english")))
        assertFalse(SteamRepository.isSelectedDepot(1, 2, config("linux,macos")))
        assertFalse(SteamRepository.isSelectedDepot(1, 2, config(language = "german")))
        assertFalse(SteamRepository.isSelectedDepot(1, 2, config(lowViolence = "1")))
        assertFalse(SteamRepository.isSelectedDepot(993090, 993092, config()))
        assertTrue(SteamRepository.isSelectedDepot(993090, 993091, config()))
    }
}
