package com.winlator.cmod.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.ui.FpsLimiterControl;

public class IngameSidebarThemeLayout extends FrameLayout {
    private int background;
    private int surfaceVariant;
    private int onSurface;
    private int onSurfaceVariant;
    private int primary;
    private int primaryContainer;

    public IngameSidebarThemeLayout(Context context) {
        super(context);
        applyChosenTheme(context);
    }

    public IngameSidebarThemeLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        applyChosenTheme(context);
    }

    public IngameSidebarThemeLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyChosenTheme(context);
    }

    private static void applyChosenTheme(Context context) {
        String theme = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString("winlator_ui_theme", "black");
        int overlay;
        if ("white".equals(theme)) overlay = R.style.IngameSidebarTheme_White;
        else if ("amoled".equals(theme)) overlay = R.style.IngameSidebarTheme_Amoled;
        else if ("blue".equals(theme)) overlay = R.style.IngameSidebarTheme_Blue;
        else if ("red".equals(theme)) overlay = R.style.IngameSidebarTheme_Red;
        else if ("purple".equals(theme)) overlay = R.style.IngameSidebarTheme_Purple;
        else overlay = R.style.IngameSidebarTheme_Black;
        context.getTheme().applyStyle(overlay, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        readPalette();
        setBackgroundColor(background);

        if (getChildCount() > 1 && getChildAt(1) instanceof ViewGroup) {
            ViewGroup legacyRoot = (ViewGroup) getChildAt(1);
            if (legacyRoot.getChildCount() >= 3) {
                legacyRoot.getChildAt(0).setVisibility(View.GONE);
                legacyRoot.getChildAt(1).setVisibility(View.GONE);
                legacyRoot.setPadding(dp(64), legacyRoot.getPaddingTop(),
                        legacyRoot.getPaddingRight(), legacyRoot.getPaddingBottom());
            }
        }

        replaceLegacyFpsLimiter();
        applyCompactPremiumLayout();
        normalizeLegacyTree(this);
        forceKnownLegacyIconTints();
        fitMetricText();

        post(() -> {
            normalizeLegacyTree(this);
            forceKnownLegacyIconTints();
        });
        postDelayed(() -> {
            normalizeLegacyTree(this);
            forceKnownLegacyIconTints();
            wrapLegacySpinnerAdapters(this);
        }, 500);
    }

    private void replaceLegacyFpsLimiter() {
        View oldSpinner = findViewById(R.id.SPNativeFPS);
        if (oldSpinner == null) return;
        if (!(oldSpinner.getParent() instanceof ViewGroup)) return;

        ViewGroup oldRow = (ViewGroup) oldSpinner.getParent();
        if (!(oldRow.getParent() instanceof ViewGroup)) return;
        ViewGroup holder = (ViewGroup) oldRow.getParent();
        int index = holder.indexOfChild(oldRow);

        int topMargin = 0;
        ViewGroup.LayoutParams oldParams = oldRow.getLayoutParams();
        if (oldParams instanceof ViewGroup.MarginLayoutParams) {
            topMargin = ((ViewGroup.MarginLayoutParams) oldParams).topMargin;
        }

        holder.removeView(oldRow);
        FpsLimiterControl control = new FpsLimiterControl(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        holder.addView(control, Math.max(0, index), params);
    }

    private void applyCompactPremiumLayout() {
        FpsLimiterControl fps = findFirstFpsLimiter(this);
        insertSectionLabelBefore(fps, "PERFORMANCE");

        View imageQuality = findViewById(R.id.LLStandardOptions);
        insertSectionLabelBefore(imageQuality, "IMAGE QUALITY");
        flattenSection(imageQuality);

        View frameGen = findViewById(R.id.LLFrameGenOptions);
        flattenSection(frameGen);

        View savePreset = findViewById(R.id.BTSaveGraphicsPreset);
        insertSectionLabelBefore(savePreset, "PRESETS");
        compactActionRow(savePreset);

        View hudStyle = findViewById(R.id.LLHudStyleRow);
        if (hudStyle != null && hudStyle.getParent() instanceof LinearLayout) {
            LinearLayout hudParent = (LinearLayout) hudStyle.getParent();
            int styleIndex = hudParent.indexOfChild(hudStyle);
            View enableHud = previousContentChild(hudParent, styleIndex);
            insertSectionLabelBefore(enableHud, "GENERAL");
            insertSectionLabelBefore(hudStyle, "APPEARANCE");

            flattenSection(enableHud);
            flattenSection(hudStyle);

            TextView resetText = findTextView(this, "Reset HUD");
            View resetRow = directChildUnder(hudParent, resetText);
            insertSectionLabelBefore(resetRow, "ACTIONS");
            compactActionRow(resetRow);
        }
    }

    private void flattenSection(View view) {
        if (view == null) return;
        view.setBackground(null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            group.setPadding(0, group.getPaddingTop() > 0 ? dp(2) : 0,
                    0, group.getPaddingBottom() > 0 ? dp(2) : 0);
        }
    }

    private View previousContentChild(LinearLayout parent, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != GONE) return child;
        }
        return null;
    }

    private void insertSectionLabelBefore(View target, String label) {
        if (target == null || !(target.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) target.getParent();
        int index = parent.indexOfChild(target);
        if (index < 0) return;

        if (index > 0) {
            Object tag = parent.getChildAt(index - 1).getTag();
            if (("winz-section-" + label).equals(tag)) return;
        }

        TextView section = new TextView(getContext());
        section.setTag("winz-section-" + label);
        section.setText(label);
        section.setTextColor(onSurfaceVariant);
        section.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        section.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        section.setLetterSpacing(0.08f);
        section.setAllCaps(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        params.bottomMargin = dp(8);
        parent.addView(section, index, params);

        ViewGroup.LayoutParams targetParams = target.getLayoutParams();
        if (targetParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) targetParams).topMargin = 0;
            target.setLayoutParams(targetParams);
        }
    }

    private void compactActionRow(View row) {
        if (row instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) row;
            layout.setGravity(Gravity.CENTER_VERTICAL);
            layout.setPadding(dp(16), layout.getPaddingTop(), dp(16), layout.getPaddingBottom());
        }
    }

    private FpsLimiterControl findFirstFpsLimiter(View view) {
        if (view instanceof FpsLimiterControl) return (FpsLimiterControl) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                FpsLimiterControl found = findFirstFpsLimiter(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findTextView(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextView(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View directChildUnder(ViewGroup ancestor, View descendant) {
        if (ancestor == null || descendant == null) return null;
        View current = descendant;
        while (current != null && current.getParent() instanceof View) {
            if (current.getParent() == ancestor) return current;
            current = (View) current.getParent();
        }
        return null;
    }

    private void readPalette() {
        background = resolveColor(R.attr.ingameSidebarBackground, Color.BLACK);
        surfaceVariant = resolveColor(R.attr.ingameSidebarSurfaceVariant, Color.rgb(28, 29, 35));
        onSurface = resolveColor(R.attr.ingameSidebarOnSurface, Color.WHITE);
        onSurfaceVariant = resolveColor(R.attr.ingameSidebarOnSurfaceVariant, Color.LTGRAY);
        primary = resolveColor(R.attr.ingameSidebarPrimary, Color.WHITE);
        primaryContainer = resolveColor(R.attr.ingameSidebarPrimaryContainer, surfaceVariant);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return fallback;
    }

    private void normalizeLegacyTree(View view) {
        Drawable drawable = view.getBackground();
        if (drawable instanceof ColorDrawable) {
            int color = ((ColorDrawable) drawable).getColor();
            if (color == Color.BLACK || color == Color.rgb(3, 8, 13)) {
                view.setBackgroundColor(background);
            } else if (color == Color.rgb(14, 34, 49) || color == Color.rgb(15, 45, 66)) {
                view.setBackgroundColor(surfaceVariant);
            }
        }

        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int current = text.getCurrentTextColor();
            if (current == Color.WHITE || current == Color.rgb(238, 247, 255)) {
                text.setTextColor(onSurface);
            } else if (isLegacyBlue(current)) {
                text.setTextColor(primary);
            } else if (current == Color.rgb(221, 246, 255)) {
                text.setTextColor(onSurfaceVariant);
            }
        }

        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            ColorStateList tint = ImageViewCompat.getImageTintList(image);
            if (tint != null) {
                int current = tint.getDefaultColor();
                if (isLegacyBlue(current)) {
                    ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(primary));
                } else if (current == Color.rgb(221, 246, 255)
                        || current == Color.rgb(238, 247, 255)) {
                    ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(onSurface));
                }
            }
        }

        if (view instanceof Switch) {
            Switch toggle = (Switch) view;
            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_checked },
                    new int[] { }
            };
            toggle.setThumbTintList(new ColorStateList(states,
                    new int[] { primary, onSurfaceVariant }));
            toggle.setTrackTintList(new ColorStateList(states,
                    new int[] { primaryContainer, surfaceVariant }));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                normalizeLegacyTree(group.getChildAt(i));
            }
        }
    }

    private void forceKnownLegacyIconTints() {
        ImageView inputSettings = findViewById(R.id.BTInputControlsSettings);
        if (inputSettings != null) {
            ImageViewCompat.setImageTintList(inputSettings, ColorStateList.valueOf(primary));
        }
    }

    private void wrapLegacySpinnerAdapters(View view) {
        if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            SpinnerAdapter adapter = spinner.getAdapter();
            if (adapter != null && !(adapter instanceof ThemeSpinnerAdapter)) {
                int selected = spinner.getSelectedItemPosition();
                spinner.setAdapter(new ThemeSpinnerAdapter(adapter));
                if (selected >= 0 && selected < spinner.getCount()) spinner.setSelection(selected, false);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) wrapLegacySpinnerAdapters(group.getChildAt(i));
        }
    }

    private View themeSpinnerView(View view, boolean dropdown) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(onSurface);
            text.setSingleLine(true);
            text.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            if (dropdown) {
                text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                text.setBackgroundColor(surfaceVariant);
            }
        } else {
            normalizeLegacyTree(view);
            if (dropdown && view.getBackground() instanceof ColorDrawable) {
                view.setBackgroundColor(surfaceVariant);
            }
        }
        return view;
    }

    private final class ThemeSpinnerAdapter implements SpinnerAdapter {
        private final SpinnerAdapter delegate;

        private ThemeSpinnerAdapter(SpinnerAdapter delegate) {
            this.delegate = delegate;
        }

        @Override public int getCount() { return delegate.getCount(); }
        @Override public Object getItem(int position) { return delegate.getItem(position); }
        @Override public long getItemId(int position) { return delegate.getItemId(position); }
        @Override public boolean hasStableIds() { return delegate.hasStableIds(); }
        @Override public int getItemViewType(int position) { return delegate.getItemViewType(position); }
        @Override public int getViewTypeCount() { return delegate.getViewTypeCount(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public void registerDataSetObserver(DataSetObserver observer) { delegate.registerDataSetObserver(observer); }
        @Override public void unregisterDataSetObserver(DataSetObserver observer) { delegate.unregisterDataSetObserver(observer); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return themeSpinnerView(delegate.getView(position, convertView, parent), false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return themeSpinnerView(delegate.getDropDownView(position, convertView, parent), true);
        }
    }

    private boolean isLegacyBlue(int color) {
        return color == Color.rgb(0, 85, 255)
                || color == Color.rgb(0, 102, 255)
                || color == Color.rgb(2, 136, 209)
                || color == Color.rgb(64, 196, 255)
                || color == Color.rgb(143, 216, 255)
                || color == Color.rgb(130, 184, 255);
    }

    private void fitMetricText() {
        TextView cpu = findViewById(R.id.TVCPUInfoCompact);
        if (cpu != null) {
            cpu.setSingleLine(true);
            cpu.setTextColor(primary);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    cpu, 14, 24, 1, TypedValue.COMPLEX_UNIT_SP);
        }

        TextView memory = findViewById(R.id.TVMemoryInfo);
        if (memory != null) {
            memory.setSingleLine(true);
            memory.setEllipsize(null);
            memory.setTextColor(primary);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    memory, 10, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
