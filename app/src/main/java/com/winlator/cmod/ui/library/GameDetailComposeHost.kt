package com.winlator.cmod.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.MainActivity
import com.winlator.cmod.ui.KeepLandscapeChromeHidden
import com.winlator.cmod.ui.applyAppFullscreen
import com.winlator.cmod.ui.theme.WinZTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface GameDetailCallbacks {
    fun onPlay()
    fun onConfigure()
    fun onArguments()
    fun onGameFolder()
    fun onFavorite(favorite: Boolean)
    fun onRemove()
}

object GameDetailComposeHost {
    @JvmStatic
    fun create(context: Context, title: String, subtitle: String, artworkPath: String?, fallback: Bitmap?, favorite: Boolean, callbacks: GameDetailCallbacks): ComposeView {
        applyAppFullscreen(context as? MainActivity)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { WinZTheme { GameDetailScreen(title, subtitle, artworkPath, fallback, favorite, callbacks) } }
        }
    }
}

@Composable
private fun GameDetailScreen(title: String, subtitle: String, artworkPath: String?, fallback: Bitmap?, initialFavorite: Boolean, callbacks: GameDetailCallbacks) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val activity = LocalContext.current as? MainActivity

    if (landscape) {
        KeepLandscapeChromeHidden(activity, restoreChromeOnPortrait = false)
    }

    DisposableEffect(activity, landscape) {
        if (!landscape) {
            activity?.setBottomNavigationVisible(false)
            activity?.setMainToolbarVisible(true)
        }
        onDispose { }
    }

    val artwork by produceState<Bitmap?>(fallback, artworkPath, fallback) {
        value = withContext(Dispatchers.IO) { artworkPath?.takeIf { File(it).isFile }?.let(BitmapFactory::decodeFile) ?: fallback }
    }
    var favorite by remember(initialFavorite) { mutableStateOf(initialFavorite) }
    val toggle = {
        favorite = !favorite
        callbacks.onFavorite(favorite)
    }
    if (landscape) LandscapeDetail(title, subtitle, artwork, favorite, callbacks, toggle)
    else PortraitDetail(title, subtitle, artwork, favorite, callbacks, toggle)
}

@Composable
private fun LandscapeDetail(title: String, subtitle: String, artwork: Bitmap?, favorite: Boolean, callbacks: GameDetailCallbacks, toggleFavorite: () -> Unit) {
    val activity = LocalContext.current as? MainActivity
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (artwork != null) Image(artwork.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.93f), Color.Black.copy(.70f), Color.Black.copy(.28f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.18f), Color.Transparent, Color.Black.copy(.55f)))))

        Surface(
            onClick = { activity?.onBackPressedDispatcher?.onBackPressed() },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 16.dp).size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(.62f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(.16f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, "Back", modifier = Modifier.size(24.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(start = 78.dp, end = 34.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight().widthIn(max = 590.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(.42f),
                border = BorderStroke(1.dp, Color.White.copy(.16f))
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(subtitle, color = Color.White.copy(.72f), style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = toggleFavorite) {
                            Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = callbacks::onPlay,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailAction(Icons.Outlined.Settings, "Configure", Modifier.weight(1f), callbacks::onConfigure)
                        DetailAction(Icons.Outlined.PlayArrow, "Enter container", Modifier.weight(1f), callbacks::onArguments)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailAction(Icons.Outlined.Folder, "Game folder", Modifier.weight(1f), callbacks::onGameFolder)
                        DetailAction(Icons.Outlined.DeleteOutline, "Remove", Modifier.weight(1f), callbacks::onRemove, true)
                    }
                }
            }
            Spacer(Modifier.weight(.75f))
        }
    }
}

@Composable
private fun PortraitDetail(title: String, subtitle: String, artwork: Bitmap?, favorite: Boolean, callbacks: GameDetailCallbacks, toggleFavorite: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.28f).background(MaterialTheme.colorScheme.surface)) {
            if (artwork != null) Image(artwork.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.18f), Color.Transparent, Color.Black.copy(.88f)))))
            IconButton(onClick = toggleFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp)) {
                Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favorite", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(.72f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = callbacks::onPlay,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.size(8.dp)); Text("Play", fontWeight = FontWeight.Bold) }
            DetailAction(Icons.Outlined.Settings, "Configure", Modifier.fillMaxWidth(), callbacks::onConfigure)
            DetailAction(Icons.Outlined.PlayArrow, "Enter container", Modifier.fillMaxWidth(), callbacks::onArguments)
            DetailAction(Icons.Outlined.Folder, "Game folder", Modifier.fillMaxWidth(), callbacks::onGameFolder)
            DetailAction(Icons.Outlined.DeleteOutline, "Remove", Modifier.fillMaxWidth(), callbacks::onRemove, true)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(.22f) else MaterialTheme.colorScheme.surface.copy(.90f),
        contentColor = tint,
        border = BorderStroke(1.dp, if (destructive) MaterialTheme.colorScheme.error.copy(.62f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.size(10.dp))
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
