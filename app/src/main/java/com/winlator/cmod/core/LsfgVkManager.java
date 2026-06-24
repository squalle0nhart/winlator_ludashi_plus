package com.winlator.cmod.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public abstract class LsfgVkManager {
    private static final String TAG = "LsfgVkManager";
    private static final String ASSET_LIB = "lsfg_vk/android_arm64_v8a/liblsfg-vk-layer.so";
    private static final String ASSET_MANIFEST = "lsfg_vk/android_arm64_v8a/VkLayer_LS_frame_generation.json";
    private static final String CONFIG_RELATIVE_PATH = ".config/lsfg-vk/conf.toml";
    private static final String DLL_RELATIVE_DIR = ".local/share/lsfg-vk";
    private static final String LAYER_RELATIVE_DIR = ".local/share/vulkan/implicit_layer.d";
    private static final String LIB_RELATIVE_DIR = ".local/lib";
    private static final String LIB_FILENAME = "liblsfg-vk-layer.so";
    private static final String MANIFEST_FILENAME = "VkLayer_LS_frame_generation.json";
    private static final String VERSION_FILENAME = ".lsfg_vk_runtime_version";
    private static final String LOSSLESS_DLL_NAME = "Lossless.dll";
    private static final String PROCESS_EXE_IDENTIFIER = "winlator-lsfg";
    private static final String RUNTIME_VERSION = "v1.4.0-android-arm64-v8a-ahb-no-props";

    public static final String EXTRA_ENABLED = "lsfgEnabled";
    public static final String EXTRA_MULTIPLIER = "lsfgMultiplier";
    public static final String EXTRA_FLOW_SCALE = "lsfgFlowScale";
    public static final String EXTRA_PERFORMANCE_MODE = "lsfgPerformanceMode";

    private LsfgVkManager() {}

    public static boolean isGlobalDllAvailable(Context context) {
        File dllFile = globalDllFile(context);
        return dllFile != null && dllFile.isFile() && dllFile.length() > 0;
    }

    public static File globalDllFile(Context context) {
        if (context == null) return null;
        return new File(context.getFilesDir(), "lsfg-vk/" + LOSSLESS_DLL_NAME);
    }

    public static String globalDllPath(Context context) {
        File dllFile = globalDllFile(context);
        return dllFile != null && dllFile.isFile() && dllFile.length() > 0 ? dllFile.getAbsolutePath() : null;
    }

    public static boolean importGlobalLosslessDll(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        File dst = globalDllFile(context);
        if (dst == null) return false;
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        return copyUriTo(context, uri, dst);
    }

    public static File containerDllFile(Container container) {
        if (container == null || container.getRootDir() == null) return null;
        return new File(container.getRootDir(), DLL_RELATIVE_DIR + "/" + LOSSLESS_DLL_NAME);
    }

    public static String containerDllPath(Container container) {
        File dllFile = containerDllFile(container);
        return dllFile != null && dllFile.isFile() ? dllFile.getAbsolutePath() : null;
    }

    public static File containerDllFile(Shortcut shortcut) {
        return shortcut == null ? null : containerDllFile(shortcut.container);
    }

    public static String containerDllPath(Shortcut shortcut) {
        File dllFile = containerDllFile(shortcut);
        return dllFile != null && dllFile.isFile() ? dllFile.getAbsolutePath() : null;
    }

    public static boolean isEnabled(Container container) {
        return container != null && container.isLsfgEnabled();
    }

    public static boolean isEnabled(Shortcut shortcut) {
        return shortcut != null && shortcut.isLsfgEnabled();
    }

    public static boolean isArmed(Container container) {
        return isEnabled(container) && containerDllPath(container) != null;
    }

    public static boolean isArmed(Shortcut shortcut) {
        return isEnabled(shortcut) && containerDllPath(shortcut) != null;
    }

    public static int multiplier(Container container) {
        return container != null ? container.getLsfgMultiplier() : 0;
    }

    public static int multiplier(Shortcut shortcut) {
        return shortcut != null ? shortcut.getLsfgMultiplier() : 0;
    }

    public static float flowScale(Container container) {
        return container != null ? container.getLsfgFlowScale() : 0.80f;
    }

    public static float flowScale(Shortcut shortcut) {
        return shortcut != null ? shortcut.getLsfgFlowScale() : 0.80f;
    }

    public static boolean performanceMode(Container container) {
        return container == null || container.getLsfgPerformanceMode();
    }

    public static boolean performanceMode(Shortcut shortcut) {
        return shortcut == null || shortcut.getLsfgPerformanceMode();
    }

    public static boolean ensureRuntimeInstalled(Context context, Container container) {
        if (context == null || container == null || container.getRootDir() == null) return false;

        File rootDir = container.getRootDir();
        File localLibDir = new File(rootDir, LIB_RELATIVE_DIR);
        File layerDir = new File(rootDir, LAYER_RELATIVE_DIR);
        File dllDir = new File(rootDir, DLL_RELATIVE_DIR);
        File libFile = new File(localLibDir, LIB_FILENAME);
        File manifestFile = new File(layerDir, MANIFEST_FILENAME);
        File versionFile = new File(layerDir, VERSION_FILENAME);

        String installedVersion = versionFile.isFile() ? FileUtils.readString(versionFile).trim() : "";
        boolean needsInstall = !RUNTIME_VERSION.equals(installedVersion) || !libFile.isFile() || !manifestFile.isFile();
        boolean success = true;

        if (needsInstall) {
            try {
                localLibDir.mkdirs();
                layerDir.mkdirs();
                FileUtils.copy(context, ASSET_LIB, libFile);
                FileUtils.copy(context, ASSET_MANIFEST, manifestFile);
                FileUtils.writeString(versionFile, RUNTIME_VERSION);
                FileUtils.chmod(libFile, 0755);
                FileUtils.chmod(manifestFile, 0644);
                FileUtils.chmod(versionFile, 0644);
                success = libFile.isFile() && manifestFile.isFile();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to install LSFG runtime", t);
                success = false;
            }
        }

        File globalDll = globalDllFile(context);
        File dllFile = new File(dllDir, LOSSLESS_DLL_NAME);
        if (globalDll != null && globalDll.isFile()) {
            try {
                if (!dllFile.isFile() || dllFile.length() != globalDll.length()) {
                    dllDir.mkdirs();
                    FileUtils.copy(globalDll, dllFile);
                    FileUtils.chmod(dllFile, 0644);
                }
                return success;
            } catch (Throwable t) {
                Log.e(TAG, "Failed to copy Lossless.dll into container", t);
                return false;
            }
        }

        return !isEnabled(container) && success;
    }

    public static boolean ensureRuntimeInstalled(Context context, Shortcut shortcut) {
        return shortcut != null && ensureRuntimeInstalled(context, shortcut.container);
    }

    public static boolean writeConfig(Container container) {
        if (container == null || container.getRootDir() == null) return false;

        String dllPath = containerDllPath(container);
        boolean enabled = isEnabled(container) && dllPath != null;
        File configFile = configFile(container);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();

        String config = buildConfigToml(dllPath, enabled, multiplier(container), flowScale(container), performanceMode(container));
        boolean ok = FileUtils.writeString(configFile, config);
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean writeConfig(Shortcut shortcut) {
        if (shortcut == null || shortcut.container == null || shortcut.container.getRootDir() == null) return false;

        String dllPath = containerDllPath(shortcut);
        boolean enabled = isEnabled(shortcut) && dllPath != null;
        File configFile = configFile(shortcut.container);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();

        String config = buildConfigToml(dllPath, enabled, multiplier(shortcut), flowScale(shortcut), performanceMode(shortcut));
        boolean ok = FileUtils.writeString(configFile, config);
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean updateConfigAtRuntime(Container container, boolean enabled, int multiplier, float flowScale, boolean performanceMode) {
        if (container == null || container.getRootDir() == null) return false;

        String dllPath = containerDllPath(container);
        File configFile = configFile(container);
        if (!configFile.isFile()) return false;

        int effectiveMultiplier = enabled && dllPath != null ? Math.max(2, Math.min(4, multiplier)) : 1;
        boolean perfMode = enabled && performanceMode;
        boolean ok = FileUtils.writeString(configFile, buildConfigToml(dllPath, true, effectiveMultiplier, flowScale, perfMode));
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean updateConfigAtRuntime(Shortcut shortcut, boolean enabled, int multiplier, float flowScale, boolean performanceMode) {
        if (shortcut == null || shortcut.container == null || shortcut.container.getRootDir() == null) return false;

        String dllPath = containerDllPath(shortcut);
        File configFile = configFile(shortcut.container);
        if (!configFile.isFile()) return false;

        int effectiveMultiplier = enabled && dllPath != null ? Math.max(2, Math.min(4, multiplier)) : 1;
        boolean perfMode = enabled && performanceMode;
        boolean ok = FileUtils.writeString(configFile, buildConfigToml(dllPath, true, effectiveMultiplier, flowScale, perfMode));
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        if (container == null || envVars == null || container.getRootDir() == null) return false;

        envVars.remove("DISABLE_LSFG");
        envVars.remove("LSFG_CONFIG");
        envVars.remove("LSFG_PROCESS");

        String dllPath = containerDllPath(container);
        boolean armed = isEnabled(container) && dllPath != null;
        if (!armed) {
            disableLayerInContainer(container);
            envVars.put("DISABLE_LSFG", "1");
            return false;
        }

        File layerDir = new File(container.getRootDir(), LAYER_RELATIVE_DIR);
        File manifestFile = new File(layerDir, MANIFEST_FILENAME);
        if (!manifestFile.isFile()) return false;

        envVars.put("LSFG_CONFIG", configFile(container).getAbsolutePath());
        envVars.put("LSFG_PROCESS", PROCESS_EXE_IDENTIFIER);

        String currentLayerPath = envVars.get("VK_LAYER_PATH");
        String layerPath = layerDir.getAbsolutePath();
        envVars.put("VK_LAYER_PATH",
                currentLayerPath == null || currentLayerPath.isEmpty()
                        ? layerPath
                        : currentLayerPath + ":" + layerPath);

        Log.i(TAG, "LSFG armed with multiplier=" + multiplier(container));
        return true;
    }

    public static boolean applyLaunchEnv(Shortcut shortcut, EnvVars envVars) {
        if (shortcut == null || shortcut.container == null || envVars == null || shortcut.container.getRootDir() == null) return false;

        envVars.remove("DISABLE_LSFG");
        envVars.remove("LSFG_CONFIG");
        envVars.remove("LSFG_PROCESS");

        String dllPath = containerDllPath(shortcut);
        boolean armed = isEnabled(shortcut) && dllPath != null;
        if (!armed) {
            disableLayerInContainer(shortcut.container);
            envVars.put("DISABLE_LSFG", "1");
            return false;
        }

        File layerDir = new File(shortcut.container.getRootDir(), LAYER_RELATIVE_DIR);
        File manifestFile = new File(layerDir, MANIFEST_FILENAME);
        if (!manifestFile.isFile()) return false;

        envVars.put("LSFG_CONFIG", configFile(shortcut.container).getAbsolutePath());
        envVars.put("LSFG_PROCESS", PROCESS_EXE_IDENTIFIER);

        String currentLayerPath = envVars.get("VK_LAYER_PATH");
        String layerPath = layerDir.getAbsolutePath();
        envVars.put("VK_LAYER_PATH",
                currentLayerPath == null || currentLayerPath.isEmpty()
                        ? layerPath
                        : currentLayerPath + ":" + layerPath);

        Log.i(TAG, "LSFG armed with multiplier=" + multiplier(shortcut));
        return true;
    }

    private static File configFile(Container container) {
        return new File(container.getRootDir(), CONFIG_RELATIVE_PATH);
    }

    private static void disableLayerInContainer(Container container) {
        File manifest = new File(container.getRootDir(), LAYER_RELATIVE_DIR + "/" + MANIFEST_FILENAME);
        if (manifest.exists() && !manifest.delete()) {
            Log.w(TAG, "Failed to remove disabled LSFG manifest: " + manifest);
        }
    }

    private static String buildConfigToml(String dllPath, boolean enabled, int multiplier, float flowScale, boolean performanceMode) {
        StringBuilder builder = new StringBuilder();
        builder.append("version = 1\n\n");
        builder.append("[global]\n");
        if (dllPath != null && !dllPath.isEmpty()) {
            builder.append("dll = ").append(tomlString(dllPath)).append('\n');
        }
        builder.append("no_fp16 = false\n\n");

        if (enabled && dllPath != null && !dllPath.isEmpty()) {
            builder.append("[[game]]\n");
            builder.append("exe = ").append(tomlString(PROCESS_EXE_IDENTIFIER)).append('\n');
            builder.append("multiplier = ").append(Math.max(1, Math.min(4, multiplier))).append('\n');
            builder.append("flow_scale = ").append(String.format(Locale.US, "%.2f", Math.max(0.25f, Math.min(1.0f, flowScale)))).append('\n');
            builder.append("performance_mode = ").append(performanceMode ? "true" : "false").append('\n');
            builder.append("hdr_mode = false\n");
            builder.append("experimental_present_mode = ").append(tomlString("fifo")).append('\n');
        }

        return builder.toString();
    }

    private static String tomlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean copyUriTo(Context context, Uri uri, File dst) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            try (FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buffer = new byte[0x20000];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            FileUtils.chmod(dst, 0644);
            Log.i(TAG, "Imported Lossless.dll to " + dst.getAbsolutePath() + " size=" + dst.length());
            return dst.isFile() && dst.length() > 0;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to import Lossless.dll", t);
            return false;
        }
    }
}
