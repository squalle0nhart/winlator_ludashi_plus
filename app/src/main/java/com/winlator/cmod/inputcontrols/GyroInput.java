package com.winlator.cmod.inputcontrols;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.WinHandler;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public class GyroInput implements SensorEventListener {
    public static final String EXTRA = "gyroConfig";
    public static final int TARGET_RIGHT_STICK = 0;
    public static final int TARGET_LEFT_STICK = 1;
    public static final int TARGET_MOUSE = 2;
    public static final int MODE_RATE = 0;
    public static final int MODE_TILT = 1;
    public static final int ACTIVATOR_ALWAYS = 0;
    public static final int ACTIVATOR_L1 = 1;
    public static final int ACTIVATOR_L2 = 2;
    public static final int ACTIVATOR_R1 = 3;
    public static final int ACTIVATOR_R3 = 4;
    public static final int ACTIVATE_HOLD = 0;
    public static final int ACTIVATE_TOGGLE = 1;

    private static final String PREF_BIAS_X = "gyro_bias_x";
    private static final String PREF_BIAS_Y = "gyro_bias_y";
    private static final String PREF_BIAS_Z = "gyro_bias_z";
    private static final int CALIBRATION_SAMPLES = 60;
    private static final float CALIBRATION_MOTION_LIMIT = 0.15f;

    private final Activity activity;
    private final WinHandler winHandler;
    private final SharedPreferences preferences;
    private final BooleanSupplier activatorPressed;
    private final SensorManager sensorManager;
    private final Sensor gyroscope;
    private final Sensor rotationVector;
    private Config config = new Config();
    private boolean resumed;
    private boolean registered;
    private boolean toggleActive;
    private boolean previousActivator;
    private boolean outputActive;
    private boolean haveOrientation;
    private boolean haveNeutral;
    private float azimuth;
    private float pitch;
    private float neutralAzimuth;
    private float neutralPitch;
    private float filteredX;
    private float filteredY;
    private float mouseRemainderX;
    private float mouseRemainderY;
    private long lastOutputNs;
    private int calibrationSamples;
    private float calibrationX;
    private float calibrationY;
    private float calibrationZ;
    private Runnable calibrationFinished;

    public GyroInput(Activity activity, WinHandler winHandler, SharedPreferences preferences,
                     BooleanSupplier activatorPressed) {
        this.activity = activity;
        this.winHandler = winHandler;
        this.preferences = preferences;
        this.activatorPressed = activatorPressed;
        sensorManager = (SensorManager) activity.getSystemService(Activity.SENSOR_SERVICE);
        gyroscope = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        Sensor absoluteRotation = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) : null;
        rotationVector = absoluteRotation != null || sensorManager == null ? absoluteRotation
                : sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
    }

    public boolean isAvailable() {
        return gyroscope != null;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        clearOutput();
        this.config = config != null ? config : new Config();
        toggleActive = false;
        previousActivator = false;
        haveNeutral = false;
        refreshRegistration();
    }

    public void onResume() {
        resumed = true;
        refreshRegistration();
    }

    public void onPause() {
        resumed = false;
        refreshRegistration();
        clearOutput();
    }

    public boolean calibrate(Runnable finished) {
        if (!resumed || sensorManager == null || gyroscope == null) return false;
        calibrationSamples = 0;
        calibrationX = calibrationY = calibrationZ = 0;
        calibrationFinished = finished;
        if (registered) sensorManager.unregisterListener(this);
        registered = registerSensors(SensorManager.SENSOR_DELAY_FASTEST);
        if (!registered) calibrationFinished = null;
        return registered;
    }

    private void refreshRegistration() {
        boolean shouldRegister = resumed && sensorManager != null && gyroscope != null
                && (config.enabled || calibrationFinished != null);
        if (shouldRegister == registered) return;
        if (shouldRegister) {
            registered = registerSensors(SensorManager.SENSOR_DELAY_GAME);
        } else {
            sensorManager.unregisterListener(this);
            registered = false;
        }
    }

    private boolean registerSensors(int delay) {
        boolean result = sensorManager.registerListener(this, gyroscope, delay);
        if (rotationVector != null) sensorManager.registerListener(this, rotationVector, delay);
        return result;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if (calibrationFinished != null) collectCalibration(event.values);
            if (config.enabled && config.mode == MODE_RATE) {
                float[] rate = rotateToScreen(event.values[0] - preferences.getFloat(PREF_BIAS_X, 0),
                        event.values[1] - preferences.getFloat(PREF_BIAS_Y, 0));
                updateOutput(rate[1], rate[0], event.timestamp);
            }
        } else if (config.enabled && config.mode == MODE_TILT) {
            updateOrientation(event.values);
            updateOutput(angleDelta(azimuth, neutralAzimuth) / ((float)Math.PI / 4f),
                    (pitch - neutralPitch) / ((float)Math.PI / 4f), event.timestamp);
        }
    }

    private void collectCalibration(float[] values) {
        float motion = (float)Math.sqrt(values[0] * values[0] + values[1] * values[1]
                + values[2] * values[2]);
        if (motion > CALIBRATION_MOTION_LIMIT) {
            calibrationSamples = 0;
            calibrationX = calibrationY = calibrationZ = 0;
            return;
        }
        calibrationX += values[0];
        calibrationY += values[1];
        calibrationZ += values[2];
        if (++calibrationSamples < CALIBRATION_SAMPLES) return;
        preferences.edit()
                .putFloat(PREF_BIAS_X, calibrationX / calibrationSamples)
                .putFloat(PREF_BIAS_Y, calibrationY / calibrationSamples)
                .putFloat(PREF_BIAS_Z, calibrationZ / calibrationSamples)
                .apply();
        Runnable finished = calibrationFinished;
        calibrationFinished = null;
        sensorManager.unregisterListener(this);
        registered = false;
        refreshRegistration();
        if (finished != null) finished.run();
    }

    private void updateOrientation(float[] vector) {
        float[] matrix = new float[9];
        float[] remapped = new float[9];
        SensorManager.getRotationMatrixFromVector(matrix, vector);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) {
            axisX = SensorManager.AXIS_Y;
            axisY = SensorManager.AXIS_MINUS_X;
        } else if (rotation == Surface.ROTATION_180) {
            axisX = SensorManager.AXIS_MINUS_X;
            axisY = SensorManager.AXIS_MINUS_Y;
        } else if (rotation == Surface.ROTATION_270) {
            axisX = SensorManager.AXIS_MINUS_Y;
            axisY = SensorManager.AXIS_X;
        }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped);
        float[] orientation = new float[3];
        SensorManager.getOrientation(remapped, orientation);
        azimuth = orientation[0];
        pitch = orientation[1];
        haveOrientation = true;
    }

    private float[] rotateToScreen(float x, float y) {
        switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90: return new float[]{-y, x};
            case Surface.ROTATION_180: return new float[]{-x, -y};
            case Surface.ROTATION_270: return new float[]{y, -x};
            default: return new float[]{x, y};
        }
    }

    private void updateOutput(float rawX, float rawY, long timestamp) {
        boolean pressed = config.activator == ACTIVATOR_ALWAYS || activatorPressed.getAsBoolean();
        if (config.activator == ACTIVATOR_ALWAYS) toggleActive = true;
        else if (config.activation == ACTIVATE_TOGGLE && pressed && !previousActivator)
            toggleActive = !toggleActive;
        previousActivator = pressed;
        boolean active = config.activator == ACTIVATOR_ALWAYS
                || (config.activation == ACTIVATE_TOGGLE ? toggleActive : pressed);
        if (!active) {
            clearOutput();
            haveNeutral = false;
            return;
        }
        if (config.mode == MODE_TILT && !haveNeutral) {
            if (!haveOrientation) return;
            neutralAzimuth = azimuth;
            neutralPitch = pitch;
            haveNeutral = true;
            rawX = rawY = 0;
        }

        float[] shaped = shape(rawX, rawY, config.sensitivity, config.deadzone);
        float retain = config.smoothing;
        filteredX = retain * filteredX + (1f - retain) * shaped[0];
        filteredY = retain * filteredY + (1f - retain) * shaped[1];
        float x = config.invertX ? -filteredX : filteredX;
        float y = config.invertY ? -filteredY : filteredY;

        if (config.target == TARGET_MOUSE) {
            float seconds = lastOutputNs == 0 ? 0 : Math.min(0.05f, (timestamp - lastOutputNs) / 1_000_000_000f);
            mouseRemainderX += x * 900f * seconds;
            mouseRemainderY += y * 900f * seconds;
            int dx = (int)mouseRemainderX;
            int dy = (int)mouseRemainderY;
            mouseRemainderX -= dx;
            mouseRemainderY -= dy;
            if (dx != 0 || dy != 0) winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0);
        } else {
            winHandler.sendGyroStick(config.target == TARGET_RIGHT_STICK, x, y);
        }
        lastOutputNs = timestamp;
        outputActive = true;
    }

    private void clearOutput() {
        if (outputActive && config.target != TARGET_MOUSE) winHandler.clearGyroStick();
        outputActive = false;
        filteredX = filteredY = 0;
        mouseRemainderX = mouseRemainderY = 0;
        lastOutputNs = 0;
    }

    static float angleDelta(float value, float origin) {
        float delta = value - origin;
        while (delta > Math.PI) delta -= (float)(Math.PI * 2);
        while (delta < -Math.PI) delta += (float)(Math.PI * 2);
        return delta;
    }

    public static float[] shape(float x, float y, float sensitivity, float deadzone) {
        float magnitude = (float)Math.sqrt(x * x + y * y);
        if (magnitude <= deadzone) return new float[]{0, 0};
        float scaled = Math.min(1f, (magnitude - deadzone) / Math.max(0.0001f, 1f - deadzone) * sensitivity);
        return new float[]{x / magnitude * scaled, y / magnitude * scaled};
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public static class Config {
        public boolean enabled;
        public int target = TARGET_RIGHT_STICK;
        public int mode = MODE_RATE;
        public int activator = ACTIVATOR_ALWAYS;
        public int activation = ACTIVATE_HOLD;
        public float sensitivity = 1f;
        public float deadzone = 0.05f;
        public float smoothing = 0.25f;
        public boolean invertX;
        public boolean invertY;

        public String encode() {
            return String.format(Locale.US, "%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%d,%d",
                    enabled ? 1 : 0, target, mode, activator, activation, sensitivity,
                    deadzone, smoothing, invertX ? 1 : 0, invertY ? 1 : 0);
        }

        public static Config decode(String value) {
            Config config = new Config();
            if (value == null || value.isEmpty()) return config;
            try {
                String[] fields = value.split(",");
                if (fields.length != 10) return config;
                config.enabled = "1".equals(fields[0]);
                config.target = clamp(Integer.parseInt(fields[1]), TARGET_RIGHT_STICK, TARGET_MOUSE);
                config.mode = clamp(Integer.parseInt(fields[2]), MODE_RATE, MODE_TILT);
                config.activator = clamp(Integer.parseInt(fields[3]), ACTIVATOR_ALWAYS, ACTIVATOR_R3);
                config.activation = clamp(Integer.parseInt(fields[4]), ACTIVATE_HOLD, ACTIVATE_TOGGLE);
                config.sensitivity = clamp(Float.parseFloat(fields[5]), 0.05f, 5f);
                config.deadzone = clamp(Float.parseFloat(fields[6]), 0, 0.5f);
                config.smoothing = clamp(Float.parseFloat(fields[7]), 0, 0.95f);
                config.invertX = "1".equals(fields[8]);
                config.invertY = "1".equals(fields[9]);
            } catch (RuntimeException ignored) {
                return new Config();
            }
            return config;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static float clamp(float value, float min, float max) {
            if (!Float.isFinite(value)) return min;
            return Math.max(min, Math.min(max, value));
        }
    }
}
