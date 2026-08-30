package com.winlator.cmod.ui.settings

internal fun isVkd3dEnabled(version: String): Boolean =
    version.isNotBlank() && !version.equals("None", ignoreCase = true)

internal fun isDxvkCompatibleWithVkd3d(version: String): Boolean {
    val match = Regex("(\\d+)\\.(\\d+)").find(version) ?: return false
    return (match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0) >= 2
}

internal fun filterDxvkForVkd3d(catalog: VersionCatalog, vkd3dVersion: String): VersionCatalog {
    if (!isVkd3dEnabled(vkd3dVersion)) return catalog
    val allowed = catalog.all.filter(::isDxvkCompatibleWithVkd3d)
    val installed = catalog.installed.filterTo(linkedSetOf(), ::isDxvkCompatibleWithVkd3d)
    return VersionCatalog(allowed, installed)
}

internal fun normalizeLocaleValue(value: String): String {
    val clean = value.trim()
    if (clean.isBlank() || clean.equals("Default", ignoreCase = true)) return ""
    return if (clean.contains('.')) clean else "$clean.UTF-8"
}

internal fun localeDisplayValue(value: String): String =
    value.trim().removeSuffix(".UTF-8").removeSuffix(".utf8").ifBlank { "Default" }
