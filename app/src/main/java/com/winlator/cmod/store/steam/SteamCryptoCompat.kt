package com.winlator.cmod.store

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

/**
 * JavaSteam hardcodes provider name "BC" for SHA-1/AES operations.
 *
 * On Android, Security.insertProviderAt() does not replace an existing provider
 * with the same name, so JavaSteam can end up binding to the platform "BC"
 * entry instead of the bundled bcprov implementation.
 */
object SteamCryptoCompat {
    private const val TAG = "SteamCrypto"

    @Volatile
    private var ready = false

    @JvmStatic
    @Synchronized
    fun ensureBcSha1() {
        if (ready && hasBcSha1()) return
        if (hasBcSha1()) {
            ready = true
            return
        }

        val current = Security.getProvider("BC")
        Log.w(
            TAG,
            "Provider BC missing SHA-1, reinstalling bundled bcprov (current=${current?.javaClass?.name ?: "missing"})",
        )

        if (current != null) {
            Security.removeProvider("BC")
        }
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        check(hasBcSha1()) { "Provider BC still does not expose SHA-1 after bcprov reinstall" }
        ready = true
    }

    @JvmStatic
    fun currentBcProviderSummary(): String {
        val provider = Security.getProvider("BC")
        return if (provider == null) {
            "BC=missing"
        } else {
            "BC=${provider.javaClass.name} v${provider.version}"
        }
    }

    private fun hasBcSha1(): Boolean = try {
        MessageDigest.getInstance("SHA-1", "BC")
        true
    } catch (_: Exception) {
        false
    }
}
