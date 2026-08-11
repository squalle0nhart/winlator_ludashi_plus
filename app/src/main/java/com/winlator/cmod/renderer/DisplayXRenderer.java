package com.winlator.cmod.renderer;

import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.CursorManager;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;

import java.util.HashSet;

/** HostRenderer adapter for Pipetto's native DisplayX renderer. */
public final class DisplayXRenderer implements HostRenderer,
        SurfaceHolder.Callback,
        WindowManager.OnWindowModificationListener,
        Pointer.OnPointerMotionListener,
        CursorManager.OnCursorModificationListener {
    private final XServerView xServerView;
    private final XServer xServer;
    private final SurfaceView surfaceView;
    private boolean cursorVisible = true;
    private boolean renderingEnabled = true;
    private boolean stopped;
    private boolean fullscreen;
    private boolean screenOffsetYRelativeToCursor;
    private float magnifierZoom = 1.0f;
    private int fullscreenMode = Container.FULLSCREEN_OFF;
    private int fpsLimit;
    private int surfaceWidth;
    private int surfaceHeight;
    private String unviewableWMClass;
    private final HashSet<Long> registeredDirectContents = new HashSet<>();

    public DisplayXRenderer(XServerView xServerView, XServer xServer, SurfaceView surfaceView) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        this.surfaceView = surfaceView;
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
        xServer.cursorManager.addOnCursorModificationListener(this);
        surfaceView.getHolder().addCallback(this);
        xServerView.nativeInit(xServerView.getContext(), xServer);
    }

    public void presentWindow(Window window, Drawable drawable) {
        if (renderingEnabled && window != null && drawable != null) {
            long key = ((long) window.id << 32) | (drawable.id & 0xffffffffL);
            if (registeredDirectContents.add(key)) {
                xServerView.nativeAddDirectContent(window.id, drawable);
            }
            xServerView.nativeUpdateDirectContent(window.id, drawable.id);
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        xServerView.nativeCreateSurface(holder.getSurface());
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        xServerView.nativeChangeSurface(width, height);
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        xServerView.nativeDestroySurface();
    }

    @Override public void onCreateWindow(Window window, Window parent) {
        xServerView.nativeCreateWindow(window, parent.id);
    }

    @Override public void onDestroyWindow(Window window) {
        registeredDirectContents.removeIf(key -> (int) (key >> 32) == window.id);
        xServerView.nativeDestroyWindow(window.id);
    }

    @Override public void onMapWindow(Window window) {
        if (unviewableWMClass != null && window.getClassName().contains(unviewableWMClass)
                && window.attributes.isEnabled()) {
            window.disableAllDescendants();
        }
        xServerView.nativeMapWindow(window.id);
    }

    @Override public void onUnmapWindow(Window window) {
        xServerView.nativeUnmapWindow(window.id);
    }

    @Override
    public void onChangeWindowZOrder(Window.StackMode stackMode, Window window, Window sibling) {
        xServerView.nativeChangeWindowZOrder(stackMode == Window.StackMode.ABOVE ? 1 : 0,
                window.id, sibling != null ? sibling.id : -1);
    }

    @Override public void onUpdateWindowContent(Window window) {
        if (renderingEnabled) xServerView.nativeUpdateWindowContent(window.id);
    }

    @Override public void onUpdateWindowGeometry(Window window, boolean resized) {
        xServerView.nativeUpdateWindowGeometry(window.id, window.getWidth(), window.getHeight(),
                window.getX(), window.getY(), resized);
    }

    @Override public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            Cursor cursor = window.attributes.getCursor();
            if (cursor != null) xServerView.nativeBindCursor(window.id, cursor.id, cursor.isVisible());
        }
    }

    @Override public void onModifyWindowProperty(Window window, Property property) {
        if ("WM_CLASS".equals(property.nameAsString())) {
            xServerView.nativeSetWindowClassName(window.id, property.toString());
        }
    }

    @Override public void onReparentWindow(Window window, Window newParent) {
        xServerView.nativeReparentWindow(window.id, newParent.id);
    }

    @Override public void onPointerMove(short x, short y) {
        xServerView.nativePointerMove(x, y);
    }

    @Override public void onCreateCursor(Cursor cursor) {
        xServerView.nativeCreateCursor(cursor);
    }

    @Override public void onFreeCursor(Cursor cursor) {
        xServerView.nativeFreeCursor(cursor.id);
    }

    @Override public XServerView getXServerView() { return xServerView; }
    @Override public void setRenderingEnabled(boolean enabled) { renderingEnabled = enabled; }
    @Override public void requestRender() {}

    @Override public void forceCleanup() {
        if (stopped) return;
        stopped = true;
        xServer.windowManager.removeOnWindowModificationListener(this);
        xServer.pointer.removeOnPointerMotionListener(this);
        xServer.cursorManager.removeOnCursorModificationListener(this);
        xServerView.nativeStop();
    }

    @Override public void setCursorVisible(boolean visible) {
        cursorVisible = visible;
        xServerView.nativeSetCursorVisible(visible);
    }
    @Override public boolean isCursorVisible() { return cursorVisible; }
    @Override public void setUnviewableWMClasses(String classes) {
        unviewableWMClass = classes == null ? null : classes.split(",")[0].trim();
        xServerView.nativeSetUnviewableWMClass(unviewableWMClass);
    }
    @Override public void setFilterMode(int mode) {}
    @Override public void setMagnifierZoom(float zoom) {
        magnifierZoom = zoom;
        xServerView.nativeSetMagnifierZoom(zoom);
    }
    @Override public float getMagnifierZoom() { return magnifierZoom; }
    @Override public void toggleFullscreen() {
        fullscreen = !fullscreen;
        xServerView.nativeToggleFullscreen();
    }
    @Override public boolean isFullscreen() { return fullscreen; }
    @Override public void setFullscreenMode(int mode) {
        boolean nextFullscreen = mode != Container.FULLSCREEN_OFF;
        if (nextFullscreen != fullscreen) toggleFullscreen();
        fullscreenMode = mode;
    }
    @Override public int getFullscreenMode() { return fullscreenMode; }
    @Override public void setScreenOffsetYRelativeToCursor(boolean enabled) {
        screenOffsetYRelativeToCursor = enabled;
        xServerView.nativeSetScreenOffsetYRelativeToCursor(enabled);
    }
    @Override public boolean isScreenOffsetYRelativeToCursor() {
        return screenOffsetYRelativeToCursor;
    }
    @Override public void setFpsWindowId(int id) {}
    @Override public void setFrameRating(Object frameRating) {}
    @Override public int getFpsLimit() { return fpsLimit; }
    @Override public void setFpsLimit(int limit) { fpsLimit = limit; }
    @Override public int getSurfaceWidth() { return surfaceWidth; }
    @Override public int getSurfaceHeight() { return surfaceHeight; }

    public void onPause() { xServerView.nativePause(); }
    public void onResume() { xServerView.nativeResume(); }
}
