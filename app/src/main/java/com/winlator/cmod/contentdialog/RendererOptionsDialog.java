package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FrameGenManager;
import com.winlator.cmod.core.UnitUtils;

import java.util.ArrayList;
import java.util.List;

public class RendererOptionsDialog extends ContentDialog {

    private final boolean isNativeMode;

    private void setGroupVisibility(int id, int vis) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(vis);
    }

    public interface Config {
        String getRendererPresentMode();
        void setRendererPresentMode(String v);

        String getRendererDriverId();
        void setRendererDriverId(String v);

        int getRendererFilterMode();
        void setRendererFilterMode(int v);

        boolean getRendererSwapRB();
        void setRendererSwapRB(boolean v);

        boolean isLsfgDllAvailable();
        String getFrameGenBackend();
        void setFrameGenBackend(String backend);
        int getLsfgMultiplier();
        void setLsfgMultiplier(int value);
        void setLsfgEnabled(boolean value);
        float getLsfgFlowScale();
        void setLsfgFlowScale(float value);
    }

    private static final String[] PRESENT_MODE_IDS    = {"mailbox", "fifo"};
    private static final String[] PRESENT_MODE_LABELS = {
        "Mailbox",
        "Fifo"
    };

    private static final String[] FILTER_LABELS_VULKAN = {
        "Bilinear",
        "Nearest neighbor",
        "Snapdragon Super Resolution",
        "SGSR HQ (edge direction)",
        "AMD FidelityFX Super Resolution",
        "Lanczos 2"
    };

    private static final int[] FILTER_VALUES_VULKAN = {0, 1, 2, 5, 3, 4};

    private static final String[] FILTER_LABELS_EGL = {
        "Bilinear",
        "Nearest neighbor",
        "Snapdragon Super Resolution",
        "SGSR HQ (edge direction)"
    };

    private static final int[] FILTER_VALUES_EGL = {0, 1, 2, 5};

    public RendererOptionsDialog(View anchorView, Config config, boolean isNativeMode) {
        super(anchorView.getContext(), R.layout.renderer_options_dialog);
        this.isNativeMode = isNativeMode;

        Context ctx = anchorView.getContext();
        findViewById(R.id.FrameLayout).getLayoutParams().width = Math.min(AppUtils.getPreferredDialogWidth(ctx), Math.round(UnitUtils.dpToPx(260)));

        Spinner  spPresent = findViewById(R.id.SPRendererPresentMode);
        Spinner  spDriver  = findViewById(R.id.SPRendererDriver);
        Spinner  spFilter  = findViewById(R.id.SPRendererFilter);
        CheckBox cbSwapRB  = findViewById(R.id.CBRendererSwapRB);

        setGroupVisibility(R.id.GroupPresentMode, isNativeMode ? View.GONE : View.VISIBLE);
        setGroupVisibility(R.id.GroupDriver,      isNativeMode ? View.GONE : View.VISIBLE);
        setGroupVisibility(R.id.GroupFilter,      View.VISIBLE);
        cbSwapRB.setVisibility(View.VISIBLE);

        setAmoledAdapter(ctx, spPresent, PRESENT_MODE_LABELS);
        int pmSel = 0;
        String curPm = config.getRendererPresentMode();
        for (int i = 0; i < PRESENT_MODE_IDS.length; i++) {
            if (PRESENT_MODE_IDS[i].equals(curPm)) { pmSel = i; break; }
        }
        spPresent.setSelection(pmSel);
        findViewById(R.id.BTHelpPresentMode).setOnClickListener(v -> AppUtils.showHelpBox(ctx, v,
                "FIFO queues frames in order and avoids tearing; use it with native frame generation. Mailbox keeps the newest completed frame and can reduce latency when frame generation is off."));
        findViewById(R.id.BTHelpFrameGeneration).setOnClickListener(v -> AppUtils.showHelpBox(ctx, v,
                "Win-FG Native is built in and runs at 2x. LSFG Native supports 2x–4x, requires Lossless.dll, Vulkan 1.3 and a compatible Vulkan renderer driver."));

        AdrenotoolsManager atm = new AdrenotoolsManager(ctx);
        List<String> driverLabels = new ArrayList<>();
        List<String> driverIds    = new ArrayList<>();
        driverLabels.add("System");  driverIds.add("system");
        for (String id : atm.enumarateInstalledDrivers()) {
            driverLabels.add(atm.getDriverName(id) + " " + atm.getDriverVersion(id));
            driverIds.add(id);
        }
        setAmoledAdapter(ctx, spDriver, driverLabels);
        String curDrv = config.getRendererDriverId();
        int drvSel = 0;
        for (int i = 0; i < driverIds.size(); i++) {
            if (driverIds.get(i).equals(curDrv)) { drvSel = i; break; }
        }
        spDriver.setSelection(drvSel);

        String[] filterLabels = isNativeMode ? FILTER_LABELS_EGL : FILTER_LABELS_VULKAN;
        int[] filterValues = isNativeMode ? FILTER_VALUES_EGL : FILTER_VALUES_VULKAN;
        setAmoledAdapter(ctx, spFilter, filterLabels);
        int filterSel = indexOf(filterValues, config.getRendererFilterMode());
        spFilter.setSelection(filterSel);
        cbSwapRB.setChecked(config.getRendererSwapRB());

        View frameGenGroup = findViewById(R.id.GroupFrameGen);
        Spinner spMultiplier = findViewById(R.id.SPFrameGenMultiplier);
        SeekBar sbFlowScale = findViewById(R.id.SBFrameGenFlowScale);
        TextView tvFlowScale = findViewById(R.id.TVLsfgFlowScale);
        TextView tvStatus = findViewById(R.id.TVFrameGenStatus);
        frameGenGroup.setVisibility(isNativeMode ? View.GONE : View.VISIBLE);
        setAmoledAdapter(ctx, spMultiplier, new String[]{
                "Off", "Win-FG Native", "LSFG Native 2x", "LSFG Native 3x", "LSFG Native 4x"});
        int multiplier = config.getLsfgMultiplier();
        String backend = FrameGenManager.normalizeBackend(config.getFrameGenBackend());
        spMultiplier.setSelection(multiplier < 2 ? 0
                : FrameGenManager.BACKEND_WIN_FG_NATIVE.equals(backend) ? 1
                : Math.min(4, multiplier));
        float flowScale = config.getLsfgFlowScale();
        tvStatus.setText(config.isLsfgDllAvailable()
                ? "Win-FG Native is built in; LSFG Native uses the imported Lossless.dll."
                : "LSFG Native can't run: Lossless.dll is missing. Import it, then relaunch the game. Win-FG Native is built in.");
        sbFlowScale.setMax(75);
        sbFlowScale.setProgress(Math.round((flowScale - 0.25f) * 100));
        tvFlowScale.setText(String.format(java.util.Locale.US, "%.2f", flowScale));
        sbFlowScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvFlowScale.setText(String.format(java.util.Locale.US, "%.2f", 0.25f + progress / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        setOnConfirmCallback(() -> {
            if (!isNativeMode) {
                config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
                config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
            }
            config.setRendererFilterMode(filterValues[spFilter.getSelectedItemPosition()]);
            config.setRendererSwapRB(cbSwapRB.isChecked());

            int selection = spMultiplier.getSelectedItemPosition();
            boolean winFg = selection == 1;
            int selectedMultiplier = selection < 1 ? 0 : winFg ? 2 : selection;
            if (!winFg && selectedMultiplier >= 2 && !config.isLsfgDllAvailable()) selectedMultiplier = 0;
            config.setFrameGenBackend(winFg
                    ? FrameGenManager.BACKEND_WIN_FG_NATIVE : FrameGenManager.BACKEND_LSFG_NATIVE);
            config.setLsfgMultiplier(selectedMultiplier);
            config.setLsfgEnabled(!winFg && selectedMultiplier >= 2);
            config.setLsfgFlowScale(0.25f + sbFlowScale.getProgress() / 100f);
        });
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }

    private void setAmoledAdapter(Context ctx, Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, R.layout.spinner_item_amoled, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_amoled);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundResource(R.drawable.dialog_background_dark_blue);
    }

    private void setAmoledAdapter(Context ctx, Spinner spinner, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, R.layout.spinner_item_amoled, items);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_amoled);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundResource(R.drawable.dialog_background_dark_blue);
    }

    public static int toVkPresentMode(String mode) {
        if (mode == null) return 2;
        switch (mode) {
            case "mailbox":       return 1;
            default:              return 2;
        }
    }
}
