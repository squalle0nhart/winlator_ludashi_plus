package com.winlator.cmod.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EnvironmentVariablesEditor(
    value: String,
    modifier: Modifier = Modifier,
    onValueChanged: (String) -> Unit
) {
    EnvironmentVariablesEditor(
        value = value,
        onChanged = onValueChanged,
        modifier = modifier
    )
}
