package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.widget.AdapterView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ScrollView;
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

        boolean supportsFrameGen();
        String getFrameGenBackend();
        void setFrameGenBackend(String v);

        boolean isLsfgDllAvailable();
        int getLsfgMultiplier();
        void setLsfgMultiplier(int v);
        void setLsfgEnabled(boolean v);
        float getLsfgFlowScale();
        void setLsfgFlowScale(float v);
        boolean getLsfgPerformanceMode();
        void setLsfgPerformanceMode(boolean v);

        int getBionicFgMultiplier();
        void setBionicFgMultiplier(int v);
        float getBionicFgFlowScale();
        void setBionicFgFlowScale(float v);
        int getBionicFgModel();
        void setBionicFgModel(int v);
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
    private static final String[] FRAME_GEN_BACKEND_IDS = {"lsfg_vk", "bionic_fg"};
    private static final String[] FRAME_GEN_BACKEND_LABELS = {"LSFG-VK", "Bionic-FG"};
    private static final int[] LSFG_MULTIPLIER_VALUES = {0, 2, 3, 4};
    private static final String[] LSFG_MULTIPLIER_LABELS = {"Off", "2x", "3x", "4x"};
    private static final int[] BIONIC_FG_MODEL_VALUES = {0, 1};
    private static final String[] BIONIC_FG_MODEL_LABELS = {"Model 0", "Model 1"};

    public RendererOptionsDialog(View anchorView, Config config, boolean isNativeMode) {
        super(anchorView.getContext(), R.layout.renderer_options_dialog);
        this.isNativeMode = isNativeMode;

        Context ctx = anchorView.getContext();
        findViewById(R.id.FrameLayout).getLayoutParams().width = Math.min(AppUtils.getPreferredDialogWidth(ctx), Math.round(UnitUtils.dpToPx(260)));
        ScrollView scrollView = findViewById(R.id.SVContent);
        if (scrollView != null) {
            int screenHeight = AppUtils.getScreenHeight();
            int screenWidth = AppUtils.getScreenWidth();
            float maxHeightRatio = screenWidth > screenHeight ? 0.45f : 0.55f;
            ViewGroup.LayoutParams params = scrollView.getLayoutParams();
            params.height = (int) (screenHeight * maxHeightRatio);
            scrollView.setLayoutParams(params);
        }

        Spinner  spPresent = findViewById(R.id.SPRendererPresentMode);
        Spinner  spDriver  = findViewById(R.id.SPRendererDriver);
        Spinner  spFilter  = findViewById(R.id.SPRendererFilter);
        CheckBox cbSwapRB  = findViewById(R.id.CBRendererSwapRB);
        View groupLsfg = findViewById(R.id.GroupLsfg);
        Spinner spFrameGenBackend = findViewById(R.id.SPFrameGenBackend);
        TextView tvLsfgStatus = findViewById(R.id.TVLsfgStatus);
        Spinner spLsfgMultiplier = findViewById(R.id.SPLsfgMultiplier);
        SeekBar sbLsfgFlowScale = findViewById(R.id.SBLsfgFlowScale);
        TextView tvLsfgFlowScale = findViewById(R.id.TVLsfgFlowScale);
        View groupBionicFgModel = findViewById(R.id.GroupBionicFgModel);
        Spinner spBionicFgModel = findViewById(R.id.SPBionicFgModel);
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

        boolean supportsFrameGen = config.supportsFrameGen();
        if (groupLsfg != null) {
            groupLsfg.setVisibility(supportsFrameGen ? View.VISIBLE : View.GONE);
        }
        if (supportsFrameGen && tvLsfgStatus != null && spFrameGenBackend != null && spLsfgMultiplier != null
                && sbLsfgFlowScale != null && tvLsfgFlowScale != null && cbLsfgPerformanceMode != null
                && groupBionicFgModel != null && spBionicFgModel != null) {
            setAmoledAdapter(ctx, spFrameGenBackend, FRAME_GEN_BACKEND_LABELS);
            setAmoledAdapter(ctx, spLsfgMultiplier, LSFG_MULTIPLIER_LABELS);
            setAmoledAdapter(ctx, spBionicFgModel, BIONIC_FG_MODEL_LABELS);

            final int[] selectedLsfgMultiplier = {config.getLsfgMultiplier()};
            final float[] selectedLsfgFlowScale = {sanitizeFlowScale(config.getLsfgFlowScale())};
            final boolean[] selectedLsfgPerformanceMode = {config.getLsfgPerformanceMode()};
            final int[] selectedBionicMultiplier = {config.getBionicFgMultiplier()};
            final float[] selectedBionicFlowScale = {sanitizeFlowScale(config.getBionicFgFlowScale())};
            final int[] selectedBionicModel = {config.getBionicFgModel()};
            final boolean[] syncingFrameGenUi = {false};

            int backendSelection = 0;
            String currentBackend = config.getFrameGenBackend();
            for (int i = 0; i < FRAME_GEN_BACKEND_IDS.length; i++) {
                if (FRAME_GEN_BACKEND_IDS[i].equals(currentBackend)) {
                    backendSelection = i;
                    break;
                }
            }
            spFrameGenBackend.setSelection(backendSelection);

            Runnable syncFrameGenUi = () -> {
                syncingFrameGenUi[0] = true;
                boolean useBionicFg = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()].equals("bionic_fg");
                boolean dllAvailable = !useBionicFg && config.isLsfgDllAvailable();
                int multiplier = useBionicFg ? selectedBionicMultiplier[0] : selectedLsfgMultiplier[0];
                float flowScale = useBionicFg ? selectedBionicFlowScale[0] : selectedLsfgFlowScale[0];

                tvLsfgStatus.setText(useBionicFg
                        ? "Bundled Vulkan frame generation layer. No DLL import required."
                        : (dllAvailable
                                ? "LSFG-VK runtime will use the imported Lossless.dll at launch."
                                : "Import Lossless.dll in app settings before enabling LSFG-VK."));

                int multiplierSelection = 0;
                for (int i = 0; i < LSFG_MULTIPLIER_VALUES.length; i++) {
                    if (LSFG_MULTIPLIER_VALUES[i] == multiplier) {
                        multiplierSelection = i;
                        break;
                    }
                }
                spLsfgMultiplier.setSelection(multiplierSelection);
                sbLsfgFlowScale.setMax(75);
                sbLsfgFlowScale.setProgress(Math.round((flowScale - 0.25f) * 100.0f));
                tvLsfgFlowScale.setText(String.format(Locale.US, "%.2f", flowScale));
                cbLsfgPerformanceMode.setChecked(selectedLsfgPerformanceMode[0]);
                groupBionicFgModel.setVisibility(useBionicFg ? View.VISIBLE : View.GONE);
                spBionicFgModel.setSelection(Math.max(0, Math.min(BIONIC_FG_MODEL_VALUES.length - 1, selectedBionicModel[0])));
                cbLsfgPerformanceMode.setVisibility(useBionicFg ? View.GONE : View.VISIBLE);
                spLsfgMultiplier.setEnabled(useBionicFg || dllAvailable);
                sbLsfgFlowScale.setEnabled(useBionicFg || dllAvailable);
                cbLsfgPerformanceMode.setEnabled(dllAvailable);
                spBionicFgModel.setEnabled(useBionicFg);
                syncingFrameGenUi[0] = false;
            };

            spFrameGenBackend.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    syncFrameGenUi.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            spLsfgMultiplier.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (syncingFrameGenUi[0]) return;
                    boolean useBionicFg = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()].equals("bionic_fg");
                    if (useBionicFg) selectedBionicMultiplier[0] = LSFG_MULTIPLIER_VALUES[position];
                    else selectedLsfgMultiplier[0] = LSFG_MULTIPLIER_VALUES[position];
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            sbLsfgFlowScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (syncingFrameGenUi[0]) return;
                    float value = sanitizeFlowScale(0.25f + (progress / 100.0f));
                    boolean useBionicFg = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()].equals("bionic_fg");
                    if (useBionicFg) selectedBionicFlowScale[0] = value;
                    else selectedLsfgFlowScale[0] = value;
                    tvLsfgFlowScale.setText(String.format(Locale.US, "%.2f", value));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            spBionicFgModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (syncingFrameGenUi[0]) return;
                    selectedBionicModel[0] = BIONIC_FG_MODEL_VALUES[position];
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            cbLsfgPerformanceMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!syncingFrameGenUi[0]) selectedLsfgPerformanceMode[0] = isChecked;
            });
            syncFrameGenUi.run();

            // Save on confirm
            setOnConfirmCallback(() -> {
                config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
                config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
                config.setRendererFilterMode(spFilter.getSelectedItemPosition());
                config.setRendererSwapRB(cbSwapRB.isChecked());

                String backend = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()];
                config.setFrameGenBackend(backend);
                config.setLsfgMultiplier(selectedLsfgMultiplier[0]);
                config.setLsfgEnabled("lsfg_vk".equals(backend) && selectedLsfgMultiplier[0] >= 2);
                config.setLsfgFlowScale(selectedLsfgFlowScale[0]);
                config.setLsfgPerformanceMode(selectedLsfgPerformanceMode[0]);
                config.setBionicFgMultiplier(selectedBionicMultiplier[0]);
                config.setBionicFgFlowScale(selectedBionicFlowScale[0]);
                config.setBionicFgModel(selectedBionicModel[0]);
            });
            return;
        }

        // Save on confirm
        setOnConfirmCallback(() -> {
            config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
            config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
            config.setRendererFilterMode(spFilter.getSelectedItemPosition());
            config.setRendererSwapRB(cbSwapRB.isChecked());
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
