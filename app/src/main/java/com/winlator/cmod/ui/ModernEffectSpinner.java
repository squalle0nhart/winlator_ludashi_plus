package com.winlator.cmod.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;

import com.winlator.cmod.R;

public class ModernEffectSpinner extends AppCompatSpinner {
    private int surface;
    private int onSurface;
    private int accent;
    private int outline;

    public ModernEffectSpinner(Context context) {
        super(context);
        init();
    }

    public ModernEffectSpinner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ModernEffectSpinner(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        surface = resolveColor(R.attr.ingameSidebarSurface, Color.rgb(18, 18, 22));
        onSurface = resolveColor(R.attr.ingameSidebarOnSurface, Color.WHITE);
        accent = resolveColor(R.attr.ingameSidebarPrimary, Color.WHITE);
        outline = resolveColor(R.attr.ingameSidebarOutline, Color.GRAY);
        setPopupBackgroundDrawable(null);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    }

    @Override
    public boolean performClick() {
        if (getAdapter() == null || getAdapter().getCount() == 0) return super.performClick();

        final int count = getAdapter().getCount();
        final String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            Object item = getAdapter().getItem(i);
            labels[i] = item != null ? item.toString() : "";
        }

        ListView list = new ListView(getContext());
        list.setDivider(null);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setSelector(android.R.color.transparent);
        list.setAdapter(new EffectAdapter(labels));
        list.setItemChecked(getSelectedItemPosition(), true);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("ReShade effect")
                .setView(list)
                .create();

        list.setOnItemClickListener((parent, view, position, id) -> {
            setSelection(position);
            dialog.dismiss();
        });

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(surface);
                bg.setCornerRadius(dp(22));
                bg.setStroke(dp(1), outline);
                dialog.getWindow().setBackgroundDrawable(bg);
            }
            int titleId = getResources().getIdentifier("alertTitle", "id", "android");
            TextView title = dialog.findViewById(titleId);
            if (title != null) {
                title.setTextColor(onSurface);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            }
        });
        dialog.show();
        return true;
    }

    private final class EffectAdapter extends BaseAdapter {
        private final String[] labels;

        EffectAdapter(String[] labels) {
            this.labels = labels;
        }

        @Override public int getCount() { return labels.length; }
        @Override public Object getItem(int position) { return labels[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView ? (TextView) convertView : new TextView(getContext());
            boolean selected = position == getSelectedItemPosition();
            row.setText(labels[position]);
            row.setTextColor(selected ? accent : onSurface);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setMinHeight(dp(52));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(selected ? withAlpha(accent, 0.10f) : Color.TRANSPARENT);
            bg.setCornerRadius(dp(14));
            if (selected) bg.setStroke(dp(1), withAlpha(accent, 0.65f));
            row.setBackground(bg);
            return row;
        }
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return fallback;
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255f * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
