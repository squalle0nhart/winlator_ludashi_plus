package com.winlator.star.core;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

/**
 * Standalone drop-in for Bannerlator's native {@code GPUInformation}.
 *
 * <p>In Bannerlator {@link #getRenderer} is a JNI method backed by {@code libwinlator.so} that probes
 * the guest driver. This library has no native blob, so {@code getRenderer} instead:
 * <ol>
 *   <li>returns an <b>injected</b> string when the integrator has called {@link #setRenderer} (the
 *       recommended path — feed it the real guest renderer / GPU model your emulator already knows);</li>
 *   <li>otherwise performs a one-off throwaway-EGL {@code GLES20.glGetString(GL_RENDERER)} probe and
 *       caches the result, so the Fusion HUD's GPU label + diagnostics still show a real device GPU
 *       name standalone.</li>
 * </ol>
 *
 * <p>{@link #getVulkanVersion} is likewise native upstream; standalone it returns an injected value
 * (see {@link #setVulkanVersion}) or {@code "Unknown"}. {@link #extractModelName} is copied verbatim
 * from upstream (self-contained regex, no native dependency) and {@link #isAdrenoGPU} is a substring
 * check, matching upstream semantics. Only the native probes differ from the app build — everything the
 * shipped HUD calls ({@code getRenderer}, {@code getVulkanVersion}) is satisfied.
 */
public abstract class GPUInformation {

    private static volatile String injectedRenderer = null;
    private static volatile String probedRenderer = null;
    private static volatile String injectedVulkanVersion = null;

    /** Inject the renderer string the HUD should display (e.g. your guest's reported GPU). */
    public static void setRenderer(String renderer) {
        injectedRenderer = renderer;
    }

    /**
     * Inject the Vulkan version string surfaced in the diagnostics report. In Bannerlator this is a
     * native probe (spins an ephemeral {@code VkInstance}); standalone we have no native blob, so the
     * integrator supplies it (or it reads {@code "Unknown"}).
     */
    public static void setVulkanVersion(String version) {
        injectedVulkanVersion = version;
    }

    /**
     * Vulkan version for the diagnostics report. Injected value if set, else {@code "Unknown"}. The
     * args mirror the upstream native signature so callers ({@code HudMetrics.buildDiagnosticsReport})
     * compile unchanged.
     */
    public static String getVulkanVersion(String driverName, Context context) {
        String v = injectedVulkanVersion;
        return v != null && !v.isEmpty() ? v : "Unknown";
    }

    /**
     * Renderer string for the HUD. Prefers an injected value; otherwise a cached GLES probe;
     * otherwise {@code "Unknown"}. The {@code driverName} / {@code context} args mirror the upstream
     * native signature so the copied HUD files call it unchanged.
     */
    public static String getRenderer(String driverName, Context context) {
        String injected = injectedRenderer;
        if (injected != null && !injected.isEmpty()) return injected;
        String probed = probedRenderer;
        if (probed == null) {
            probed = probeGlRenderer();
            probedRenderer = probed;
        }
        return probed != null ? probed : "Unknown";
    }

    public static boolean isAdrenoGPU(Context context) {
        return getRenderer(null, context).toLowerCase().contains("adreno");
    }

    /**
     * Extract a short GPU model (e.g. {@code "Adreno 750"}, {@code "Mali-G715"}, {@code "Xclipse 920"})
     * from a raw Vulkan/GL renderer string such as
     * {@code "zink Vulkan 1.4(Wrapper(Adreno (TM) 750) (MESA_TURNIP))"} — what the guest reports via
     * {@code _MESA_DRV_GPU_NAME}. Used for the perf-HUD GPU-model row so it shows the chip, not the whole
     * driver string. Falls back to the trimmed input when no known vendor token is found, so unknown
     * GPUs still show something rather than blank.
     */
    public static String extractModelName(String raw) {
        if (raw == null) return null;
        String s = raw.replace("(TM)", "").replace("(R)", "").replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher m;

        m = java.util.regex.Pattern.compile("Adreno\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) return "Adreno " + m.group(1);

        // ARM Immortalis-G### / Mali-<letter>### — keep the vendor-model form.
        m = java.util.regex.Pattern.compile("(Immortalis|Mali)[\\s-]*([A-Za-z]?\\d+[A-Za-z0-9]*)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) {
            String vendor = m.group(1);
            vendor = Character.toUpperCase(vendor.charAt(0)) + vendor.substring(1).toLowerCase();
            return vendor + "-" + m.group(2).toUpperCase();
        }

        m = java.util.regex.Pattern.compile("Xclipse\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) return "Xclipse " + m.group(1);

        m = java.util.regex.Pattern.compile("PowerVR\\s+([A-Za-z0-9]+(?:\\s+[A-Za-z0-9]+)?)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) return "PowerVR " + m.group(1);

        // Fallback for GPUs we have no explicit pattern for: strip the wrapper/driver scaffolding so a
        // raw "zink Vulkan X(Wrapper(<NAME>) (<DRIVER>))" never leaks "Wrapper" or the driver tag into
        // the HUD. Take the inner Wrapper(...) name when present, then drop MESA/driver tags and parens.
        java.util.regex.Matcher wrap = java.util.regex.Pattern.compile("(?i)wrapper\\(").matcher(s);
        if (wrap.find()) s = s.substring(wrap.end());
        s = s.replaceAll("(?i)\\(?\\bMESA[_A-Za-z0-9]*\\)?", " ") // MESA_TURNIP / (MESA...) driver tags
             .replaceAll("[()]", " ")
             .replaceAll("\\s+", " ")
             .trim();
        return s;
    }

    /** One-off throwaway-EGL probe of {@code GL_RENDERER}. Returns null on any failure. */
    private static String probeGlRenderer() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return null;
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null;
        EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            int[] configAttribs = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
                    || numConfigs[0] == 0) {
                return null;
            }
            int[] ctxAttribs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
            eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) return null;
            int[] pbufferAttribs = { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE };
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufferAttribs, 0);
            if (surface == EGL14.EGL_NO_SURFACE) return null;
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return null;
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            return renderer != null && !renderer.isEmpty() ? renderer : null;
        } catch (Throwable t) {
            return null;
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
            EGL14.eglTerminate(display);
        }
    }
}
