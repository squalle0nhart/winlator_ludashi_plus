package com.winlator.cmod.core

import com.winlator.cmod.contents.ContentProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class WineInfoTest {
    @Test
    fun downloadedX8664ProfileUsesTypeFromFullEntryIdentifier() {
        assertEquals(
            "proton-9.0-x86_64",
            normalizeIdentifier(
                "Proton-9.0-x86_64-0",
                ContentProfile.ContentType.CONTENT_TYPE_PROTON,
                "9.0-x86_64",
            ),
        )
    }

    @Test
    fun unprefixedArm64ecProfileUsesItsContentType() {
        assertEquals(
            "proton-11.1-ge-arm64ec",
            normalizeIdentifier(
                "Proton-11.1-ge-arm64ec-steam-0",
                ContentProfile.ContentType.CONTENT_TYPE_PROTON,
                "11.1-ge-arm64ec",
            ),
        )
    }

    private fun normalizeIdentifier(
        identifier: String,
        type: ContentProfile.ContentType,
        versionName: String,
    ): String? {
        val profile = ContentProfile().apply {
            this.type = type
            verName = versionName
        }
        val method = WineInfo::class.java.getDeclaredMethod(
            "normalizeIdentifier",
            String::class.java,
            ContentProfile::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, identifier, profile) as String?
    }
}
