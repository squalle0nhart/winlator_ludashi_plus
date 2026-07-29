package com.winlator.cmod.core;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.Locale;

public abstract class BionicFgManager {
    private static final String TAG = "BionicFgManager";
    private static final String ASSET_LIB = "bionic_fg/android_arm64_v8a/libbionic_fg.so";
    private static final String ASSET_MANIFEST = "bionic_fg/android_arm64_v8a/VkLayer_BIONIC_framegen.json";
    private static final String CONFIG_RELATIVE_PATH = ".config/bionic-fg/conf.toml";
    private static final String LAYER_RELATIVE_DIR = ".local/share/vulkan/implicit_layer.d";
    private static final String LIB_RELATIVE_DIR = ".local/lib";
    private static final String LIB_FILENAME = "libbionic_fg.so";
    private static final String MANIFEST_FILENAME = "VkLayer_BIONIC_framegen.json";
    private static final String VERSION_FILENAME = ".bionic_fg_runtime_version";
    private static final String RUNTIME_VERSION = "ludashi-bionicfg-layer-path-fastfail-20260728";

    private BionicFgManager() {}

    public static boolean isEnabled(Container container) {
        return container != null && container.getBionicFgMultiplier() >= 2;
    }

    public static boolean isEnabled(Shortcut shortcut) {
        return shortcut != null && shortcut.getBionicFgMultiplier() >= 2;
    }

    public static int multiplier(Container container) {
        return container != null ? container.getBionicFgMultiplier() : 0;
    }

    public static int multiplier(Shortcut shortcut) {
        return shortcut != null ? shortcut.getBionicFgMultiplier() : 0;
    }

    public static float flowScale(Container container) {
        return container != null ? container.getBionicFgFlowScale() : 0.80f;
    }

    public static float flowScale(Shortcut shortcut) {
        return shortcut != null ? shortcut.getBionicFgFlowScale() : 0.80f;
    }

    public static int model(Container container) {
        return container != null ? container.getBionicFgModel() : 0;
    }

    public static int model(Shortcut shortcut) {
        return shortcut != null ? shortcut.getBionicFgModel() : 0;
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
                throw new IllegalStateException("Failed to atomically stage Bionic-FG runtime");
            }
            FileUtils.copy(context, ASSET_MANIFEST, manifestFile);
            FileUtils.writeString(versionFile, RUNTIME_VERSION);
            FileUtils.chmod(libFile, 0755);
            FileUtils.chmod(manifestFile, 0644);
            FileUtils.chmod(versionFile, 0644);
            return libFile.isFile() && manifestFile.isFile();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install Bionic-FG runtime", t);
            return false;
        }
    }

    public static boolean ensureRuntimeInstalled(Context context, Shortcut shortcut) {
        return shortcut != null && ensureRuntimeInstalled(context, shortcut.container);
    }

    public static boolean writeConfig(Container container) {
        if (container == null || container.getRootDir() == null) return false;
        File configFile = configFile(container);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();

        boolean enabled = isEnabled(container);
        boolean ok = FileUtils.writeString(configFile, buildConfigToml(enabled, multiplier(container), flowScale(container), model(container)));
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean writeConfig(Shortcut shortcut) {
        if (shortcut == null || shortcut.container == null || shortcut.container.getRootDir() == null) return false;
        File configFile = configFile(shortcut.container);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();

        boolean enabled = isEnabled(shortcut);
        boolean ok = FileUtils.writeString(configFile, buildConfigToml(enabled, multiplier(shortcut), flowScale(shortcut), model(shortcut)));
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        if (container == null || envVars == null || container.getRootDir() == null) return false;
        clearLaunchEnv(envVars);

        File manifestFile = new File(container.getRootDir(), LAYER_RELATIVE_DIR + "/" + MANIFEST_FILENAME);
        if (!isEnabled(container) || !manifestFile.isFile()) {
            envVars.put("BIONIC_FG_DISABLE", "1");
            return false;
        }

        envVars.put("BIONIC_FG_ENABLE", "1");
        envVars.put("BIONIC_FG_CONFIG", configPath(container));
        envVars.put("BIONIC_FG_MULTIPLIER", multiplier(container));
        envVars.put("BIONIC_FG_FLOW_SCALE", String.format(Locale.US, "%.2f", flowScale(container)));
        envVars.put("BIONIC_FG_MODEL", model(container));
        appendImplicitLayerPath(envVars, layerDirPath(container));
        Log.i(TAG, "Bionic-FG armed with multiplier=" + multiplier(container)
                + " model=" + model(container) + " layerPath=" + layerDirPath(container));
        return true;
    }

    public static boolean applyLaunchEnv(Shortcut shortcut, EnvVars envVars) {
        if (shortcut == null || shortcut.container == null || envVars == null || shortcut.container.getRootDir() == null) return false;
        clearLaunchEnv(envVars);

        File manifestFile = new File(shortcut.container.getRootDir(), LAYER_RELATIVE_DIR + "/" + MANIFEST_FILENAME);
        if (!isEnabled(shortcut) || !manifestFile.isFile()) {
            envVars.put("BIONIC_FG_DISABLE", "1");
            return false;
        }

        envVars.put("BIONIC_FG_ENABLE", "1");
        envVars.put("BIONIC_FG_CONFIG", configPath(shortcut));
        envVars.put("BIONIC_FG_MULTIPLIER", multiplier(shortcut));
        envVars.put("BIONIC_FG_FLOW_SCALE", String.format(Locale.US, "%.2f", flowScale(shortcut)));
        envVars.put("BIONIC_FG_MODEL", model(shortcut));
        appendImplicitLayerPath(envVars, layerDirPath(shortcut));
        Log.i(TAG, "Bionic-FG armed with multiplier=" + multiplier(shortcut)
                + " model=" + model(shortcut) + " layerPath=" + layerDirPath(shortcut));
        return true;
    }

    public static void clearLaunchEnv(EnvVars envVars) {
        envVars.remove("BIONIC_FG_ENABLE");
        envVars.remove("BIONIC_FG_DISABLE");
        envVars.remove("BIONIC_FG_CONFIG");
        envVars.remove("BIONIC_FG_MULTIPLIER");
        envVars.remove("BIONIC_FG_FLOW_SCALE");
        envVars.remove("BIONIC_FG_MODEL");
        envVars.remove("BIONIC_FG_FPS_LIMIT");
        envVars.remove("VK_ADD_IMPLICIT_LAYER_PATH");
        envVars.remove("VK_IMPLICIT_LAYER_PATH");
    }

    public static String layerDirPath(Container container) {
        if (container == null || container.getRootDir() == null) return null;
        return new File(container.getRootDir(), LAYER_RELATIVE_DIR).getAbsolutePath();
    }

    public static String layerDirPath(Shortcut shortcut) {
        return shortcut == null ? null : layerDirPath(shortcut.container);
    }

    public static String configPath(Container container) {
        if (container == null || container.getRootDir() == null) return null;
        return configFile(container).getAbsolutePath();
    }

    public static String configPath(Shortcut shortcut) {
        return shortcut == null ? null : configPath(shortcut.container);
    }

    private static void appendImplicitLayerPath(EnvVars envVars, String layerPath) {
        if (layerPath == null || layerPath.isEmpty()) return;
        String current = envVars.get("VK_LAYER_PATH");
        envVars.put("VK_LAYER_PATH",
                current == null || current.isEmpty() ? layerPath : current + ":" + layerPath);
    }

    private static File configFile(Container container) {
        return new File(container.getRootDir(), CONFIG_RELATIVE_PATH);
    }

    private static String buildConfigToml(boolean enabled, int multiplier, float flowScale, int model) {
        StringBuilder builder = new StringBuilder();
        builder.append("version = 1\n\n");
        builder.append("[global]\n");
        builder.append("enabled = ").append(enabled ? "true" : "false").append('\n');
        builder.append("multiplier = ").append(enabled ? Math.max(2, Math.min(4, multiplier)) : 0).append('\n');
        builder.append("flow_scale = ").append(String.format(Locale.US, "%.2f", Math.max(0.2f, Math.min(1.0f, flowScale)))).append('\n');
        builder.append("model = ").append(Math.max(0, Math.min(4, model))).append('\n');
        return builder.toString();
    }
}
