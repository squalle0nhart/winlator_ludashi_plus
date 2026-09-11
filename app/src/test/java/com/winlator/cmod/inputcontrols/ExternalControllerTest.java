package com.winlator.cmod.inputcontrols;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExternalControllerTest {
    @Test
    public void triggerModesPreserveAnalogAndSupportDigitalFallback() {
        assertEquals(0.35f, ExternalController.resolveTriggerValue(ExternalController.TRIGGER_IS_AXIS, 0.35f, false), 0f);
        assertEquals(0f, ExternalController.resolveTriggerValue(ExternalController.TRIGGER_IS_BUTTON, 0.35f, false), 0f);
        assertEquals(0f, ExternalController.resolveTriggerValue(ExternalController.TRIGGER_IS_BUTTON, 0.75f, false), 0f);
        assertEquals(1f, ExternalController.resolveTriggerValue(ExternalController.TRIGGER_IS_BUTTON, 0.75f, true), 0f);
        assertEquals(1f, ExternalController.resolveTriggerValue(ExternalController.TRIGGER_IS_BOTH, 0f, true), 0f);
        assertEquals(0.35f, ExternalController.resolveTriggerValue(ExternalController.TRIGGER_IS_BOTH, 0.35f, true), 0f);
        assertEquals(ExternalController.TRIGGER_IS_AXIS, ExternalController.normalizeTriggerType(-1));
        assertEquals(ExternalController.TRIGGER_IS_BOTH, ExternalController.normalizeTriggerType(ExternalController.TRIGGER_IS_BOTH));
    }
}
