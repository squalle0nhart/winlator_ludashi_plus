package com.winlator.cmod.ui.onboarding

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import kotlin.reflect.KProperty

internal operator fun <T> State<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

internal operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
    value = newValue
}
