package com.winlator.cmod.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

import java.util.List;

public final class ThemeUtils {
    public static final String PREF_DARK_MODE = "dark_mode";
    public static final String PREF_COLOR_THEME = "color_theme";
    public static final String COLOR_THEME_BLUE = "blue";
    public static final String COLOR_THEME_EMERALD = "emerald";
    public static final String COLOR_THEME_SUNSET = "sunset";
    public static final String COLOR_THEME_ROSE = "rose";
    public static final String DEFAULT_COLOR_THEME = COLOR_THEME_BLUE;

    private ThemeUtils() {}

    public static boolean isDarkMode(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_DARK_MODE, true);
    }

    @NonNull
    public static String getColorTheme(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_COLOR_THEME, DEFAULT_COLOR_THEME);
    }

    public static int getThemeResId(boolean darkMode, @NonNull String colorTheme) {
        return switch (colorTheme) {
            case COLOR_THEME_EMERALD -> darkMode ? R.style.AppTheme_Emerald_Dark : R.style.AppTheme_Emerald;
            case COLOR_THEME_SUNSET -> darkMode ? R.style.AppTheme_Sunset_Dark : R.style.AppTheme_Sunset;
            case COLOR_THEME_ROSE -> darkMode ? R.style.AppTheme_Rose_Dark : R.style.AppTheme_Rose;
            case COLOR_THEME_BLUE -> darkMode ? R.style.AppTheme_Blue_Dark : R.style.AppTheme_Blue;
            default -> darkMode ? R.style.AppTheme_Dark : R.style.AppTheme;
        };
    }

    public static int getFullscreenThemeResId(boolean darkMode, @NonNull String colorTheme) {
        return switch (colorTheme) {
            case COLOR_THEME_EMERALD -> darkMode ? R.style.AppThemeFullscreen_Emerald_Dark : R.style.AppThemeFullscreen_Emerald;
            case COLOR_THEME_SUNSET -> darkMode ? R.style.AppThemeFullscreen_Sunset_Dark : R.style.AppThemeFullscreen_Sunset;
            case COLOR_THEME_ROSE -> darkMode ? R.style.AppThemeFullscreen_Rose_Dark : R.style.AppThemeFullscreen_Rose;
            case COLOR_THEME_BLUE -> darkMode ? R.style.AppThemeFullscreen_Blue_Dark : R.style.AppThemeFullscreen_Blue;
            default -> darkMode ? R.style.AppThemeFullscreen_Dark : R.style.AppThemeFullscreen;
        };
    }

    public static int getThemeResId(@NonNull Context context) {
        return getThemeResId(isDarkMode(context), getColorTheme(context));
    }

    public static int getFullscreenThemeResId(@NonNull Context context) {
        return getFullscreenThemeResId(isDarkMode(context), getColorTheme(context));
    }

    @ColorInt
    public static int getColorAttr(@NonNull Context context, @AttrRes int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (!context.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return 0;
        }
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(context, typedValue.resourceId);
        }
        return typedValue.data;
    }

    @NonNull
    public static ColorStateList getColorStateListAttr(@NonNull Context context, @AttrRes int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (!context.getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return ColorStateList.valueOf(0);
        }
        if (typedValue.resourceId != 0) {
            ColorStateList colorStateList = ContextCompat.getColorStateList(context, typedValue.resourceId);
            if (colorStateList != null) return colorStateList;
        }
        return ColorStateList.valueOf(typedValue.data);
    }

    public static int getDialogBackgroundRes() {
        return R.drawable.dialog_background_dark_blue;
    }

    public static int getPopupBackgroundRes() {
        return R.drawable.dialog_background_dark_blue;
    }

    public static int getEditTextBackgroundRes() {
        return R.drawable.edit_text;
    }

    public static void applyEditTextTheme(@NonNull EditText editText) {
        editText.setTextColor(getColorAttr(editText.getContext(), R.attr.colorOnSurface));
        editText.setHintTextColor(getColorAttr(editText.getContext(), R.attr.colorOnSurfaceVariant));
        editText.setBackgroundResource(getEditTextBackgroundRes());
    }

    public static void applyFieldSetLabelStyle(@NonNull TextView textView) {
        textView.setTextColor(getColorAttr(textView.getContext(), R.attr.themeAccentColor));
        textView.setBackgroundColor(getColorAttr(textView.getContext(), R.attr.colorWindowBackground));
    }

    public static void applyWindowChrome(@NonNull Activity activity) {
        activity.getWindow().setStatusBarColor(getColorAttr(activity, R.attr.colorToolbarSurface));
        activity.getWindow().setNavigationBarColor(getColorAttr(activity, R.attr.colorToolbarSurface));
        View decorView = activity.getWindow().getDecorView();
        int flags = decorView.getSystemUiVisibility();
        if (isDarkMode(activity)) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decorView.setSystemUiVisibility(flags);
    }

    @NonNull
    public static Drawable getTintedDrawable(@NonNull Context context, int drawableResId, @AttrRes int colorAttrResId) {
        Drawable drawable = AppCompatResources.getDrawable(context, drawableResId);
        if (drawable == null) {
            throw new IllegalArgumentException("Drawable not found: " + drawableResId);
        }
        Drawable wrapped = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(wrapped, getColorAttr(context, colorAttrResId));
        return wrapped;
    }

    public static void applySpinnerTheme(@NonNull Spinner spinner) {
        spinner.setPopupBackgroundResource(getPopupBackgroundRes());
    }

    @NonNull
    public static <T> ArrayAdapter<T> createSpinnerAdapter(@NonNull Context context, @NonNull T[] items) {
        ArrayAdapter<T> adapter = new ArrayAdapter<>(context, R.layout.spinner_item_amoled, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_amoled);
        return adapter;
    }

    @NonNull
    public static <T> ArrayAdapter<T> createSpinnerAdapter(@NonNull Context context, @NonNull List<T> items) {
        ArrayAdapter<T> adapter = new ArrayAdapter<>(context, R.layout.spinner_item_amoled, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_amoled);
        return adapter;
    }
}
