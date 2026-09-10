package com.winlator.cmod.core;

public final class DXWrapper {
    public static final String VEGAS = "vegas";
    public static final String VEGAS_VERSION = "2.7.3-vegas";

    private DXWrapper() {}

    public static boolean isVulkan(String wrapper) {
        return VEGAS.equals(wrapper) || (wrapper != null && wrapper.contains("dxvk"));
    }

    public static String migrate(String wrapper, String version) {
        return isVulkan(wrapper) && VEGAS_VERSION.equals(version) ? VEGAS : wrapper;
    }

    public static String versionFor(String wrapper, String version, String defaultVersion) {
        if (VEGAS.equals(wrapper)) return VEGAS_VERSION;
        return VEGAS_VERSION.equals(version) || version == null || version.isEmpty()
                ? defaultVersion : version;
    }
}
