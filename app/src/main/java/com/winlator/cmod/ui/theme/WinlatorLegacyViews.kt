package com.winlator.cmod.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.winlator.cmod.R

private fun ripple(color: Int, contentColor: Int): RippleDrawable {
    val rippleColor = Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))
    return RippleDrawable(ColorStateList.valueOf(rippleColor), ColorDrawable(contentColor), null)
}

private fun roundedDrawable(context: Context, color: Int, radiusDp: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
    }

private fun roundedRipple(context: Context, color: Int, contentColor: Int, radiusDp: Float): RippleDrawable {
    val rippleColor = Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))
    return RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        roundedDrawable(context, contentColor, radiusDp),
        roundedDrawable(context, Color.WHITE, radiusDp)
    )
}

class ThemedFileManagerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()
        applyTheme()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyTheme()
    }

    private fun applyTheme() {
        val bg = WinlatorLegacyTheme.background(context)
        val surface = WinlatorLegacyTheme.surface(context)
        val surfaceVariant = WinlatorLegacyTheme.surfaceVariant(context)
        val onBg = WinlatorLegacyTheme.onBackground(context)
        val muted = WinlatorLegacyTheme.onSurfaceVariant(context)
        val primary = WinlatorLegacyTheme.primary(context)
        val onPrimary = WinlatorLegacyTheme.onPrimary(context)
        val outline = WinlatorLegacyTheme.outlineVariant(context)

        setBackgroundColor(bg)
        findViewById<View>(R.id.FileManagerContent)?.setBackgroundColor(bg)
        findViewById<View>(R.id.FileManagerHeader)?.setBackgroundColor(bg)
        findViewById<RecyclerView>(R.id.RecyclerViewFiles)?.setBackgroundColor(bg)
        findViewById<View>(R.id.FileManagerDivider)?.setBackgroundColor(outline)
        findViewById<View>(R.id.FileManagerPathCard)?.background = roundedDrawable(context, surface, 12f)
        findViewById<View>(R.id.DriveOptionsPanel)?.background = roundedDrawable(context, surface, 12f)
        findViewById<View>(R.id.FileManagerStorageCard)?.background = roundedDrawable(context, surface, 12f)

        findViewById<TextView>(R.id.TVCurrentPath)?.setTextColor(muted)
        findViewById<TextView>(R.id.TVDriveName)?.setTextColor(onBg)
        findViewById<TextView>(R.id.TVDriveStorageLabel)?.setTextColor(muted)
        findViewById<TextView>(R.id.TVDriveStorage)?.setTextColor(onBg)
        findViewById<ImageView>(R.id.IVDriveIcon)?.imageTintList = ColorStateList.valueOf(onBg)
        findViewById<ImageView>(R.id.IVDriveArrow)?.imageTintList = ColorStateList.valueOf(muted)

        findViewById<ProgressBar>(R.id.PBDriveStorage)?.let { progress ->
            progress.progressTintList = ColorStateList.valueOf(primary)
            progress.progressBackgroundTintList = ColorStateList.valueOf(surfaceVariant)
        }

        findViewById<ImageButton>(R.id.BTUpDir)?.let { button ->
            button.imageTintList = ColorStateList.valueOf(onBg)
            button.background = ripple(primary, Color.TRANSPARENT)
        }
        findViewById<View>(R.id.LLDriveSelect)?.background = roundedRipple(context, primary, surface, 14f)

        findViewById<FloatingActionButton>(R.id.fabPaste)?.let { fab ->
            fab.backgroundTintList = ColorStateList.valueOf(primary)
            fab.imageTintList = ColorStateList.valueOf(onPrimary)
        }
    }
}

class ThemedFileRowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()
        applyTheme()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyTheme()
    }

    private fun applyTheme() {
        val surface = WinlatorLegacyTheme.surface(context)
        val onSurface = WinlatorLegacyTheme.onSurface(context)
        val muted = WinlatorLegacyTheme.onSurfaceVariant(context)
        val primary = WinlatorLegacyTheme.primary(context)
        val outline = WinlatorLegacyTheme.outlineVariant(context)

        background = ripple(primary, Color.TRANSPARENT)
        findViewById<View>(R.id.FileRowIconContainer)?.background = roundedDrawable(context, surface, 12f)
        findViewById<View>(R.id.FileRowDivider)?.setBackgroundColor(outline)
        findViewById<TextView>(R.id.TVFileName)?.setTextColor(onSurface)
        findViewById<TextView>(R.id.TVFileDetails)?.setTextColor(muted)
        findViewById<ImageView>(R.id.IVIcon)?.imageTintList = ColorStateList.valueOf(muted)
        findViewById<ImageView>(R.id.BTFileMenu)?.imageTintList = ColorStateList.valueOf(muted)
    }
}
