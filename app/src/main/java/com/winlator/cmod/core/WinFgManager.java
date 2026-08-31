package com.winlator.cmod.core;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Locale;

public abstract class WinFgManager {
    private static final String TAG = "WinFgManager";
    private static final String ASSET_LIB = "win-fg/libwin_fg.so";
    private static final String ASSET_MANIFEST = "win-fg/VkLayer_win_framegen.json";
    private static final String CONFIG_RELATIVE_PATH = ".config/win-fg/conf.toml";
    private static final String LAYER_RELATIVE_DIR = ".local/share/vulkan/implicit_layer.d";
    private static final String LIB_RELATIVE_DIR = ".local/lib";
    private static final String LIB_FILENAME = "libwin_fg.so";
    private static final String MANIFEST_FILENAME = "VkLayer_win_framegen.json";
    private static final String VERSION_FILENAME = ".win_fg_runtime_version";
    private static final String RUNTIME_VERSION = "bannerlator-68b528d-win-fg-0096525";

    private WinFgManager() {}

    public static boolean isEnabled(Container container) {
        return container != null && container.getWinFgMultiplier() >= 2;
    }

    public static boolean isEnabled(Shortcut shortcut) {
        return shortcut != null && shortcut.getWinFgMultiplier() >= 2;
    }

    public static boolean ensureRuntimeInstalled(Context context, Container container) {
        if (context == null || container == null || container.getRootDir() == null) return false;

        File rootDir = container.getRootDir();
        File localLibDir = new File(rootDir, LIB_RELATIVE_DIR);
        File layerDir = new File(rootDir, LAYER_RELATIVE_DIR);
        File libFile = new File(localLibDir, LIB_FILENAME);
        File manifestFile = new File(layerDir, MANIFEST_FILENAME);
        File versionFile = new File(layerDir, VERSION_FILENAME);

        String installedVersion = versionFile.isFile() ? FileUtils.readString(versionFile).trim() : "";
        long assetSize = FileUtils.getSize(context, ASSET_LIB);
        boolean needsInstall = !RUNTIME_VERSION.equals(installedVersion)
                || !libFile.isFile()
                || libFile.length() != assetSize
                || !manifestFile.isFile();
        if (!needsInstall) return true;

        try {
            localLibDir.mkdirs();
            layerDir.mkdirs();
            File stagedLib = new File(localLibDir, LIB_FILENAME + ".staging");
            if (stagedLib.exists()) stagedLib.delete();
            FileUtils.copy(context, ASSET_LIB, stagedLib);
            if (!stagedLib.isFile() || stagedLib.length() != assetSize || !stagedLib.renameTo(libFile)) {
                stagedLib.delete();
                throw new IllegalStateException("Failed to atomically stage win-fg runtime");
            }
            FileUtils.copy(context, ASSET_MANIFEST, manifestFile);
            FileUtils.writeString(versionFile, RUNTIME_VERSION);
            FileUtils.chmod(libFile, 0755);
            FileUtils.chmod(manifestFile, 0644);
            FileUtils.chmod(versionFile, 0644);
            return libFile.isFile() && manifestFile.isFile();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install win-fg runtime", t);
            return false;
        }
    }

    public static boolean ensureRuntimeInstalled(Context context, Shortcut shortcut) {
        return shortcut != null && ensureRuntimeInstalled(context, shortcut.container);
    }

    public static boolean writeConfig(Container container) {
        if (container == null || container.getRootDir() == null) return false;
        return writeConfig(container, isEnabled(container),
                container.getWinFgFlowScale(), container.getWinFgModel());
    }

    public static boolean writeConfig(Shortcut shortcut) {
        if (shortcut == null || shortcut.container == null || shortcut.container.getRootDir() == null) return false;
        return writeConfig(shortcut.container, isEnabled(shortcut),
                shortcut.getWinFgFlowScale(), shortcut.getWinFgModel());
    }

    private static boolean writeConfig(Container container, boolean enabled, float flowScale, int model) {
        File configFile = new File(container.getRootDir(), CONFIG_RELATIVE_PATH);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();
        String toml = "# Written by Winlator (per-container win-fg)\n"
                + "enabled = " + (enabled ? "1" : "0") + "\n"
                + "multiplier = 2\n"
                + "flowScale = " + String.format(Locale.US, "%.2f", sanitizeFlowScale(flowScale)) + "\n"
                + "model = " + sanitizeModel(model) + "\n";
        boolean ok = FileUtils.writeString(configFile, toml);
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        if (container == null || envVars == null || container.getRootDir() == null) return false;
        clearLaunchEnv(envVars);
        File manifestFile = new File(container.getRootDir(), LAYER_RELATIVE_DIR + "/" + MANIFEST_FILENAME);
        // The device-proven 000279c runtime initializes while the selected backend is Off,
        // then hot-reloads conf.toml so Off -> On can apply without relaunching the game.
        if (!manifestFile.isFile()) {
            envVars.put("WIN_FG_DISABLE", "1");
            return false;
        }
        envVars.put("WIN_FG_ENABLE", "1");
        appendImplicitLayerPath(envVars, layerDirPath(container));
        Log.i(TAG, "win-fg armed with multiplier=" + container.getWinFgMultiplier()
                + " model=" + sanitizeModel(container.getWinFgModel()));
        return true;
    }

    public static boolean applyLaunchEnv(Shortcut shortcut, EnvVars envVars) {
        if (shortcut == null || shortcut.container == null || envVars == null
                || shortcut.container.getRootDir() == null) return false;
        clearLaunchEnv(envVars);
        File manifestFile = new File(shortcut.container.getRootDir(),
                LAYER_RELATIVE_DIR + "/" + MANIFEST_FILENAME);
        if (!manifestFile.isFile()) {
            envVars.put("WIN_FG_DISABLE", "1");
            return false;
        }
        envVars.put("WIN_FG_ENABLE", "1");
        appendImplicitLayerPath(envVars, layerDirPath(shortcut.container));
        Log.i(TAG, "win-fg armed with multiplier=" + shortcut.getWinFgMultiplier()
                + " model=" + sanitizeModel(shortcut.getWinFgModel()));
        return true;
    }

    public static void clearLaunchEnv(EnvVars envVars) {
        envVars.remove("WIN_FG_ENABLE");
        envVars.remove("WIN_FG_DISABLE");
        envVars.remove("WIN_FG_CONF");
        envVars.remove("WIN_FG_MULT");
        envVars.remove("WIN_FG_FLOWSCALE");
        envVars.remove("WIN_FG_MODEL");
    }

    private static String layerDirPath(Container container) {
        return new File(container.getRootDir(), LAYER_RELATIVE_DIR).getAbsolutePath();
    }

    private static void appendImplicitLayerPath(EnvVars envVars, String layerPath) {
        String current = envVars.get("VK_LAYER_PATH");
        if (current == null || current.isEmpty()) {
            envVars.put("VK_LAYER_PATH", layerPath);
            return;
        }
        for (String path : current.split(":")) {
            if (layerPath.equals(path)) return;
        }
        envVars.put("VK_LAYER_PATH", current + ":" + layerPath);
    }

    private static float sanitizeFlowScale(float flowScale) {
        return Math.max(0.25f, Math.min(1.0f, flowScale));
    }

    private static int sanitizeModel(int model) {
        return Math.max(3, Math.min(4, model));
    }
}
