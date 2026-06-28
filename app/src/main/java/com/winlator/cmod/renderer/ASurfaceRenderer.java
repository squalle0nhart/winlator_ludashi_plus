package com.winlator.cmod.renderer;

import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SurfaceFlinger host renderer (ASurfaceRenderer / "ASR").
 *
 * <p>Phase-1 scene compositor ported from GameNative PR #1582 (André Vito), built on StevenMX's
 * scanout work (the shared {@code VulkanRendererScanout.cpp} foundation already in this tree).
 * Each X11 window gets its own Android {@link android.view.SurfaceControl} layer (created in the
 * native lib) fed an AHardwareBuffer; SurfaceFlinger composites them directly — there is no
 * GL/Vulkan compositor pass.</p>
 *
 * <p>Structure mirrors Bannerlator's own {@code VulkanRenderer} (same X-server API, {@link XLock}
 * idioms, {@code collectWindows} scene walk, cursor path) so it slots into the existing
 * {@link WindowManager.OnWindowModificationListener} / {@link Pointer.OnPointerMotionListener}
 * machinery. Frame content is only pushed for windows that already carry a real AHardwareBuffer
 * (i.e. the DXVK/DRI3 game frame). CPU-only window chrome (the explorer desktop) is not yet
 * scanned out — that needs Drawable→AHB backing and is a follow-up.</p>
 */
public class ASurfaceRenderer implements HostRenderer,
        WindowManager.OnWindowModificationListener,
        Pointer.OnPointerMotionListener {

    private static final String TAG = "ASurfaceRenderer";
    private static final boolean NATIVE_LIBRARY_LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("asurface_renderer");
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "SurfaceFlinger renderer native library is unavailable", e);
        }
        NATIVE_LIBRARY_LOADED = loaded;
    }

    /** ASurfaceControl / ASurfaceTransaction require API 29+. */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && NATIVE_LIBRARY_LOADED;
    }

    public final XServerView xServerView;
    private final XServer xServer;
    private final ViewTransformation viewTransformation = new ViewTransformation();

    private int surfaceWidth;
    private int surfaceHeight;
    private boolean surfaceInitialized = false;

    // HostRenderer-backed state
    private boolean cursorVisible = true;     // container-level cursor toggle
    private boolean gameCursorVisible = true; // guest-requested cursor visibility
    private boolean fullscreen = false;
    private boolean screenOffsetYRelativeToCursor = false;
    private float magnifierZoom = 1.0f;
    private int fpsLimit = 0;
    private int fpsWindowId = -1;
    private String[] unviewableWMClasses = null;
    private Cursor lastCursor = null;
    private Object hudRef = null;

    // Per-window SurfaceControl bookkeeping (native owns the actual SC). Keyed by X11 window id.
    private static class WindowSurface {
        int width, height, zOrder;
        boolean visible = false;
        final Rect lastSrc = new Rect();
        final Rect lastDst = new Rect();
    }
    private final ConcurrentHashMap<Integer, WindowSurface> windowSurfaces = new ConcurrentHashMap<>();
    private final Object sceneLock = new Object();

    // Desktop (explorer.exe) geometry cache for placing desktop child windows.
    private Window desktopWindow = null;
    private Rect cachedDesktopDst = null;
    private int cachedDesktopSrcW = 0, cachedDesktopSrcH = 0;

    private static class RenderableWindow {
        Drawable content; Window window; int rootX, rootY;
        boolean isDesktopChild, isDesktopWindow;
        void set(Drawable c, Window w, int x, int y, boolean dc, boolean dw) {
            content = c; window = w; rootX = x; rootY = y; isDesktopChild = dc; isDesktopWindow = dw;
        }
    }
    private final ArrayList<RenderableWindow> renderList = new ArrayList<>();
    private int renderListSize = 0;

    public ASurfaceRenderer(XServerView xServerView, XServer xServer) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
    }

    // -------------------------------------------------------------------------
    // Surface lifecycle (driven by XServerView's SurfaceHolder.Callback)
    // -------------------------------------------------------------------------

    public void onSurfaceCreated(Surface surface) {
        if (surface == null) return;
        if (surfaceInitialized) {
            if (nativeReattachSurface(surface)) {
                updateScene();
                return;
            }
            nativeDestroy();
            surfaceInitialized = false;
        }
        surfaceInitialized = nativeInit(surface, xServer.screenInfo.width, xServer.screenInfo.height);
        if (surfaceInitialized) {
            nativeSetSfCallbackTarget(this);
            updateTransform();
            nativeInitScanout();
            sendCursorToNative(lastCursor);
            updateScene();
            Log.i(TAG, "SurfaceFlinger renderer context created (" +
                    xServer.screenInfo.width + "x" + xServer.screenInfo.height + ")");
        } else {
            Log.e(TAG, "nativeInit failed — SurfaceFlinger context not created");
        }
    }

    public void onSurfaceChanged(Surface surface, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
        if (!surfaceInitialized) {
            onSurfaceCreated(surface);
        } else {
            updateTransform();
            updateScene();
        }
    }

    public void onSurfaceDestroyed() {
        if (surfaceInitialized) {
            nativeDestroyScanout();
            nativeDestroy();
            surfaceInitialized = false;
        }
        windowSurfaces.clear();
        cachedDesktopDst = null;
    }

    private void updateTransform() {
        if (!surfaceInitialized) return;
        nativeScanoutSetDst(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY,
                viewTransformation.viewWidth, viewTransformation.viewHeight);
    }

    // -------------------------------------------------------------------------
    // Scene walk: build the window render list and sync SurfaceControl layers.
    // -------------------------------------------------------------------------

    public void updateScene() {
        if (!surfaceInitialized) return;
        synchronized (sceneLock) {
            renderListSize = 0;
            try (XLock xl = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
                collectWindows(xServer.windowManager.rootWindow,
                        xServer.windowManager.rootWindow.getX(),
                        xServer.windowManager.rootWindow.getY());
            }
            pushRenderList();
        }
    }

    private void collectWindows(Window window, int x, int y) {
        if (!window.attributes.isMapped()) return;
        if (window != xServer.windowManager.rootWindow) {
            RenderableWindow rw;
            if (renderList.size() <= renderListSize) {
                rw = new RenderableWindow();
                renderList.add(rw);
            } else {
                rw = renderList.get(renderListSize);
            }
            renderListSize++;
            rw.set(window.getContent(), window, x, y, isDesktopChild(window), window == findDesktopWindow());
        }
        for (Window child : window.getChildren()) {
            collectWindows(child, child.getX() + x, child.getY() + y);
        }
    }

    private void pushRenderList() {
        nativeBeginTransaction();
        HashSet<Integer> visibleIds = new HashSet<>();
        for (int i = 0; i < renderListSize; i++) {
            RenderableWindow rw = renderList.get(i);
            if (rw.content == null) continue;
            if (isUnviewable(rw.window)) continue;

            int contentId = rw.window.id;
            visibleIds.add(contentId);

            String debugName = rw.window.getClassName();
            if (debugName == null || debugName.isEmpty()) debugName = "(x11_window)";

            Rect src = new Rect(), dst = new Rect();
            boolean geometryOk = computeWindowRect(rw.rootX, rw.rootY,
                    rw.content.width, rw.content.height, rw.isDesktopWindow, rw.isDesktopChild, src, dst);

            WindowSurface ws = getOrCreateWindowSurface(contentId, rw.content.width, rw.content.height, debugName);
            if (ws == null) continue;

            boolean needsUpdate = !ws.visible || ws.zOrder != i
                    || !dst.equals(ws.lastDst) || !src.equals(ws.lastSrc);
            if (geometryOk && needsUpdate) {
                ws.visible = true;
                ws.zOrder = i;
                ws.lastDst.set(dst);
                ws.lastSrc.set(src);
                nativeUpdateWindow(contentId, true, i,
                        src.left, src.top, src.right, src.bottom,
                        dst.left, dst.top, dst.right, dst.bottom);
            }
        }
        // Hide windows that dropped out of the render list.
        for (Map.Entry<Integer, WindowSurface> e : windowSurfaces.entrySet()) {
            if (!visibleIds.contains(e.getKey()) && e.getValue().visible) {
                e.getValue().visible = false;
                nativeUpdateWindow(e.getKey(), false, e.getValue().zOrder, 0, 0, 0, 0, 0, 0, 0, 0);
            }
        }
        nativeApplyTransaction();
    }

    private WindowSurface getOrCreateWindowSurface(int contentId, int w, int h, String debugName) {
        WindowSurface ws = windowSurfaces.get(contentId);
        if (ws != null && (ws.width != w || ws.height != h)) {
            windowSurfaces.remove(contentId);
            nativeUnregisterWindowSC(contentId);
            ws = null;
        }
        if (ws == null) {
            ws = new WindowSurface();
            ws.width = w; ws.height = h;
            nativeRegisterWindowSC(contentId, debugName);
            windowSurfaces.put(contentId, ws);
        }
        return ws;
    }

    // -------------------------------------------------------------------------
    // Desktop / geometry helpers (ported from GameNative ASR).
    // -------------------------------------------------------------------------

    private Window findDesktopWindow() {
        if (desktopWindow != null && desktopWindow.attributes.isMapped()) return desktopWindow;
        desktopWindow = null;
        for (Window child : xServer.windowManager.rootWindow.getChildren()) {
            if (!child.attributes.isOverrideRedirect() && "explorer.exe".equals(child.getClassName())) {
                desktopWindow = child;
                break;
            }
        }
        return desktopWindow;
    }

    private boolean isDesktopChild(Window window) {
        Window desktop = findDesktopWindow();
        if (desktop == null) return false;
        Window parent = window.getParent();
        while (parent != null) {
            if (parent == desktop) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isUnviewable(Window window) {
        if (unviewableWMClasses == null) return false;
        String wc = window.getClassName();
        if (wc == null) return false;
        for (String cls : unviewableWMClasses) if (wc.contains(cls)) return true;
        return false;
    }

    private boolean computeWindowRect(int rootX, int rootY, int w, int h,
                                      boolean isDesktopWindow, boolean isDesktopChild,
                                      Rect outSrc, Rect outDst) {
        outSrc.set(0, 0, w, h);
        // Uniform map from X-screen space to the letterboxed surface region. `aspect` is the
        // surface-pixels-per-X-pixel scale (viewWidth/screenWidth) and viewOffset is the
        // letterbox bar. rootX/rootY are already root-relative X-screen coords, so every window
        // (desktop, child, top-level) scales identically — no special desktop casing needed.
        // (Previously used the normalized sceneScaleX (~1.0), which left the game at native size
        // in the top-left corner instead of filling the surface.)
        float s = viewTransformation.aspect > 0f ? viewTransformation.aspect : 1f;
        int dstL = viewTransformation.viewOffsetX + Math.round(rootX * s);
        int dstT = viewTransformation.viewOffsetY + Math.round(rootY * s);
        outDst.set(dstL, dstT, dstL + Math.round(w * s), dstT + Math.round(h * s));
        return adjustRectLT(outSrc, outDst);
    }

    private boolean adjustRectLT(Rect src, Rect dst) {
        final int originalDstW = dst.width(), originalDstH = dst.height();
        if (originalDstW <= 0 || originalDstH <= 0) return false;
        if (dst.left < 0) {
            int clip = -dst.left;
            src.left += (int) (((long) clip * src.width()) / originalDstW);
            dst.left = 0;
        }
        if (dst.top < 0) {
            int clip = -dst.top;
            src.top += (int) (((long) clip * src.height()) / originalDstH);
            dst.top = 0;
        }
        return src.right > src.left && src.bottom > src.top && dst.right > dst.left && dst.bottom > dst.top;
    }

    // -------------------------------------------------------------------------
    // Frame content: push a window's AHardwareBuffer to its SurfaceControl.
    // Only windows that already carry a real AHB (the DXVK/DRI3 game frame) are
    // scanned out; CPU-only chrome is skipped for now.
    // -------------------------------------------------------------------------

    @Override
    public void onUpdateWindowContent(Window window) {
        if (!surfaceInitialized) return;
        Drawable drawable = window.getContent();
        if (drawable == null || !window.attributes.isMapped() || isUnviewable(window)) return;
        pushWindowBuffer(window.id, drawable);
    }

    /** Direct present entry point for PresentExtension (the DXVK FLIP/AHB path). */
    public void presentWindow(Window window, Drawable pixmap) {
        if (!surfaceInitialized || pixmap == null) return;
        if (!window.attributes.isMapped() || isUnviewable(window)) return;
        pushWindowBuffer(window.id, pixmap);
    }

    private void pushWindowBuffer(int windowId, Drawable drawable) {
        if (!windowSurfaces.containsKey(windowId)) return; // SC not created yet; updateScene will
        synchronized (drawable.renderLock) {
            if (drawable.getTexture() instanceof GPUImage) {
                long ahbPtr = ((GPUImage) drawable.getTexture()).getHardwareBufferPtr();
                if (ahbPtr != 0) {
                    nativeSetWindowBuffer(windowId, ahbPtr, -1, windowId, 0);
                    if (hudFrameTick != null) hudFrameTick.accept(windowId);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cursor.
    // -------------------------------------------------------------------------

    private void sendCursorToNative(Cursor cursor) {
        if (!surfaceInitialized || !cursorVisible) return;
        if (cursor != null && !cursor.isVisible()) return;
        Drawable cd = cursor != null ? cursor.cursorImage : null;
        if (cd == null || cd.getBuffer() == null) return;
        synchronized (cd.renderLock) {
            ByteBuffer buf = cd.getBuffer();
            nativeScanoutSetCursorImage(buf, cd.width, cd.height, (short) (buf.capacity() / (cd.height * 4)));
        }
    }

    @Override
    public void onPointerMove(short x, short y) {
        if (!surfaceInitialized) return;
        Window pw = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = pw != null ? pw.attributes.getCursor() : null;
        if (cursor != null) {
            if (cursor != lastCursor) { lastCursor = cursor; sendCursorToNative(cursor); }
            if (gameCursorVisible != cursor.isVisible()) {
                gameCursorVisible = cursor.isVisible();
                nativeScanoutSetCursorVisibility(cursorVisible && gameCursorVisible);
            }
            nativeScanoutSetCursorPos(x, y, (short) cursor.hotSpotX, (short) cursor.hotSpotY,
                    cursorVisible && gameCursorVisible);
        }
    }

    // -------------------------------------------------------------------------
    // Window lifecycle listeners.
    // -------------------------------------------------------------------------

    @Override public void onMapWindow(Window window) { updateScene(); }

    @Override
    public void onUnmapWindow(Window window) {
        windowSurfaces.remove(window.id);
        if (surfaceInitialized) nativeUnregisterWindowSC(window.id);
        updateScene();
    }

    @Override
    public void onDestroyWindow(Window window) {
        if (window == desktopWindow) desktopWindow = null;
        windowSurfaces.remove(window.id);
        if (surfaceInitialized) nativeUnregisterWindowSC(window.id);
        updateScene();
    }

    @Override public void onChangeWindowZOrder(Window window) { updateScene(); }

    @Override
    public void onUpdateWindowGeometry(Window window, boolean resized) {
        if (resized) {
            windowSurfaces.remove(window.id);
            if (surfaceInitialized) nativeUnregisterWindowSC(window.id);
        }
        updateScene();
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            Window pw = xServer.inputDeviceManager.getPointWindow();
            if (pw == window) { lastCursor = window.attributes.getCursor(); sendCursorToNative(lastCursor); }
        }
    }

    /** Invoked from native (libasurface_renderer) when a scanout frame is latched by SurfaceFlinger.
     *  The per-present HUD tick happens in pushWindowBuffer; nothing extra needed here for now. */
    @androidx.annotation.Keep
    public void onScanoutFrameComplete(long packed) {
    }

    // Ticked once per presented game frame (the activity wires this, gating on its FPS window),
    // mirroring VulkanRenderer.setHudFrameTick — the perf HUD is otherwise never driven under ASR.
    private java.util.function.IntConsumer hudFrameTick = null;
    public void setHudFrameTick(java.util.function.IntConsumer c) { hudFrameTick = c; }

    // -------------------------------------------------------------------------
    // HostRenderer
    // -------------------------------------------------------------------------

    @Override public XServerView getXServerView() { return xServerView; }
    @Override public void setRenderingEnabled(boolean enabled) { xServer.setRenderingEnabled(enabled); }
    @Override public void requestRender() { /* ASR presents via SurfaceFlinger transactions */ }
    @Override public void forceCleanup() { onSurfaceDestroyed(); }

    @Override
    public void setCursorVisible(boolean visible) {
        this.cursorVisible = visible;
        if (!surfaceInitialized) return;
        if (visible) sendCursorToNative(lastCursor);
        else nativeScanoutSetCursorVisibility(false);
    }

    @Override public boolean isCursorVisible() { return cursorVisible; }

    @Override
    public void setUnviewableWMClasses(String wmClasses) {
        this.unviewableWMClasses = wmClasses != null ? wmClasses.split(";") : null;
    }

    @Override public void setFilterMode(int mode) { /* ASR has no shader/filter pass */ }
    @Override public void setMagnifierZoom(float zoom) { this.magnifierZoom = zoom; }
    @Override public float getMagnifierZoom() { return magnifierZoom; }
    @Override public void toggleFullscreen() { fullscreen = !fullscreen; updateScene(); }
    @Override public boolean isFullscreen() { return fullscreen; }
    @Override public void setScreenOffsetYRelativeToCursor(boolean b) { screenOffsetYRelativeToCursor = b; }
    @Override public boolean isScreenOffsetYRelativeToCursor() { return screenOffsetYRelativeToCursor; }
    @Override public void setFpsWindowId(int id) { this.fpsWindowId = id; }
    @Override public void setFrameRating(Object fr) { this.hudRef = fr; }
    @Override public int getFpsLimit() { return fpsLimit; }
    @Override public void setFpsLimit(int limit) { this.fpsLimit = limit; }
    @Override public int getSurfaceWidth() { return surfaceWidth; }
    @Override public int getSurfaceHeight() { return surfaceHeight; }

    // -------------------------------------------------------------------------
    // Native contract (libasurface_renderer.so).
    // -------------------------------------------------------------------------

    private native boolean nativeInit(Surface surface, int screenWidth, int screenHeight);
    private native void nativeDestroy();
    private native void nativeInitScanout();
    private native boolean nativeReattachSurface(Surface surface);
    private native void nativeDestroyScanout();
    private native void nativeSetWindowBuffer(long contentId, long ahbPtr, int fenceFd, long windowId, long serial);
    private native void nativeScanoutSetCursorVisibility(boolean visible);
    private native void nativeRegisterWindowSC(long contentId, String debugName);
    private native void nativeUnregisterWindowSC(long contentId);
    private native void nativeScanoutSetCursorImage(java.nio.ByteBuffer pixels, short w, short h, short stride);
    private native void nativeScanoutSetCursorPos(short x, short y, short hotX, short hotY, boolean cursorVisible);
    private native void nativeScanoutSetDst(int x, int y, int w, int h);
    private native void nativeSetSfCallbackTarget(Object rendererRef);
    private native void nativeBeginTransaction();
    private native void nativeApplyTransaction();
    private native void nativeUpdateWindow(long contentId, boolean visible, int zOrder,
            int srcL, int srcT, int srcR, int srcB,
            int dstL, int dstT, int dstR, int dstB);
}
