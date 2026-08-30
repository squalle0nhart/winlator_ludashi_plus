package com.winlator.cmod.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

public class SidebarNavItemView extends LinearLayout {
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF indicatorRect = new RectF();

    private boolean inflated;
    private boolean applyingBackground;
    private boolean neutralDarkTheme;
    private int navAccent = Color.WHITE;
    private int inactiveTint = Color.GRAY;

    public SidebarNavItemView(Context context) {
        super(context);
        init();
    }

    public SidebarNavItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SidebarNavItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
        refreshPalette();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inflated = true;
        refreshPalette();
        applyStateBackground();
        applyVisualState(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshPalette();
        applyIconTint();
    }

    private void refreshPalette() {
        int primary = resolveColor(R.attr.ingameSidebarPrimary, Color.WHITE);
        inactiveTint = resolveColor(R.attr.ingameSidebarOnSurfaceVariant, Color.GRAY);

        String theme = PreferenceManager.getDefaultSharedPreferences(
                getContext().getApplicationContext()).getString("winlator_ui_theme", "black");
        neutralDarkTheme = "black".equals(theme) || "amoled".equals(theme);

        if ("black".equals(theme)) {
            navAccent = Color.rgb(184, 187, 193);
        } else if ("amoled".equals(theme)) {
            navAccent = Color.rgb(208, 208, 208);
        } else {
            navAccent = primary;
        }
        indicatorPaint.setColor(navAccent);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return fallback;
    }

    private void applyStateBackground() {
        applyingBackground = true;
        super.setBackgroundResource(R.drawable.sidebar_nav_icon_bg);
        applyingBackground = false;
    }

    private void applyIconTint() {
        int tint = isSelected() ? navAccent : inactiveTint;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof ImageView) {
                ImageViewCompat.setImageTintList((ImageView) getChildAt(i),
                        ColorStateList.valueOf(tint));
            }
        }
    }

    private void applyVisualState(boolean animate) {
        float alpha = isSelected() ? (neutralDarkTheme ? 0.96f : 1.0f) : 0.66f;
        float scale = isSelected() ? 1.0f : 0.96f;
        float elevation = isSelected() ? dp(2) : 0f;

        applyIconTint();

        if (animate && isLaidOut()) {
            animate().cancel();
            animate()
                    .alpha(alpha)
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(140)
                    .start();
        } else {
            setAlpha(alpha);
            setScaleX(scale);
            setScaleY(scale);
        }
        setElevation(elevation);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!isSelected()) return;

        float indicatorWidth = dp(neutralDarkTheme ? 2 : 3);
        float indicatorHeight = dp(neutralDarkTheme ? 16 : 18);
        float right = getWidth() - dp(1);
        float left = right - indicatorWidth;
        float top = (getHeight() - indicatorHeight) * 0.5f;
        indicatorRect.set(left, top, right, top + indicatorHeight);
        canvas.drawRoundRect(indicatorRect, dp(2), dp(2), indicatorPaint);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public void setSelected(boolean selected) {
        boolean changed = selected != isSelected();
        super.setSelected(selected);
        if (inflated && changed) applyVisualState(true);
    }

    @Override
    public void setBackground(@Nullable Drawable background) {
        if (!inflated || applyingBackground) {
            super.setBackground(background);
            return;
        }

        setSelected(true);
        applyStateBackground();
    }

    @Override
    public void setBackgroundResource(int resId) {
        if (!inflated || applyingBackground) {
            super.setBackgroundResource(resId);
            return;
        }

        setSelected(false);
        applyStateBackground();
    }
}
