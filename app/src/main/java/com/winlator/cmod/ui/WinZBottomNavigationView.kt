package com.winlator.cmod.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.winlator.cmod.ContainersFragment
import com.winlator.cmod.FileManagerFragment
import com.winlator.cmod.InputControlsFragment
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.SettingsFragment
import com.winlator.cmod.ShortcutsFragment

class WinZBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.bottomNavigationStyle
) : BottomNavigationView(context, attrs, defStyleAttr) {

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }
        setPadding(0, 0, 0, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setPadding(0, 0, 0, 0)
        ViewCompat.requestApplyInsets(this)
        post { restoreForCurrentOrientation() }
    }

    override fun onWindowVisibilityChanged(windowVisibility: Int) {
        super.onWindowVisibilityChanged(windowVisibility)
        if (windowVisibility == View.VISIBLE) {
            post { restoreForCurrentOrientation() }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            post { restoreForCurrentOrientation() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        post {
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                restoreForCurrentOrientation()
            }
        }
    }

    private fun restoreForCurrentOrientation() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return
        val activity = context.findMainActivity() ?: return
        val current = activity.supportFragmentManager.findFragmentById(R.id.FLFragmentContainer)
        val topLevel = current is ShortcutsFragment ||
            current is ContainersFragment ||
            current is InputControlsFragment ||
            current is SettingsFragment ||
            current is FileManagerFragment
        if (topLevel && visibility != View.VISIBLE) visibility = View.VISIBLE
    }
}

private tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}
