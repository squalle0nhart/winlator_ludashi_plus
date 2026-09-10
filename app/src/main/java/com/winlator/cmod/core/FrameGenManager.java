package com.winlator.cmod.core;

public final class FrameGenManager {
    public static final String BACKEND_LSFG_NATIVE = "lsfg_native";
    public static final String BACKEND_WIN_FG_NATIVE = "win_fg_native";

    private FrameGenManager() {}

    public static String normalizeBackend(String backend) {
        if (BACKEND_WIN_FG_NATIVE.equalsIgnoreCase(backend)
                || "win_fg".equalsIgnoreCase(backend)
                || "bionic_fg".equalsIgnoreCase(backend)
                || "native_fg".equalsIgnoreCase(backend)) return BACKEND_WIN_FG_NATIVE;
        return BACKEND_LSFG_NATIVE;
    }

    public static void applyLaunchEnv(EnvVars envVars) {
        for (String name : envVars.toStringArray()) {
            String key = name.substring(0, name.indexOf('='));
            if (key.startsWith("LSFG_") || key.startsWith("WIN_FG_") || key.startsWith("BIONIC_FG_")) {
                envVars.remove(key);
            }
        }
        String layers = envVars.get("VK_INSTANCE_LAYERS");
        java.util.ArrayList<String> retained = new java.util.ArrayList<>();
        for (String layer : layers.split(":")) {
            if (!layer.isEmpty() && !layer.equals("VK_LAYER_LS_frame_generation")
                    && !layer.equals("VK_LAYER_WIN_framegen")) retained.add(layer);
        }
        if (retained.isEmpty()) envVars.remove("VK_INSTANCE_LAYERS");
        else envVars.put("VK_INSTANCE_LAYERS", String.join(":", retained));
        envVars.put("DISABLE_LSFG", "1");
        envVars.put("BIONIC_FG_DISABLE", "1");
        envVars.put("WIN_FG_DISABLE", "1");
    }
}
