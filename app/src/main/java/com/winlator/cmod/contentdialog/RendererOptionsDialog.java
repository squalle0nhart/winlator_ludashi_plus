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
import java.util.Locale;

public class RendererOptionsDialog extends ContentDialog {

    private final boolean isNativeMode;

    private void setGroupVisibility(int id, int vis) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(vis);
    }

    public interface Config {
        boolean getRendererNative();
        void setRendererNative(boolean v);

        String getRendererPresentMode();
        void setRendererPresentMode(String v);

        String getRendererDriverId();
        void setRendererDriverId(String v);

        int getRendererFilterMode();
        void setRendererFilterMode(int v);

        boolean getRendererSwapRB();
        void setRendererSwapRB(boolean v);

        boolean supportsLsfg();
        boolean isLsfgDllAvailable();
        int getLsfgMultiplier();
        void setLsfgMultiplier(int v);
        void setLsfgEnabled(boolean v);
        float getLsfgFlowScale();
        void setLsfgFlowScale(float v);
        boolean getLsfgPerformanceMode();
        void setLsfgPerformanceMode(boolean v);
    }

    private static final String[] PRESENT_MODE_IDS    = {"mailbox", "fifo"};
    private static final String[] PRESENT_MODE_LABELS = {
        "Mailbox",
        "Fifo"
    };

    private static final String[] FILTER_LABELS = {
        "Bilinear",
        "Nearest neighbor",
        "Snapdragon Super Resolution"
    };
    private static final int[] LSFG_MULTIPLIER_VALUES = {0, 2, 3, 4};
    private static final String[] LSFG_MULTIPLIER_LABELS = {"Off", "2x", "3x", "4x"};

    public RendererOptionsDialog(View anchorView, Config config, boolean isNativeMode) {
        super(anchorView.getContext(), R.layout.renderer_options_dialog);
        this.isNativeMode = isNativeMode;

        Context ctx = anchorView.getContext();
        findViewById(R.id.FrameLayout).getLayoutParams().width = Math.min(AppUtils.getPreferredDialogWidth(ctx), Math.round(UnitUtils.dpToPx(260)));

        Spinner  spPresent = findViewById(R.id.SPRendererPresentMode);
        Spinner  spDriver  = findViewById(R.id.SPRendererDriver);
        Spinner  spFilter  = findViewById(R.id.SPRendererFilter);
        CheckBox cbSwapRB  = findViewById(R.id.CBRendererSwapRB);
        View groupLsfg = findViewById(R.id.GroupLsfg);
        TextView tvLsfgStatus = findViewById(R.id.TVLsfgStatus);
        Spinner spLsfgMultiplier = findViewById(R.id.SPLsfgMultiplier);
        SeekBar sbLsfgFlowScale = findViewById(R.id.SBLsfgFlowScale);
        TextView tvLsfgFlowScale = findViewById(R.id.TVLsfgFlowScale);
        CheckBox cbLsfgPerformanceMode = findViewById(R.id.CBLsfgPerformanceMode);

        // Keep driver and filter controls available in both Vulkan and native modes.
        setGroupVisibility(R.id.GroupDriver,  View.VISIBLE);
        setGroupVisibility(R.id.GroupFilter,  View.VISIBLE);

        // Present Mode (visible in both modes)
        setAmoledAdapter(ctx, spPresent, PRESENT_MODE_LABELS);
        int pmSel = 0;
        String curPm = config.getRendererPresentMode();
        for (int i = 0; i < PRESENT_MODE_IDS.length; i++) {
            if (PRESENT_MODE_IDS[i].equals(curPm)) { pmSel = i; break; }
        }
        spPresent.setSelection(pmSel);

        // Renderer Driver
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

        // Texture Filter
        setAmoledAdapter(ctx, spFilter, FILTER_LABELS);
        spFilter.setSelection(config.getRendererFilterMode());
        cbSwapRB.setChecked(config.getRendererSwapRB());

        boolean supportsLsfg = config.supportsLsfg();
        if (groupLsfg != null) {
            groupLsfg.setVisibility(supportsLsfg ? View.VISIBLE : View.GONE);
        }
        if (supportsLsfg && tvLsfgStatus != null && spLsfgMultiplier != null && sbLsfgFlowScale != null
                && tvLsfgFlowScale != null && cbLsfgPerformanceMode != null) {
            boolean dllAvailable = config.isLsfgDllAvailable();
            tvLsfgStatus.setText(dllAvailable
                    ? "LSFG-VK runtime will use the imported Lossless.dll at launch."
                    : "Import Lossless.dll in app settings before enabling LSFG-VK.");

            setAmoledAdapter(ctx, spLsfgMultiplier, LSFG_MULTIPLIER_LABELS);
            int multiplierSelection = 0;
            int currentMultiplier = config.getLsfgMultiplier();
            for (int i = 0; i < LSFG_MULTIPLIER_VALUES.length; i++) {
                if (LSFG_MULTIPLIER_VALUES[i] == currentMultiplier) {
                    multiplierSelection = i;
                    break;
                }
            }
            spLsfgMultiplier.setSelection(multiplierSelection);

            float flowScale = sanitizeFlowScale(config.getLsfgFlowScale());
            sbLsfgFlowScale.setMax(75);
            sbLsfgFlowScale.setProgress(Math.round((flowScale - 0.25f) * 100.0f));
            tvLsfgFlowScale.setText(String.format(Locale.US, "%.2f", flowScale));
            sbLsfgFlowScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = sanitizeFlowScale(0.25f + (progress / 100.0f));
                    tvLsfgFlowScale.setText(String.format(Locale.US, "%.2f", value));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            cbLsfgPerformanceMode.setChecked(config.getLsfgPerformanceMode());

            spLsfgMultiplier.setEnabled(dllAvailable);
            sbLsfgFlowScale.setEnabled(dllAvailable);
            cbLsfgPerformanceMode.setEnabled(dllAvailable);
        }

        // Save on confirm
        setOnConfirmCallback(() -> {
            config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
            config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
            config.setRendererFilterMode(spFilter.getSelectedItemPosition());
            config.setRendererSwapRB(cbSwapRB.isChecked());
            if (supportsLsfg && spLsfgMultiplier != null && sbLsfgFlowScale != null && cbLsfgPerformanceMode != null) {
                int multiplier = LSFG_MULTIPLIER_VALUES[spLsfgMultiplier.getSelectedItemPosition()];
                config.setLsfgMultiplier(multiplier);
                config.setLsfgEnabled(multiplier >= 2);
                config.setLsfgFlowScale(sanitizeFlowScale(0.25f + (sbLsfgFlowScale.getProgress() / 100.0f)));
                config.setLsfgPerformanceMode(cbLsfgPerformanceMode.isChecked());
            }
        });
    }

    private static float sanitizeFlowScale(float value) {
        return Math.max(0.25f, Math.min(1.0f, value));
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
