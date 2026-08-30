package com.winlator.cmod.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R

@Composable
internal fun ClassicWelcomeLandscape(
    ready: State<Boolean>,
    progress: State<Int>,
    start: () -> Unit,
    skip: () -> Unit,
    retry: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(.9f), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.winlator_mark_exact), "Winlator", Modifier.size(132.dp))
                Spacer(Modifier.height(14.dp))
                Text("Welcome to Winlator", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Your lightweight PC emulator for Android.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                ClassicWelcomeCard(Icons.Outlined.Apps, "Get started", "Choose components", start)
                Spacer(Modifier.height(10.dp))
                ClassicWelcomeCard(Icons.Outlined.SkipNext, "Skip", "Use the components bundled with Winlator and configure everything later.", skip)
                Spacer(Modifier.height(10.dp))
                ClassicCoreStatusCard(ready, progress, retry)
            }
        }
    }
}
