package com.winlator.cmod.winhandler;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.preference.PreferenceManager;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.FakeInputWriter;
import com.winlator.cmod.inputcontrols.GamepadState;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XLock;

import android.content.Context;
import android.hardware.input.InputManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WinHandler {

    @FunctionalInterface
    public interface WineExecCallback {
        void exec(String command);
    }

    private static final short SERVER_PORT = 7947;
    private static final short CLIENT_PORT = 7946;
    public static final byte FLAG_INPUT_TYPE_XINPUT = 0x04;
    public static final byte FLAG_INPUT_TYPE_DINPUT = 0x08;
    public static final byte DEFAULT_INPUT_TYPE = FLAG_INPUT_TYPE_XINPUT;

    private DatagramSocket socket;
    private final ByteBuffer sendData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer receiveData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket sendPacket = new DatagramPacket(sendData.array(), 64);
    private final DatagramPacket receivePacket = new DatagramPacket(receiveData.array(), 64);
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();

    private boolean initReceived = false;
    private boolean running = false;
    private InetAddress localhost;
    private byte inputType = DEFAULT_INPUT_TYPE;
    private final XServerDisplayActivity activity;
    private SharedPreferences preferences;

    private static final int MAX_CONTROLLERS = 4;
    private static final int OSC_DEVICE_ID = -1;
    private FakeInputWriter[] writers = new FakeInputWriter[MAX_CONTROLLERS];
    private Map<Integer, Integer> deviceToSlot = new HashMap<>();
    private Set<Integer> usedSlots = new HashSet<>();
    private String fakeInputBasePath;
    private LocalServerSocket vibrationServer;
    private volatile boolean vibrationRunning = false;
    private boolean[] vibrationEnabledSlots = new boolean[MAX_CONTROLLERS];

    private boolean xinputDisabled;
    private boolean xinputDisabledInitialized = false;
    private WineExecCallback wineExecCallback;

    private int fallbackSlot = -1;

    private final Map<Integer, ExternalController> controllers = new HashMap<>();
    private final InputManager inputManager;
    private final InputManager.InputDeviceListener inputDeviceListener;

    public WinHandler(XServerDisplayActivity activity) {
        this.activity = activity;
        this.inputManager = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        this.inputDeviceListener = new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {}

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                releaseSlot(deviceId);
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {}
        };
        inputManager.registerInputDeviceListener(inputDeviceListener, null);
        preferences = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            vibrationEnabledSlots[i] = preferences.getBoolean("vibration_slot_" + i, true);
        }
    }

    private boolean sendPacket(int port) {
        try {
            int size = sendData.position();
            if (size == 0) return false;
            sendPacket.setAddress(localhost);
            sendPacket.setPort(port);
            socket.send(sendPacket);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void setWineExecCallback(WineExecCallback cb) {
        this.wineExecCallback = cb;
    }

    public void exec(String command) {
        command = command.trim();
        if (command.isEmpty()) return;
        if (wineExecCallback == null) return;
        final String cmd = command;
        Executors.newSingleThreadExecutor().execute(() -> wineExecCallback.exec(cmd));
    }

    public void execWithDelay(String command, int delaySeconds) {
        if (command == null || command.trim().isEmpty() || delaySeconds < 0) return;
        Executors.newSingleThreadScheduledExecutor().schedule(() -> exec(command), delaySeconds, TimeUnit.SECONDS);
    }

    public void killProcess(final String processName) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KILL_PROCESS);
            byte[] bytes = processName.getBytes();
            sendData.putInt(bytes.length);
            sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void keyboardEvent(byte vkey, int flags) {
        if (!initReceived) return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KEYBOARD_EVENT);
            sendData.put(vkey);
            sendData.putInt(flags);
            sendPacket(CLIENT_PORT);
        });
    }

    public void bringToFront(final String processName) {
        bringToFront(processName, 0);
    }

    public void bringToFront(final String processName, final long handle) {
        Executors.newSingleThreadExecutor().execute(() -> {
            XServer xServer = activity.getXServer();
            if (xServer == null) return;
            try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                Window window = xServer.windowManager.findWindowByProcessName(processName);
                if (window == null) return;
                xServer.windowManager.raiseWindow(window);
                xServer.windowManager.setFocus(window, WindowManager.FocusRevertTo.NONE);
            }
        });
    }

    private void addAction(Runnable action) {
        synchronized (actions) {
            actions.add(action);
            actions.notify();
        }
    }

    private void startSendThread() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (running) {
                synchronized (actions) {
                    while (initReceived && !actions.isEmpty())
                        actions.poll().run();
                    try {
                        actions.wait();
                    } catch (InterruptedException e) {}
                }
            }
        });
    }

    public void stop() {
        running = false;
        closeFakeInputWriter();
        if (socket != null) {
            socket.close();
            socket = null;
        }
        synchronized (actions) {
            actions.notify();
        }
    }

    public void startVibrationListener() {
        if (vibrationRunning) return;
        vibrationRunning = true;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                vibrationServer = new LocalServerSocket("winlator_vibration");
                while (vibrationRunning) {
                    LocalSocket client = vibrationServer.accept();
                    try {
                        java.io.InputStream is = client.getInputStream();
                        byte[] buf = new byte[8];
                        int read = is.read(buf);
                        if (read == 8) {
                            int strong = (buf[0] & 0xFF) | ((buf[1] & 0xFF) << 8);
                            int weak = (buf[2] & 0xFF) | ((buf[3] & 0xFF) << 8);
                            int durationMs = (buf[4] & 0xFF) | ((buf[5] & 0xFF) << 8);
                            int slot = (buf[6] & 0xFF) | ((buf[7] & 0xFF) << 8);
                            triggerVibration(strong, weak, durationMs, slot);
                        }
                        client.close();
                    } catch (IOException e) {
                        Log.e("WinHandler", "Vibration client error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (vibrationRunning) {
                    Log.e("WinHandler", "Vibration listener error: " + e.getMessage());
                }
            }
        });
    }

    private void triggerVibration(int strong, int weak, int durationMs, int slot) {
        if (!isValidSlot(slot) || !vibrationEnabledSlots[slot]) return;

        boolean shouldCancel = (durationMs == 0 && strong == 0 && weak == 0);
        Vibrator vibrator = null;
        android.os.VibratorManager vibratorManager = null;
        boolean hasMultiMotor = false;

        Integer deviceId = null;
        for (Map.Entry<Integer, Integer> entry : deviceToSlot.entrySet()) {
            if (entry.getValue() == slot) {
                deviceId = entry.getKey();
                break;
            }
        }

        if (deviceId != null && deviceId.equals(OSC_DEVICE_ID)) {
            vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        } else if (deviceId != null) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
            if (device != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    vibratorManager = device.getVibratorManager();
                    if (vibratorManager != null && vibratorManager.getVibratorIds().length > 1) {
                        hasMultiMotor = true;
                    }
                }
                if (!hasMultiMotor) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && vibratorManager != null) {
                        int[] ids = vibratorManager.getVibratorIds();
                        if (ids.length > 0) vibrator = vibratorManager.getVibrator(ids[0]);
                    } else {
                        vibrator = device.getVibrator();
                    }
                    if (vibrator == null || !vibrator.hasVibrator()) {
                        if (!deviceToSlot.containsKey(OSC_DEVICE_ID) && (fallbackSlot == -1 || fallbackSlot == slot)) {
                            vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                            fallbackSlot = slot;
                        } else {
                            vibrator = null;
                        }
                    }
                }
            }
        }

        if (hasMultiMotor && vibratorManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            int[] vibratorIds = vibratorManager.getVibratorIds();
            if (vibratorIds.length >= 1) {
                Vibrator vStrong = vibratorManager.getVibrator(vibratorIds[0]);
                if (!shouldCancel && strong > 0) {
                    vStrong.vibrate(VibrationEffect.createOneShot(Math.max(1, durationMs), clampAmplitude(strong)));
                } else {
                    vStrong.cancel();
                }
            }
            if (vibratorIds.length >= 2) {
                Vibrator vWeak = vibratorManager.getVibrator(vibratorIds[1]);
                if (!shouldCancel && weak > 0) {
                    vWeak.vibrate(VibrationEffect.createOneShot(Math.max(1, durationMs), clampAmplitude(weak)));
                } else {
                    vWeak.cancel();
                }
            }
            return;
        }

        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (!shouldCancel && (strong > 0 || weak > 0)) {
            int amplitude = clampAmplitude(Math.max(strong, weak));
            int duration = Math.max(1, durationMs);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            } else {
                vibrator.vibrate(duration);
            }
        } else {
            vibrator.cancel();
        }
    }

    private int clampAmplitude(int value) {
        return Math.min(255, Math.max(1, (int) ((value / 65535.0f) * 255)));
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < MAX_CONTROLLERS;
    }

    public boolean isVibrationEnabledForSlot(int slot) {
        return isValidSlot(slot) && vibrationEnabledSlots[slot];
    }

    public void setVibrationEnabledForSlot(int slot, boolean enabled) {
        if (isValidSlot(slot)) {
            vibrationEnabledSlots[slot] = enabled;
            preferences.edit().putBoolean("vibration_slot_" + slot, enabled).apply();
        }
    }

    public int getMaxControllers() {
        return MAX_CONTROLLERS;
    }

    private void handleRequest(byte requestCode, final int port) {
    }

    public void start() {
        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            try {
                localhost = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException ex) {}
        }
        running = true;
        initReceived = true;
        startSendThread();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress((InetAddress) null, SERVER_PORT));
                while (running) {
                    socket.receive(receivePacket);
                    synchronized (actions) {
                        receiveData.rewind();
                        byte requestCode = receiveData.get();
                        handleRequest(requestCode, receivePacket.getPort());
                    }
                }
            } catch (IOException e) {}
        });
    }

    public void sendGamepadState() {
        final ControlsProfile profile = activity.getInputControlsView().getProfile();
        if (profile == null) {
            releaseSlot(OSC_DEVICE_ID);
            return;
        }
        final GamepadState gamepadState = profile.getGamepadState();
        final boolean useVirtualGamepad = profile.isVirtualGamepad()
                && activity.getInputControlsView().isShowTouchscreenControls();
        if (useVirtualGamepad) {
            int slot = assignSlot(OSC_DEVICE_ID);
            if (slot >= 0 && writers[slot] != null) {
                writers[slot].writeGamepadState(gamepadState);
            }
        } else {
            releaseSlot(OSC_DEVICE_ID);
        }
    }

    public void sendGamepadState(ExternalController controller) {
        if (controller == null) return;
        ControlsProfile profile = activity.getInputControlsView().getProfile();
        if (profile != null) {
            ExternalController profileController = profile.getController(controller.getDeviceId());
            if (profileController != null && profileController.getControllerBindingCount() > 0) {
                int slot = assignSlot(controller.getDeviceId());
                if (slot >= 0 && writers[slot] != null) {
                    writers[slot].writeGamepadState(controller.remappedState);
                }
                return;
            }
        }
        int slot = assignSlot(controller.getDeviceId());
        if (slot >= 0 && writers[slot] != null) {
            writers[slot].writeGamepadState(controller.state);
        }
    }

    private int assignSlot(int deviceId) {
        Integer existing = deviceToSlot.get(deviceId);
        if (existing != null) return existing;
        for (int slot = 0; slot < MAX_CONTROLLERS; slot++) {
            if (!usedSlots.contains(slot)) {
                usedSlots.add(slot);
                deviceToSlot.put(deviceId, slot);
                if (fakeInputBasePath != null && writers[slot] == null) {
                    writers[slot] = new FakeInputWriter(fakeInputBasePath, slot);
                    writers[slot].open();
                    Log.d("WinHandler", "Assigned device " + deviceId + " to slot " + slot);
                }
                return slot;
            }
        }
        Log.w("WinHandler", "No slots available for device " + deviceId);
        return -1;
    }

    private void releaseSlot(int deviceId) {
        Integer slot = deviceToSlot.remove(deviceId);
        if (slot != null) {
            if (fallbackSlot == slot) fallbackSlot = -1;
            if (writers[slot] != null) {
                writers[slot].destroy();
                writers[slot] = null;
            }
            usedSlots.remove(slot);
            controllers.remove(deviceId);
            Log.d("WinHandler", "Device " + deviceId + " released slot: " + slot);
        }
    }

    public void setXInputDisabled(boolean disabled) {
        this.xinputDisabled = disabled;
        this.xinputDisabledInitialized = true;
    }

    public void setFakeInputPath(String fakeInputPath) {
        if (fakeInputPath != null && !fakeInputPath.isEmpty()) {
            this.fakeInputBasePath = fakeInputPath;
            Log.d("WinHandler", "FakeInputWriter base path set: " + fakeInputPath);
            startVibrationListener();
        }
    }

    public void closeFakeInputWriter() {
        if (inputManager != null && inputDeviceListener != null) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
        }
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            if (writers[i] != null) {
                writers[i].destroy();
                writers[i] = null;
            }
        }
        deviceToSlot.clear();
        usedSlots.clear();
        controllers.clear();
        fallbackSlot = -1;
        vibrationRunning = false;
        if (vibrationServer != null) {
            try {
                vibrationServer.close();
            } catch (IOException e) {}
            vibrationServer = null;
        }
    }

    private ExternalController getController(int deviceId) {
        if (controllers.containsKey(deviceId)) return controllers.get(deviceId);
        ExternalController controller = ExternalController.getController(deviceId);
        if (controller != null) controllers.put(deviceId, controller);
        return controller;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        ExternalController controller = getController(event.getDeviceId());
        if (controller != null) {
            boolean handled = controller.updateStateFromMotionEvent(event);
            if (handled) sendGamepadState(controller);
            return handled;
        }
        return false;
    }

    public boolean onKeyEvent(KeyEvent event) {
        ExternalController controller = getController(event.getDeviceId());
        if (controller != null && event.getRepeatCount() == 0) {
            boolean handled = controller.updateStateFromKeyEvent(event);
            if (handled) sendGamepadState(controller);
            return handled;
        }
        return false;
    }

    public byte getInputType() {
        return inputType;
    }

    public void setInputType(byte inputType) {
        this.inputType = inputType;
    }
}
