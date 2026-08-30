package com.winlator.cmod.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public abstract class CPUStatus {
    private static long previousCpuTotal = -1L;
    private static long previousCpuIdle = -1L;
    private static volatile String[] cpuTempPaths;

    public static short[] getCurrentClockSpeeds() {
        int numProcessors = Runtime.getRuntime().availableProcessors();
        short[] clockSpeeds = new short[numProcessors];
        for (int i = 0; i < numProcessors; i++) {
            int currFreq = FileUtils.readInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            clockSpeeds[i] = (short) (currFreq / 1000);
        }
        return clockSpeeds;
    }

    public static short getMaxClockSpeed(int cpuIndex) {
        int maxFreq = FileUtils.readInt("/sys/devices/system/cpu/cpu" + cpuIndex + "/cpufreq/cpuinfo_max_freq");
        return (short) (maxFreq / 1000);
    }

    public static synchronized int getCpuUsagePercent() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5 && "cpu".equals(parts[0])) {
                    long total = 0L;
                    long idle = 0L;
                    for (int i = 1; i < parts.length; i++) {
                        long value = Long.parseLong(parts[i]);
                        total += value;
                        if (i == 4 || i == 5) idle += value;
                    }

                    long oldTotal = previousCpuTotal;
                    long oldIdle = previousCpuIdle;
                    previousCpuTotal = total;
                    previousCpuIdle = idle;

                    if (oldTotal >= 0L && oldIdle >= 0L) {
                        long deltaTotal = total - oldTotal;
                        long deltaIdle = idle - oldIdle;
                        if (deltaTotal > 0L) {
                            return clampPercent((int) (((deltaTotal - Math.max(0L, deltaIdle)) * 100L) / deltaTotal));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return getClockFreqLoadPercent();
    }

    public static int getClockFreqLoadPercent() {
        short[] clocks = getCurrentClockSpeeds();
        if (clocks == null || clocks.length == 0) return -1;
        long current = 0L;
        long maximum = 0L;
        for (int i = 0; i < clocks.length; i++) {
            int max = getMaxClockSpeed(i);
            if (clocks[i] > 0 && max > 0) {
                current += clocks[i];
                maximum += max;
            }
        }
        return maximum > 0L ? clampPercent((int) ((current * 100L) / maximum)) : -1;
    }

    public static int getCpuTempC() {
        String[] paths = cpuTempPaths;
        if (paths == null) {
            synchronized (CPUStatus.class) {
                paths = cpuTempPaths;
                if (paths == null) {
                    paths = discoverCpuTempPaths();
                    cpuTempPaths = paths;
                }
            }
        }

        for (String path : paths) {
            int raw = readInt(path);
            if (raw <= 0) continue;
            int celsius = raw > 1000 ? (raw + 500) / 1000 : raw;
            if (celsius >= 1 && celsius <= 150) return celsius;
        }
        return -1;
    }

    private static String[] discoverCpuTempPaths() {
        ArrayList<TempPath> found = new ArrayList<>();
        File[] roots = {
                new File("/sys/class/thermal"),
                new File("/sys/devices/virtual/thermal")
        };

        for (File root : roots) {
            File[] zones = root.listFiles((dir, name) -> name.startsWith("thermal_zone"));
            if (zones == null) continue;
            for (File zone : zones) {
                if (!zone.isDirectory()) continue;
                String type = readLine(new File(zone, "type"));
                if (type == null) continue;
                int rank = rankCpuZone(type.trim().toLowerCase(Locale.US));
                if (rank < 0) continue;
                File temp = new File(zone, "temp");
                if (!temp.canRead()) continue;
                String path = temp.getAbsolutePath();
                boolean duplicate = false;
                for (TempPath old : found) {
                    if (old.path.equals(path)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) found.add(new TempPath(rank, path));
            }
        }

        Collections.sort(found, (a, b) -> a.rank != b.rank
                ? Integer.compare(a.rank, b.rank)
                : a.path.compareTo(b.path));
        String[] result = new String[found.size()];
        for (int i = 0; i < found.size(); i++) result[i] = found.get(i).path;
        return result;
    }

    private static int rankCpuZone(String type) {
        if (type.contains("gpu")) return -1;
        if (type.contains("cpu-silicon")) return 0;
        if (type.contains("cpu-0")) return 1;
        if (type.contains("cpuss")) return 2;
        if (type.contains("mtktscpu")) return 2;
        if (type.contains("cpu")) return 2;
        if (type.contains("s5p-tmu")) return 3;
        if (type.contains("soc")) return 4;
        if (type.contains("cputop")) return 5;
        if (type.contains("tsens")) return 6;
        if (type.contains("cluster")) return 7;
        if (type.contains("big") || type.contains("little")) return 8;
        return -1;
    }

    private static String readLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int readInt(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            return line != null ? Integer.parseInt(line.trim()) : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static final class TempPath {
        final int rank;
        final String path;

        TempPath(int rank, String path) {
            this.rank = rank;
            this.path = path;
        }
    }
}
