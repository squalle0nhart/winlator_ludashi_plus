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
        String getRenderer();
        void setRenderer(String v);

        boolean getRendererNative();
        void setRendererNative(boolean v);

        String getRendererNativeBackend();
        void setRendererNativeBackend(String v);

        String getRendererPresentMode();
        void setRendererPresentMode(String v);

        String getRendererDriverId();
        void setRendererDriverId(String v);

        int getRendererFilterMode();
        void setRendererFilterMode(int v);

        boolean getRendererSwapRB();
        void setRendererSwapRB(boolean v);

        boolean getRendererSfCompatMode();
        void setRendererSfCompatMode(boolean v);

        boolean getRendererLegacyScanout();
        void setRendererLegacyScanout(boolean v);

        int getGraphicsFilterMode();
        void setGraphicsFilterMode(int v);

        boolean getGraphicsSupersamplingEnabled();
        void setGraphicsSupersamplingEnabled(boolean v);

        int getGraphicsPostFXMode();
        void setGraphicsPostFXMode(int v);

        float getGraphicsSharpness();
        void setGraphicsSharpness(float v);

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

        int getWinFgMultiplier();
        void setWinFgMultiplier(int v);
        float getWinFgFlowScale();
        void setWinFgFlowScale(float v);
        int getWinFgModel();
        void setWinFgModel(int v);

    }

    private static final String[] PRESENT_MODE_IDS    = {"fifo", "mailbox", "immediate"};
    private static final String[] PRESENT_MODE_LABELS = {
        "FIFO",
        "Mailbox",
        "Immediate"
    };

    private static final String[] FILTER_LABELS = {
        "Bilinear (Linear)",
        "Nearest neighbor",
        "Snapdragon Super Resolution"
    };
    private static final String[] FRAME_GEN_BACKEND_IDS = {"lsfg_vk", "win_fg"};
    private static final String[] FRAME_GEN_BACKEND_LABELS = {"LSFG-VK", "win-fg"};
    private static final String[] NATIVE_BACKEND_IDS = {"auto", "asr", "flip"};
    private static final String[] UPSCALER_LABELS = {"SGSR", "FSR / FidelityFX-CAS", "DLS", "NVScaler"};
    private static final int[] UPSCALER_FILTER_VALUES = {2, 4, 5, 3};
    private static final String[] POSTFX_LABELS = {"None", "DLS", "CRT", "HDR", "Natural"};
    private static final int[] LSFG_MULTIPLIER_VALUES = {0, 2, 3, 4};
    private static final String[] LSFG_MULTIPLIER_LABELS = {"Off", "2x", "3x", "4x"};
    private static final int[] WIN_FG_MULTIPLIER_VALUES = {0, 2};
    private static final String[] WIN_FG_MULTIPLIER_LABELS = {"Off", "On"};
    private static final String[] WIN_FG_MODEL_LABELS = {
        "Optical flow",
        "Optical flow · bidirectional"
    };

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

        Spinner  spRenderer = findViewById(R.id.SPRendererType);
        Spinner  spPresent = findViewById(R.id.SPRendererPresentMode);
        View presentModeNote = findViewById(R.id.TVRendererPresentModeNote);
        Spinner  spDriver  = findViewById(R.id.SPRendererDriver);
        Spinner  spFilter  = findViewById(R.id.SPRendererFilter);
        CheckBox cbNativeRendering = findViewById(R.id.CBRendererNative);
        View groupNativeBackend = findViewById(R.id.GroupRendererNativeBackend);
        Spinner spNativeBackend = findViewById(R.id.SPRendererNativeBackend);
        CheckBox cbSwapRB  = findViewById(R.id.CBRendererSwapRB);
        CheckBox cbSfCompatMode = findViewById(R.id.CBRendererSfCompatMode);
        CheckBox cbLegacyScanout = findViewById(R.id.CBRendererLegacyScanout);
        CheckBox cbDefaultUpscaler = findViewById(R.id.CBDefaultUpscaler);
        CheckBox cbDefaultSupersampling = findViewById(R.id.CBDefaultSupersampling);
        Spinner spDefaultUpscaler = findViewById(R.id.SPDefaultUpscalerMode);
        Spinner spDefaultPostFX = findViewById(R.id.SPDefaultPostFXMode);
        com.winlator.cmod.widget.SeekBar sbDefaultSharpness = findViewById(R.id.SBDefaultSharpness);
        TextView tvDefaultSharpnessValue = findViewById(R.id.TVDefaultSharpnessValue);
        View groupLsfg = findViewById(R.id.GroupLsfg);
        Spinner spFrameGenBackend = findViewById(R.id.SPFrameGenBackend);
        TextView tvLsfgStatus = findViewById(R.id.TVLsfgStatus);
        Spinner spLsfgMultiplier = findViewById(R.id.SPLsfgMultiplier);
        SeekBar sbLsfgFlowScale = findViewById(R.id.SBLsfgFlowScale);
        TextView tvLsfgFlowScale = findViewById(R.id.TVLsfgFlowScale);
        View groupFrameGenModel = findViewById(R.id.GroupFrameGenModel);
        Spinner spFrameGenModel = findViewById(R.id.SPFrameGenModel);
        CheckBox cbLsfgPerformanceMode = findViewById(R.id.CBLsfgPerformanceMode);

        View presentModeHelp = findViewById(R.id.BTHelpRendererPresentMode);
        if (presentModeHelp != null) {
            presentModeHelp.setOnClickListener(v ->
                    AppUtils.showHelpBox(ctx, v, R.string.renderer_present_mode_help_content));
        }

        List<String> rendererIds = new ArrayList<>();
        List<String> rendererLabels = new ArrayList<>();
        rendererIds.add("gl");
        rendererLabels.add("OpenGL");
        rendererIds.add("vulkan");
        rendererLabels.add("Vulkan");
        setAmoledAdapter(ctx, spRenderer, rendererLabels);
        int rendererSel = 1;
        String currentRenderer = config.getRenderer();
        boolean legacySurfaceFlinger = "surfaceflinger".equalsIgnoreCase(currentRenderer);
        if ("displayx".equalsIgnoreCase(currentRenderer) || legacySurfaceFlinger) currentRenderer = "vulkan";
        for (int i = 0; i < rendererIds.size(); i++) {
            if (rendererIds.get(i).equalsIgnoreCase(currentRenderer)) {
                rendererSel = i;
                break;
            }
        }
        spRenderer.setSelection(rendererSel);

        final boolean[] isExternalFrameGenRendererSelected = {true};
        final Runnable[] syncFrameGenUiRef = new Runnable[1];
        Runnable syncRendererUi = () -> {
            int rendererPosition = spRenderer.getSelectedItemPosition();
            boolean isVulkanRenderer = rendererPosition == 1;
            boolean isGlRenderer = rendererPosition == 0;
            isExternalFrameGenRendererSelected[0] = isVulkanRenderer;
            setGroupVisibility(R.id.GroupDriver, isVulkanRenderer ? View.VISIBLE : View.GONE);
            setGroupVisibility(R.id.GroupFilter, View.VISIBLE);
            if (cbDefaultSupersampling != null) cbDefaultSupersampling.setVisibility(isVulkanRenderer ? View.VISIBLE : View.GONE);
            if (spPresent != null) spPresent.setEnabled(isVulkanRenderer);
            if (groupLsfg != null) groupLsfg.setAlpha(isExternalFrameGenRendererSelected[0] ? 1.0f : 0.5f);
            if (spFrameGenBackend != null) spFrameGenBackend.setEnabled(isExternalFrameGenRendererSelected[0]);
            if (cbNativeRendering != null) cbNativeRendering.setVisibility(
                    isGlRenderer || isVulkanRenderer ? View.VISIBLE : View.GONE);
            if (groupNativeBackend != null) groupNativeBackend.setVisibility(
                    isVulkanRenderer ? View.VISIBLE : View.GONE);
            if (cbSwapRB != null) cbSwapRB.setVisibility((isVulkanRenderer || isGlRenderer) ? View.VISIBLE : View.GONE);
            if (cbSfCompatMode != null) cbSfCompatMode.setVisibility(View.GONE);
            if (cbLegacyScanout != null) cbLegacyScanout.setVisibility(View.GONE);
            if (syncFrameGenUiRef[0] != null) syncFrameGenUiRef[0].run();
        };
        spRenderer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                syncRendererUi.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        syncRendererUi.run();

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
        cbNativeRendering.setChecked(isNativeMode || legacySurfaceFlinger);
        setAmoledAdapter(ctx, spNativeBackend, new String[] {
                ctx.getString(R.string.renderer_native_backend_auto),
                ctx.getString(R.string.renderer_native_backend_asr),
                ctx.getString(R.string.renderer_native_backend_flip)
        });
        String nativeBackend = legacySurfaceFlinger ? "asr" : config.getRendererNativeBackend();
        int nativeBackendSelection = 0;
        for (int i = 0; i < NATIVE_BACKEND_IDS.length; i++) {
            if (NATIVE_BACKEND_IDS[i].equalsIgnoreCase(nativeBackend)) {
                nativeBackendSelection = i;
                break;
            }
        }
        spNativeBackend.setSelection(nativeBackendSelection);
        cbNativeRendering.setOnCheckedChangeListener((button, checked) -> syncRendererUi.run());
        syncRendererUi.run();
        cbSwapRB.setChecked(config.getRendererSwapRB());
        cbSfCompatMode.setChecked(config.getRendererSfCompatMode());
        cbLegacyScanout.setChecked(config.getRendererLegacyScanout());
        setAmoledAdapter(ctx, spDefaultUpscaler, UPSCALER_LABELS);
        setAmoledAdapter(ctx, spDefaultPostFX, POSTFX_LABELS);
        int currentGraphicsFilter = config.getGraphicsFilterMode();
        cbDefaultUpscaler.setChecked(currentGraphicsFilter > 0);
        cbDefaultSupersampling.setChecked(config.getGraphicsSupersamplingEnabled());
        spDefaultUpscaler.setSelection(getUpscalerSelection(currentGraphicsFilter));
        int currentPostFX = Math.max(0, Math.min(POSTFX_LABELS.length - 1, config.getGraphicsPostFXMode()));
        spDefaultPostFX.setSelection(currentPostFX);
        float currentSharpness = Math.max(0f, Math.min(100f, config.getGraphicsSharpness() * 100f));
        sbDefaultSharpness.setValue(currentSharpness);
        tvDefaultSharpnessValue.setText(String.valueOf(Math.round(currentSharpness)));
        sbDefaultSharpness.setOnValueChangeListener((seekBar, value) ->
            tvDefaultSharpnessValue.setText(String.valueOf(Math.round(value))));

        boolean supportsFrameGen = config.supportsFrameGen();
        if (groupLsfg != null) {
            groupLsfg.setVisibility(supportsFrameGen ? View.VISIBLE : View.GONE);
        }
        if (supportsFrameGen && tvLsfgStatus != null && spFrameGenBackend != null && spLsfgMultiplier != null
                && sbLsfgFlowScale != null && tvLsfgFlowScale != null && cbLsfgPerformanceMode != null
                && groupFrameGenModel != null && spFrameGenModel != null) {
            setAmoledAdapter(ctx, spFrameGenBackend, FRAME_GEN_BACKEND_LABELS);
            setAmoledAdapter(ctx, spFrameGenModel, WIN_FG_MODEL_LABELS);

            final int[] selectedLsfgMultiplier = {config.getLsfgMultiplier()};
            final float[] selectedLsfgFlowScale = {sanitizeFlowScale(config.getLsfgFlowScale())};
            final boolean[] selectedLsfgPerformanceMode = {config.getLsfgPerformanceMode()};
            final int[] selectedWinMultiplier = {config.getWinFgMultiplier()};
            final float[] selectedWinFlowScale = {sanitizeFlowScale(config.getWinFgFlowScale())};
            final int[] selectedWinModel = {Math.max(3, Math.min(4, config.getWinFgModel()))};
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
                int backendPosition = spFrameGenBackend.getSelectedItemPosition();
                boolean useWinFg = FRAME_GEN_BACKEND_IDS[backendPosition].equals("win_fg");
                boolean frameGenAvailable = isExternalFrameGenRendererSelected[0];
                boolean dllAvailable = !useWinFg && config.isLsfgDllAvailable();
                int multiplier = useWinFg ? selectedWinMultiplier[0]
                        : selectedLsfgMultiplier[0];
                float flowScale = useWinFg ? selectedWinFlowScale[0] : selectedLsfgFlowScale[0];

                tvLsfgStatus.setText(!frameGenAvailable
                        ? ctx.getString(R.string.frame_generation_requires_vulkan_or_surfaceflinger)
                        : (useWinFg
                                ? "Clean-room win-fg Vulkan layer with live model and flow-scale updates. On uses 2x frame generation."
                                : (dllAvailable
                                        ? "Experimental LSFG-VK will use the imported Lossless.dll at launch."
                                        : "Import Lossless.dll in app settings before enabling LSFG-VK.")));

                int multiplierSelection = 0;
                int[] multiplierValues = useWinFg ? WIN_FG_MULTIPLIER_VALUES : LSFG_MULTIPLIER_VALUES;
                setAmoledAdapter(ctx, spLsfgMultiplier,
                        useWinFg ? WIN_FG_MULTIPLIER_LABELS : LSFG_MULTIPLIER_LABELS);
                for (int i = 0; i < multiplierValues.length; i++) {
                    if (multiplierValues[i] == multiplier) {
                        multiplierSelection = i;
                        break;
                    }
                }
                spLsfgMultiplier.setSelection(multiplierSelection);
                sbLsfgFlowScale.setMax(75);
                sbLsfgFlowScale.setProgress(Math.round((flowScale - 0.25f) * 100.0f));
                tvLsfgFlowScale.setText(String.format(Locale.US, "%.2f", flowScale));
                cbLsfgPerformanceMode.setChecked(selectedLsfgPerformanceMode[0]);
                groupFrameGenModel.setVisibility(useWinFg ? View.VISIBLE : View.GONE);
                spFrameGenModel.setSelection(selectedWinModel[0] - 3);
                cbLsfgPerformanceMode.setVisibility(useWinFg ? View.GONE : View.VISIBLE);
                spFrameGenBackend.setEnabled(isExternalFrameGenRendererSelected[0]);
                spLsfgMultiplier.setEnabled(frameGenAvailable && (useWinFg || dllAvailable));
                sbLsfgFlowScale.setVisibility(View.VISIBLE);
                tvLsfgFlowScale.setVisibility(View.VISIBLE);
                sbLsfgFlowScale.setEnabled(frameGenAvailable && (useWinFg || dllAvailable));
                cbLsfgPerformanceMode.setEnabled(frameGenAvailable && dllAvailable);
                spFrameGenModel.setEnabled(frameGenAvailable && useWinFg);
                if (presentModeNote != null) presentModeNote.setVisibility(View.GONE);
                syncingFrameGenUi[0] = false;
            };
            syncFrameGenUiRef[0] = syncFrameGenUi;

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
                    boolean useWinFg = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()].equals("win_fg");
                    int multiplier = useWinFg
                            ? WIN_FG_MULTIPLIER_VALUES[Math.min(position, WIN_FG_MULTIPLIER_VALUES.length - 1)]
                            : LSFG_MULTIPLIER_VALUES[Math.min(position, LSFG_MULTIPLIER_VALUES.length - 1)];
                    if (useWinFg) selectedWinMultiplier[0] = multiplier;
                    else selectedLsfgMultiplier[0] = multiplier;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            sbLsfgFlowScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (syncingFrameGenUi[0]) return;
                    float value = sanitizeFlowScale(0.25f + (progress / 100.0f));
                    boolean useWinFg = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()].equals("win_fg");
                    if (useWinFg) selectedWinFlowScale[0] = value;
                    else selectedLsfgFlowScale[0] = value;
                    tvLsfgFlowScale.setText(String.format(Locale.US, "%.2f", value));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            spFrameGenModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (syncingFrameGenUi[0]) return;
                    selectedWinModel[0] = Math.max(3, Math.min(4, position + 3));
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
                config.setRenderer(rendererIds.get(spRenderer.getSelectedItemPosition()));
                config.setGraphicsFilterMode(cbDefaultUpscaler.isChecked()
                        ? getSelectedUpscalerFilterMode(spDefaultUpscaler) : 0);
                config.setGraphicsSupersamplingEnabled(cbDefaultSupersampling.isChecked());
                config.setGraphicsPostFXMode(spDefaultPostFX.getSelectedItemPosition());
                config.setGraphicsSharpness(sbDefaultSharpness.getValue() / 100f);
                config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
                config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
                config.setRendererFilterMode(spFilter.getSelectedItemPosition());
                config.setRendererNative(cbNativeRendering.isChecked());
                config.setRendererNativeBackend(NATIVE_BACKEND_IDS[spNativeBackend.getSelectedItemPosition()]);
                config.setRendererSwapRB(cbSwapRB.isChecked());
                config.setRendererSfCompatMode(cbSfCompatMode.isChecked());
                config.setRendererLegacyScanout(cbLegacyScanout.isChecked());

                String backend = FRAME_GEN_BACKEND_IDS[spFrameGenBackend.getSelectedItemPosition()];
                config.setFrameGenBackend(backend);
                config.setLsfgMultiplier(selectedLsfgMultiplier[0]);
                config.setLsfgEnabled("lsfg_vk".equals(backend) && selectedLsfgMultiplier[0] >= 2);
                config.setLsfgFlowScale(selectedLsfgFlowScale[0]);
                config.setLsfgPerformanceMode(selectedLsfgPerformanceMode[0]);
                config.setWinFgMultiplier(selectedWinMultiplier[0]);
                config.setWinFgFlowScale(selectedWinFlowScale[0]);
                config.setWinFgModel(selectedWinModel[0]);
            });
            return;
        }

        // Save on confirm
        setOnConfirmCallback(() -> {
            config.setRenderer(rendererIds.get(spRenderer.getSelectedItemPosition()));
            config.setGraphicsFilterMode(cbDefaultUpscaler.isChecked()
                    ? getSelectedUpscalerFilterMode(spDefaultUpscaler) : 0);
            config.setGraphicsSupersamplingEnabled(cbDefaultSupersampling.isChecked());
            config.setGraphicsPostFXMode(spDefaultPostFX.getSelectedItemPosition());
            config.setGraphicsSharpness(sbDefaultSharpness.getValue() / 100f);
            config.setRendererPresentMode(PRESENT_MODE_IDS[spPresent.getSelectedItemPosition()]);
            config.setRendererDriverId(driverIds.get(spDriver.getSelectedItemPosition()));
            config.setRendererFilterMode(spFilter.getSelectedItemPosition());
            config.setRendererNative(cbNativeRendering.isChecked());
            config.setRendererNativeBackend(NATIVE_BACKEND_IDS[spNativeBackend.getSelectedItemPosition()]);
            config.setRendererSwapRB(cbSwapRB.isChecked());
            config.setRendererSfCompatMode(cbSfCompatMode.isChecked());
            config.setRendererLegacyScanout(cbLegacyScanout.isChecked());
        });
    }

    private static float sanitizeFlowScale(float value) {
        return Math.max(0.25f, Math.min(1.0f, value));
    }

    private static int getUpscalerSelection(int filterMode) {
        for (int i = 0; i < UPSCALER_FILTER_VALUES.length; i++) {
            if (UPSCALER_FILTER_VALUES[i] == filterMode) return i;
        }
        return 0;
    }

    private static int getSelectedUpscalerFilterMode(Spinner spinner) {
        int index = spinner != null ? spinner.getSelectedItemPosition() : 0;
        if (index < 0 || index >= UPSCALER_FILTER_VALUES.length) index = 0;
        return UPSCALER_FILTER_VALUES[index];
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
            case "immediate":     return 0;
            case "mailbox":       return 1;
            default:              return 2;
        }
    }
}
