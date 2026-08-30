package com.winlator.cmod.ui.onboarding

internal val OnboardingCallbacks.onBrowseLocal: () -> Unit
    get() = { onBrowseLocal() }

internal val OnboardingCallbacks.onBrowseDriver: () -> Unit
    get() = { onBrowseDriver() }
