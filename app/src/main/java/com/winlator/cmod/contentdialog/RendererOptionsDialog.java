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
        boolean getLsfgPerformanceMode();
        void setLsfgPerformanceMode(boolean value);
        int getWinFgMultiplier();
        void setWinFgMultiplier(int value);
        float getWinFgFlowScale();
        void setWinFgFlowScale(float value);
        int getWinFgModel();
        void setWinFgModel(int value);
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
        "AMD FidelityFX Super Resolution",
        "Lanczos 2"
    };

    private static final String[] FILTER_LABELS_EGL = {
        "Bilinear",
        "Nearest neighbor"
    };

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
        setAmoledAdapter(ctx, spFilter, filterLabels);
        int filterSel = config.getRendererFilterMode();
        if (filterSel < 0 || filterSel >= filterLabels.length) filterSel = 0;
        spFilter.setSelection(filterSel);
        cbSwapRB.setChecked(config.getRendererSwapRB());

        View frameGenGroup = findViewById(R.id.GroupFrameGen);
        Spinner spBackend = findViewById(R.id.SPFrameGenBackend);
        Spinner spMultiplier = findViewById(R.id.SPFrameGenMultiplier);
        Spinner spModel = findViewById(R.id.SPWinFgModel);
        SeekBar sbFlowScale = findViewById(R.id.SBFrameGenFlowScale);
        TextView tvFlowScale = findViewById(R.id.TVLsfgFlowScale);
        TextView tvStatus = findViewById(R.id.TVFrameGenStatus);
        View modelGroup = findViewById(R.id.GroupWinFgModel);
        CheckBox cbPerformance = findViewById(R.id.CBFrameGenPerformanceMode);

        frameGenGroup.setVisibility(isNativeMode ? View.GONE : View.VISIBLE);
        setAmoledAdapter(ctx, spBackend, new String[]{"LSFG-VK", "win-fg", "LSFG Native"});
        setAmoledAdapter(ctx, spMultiplier, new String[]{"Off", "2x", "3x", "4x"});
        setAmoledAdapter(ctx, spModel, new String[]{"FSR3", "FSR3+"});

        boolean winFg = "win_fg".equals(config.getFrameGenBackend());
        spBackend.setSelection("lsfg_native".equals(config.getFrameGenBackend()) ? 2 : winFg ? 1 : 0);
        int multiplier = winFg ? config.getWinFgMultiplier() : config.getLsfgMultiplier();
        spMultiplier.setSelection(multiplier < 2 ? 0 : Math.min(3, multiplier - 1));
        spModel.setSelection(Math.max(0, Math.min(1, config.getWinFgModel() - 3)));
        float flowScale = winFg ? config.getWinFgFlowScale() : config.getLsfgFlowScale();
        sbFlowScale.setMax(75);
        sbFlowScale.setProgress(Math.round((flowScale - 0.25f) * 100));
        tvFlowScale.setText(String.format(java.util.Locale.US, "%.2f", flowScale));
        cbPerformance.setChecked(config.getLsfgPerformanceMode());

        Runnable updateFrameGenUi = () -> {
            boolean useWinFg = spBackend.getSelectedItemPosition() == 1;
            modelGroup.setVisibility(useWinFg ? View.VISIBLE : View.GONE);
            cbPerformance.setVisibility(spBackend.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
            tvStatus.setText(useWinFg
                    ? "Bundled win-fg runtime"
                    : config.isLsfgDllAvailable()
                            ? spBackend.getSelectedItemPosition() == 2
                                    ? "Requires the Vulkan renderer and a Vulkan 1.3 driver" : "LSFG-VK ready"
                            : "Import Lossless.dll before enabling LSFG");
        };
        spBackend.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateFrameGenUi.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        sbFlowScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvFlowScale.setText(String.format(java.util.Locale.US, "%.2f", 0.25f + progress / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        updateFrameGenUi.run();

        setOnConfirmCallback(() -> {
            if (!isNativeMode) {
                config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
                config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
            }
            config.setRendererFilterMode(spFilter.getSelectedItemPosition());
            config.setRendererSwapRB(cbSwapRB.isChecked());

            boolean useWinFg = spBackend.getSelectedItemPosition() == 1;
            int selectedMultiplier = spMultiplier.getSelectedItemPosition() == 0
                    ? 0 : spMultiplier.getSelectedItemPosition() + 1;
            float selectedFlowScale = 0.25f + sbFlowScale.getProgress() / 100f;
            config.setFrameGenBackend(spBackend.getSelectedItemPosition() == 2 ? "lsfg_native" : useWinFg ? "win_fg" : "lsfg_vk");
            if (useWinFg) {
                config.setWinFgMultiplier(selectedMultiplier);
                config.setWinFgFlowScale(selectedFlowScale);
                config.setWinFgModel(spModel.getSelectedItemPosition() + 3);
                config.setLsfgEnabled(false);
            } else {
                int lsfgMultiplier = config.isLsfgDllAvailable() ? selectedMultiplier : 0;
                config.setLsfgMultiplier(lsfgMultiplier);
                config.setLsfgEnabled(lsfgMultiplier >= 2);
                config.setLsfgFlowScale(selectedFlowScale);
                config.setLsfgPerformanceMode(cbPerformance.isChecked());
            }
        });
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
