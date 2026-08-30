package com.winlator.cmod.ui.container

import androidx.compose.runtime.Stable

@Stable
interface ContainerInlineCallbacks {
    fun onInstallComponent(type: String, version: String)
    fun onManageComponents()
    fun onSaved()
}
