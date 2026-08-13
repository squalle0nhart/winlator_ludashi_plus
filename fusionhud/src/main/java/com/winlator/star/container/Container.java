package com.winlator.star.container;

/**
 * Standalone shim of Bannerlator's {@code Container} exposing ONLY the constants the HUD subsystem
 * references. The real Bannerlator {@code Container} is a large Room-backed entity describing a Wine
 * prefix; the HUD only ever reads these two static defaults, so nothing else is reproduced here.
 *
 * <p>Values are copied verbatim from Bannerlator's {@code Container.java} so behaviour is identical.
 * {@link #DEFAULT_FPS_COUNTER_CONFIG} is the canonical config string the HUD views parse via
 * {@link com.winlator.star.core.KeyValueSet}; integrators can pass their own to {@code applyConfig}.
 */
public class Container {
    public static final int DEFAULT_HUD_SCALE = 100;
    public static final String DEFAULT_FPS_COUNTER_CONFIG = "hudMode=horizontal,showFPS=1,showCPULoad=1,showGPULoad=1,showRAM=1,showRenderer=1,showBatteryTemp=1,hudScale=" + DEFAULT_HUD_SCALE + ",hudSize=full,showVram=1,showLow001=1,fpsDecimal=1,hudLocked=0,showPerCore=1,showSwap=1,showNet=1,showResolution=1,showProton=1,showWrapper=1,showDxVer=1,showSession=1";
}
