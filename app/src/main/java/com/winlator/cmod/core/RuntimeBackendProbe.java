package com.winlator.cmod.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RuntimeBackendProbe {
    public enum FexMode { UNIXLIB, DLL, NA }

    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(FexMode.NA, false, false);

        public final FexMode fexMode;
        public final boolean winFgLoaded;
        public final boolean lsfgLoaded;

        Snapshot(FexMode fexMode, boolean winFgLoaded, boolean lsfgLoaded) {
            this.fexMode = fexMode;
            this.winFgLoaded = winFgLoaded;
            this.lsfgLoaded = lsfgLoaded;
        }

        Snapshot merge(Snapshot other) {
            FexMode mergedMode = fexMode == FexMode.UNIXLIB || other.fexMode == FexMode.UNIXLIB
                    ? FexMode.UNIXLIB
                    : fexMode == FexMode.DLL || other.fexMode == FexMode.DLL
                            ? FexMode.DLL : FexMode.NA;
            return new Snapshot(mergedMode,
                    winFgLoaded || other.winFgLoaded,
                    lsfgLoaded || other.lsfgLoaded);
        }
    }

    private RuntimeBackendProbe() {}

    public static Snapshot detect(int hintPid, File containerRoot) {
        File[] processes = new File("/proc").listFiles(file ->
                file.isDirectory() && isNumeric(file.getName()));
        if (processes == null) return Snapshot.EMPTY;

        Map<Integer, Integer> parents = new HashMap<>();
        for (File process : processes) {
            int pid = parsePid(process.getName());
            parents.put(pid, readParentPid(pid));
        }

        Set<Integer> processTree = new HashSet<>();
        if (hintPid > 0) processTree.add(hintPid);
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Integer, Integer> entry : parents.entrySet()) {
                if (processTree.contains(entry.getValue()) && processTree.add(entry.getKey())) {
                    changed = true;
                }
            }
        } while (changed);

        String containerPath = containerRoot == null ? "" : containerRoot.getAbsolutePath();
        Snapshot result = Snapshot.EMPTY;
        for (File process : processes) {
            int pid = parsePid(process.getName());
            ProcessSnapshot processSnapshot = inspectProcess(pid, containerPath);
            if (processTree.contains(pid) || processSnapshot.belongsToContainer) {
                result = result.merge(processSnapshot.snapshot);
            }
        }
        return result;
    }

    static Snapshot inspectMaps(Iterable<String> lines) {
        FexMode mode = FexMode.NA;
        boolean winFg = false;
        boolean lsfg = false;
        for (String line : lines) {
            if (line.contains("libarm64ecfex.so") || line.contains("libwow64fex.so")) {
                mode = FexMode.UNIXLIB;
            } else if (mode == FexMode.NA
                    && (line.contains("libarm64ecfex.dll") || line.contains("libwow64fex.dll"))) {
                mode = FexMode.DLL;
            }
            winFg |= line.contains("libwin_fg.so");
            lsfg |= line.contains("liblsfg-vk-layer.so");
        }
        return new Snapshot(mode, winFg, lsfg);
    }

    private static ProcessSnapshot inspectProcess(int pid, String containerPath) {
        if (pid <= 0) return ProcessSnapshot.EMPTY;
        File maps = new File("/proc/" + pid + "/maps");
        if (!maps.canRead()) return ProcessSnapshot.EMPTY;

        Snapshot snapshot = Snapshot.EMPTY;
        boolean belongsToContainer = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(maps))) {
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                belongsToContainer |= !containerPath.isEmpty() && line.contains(containerPath);
            }
            snapshot = inspectMaps(lines);
        } catch (Exception ignored) {
            return ProcessSnapshot.EMPTY;
        }
        return new ProcessSnapshot(snapshot, belongsToContainer);
    }

    private static int readParentPid(int pid) {
        File status = new File("/proc/" + pid + "/status");
        if (!status.canRead()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(status))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PPid:")) return parsePid(line.substring(5).trim());
            }
        } catch (Exception ignored) {}
        return -1;
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

    private static final class ProcessSnapshot {
        static final ProcessSnapshot EMPTY = new ProcessSnapshot(Snapshot.EMPTY, false);

        final Snapshot snapshot;
        final boolean belongsToContainer;

        ProcessSnapshot(Snapshot snapshot, boolean belongsToContainer) {
            this.snapshot = snapshot;
            this.belongsToContainer = belongsToContainer;
        }
    }
}
