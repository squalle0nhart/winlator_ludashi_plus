package com.winlator.cmod;

import static com.winlator.cmod.core.AppUtils.showToast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PictureInPictureParams;
import android.graphics.Rect;
import android.util.Rational;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.DebugDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.PerformanceControlDialog;
import com.winlator.cmod.contentdialog.WineD3DConfigDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.FrameGenManager;
import com.winlator.cmod.core.FrameGenQuickMenuHelper;
import com.winlator.cmod.core.LsfgVkManager;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.RuntimeBackendProbe;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.ThemeUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineRequestHandler;
import com.winlator.cmod.core.WineStartMenuCreator;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.midi.MidiHandler;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.renderer.ASurfaceRenderer;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.renderer.effects.CRTEffect;
import com.winlator.cmod.renderer.effects.ColorEffect;
import com.winlator.cmod.renderer.effects.FSREffect;
import com.winlator.cmod.renderer.effects.FXAAEffect;
import com.winlator.cmod.renderer.effects.HDREffect;
import com.winlator.cmod.renderer.effects.NTSCCombinedEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.widget.FrameRating;
import com.winlator.cmod.widget.SeekBar;
import com.winlator.cmod.widget.WinlatorHUD;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.LogView;
import com.winlator.cmod.widget.MagnifierView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.OnGetProcessInfoListener;
import com.winlator.cmod.winhandler.ProcessInfo;
import com.winlator.cmod.winhandler.TaskManagerSidebar;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.XEnvironment;
import com.winlator.cmod.xenvironment.components.ALSAServerComponent;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.xenvironment.components.PulseAudioComponent;
import com.winlator.cmod.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.cmod.xenvironment.components.XServerComponent;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.ScreenInfo;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends AppCompatActivity {
    private static final String WRAPPER_DEFAULT_BUNDLE_VERSION = "pipetto-e91223d-20260721";
    private static final String WRAPPER_GAMENATIVE_BUNDLE_VERSION = "20260724";
    private static final int[] VULKAN_UPSCALER_FILTER_VALUES = {2, 4, 5, 3};
    private static final String GRAPHICS_SIDEBAR_SCALING_MODE_KEY = "graphicsSidebarScalingMode";
    private static final int GRAPHICS_SCALING_NONE = 0;
    // Mode 1 used to be labelled Linear. A linear 2D texture sampler is bilinear,
    // so retain the saved value while using the technically accurate label.
    private static final int GRAPHICS_SCALING_BILINEAR = 1;
    private static final int GRAPHICS_SCALING_NEAREST = 2;
    private static final int GRAPHICS_SCALING_SGSR = 3;
    private static final int GRAPHICS_SCALING_FSR = 4;
    private static final int GRAPHICS_SCALING_FSR_FIT = 5;
    private static final int GRAPHICS_SCALING_DLS = 6;
    private static final int GRAPHICS_SCALING_NIS = 7;

    private static final boolean DISABLE_TOUCHSCREEN_AUTO_HIDE = true;

    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private ContainerManager containerManager;
    protected Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private ImageFs imageFs;
    private FrameRating classicHud = null;   
    private WinlatorHUD modernHud = null;     
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;
    private String graphicsDriverConfigData = Container.DEFAULT_GRAPHICSDRIVERCONFIG;
    private HashMap<String, String> graphicsDriverConfig;
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String emulator = Container.DEFAULT_EMULATOR;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private KeyValueSet dxwrapperConfig;
    private String startupSelection;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private boolean firstTimeBoot = false;
    private boolean forceGraphicsDriverExtraction = false;
    private SharedPreferences preferences;
    private OnExtractFileListener onExtractFileListener;
    private WinHandler winHandler;
    private TaskManagerSidebar taskManagerSidebar;
    private WineRequestHandler wineRequestHandler;
    private float globalCursorSpeed = 1.0f;
    private MagnifierView magnifierView;
    private boolean softStretchEnabled = false;
    private DebugDialog debugDialog;
    private short taskAffinityMask = 0;
    private short taskAffinityMaskWoW64 = 0;
    private final HashMap<Integer, Integer> bigCoreAffinitySnapshot = new HashMap<>();
    private String wineCpuTopologyValue = "";
    private int frameRatingWindowId = -1;
    private String activeFrameGenBackend;
    private int activeFrameGenMultiplier;
    private boolean performanceControlsReverted;

    private int activeRendererWindowId = -1;
    private String lastRendererName = null;
    private boolean cursorLock; 
    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private boolean navigationFocused = false;
    private MidiHandler midiHandler;
    private String midiSoundFont = "";
    private String lc_all = "";
    private String vkbasaltConfig = "";
    PreloaderDialog preloaderDialog = null;
    private Runnable configChangedCallback = null;
    private boolean isPaused = false;
    private boolean isRelativeMouseMovement = false;
    private boolean isMouseDisabled = false;
    private boolean simulateTouchScreen = false;

    private SensorManager sensorManager;

    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    private static final long SAVE_INTERVAL_MS = 1000;

    private String getLaunchGraphicsExtra(String key, String fallback) {
        if (shortcut != null) {
            return shortcut.getExtra(key, container != null ? container.getExtra(key, fallback) : fallback);
        }
        return container != null ? container.getExtra(key, fallback) : fallback;
    }

    private int resolveLaunchFullscreenMode() {
        String shortcutMode = shortcut != null ? shortcut.getExtra("fullscreenMode") : "";
        String legacyShortcutStretch = shortcut != null ? shortcut.getExtra("fullscreenStretched") : "";
        if (shortcut != null && shortcutMode != null && !shortcutMode.isEmpty()) {
            try {
                return Integer.parseInt(shortcutMode);
            } catch (NumberFormatException ignored) {
            }
        }
        if (shortcut != null && legacyShortcutStretch != null && !legacyShortcutStretch.isEmpty()) {
            return "1".equals(legacyShortcutStretch) ? Container.FULLSCREEN_STRETCH : Container.FULLSCREEN_OFF;
        }
        return container != null ? container.getFullscreenMode() : Container.FULLSCREEN_OFF;
    }

    private int getFullscreenModeLabelRes(int fullscreenMode) {
        switch (fullscreenMode) {
            case Container.FULLSCREEN_FIT:
                return R.string.fullscreen_mode_fit;
            case Container.FULLSCREEN_STRETCH:
                return R.string.fullscreen_mode_stretch;
            case Container.FULLSCREEN_FILL:
                return R.string.fullscreen_mode_fill;
            case Container.FULLSCREEN_INTEGER:
                return R.string.fullscreen_mode_integer;
            case Container.FULLSCREEN_OFF:
            default:
                return R.string.fullscreen_mode_off;
        }
    }

    private void updateFullscreenModeUi(int fullscreenMode) {
        TextView titleView = findViewById(R.id.TVToggleFullscreenTitle);
        TextView valueView = findViewById(R.id.TVToggleFullscreenValue);
        if (titleView != null) titleView.setText(R.string.fullscreen_mode);
        if (valueView != null) valueView.setText(getFullscreenModeLabelRes(fullscreenMode));
        setSelectedModeButton(R.id.BTFullscreenOff, fullscreenMode == Container.FULLSCREEN_OFF);
        setSelectedModeButton(R.id.BTFullscreenFit, fullscreenMode == Container.FULLSCREEN_FIT);
        setSelectedModeButton(R.id.BTFullscreenStretch, fullscreenMode == Container.FULLSCREEN_STRETCH);
        setSelectedModeButton(R.id.BTFullscreenFill, fullscreenMode == Container.FULLSCREEN_FILL);
        setSelectedModeButton(R.id.BTFullscreenInteger, fullscreenMode == Container.FULLSCREEN_INTEGER);
    }

    private void persistFullscreenModeSelection(int fullscreenMode) {
        if (shortcut != null) {
            shortcut.putExtra("fullscreenMode", String.valueOf(fullscreenMode));
            shortcut.putExtra("fullscreenStretched", null);
            shortcut.saveData();
        } else if (container != null) {
            container.setFullscreenMode(fullscreenMode);
            container.saveData();
        }
    }

    private void setRendererFullscreenMode(int fullscreenMode, boolean persist) {
        if (xServerView == null) return;
        HostRenderer rendererRef = xServerView.getRenderer();
        int previousMode = rendererRef.getFullscreenMode();
        rendererRef.setFullscreenMode(fullscreenMode);
        if (touchpadView != null && previousMode != fullscreenMode) {
            touchpadView.toggleFullscreen();
        }
        updateFullscreenModeUi(fullscreenMode);
        if (persist) persistFullscreenModeSelection(fullscreenMode);
    }

    private int getSavedUpscalerSelection(String savedFilter) {
        if (savedFilter == null || savedFilter.isEmpty()) return 0;
        try {
            int mode = Integer.parseInt(savedFilter);
            for (int i = 0; i < VULKAN_UPSCALER_FILTER_VALUES.length; i++) {
                if (VULKAN_UPSCALER_FILTER_VALUES[i] == mode) return i;
            }
            return 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int getSelectedUpscalerFilterMode(Spinner spinner) {
        int index = spinner != null ? spinner.getSelectedItemPosition() : 0;
        if (index < 0 || index >= VULKAN_UPSCALER_FILTER_VALUES.length) index = 0;
        return VULKAN_UPSCALER_FILTER_VALUES[index];
    }

    private void setSelectedModeButton(int viewId, boolean selected) {
        View view = findViewById(viewId);
        if (view != null) view.setSelected(selected);
    }

    private void setOnClickListenerIfPresent(int viewId, View.OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) view.setOnClickListener(listener);
    }

    private int getPersistentRendererFilterMode() {
        if (shortcut != null) return shortcut.getRendererFilterMode();
        if (container != null) return container.getRendererFilterMode();
        return 0;
    }

    private void setPersistentRendererFilterMode(int mode) {
        if (shortcut != null) {
            shortcut.setRendererFilterMode(mode);
        } else if (container != null) {
            container.setRendererFilterMode(mode);
        }
    }

    private void setPersistentRendererNative(boolean enabled) {
        if (shortcut != null) {
            shortcut.setRendererNative(enabled);
        } else if (container != null) {
            container.setRendererNative(enabled);
        }
    }

    private void saveLaunchGraphicsExtra(String key, String value) {
        if (shortcut != null) {
            shortcut.putExtra(key, value);
        } else if (container != null) {
            container.putExtra(key, value);
        }
    }

    private void persistLaunchGraphicsPreset() {
        if (shortcut != null) {
            shortcut.saveData();
        } else if (container != null) {
            container.saveData();
        }
    }

    private boolean getLaunchGraphicsBoolean(String key, boolean fallback) {
        String value = getLaunchGraphicsExtra(key, fallback ? "1" : "0");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private float getLaunchGraphicsSharpnessPercent() {
        String savedSharp = getLaunchGraphicsExtra("graphicsSharpness", "");
        if (savedSharp == null || savedSharp.isEmpty()) return 50f;
        try {
            float raw = Float.parseFloat(savedSharp);
            // Renderer defaults store 0.00..1.00 while the sidebar preset stores 0..100.
            if (raw <= 1.0f && savedSharp.contains(".")) raw *= 100f;
            return Math.max(0f, Math.min(100f, raw));
        } catch (NumberFormatException ignored) {
            return 50f;
        }
    }

    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    private boolean isDarkMode;

    private String screenEffectProfile;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    private EnvVars overrideEnvVars;

    private void createNotifcationChannel() {
        String name = "Winlator";
        String description = "Winlator XServer Messages";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (configChangedCallback != null) {
            configChangedCallback.run();
            configChangedCallback = null;
        }
    }

    private float pickHighestRefreshRate() {
        android.view.Display display = getWindowManager().getDefaultDisplay();
        android.view.Display.Mode[] modes = display.getSupportedModes();

        float maxRefresh = 0f;

        for (android.view.Display.Mode mode : modes) {
            if (mode.getRefreshRate() > maxRefresh)
                maxRefresh = mode.getRefreshRate();
        }

        Log.d("XServerDisplayActivity", "Picking refresh rate " + maxRefresh);

        return maxRefresh;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeUtils.getFullscreenThemeResId(this));
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);

        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = pickHighestRefreshRate();
        getWindow().setAttributes(params);

        setContentView(R.layout.xserver_display_activity);

        preloaderDialog = new PreloaderDialog(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        cursorLock = preferences.getBoolean("cursor_lock", true);

        isDarkMode = preferences.getBoolean("dark_mode", false);

        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        boolean xinputDisabledFromShortcut = false;

        startTime = System.currentTimeMillis();

        handler = new Handler(Looper.getMainLooper());
        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);

        hideControlsRunnable = () -> {
            if (DISABLE_TOUCHSCREEN_AUTO_HIDE) {
                return;
            }

            if (preferences.getBoolean("touchscreen_timeout_enabled", false)
                    && inputControlsView != null
                    && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.GONE);
                Log.d("XServerDisplayActivity", "Touchscreen controls hidden after timeout.");
            }
        };

        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener(
                (view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                openSidebarPanel(activeSidebarItemId, activeSidebarPanelId);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                hideAllSidebarPanels();
            }
        });

        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false)
                || preferences.getBoolean("enable_box64_logs", false);

        wireSidebarListeners(enableLogs);

        imageFs = ImageFs.find(this);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {
            for (int i = 0; i < 4; i++) {
                File eventFile = new File(devInputDir, "event" + i);
                if (eventFile.exists())
                    eventFile.delete();
            }
        }

        winHandler = new WinHandler(this);
        winHandler.setFakeInputPath(devInputDir.getAbsolutePath());

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));

        String shortcutPath = getIntent().getStringExtra("shortcut_path");
        Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);

        int containerId = getIntent().getIntExtra("container_id", 0);
        Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);
        if (containerId == 0) {
            Log.d("XServerDisplayActivity", "Container ID is 0, attempting to parse from .desktop file");

        }

        if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {
            File shortcutFile = new File(shortcutPath);
            containerId = parseContainerIdFromDesktopFile(shortcutFile);
            Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);
        }

        playtimePrefs = getSharedPreferences("playtime_stats", MODE_PRIVATE);
        shortcutName = getIntent().getStringExtra("shortcut_name");

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            if (shortcutName == null || shortcutName.isEmpty()) {
                shortcutName = parseShortcutNameFromDesktopFile(new File(shortcutPath));
                Log.d("XServerDisplayActivity", "Parsed Shortcut Name from .desktop file: " + shortcutName);
            }
        } else {
            Log.d("XServerDisplayActivity", "No shortcut path provided, skipping shortcut parsing.");
        }

        incrementPlayCount();

        Log.d("XServerDisplayActivity", "Final Container ID: " + containerId);

        container = containerManager.getContainerById(containerId);

        if (container == null) {
            Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
            finish(); 
            return;
        }

        containerManager.activateContainer(container);

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            shortcut = new Shortcut(container, new File(shortcutPath));
        }

        boolean sustainedPerformance = resolvedPerformanceBoolean(
                com.winlator.cmod.perf.PerformanceSettings.KEY_SUSTAINED);
        boolean preferBigCores = resolvedPerformanceBoolean(
                com.winlator.cmod.perf.PerformanceSettings.KEY_BIG_CORES);
        getWindow().setSustainedPerformanceMode(sustainedPerformance);
        com.winlator.cmod.perf.TempWatchdog.INSTANCE.start(this);

        taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));

        String affinityCpuList = container.getCPUList(true);

        if (shortcut != null) {
            affinityCpuList = shortcut.getExtra("cpuList", container.getCPUList(true));
            taskAffinityMask = (short) ProcessHelper.getAffinityMask(affinityCpuList);
            taskAffinityMaskWoW64 = taskAffinityMask;
        }

        if (preferBigCores) {
            String bigCoreList = com.winlator.cmod.perf.CpuTopology.INSTANCE.detectBigCoreCpuList();
            if (bigCoreList != null && !bigCoreList.isEmpty()) {
                affinityCpuList = bigCoreList;
                taskAffinityMask = (short) ProcessHelper.getAffinityMask(bigCoreList);
                taskAffinityMaskWoW64 = taskAffinityMask;
            }
        }

        if (com.winlator.cmod.perf.RootManager.INSTANCE.isGranted()) {
            applyEffectiveRootPerformance();
        } else {
            handler.postDelayed(this::applyEffectiveRootPerformance, 3000);
        }

        boolean syncCpuTopology = shortcut != null
                ? shortcut.getExtra("syncCpuTopology", container.isSyncCpuTopology() ? "1" : "").equals("1")
                : container.isSyncCpuTopology();

        wineCpuTopologyValue = "";
        if (syncCpuTopology && affinityCpuList != null && !affinityCpuList.isEmpty()) {
            int coreCount = affinityCpuList.split(",").length;
            wineCpuTopologyValue = coreCount + ":" + affinityCpuList;
        }

        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("imgVersion").isEmpty();

        String wineVersion = container.getWineVersion();
        wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);

        imageFs.setWinePath(wineInfo.path);

        ProcessHelper.removeAllDebugCallbacks();
        if (enableLogs) {
            LogView.setFilename(getExecutable());
            ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        }

        graphicsDriver = container.getGraphicsDriver();
        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        audioDriver = container.getAudioDriver();
        emulator = container.getEmulator();
        midiSoundFont = container.getMIDISoundFont();
        dxwrapper = container.getDXWrapper();
        String dxwrapperConfig = container.getDXWrapperConfig();
        screenSize = container.getScreenSize();
        winHandler.setInputType((byte) container.getInputType());
        lc_all = container.getLC_ALL();

        Intent intent = getIntent();
        Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());

        if (shortcut != null) {
            graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            screenSize = shortcut.getExtra("screenSize", container.getScreenSize());
            lc_all = shortcut.getExtra("lc_all", container.getLC_ALL());
            String inputType = shortcut.getExtra("inputType");
            if (!inputType.isEmpty())
                winHandler.setInputType(Byte.parseByte(inputType));
            String xinputDisabledString = shortcut.getExtra("disableXinput", "false");
            xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);

            winHandler.setXInputDisabled(xinputDisabledFromShortcut);
            String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");
            if (!sharpnessEffect.equals("None")) {
                double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));
                double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));
                vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness="
                        + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100 + ";" + "dlsDenoise="
                        + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";
            }
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);

            simulateTouchScreen = shortcut.getExtra("simTouchScreen").equals("1");
        }

        this.graphicsDriverConfigData = graphicsDriverConfig;
        this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

        if (!wineInfo.isWin64()) {
            onExtractFileListener = (file, size) -> {
                String path = file.getPath();
                if (path.contains("system32/"))
                    return null;
                return new File(path.replace("syswow64/", "system32/"));
            };
        }

        boolean removeLoadingBarWhenBootingGames = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("remove_loading_bar_when_booting_games", false);
        if (!removeLoadingBarWhenBootingGames) preloaderDialog.show(R.string.starting_up);

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);

        boolean[] winStarted = { false };

        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    if (!simulateTouchScreen) {
                        xServerView.getRenderer().setCursorVisible(true);
                    }
                    preloaderDialog.closeOnUiThread();
                    winStarted[0] = true;
                }

                if (frameRatingWindowId == window.id) {
                    if (classicHud != null) classicHud.update();
                    if (modernHud != null) modernHud.onFrame();
                } else if (frameRatingWindowId == -1 && window.isApplicationWindow()
                        && ((modernHud != null && modernHud.isUserEnabled())
                         || (classicHud != null && classicHud.isUserEnabled()))) {

                    frameRatingWindowId = window.id;
                    activeRendererWindowId = window.id;
                    if (xServerView != null) xServerView.getRenderer().setFpsWindowId(window.id);
                    if (classicHud != null) classicHud.update();
                    if (modernHud != null) modernHud.onFrame();
                }
            }

            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                changeFrameRatingVisibility(window, property);
            }

            @Override
            public void onDestroyWindow(Window window) {
                changeFrameRatingVisibility(window, null);
            }
        });

        if (!midiSoundFont.equals("")) {
            try {
                final InputStream in;
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    in = getAssets().open(MidiManager.SF2_ASSETS_DIR + "/" + midiSoundFont);
                } else {
                    in = null;
                }
                MidiManager.OnMidiLoadedCallback callback = new MidiManager.OnMidiLoadedCallback() {
                    @Override
                    public void onSuccess(SF2Soundbank soundbank) {
                        midiHandler = new MidiHandler();
                        midiHandler.setSoundBank(soundbank);
                        midiHandler.start();
                    }

                    @Override
                    public void onFailed(Exception e) {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Exception e2) {
                            }
                        }
                    }
                };
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    MidiManager.load(in, callback);
                } else {
                    MidiManager.load(new File(MidiManager.getSoundFontDir(this), midiSoundFont), callback);
                }
            } catch (Exception e) {
            }
        }

        String controlsProfile = shortcut != null ? shortcut.getExtra("controlsProfile", "") : "";

        Runnable runnable = () -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                setupWineSystemFiles();
                extractGraphicsDriverFiles();
                changeWineAudioDriver();
                CountDownLatch uiReady = new CountDownLatch(1);
                runOnUiThread(() -> {
                    setupUI();
                    setupSidebarInputControls();
                    if (controlsProfile.isEmpty()) {
                        simulateConfirmInputControlsDialog();
                    }
                    uiReady.countDown();
                });
                try {
                    uiReady.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    setupXEnvironment();
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        };

        if (xServer.screenInfo.height > xServer.screenInfo.width) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            configChangedCallback = runnable;
        } else
            runnable.run();
    }

    private int parseContainerIdFromDesktopFile(File desktopFile) {
        int containerId = 0;
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        containerId = Integer.parseInt(line.split(":")[1].trim());
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);
            }
        }
        return containerId;
    }

    private boolean parseBoolean(String value) {

        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }

        return false;
    }

    private void handleCapturedPointer(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS: {
                int button = event.getActionButton();
                if (button == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                } else if (button == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                } else if (button == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.MIDDLEDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE);
                }
                break;
            }
            case MotionEvent.ACTION_BUTTON_RELEASE: {
                int button = event.getActionButton();
                if (button == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                } else if (button == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                } else if (button == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.MIDDLEUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE: {
                float[] p = XForm.transformPoint(xform, event.getX(), event.getY());
                int dx = (int) p[0];
                int dy = (int) p[1];
                if (xServer.isRelativeMouseMovement())
                    winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0);
                else
                    xServer.injectPointerMoveDelta(dx, dy);
                break;
            }
            case MotionEvent.ACTION_SCROLL: {
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY <= -1.0f) {
                    if (xServer.isRelativeMouseMovement()) {
                        winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int) scrollY * 270);
                    } else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN);
                    }
                } else if (scrollY >= 1.0f) {
                    if (xServer.isRelativeMouseMovement()) {
                        winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int) scrollY * 270);
                    } else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP);
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
            com.winlator.cmod.perf.TempWatchdog.INSTANCE.start(this);
            applyEffectiveRootPerformance();
        }
        startTime = System.currentTimeMillis();
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
        if (!isInPictureInPictureMode())
            ProcessHelper.resumeAllWineProcesses();
    }

    @Override
    public void onPause() {
        if (taskManagerSidebar != null) taskManagerSidebar.stop();
        super.onPause();

        if (!isInPictureInPictureMode()) {
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }

            ProcessHelper.pauseAllWineProcesses();
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        int w = xServer.screenInfo.width;
        int h = xServer.screenInfo.height;
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(w, h));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && xServerView != null) {
            int[] loc = new int[2];
            xServerView.getLocationOnScreen(loc);
            Rect hint = new Rect(loc[0], loc[1],
                    loc[0] + xServerView.getWidth(), loc[1] + xServerView.getHeight());
            builder.setSourceRectHint(hint);
        }
        enterPictureInPictureMode(builder.build());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (xServerView == null) return;
        xServerView.getRenderer().setPipMode(isInPictureInPictureMode);
        if (!isInPictureInPictureMode) {
            xServerView.post(() -> {
                if (xServerView == null) return;
                int w = xServerView.getWidth();
                int h = xServerView.getHeight();
                if (w > 0 && h > 0) xServerView.getRenderer().onHostSurfaceChanged(w, h);
            });
        }
    }

    private void savePlaytimeData() {
        long endTime = System.currentTimeMillis();
        long playtime = endTime - startTime;

        if (playtime < 0) {
            playtime = 0;
        }

        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playtimeKey = shortcutName + "_playtime";

        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;
        editor.putLong(playtimeKey, totalPlaytime);
        editor.apply();

        startTime = System.currentTimeMillis();
    }

    private void incrementPlayCount() {
        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playCountKey = shortcutName + "_play_count";
        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;
        editor.putInt(playCountKey, playCount);
        editor.apply();
    }

    private void exit() {
        stopAndRevertPerformanceControls();
        boolean removeLoadingBar = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("remove_loading_bar_when_booting_games", false);
        if (!removeLoadingBar) preloaderDialog.showOnUiThread(R.string.shutdown);

        if (xServerView != null) {
            xServerView.getRenderer().forceCleanup();
            xServerView.setVisibility(View.GONE);
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);

        if (midiHandler != null)
            midiHandler.stop();

        if (environment != null)
            environment.stopEnvironmentComponents();
        if (winHandler != null)
            winHandler.stop();
        if (wineRequestHandler != null)
            wineRequestHandler.stop();

        Executors.newSingleThreadExecutor().execute(() -> {

            ProcessHelper.terminateAllWineProcesses();

            long start = System.currentTimeMillis();
            while (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed >= 1500) {

                    for (String pid : ProcessHelper.listRunningWineProcesses()) {
                        ProcessHelper.killProcess(Integer.parseInt(pid));
                    }
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            preloaderDialog.closeOnUiThread();
            AppUtils.restartApplication(getApplicationContext());
        });
    }

    @Override
    protected void onDestroy() {
        stopAndRevertPerformanceControls();
        if (taskManagerSidebar != null) taskManagerSidebar.stop();
        if (handler != null) {
            handler.removeCallbacks(savePlaytimeRunnable);
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else
                drawerLayout.closeDrawers();
        }
    }

    private void showVibrationDialog() {
        if (winHandler == null)
            return;

        Context context = this;
        int maxControllers = winHandler.getMaxControllers();
        boolean[] checkedItems = new boolean[maxControllers];
        String[] items = new String[maxControllers];

        for (int i = 0; i < maxControllers; i++) {
            items[i] = getString(R.string.vibration_slot, i + 1);
            checkedItems[i] = winHandler.isVibrationEnabledForSlot(i);
        }

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.vibration)
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    winHandler.setVibrationEnabledForSlot(which, isChecked);
                })
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && cursorLock)
            touchpadView.requestPointerCapture();
        else if (!hasFocus)
            touchpadView.releasePointerCapture();
    }

    private void setupWineSystemFiles() {
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        if (!container.getExtra("imgVersion").equals(imgVersion)) {
            applyGeneralPatches(container);
            container.putExtra("imgVersion", imgVersion);
            containerDataChanged = true;
        }

        String dxwrapper = this.dxwrapper;
        String graphicsDriverState = graphicsDriver + ";" + graphicsDriverConfigData;
        String graphicsDriverArchive = resolveGraphicsDriverArchiveName();
        if ("wrapper".equals(graphicsDriverArchive)) {
            graphicsDriverState += ";bundle=" + WRAPPER_DEFAULT_BUNDLE_VERSION;
        } else if ("wrapper-gamenative".equals(graphicsDriverArchive)) {
            // Include the bundled wrapper revision in the extraction state so existing
            // containers replace an older libvulkan_wrapper.so after an app update.
            graphicsDriverState += ";bundle=" + WRAPPER_GAMENATIVE_BUNDLE_VERSION;
        }

        forceGraphicsDriverExtraction = !graphicsDriverState.equals(container.getExtra("graphicsDriver"));
        if (forceGraphicsDriverExtraction) {
            container.putExtra("graphicsDriver", graphicsDriverState);
            containerDataChanged = true;
        }

        if (dxwrapper.contains("dxvk")) {
            String dxvkWrapper = "dxvk-" + dxwrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + dxwrapperConfig.get("vkd3dVersion");
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper;
        } else if (dxwrapper.contains("vegas")) {
            String vegasVersion = dxwrapperConfig.get("version");
            if (vegasVersion == null || vegasVersion.isEmpty()) {
                vegasVersion = DefaultVersion.getVegasDefault();
            }
            String vkd3dWrapper = dxwrapper.contains("+vkd3d")
                    ? "vkd3d-" + dxwrapperConfig.get("vkd3dVersion")
                    : "None";
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            dxwrapper = "vegas-" + vegasVersion + ";" + vkd3dWrapper + ";" + ddrawrapper;
        }

        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {
            extractDXWrapperFiles(dxwrapper);
            container.putExtra("dxwrapper", dxwrapper);
            containerDataChanged = true;
        }

        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents())
                : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme + "," + xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme + "," + xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container);

        int inputType = container.getInputType();
        if (shortcut != null) {
            String shortcutInputType = shortcut.getExtra("inputType");
            if (!shortcutInputType.isEmpty()) {
                inputType = Byte.parseByte(shortcutInputType);
            }
        }
        boolean dinputEnabled = (inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT;

        boolean exclusiveXInput = container.isExclusiveXInput();
        if (shortcut != null) {
            String extra = shortcut.getExtra("exclusiveXInput");
            if (!extra.isEmpty())
                exclusiveXInput = extra.equals("1");
        }

        WineUtils.setJoystickRegistryKeys(container, dinputEnabled, exclusiveXInput);

        if (shortcut != null)
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));
        else
            startupSelection = String.valueOf(container.getStartupSelection());

        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, startupSelection);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }
        if (containerDataChanged)
            container.saveData();
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        envVars.put("LC_ALL", lc_all);
        envVars.put("WINEPREFIX", imageFs.wineprefix);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels",
                SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty()
                ? "+" + wineDebugChannels.replace(",", ",+")
                : "-all");

        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());

        guestProgramLauncherComponent = new GuestProgramLauncherComponent(
                contentsManager,
                contentsManager.getProfileByEntryName(container.getWineVersion()),
                shortcut);

        if (container != null) {
            if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {

            }
            guestProgramLauncherComponent.setContainer(this.container);
            guestProgramLauncherComponent.setWineInfo(this.wineInfo);

            String guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();

            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());

            if (shortcut != null)
                envVars.putAll(shortcut.getExtra("envVars"));

            if (!wineCpuTopologyValue.isEmpty()) {
                envVars.put("WINE_CPU_TOPOLOGY", wineCpuTopologyValue);
            }

            if (!envVars.has("WINEESYNC")) {
                envVars.put("WINEESYNC", "1");
            }

            ArrayList<String> bindingPaths = new ArrayList<>();
            for (String[] drive : container.drivesIterator()) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            guestProgramLauncherComponent.setBox64Preset(
                    shortcut != null
                            ? shortcut.getExtra("box64Preset", container.getBox64Preset())
                            : container.getBox64Preset());

            guestProgramLauncherComponent.setFEXCorePreset(
                    shortcut != null
                            ? shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())
                            : container.getFEXCorePreset());
        }

        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars.clear(); 
        }

        environment = new XEnvironment(this, imageFs);
        environment.addComponent(
                new SysVSharedMemoryComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(
                new XServerComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)));

        if (audioDriver.equals("alsa")) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", "true");
            environment.addComponent(
                    new ALSAServerComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)));
        } else if (audioDriver.equals("pulseaudio")) {
            envVars.put("PULSE_SERVER", rootPath + UnixSocketConfig.PULSE_SERVER_PATH);
            environment.addComponent(
                    new PulseAudioComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)));
        }

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> runOnUiThread(this::exit));

        environment.addComponent(guestProgramLauncherComponent);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {

        }

        environment.startEnvironmentComponents();

        if (resolvedPerformanceBoolean(com.winlator.cmod.perf.PerformanceSettings.KEY_PRIORITY)) {
            handler.postDelayed(
                    () -> com.winlator.cmod.perf.PerfPriority.INSTANCE.boost(
                            GuestProgramLauncherComponent.getPid()),
                    5000);
        }

        winHandler.start();

        if (wineRequestHandler != null)
            wineRequestHandler.start();

        dxwrapperConfig = null;

    }

    private void createWrapperScript(String path, String content) {
        File scriptFile = new File(path);
        FileUtils.writeString(scriptFile, content);
        scriptFile.setExecutable(true);
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        String rendererType = shortcut != null ? shortcut.getRenderer()
                : (container != null ? container.getRenderer() : "vulkan");
        if ("surfaceflinger".equalsIgnoreCase(rendererType) && !ASurfaceRenderer.isSupported()) {
            rendererType = "vulkan";
        }
        xServerView.initRenderer(rendererType);
        final HostRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);
        final String rendererLabel = "gl".equalsIgnoreCase(rendererType) ? "OpenGL"
                : "surfaceflinger".equalsIgnoreCase(rendererType) ? "SurfaceFlinger" : "Vulkan";

        if (renderer instanceof VulkanRenderer) {
            VulkanRenderer vkRenderer = (VulkanRenderer) renderer;
            String rendererDriverId = shortcut != null ? shortcut.getRendererDriverId()
                    : (container != null ? container.getRendererDriverId() : "");
            if (rendererDriverId == null || rendererDriverId.isEmpty()) {
                rendererDriverId = graphicsDriverConfig != null ? graphicsDriverConfig.get("version") : null;
            }
            if (rendererDriverId != null && !rendererDriverId.isEmpty() && !rendererDriverId.equalsIgnoreCase("system")) {
                try {
                    String driverPath = getFilesDir().getAbsolutePath() + "/contents/adrenotools/" + rendererDriverId + "/";
                    AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
                    String libraryName = adrenotoolsManager.getLibraryName(rendererDriverId);
                    String nativeLibDir = AppUtils.getNativeLibDir(this);
                    if (!libraryName.isEmpty()) vkRenderer.setDriverInfo(driverPath, libraryName, nativeLibDir);
                } catch (Exception ignored) {
                }
            }

            String presentMode = shortcut != null ? shortcut.getRendererPresentMode()
                    : (container != null ? container.getRendererPresentMode() : "fifo");
            vkRenderer.setVkPresentMode(com.winlator.cmod.contentdialog.RendererOptionsDialog.toVkPresentMode(presentMode));
            vkRenderer.setFilterMode(shortcut != null ? shortcut.getRendererFilterMode()
                    : (container != null ? container.getRendererFilterMode() : 0));
            vkRenderer.setSwapRB(shortcut != null ? shortcut.getRendererSwapRB()
                    : (container != null && container.getRendererSwapRB()));
            softStretchEnabled = getLaunchGraphicsBoolean("graphicsStretchMode", false);
            vkRenderer.setStretchMode(softStretchEnabled ? 1 : 0);
        } else if (renderer instanceof GLRenderer) {
            GLRenderer glRenderer = (GLRenderer) renderer;
            int rendererFilterMode = shortcut != null ? shortcut.getRendererFilterMode()
                    : (container != null ? container.getRendererFilterMode() : 0);
            // Renderer settings use 0=Bilinear, 1=Nearest. GLRenderer's runtime API uses
            // 0=Linear and 2=Nearest because mode 2 is also its effect/upscaler path.
            glRenderer.setFilterMode(rendererFilterMode == 1 ? 2 : 0);
            glRenderer.setSwapRB(shortcut != null ? shortcut.getRendererSwapRB()
                    : (container != null && container.getRendererSwapRB()));
            boolean nativeMode = shortcut != null ? shortcut.getRendererNative()
                    : (container != null && container.isRendererNative());
            glRenderer.setInitialNativeMode(nativeMode);
        } else if (renderer instanceof ASurfaceRenderer) {
            ASurfaceRenderer asrRenderer = (ASurfaceRenderer) renderer;
            asrRenderer.setSfCompatMode(shortcut != null ? shortcut.getRendererSfCompatMode()
                    : (container == null || container.getRendererSfCompatMode()));
            asrRenderer.setDirectRgbaGameFrames(isDefaultWrapperDirectRgbaMode());
            String savedGraphicsFilter = getLaunchGraphicsExtra("graphicsFilterMode", "0");
            try {
                asrRenderer.setFilterMode(Integer.parseInt(savedGraphicsFilter));
            } catch (NumberFormatException ignored) {
                asrRenderer.setFilterMode(0);
            }
            asrRenderer.setSharpness(getLaunchGraphicsSharpnessPercent() / 100f);
        }

        if (shortcut != null) {
            renderer.setUnviewableWMClasses("explorer.exe");
        }

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        touchpadView = new TouchpadView(this, xServer, timeoutHandler, hideControlsRunnable);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMouseEnabled(!isMouseDisabled);
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.openDrawer(GravityCompat.START);
        });
        View.OnCapturedPointerListener capturedPointerListener = new View.OnCapturedPointerListener() {
            @Override
            public boolean onCapturedPointer(View view, MotionEvent event) {
                handleCapturedPointer(event);
                return true;
            }
        };
        touchpadView.setOnCapturedPointerListener(cursorLock ? capturedPointerListener : null);
        touchpadView.setFocusable(true);
        touchpadView.setFocusableInTouchMode(true);
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this, timeoutHandler, hideControlsRunnable);
        inputControlsView
                .setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (isTimeoutEnabled) {
            startTouchscreenTimeout();
        }

        if (container != null) {
            String hudModeExtra = container.getExtra("hudMode");
            int hudMode = !hudModeExtra.isEmpty()
                    ? Integer.parseInt(hudModeExtra)
                    : (container.isShowFPS() ? 1 : 0);

            if (hudMode == 1) {

                classicHud = new FrameRating(this, graphicsDriverConfig);
                classicHud.setVisibility(View.GONE);
                rootView.addView(classicHud);
                renderer.setFrameRating(classicHud);
                classicHud.setRenderer(rendererLabel);
                classicHud.enableByUser();
            } else if (hudMode == 2) {

                modernHud = new WinlatorHUD(this);
                modernHud.setVisibility(View.GONE);
                rootView.addView(modernHud);
                modernHud.enableByUser();
                renderer.setFrameRating(modernHud);
                modernHud.setRenderer(rendererLabel);
            }
        }

        setRendererFullscreenMode(resolveLaunchFullscreenMode(), false);

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null)
                    showInputControls(profile);
            }

            String simTouchScreen = shortcut.getExtra("simTouchScreen");
            touchpadView.setSimTouchScreen(simTouchScreen.equals("1"));
            if (simulateTouchScreen) {
                renderer.setCursorVisible(false);
            }
        }

        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);

        setupSidebarHudControls();
        setupSidebarGraphicsControls();
        setupRuntimeBackendIndicator();
    }

    private void setupRuntimeBackendIndicator() {
        TextView indicator = findViewById(R.id.TVRuntimeBackend);
        if (indicator == null || wineInfo == null) return;

        boolean arm64ec = wineInfo.isArm64EC();
        String arch = arm64ec ? "arm64ec" : "x86-64";
        String normalizedEmulator = emulator == null ? "" : emulator.toLowerCase(java.util.Locale.ROOT);
        String translator = !arm64ec ? "Box64"
                : normalizedEmulator.contains("wowbox64") ? "wowbox64" : "FEXCore";
        String baseLabel = arch + " · " + translator;

        indicator.setText(baseLabel + (arm64ec ? " · N/A" : ""));
        indicator.setVisibility(View.VISIBLE);
        if (!arm64ec) return;

        new Thread(() -> {
            RuntimeBackendProbe.FexMode mode = RuntimeBackendProbe.FexMode.NA;
            for (int i = 0; i < 15 && mode == RuntimeBackendProbe.FexMode.NA; i++) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                mode = RuntimeBackendProbe.detect(GuestProgramLauncherComponent.getPid());
            }

            RuntimeBackendProbe.FexMode detectedMode = mode;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                String modeLabel = detectedMode == RuntimeBackendProbe.FexMode.UNIXLIB
                        ? "unixlib" : detectedMode == RuntimeBackendProbe.FexMode.DLL ? "DLL" : "N/A";
                indicator.setText(baseLabel + " · " + modeLabel);
                indicator.setTextColor(detectedMode == RuntimeBackendProbe.FexMode.UNIXLIB
                        ? android.graphics.Color.rgb(76, 175, 80)
                        : ThemeUtils.getColorAttr(this, R.attr.colorOnSurfaceVariant));
            });
        }, "fex-runtime-probe").start();
    }

    private void applyScreenEffects(GLRenderer renderer, float brightness, float contrast, float gamma,
            boolean fxaaEn, boolean crtEn, boolean toonEn, boolean ntscEn) {
        ColorEffect ce = (ColorEffect) renderer.getEffectComposer().getEffect(ColorEffect.class);
        if (brightness == 0 && contrast == 0 && gamma == 1.0f) {
            if (ce != null) renderer.getEffectComposer().removeEffect(ce);
        } else {
            if (ce == null) ce = new ColorEffect();
            ce.setBrightness(brightness / 100f);
            ce.setContrast(contrast / 100f);
            ce.setGamma(gamma);
            renderer.getEffectComposer().addEffect(ce);
        }

        FXAAEffect fxaa = (FXAAEffect) renderer.getEffectComposer().getEffect(FXAAEffect.class);
        if (fxaaEn) {
            if (fxaa == null) renderer.getEffectComposer().addEffect(new FXAAEffect());
        } else if (fxaa != null) {
            renderer.getEffectComposer().removeEffect(fxaa);
        }

        CRTEffect crt = (CRTEffect) renderer.getEffectComposer().getEffect(CRTEffect.class);
        if (crtEn) {
            if (crt == null) renderer.getEffectComposer().addEffect(new CRTEffect());
        } else if (crt != null) {
            renderer.getEffectComposer().removeEffect(crt);
        }

        ToonEffect toon = (ToonEffect) renderer.getEffectComposer().getEffect(ToonEffect.class);
        if (toonEn) {
            if (toon == null) renderer.getEffectComposer().addEffect(new ToonEffect());
        } else if (toon != null) {
            renderer.getEffectComposer().removeEffect(toon);
        }

        NTSCCombinedEffect ntsc = (NTSCCombinedEffect) renderer.getEffectComposer().getEffect(NTSCCombinedEffect.class);
        if (ntscEn) {
            if (ntsc == null) renderer.getEffectComposer().addEffect(new NTSCCombinedEffect());
        } else if (ntsc != null) {
            renderer.getEffectComposer().removeEffect(ntsc);
        }
    }

    private void saveScreenEffectProfile(String name, float brightness, float contrast, float gamma,
            boolean fxaa, boolean crt, boolean toon, boolean ntsc) {
        KeyValueSet settings = new KeyValueSet();
        settings.put("brightness", brightness);
        settings.put("contrast", contrast);
        settings.put("gamma", gamma);
        settings.put("fxaa", fxaa);
        settings.put("crt_shader", crt);
        settings.put("toon_shader", toon);
        settings.put("ntsc_effect", ntsc);

        java.util.Set<String> oldProfiles = new java.util.LinkedHashSet<>(
                preferences.getStringSet("screen_effect_profiles", new java.util.LinkedHashSet<>()));
        java.util.Set<String> newProfiles = new java.util.LinkedHashSet<>();
        for (String p : oldProfiles) {
            String n = p.split(":")[0];
            newProfiles.add(n.equals(name) ? name + ":" + settings.toString() : p);
        }
        preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply();
    }

    private ActivityResultLauncher<Intent> controlsEditorActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (editInputControlsCallback != null) {
                    editInputControlsCallback.run();
                    editInputControlsCallback = null;
                }
            });

    private String parseShortcutNameFromDesktopFile(File desktopFile) {
        String shortcutName = "";
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Name=")) {
                        shortcutName = line.split("=")[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("XServerDisplayActivity", "Error reading shortcut name from .desktop file", e);
            }
        }
        return shortcutName;
    }

    private void setTextColorForDialog(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {

                setTextColorForDialog((ViewGroup) child, color);
            } else if (child instanceof TextView) {

                ((TextView) child).setTextColor(color);
            }
        }
    }

    private void wireSidebarListeners(boolean enableLogs) {
        applySidebarSpinnerTheme();

        View btItemLogs = findViewById(R.id.BTItemLogs);
        if (btItemLogs != null)
            btItemLogs.setVisibility(enableLogs ? View.VISIBLE : View.GONE);

        if (XrActivity.isEnabled(this)) {
            View btItemMagnifier = findViewById(R.id.BTItemMagnifier);
            if (btItemMagnifier != null)
                btItemMagnifier.setVisibility(View.GONE);
        }

        toggleOnClick(R.id.BTItemInput, R.id.LLSubInput);
        toggleOnClick(R.id.BTItemMouse, R.id.LLSubMouse);
        toggleOnClick(R.id.BTItemFPS, R.id.LLSubFPS);
        toggleOnClick(R.id.BTItemGraphics, R.id.LLSubGraphics);
        toggleOnClick(R.id.BTItemScreen, R.id.LLSubScreen);
        openSidebarPanel(R.id.BTItemFPS, R.id.LLSubFPS);

        ViewGroup btItemPause = (ViewGroup) findViewById(R.id.BTItemPause);
        if (btItemPause != null) {
            ImageView pauseIcon = (ImageView) btItemPause.getChildAt(0);
            btItemPause.setOnClickListener(v -> {
                if (isPaused) {
                    ProcessHelper.resumeAllWineProcesses();
                    if (pauseIcon != null) pauseIcon.setImageResource(R.drawable.icon_pause);
                } else {
                    ProcessHelper.pauseAllWineProcesses();
                    if (pauseIcon != null) pauseIcon.setImageResource(R.drawable.icon_play);
                }
                isPaused = !isPaused;
                drawerLayout.closeDrawers();
            });
        }

        View btSubKeyboard = findViewById(R.id.BTSubKeyboard);
        if (btSubKeyboard != null) {
            btSubKeyboard.setOnClickListener(v -> {
                AppUtils.showKeyboard(this);
                drawerLayout.closeDrawers();
            });
        }

        setupSidebarInputControls();

        View btSubVibration = findViewById(R.id.BTSubVibration);
        if (btSubVibration != null) {
            btSubVibration.setOnClickListener(v -> {
                showVibrationDialog();
                drawerLayout.closeDrawers();
            });
        }

        Switch swRelativeMouse = findViewById(R.id.SWRelativeMouse);
        if (swRelativeMouse != null) {
            swRelativeMouse.setChecked(isRelativeMouseMovement);
            swRelativeMouse.setOnCheckedChangeListener((btn, checked) -> {
                isRelativeMouseMovement = checked;
                if (xServer != null)
                    xServer.setRelativeMouseMovement(isRelativeMouseMovement);
            });
        }

        Switch swDisableMouse = findViewById(R.id.SWDisableMouse);
        if (swDisableMouse != null) {
            swDisableMouse.setChecked(isMouseDisabled);
            swDisableMouse.setOnCheckedChangeListener((btn, checked) -> {
                isMouseDisabled = checked;
                if (touchpadView != null)
                    touchpadView.setMouseEnabled(!isMouseDisabled);
            });
        }

        View btItemPipMode = findViewById(R.id.BTItemPipMode);
        if (btItemPipMode != null) {
            btItemPipMode.setOnClickListener(v -> {
                enterPipMode();
                drawerLayout.closeDrawers();
            });
        }

        View btItemToggleFullscreen = findViewById(R.id.BTItemToggleFullscreen);
        if (btItemToggleFullscreen != null) {
            btItemToggleFullscreen.setOnClickListener(v -> {
                if (xServerView != null) {
                    HostRenderer rendererRef = xServerView.getRenderer();
                    int nextFullscreenMode = Container.nextFullscreenMode(rendererRef.getFullscreenMode());
                    setRendererFullscreenMode(nextFullscreenMode, true);
                }
                drawerLayout.closeDrawers();
            });
        }

        View btItemMagnifier = findViewById(R.id.BTItemMagnifier);
        if (btItemMagnifier != null) {
            btItemMagnifier.setOnClickListener(v -> {
                if (xServerView != null) {
                    final HostRenderer renderer = xServerView.getRenderer();
                    if (magnifierView == null) {
                        FrameLayout flContainer = findViewById(R.id.FLXServerDisplay);
                        magnifierView = new MagnifierView(this);
                        magnifierView.setZoomButtonCallback(value -> {
                            renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                            magnifierView.setZoomValue(renderer.getMagnifierZoom());
                        });
                        magnifierView.setZoomValue(renderer.getMagnifierZoom());
                        magnifierView.setHideButtonCallback(() -> {
                            flContainer.removeView(magnifierView);
                            magnifierView = null;
                        });
                        flContainer.addView(magnifierView);
                    }
                }
                drawerLayout.closeDrawers();
            });
        }

        View btItemSoftStretch = findViewById(R.id.BTItemSoftStretch);
        if (btItemSoftStretch != null) {
            btItemSoftStretch.setSelected(softStretchEnabled);
            btItemSoftStretch.setOnClickListener(v -> {
                if (xServerView != null) {
                    softStretchEnabled = !softStretchEnabled;
                    HostRenderer rendererRef = xServerView.getRenderer();
                    if (rendererRef instanceof VulkanRenderer) {
                        ((VulkanRenderer) rendererRef).setStretchMode(softStretchEnabled ? 1 : 0);
                    }
                    btItemSoftStretch.setSelected(softStretchEnabled);
                }
                drawerLayout.closeDrawers();
            });
        }

        View btItemTaskManager = findViewById(R.id.BTItemTaskManager);
        if (btItemTaskManager != null) {
            btItemTaskManager.setOnClickListener(v -> {
                openSidebarPanel(R.id.BTItemTaskManager, R.id.LLSubTaskManager);
                View taskPanel = findViewById(R.id.LLSubTaskManager);
                if (taskPanel != null) {
                    if (taskManagerSidebar == null)
                        taskManagerSidebar = new TaskManagerSidebar(this, taskPanel);
                    taskManagerSidebar.start();
                }
            });
        }

        if (btItemLogs != null) {
            btItemLogs.setOnClickListener(v -> {
                if (debugDialog != null)
                    debugDialog.show();
                drawerLayout.closeDrawers();
            });
        }

        View btItemExit = findViewById(R.id.BTItemExit);
        if (btItemExit != null) {
            btItemExit.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                exit();
            });
        }

        View performanceControls = findViewById(R.id.BTPowerUserPerformance);
        if (performanceControls != null) {
            performanceControls.setOnClickListener(v -> {
                drawerLayout.closeDrawers();
                PerformanceControlDialog.showInGame(
                        this,
                        shortcut,
                        this::applyPerformanceKeyLive);
            });
        }
    }

    private int activeSidebarItemId = R.id.BTItemFPS;
    private int activeSidebarPanelId = R.id.LLSubFPS;

    private final int[] sidebarPanelIds = {
        R.id.LLSubInput,
        R.id.LLSubMouse,
        R.id.LLSubFPS,
        R.id.LLSubGraphics,
        R.id.LLSubScreen,
        R.id.LLSubTaskManager
    };

    private final int[] sidebarItemIds = {
        R.id.BTItemInput,
        R.id.BTItemMouse,
        R.id.BTItemFPS,
        R.id.BTItemGraphics,
        R.id.BTItemScreen,
        R.id.BTItemTaskManager
    };

    private void hideAllSidebarPanels() {
        if (taskManagerSidebar != null) taskManagerSidebar.stop();
        for (int panelId : sidebarPanelIds) {
            View panel = findViewById(panelId);
            if (panel != null) panel.setVisibility(View.GONE);
        }
    }

    private void setSidebarActiveItem(int activeId) {
        for (int itemId : sidebarItemIds) {
            View item = findViewById(itemId);
            if (item == null) continue;
            if (itemId == activeId) {
                item.setBackgroundResource(R.drawable.sidebar_nav_icon_active);
                item.animate().scaleX(1.025f).scaleY(1.025f).setDuration(105).start();
            } else {
                item.setBackgroundResource(R.drawable.sidebar_nav_icon_bg);
                item.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start();
            }
        }
    }

    private void openSidebarPanel(int parentId, int subId) {
        hideAllSidebarPanels();
        View sub = findViewById(subId);
        if (sub != null) {
            float density = getResources().getDisplayMetrics().density;
            sub.setVisibility(View.VISIBLE);
            sub.setAlpha(0.0f);
            sub.setTranslationX(-8.0f * density);
            sub.animate().alpha(1.0f).translationX(0.0f).setDuration(130).start();
        }
        setSidebarActiveItem(parentId);
        if (parentId != R.id.BTItemMouse && parentId != R.id.BTItemPause) {
            activeSidebarItemId = parentId;
            activeSidebarPanelId = subId;
        }
    }

    private void toggleOnClick(int parentId, int subId) {
        View parent = findViewById(parentId);
        View sub = findViewById(subId);
        if (parent != null && sub != null) {
            parent.setOnClickListener(v -> openSidebarPanel(parentId, subId));
        }
    }

    private ArrayAdapter<String> createSidebarSpinnerAdapter(String[] items) {
        return ThemeUtils.createSpinnerAdapter(this, items);
    }

    private void applySidebarSpinnerTheme() {
        int[] spinnerIds = {
            R.id.SPNativeFPS,
            R.id.SPUpscalerMode,
            R.id.SPPostFXMode,
            R.id.SPColorMode,
            R.id.SPInputControlsProfile,
            R.id.SPHudStyle
        };

        for (int spinnerId : spinnerIds) {
            Spinner spinner = findViewById(spinnerId);
            if (spinner != null) ThemeUtils.applySpinnerTheme(spinner);
        }
    }

    private void setupSidebarHudControls() {
        Switch       swHudMaster     = findViewById(R.id.SWHudMaster);
        Spinner      spHudStyle      = findViewById(R.id.SPHudStyle);
        LinearLayout llHudStyleRow   = findViewById(R.id.LLHudStyleRow);
        LinearLayout llModernOptions = findViewById(R.id.LLModernHudOptions);
        CheckBox     cbFps           = findViewById(R.id.CBHudFps);
        CheckBox     cbGpu           = findViewById(R.id.CBHudGpu);
        CheckBox     cbCpuRam        = findViewById(R.id.CBHudCpuRam);
        CheckBox     cbRam           = findViewById(R.id.CBHudRam);
        CheckBox     cbBattTemp      = findViewById(R.id.CBHudBattTemp);
        CheckBox     cbGraph         = findViewById(R.id.CBHudGraph);
        CheckBox     cbRenderer      = findViewById(R.id.CBHudRenderer);
        SeekBar      sbScale         = findViewById(R.id.SBHudScale);
        SeekBar      sbAlpha         = findViewById(R.id.SBHudAlpha);
        View         btResetHud      = findViewById(R.id.BTResetHud);

        int currentMode = 0;
        if      (modernHud  != null) currentMode = 2;
        else if (classicHud != null) currentMode = 1;
        else if (container  != null) {
            String extra = container.getExtra("hudMode");
            if (!extra.isEmpty())           currentMode = Integer.parseInt(extra);
            else if (container.isShowFPS()) currentMode = 1;
        }

        boolean hudOn    = currentMode != 0;
        boolean isModern = currentMode == 2;

        if (spHudStyle != null) {
            ArrayAdapter<String> styleAdapter = createSidebarSpinnerAdapter(new String[]{"Classic", "Modern"});
            spHudStyle.setAdapter(styleAdapter);
            spHudStyle.setSelection(isModern ? 1 : 0, false);
        }
        if (llHudStyleRow  != null) llHudStyleRow.setVisibility(hudOn ? View.VISIBLE : View.GONE);
        if (llModernOptions != null) llModernOptions.setVisibility(isModern ? View.VISIBLE : View.GONE);

        if (modernHud != null) {
            modernHud.syncCheckboxes(cbFps, cbGpu, cbCpuRam, cbBattTemp, cbGraph, cbRenderer);
            if (cbRam != null) cbRam.setChecked(true);
            bindModernHudCheckboxes(cbFps, cbGpu, cbCpuRam, cbRam, cbBattTemp, cbRenderer);
        }
        if (sbScale != null) sbScale.setOnValueChangeListener((sb, v) -> {
            if (modernHud != null) modernHud.setHudScale(1f + (v - 50f) / 50f);
        });
        if (sbAlpha != null) sbAlpha.setOnValueChangeListener((sb, v) -> {
            if (modernHud != null) modernHud.setHudAlpha(v / 100f);
        });
        if (btResetHud != null) btResetHud.setOnClickListener(v -> {
            if (modernHud != null) modernHud.forceReset();
        });

        if (swHudMaster != null) {
            swHudMaster.setChecked(hudOn);
            swHudMaster.setOnCheckedChangeListener((btn, checked) -> {
                int style = resolveSelectedStyle(spHudStyle);
                if (checked) {
                    enableHudLazily(style);
                    if (llHudStyleRow  != null) llHudStyleRow.setVisibility(View.VISIBLE);
                    if (llModernOptions != null)
                        llModernOptions.setVisibility(style == 2 ? View.VISIBLE : View.GONE);
                    if (style == 2 && modernHud != null) {
                        modernHud.syncCheckboxes(cbFps, cbGpu, cbCpuRam, cbBattTemp, cbGraph, cbRenderer);
                        if (cbRam != null) cbRam.setChecked(true);
                        bindModernHudCheckboxes(cbFps, cbGpu, cbCpuRam, cbRam, cbBattTemp, cbRenderer);
                    }
                    saveHudModeToContainer(style);
                } else {
                    if (classicHud != null) classicHud.disableByUser();
                    if (modernHud  != null) modernHud.disableByUser();
                    if (llHudStyleRow  != null) llHudStyleRow.setVisibility(View.GONE);
                    if (llModernOptions != null) llModernOptions.setVisibility(View.GONE);
                    saveHudModeToContainer(0);
                }
            });
        }

        if (spHudStyle != null) {
            spHudStyle.post(() -> spHudStyle.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (swHudMaster == null || !swHudMaster.isChecked()) return;
                        int newStyle = (pos == 1) ? 2 : 1;
                        if (classicHud != null) classicHud.disableByUser(false);
                        if (modernHud  != null) modernHud.disableByUser(false);
                        enableHudLazily(newStyle);
                        if (llModernOptions != null)
                            llModernOptions.setVisibility(newStyle == 2 ? View.VISIBLE : View.GONE);
                        if (newStyle == 2 && modernHud != null) {
                            modernHud.syncCheckboxes(cbFps, cbGpu, cbCpuRam, cbBattTemp, cbGraph, cbRenderer);
                            if (cbRam != null) cbRam.setChecked(true);
                            bindModernHudCheckboxes(cbFps, cbGpu, cbCpuRam, cbRam, cbBattTemp, cbRenderer);
                        }
                        saveHudModeToContainer(newStyle);
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                }
            ));
        }
    }

    private void enableHudLazily(int style) {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        if (rootView == null || xServerView == null) return;
        final HostRenderer renderer = xServerView.getRenderer();

        boolean rendererAlreadyActive = (activeRendererWindowId != -1);

        if (style == 2) {
            if (modernHud == null) {
                modernHud = new WinlatorHUD(this);
                modernHud.setVisibility(View.GONE);
                rootView.addView(modernHud);
                if (renderer != null) renderer.setFrameRating(modernHud);
                if (rendererAlreadyActive) {

                    frameRatingWindowId = activeRendererWindowId;
                    if (renderer != null) renderer.setFpsWindowId(frameRatingWindowId);

                    final String name = lastRendererName;
                    modernHud.onRendererDetected(name);
                }
            }
            modernHud.enableByUser();
        } else {
            if (classicHud == null) {
                classicHud = new FrameRating(this, graphicsDriverConfig);
                classicHud.setVisibility(View.GONE);
                rootView.addView(classicHud);
                if (renderer != null) renderer.setFrameRating(classicHud);
                if (rendererAlreadyActive) {
                    frameRatingWindowId = activeRendererWindowId;
                    if (renderer != null) renderer.setFpsWindowId(frameRatingWindowId);
                    runOnUiThread(() -> classicHud.update());
                }
            }
            classicHud.enableByUser();
        }
    }

    private void bindModernHudCheckboxes(CheckBox cbFps, CheckBox cbGpu, CheckBox cbCpuRam,
            CheckBox cbRam, CheckBox cbBattTemp, CheckBox cbRenderer) {
        if (modernHud == null) return;
        if (cbFps      != null) cbFps.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(0, v));
        if (cbGpu      != null) cbGpu.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(2, v));
        if (cbCpuRam   != null) cbCpuRam.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(3, v));
        if (cbRam      != null) cbRam.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(7, v));
        if (cbBattTemp != null) cbBattTemp.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(4, v));
        if (cbRenderer != null) cbRenderer.setOnCheckedChangeListener((b, v) -> modernHud.toggleElement(6, v));
    }

    private int resolveSelectedStyle(Spinner spHudStyle) {
        if (spHudStyle == null) return 1;
        return spHudStyle.getSelectedItemPosition() == 1 ? 2 : 1;
    }

    private void saveHudModeToContainer(int mode) {
        if (container == null) return;
        container.putExtra("hudMode", String.valueOf(mode));
        container.setShowFPS(mode != 0);
        container.saveData();
    }

    private void setupSidebarGraphicsControls() {
        if (xServerView == null) return;
        final HostRenderer renderer = xServerView.getRenderer();
        final VulkanRenderer vkRenderer = renderer instanceof VulkanRenderer ? (VulkanRenderer) renderer : null;
        final GLRenderer glRenderer = renderer instanceof GLRenderer ? (GLRenderer) renderer : null;
        final ASurfaceRenderer asrRenderer = renderer instanceof ASurfaceRenderer ? (ASurfaceRenderer) renderer : null;

        Spinner spNativeFPS        = findViewById(R.id.SPNativeFPS);
        View    llStandardOptions  = findViewById(R.id.LLStandardOptions);
        Switch  swEnableFSR        = findViewById(R.id.SWEnableFSR);
        Spinner spUpscalerMode     = findViewById(R.id.SPUpscalerMode);
        View    lblSharpnessHeader = findViewById(R.id.LBLSharpnessHeader);
        SeekBar sbSharpness        = findViewById(R.id.SBSharpness);
        TextView tvSharpnessValue  = findViewById(R.id.TVSharpnessValue);
        Spinner spPostFXMode       = findViewById(R.id.SPPostFXMode);
        Spinner spColorMode        = findViewById(R.id.SPColorMode);
        View    btSaveGraphicsPreset = findViewById(R.id.BTSaveGraphicsPreset);
        View    btScalingNone      = findViewById(R.id.BTScalingNone);
        View    btScalingBilinear  = findViewById(R.id.BTScalingBilinear);
        View    btScalingNearest   = findViewById(R.id.BTScalingNearest);
        View    btScalingSgsr      = findViewById(R.id.BTScalingSgsr);
        View    btScalingFsr       = findViewById(R.id.BTScalingFsr);
        View    btScalingFsrFit    = findViewById(R.id.BTScalingFsrFit);
        View    btScalingDls       = findViewById(R.id.BTScalingDls);
        View    btScalingNis       = findViewById(R.id.BTScalingNis);

        if (spColorMode    != null) spColorMode.setVisibility(View.GONE);

        final int[]    fpsValues = {0, 30, 60, 90, 120};
        final String[] fpsLabels = {"Off", "30 FPS", "60 FPS", "90 FPS", "120 FPS"};

        if (spNativeFPS != null) {
            ArrayAdapter<String> a = createSidebarSpinnerAdapter(fpsLabels);
            spNativeFPS.setAdapter(a);
            String savedFps = getLaunchGraphicsExtra("graphicsFpsPreset", "");
            int savedFpsPos = savedFps.isEmpty() ? 0 : Integer.parseInt(savedFps);
            if (savedFpsPos < 0 || savedFpsPos >= fpsLabels.length) savedFpsPos = 0;
            spNativeFPS.setSelection(savedFpsPos);
            renderer.setFpsLimit(savedFpsPos < fpsValues.length ? fpsValues[savedFpsPos] : 0);
            spNativeFPS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (llStandardOptions != null) llStandardOptions.setVisibility(View.VISIBLE);
                    renderer.setFpsLimit(pos < fpsValues.length ? fpsValues[pos] : 0);
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        final String[] upscalerLabels = {"SGSR", "FSR / FidelityFX-CAS", "DLS", "NVScaler"};
        final Runnable[] applyGlEffectsRef = new Runnable[1];
        final int[] selectedBaseFilterMode = {getPersistentRendererFilterMode()};
        final int[] selectedScalingMode = {GRAPHICS_SCALING_NONE};

        if (spUpscalerMode != null) {
            ArrayAdapter<String> a = createSidebarSpinnerAdapter(upscalerLabels);
            spUpscalerMode.setAdapter(a);
            spUpscalerMode.setSelection(getSavedUpscalerSelection(getLaunchGraphicsExtra("graphicsFilterMode", "")));
        }

        final String[] pfxLabels = {"None", "DLS", "CRT", "HDR", "Natural"};
        if (spPostFXMode != null) {
            ArrayAdapter<String> a = createSidebarSpinnerAdapter(pfxLabels);
            spPostFXMode.setAdapter(a);

            String savedPFX = getLaunchGraphicsExtra("graphicsPostFXMode", "");
            int initPFX = savedPFX.isEmpty() ? 0 : Integer.parseInt(savedPFX);
            if (initPFX < 0 || initPFX >= pfxLabels.length) initPFX = 0;

            spPostFXMode.setSelection(initPFX, false);
            if (initPFX > 0 && vkRenderer != null) vkRenderer.setPostFXMode(initPFX);
        }

        float initSharp = getLaunchGraphicsSharpnessPercent();
        if (sbSharpness != null) {
            sbSharpness.setValue(initSharp);
            if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(initSharp)));
            if (vkRenderer != null) {
                vkRenderer.setSharpness(initSharp / 100f);
                sbSharpness.setOnValueChangeListener((sb, v) -> {
                    if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(v)));
                    vkRenderer.setSharpness(v / 100f);
                });
            } else if (asrRenderer != null) {
                asrRenderer.setSharpness(initSharp / 100f);
                sbSharpness.setOnValueChangeListener((sb, v) -> {
                    if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(v)));
                    asrRenderer.setSharpness(v / 100f);
                });
            } else if (glRenderer != null) {
                sbSharpness.setOnValueChangeListener((sb, v) -> {
                    if (tvSharpnessValue != null) tvSharpnessValue.setText(String.valueOf(Math.round(v)));
                    if (applyGlEffectsRef[0] != null) applyGlEffectsRef[0].run();
                });
            }
        }

        Runnable updateSharpnessVis = () -> {
            if (lblSharpnessHeader != null) lblSharpnessHeader.setVisibility(View.VISIBLE);
            if (sbSharpness != null) sbSharpness.setVisibility(View.VISIBLE);
        };

        if (glRenderer != null) {
            applyGlEffectsRef[0] = () -> {
                FSREffect fsrEffect = (FSREffect) glRenderer.getEffectComposer().getEffect(FSREffect.class);
                if (fsrEffect != null) glRenderer.getEffectComposer().removeEffect(fsrEffect);
                CRTEffect crtEffect = (CRTEffect) glRenderer.getEffectComposer().getEffect(CRTEffect.class);
                if (crtEffect != null) glRenderer.getEffectComposer().removeEffect(crtEffect);
                HDREffect hdrEffect = (HDREffect) glRenderer.getEffectComposer().getEffect(HDREffect.class);
                if (hdrEffect != null) glRenderer.getEffectComposer().removeEffect(hdrEffect);
                ColorEffect colorEffect = (ColorEffect) glRenderer.getEffectComposer().getEffect(ColorEffect.class);
                if (colorEffect != null) glRenderer.getEffectComposer().removeEffect(colorEffect);

                int sharpnessPos = sbSharpness != null ? Math.round(sbSharpness.getValue()) : 50;
                boolean fsrEnabled = swEnableFSR != null && swEnableFSR.isChecked();
                int postFxPos = spPostFXMode != null ? spPostFXMode.getSelectedItemPosition() : 0;

                if (fsrEnabled) {
                    FSREffect newFsr = new FSREffect();
                    newFsr.setMode(postFxPos == 1 ? FSREffect.MODE_DLS : FSREffect.MODE_SUPER_RESOLUTION);
                    newFsr.setLevel((float) sharpnessPos / 25.0f + 1.0f);
                    glRenderer.getEffectComposer().addEffect(newFsr);
                }

                if (postFxPos == 2) {
                    glRenderer.getEffectComposer().addEffect(new CRTEffect());
                } else if (postFxPos == 3) {
                    HDREffect newHdr = new HDREffect();
                    newHdr.setStrength(1.0f);
                    glRenderer.getEffectComposer().addEffect(newHdr);
                } else if (postFxPos == 4) {
                    ColorEffect natural = new ColorEffect();
                    natural.setContrast(0.10f);
                    natural.setGamma(1.05f);
                    glRenderer.getEffectComposer().addEffect(natural);
                }
                glRenderer.getXServerView().requestRender();
            };
        }

        final Runnable updateScalingButtons = () -> {
            setSelectedModeButton(R.id.BTScalingNone, selectedScalingMode[0] == GRAPHICS_SCALING_NONE);
            setSelectedModeButton(R.id.BTScalingBilinear,
                    selectedScalingMode[0] == GRAPHICS_SCALING_BILINEAR);
            setSelectedModeButton(R.id.BTScalingNearest, selectedScalingMode[0] == GRAPHICS_SCALING_NEAREST);
            setSelectedModeButton(R.id.BTScalingSgsr, selectedScalingMode[0] == GRAPHICS_SCALING_SGSR);
            setSelectedModeButton(R.id.BTScalingFsr, selectedScalingMode[0] == GRAPHICS_SCALING_FSR);
            setSelectedModeButton(R.id.BTScalingFsrFit, selectedScalingMode[0] == GRAPHICS_SCALING_FSR_FIT);
            setSelectedModeButton(R.id.BTScalingDls, selectedScalingMode[0] == GRAPHICS_SCALING_DLS);
            setSelectedModeButton(R.id.BTScalingNis, selectedScalingMode[0] == GRAPHICS_SCALING_NIS);
        };

        final Runnable applyScalingSelection = () -> {
            int upscalerSelection = spUpscalerMode != null ? spUpscalerMode.getSelectedItemPosition() : 0;
            int postFxSelection = spPostFXMode != null ? spPostFXMode.getSelectedItemPosition() : 0;
            boolean enableUpscaler = false;
            boolean enableSoftStretch = false;

            switch (selectedScalingMode[0]) {
                case GRAPHICS_SCALING_NONE:
                    // Disable the active upscaler without overwriting the user's
                    // configured Bilinear/Nearest texture-filter preference.
                    postFxSelection = 0;
                    break;
                case GRAPHICS_SCALING_BILINEAR:
                    selectedBaseFilterMode[0] = 0;
                    postFxSelection = 0;
                    break;
                case GRAPHICS_SCALING_NEAREST:
                    selectedBaseFilterMode[0] = 1;
                    postFxSelection = 0;
                    break;
                case GRAPHICS_SCALING_SGSR:
                    enableUpscaler = true;
                    upscalerSelection = 0;
                    postFxSelection = 0;
                    break;
                case GRAPHICS_SCALING_FSR:
                    enableUpscaler = true;
                    upscalerSelection = 1;
                    postFxSelection = 0;
                    break;
                case GRAPHICS_SCALING_FSR_FIT:
                    enableUpscaler = true;
                    upscalerSelection = 1;
                    postFxSelection = 0;
                    enableSoftStretch = true;
                    break;
                case GRAPHICS_SCALING_DLS:
                    enableUpscaler = true;
                    upscalerSelection = 2;
                    postFxSelection = glRenderer != null ? 1 : 0;
                    break;
                case GRAPHICS_SCALING_NIS:
                    enableUpscaler = true;
                    upscalerSelection = 3;
                    postFxSelection = 0;
                    break;
                default:
                    selectedBaseFilterMode[0] = 0;
                    postFxSelection = 0;
                    break;
            }

            softStretchEnabled = enableSoftStretch;
            View btItemSoftStretch = findViewById(R.id.BTItemSoftStretch);
            if (btItemSoftStretch != null) btItemSoftStretch.setSelected(softStretchEnabled);
            if (vkRenderer != null) vkRenderer.setStretchMode(softStretchEnabled ? 1 : 0);

            if (spUpscalerMode != null && spUpscalerMode.getSelectedItemPosition() != upscalerSelection) {
                spUpscalerMode.setSelection(upscalerSelection);
            }
            if (spPostFXMode != null && spPostFXMode.getSelectedItemPosition() != postFxSelection) {
                spPostFXMode.setSelection(postFxSelection, false);
            }
            if (swEnableFSR != null && swEnableFSR.isChecked() != enableUpscaler) {
                swEnableFSR.setChecked(enableUpscaler);
            }

            if (!enableUpscaler) {
                int runtimeFilter = glRenderer != null && selectedBaseFilterMode[0] == 1 ? 2 : selectedBaseFilterMode[0];
                renderer.setFilterMode(runtimeFilter);
                if (glRenderer != null && applyGlEffectsRef[0] != null) applyGlEffectsRef[0].run();
            } else if (vkRenderer != null || asrRenderer != null) {
                renderer.setFilterMode(getSelectedUpscalerFilterMode(spUpscalerMode));
            }

            updateScalingButtons.run();
            updateSharpnessVis.run();
        };

        String savedFilter = getLaunchGraphicsExtra("graphicsFilterMode", "");
        int savedFilterMode = 0;
        try {
            savedFilterMode = savedFilter == null || savedFilter.isEmpty() ? 0 : Integer.parseInt(savedFilter);
        } catch (NumberFormatException ignored) {}
        String savedScalingModeValue = getLaunchGraphicsExtra(GRAPHICS_SIDEBAR_SCALING_MODE_KEY, "");
        try {
            if (!savedScalingModeValue.isEmpty()) {
                selectedScalingMode[0] = Integer.parseInt(savedScalingModeValue);
            } else if (savedFilterMode == 2) {
                selectedScalingMode[0] = GRAPHICS_SCALING_SGSR;
            } else if (savedFilterMode == 4) {
                selectedScalingMode[0] = softStretchEnabled ? GRAPHICS_SCALING_FSR_FIT : GRAPHICS_SCALING_FSR;
            } else if (savedFilterMode == 5) {
                selectedScalingMode[0] = GRAPHICS_SCALING_DLS;
            } else if (savedFilterMode == 3) {
                selectedScalingMode[0] = GRAPHICS_SCALING_NIS;
            } else if (selectedBaseFilterMode[0] == 1) {
                selectedScalingMode[0] = GRAPHICS_SCALING_NEAREST;
            } else {
                selectedScalingMode[0] = GRAPHICS_SCALING_NONE;
            }
        } catch (NumberFormatException ignored) {
            selectedScalingMode[0] = GRAPHICS_SCALING_NONE;
        }
        if (glRenderer != null && glRenderer.isNativeMode()
                && selectedScalingMode[0] != GRAPHICS_SCALING_NONE
                && !savedScalingModeValue.isEmpty()) {
            glRenderer.setNativeMode(false);
            setPersistentRendererNative(false);
            persistLaunchGraphicsPreset();
        }

        if (spUpscalerMode != null) {
            spUpscalerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (swEnableFSR != null && swEnableFSR.isChecked()) {
                        if (vkRenderer != null || asrRenderer != null) {
                            renderer.setFilterMode(getSelectedUpscalerFilterMode(spUpscalerMode));
                        }
                        else if (applyGlEffectsRef[0] != null) applyGlEffectsRef[0].run();
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        if (swEnableFSR != null) {
            swEnableFSR.setOnCheckedChangeListener((btn, checked) -> {
                if (vkRenderer != null || asrRenderer != null) {
                    renderer.setFilterMode(checked
                            ? (spUpscalerMode != null ? getSelectedUpscalerFilterMode(spUpscalerMode) : VULKAN_UPSCALER_FILTER_VALUES[0])
                            : selectedBaseFilterMode[0]);
                } else if (glRenderer != null) {
                    renderer.setFilterMode(checked ? 2 : (selectedBaseFilterMode[0] == 1 ? 2 : 0));
                    if (applyGlEffectsRef[0] != null) applyGlEffectsRef[0].run();
                }
                updateSharpnessVis.run();
            });
        }

        if (spPostFXMode != null) {
            spPostFXMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (vkRenderer != null) vkRenderer.setPostFXMode(pos);
                    else if (applyGlEffectsRef[0] != null) applyGlEffectsRef[0].run();
                    updateSharpnessVis.run();
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        View.OnClickListener scalingClickListener = v -> {
            int viewId = v.getId();
            if (viewId == R.id.BTScalingNone) selectedScalingMode[0] = GRAPHICS_SCALING_NONE;
            else if (viewId == R.id.BTScalingBilinear) selectedScalingMode[0] = GRAPHICS_SCALING_BILINEAR;
            else if (viewId == R.id.BTScalingNearest) selectedScalingMode[0] = GRAPHICS_SCALING_NEAREST;
            else if (viewId == R.id.BTScalingSgsr) selectedScalingMode[0] = GRAPHICS_SCALING_SGSR;
            else if (viewId == R.id.BTScalingFsr) selectedScalingMode[0] = GRAPHICS_SCALING_FSR;
            else if (viewId == R.id.BTScalingFsrFit) selectedScalingMode[0] = GRAPHICS_SCALING_FSR_FIT;
            else if (viewId == R.id.BTScalingDls) selectedScalingMode[0] = GRAPHICS_SCALING_DLS;
            else if (viewId == R.id.BTScalingNis) selectedScalingMode[0] = GRAPHICS_SCALING_NIS;
            // GL native scanout bypasses the compositor sampler/effect chain. Turn it off
            // before applying a scaling selection so the chosen mode has a visible effect.
            if (glRenderer != null && glRenderer.isNativeMode()
                    && selectedScalingMode[0] != GRAPHICS_SCALING_NONE) {
                glRenderer.setNativeMode(false);
                setPersistentRendererNative(false);
            }
            applyScalingSelection.run();
            saveLaunchGraphicsExtra(GRAPHICS_SIDEBAR_SCALING_MODE_KEY,
                    String.valueOf(selectedScalingMode[0]));
            int savedGraphicsFilter = selectedScalingMode[0] == GRAPHICS_SCALING_SGSR ? 2
                    : selectedScalingMode[0] == GRAPHICS_SCALING_FSR
                            || selectedScalingMode[0] == GRAPHICS_SCALING_FSR_FIT ? 4
                    : selectedScalingMode[0] == GRAPHICS_SCALING_DLS ? 5
                    : selectedScalingMode[0] == GRAPHICS_SCALING_NIS ? 3 : 0;
            saveLaunchGraphicsExtra("graphicsFilterMode", String.valueOf(savedGraphicsFilter));
            saveLaunchGraphicsExtra("graphicsStretchMode", softStretchEnabled ? "1" : "0");
            if (selectedScalingMode[0] == GRAPHICS_SCALING_BILINEAR) {
                setPersistentRendererFilterMode(0);
            } else if (selectedScalingMode[0] == GRAPHICS_SCALING_NEAREST) {
                setPersistentRendererFilterMode(1);
            }
            persistLaunchGraphicsPreset();
        };
        if (btScalingNone != null) btScalingNone.setOnClickListener(scalingClickListener);
        if (btScalingBilinear != null) btScalingBilinear.setOnClickListener(scalingClickListener);
        if (btScalingNearest != null) btScalingNearest.setOnClickListener(scalingClickListener);
        if (btScalingSgsr != null) btScalingSgsr.setOnClickListener(scalingClickListener);
        if (btScalingFsr != null) btScalingFsr.setOnClickListener(scalingClickListener);
        if (btScalingFsrFit != null) btScalingFsrFit.setOnClickListener(scalingClickListener);
        if (btScalingDls != null) btScalingDls.setOnClickListener(scalingClickListener);
        if (btScalingNis != null) btScalingNis.setOnClickListener(scalingClickListener);

        View.OnClickListener fullscreenClickListener = v -> {
            int viewId = v.getId();
            int fullscreenMode = Container.FULLSCREEN_OFF;
            if (viewId == R.id.BTFullscreenFit) fullscreenMode = Container.FULLSCREEN_FIT;
            else if (viewId == R.id.BTFullscreenStretch) fullscreenMode = Container.FULLSCREEN_STRETCH;
            else if (viewId == R.id.BTFullscreenFill) fullscreenMode = Container.FULLSCREEN_FILL;
            else if (viewId == R.id.BTFullscreenInteger) fullscreenMode = Container.FULLSCREEN_INTEGER;
            setRendererFullscreenMode(fullscreenMode, true);
        };
        setOnClickListenerIfPresent(R.id.BTFullscreenOff, fullscreenClickListener);
        setOnClickListenerIfPresent(R.id.BTFullscreenFit, fullscreenClickListener);
        setOnClickListenerIfPresent(R.id.BTFullscreenStretch, fullscreenClickListener);
        setOnClickListenerIfPresent(R.id.BTFullscreenFill, fullscreenClickListener);
        setOnClickListenerIfPresent(R.id.BTFullscreenInteger, fullscreenClickListener);

        applyScalingSelection.run();
        updateFullscreenModeUi(renderer.getFullscreenMode());

        if (glRenderer != null) {
            applyGlEffectsRef[0].run();
        }
        setupSidebarFrameGenControls(vkRenderer);
        updateSharpnessVis.run();

        if (btSaveGraphicsPreset != null) {
            btSaveGraphicsPreset.setOnClickListener(v -> {
                if (container == null && shortcut == null) return;
                saveLaunchGraphicsExtra("graphicsFpsPreset",
                    String.valueOf(spNativeFPS != null ? spNativeFPS.getSelectedItemPosition() : 0));
                saveLaunchGraphicsExtra("graphicsFilterMode",
                    String.valueOf(swEnableFSR != null && swEnableFSR.isChecked()
                        ? (spUpscalerMode != null ? getSelectedUpscalerFilterMode(spUpscalerMode) : VULKAN_UPSCALER_FILTER_VALUES[0]) : 0));
                saveLaunchGraphicsExtra("graphicsStretchMode", softStretchEnabled ? "1" : "0");
                saveLaunchGraphicsExtra("graphicsSharpness",
                    String.valueOf(sbSharpness != null ? sbSharpness.getValue() : 50f));
                saveLaunchGraphicsExtra("graphicsPostFXMode",
                    String.valueOf(spPostFXMode != null ? spPostFXMode.getSelectedItemPosition() : 0));
                saveLaunchGraphicsExtra(GRAPHICS_SIDEBAR_SCALING_MODE_KEY, String.valueOf(selectedScalingMode[0]));
                saveLaunchGraphicsExtra("graphicsColorMode", "0");
                setPersistentRendererFilterMode(selectedBaseFilterMode[0]);
                persistLaunchGraphicsPreset();
                Toast.makeText(this, "Preset saved", Toast.LENGTH_SHORT).show();
            });
        }

    }

    private void setupSidebarFrameGenControls(VulkanRenderer vkRenderer) {
        if (container == null) return;

        boolean dllAvailable = shortcut != null
                ? LsfgVkManager.containerDllPath(shortcut) != null || LsfgVkManager.isGlobalDllAvailable(this)
                : LsfgVkManager.containerDllPath(container) != null || LsfgVkManager.isGlobalDllAvailable(this);
        final String[] selectedBackend = {shortcut != null
                ? FrameGenManager.getBackend(shortcut) : FrameGenManager.getBackend(container)};
        final int[] selectedMultiplier = {getFrameGenMultiplier(selectedBackend[0])};
        final int[] selectedBionicModel = {shortcut != null
                ? shortcut.getBionicFgModel() : container.getBionicFgModel()};
        if (activeFrameGenBackend == null) {
            activeFrameGenBackend = selectedBackend[0];
            activeFrameGenMultiplier = selectedMultiplier[0];
        }
        TextView status = findViewById(R.id.TVFrameGenStatus);
        View modelGroup = findViewById(R.id.GroupFrameGenModel);

        Runnable updateUi = () -> {
            boolean nativeBackend = FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0]);
            boolean bionicBackend = FrameGenManager.BACKEND_BIONIC_FG.equals(selectedBackend[0]);
            setSelectedModeButton(R.id.BTFrameGenLsfg, FrameGenManager.BACKEND_LSFG_VK.equals(selectedBackend[0]));
            setSelectedModeButton(R.id.BTFrameGenBionic, bionicBackend);
            setSelectedModeButton(R.id.BTFrameGenNative, nativeBackend);
            setSelectedModeButton(R.id.BTFrameGenOff, selectedMultiplier[0] < 2);
            setSelectedModeButton(R.id.BTFrameGen2x, selectedMultiplier[0] == 2);
            setSelectedModeButton(R.id.BTFrameGen3x, selectedMultiplier[0] == 3);
            setSelectedModeButton(R.id.BTFrameGen4x, selectedMultiplier[0] == 4);
            if (modelGroup != null) {
                modelGroup.setVisibility(bionicBackend && selectedMultiplier[0] >= 2
                        ? View.VISIBLE : View.GONE);
            }
            setSelectedModeButton(R.id.BTFrameGenModelDefault, selectedBionicModel[0] == 0);
            setSelectedModeButton(R.id.BTFrameGenModelTraced, selectedBionicModel[0] == 1);
            setSelectedModeButton(R.id.BTFrameGenModelV2, selectedBionicModel[0] == 2);
            setSelectedModeButton(R.id.BTFrameGenModelFsr3, selectedBionicModel[0] == 3);
            setSelectedModeButton(R.id.BTFrameGenModelFsr3Plus, selectedBionicModel[0] == 4);
            if (status != null) {
                if (nativeBackend) {
                    boolean canApplyLive = FrameGenManager.BACKEND_NATIVE_FG.equals(activeFrameGenBackend)
                            || activeFrameGenMultiplier < 2;
                    status.setText(vkRenderer == null
                            ? "Native Framegen requires the Vulkan renderer."
                            : canApplyLive
                                    ? "Native optical-flow frame generation applies immediately."
                                    : "Relaunch to switch from the active external framegen layer.");
                } else if (bionicBackend) {
                    boolean modelAppliesLive = FrameGenManager.BACKEND_BIONIC_FG.equals(activeFrameGenBackend)
                            && activeFrameGenMultiplier >= 2;
                    status.setText(modelAppliesLive
                            ? "Bionic-FG model changes apply immediately. Backend and multiplier changes need a relaunch."
                            : "Bionic-FG changes apply after relaunch.");
                } else {
                    status.setText(dllAvailable
                            ? "LSFG-VK changes apply after relaunch."
                            : "Import Lossless.dll before enabling LSFG-VK.");
                }
            }
            if (vkRenderer != null) {
                boolean canApplyLive = FrameGenManager.BACKEND_NATIVE_FG.equals(activeFrameGenBackend)
                        || activeFrameGenMultiplier < 2;
                float nativeSmoothing = shortcut != null
                        ? shortcut.getNativeFgSmoothing() : container.getNativeFgSmoothing();
                vkRenderer.setFrameGenerationSmoothing(nativeSmoothing);
                vkRenderer.setFrameGenerationMultiplier(nativeBackend && canApplyLive ? selectedMultiplier[0] : 0);
            }
        };

        View.OnClickListener backendListener = view -> {
            String backend = view.getId() == R.id.BTFrameGenBionic
                    ? FrameGenManager.BACKEND_BIONIC_FG
                    : view.getId() == R.id.BTFrameGenNative
                            ? FrameGenManager.BACKEND_NATIVE_FG
                            : FrameGenManager.BACKEND_LSFG_VK;
            if (FrameGenManager.BACKEND_NATIVE_FG.equals(backend) && vkRenderer == null) {
                AppUtils.showToast(this, "Native Framegen requires the Vulkan renderer");
                return;
            }
            boolean changed = !backend.equals(selectedBackend[0]);
            selectedBackend[0] = backend;
            selectedMultiplier[0] = getFrameGenMultiplier(backend);
            persistFrameGenSelection(backend, selectedMultiplier[0], selectedBionicModel[0]);
            updateUi.run();
            boolean needsRelaunch = changed && (!FrameGenManager.BACKEND_NATIVE_FG.equals(backend)
                    || (!FrameGenManager.BACKEND_NATIVE_FG.equals(activeFrameGenBackend)
                            && activeFrameGenMultiplier >= 2));
            if (needsRelaunch) {
                AppUtils.showToast(this, "Framegen backend changed. Relaunch the game to apply it.");
            }
        };
        setOnClickListenerIfPresent(R.id.BTFrameGenLsfg, backendListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenBionic, backendListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenNative, backendListener);

        View.OnClickListener multiplierListener = view -> {
            int multiplier = view.getId() == R.id.BTFrameGen2x ? 2
                    : view.getId() == R.id.BTFrameGen3x ? 3
                    : view.getId() == R.id.BTFrameGen4x ? 4 : 0;
            if (multiplier >= 2 && FrameGenManager.BACKEND_LSFG_VK.equals(selectedBackend[0]) && !dllAvailable) {
                AppUtils.showToast(this, "Import Lossless.dll before enabling LSFG-VK");
                return;
            }
            selectedMultiplier[0] = multiplier;
            persistFrameGenSelection(selectedBackend[0], multiplier, selectedBionicModel[0]);
            updateUi.run();
            if (!FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0])) {
                AppUtils.showToast(this, multiplier >= 2
                        ? "Framegen set to " + multiplier + "x. Relaunch the game to apply it."
                        : "Frame generation disabled after relaunch");
            }
        };
        setOnClickListenerIfPresent(R.id.BTFrameGenOff, multiplierListener);
        setOnClickListenerIfPresent(R.id.BTFrameGen2x, multiplierListener);
        setOnClickListenerIfPresent(R.id.BTFrameGen3x, multiplierListener);
        setOnClickListenerIfPresent(R.id.BTFrameGen4x, multiplierListener);

        View.OnClickListener modelListener = view -> {
            int model = view.getId() == R.id.BTFrameGenModelTraced ? 1
                    : view.getId() == R.id.BTFrameGenModelV2 ? 2
                    : view.getId() == R.id.BTFrameGenModelFsr3 ? 3
                    : view.getId() == R.id.BTFrameGenModelFsr3Plus ? 4 : 0;
            selectedBionicModel[0] = model;
            persistFrameGenSelection(selectedBackend[0], selectedMultiplier[0], model);
            updateUi.run();
            boolean appliesLive = FrameGenManager.BACKEND_BIONIC_FG.equals(activeFrameGenBackend)
                    && activeFrameGenMultiplier >= 2;
            AppUtils.showToast(this, appliesLive
                    ? "Bionic-FG model changed"
                    : "Bionic-FG model saved. Relaunch the game to apply it.");
        };
        setOnClickListenerIfPresent(R.id.BTFrameGenModelDefault, modelListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenModelTraced, modelListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenModelV2, modelListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenModelFsr3, modelListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenModelFsr3Plus, modelListener);
        setOnClickListenerIfPresent(R.id.BTFrameGenAdvanced, view ->
                showFrameGenAdvancedDialog(() -> {
                    selectedBackend[0] = shortcut != null
                            ? FrameGenManager.getBackend(shortcut) : FrameGenManager.getBackend(container);
                    selectedMultiplier[0] = getFrameGenMultiplier(selectedBackend[0]);
                    selectedBionicModel[0] = shortcut != null
                            ? shortcut.getBionicFgModel() : container.getBionicFgModel();
                    updateUi.run();
                }));
        updateUi.run();
    }

    private void showFrameGenAdvancedDialog(Runnable onSaved) {
        if (container == null) {
            AppUtils.showToast(this, "Container is not ready");
            return;
        }

        boolean dllAvailable = shortcut != null
                ? LsfgVkManager.containerDllPath(shortcut) != null || LsfgVkManager.isGlobalDllAvailable(this)
                : LsfgVkManager.containerDllPath(container) != null || LsfgVkManager.isGlobalDllAvailable(this);
        boolean nativeAvailable = xServerView != null
                && xServerView.getRenderer() instanceof VulkanRenderer;

        FrameGenQuickMenuHelper.Settings currentSettings = shortcut != null
                ? FrameGenQuickMenuHelper.readSettings(shortcut)
                : FrameGenQuickMenuHelper.readSettings(container);
        final String[] selectedBackend = {currentSettings.backend};
        final int[] selectedLsfgMultiplier = {shortcut != null
                ? shortcut.getLsfgMultiplier() : container.getLsfgMultiplier()};
        final float[] selectedLsfgFlowScale = {shortcut != null
                ? shortcut.getLsfgFlowScale() : container.getLsfgFlowScale()};
        final boolean[] selectedPerformanceMode = {shortcut != null
                ? shortcut.getLsfgPerformanceMode() : container.getLsfgPerformanceMode()};
        final int[] selectedBionicMultiplier = {shortcut != null
                ? shortcut.getBionicFgMultiplier() : container.getBionicFgMultiplier()};
        final float[] selectedBionicFlowScale = {shortcut != null
                ? shortcut.getBionicFgFlowScale() : container.getBionicFgFlowScale()};
        final int[] selectedBionicModel = {shortcut != null
                ? shortcut.getBionicFgModel() : container.getBionicFgModel()};
        final int[] selectedNativeMultiplier = {shortcut != null
                ? shortcut.getNativeFgMultiplier() : container.getNativeFgMultiplier()};
        final float[] selectedNativeSmoothing = {shortcut != null
                ? shortcut.getNativeFgSmoothing() : container.getNativeFgSmoothing()};

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(getResources().getDisplayMetrics().density * 16.0f);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView backendLabel = new TextView(this);
        backendLabel.setText("Backend");
        backendLabel.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurface));
        layout.addView(backendLabel);

        Spinner backendSpinner = new Spinner(this);
        ArrayAdapter<String> backendAdapter = ThemeUtils.createSpinnerAdapter(
                this,
                new String[]{"LSFG-VK", "Bionic-FG", "Native Framegen"});
        backendSpinner.setAdapter(backendAdapter);
        ThemeUtils.applySpinnerTheme(backendSpinner);
        backendSpinner.setBackgroundResource(R.drawable.framegen_spinner_background);
        backendSpinner.setPadding(padding / 2, 0, padding / 2, 0);
        backendSpinner.setMinimumHeight(padding * 3);
        backendSpinner.setSelection(FrameGenManager.BACKEND_BIONIC_FG.equals(currentSettings.backend) ? 1
                : FrameGenManager.BACKEND_NATIVE_FG.equals(currentSettings.backend) ? 2 : 0);
        layout.addView(backendSpinner);

        TextView status = new TextView(this);
        status.setPadding(0, padding / 2, 0, 0);
        status.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurfaceVariant));
        layout.addView(status);

        TextView multiplierLabel = new TextView(this);
        multiplierLabel.setPadding(0, padding, 0, 0);
        multiplierLabel.setText("Frame Multiplier");
        multiplierLabel.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurface));
        layout.addView(multiplierLabel);

        android.widget.RadioGroup multiplierGroup = new android.widget.RadioGroup(this);
        multiplierGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        int[] multiplierValues = {0, 2, 3, 4};
        for (int value : multiplierValues) {
            android.widget.RadioButton radioButton = new android.widget.RadioButton(this);
            radioButton.setId(View.generateViewId());
            radioButton.setTag(value);
            radioButton.setText(value == 0 ? "Off" : value + "x");
            radioButton.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurface));
            multiplierGroup.addView(radioButton);
        }
        layout.addView(multiplierGroup);

        TextView flowLabel = new TextView(this);
        flowLabel.setPadding(0, padding, 0, 0);
        flowLabel.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurface));
        layout.addView(flowLabel);

        android.widget.SeekBar flowSeekBar = new android.widget.SeekBar(this);
        flowSeekBar.setMax(75);
        layout.addView(flowSeekBar);

        TextView modelLabel = new TextView(this);
        modelLabel.setPadding(0, padding, 0, 0);
        modelLabel.setText("Bionic-FG Model");
        modelLabel.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurface));
        layout.addView(modelLabel);

        Spinner modelSpinner = new Spinner(this);
        ArrayAdapter<String> modelAdapter = ThemeUtils.createSpinnerAdapter(
                this,
                new String[]{"Default", "Traced graph", "V2 engine", "FSR3", "FSR3+"});
        modelSpinner.setAdapter(modelAdapter);
        ThemeUtils.applySpinnerTheme(modelSpinner);
        modelSpinner.setBackgroundResource(R.drawable.framegen_spinner_background);
        modelSpinner.setPadding(padding / 2, 0, padding / 2, 0);
        modelSpinner.setMinimumHeight(padding * 3);
        modelSpinner.setSelection(FrameGenQuickMenuHelper.sanitizeModel(selectedBionicModel[0]));
        layout.addView(modelSpinner);

        CheckBox performanceMode = new CheckBox(this);
        performanceMode.setText("LSFG performance mode");
        performanceMode.setTextColor(ThemeUtils.getColorAttr(this, R.attr.colorOnSurface));
        performanceMode.setChecked(selectedPerformanceMode[0]);
        layout.addView(performanceMode);

        final boolean[] syncingUi = {false};
        Runnable syncUi = () -> {
            syncingUi[0] = true;
            int backendPosition = backendSpinner.getSelectedItemPosition();
            selectedBackend[0] = backendPosition == 1
                    ? FrameGenManager.BACKEND_BIONIC_FG
                    : backendPosition == 2
                            ? FrameGenManager.BACKEND_NATIVE_FG
                            : FrameGenManager.BACKEND_LSFG_VK;
            boolean useBionicFg = FrameGenManager.BACKEND_BIONIC_FG.equals(selectedBackend[0]);
            boolean useNativeFg = FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0]);
            boolean backendAvailable = useBionicFg || (useNativeFg ? nativeAvailable : dllAvailable);

            status.setText(useBionicFg
                    ? "Bundled Bionic-FG. Model changes can apply live when Bionic-FG is active."
                    : useNativeFg
                            ? (nativeAvailable
                                    ? "Native frame generation can apply live with the Vulkan renderer."
                                    : "Native frame generation requires the Vulkan renderer.")
                            : (dllAvailable
                                    ? "LSFG-VK uses the imported Lossless.dll."
                                    : "Import Lossless.dll before enabling LSFG-VK."));

            int selectedMultiplier = useNativeFg ? selectedNativeMultiplier[0]
                    : useBionicFg ? selectedBionicMultiplier[0] : selectedLsfgMultiplier[0];
            for (int i = 0; i < multiplierGroup.getChildCount(); i++) {
                View child = multiplierGroup.getChildAt(i);
                if (child instanceof android.widget.RadioButton) {
                    Object tag = child.getTag();
                    ((android.widget.RadioButton) child).setChecked(tag instanceof Integer
                            && (Integer) tag == selectedMultiplier);
                    child.setEnabled(backendAvailable || (tag instanceof Integer && (Integer) tag == 0));
                }
            }

            float flowScale = useBionicFg ? selectedBionicFlowScale[0] : selectedLsfgFlowScale[0];
            flowLabel.setVisibility(View.VISIBLE);
            flowSeekBar.setVisibility(View.VISIBLE);
            flowSeekBar.setEnabled(backendAvailable);
            flowSeekBar.setMax(useNativeFg ? 100 : 75);
            flowSeekBar.setProgress(useNativeFg
                    ? Math.round(selectedNativeSmoothing[0] * 100.0f)
                    : Math.round((flowScale - 0.25f) * 100.0f));
            flowLabel.setText(useNativeFg
                    ? "Smoothness: " + Math.round(selectedNativeSmoothing[0] * 100.0f) + "%"
                    : String.format(java.util.Locale.US, "Flow scale: %.2f", flowScale));
            modelLabel.setVisibility(useBionicFg ? View.VISIBLE : View.GONE);
            modelSpinner.setVisibility(useBionicFg ? View.VISIBLE : View.GONE);
            performanceMode.setVisibility(useBionicFg || useNativeFg ? View.GONE : View.VISIBLE);
            performanceMode.setEnabled(dllAvailable);
            syncingUi[0] = false;
        };

        backendSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                syncUi.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        multiplierGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (syncingUi[0]) return;
            View checkedView = group.findViewById(checkedId);
            Object tag = checkedView != null ? checkedView.getTag() : null;
            if (!(tag instanceof Integer)) return;
            if (FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0])) {
                selectedNativeMultiplier[0] = (Integer) tag;
            } else if (FrameGenManager.BACKEND_BIONIC_FG.equals(selectedBackend[0])) {
                selectedBionicMultiplier[0] = (Integer) tag;
            } else {
                selectedLsfgMultiplier[0] = (Integer) tag;
            }
        });
        flowSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (syncingUi[0]) return;
                if (FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0])) {
                    selectedNativeSmoothing[0] = Math.max(0.0f, Math.min(1.0f, progress / 100.0f));
                    flowLabel.setText("Smoothness: "
                            + Math.round(selectedNativeSmoothing[0] * 100.0f) + "%");
                } else {
                    float value = FrameGenQuickMenuHelper.sanitizeFlowScale(0.25f + progress / 100.0f);
                    if (FrameGenManager.BACKEND_BIONIC_FG.equals(selectedBackend[0])) {
                        selectedBionicFlowScale[0] = value;
                    } else {
                        selectedLsfgFlowScale[0] = value;
                    }
                    flowLabel.setText(String.format(java.util.Locale.US, "Flow scale: %.2f", value));
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingUi[0]) selectedBionicModel[0] = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        performanceMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!syncingUi[0]) selectedPerformanceMode[0] = isChecked;
        });
        syncUi.run();

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(layout);
        ContentDialog frameGenDialog = new ContentDialog(this);
        FrameLayout dialogFrame = frameGenDialog.findViewById(R.id.FrameLayout);
        dialogFrame.setVisibility(View.VISIBLE);
        int dialogHeight = Math.max(
                padding * 5,
                Math.min(
                        getResources().getDisplayMetrics().heightPixels - padding * 12,
                        Math.round(getResources().getDisplayMetrics().density * 480.0f)));
        dialogFrame.addView(scrollView, new FrameLayout.LayoutParams(
                AppUtils.getPreferredDialogWidth(this),
                dialogHeight));
        frameGenDialog.setTitle("Framegen Advanced");
        frameGenDialog.setOnConfirmCallback(() -> {
                    int selectedMultiplier = FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0])
                            ? selectedNativeMultiplier[0]
                            : FrameGenManager.BACKEND_BIONIC_FG.equals(selectedBackend[0])
                                    ? selectedBionicMultiplier[0] : selectedLsfgMultiplier[0];
                    if (shortcut != null) {
                        shortcut.setFrameGenBackend(selectedBackend[0]);
                        shortcut.setLsfgMultiplier(selectedLsfgMultiplier[0]);
                        shortcut.setLsfgFlowScale(selectedLsfgFlowScale[0]);
                        shortcut.setLsfgPerformanceMode(selectedPerformanceMode[0]);
                        shortcut.setLsfgEnabled(FrameGenManager.BACKEND_LSFG_VK.equals(selectedBackend[0])
                                && selectedLsfgMultiplier[0] >= 2);
                        shortcut.setBionicFgMultiplier(selectedBionicMultiplier[0]);
                        shortcut.setBionicFgFlowScale(selectedBionicFlowScale[0]);
                        shortcut.setBionicFgModel(selectedBionicModel[0]);
                        shortcut.setNativeFgMultiplier(selectedNativeMultiplier[0]);
                        shortcut.setNativeFgSmoothing(selectedNativeSmoothing[0]);
                        shortcut.saveData();
                        if (selectedMultiplier >= 2) FrameGenManager.ensureRuntimeInstalled(this, shortcut);
                        FrameGenManager.writeConfig(shortcut);
                    } else {
                        container.setFrameGenBackend(selectedBackend[0]);
                        container.setLsfgMultiplier(selectedLsfgMultiplier[0]);
                        container.setLsfgFlowScale(selectedLsfgFlowScale[0]);
                        container.setLsfgPerformanceMode(selectedPerformanceMode[0]);
                        container.setLsfgEnabled(FrameGenManager.BACKEND_LSFG_VK.equals(selectedBackend[0])
                                && selectedLsfgMultiplier[0] >= 2);
                        container.setBionicFgMultiplier(selectedBionicMultiplier[0]);
                        container.setBionicFgFlowScale(selectedBionicFlowScale[0]);
                        container.setBionicFgModel(selectedBionicModel[0]);
                        container.setNativeFgMultiplier(selectedNativeMultiplier[0]);
                        container.setNativeFgSmoothing(selectedNativeSmoothing[0]);
                        container.saveData();
                        if (selectedMultiplier >= 2) FrameGenManager.ensureRuntimeInstalled(this, container);
                        FrameGenManager.writeConfig(container);
                    }
                    if (FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0])
                            && xServerView != null
                            && xServerView.getRenderer() instanceof VulkanRenderer) {
                        ((VulkanRenderer) xServerView.getRenderer())
                                .setFrameGenerationSmoothing(selectedNativeSmoothing[0]);
                    }
                    if (onSaved != null) onSaved.run();
                    boolean appliesLive = FrameGenManager.BACKEND_NATIVE_FG.equals(selectedBackend[0])
                            || (FrameGenManager.BACKEND_BIONIC_FG.equals(activeFrameGenBackend)
                                    && activeFrameGenMultiplier >= 2);
                    AppUtils.showToast(this, appliesLive
                            ? "Framegen advanced settings saved"
                            : "Framegen settings saved. Relaunch the game to apply them.");
                });
        frameGenDialog.show();
    }

    private int getFrameGenMultiplier(String backend) {
        if (shortcut != null) {
            if (FrameGenManager.BACKEND_NATIVE_FG.equals(backend)) return shortcut.getNativeFgMultiplier();
            if (FrameGenManager.BACKEND_BIONIC_FG.equals(backend)) return shortcut.getBionicFgMultiplier();
            return shortcut.getLsfgMultiplier();
        }
        if (FrameGenManager.BACKEND_NATIVE_FG.equals(backend)) return container.getNativeFgMultiplier();
        if (FrameGenManager.BACKEND_BIONIC_FG.equals(backend)) return container.getBionicFgMultiplier();
        return container.getLsfgMultiplier();
    }

    private void persistFrameGenSelection(String backend, int multiplier, int bionicModel) {
        float flowScale = shortcut != null
                ? (FrameGenManager.BACKEND_BIONIC_FG.equals(backend)
                        ? shortcut.getBionicFgFlowScale() : shortcut.getLsfgFlowScale())
                : (FrameGenManager.BACKEND_BIONIC_FG.equals(backend)
                        ? container.getBionicFgFlowScale() : container.getLsfgFlowScale());
        boolean performanceMode = shortcut != null
                ? shortcut.getLsfgPerformanceMode() : container.getLsfgPerformanceMode();
        FrameGenQuickMenuHelper.Settings settings = new FrameGenQuickMenuHelper.Settings(
                backend, multiplier, flowScale, performanceMode, bionicModel);
        if (shortcut != null) {
            FrameGenQuickMenuHelper.applySettings(shortcut, settings);
            if (multiplier >= 2) FrameGenManager.ensureRuntimeInstalled(this, shortcut);
            FrameGenManager.writeConfig(shortcut);
        } else {
            FrameGenQuickMenuHelper.applySettings(container, settings);
            if (multiplier >= 2) FrameGenManager.ensureRuntimeInstalled(this, container);
            FrameGenManager.writeConfig(container);
        }
    }

    private void setupSidebarInputControls() {
        if (inputControlsView == null || inputControlsManager == null) return;

        Spinner spInputControlsProfile = findViewById(R.id.SPInputControlsProfile);
        Switch swShowTouchscreenControls = findViewById(R.id.SWShowTouchscreenControls);
        Switch swEnableTimeout = findViewById(R.id.SWEnableTouchscreenTimeout);
        Switch swEnableHaptics = findViewById(R.id.SWEnableTouchscreenHaptics);
        View btInputControlsSettings = findViewById(R.id.BTInputControlsSettings);
        SeekBar sbControlsOpacity = findViewById(R.id.SBControlsOpacity);

        if (spInputControlsProfile == null)
            return;

        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- " + getString(R.string.disabled) + " --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                    selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            ArrayAdapter<String> adapter = createSidebarSpinnerAdapter(profileItems.toArray(new String[0]));
            spInputControlsProfile.setAdapter(adapter);
            spInputControlsProfile.setSelection(selectedPosition, false);
        };
        loadProfileSpinner.run();

        if (swShowTouchscreenControls != null)
            swShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());
        if (swEnableTimeout != null)
            swEnableTimeout.setChecked(preferences.getBoolean("touchscreen_timeout_enabled", false));
        if (swEnableHaptics != null)
            swEnableHaptics.setChecked(preferences.getBoolean("touchscreen_haptics_enabled", false));

        if (sbControlsOpacity != null) {
            sbControlsOpacity.setValue(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY) * 100f);
            sbControlsOpacity.setOnValueChangeListener((sb, v) -> {
                float opacity = v / 100f;
                preferences.edit().putFloat("overlay_opacity", opacity).apply();
                inputControlsView.setOverlayOpacity(opacity);
                inputControlsView.invalidate();
            });
        }

        Runnable applySidebarInputControls = () -> {
            if (swShowTouchscreenControls != null) {
                boolean showControls = swShowTouchscreenControls.isChecked();
                inputControlsView.setShowTouchscreenControls(showControls);
                preferences.edit().putBoolean("show_touchscreen_controls_enabled", showControls).apply();
            }

            boolean isTimeoutEnabled = swEnableTimeout != null && swEnableTimeout.isChecked();
            boolean isHapticsEnabled = swEnableHaptics != null && swEnableHaptics.isChecked();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
            editor.putInt("selected_profile_index", spInputControlsProfile.getSelectedItemPosition() - 1);
            editor.apply();

            int position = spInputControlsProfile.getSelectedItemPosition();
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles();
            if (position > 0 && position - 1 < profiles.size()) {
                showInputControls(profiles.get(position - 1));
            } else {
                hideInputControls();
            }

            if (isTimeoutEnabled && inputControlsView.getVisibility() == View.VISIBLE) {
                startTouchscreenTimeout();
            } else if (touchpadView != null) {
                touchpadView.setOnTouchListener(null);
            }
        };

        spInputControlsProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applySidebarInputControls.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (swShowTouchscreenControls != null)
            swShowTouchscreenControls.setOnCheckedChangeListener((buttonView, isChecked) -> applySidebarInputControls.run());
        if (swEnableTimeout != null)
            swEnableTimeout.setOnCheckedChangeListener((buttonView, isChecked) -> applySidebarInputControls.run());
        if (swEnableHaptics != null)
            swEnableHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> applySidebarInputControls.run());

        if (btInputControlsSettings != null) {
            btInputControlsSettings.setOnClickListener(v -> {
                int position = spInputControlsProfile.getSelectedItemPosition();
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("edit_input_controls", true);
                intent.putExtra("selected_profile_id",
                        position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);
                editInputControlsCallback = () -> {
                    hideInputControls();
                    inputControlsManager.loadProfiles(true);
                    loadProfileSpinner.run();
                    applySidebarInputControls.run();
                };
                controlsEditorActivityResultLauncher.launch(intent);
            });
        }
    }

    private void showInputControlsDialog() {
        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);
        dialog.setTitle(R.string.input_controls);
        dialog.setIcon(R.drawable.icon_input_controls);

        final Spinner sProfile = dialog.findViewById(R.id.SProfile);

        dialog.getWindow().setBackgroundDrawableResource(
                isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sProfile.setPopupBackgroundResource(
                isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        int textColor = ContextCompat.getColor(this, isDarkMode ? R.color.white : R.color.black);
        ViewGroup dialogViewGroup = (ViewGroup) dialog.getWindow().getDecorView().findViewById(android.R.id.content);
        setTextColorForDialog(dialogViewGroup, textColor);

        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- " + getString(R.string.disabled) + " --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                    selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));
            sProfile.setSelection(selectedPosition);
        };
        loadProfileSpinner.run();

        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);
        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());

        final CheckBox cbEnableTimeout = dialog.findViewById(R.id.CBEnableTimeout);
        cbEnableTimeout.setChecked(preferences.getBoolean("touchscreen_timeout_enabled", false));

        final CheckBox cbEnableHaptics = dialog.findViewById(R.id.CBEnableHaptics);
        cbEnableHaptics.setChecked(preferences.getBoolean("touchscreen_haptics_enabled", false));

        final Runnable updateProfile = () -> {
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            } else
                hideInputControls();
        };

        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {
            int position = sProfile.getSelectedItemPosition();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("edit_input_controls", true);
            intent.putExtra("selected_profile_id",
                    position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);
            editInputControlsCallback = () -> {
                hideInputControls();
                inputControlsManager.loadProfiles(true);
                loadProfileSpinner.run();
                updateProfile.run();
            };
            controlsEditorActivityResultLauncher.launch(intent);
        });

        dialog.setOnConfirmCallback(() -> {
            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());
            boolean isTimeoutEnabled = cbEnableTimeout.isChecked();
            boolean isHapticsEnabled = cbEnableHaptics.isChecked();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
            editor.apply();

            if (isTimeoutEnabled) {
                startTouchscreenTimeout(); 
            } else {
                touchpadView.setOnTouchListener(null); 
            }
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            } else
                hideInputControls();
            updateProfile.run();
        });

        dialog.setOnCancelCallback(updateProfile::run);

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void simulateConfirmInputControlsDialog() {

        boolean isShowTouchscreenControls = preferences.getBoolean("show_touchscreen_controls_enabled", false); 

        inputControlsView.setShowTouchscreenControls(isShowTouchscreenControls);

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        boolean isHapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", false);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
        editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
        editor.apply();

        int selectedProfileIndex = preferences.getInt("selected_profile_index", -1); 

        if (selectedProfileIndex >= 0 && selectedProfileIndex < inputControlsManager.getProfiles().size()) {

            ControlsProfile profile = inputControlsManager.getProfiles().get(selectedProfileIndex);
            showInputControls(profile);
        } else {

            hideInputControls();
        }

        if (isTimeoutEnabled && inputControlsView.getVisibility() == View.VISIBLE) {
            startTouchscreenTimeout(); 
        } else {
            touchpadView.setOnTouchListener(null); 
        }

        Log.d("XServerDisplayActivity", "Input controls simulated confirmation executed.");
    }

    private void startTouchscreenTimeout() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }

        if (DISABLE_TOUCHSCREEN_AUTO_HIDE) {
            Log.d("XServerDisplayActivity", "Touchscreen auto-hide disabled; controls remain visible.");
            if (touchpadView != null) {
                touchpadView.setOnTouchListener(null);
            }
            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.VISIBLE);
            }
            return;
        }

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

        if (isTimeoutEnabled) {
            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.VISIBLE);
            }
            Log.d("XServerDisplayActivity", "Timeout is enabled, setting up timeout logic.");

            touchpadView.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    if (inputControlsView != null && inputControlsView.getProfile() != null) {
                        inputControlsView.setVisibility(View.VISIBLE);
                    }

                    timeoutHandler.removeCallbacks(hideControlsRunnable);
                    timeoutHandler.postDelayed(hideControlsRunnable, 5000);
                }

                return false;
            });

            timeoutHandler.postDelayed(hideControlsRunnable, 5000);
        } else {
            Log.d("XServerDisplayActivity", "Timeout is disabled, controls will stay visible.");

            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.VISIBLE);
            }
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            touchpadView.setOnTouchListener(null);
        }
    }

    private void showInputControls(ControlsProfile profile) {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }

        inputControlsView.setProfile(profile);
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);

        inputControlsView.invalidate();
        winHandler.sendGamepadState();
    }

    private void hideInputControls() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }

        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
        winHandler.sendGamepadState();
    }

    private void extractGraphicsDriverFiles() {
        String adrenoToolsDriverId = graphicsDriverConfig.get("version");

        Log.d("GraphicsDriverExtraction", "Adrenotools DriverID: " + adrenoToolsDriverId);

        File rootDir = imageFs.getRootDir();

        if (dxwrapper.contains("dxvk") || dxwrapper.contains("vegas")) {
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
            String version = dxwrapperConfig.get("version");
            if ("1.11.1-sarek".equals(version)) {
                Log.d("GraphicsDriverExtraction", "Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass");
                envVars.put("WRAPPER_NO_PATCH_OPCONSTCOMP", "1");
            }
        } else {
            WineD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
        }

        boolean useDRI3 = preferences.getBoolean("use_dri3", true);
        if (!useDRI3) {
            envVars.put("MESA_VK_WSI_DEBUG", "sw");
        }

        envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/wrapper_icd.aarch64.json");
        envVars.put("GALLIUM_DRIVER", "zink");

        if (firstTimeBoot || forceGraphicsDriverExtraction) {
            String graphicsDriverArchive = resolveGraphicsDriverArchiveName();
            Log.d("XServerDisplayActivity", "Extracting graphics driver libs from " + graphicsDriverArchive);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                    "graphics_driver/" + graphicsDriverArchive + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "layers" + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/extra_libs" + ".tzst",
                    rootDir);
        }

        if (!"System".equals(adrenoToolsDriverId)) {
            AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
            adrenotoolsManager.setDriverById(envVars, imageFs, adrenoToolsDriverId);
        }

        String vulkanVersion = graphicsDriverConfig.get("vulkanVersion");
        String vulkanVersionPatch = GPUInformation.getVulkanVersion(adrenoToolsDriverId, this).split("\\.")[2];
        vulkanVersion = vulkanVersion + "." + vulkanVersionPatch;
        envVars.put("WRAPPER_VK_VERSION", vulkanVersion);

        String blacklistedExtensions = graphicsDriverConfig.get("blacklistedExtensions");
        envVars.put("WRAPPER_EXTENSION_BLACKLIST", blacklistedExtensions);

        String gpuName = graphicsDriverConfig.get("gpuName");
        String dxvkVersion = dxwrapperConfig.get("version");
        if (!gpuName.equals("Device") && !dxvkVersion.equals("1.11.1-sarek")) {
            envVars.put("WRAPPER_DEVICE_NAME", gpuName);
            envVars.put("WRAPPER_DEVICE_ID", WineD3DConfigDialog.getDeviceIdFromGPUName(this, gpuName));
            envVars.put("WRAPPER_VENDOR_ID", WineD3DConfigDialog.getVendorIdFromGPUName(this, gpuName));
        }

        String maxDeviceMemory = graphicsDriverConfig.get("maxDeviceMemory");
        if (maxDeviceMemory != null && Integer.parseInt(maxDeviceMemory) > 0)
            envVars.put("WRAPPER_VMEM_MAX_SIZE", maxDeviceMemory);

        String presentMode = graphicsDriverConfig.get("presentMode");
        if (presentMode.contains("immediate")) {
            envVars.put("WRAPPER_MAX_IMAGE_COUNT", "1");
        }
        envVars.put("MESA_VK_WSI_PRESENT_MODE", presentMode);

        String resourceType = graphicsDriverConfig.get("resourceType");
        envVars.put("WRAPPER_RESOURCE_TYPE", resourceType);

        String syncFrame = graphicsDriverConfig.get("syncFrame");
        if (syncFrame.equals("1"))
            envVars.put("MESA_VK_WSI_DEBUG", "forcesync");

        String disablePresentWait = graphicsDriverConfig.get("disablePresentWait");
        envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", disablePresentWait);

        boolean isWrapperGamenative = "wrapper-gamenative".equalsIgnoreCase(graphicsDriver);
        int vendorId = GPUInformation.getVendorID(null, null);
        boolean isAdreno = vendorId == 0x5143;
        boolean isXclipse = vendorId == 0x144D;
        boolean excludeBcnCompute = isAdreno || (isWrapperGamenative && isXclipse);
        String timelineSemaphores = graphicsDriverConfig.get("timelineSemaphores");
        if ("1".equals(timelineSemaphores)) {
            envVars.remove("DXVK_DISABLE_TIMELINE_SEMAPHORES");
        } else {
            envVars.put("DXVK_DISABLE_TIMELINE_SEMAPHORES", "1");
        }

        String tuDebugSysmem = graphicsDriverConfig.get("tuDebugSysmem");
        envVars.put("TU_DEBUG", "1".equals(tuDebugSysmem) ? "noconform,sysmem" : "noconform");

        String mesaGlthread = graphicsDriverConfig.get("mesaGlthread");
        envVars.put("mesa_glthread", "1".equals(mesaGlthread) ? "true" : "false");

        String bcnEmulation = graphicsDriverConfig.get("bcnEmulation");
        String bcnEmulationType = graphicsDriverConfig.get("bcnEmulationType");

        switch (bcnEmulation) {
            case "auto" -> {
                if (bcnEmulationType.equals("compute") && !excludeBcnCompute) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "1");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "3");
            }
            case "full" -> {
                if (bcnEmulationType.equals("compute") && !excludeBcnCompute) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "0");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "2");
            }
            case "none" -> envVars.put("WRAPPER_EMULATE_BCN", "0");
            default -> envVars.put("WRAPPER_EMULATE_BCN", "1");
        }

        String bcnEmulationCache = graphicsDriverConfig.get("bcnEmulationCache");
        envVars.put("WRAPPER_USE_BCN_CACHE", bcnEmulationCache);

        if (isDefaultWrapperDirectRgbaMode()) {
            // Pipetto a1dfbdec: request RGBA8 buffers so SurfaceFlinger can present game frames
            // directly without the ASR BGRA->RGBA compatibility blit.
            envVars.put("WRAPPER_SURFACE_FORMAT", "rgba8");
        }

        if (!vkbasaltConfig.isEmpty()) {
            envVars.put("ENABLE_VKBASALT", "1");
            envVars.put("VKBASALT_CONFIG", vkbasaltConfig);
        }
    }

    private String resolveGraphicsDriverArchiveName() {
        if (graphicsDriver == null || graphicsDriver.isEmpty() || graphicsDriver.equals("wrapper")) {
            return "wrapper";
        }
        if (graphicsDriver.startsWith("wrapper-v2")) {
            return "wrapper-gamenative";
        }
        if (graphicsDriver.startsWith("wrapper-original")) {
            return "wrapper-original";
        }
        if (graphicsDriver.startsWith("wrapper-leegao")) {
            return "wrapper-leegao";
        }
        if (graphicsDriver.startsWith("wrapper-legacy")) {
            return "wrapper-legacy";
        }
        if (graphicsDriver.startsWith("wrapper-gamenative")) {
            return "wrapper-gamenative";
        }
        return "wrapper";
    }

    private boolean isDefaultWrapperDirectRgbaMode() {
        String rendererType = shortcut != null ? shortcut.getRenderer()
                : (container != null ? container.getRenderer() : "vulkan");
        return "surfaceflinger".equalsIgnoreCase(rendererType)
                && "wrapper".equals(resolveGraphicsDriverArchiveName());
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        boolean handledByWinHandler = false;
        boolean handledByTouchpadView = false;

        if (winHandler != null) {
            handledByWinHandler = winHandler.onGenericMotionEvent(event);
            if (handledByWinHandler) {

            }
        }

        if (touchpadView != null) {
            handledByTouchpadView = touchpadView.onExternalMouseEvent(event);
            if (handledByTouchpadView) {

            }
        }

        boolean handledBySuper = super.dispatchGenericMotionEvent(event);
        if (!handledBySuper) {

        }

        return handledByWinHandler || handledByTouchpadView || handledBySuper;
    }

    private static final int RECAPTURE_DELAY_MS = 10000; 

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE || event.getKeyCode() == KeyEvent.KEYCODE_HOME
                    || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT) {
                boolean handled = inputControlsView.onKeyEvent(event)
                        || (winHandler != null && winHandler.onKeyEvent(event))
                                && (xServer != null && xServer.keyboard.onKeyEvent(event));
                return true;
            }
        }

        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event)
                && xServer.keyboard.onKeyEvent(event)) ||
                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }

    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    private static final String TAG = "DXWrapperExtraction";

    private void extractDXWrapperFiles(String dxwrapper) {
        final String[] dlls = { "d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll",
                "d3d8.dll", "d3d9.dll", "dxgi.dll", "ddraw.dll", "d3dimm.dll" };

        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");

        if (dxwrapper.contains("dxvk")) {
            Log.d(TAG, "Extracting DXVK wrapper files, version: " + dxwrapper);

            String dxvkWrapper = dxwrapper.split(";")[0];
            String vkd3dWrapper = dxwrapper.split(";")[1];
            String ddrawrapper = dxwrapper.split(";")[2];

            ContentProfile dxvkProfile = contentsManager.getProfileByEntryName(dxvkWrapper);
            if (dxvkProfile != null) {
                Log.d(TAG, "Applying user-defined DXVK content profile: " + dxvkWrapper);
                contentsManager.applyContent(dxvkProfile);
            } else {
                Log.d(TAG, "Extracting fallback DXVK .tzst archive: " + dxvkWrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxvkWrapper + ".tzst",
                        windowsDir, onExtractFileListener);

                if (compareVersion(dxvkWrapper, "2.4") < 0) {
                    Log.d(TAG, "Extracting d8vk as part of DXVK version " + dxvkWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "dxwrapper/d8vk-" + DefaultVersion.D8VK + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            if (vkd3dWrapper.contains("None")) {
                Log.d(TAG, "No VKD3D has been selected, restoring original d3d12");
                restoreOriginalDllFiles(new String[] { "d3d12.dll", "d3d12core.dll" });
            } else {
                ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
                if (vkd3dProfile != null) {
                    Log.d(TAG, "Applying user-defined VKD3D content profile: " + vkd3dWrapper);
                    contentsManager.applyContent(vkd3dProfile);
                } else {
                    Log.d(TAG, "Extracting fallback VKD3D .tzst archive: " + vkd3dWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "dxwrapper/" + vkd3dWrapper + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            Log.d(TAG, "Extracting nglide wrapper");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir,
                    onExtractFileListener);

            if (ddrawrapper.equalsIgnoreCase("none")) {
                Log.d(TAG, "No DDRaw wrapper has been selected, restoring original ddraw files");
                restoreOriginalDllFiles(new String[] { "ddraw.dll", "d3dimm.dll" });
            } else {
                if (ddrawrapper.equals("cnc-ddraw"))
                    envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");

                Log.d(TAG, "Extracting ddrawrapper " + ddrawrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst",
                        windowsDir, onExtractFileListener);
            }

            Log.d(TAG, "Finished extraction of DXVK wrapper files, version: " + dxwrapper);
        } else if (dxwrapper.contains("wined3d")) {
            Log.d(TAG, "Restoring original DLL files for wined3d.");
            restoreOriginalDllFiles(dlls);
        } else if (dxwrapper.contains("vegas")) {
            Log.d(TAG, "Extracting VEGAS wrapper files, version: " + dxwrapper);

            String[] parts = dxwrapper.split(";");
            String vegasWrapper = parts.length > 0 ? parts[0] : "vegas-" + DefaultVersion.getVegasDefault();
            String vkd3dWrapper = parts.length > 1 ? parts[1] : "None";
            String ddrawrapper = parts.length > 2 ? parts[2] : "none";

            ContentProfile vegasProfile = contentsManager.getProfileByEntryName(vegasWrapper);
            if (vegasProfile == null && vegasWrapper.startsWith("vegas-")) {
                String requestedVersion = vegasWrapper.substring("vegas-".length());
                for (ContentProfile profile : contentsManager.getInstalledProfiles(
                        ContentProfile.ContentType.CONTENT_TYPE_VEGAS)) {
                    String installedVersion = profile.verName;
                    if (installedVersion != null && installedVersion.startsWith("vegas-")) {
                        installedVersion = installedVersion.substring("vegas-".length());
                    }
                    if (requestedVersion.equals(installedVersion)) {
                        vegasProfile = profile;
                        Log.d(TAG, "Found installed VEGAS content profile: "
                                + ContentsManager.getEntryName(profile));
                        break;
                    }
                }
            }
            if (vegasProfile != null) {
                Log.d(TAG, "Applying user-defined VEGAS content profile: " + vegasWrapper);
                contentsManager.applyContent(vegasProfile);
            } else {
                Log.d(TAG, "Extracting fallback VEGAS .tzst archive: " + vegasWrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + vegasWrapper + ".tzst",
                        windowsDir, onExtractFileListener);
            }

            if (vkd3dWrapper.contains("None")) {
                Log.d(TAG, "No VKD3D has been selected for VEGAS, restoring original d3d12");
                restoreOriginalDllFiles(new String[] { "d3d12.dll", "d3d12core.dll" });
            } else {
                ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
                if (vkd3dProfile != null) {
                    Log.d(TAG, "Applying user-defined VKD3D content profile: " + vkd3dWrapper);
                    contentsManager.applyContent(vkd3dProfile);
                } else {
                    Log.d(TAG, "Extracting fallback VKD3D .tzst archive: " + vkd3dWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "dxwrapper/" + vkd3dWrapper + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            Log.d(TAG, "Extracting nglide wrapper");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir,
                    onExtractFileListener);

            if (ddrawrapper.equalsIgnoreCase("none")) {
                Log.d(TAG, "No DDRaw wrapper has been selected, restoring original ddraw files");
                restoreOriginalDllFiles(new String[] { "ddraw.dll", "d3dimm.dll" });
            } else {
                if (ddrawrapper.equals("cnc-ddraw"))
                    envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");

                Log.d(TAG, "Extracting ddrawrapper " + ddrawrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst",
                        windowsDir, onExtractFileListener);
            }

            Log.d(TAG, "Finished extraction of VEGAS wrapper files, version: " + dxwrapper);
        }
    }

    private static int compareVersion(String varA, String varB) {
        int[] a = parseSemverLoose(varA);
        int[] b = parseSemverLoose(varB);

        if (a[0] != b[0])
            return a[0] - b[0];
        if (a[1] != b[1])
            return a[1] - b[1];
        return a[2] - b[2];
    }

    private static final Pattern SEMVER_LOOSE = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static int[] parseSemverLoose(String s) {
        if (s == null)
            return new int[] { 0, 0, 0 };

        Matcher m = SEMVER_LOOSE.matcher(s);

        String g1 = null, g2 = null, g3 = null;
        while (m.find()) {
            g1 = m.group(1);
            g2 = m.group(2);
            g3 = m.group(3);
        }

        if (g1 == null || g2 == null) {
            return new int[] { 0, 0, 0 };
        }

        int major = safeParseInt(g1);
        int minor = safeParseInt(g2);
        int patch = safeParseInt(g3);
        return new int[] { major, minor, patch };
    }

    private static int safeParseInt(String s) {
        if (s == null || s.isEmpty())
            return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void extractWinComponentFiles() {
        Log.d("XServerDisplayActivity", "Extracting WinComponents");
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(
                    FileUtils.readString(this, "wincomponents/wincomponents.json"));
            ArrayList<String> dlls = new ArrayList<>();
            String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents())
                    : container.getWinComponents();

            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(
                    container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                if (wincomponent[1].equals(oldWinComponentsIter.next()[1]) && !firstTimeBoot)
                    continue;
                String identifier = wincomponent[0];
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "wincomponents/" + identifier + ".tzst", windowsDir, onExtractFileListener);
                } else {
                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
                    for (int i = 0; i < dlnames.length(); i++) {
                        String dlname = dlnames.getString(i);
                        dlls.add(!dlname.endsWith(".exe") ? dlname + ".dll" : dlname);
                    }
                }
                Log.d("XServerDisplayActivity",
                        "Setting wincomponent " + identifier + " to " + String.valueOf(useNative));
                WineUtils.overrideWinComponentDlls(this, container, identifier, useNative);
                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative, this);
            }

            if (!dlls.isEmpty())
                restoreOriginalDllFiles(dlls.toArray(new String[0]));
        } catch (JSONException e) {
        }
    }

    private void restoreOriginalDllFiles(final String... dlls) {
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
        File system32dlls = null;
        File syswow64dlls = null;

        if (wineInfo.isArm64EC())
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/aarch64-windows");
        else
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/x86_64-windows");

        syswow64dlls = new File(imageFs.getWinePath() + "/lib/wine/i386-windows");

        for (String dll : dlls) {
            File srcFile = new File(system32dlls, dll);
            File dstFile = new File(windowsDir, "system32/" + dll);
            FileUtils.copy(srcFile, dstFile);
            srcFile = new File(syswow64dlls, dll);
            dstFile = new File(windowsDir, "syswow64/" + dll);
            FileUtils.copy(srcFile, dstFile);
        }
    }

    private String getWineStartCommand() {
        // Initialize overrideEnvVars if not already done
        EnvVars envVars = getOverrideEnvVars();

        // Define default arguments
        String args = "";

        if (shortcut != null) {
            String execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " " + execArgs : "";

            if (shortcut.path.endsWith(".lnk")) {
                args += "\"" + shortcut.path + "\"" + execArgs;
            } else {
                String fullPath = shortcut.path.replace("\"", "");
                String exeDir;
                String filename;

                if (fullPath.contains("\\")) {
                    int lastSlash = fullPath.lastIndexOf("\\");
                    if (lastSlash != -1) {
                        exeDir = fullPath.substring(0, lastSlash);
                        filename = fullPath.substring(lastSlash + 1);
                    } else {
                        exeDir = "D:\\";
                        filename = fullPath;
                    }
                } else {
                    exeDir = FileUtils.getDirname(fullPath);
                    filename = FileUtils.getName(fullPath);
                }

                int dotIndex = filename.lastIndexOf(".");
                int spaceIndex = (dotIndex != -1) ? filename.indexOf(" ", dotIndex) : -1;

                if (spaceIndex != -1) {
                    execArgs = filename.substring(spaceIndex + 1) + execArgs;
                    filename = filename.substring(0, spaceIndex);
                }

                args += "/dir " + StringUtils.escapeDOSPath(exeDir) + " \"" + filename + "\"" + execArgs;
            }
        } else {
            // Append EXTRA_EXEC_ARGS from overrideEnvVars if it exists
            if (envVars.has("EXTRA_EXEC_ARGS")) {
                args += " " + envVars.get("EXTRA_EXEC_ARGS");
                envVars.remove("EXTRA_EXEC_ARGS"); // Remove the key after use
            } else {
                args += "\"wfm.exe\"";
            }
        }
        // Construct the final command
        String command = "winhandler.exe " + args;

        return command;
    }

    private String getExecutable() {
        String filename = "wfm.exe";
        if (shortcut != null && shortcut.path != null) {
            String cleanPath = shortcut.path.replace("\"", "");
            int lastSlash = cleanPath.lastIndexOf('/');
            int lastBackslash = cleanPath.lastIndexOf('\\');
            int lastSeparator = Math.max(lastSlash, lastBackslash);
            if (lastSeparator != -1) {
                filename = cleanPath.substring(lastSeparator + 1);
            } else {
                filename = cleanPath;
            }
        }
        return filename;
    }

    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) {
            overrideEnvVars = new EnvVars();
        }
        return overrideEnvVars;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = imageFs.getRootDir();
            File userRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals("alsa")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                } else if (audioDriver.equals("pulseaudio")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = imageFs.getRootDir();
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "container_pattern_common.tzst", rootDir);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst",
                new File(getFilesDir(), "pulseaudio"));
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("graphicsDriver", null);
        container.putExtra("desktopTheme", null);
    }

    private boolean resolvedPerformanceBoolean(String key) {
        boolean global = com.winlator.cmod.perf.PerformanceSettings.INSTANCE.globalDefault(key);
        if (shortcut == null || !shortcut.hasExtra(key)) return global;
        String value = shortcut.getExtra(key, global ? "1" : "0");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private void applyEffectiveRootPerformance() {
        if (!com.winlator.cmod.perf.RootManager.INSTANCE.isGranted()) return;
        HashMap<String, Boolean> effective = new HashMap<>();
        for (String key : com.winlator.cmod.perf.PerfRootApplier.INSTANCE.getROOT_KEYS()) {
            effective.put(key, resolvedPerformanceBoolean(key));
        }
        new Thread(
                () -> com.winlator.cmod.perf.PerfRootApplier.INSTANCE.applyEffective(effective),
                "perf-root-launch").start();
    }

    private void applyPerformanceKeyLive(String key, boolean enabled) {
        performanceControlsReverted = false;
        if (com.winlator.cmod.perf.PerformanceSettings.KEY_SUSTAINED.equals(key)) {
            getWindow().setSustainedPerformanceMode(enabled);
            return;
        }
        if (com.winlator.cmod.perf.PerformanceSettings.KEY_PRIORITY.equals(key)) {
            new Thread(() -> {
                if (enabled) {
                    com.winlator.cmod.perf.PerfPriority.INSTANCE.boost(
                            GuestProgramLauncherComponent.getPid());
                } else {
                    com.winlator.cmod.perf.PerfPriority.INSTANCE.restore();
                }
            }, "perf-priority-toggle").start();
            return;
        }
        if (com.winlator.cmod.perf.PerformanceSettings.KEY_BIG_CORES.equals(key)) {
            if (enabled) {
                String bigList = com.winlator.cmod.perf.CpuTopology.INSTANCE.detectBigCoreCpuList();
                if (bigList == null || bigList.isEmpty()) return;
                taskAffinityMask = (short) ProcessHelper.getAffinityMask(bigList);
                taskAffinityMaskWoW64 = taskAffinityMask;
            } else {
                String cpuList = shortcut != null
                        ? shortcut.getExtra("cpuList", container.getCPUList(true))
                        : container.getCPUList(true);
                taskAffinityMask = (short) ProcessHelper.getAffinityMask(cpuList);
                taskAffinityMaskWoW64 = shortcut != null
                        ? taskAffinityMask
                        : (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));
            }
            reapplyBigCoresToRunningGuest(enabled);
            return;
        }
        if (com.winlator.cmod.perf.PerfRootApplier.INSTANCE.getROOT_KEYS().contains(key)) {
            new Thread(
                    () -> com.winlator.cmod.perf.PerfRootApplier.INSTANCE.apply(key, enabled),
                    "perf-root-toggle").start();
        }
    }

    private void reapplyBigCoresToRunningGuest(boolean enabled) {
        if (winHandler == null) return;
        if (!enabled && bigCoreAffinitySnapshot.isEmpty()) return;
        final int bigMask;
        if (enabled) {
            String bigList = com.winlator.cmod.perf.CpuTopology.INSTANCE.detectBigCoreCpuList();
            if (bigList == null || bigList.isEmpty()) return;
            bigMask = ProcessHelper.getAffinityMask(bigList);
        } else {
            bigMask = 0;
        }

        String cpuList = shortcut != null
                ? shortcut.getExtra("cpuList", container.getCPUList(true))
                : container.getCPUList(true);
        final int fallbackMask = ProcessHelper.getAffinityMask(
                cpuList != null && !cpuList.isEmpty()
                        ? cpuList
                        : Container.getFallbackCPUList());

        final OnGetProcessInfoListener previous = winHandler.getOnGetProcessInfoListener();
        final ArrayList<ProcessInfo> collected = new ArrayList<>();
        winHandler.setOnGetProcessInfoListener((index, count, info) -> {
            if (index == 0) collected.clear();
            if (info != null && info.pid > 0) collected.add(info);
            if (count == 0 || index == count - 1) {
                for (ProcessInfo process : collected) {
                    if (enabled) {
                        if (!bigCoreAffinitySnapshot.containsKey(process.pid)) {
                            bigCoreAffinitySnapshot.put(process.pid, process.affinityMask);
                        }
                        winHandler.setProcessAffinity(process.pid, bigMask);
                    } else {
                        Integer original = bigCoreAffinitySnapshot.get(process.pid);
                        winHandler.setProcessAffinity(
                                process.pid,
                                original != null ? original : fallbackMask);
                    }
                }
                if (!enabled) bigCoreAffinitySnapshot.clear();
                winHandler.setOnGetProcessInfoListener(previous);
            }
        });
        winHandler.listProcesses();
    }

    private void stopAndRevertPerformanceControls() {
        if (performanceControlsReverted) return;
        performanceControlsReverted = true;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        com.winlator.cmod.perf.TempWatchdog.INSTANCE.stop();
        com.winlator.cmod.perf.PerfPriority.INSTANCE.restore();
        getWindow().setSustainedPerformanceMode(false);
        try {
            com.winlator.cmod.perf.PerfRevertRegistry.INSTANCE.revertAll();
        } catch (Throwable throwable) {
            Log.w("XServerDisplayActivity", "Performance-state revert failed", throwable);
        }
    }

    private void assignTaskAffinity(Window window) {
        if (taskAffinityMask == 0 || taskAffinityMaskWoW64 == 0)
            return;
        int processId = window.getProcessId();
        String className = window.getClassName();
        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

        if (processId > 0) {
            winHandler.setProcessAffinity(processId, processAffinity);
        } else if (!className.isEmpty()) {
            winHandler.setProcessAffinity(window.getClassName(), processAffinity);
        }
    }

    private void changeFrameRatingVisibility(Window window, Property property) {

        String propName = (property != null) ? property.nameAsString() : null;

        if (property != null) {
            if (activeRendererWindowId == -1 && propName.contains("_MESA_DRV")) {
                activeRendererWindowId = window.id;
            }

            if (propName.contains("_MESA_DRV_ENGINE_NAME")
                    && (activeRendererWindowId == -1 || window.id == activeRendererWindowId)) {
                lastRendererName = property.toString();
            }
        } else if (activeRendererWindowId != -1 && window.id == activeRendererWindowId) {

            activeRendererWindowId = -1;
            lastRendererName = null;
        }

        if (classicHud == null && modernHud == null) return;

        if (property != null) {
            if (frameRatingWindowId == -1 && propName.contains("_MESA_DRV")) {
                frameRatingWindowId = window.id;
                if (xServerView != null) xServerView.getRenderer().setFpsWindowId(window.id);
                Log.d("XServerDisplayActivity", "Showing hud for Window " + window.getName());
                if (classicHud != null) classicHud.update();

                if (modernHud != null) {
                    final String nameToPass = lastRendererName;
                    runOnUiThread(() -> modernHud.onRendererDetected(nameToPass));
                }
            }

            if (propName.contains("_MESA_DRV_ENGINE_NAME") && window.id == frameRatingWindowId) {
                String rendererName = property.toString();
                if (classicHud != null) runOnUiThread(() -> classicHud.setRenderer(rendererName));
                if (modernHud != null) runOnUiThread(() -> modernHud.setRenderer(rendererName));
            }
            if (propName.contains("_MESA_DRV_GPU_NAME") && window.id == frameRatingWindowId) {
                String gpuName = property.toString();
                if (classicHud != null) runOnUiThread(() -> classicHud.setGpuName(gpuName));
                if (modernHud != null) runOnUiThread(() -> modernHud.setGpuName(gpuName));
            }
        } else if (frameRatingWindowId != -1 && window.id == frameRatingWindowId) {

            frameRatingWindowId = -1;
            if (xServerView != null) xServerView.getRenderer().setFpsWindowId(-1);
            Log.d("XServerDisplayActivity", "Hiding hud for Window " + window.getName());
            if (classicHud != null) runOnUiThread(() -> {
                classicHud.setVisibility(View.GONE);
                classicHud.reset();
            });
            if (modernHud != null) runOnUiThread(() -> modernHud.onRendererGone());
        }
    }

    public String getScreenEffectProfile() {
        return screenEffectProfile;
    }

    public void setScreenEffectProfile(String screenEffectProfile) {
        this.screenEffectProfile = screenEffectProfile;
    }

}
