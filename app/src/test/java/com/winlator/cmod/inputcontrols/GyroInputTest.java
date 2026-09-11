package com.winlator.cmod.inputcontrols;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GyroInputTest {
    @Test
    public void configAndAimShapingStayBounded() {
        GyroInput.Config config = new GyroInput.Config();
        config.enabled = true;
        config.target = GyroInput.TARGET_MOUSE;
        config.mode = GyroInput.MODE_TILT;
        config.activator = GyroInput.ACTIVATOR_R3;
        config.activation = GyroInput.ACTIVATE_TOGGLE;
        config.sensitivity = 2.5f;
        config.deadzone = 0.1f;
        config.smoothing = 0.4f;
        config.invertX = true;

        GyroInput.Config restored = GyroInput.Config.decode(config.encode());
        assertEquals(config.encode(), restored.encode());
        assertEquals(0f, GyroInput.shape(0.05f, 0, 1, 0.1f)[0], 0.0001f);
        assertEquals(1f, GyroInput.shape(4, 0, 3, 0)[0], 0.0001f);
        assertEquals(0.02f, GyroInput.angleDelta(-(float)Math.PI + 0.01f,
                (float)Math.PI - 0.01f), 0.0001f);
    }
}
