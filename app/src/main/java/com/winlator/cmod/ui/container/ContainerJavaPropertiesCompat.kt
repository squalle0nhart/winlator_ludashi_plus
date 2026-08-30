package com.winlator.cmod.ui.container

import com.winlator.cmod.container.Container

internal val Container.dxWrapper: String
    get() = getDXWrapper()

internal val Container.dxWrapperConfig: String
    get() = getDXWrapperConfig()

internal val Container.fexCoreVersion: String?
    get() = getFEXCoreVersion()

internal val Container.fexCorePreset: String
    get() = getFEXCorePreset()
