package com.winlator.cmod.core;

import android.content.Context;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

public abstract class FrameGenManager {
    public static final String BACKEND_LSFG_VK = "lsfg_vk";
    public static final String BACKEND_BIONIC_FG = "bionic_fg";
    public static final String BACKEND_NATIVE_FG = "native_fg";

    private FrameGenManager() {}

    public static String normalizeBackend(String backend) {
        if (BACKEND_BIONIC_FG.equalsIgnoreCase(backend)) return BACKEND_BIONIC_FG;
        if (BACKEND_NATIVE_FG.equalsIgnoreCase(backend)) return BACKEND_NATIVE_FG;
        return BACKEND_LSFG_VK;
    }

    public static String getBackend(Container container) {
        return container == null ? BACKEND_LSFG_VK : normalizeBackend(container.getFrameGenBackend());
    }

    public static String getBackend(Shortcut shortcut) {
        return shortcut == null ? BACKEND_LSFG_VK : normalizeBackend(shortcut.getFrameGenBackend());
    }

    public static boolean ensureRuntimeInstalled(Context context, Container container) {
        if (BACKEND_NATIVE_FG.equals(getBackend(container))) return true;
        if (BACKEND_BIONIC_FG.equals(getBackend(container))) {
            return BionicFgManager.ensureRuntimeInstalled(context, container);
        }
        return LsfgVkManager.ensureRuntimeInstalled(context, container);
    }

    public static boolean ensureRuntimeInstalled(Context context, Shortcut shortcut) {
        if (BACKEND_NATIVE_FG.equals(getBackend(shortcut))) return true;
        if (BACKEND_BIONIC_FG.equals(getBackend(shortcut))) {
            return BionicFgManager.ensureRuntimeInstalled(context, shortcut);
        }
        return LsfgVkManager.ensureRuntimeInstalled(context, shortcut);
    }

    public static boolean writeConfig(Container container) {
        if (BACKEND_NATIVE_FG.equals(getBackend(container))) return true;
        if (BACKEND_BIONIC_FG.equals(getBackend(container))) {
            return BionicFgManager.writeConfig(container);
        }
        return LsfgVkManager.writeConfig(container);
    }

    public static boolean writeConfig(Shortcut shortcut) {
        if (BACKEND_NATIVE_FG.equals(getBackend(shortcut))) return true;
        if (BACKEND_BIONIC_FG.equals(getBackend(shortcut))) {
            return BionicFgManager.writeConfig(shortcut);
        }
        return LsfgVkManager.writeConfig(shortcut);
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        LsfgVkManager.clearLaunchEnv(envVars);
        BionicFgManager.clearLaunchEnv(envVars);
        if (BACKEND_NATIVE_FG.equals(getBackend(container))) {
            envVars.put("DISABLE_LSFG", "1");
            envVars.put("BIONIC_FG_DISABLE", "1");
            return true;
        }
        if (BACKEND_BIONIC_FG.equals(getBackend(container))) {
            envVars.put("DISABLE_LSFG", "1");
            return BionicFgManager.applyLaunchEnv(container, envVars);
        }
        envVars.put("BIONIC_FG_DISABLE", "1");
        return LsfgVkManager.applyLaunchEnv(container, envVars);
    }

    public static boolean applyLaunchEnv(Shortcut shortcut, EnvVars envVars) {
        LsfgVkManager.clearLaunchEnv(envVars);
        BionicFgManager.clearLaunchEnv(envVars);
        if (BACKEND_NATIVE_FG.equals(getBackend(shortcut))) {
            envVars.put("DISABLE_LSFG", "1");
            envVars.put("BIONIC_FG_DISABLE", "1");
            return true;
        }
        if (BACKEND_BIONIC_FG.equals(getBackend(shortcut))) {
            envVars.put("DISABLE_LSFG", "1");
            return BionicFgManager.applyLaunchEnv(shortcut, envVars);
        }
        envVars.put("BIONIC_FG_DISABLE", "1");
        return LsfgVkManager.applyLaunchEnv(shortcut, envVars);
    }
}
