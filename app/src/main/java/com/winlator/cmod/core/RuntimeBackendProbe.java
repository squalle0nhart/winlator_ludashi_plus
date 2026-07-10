package com.winlator.cmod.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class RuntimeBackendProbe {
    public enum FexMode { UNIXLIB, DLL, NA }

    private RuntimeBackendProbe() {}

    public static FexMode detect(int hintPid) {
        FexMode direct = fexModeFor(hintPid);
        if (direct != FexMode.NA) return direct;

        boolean sawDll = false;
        File[] processes = new File("/proc").listFiles(file ->
                file.isDirectory() && isNumeric(file.getName()));
        if (processes == null) return FexMode.NA;

        for (File process : processes) {
            FexMode mode = fexModeFor(parsePid(process.getName()));
            if (mode == FexMode.UNIXLIB) return mode;
            if (mode == FexMode.DLL) sawDll = true;
        }
        return sawDll ? FexMode.DLL : FexMode.NA;
    }

    private static FexMode fexModeFor(int pid) {
        if (pid <= 0) return FexMode.NA;
        File maps = new File("/proc/" + pid + "/maps");
        if (!maps.canRead()) return FexMode.NA;

        boolean sawDll = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(maps))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("libarm64ecfex.so") || line.contains("libwow64fex.so")) {
                    return FexMode.UNIXLIB;
                }
                if (line.contains("libarm64ecfex.dll") || line.contains("libwow64fex.dll")) {
                    sawDll = true;
                }
            }
        } catch (Exception ignored) {
            return FexMode.NA;
        }
        return sawDll ? FexMode.DLL : FexMode.NA;
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static int parsePid(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
