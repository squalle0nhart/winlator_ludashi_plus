package com.winlator.cmod.core;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

public abstract class FrameGenManager {
    private static final String TAG = "FrameGenManager";
    public static final String BACKEND_LSFG_VK = "lsfg_vk";
    public static final String BACKEND_WIN_FG = "win_fg";

    private FrameGenManager() {}

    public static String normalizeBackend(String backend) {
        if ("bionic_fg".equalsIgnoreCase(backend) || "native_fg".equalsIgnoreCase(backend)) return BACKEND_WIN_FG;
        if (BACKEND_WIN_FG.equalsIgnoreCase(backend)) return BACKEND_WIN_FG;
        return BACKEND_LSFG_VK;
    }

    public static String getBackend(Container container) {
        return container == null ? BACKEND_LSFG_VK : normalizeBackend(container.getFrameGenBackend());
    }

    public static String getBackend(Shortcut shortcut) {
        return shortcut == null ? BACKEND_LSFG_VK : normalizeBackend(shortcut.getFrameGenBackend());
    }

    public static boolean ensureRuntimeInstalled(Context context, Container container) {
        if (BACKEND_WIN_FG.equals(getBackend(container))) {
            return WinFgManager.ensureRuntimeInstalled(context, container);
        }
        return LsfgVkManager.ensureRuntimeInstalled(context, container);
    }

    public static boolean ensureRuntimeInstalled(Context context, Shortcut shortcut) {
        if (BACKEND_WIN_FG.equals(getBackend(shortcut))) {
            return WinFgManager.ensureRuntimeInstalled(context, shortcut);
        }
        return LsfgVkManager.ensureRuntimeInstalled(context, shortcut);
    }

    public static boolean writeConfig(Container container) {
        if (BACKEND_WIN_FG.equals(getBackend(container))) {
            return WinFgManager.writeConfig(container);
        }
        return LsfgVkManager.writeConfig(container);
    }

    public static boolean writeConfig(Shortcut shortcut) {
        if (BACKEND_WIN_FG.equals(getBackend(shortcut))) {
            return WinFgManager.writeConfig(shortcut);
        }
        return LsfgVkManager.writeConfig(shortcut);
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        LsfgVkManager.clearLaunchEnv(envVars);
        WinFgManager.clearLaunchEnv(envVars);
        if (disableExternalFrameGenForTrueDisplayX(envVars)) return false;
        if (BACKEND_WIN_FG.equals(getBackend(container))) {
            envVars.put("DISABLE_LSFG", "1");
            envVars.put("BIONIC_FG_DISABLE", "1");
            return WinFgManager.applyLaunchEnv(container, envVars);
        }
        envVars.put("BIONIC_FG_DISABLE", "1");
        envVars.put("WIN_FG_DISABLE", "1");
        return LsfgVkManager.applyLaunchEnv(container, envVars);
    }

    public static boolean applyLaunchEnv(Shortcut shortcut, EnvVars envVars) {
        LsfgVkManager.clearLaunchEnv(envVars);
        WinFgManager.clearLaunchEnv(envVars);
        if (disableExternalFrameGenForTrueDisplayX(envVars)) return false;
        if (BACKEND_WIN_FG.equals(getBackend(shortcut))) {
            envVars.put("DISABLE_LSFG", "1");
            envVars.put("BIONIC_FG_DISABLE", "1");
            return WinFgManager.applyLaunchEnv(shortcut, envVars);
        }
        envVars.put("BIONIC_FG_DISABLE", "1");
        envVars.put("WIN_FG_DISABLE", "1");
        return LsfgVkManager.applyLaunchEnv(shortcut, envVars);
    }

    private static boolean disableExternalFrameGenForTrueDisplayX(EnvVars envVars) {
        boolean explicitDisplayX = envVars.get("VK_INSTANCE_LAYERS")
                .contains("VK_LAYER_DISPLAYX_display_x");
        boolean implicitDisplayX = "1".equals(envVars.get("ENABLE_DISPLAYX"))
                && !"1".equals(envVars.get("DISABLE_DISPLAYX"));
        if (!explicitDisplayX && !implicitDisplayX) {
            return false;
        }
        // True DisplayX already owns and exports the Vulkan swapchain. Stacking an external
        // frame-generation layer on it makes Wine's vkGetSwapchainImagesKHR call fail at startup.
        envVars.put("DISABLE_LSFG", "1");
        envVars.put("BIONIC_FG_DISABLE", "1");
        envVars.put("WIN_FG_DISABLE", "1");
        Log.i(TAG, "External frame generation disabled for True DisplayX compatibility");
        return true;
    }
}
