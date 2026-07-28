package com.winlator.cmod.contentdialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.ThemeUtils;
import com.winlator.cmod.perf.PerfRevertRegistry;
import com.winlator.cmod.perf.PerfRootApplier;
import com.winlator.cmod.perf.PerformanceSettings;
import com.winlator.cmod.perf.RootManager;
import com.winlator.cmod.perf.TempWatchdog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-View adaptation of Bannerlator 2.9's Compose performance screen. The same dialog is used
 * for global defaults, shortcut overrides, and the live in-game entry point.
 */
public final class PerformanceControlDialog {
    public interface LiveController {
        void apply(String key, boolean value);
    }

    private static final String[] KEYS = {
            PerformanceSettings.KEY_SUSTAINED,
            PerformanceSettings.KEY_PRIORITY,
            PerformanceSettings.KEY_BIG_CORES,
            PerfRootApplier.KEY_CPU_GOVERNOR,
            PerfRootApplier.KEY_CPU_FREQ_LOCK,
            PerfRootApplier.KEY_CORES_ONLINE,
            PerfRootApplier.KEY_GPU_CLOCK_LOCK,
            PerfRootApplier.KEY_THERMAL_DISABLE,
            PerfRootApplier.KEY_FAN_MAX
    };
    private static final int[] LABELS = {
            R.string.performance_sustained,
            R.string.performance_priority,
            R.string.performance_big_cores,
            R.string.performance_cpu_governor,
            R.string.performance_cpu_frequency,
            R.string.performance_all_cores,
            R.string.performance_gpu_clock,
            R.string.performance_disable_thermal,
            R.string.performance_fan_max
    };
    private static final int[] INFO = {
            R.string.performance_info_sustained,
            R.string.performance_info_priority,
            R.string.performance_info_big_cores,
            R.string.performance_info_cpu_governor,
            R.string.performance_info_cpu_frequency,
            R.string.performance_info_all_cores,
            R.string.performance_info_gpu_clock,
            R.string.performance_info_disable_thermal,
            R.string.performance_info_fan
    };

    private final Context context;
    private final Shortcut shortcut;
    private final LiveController liveController;
    private final boolean gameMode;
    private final Dialog dialog;
    private final LinearLayout content;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ToggleBinding> toggles = new LinkedHashMap<>();
    private TextView temperatureView;
    private final Runnable temperatureRefresh = new Runnable() {
        @Override
        public void run() {
            if (!dialog.isShowing()) return;
            if (temperatureView != null) {
                Integer value = com.winlator.cmod.perf.PerfNodeResolver.INSTANCE.readHottestWatchedC();
                temperatureView.setText(value != null
                        ? context.getString(R.string.performance_temperature_current, value)
                        : context.getString(R.string.performance_temperature_unavailable));
            }
            handler.postDelayed(this, 1500);
        }
    };

    private PerformanceControlDialog(
            Context context,
            Shortcut shortcut,
            LiveController liveController,
            boolean gameMode) {
        this.context = context;
        this.shortcut = shortcut;
        this.liveController = liveController;
        this.gameMode = gameMode;
        dialog = createThemedDialog();

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundResource(ThemeUtils.getDialogBackgroundRes());
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(20));
        content.setBackgroundColor(Color.TRANSPARENT);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scrollView);
        build();
        dialog.setOnDismissListener(ignored -> handler.removeCallbacks(temperatureRefresh));
    }

    public static void showGlobal(Context context) {
        new PerformanceControlDialog(context, null, null, false).show();
    }

    public static void showForShortcut(Context context, Shortcut shortcut) {
        new PerformanceControlDialog(context, shortcut, null, true).show();
    }

    public static void showInGame(
            Activity activity,
            Shortcut shortcut,
            LiveController liveController) {
        new PerformanceControlDialog(activity, shortcut, liveController, shortcut != null).show();
    }

    private void show() {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(24), dp(620)),
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        handler.post(temperatureRefresh);
    }

    private void build() {
        TextView title = text(R.string.power_user_performance, 23, true);
        content.addView(title, matchWrap());

        TextView summary = text(
                gameMode ? R.string.performance_game_summary : R.string.performance_global_summary,
                12,
                false);
        summary.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.topMargin = dp(6);
        summaryParams.bottomMargin = dp(20);
        content.addView(summary, summaryParams);

        LinearLayout noRoot = section(R.string.performance_no_root);
        for (int index = 0; index < 3; index++) {
            addToggle(noRoot, KEYS[index], LABELS[index], INFO[index], false);
        }

        LinearLayout root = section(R.string.performance_root_controls);
        addRootStatus(root);
        for (int index = 3; index < KEYS.length; index++) {
            addToggle(root, KEYS[index], LABELS[index], INFO[index], true);
        }

        Button freeMemory = button(R.string.performance_free_memory);
        freeMemory.setEnabled(RootManager.INSTANCE.isGranted());
        freeMemory.setOnClickListener(view -> new Thread(() -> {
            boolean success = PerfRootApplier.INSTANCE.freeMemoryNow();
            handler.post(() -> Toast.makeText(
                    context,
                    success ? R.string.performance_memory_freed : R.string.performance_action_failed,
                    Toast.LENGTH_SHORT).show());
        }, "perf-free-memory").start());
        root.addView(freeMemory, matchWrap());

        addWatchdogSection();

        if (gameMode && shortcut != null) {
            Button resetAll = button(R.string.performance_reset_global);
            resetAll.setOnClickListener(view -> {
                for (String key : KEYS) {
                    shortcut.removeExtra(key);
                    boolean value = PerformanceSettings.INSTANCE.globalDefault(key);
                    if (liveController != null) liveController.apply(key, value);
                }
                shortcut.saveData();
                toggles.forEach((key, binding) -> binding.refresh());
            });
            LinearLayout.LayoutParams resetParams = matchWrap();
            resetParams.topMargin = dp(12);
            content.addView(resetAll, resetParams);
        }

        TextView alwaysRevert = text(R.string.performance_always_revert, 11, false);
        alwaysRevert.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(12);
        content.addView(alwaysRevert, noteParams);

        Button close = button(android.R.string.ok);
        LinearLayout.LayoutParams closeParams = matchWrap();
        closeParams.topMargin = dp(14);
        content.addView(close, closeParams);
        close.setOnClickListener(view -> dialog.dismiss());
    }

    private void addRootStatus(LinearLayout parent) {
        int label;
        switch (RootManager.INSTANCE.getState().getValue()) {
            case UNAVAILABLE:
                label = R.string.performance_root_unavailable;
                break;
            case GRANTED:
                label = R.string.performance_root_granted;
                break;
            case DENIED:
                label = R.string.performance_root_denied;
                break;
            default:
                label = R.string.performance_root_not_granted;
                break;
        }
        TextView status = text(label, 12, false);
        status.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        parent.addView(status, matchWrap());

        if (!RootManager.INSTANCE.isGranted()
                && RootManager.INSTANCE.getState().getValue() != RootManager.RootState.UNAVAILABLE) {
            Button grant = button(R.string.performance_grant_root);
            grant.setOnClickListener(view -> showRiskGate(
                    R.string.performance_root_risk,
                    R.string.performance_grant_root,
                    () -> {
                        dialog.dismiss();
                        new Thread(() -> {
                            RootManager.INSTANCE.ensureGrantedBlocking();
                            handler.post(() -> {
                                if (context instanceof Activity) {
                                    Activity activity = (Activity) context;
                                    if (activity.isFinishing() || activity.isDestroyed()) return;
                                }
                                new PerformanceControlDialog(
                                        context,
                                        shortcut,
                                        liveController,
                                        gameMode).show();
                            });
                        }, "perf-root-grant").start();
                    }));
            parent.addView(grant, matchWrap());
        }
    }

    private void addToggle(
            LinearLayout parent,
            String key,
            int labelRes,
            int infoRes,
            boolean rootOnly) {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = matchWrap();
        blockParams.topMargin = dp(5);
        parent.addView(block, blockParams);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        block.addView(row, matchWrap());

        TextView label = text(labelRes, 14, false);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button info = button("?");
        info.setMinWidth(dp(42));
        info.setMinimumWidth(dp(42));
        info.setOnClickListener(view -> showInfoDialog(labelRes, infoRes));
        row.addView(info, new LinearLayout.LayoutParams(dp(46), dp(44)));

        SwitchCompat control = new SwitchCompat(context);
        boolean rootEnabled = !rootOnly || RootManager.INSTANCE.isGranted();
        boolean harnessEnabled = !PerfRootApplier.INSTANCE.isHarnessGated(key)
                || PerfRevertRegistry.INSTANCE.getHarnessProven().getValue();
        control.setEnabled(rootEnabled && harnessEnabled);
        row.addView(control, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)));

        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView status = text(
                gameMode && shortcut != null && shortcut.hasExtra(key)
                        ? R.string.performance_game_override
                        : R.string.performance_using_global,
                11,
                false);
        status.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        statusRow.addView(status, new LinearLayout.LayoutParams(0, dp(34), 1));

        Button reset = button(R.string.reset);
        reset.setTextSize(11);
        reset.setMinHeight(0);
        reset.setMinimumHeight(0);
        reset.setVisibility(gameMode && shortcut != null && shortcut.hasExtra(key)
                ? View.VISIBLE : View.GONE);
        statusRow.addView(reset, new LinearLayout.LayoutParams(dp(88), dp(34)));
        if (gameMode && shortcut != null) block.addView(statusRow, matchWrap());

        ToggleBinding binding = new ToggleBinding(key, control, status, reset, rootOnly);
        toggles.put(key, binding);
        binding.refresh();
        control.setOnCheckedChangeListener((buttonView, checked) -> binding.persistAndApply(checked));
        reset.setOnClickListener(view -> binding.resetToGlobal());
    }

    private void addWatchdogSection() {
        LinearLayout watchdog = section(R.string.performance_thermal_watchdog);
        TextView info = text(R.string.performance_info_watchdog, 12, false);
        info.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        watchdog.addView(info, matchWrap());

        SwitchCompat enabled = new SwitchCompat(context);
        enabled.setText(context.getString(
                R.string.performance_watchdog_enabled,
                TempWatchdog.INSTANCE.getCeilingC().getValue()));
        enabled.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurface));
        enabled.setChecked(TempWatchdog.INSTANCE.getEnabled().getValue());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                TempWatchdog.INSTANCE.setWatchdogEnabled(true);
            } else {
                button.setChecked(true);
                showRiskGate(
                        R.string.performance_watchdog_risk,
                        R.string.performance_turn_watchdog_off,
                        () -> {
                            TempWatchdog.INSTANCE.setWatchdogEnabled(false);
                            enabled.setOnCheckedChangeListener(null);
                            enabled.setChecked(false);
                            enabled.setOnCheckedChangeListener(this::onWatchdogChanged);
                        });
            }
        });
        watchdog.addView(enabled, matchWrap());

        temperatureView = text("", 12, true);
        temperatureView.setTextColor(ThemeUtils.getColorAttr(context, R.attr.themeAccentColor));
        watchdog.addView(temperatureView, matchWrap());

        TextView limits;
        if (TempWatchdog.INSTANCE.getHasDeviceTrips()) {
            limits = text(
                    context.getString(
                            R.string.performance_watchdog_device_limits,
                            TempWatchdog.INSTANCE.getFirstTripC(),
                            TempWatchdog.INSTANCE.getTopTripC()),
                    11,
                    false);
        } else {
            limits = text(
                    context.getString(
                            R.string.performance_watchdog_fallback,
                            TempWatchdog.FALLBACK_CEILING_C),
                    11,
                    false);
        }
        limits.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        watchdog.addView(limits, matchWrap());

        TextView threshold = text(R.string.performance_threshold, 13, true);
        LinearLayout.LayoutParams thresholdParams = matchWrap();
        thresholdParams.topMargin = dp(8);
        watchdog.addView(threshold, thresholdParams);

        RadioGroup modes = new RadioGroup(context);
        TempWatchdog.ThresholdMode[] values = TempWatchdog.ThresholdMode.values();
        int[] labels = {
                R.string.performance_conservative,
                R.string.performance_balanced,
                R.string.performance_aggressive,
                R.string.performance_manual
        };
        for (int index = 0; index < values.length; index++) {
            RadioButton radio = new RadioButton(context);
            radio.setId(View.generateViewId());
            radio.setTag(values[index]);
            radio.setText(context.getString(
                    R.string.performance_threshold_mode,
                    context.getString(labels[index]),
                    TempWatchdog.INSTANCE.resolvedCeilingFor(values[index])));
            radio.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurface));
            radio.setChecked(TempWatchdog.INSTANCE.getMode().getValue() == values[index]);
            modes.addView(radio, matchWrap());
        }
        watchdog.addView(modes, matchWrap());

        TextView manualLabel = text(
                context.getString(
                        R.string.performance_manual_value,
                        TempWatchdog.INSTANCE.getManualCeilingC().getValue()),
                12,
                false);
        SeekBar manual = new SeekBar(context);
        int max = Math.min(
                TempWatchdog.MANUAL_MAX_C,
                TempWatchdog.INSTANCE.getTopTripC() != null
                        ? TempWatchdog.INSTANCE.getTopTripC()
                        : TempWatchdog.MANUAL_MAX_C);
        manual.setMax(Math.max(1, max - TempWatchdog.MANUAL_MIN_C));
        manual.setProgress(TempWatchdog.INSTANCE.getManualCeilingC().getValue()
                - TempWatchdog.MANUAL_MIN_C);
        boolean manualVisible =
                TempWatchdog.INSTANCE.getMode().getValue() == TempWatchdog.ThresholdMode.MANUAL;
        manualLabel.setVisibility(manualVisible ? View.VISIBLE : View.GONE);
        manual.setVisibility(manualVisible ? View.VISIBLE : View.GONE);
        watchdog.addView(manualLabel, matchWrap());
        watchdog.addView(manual, matchWrap());

        modes.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton radio = group.findViewById(checkedId);
            if (radio == null) return;
            TempWatchdog.ThresholdMode mode = (TempWatchdog.ThresholdMode) radio.getTag();
            TempWatchdog.INSTANCE.setMode(mode);
            boolean visible = mode == TempWatchdog.ThresholdMode.MANUAL;
            manualLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
            manual.setVisibility(visible ? View.VISIBLE : View.GONE);
            enabled.setText(context.getString(
                    R.string.performance_watchdog_enabled,
                    TempWatchdog.INSTANCE.getCeilingC().getValue()));
        });
        manual.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = TempWatchdog.MANUAL_MIN_C + progress;
                manualLabel.setText(context.getString(R.string.performance_manual_value, value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int value = TempWatchdog.MANUAL_MIN_C + seekBar.getProgress();
                TempWatchdog.INSTANCE.setManualCeilingC(value);
                enabled.setText(context.getString(
                        R.string.performance_watchdog_enabled,
                        TempWatchdog.INSTANCE.getCeilingC().getValue()));
            }
        });
    }

    private void onWatchdogChanged(CompoundButton button, boolean checked) {
        if (checked) TempWatchdog.INSTANCE.setWatchdogEnabled(true);
        else {
            button.setChecked(true);
            showRiskGate(
                    R.string.performance_watchdog_risk,
                    R.string.performance_turn_watchdog_off,
                    () -> {
                        TempWatchdog.INSTANCE.setWatchdogEnabled(false);
                        button.setOnCheckedChangeListener(null);
                        button.setChecked(false);
                        button.setOnCheckedChangeListener(this::onWatchdogChanged);
                    });
        }
    }

    private void showRiskGate(int messageRes, int confirmRes, Runnable onConfirmed) {
        Dialog risk = createThemedDialog();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(12));
        root.setBackgroundResource(ThemeUtils.getDialogBackgroundRes());

        TextView title = text(R.string.power_user_performance, 18, true);
        title.setTextColor(Color.rgb(255, 82, 82));
        root.addView(title, matchWrap());

        ScrollView scroll = new ScrollView(context);
        TextView message = text(messageRes, 13, false);
        message.setPadding(0, dp(10), dp(8), dp(10));
        scroll.addView(message, matchWrap());
        LinearLayout.LayoutParams scrollParams = matchWrap();
        scrollParams.height = dp(230);
        root.addView(scroll, scrollParams);

        TextView scrollHint = text(R.string.performance_scroll_to_continue, 11, false);
        scrollHint.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        root.addView(scrollHint, matchWrap());

        CheckBox accepted = new CheckBox(context);
        accepted.setText(R.string.performance_accept_risk);
        accepted.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurface));
        accepted.setEnabled(false);
        root.addView(accepted, matchWrap());

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END);
        Button cancel = button(android.R.string.cancel);
        Button confirm = button(confirmRes);
        confirm.setEnabled(false);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(confirm, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions, matchWrap());

        risk.setContentView(root);
        cancel.setOnClickListener(view -> risk.dismiss());
        confirm.setOnClickListener(view -> {
            risk.dismiss();
            onConfirmed.run();
        });
        accepted.setOnCheckedChangeListener((button, checked) -> confirm.setEnabled(checked));

        View.OnScrollChangeListener listener = (view, x, y, oldX, oldY) -> {
            View child = scroll.getChildAt(0);
            if (child != null && y + scroll.getHeight() >= child.getHeight() - dp(8)) {
                accepted.setEnabled(true);
                scrollHint.setVisibility(View.GONE);
            }
        };
        scroll.setOnScrollChangeListener(listener);
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View child = scroll.getChildAt(0);
            if (child != null && child.getHeight() <= scroll.getHeight()) {
                accepted.setEnabled(true);
                scrollHint.setVisibility(View.GONE);
            }
        });
        risk.show();
        Window riskWindow = risk.getWindow();
        if (riskWindow != null) {
            riskWindow.setLayout(
                    Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(32), dp(520)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showInfoDialog(int titleRes, int messageRes) {
        ContentDialog info = new ContentDialog(context);
        info.setTitle(titleRes);
        info.setMessage(messageRes);
        info.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        info.show();
    }

    private Dialog createThemedDialog() {
        int style = ThemeUtils.isDarkMode(context)
                ? R.style.ContentDialog_Dark
                : R.style.ContentDialog;
        Dialog themedDialog = new Dialog(context, style);
        themedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = themedDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return themedDialog;
    }

    private LinearLayout section(int titleRes) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(10), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(ThemeUtils.getColorAttr(context, R.attr.colorToolbarSurface));
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), ThemeUtils.getColorAttr(context, R.attr.colorOnSurfaceVariant));
        section.setBackground(background);

        TextView title = text(titleRes, 15, true);
        title.setTextColor(ThemeUtils.getColorAttr(context, R.attr.themeAccentColor));
        section.addView(title, matchWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        content.addView(section, params);
        return section;
    }

    private TextView text(int stringRes, int sizeSp, boolean bold) {
        return text(context.getString(stringRes), sizeSp, bold);
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(ThemeUtils.getColorAttr(context, R.attr.colorOnSurface));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(int stringRes) {
        return button(context.getString(stringRes));
    }

    private Button button(String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(ThemeUtils.getColorAttr(context, R.attr.themeAccentColor));
        button.setBackgroundResource(R.drawable.ui_add_button_outline);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private boolean resolved(String key) {
        boolean global = PerformanceSettings.INSTANCE.globalDefault(key);
        if (shortcut == null || !shortcut.hasExtra(key)) return global;
        String value = shortcut.getExtra(key, global ? "1" : "0");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private final class ToggleBinding {
        private final String key;
        private final SwitchCompat control;
        private final TextView status;
        private final Button reset;
        private final boolean rootOnly;
        private boolean refreshing;

        ToggleBinding(
                String key,
                SwitchCompat control,
                TextView status,
                Button reset,
                boolean rootOnly) {
            this.key = key;
            this.control = control;
            this.status = status;
            this.reset = reset;
            this.rootOnly = rootOnly;
        }

        void refresh() {
            refreshing = true;
            control.setChecked(resolved(key));
            boolean overridden = shortcut != null && shortcut.hasExtra(key);
            status.setText(overridden
                    ? R.string.performance_game_override
                    : R.string.performance_using_global);
            reset.setVisibility(overridden ? View.VISIBLE : View.GONE);
            refreshing = false;
        }

        void persistAndApply(boolean value) {
            if (refreshing) return;
            if (shortcut != null) {
                boolean global = PerformanceSettings.INSTANCE.globalDefault(key);
                shortcut.putExtra(key, value == global ? null : (value ? "1" : "0"));
                shortcut.saveData();
            } else {
                PerformanceSettings.INSTANCE.setGlobalDefault(key, value);
            }
            if (liveController != null) {
                liveController.apply(key, value);
            } else if (shortcut == null && rootOnly) {
                new Thread(
                        () -> PerfRootApplier.INSTANCE.apply(key, value),
                        "perf-root-default").start();
            }
            refresh();
        }

        void resetToGlobal() {
            if (shortcut == null) return;
            shortcut.removeExtra(key);
            shortcut.saveData();
            boolean global = PerformanceSettings.INSTANCE.globalDefault(key);
            if (liveController != null) liveController.apply(key, global);
            refresh();
        }
    }

}
