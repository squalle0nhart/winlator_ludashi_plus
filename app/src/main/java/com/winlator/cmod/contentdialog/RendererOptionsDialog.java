package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

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

        setOnConfirmCallback(() -> {
            if (!isNativeMode) {
                config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
                config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
            }
            config.setRendererFilterMode(spFilter.getSelectedItemPosition());
            config.setRendererSwapRB(cbSwapRB.isChecked());
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
