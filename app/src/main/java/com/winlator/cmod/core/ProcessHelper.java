package com.winlator.cmod.core;

import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public abstract class ProcessHelper {
    public static final boolean PRINT_DEBUG = false;
    private static final ArrayList<Callback<String>> debugCallbacks = new ArrayList<>();
    private static final byte SIGCONT = 18;
    private static final byte SIGSTOP = 19;
    private static final byte SIGTERM = 15;
    private static final byte SIGKILL = 9;

    static {
        System.loadLibrary("winlator");
    }

    private static native int nativeSetProcessAffinity(int pid, int mask);
    private static native int nativeGetProcessAffinity(int pid);

    public static boolean setProcessAffinity(int pid, int mask) {
        if (pid <= 0 || mask <= 0) return false;
        int result = nativeSetProcessAffinity(pid, mask);
        if (result != 0)
            Log.w("ProcessHelper", "sched_setaffinity failed for pid=" + pid + " mask=0x" + Integer.toHexString(mask));
        return result == 0;
    }

    public static void setProcessAffinityAllThreads(int pid, int mask) {
        if (pid <= 0 || mask <= 0) return;
        File taskDir = new File("/proc/" + pid + "/task");
        if (!taskDir.isDirectory()) {

            setProcessAffinity(pid, mask);
            return;
        }
        File[] tasks = taskDir.listFiles();
        if (tasks == null || tasks.length == 0) {
            setProcessAffinity(pid, mask);
            return;
        }
        for (File task : tasks) {
            try {
                int tid = Integer.parseInt(task.getName());
                int result = nativeSetProcessAffinity(tid, mask);
                if (result != 0)
                    Log.w("ProcessHelper", "sched_setaffinity failed for tid=" + tid
                        + " (pid=" + pid + ") mask=0x" + Integer.toHexString(mask));
            } catch (NumberFormatException ignored) {}
        }
    }

    public static int getProcessAffinity(int pid) {
        if (pid <= 0) return -1;
        return nativeGetProcessAffinity(pid);
    }

    public static List<ProcessInfo> listProcessInfo() {
        List<ProcessInfo> result = new ArrayList<>();
        File proc = new File("/proc");
        File[] pidDirs = proc.listFiles(f -> f.isDirectory() && f.getName().matches("[0-9]+"));
        if (pidDirs == null) return result;

        for (File pidDir : pidDirs) {
            try {
                int pid = Integer.parseInt(pidDir.getName());
                String cmdline = readProcFile(new File(pidDir, "cmdline"));
                String comm = readProcFile(new File(pidDir, "comm"));
                if (comm != null) comm = comm.trim();

                String exeName = null;
                if (cmdline != null && cmdline.contains(".exe")) {
                    exeName = extractExeName(cmdline);
                }
                if (exeName == null && comm != null && comm.toLowerCase().endsWith(".exe")) {
                    exeName = comm;
                }
                if (exeName == null) continue;

                boolean wow64 = cmdline != null && isWow64Process(cmdline);
                long memoryBytes = readVmRSS(pidDir);
                int affinity = nativeGetProcessAffinity(pid);
                if (affinity < 0) continue;

                result.add(new ProcessInfo(pid, exeName, memoryBytes, affinity, wow64));
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static String readProcFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int len = fis.read(buf);
            if (len <= 0) return null;
            for (int i = 0; i < len; i++) {
                if (buf[i] == 0) buf[i] = ' ';
            }
            return new String(buf, 0, len).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractExeName(String cmdline) {
        String[] parts = cmdline.split(" ");
        for (String part : parts) {
            if (part.toLowerCase().endsWith(".exe")) {
                int lastSep = Math.max(part.lastIndexOf('\\'), part.lastIndexOf('/'));
                return lastSep >= 0 ? part.substring(lastSep + 1) : part;
            }
        }
        return null;
    }

    private static long readVmRSS(File pidDir) {
        File statusFile = new File(pidDir, "status");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(statusFile)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]) * 1024L;
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static boolean isWow64Process(String cmdline) {
        return cmdline.contains("wine") && !cmdline.contains("wine64");
    }

    public static void suspendProcess(int pid) {
        Process.sendSignal(pid, SIGSTOP);
        Log.d("ProcessHelper", "Process suspended with pid: " + pid);
    }

    public static void resumeProcess(int pid) {
        Process.sendSignal(pid, SIGCONT);
        Log.d("ProcessHelper", "Process resumed with pid: " + pid);
    }

    public static void terminateProcess(int pid) {
        Process.sendSignal(pid, SIGTERM);
        Log.d("ProcessHelper", "Process terminated with pid: " + pid);
    }

    public static void killProcess(int pid) {
        Process.sendSignal(pid, SIGKILL);
        Log.d("ProcessHelper", "Process killed with pid: " + pid);
    }

    public static void terminateAllWineProcesses() {
        for (String process : listRunningWineProcesses()) {
            terminateProcess(Integer.parseInt(process));
        }
    }

    public static void pauseAllWineProcesses() {
        for (String process : listRunningWineProcesses()) {
            suspendProcess(Integer.parseInt(process));
        }
    }

    public static void resumeAllWineProcesses() {
        for (String process : listRunningWineProcesses()) {
            resumeProcess(Integer.parseInt(process));
        }
    }

    public static int exec(String command) {
        return exec(command, null);
    }

    public static int exec(String command, String[] envp) {
        return exec(command, envp, null);
    }

    public static int exec(String command, String[] envp, File workingDir) {
        return exec(command, envp, workingDir, null);
    }

    public static int exec(String command, String[] envp, File workingDir, Callback<Integer> terminationCallback) {
        Log.d("ProcessHelper", "env: " + Arrays.toString(envp) + "\ncmd: " + command);
        EnvironmentManager.setEnvVars(envp);

        int pid = -1;
        try {
            String[] splitCommand = splitCommand(command);
            ProcessBuilder pb = new ProcessBuilder(splitCommand);
            pb.directory(workingDir);
            pb.environment().putAll(EnvironmentManager.getEnvVars());
            if (debugCallbacks.isEmpty()) {
                File null_file = new File("/dev/null");
                pb.redirectError(null_file);
                pb.redirectOutput(null_file);
            }
            java.lang.Process process = pb.start();

            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
            Log.d("ProcessHelper", "Process started with pid: " + pid);

            if (!debugCallbacks.isEmpty()) {
                createDebugThread(process.getInputStream());
                createDebugThread(process.getErrorStream());
            }

            if (terminationCallback != null) createWaitForThread(process, terminationCallback);
        } catch (Exception e) {
            Log.e("ProcessHelper", "Error executing command: " + command, e);
        }
        return pid;
    }

    private static void createDebugThread(final InputStream inputStream) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (PRINT_DEBUG) System.out.println(line);
                    synchronized (debugCallbacks) {
                        if (!debugCallbacks.isEmpty()) {
                            for (Callback<String> callback : debugCallbacks) callback.call(line);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("ProcessHelper", "Error in debug thread", e);
            }
        });
    }

    private static void createWaitForThread(java.lang.Process process, final Callback<Integer> terminationCallback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                terminationCallback.call(process.waitFor());
            } catch (InterruptedException e) {
                Log.e("ProcessHelper", "Error waiting for process termination", e);
            }
        });
    }

    public static void removeAllDebugCallbacks() {
        synchronized (debugCallbacks) {
            debugCallbacks.clear();
        }
    }

    public static void addDebugCallback(Callback<String> callback) {
        synchronized (debugCallbacks) {
            if (!debugCallbacks.contains(callback)) debugCallbacks.add(callback);
        }
    }

    public static void removeDebugCallback(Callback<String> callback) {
        synchronized (debugCallbacks) {
            debugCallbacks.remove(callback);
        }
    }

    public static String[] splitCommand(String command) {
        ArrayList<String> result = new ArrayList<>();
        boolean startedQuotes = false;
        String value = "";
        char currChar, nextChar;
        for (int i = 0, count = command.length(); i < count; i++) {
            currChar = command.charAt(i);
            if (startedQuotes) {
                if (currChar == '"') {
                    startedQuotes = false;
                    if (!value.isEmpty()) { value += '"'; result.add(value); value = ""; }
                } else value += currChar;
            } else if (currChar == '"') {
                startedQuotes = true;
                value += '"';
            } else {
                nextChar = i < count - 1 ? command.charAt(i + 1) : '\0';
                if (currChar == ' ' || (currChar == '\\' && nextChar == ' ')) {
                    if (currChar == '\\') { value += ' '; i++; }
                    else if (!value.isEmpty()) { result.add(value); value = ""; }
                } else {
                    value += currChar;
                    if (i == count - 1) { result.add(value); value = ""; }
                }
            }
        }
        return result.toArray(new String[0]);
    }

    public static String getAffinityMaskAsHexString(String cpuList) {
        return Integer.toHexString(getAffinityMask(cpuList));
    }

    public static int getAffinityMask(String cpuList) {
        if (cpuList == null || cpuList.isEmpty()) return 0;
        int affinityMask = 0;
        for (String value : cpuList.split(",")) {
            affinityMask |= (int) Math.pow(2, Byte.parseByte(value));
        }
        return affinityMask;
    }

    public static int getAffinityMask(boolean[] cpuList) {
        int affinityMask = 0;
        for (int i = 0; i < cpuList.length; i++) {
            if (cpuList[i]) affinityMask |= (int) Math.pow(2, i);
        }
        return affinityMask;
    }

    public static int getAffinityMask(int from, int to) {
        int affinityMask = 0;
        for (int i = from; i < to; i++) affinityMask |= (int) Math.pow(2, i);
        return affinityMask;
    }

    public static ArrayList<String> listRunningWineProcesses() {
        File proc = new File("/proc");
        String[] filters = {"wine", "exe"};
        ArrayList<String> filteredPids = new ArrayList<>();
        List<String> filterList = Arrays.asList(filters);
        String[] allPids = proc.list((dir, name) ->
                new File(dir, name).isDirectory() && name.matches("[0-9]+"));
        if (allPids == null) return filteredPids;

        for (String pid : allPids) {
            String data = "";
            try {
                FileInputStream fr = new FileInputStream("/proc/" + pid + "/stat");
                BufferedReader br = new BufferedReader(new InputStreamReader(fr));
                data = br.readLine();
                br.close();
            } catch (IOException e) {}
            for (String filter : filterList) {
                if (data != null && data.contains(filter)) {
                    filteredPids.add(pid);
                    break;
                }
            }
        }
        return filteredPids;
    }
}
