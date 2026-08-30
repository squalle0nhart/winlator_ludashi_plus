package com.winlator.cmod.widget;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import androidx.annotation.Keep;
import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.CursorManager;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;
import dalvik.annotation.optimization.FastNative;
import java.nio.ByteBuffer;

public class DisplayXServerView extends XServerRendererView implements SurfaceHolder.Callback, WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener, CursorManager.OnCursorModificationListener {
    private Context context;
    private boolean fullscreen = false;
    private String[] unviewableWMClasses = null;
    private boolean cursorVisible = true;
    private int fpsWindowId = -1;
    private WinlatorHUD hudRef = null;
    private FrameRating classicHudRef = null;
    private boolean pipMode = false;
    private volatile int fpsLimit = 0;

    private static volatile boolean nativeLibLoaded = false;

    public static synchronized void loadNativeLibrary() {
        if (!nativeLibLoaded) {
            System.loadLibrary("winlator");
            nativeLibLoaded = true;
        }
    }

    public DisplayXServerView(Context context, XServer xserver) {
        this(context, xserver, true, true);
    }

    public DisplayXServerView(Context context, XServer xserver,
                              boolean performanceMode,
                              boolean presentAtRefreshRate) {
        super(context, xserver);
        this.context = context;
        getHolder().addCallback(this);
        xServer.setXServerView(this);
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
        xServer.cursorManager.addOnCursorModificationListener(this);
        nativeInit(this.context, xServer, getDisplayRefreshRate(context),
                   performanceMode, presentAtRefreshRate);
    }

    private static float getDisplayRefreshRate(Context context) {
        android.view.WindowManager manager = (android.view.WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null || manager.getDefaultDisplay() == null) return 60.0f;
        float refreshRate = manager.getDefaultDisplay().getRefreshRate();
        return refreshRate > 1.0f ? refreshRate : 60.0f;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        nativeCreateSurface(getHolder().getSurface());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        nativeDestroySurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        nativeChangeSurface(width, height);
    }

    @Override
    public void onDestroy() {
        nativeStop();
    }

    @Override
    public void onPause() {
        nativePause();
    }

    @Override
    public void onResume() {
        nativeResume();
    }

    @Override
    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        nativeToggleFullscreen();
    }

    @Override
    public void setCursorVisible(boolean cursorVisible) {
        this.cursorVisible = cursorVisible;
        nativeSetCursorVisible(cursorVisible);
    }

    @Override
    public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
        nativeSetScreenOffsetYRelativeToCursor(screenOffsetYRelativeToCursor);
    }

    @Override
    public boolean isFullscreen() {
        return fullscreen;
    }

    @Override
    public float getMagnifierZoom() {
        return 1.0f;
    }

    @Override
    public void setMagnifierZoom(float magnifierZoom) {
        nativeSetMagnifierZoom(magnifierZoom);
    }

    @Override
    public void setUnviewableWMClasses(String... classes) {
        this.unviewableWMClasses = classes;
    }

    @Override
    public void onCreateWindow(Window window, Window parent) {
        nativeCreateWindow(window, parent.id);
    }

    @Override
    public void onDestroyWindow(Window window) {
        nativeDestroyWindow(window.id);
    }

    @Override
    public void onMapWindow(Window window) {
        if (unviewableWMClasses != null) {
            String wmClass = window.getClassName();
            for (String cls : unviewableWMClasses) {
                if (wmClass.contains(cls)) {
                    if (window.attributes.isEnabled()) {
                        window.disableAllDescendants();
                    }
                    break;
                }
            }
        }

        nativeMapWindow(window.id);
    }

    @Override
    public void onUnmapWindow(Window window) {
        nativeUnmapWindow(window.id);
    }

    @Override
    public void onChangeWindowZOrder(Window window) {
        Window parent = window.getParent();
        if (parent == null) return;
        int previousId = -1;
        for (Window sibling : parent.getChildren()) {
            nativeChangeWindowZOrder(1, sibling.id, previousId);
            previousId = sibling.id;
        }
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        nativeUpdateWindowContent(window.id);
    }

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable) {
        nativeUpdateDirectContent(window.id, drawable.id, (short) 0, (short) 0);
    }

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable, short xOff, short yOff) {
        if (window.id == fpsWindowId) {
            if (hudRef != null) hudRef.onFrame();
            if (classicHudRef != null) classicHudRef.update();
        }
        nativeUpdateDirectContent(window.id, drawable.id, xOff, yOff);
    }

    @Override
    public void onUpdateWindowGeometry(final Window window, boolean resized) {
        nativeUpdateWindowGeometry(window.id, window.getWidth(), window.getHeight(), window.getX(), window.getY(), resized);
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            Cursor cursor = window.attributes.getCursor();
            if (cursor != null)
                nativeBindCursor(window.id, cursor.id, cursor.isVisible());
        }
    }

    @Override
    public void onReparentWindow(Window window, Window newParent) {
        nativeReparentWindow(window.id, newParent.id);
    }

    @Override
    public void onPointerMove(short x, short y) {
        nativePointerMove(x, y);
    }

    @Override
    public void onCreateCursor(Cursor cursor) {
        nativeCreateCursor(cursor);
    }

    @Override
    public void onFreeCursor(Cursor cursor) {
        nativeFreeCursor(cursor.id);
    }

    @FastNative
    public native void nativeCreateSurface(Surface surface);
    @FastNative
    public native void nativeDestroySurface();
    @FastNative
    public native void nativeInit(Context context, XServer xserver, float refreshRate,
                                  boolean performanceMode,
                                  boolean presentAtRefreshRate);
    @FastNative
    public native void nativeChangeSurface(int width, int height);
    @FastNative
    public native void nativeCreateWindow(Window window, int parentId);
    @FastNative
    public native void nativeDestroyWindow(int id);
    @FastNative
    public native void nativeCreateCursor(Cursor cursor);
    @FastNative
    public native void nativeFreeCursor(int id);
    @FastNative
    public native void nativeBindCursor(int windowId, int cursorId, boolean visible);
    @FastNative
    public native void nativeMapWindow(int id);
    @FastNative
    public native void nativeUnmapWindow(int id);
    @FastNative
    public native void nativeChangeWindowZOrder(int stackMode, int id, int siblingId);
    @FastNative
    public native void nativeUpdateWindowGeometry(int id, int width, int height, int x, int y, boolean resized);
    @FastNative
    public native void nativePointerMove(int x, int y);
    @FastNative
    public native void nativeToggleFullscreen();
    @FastNative
    public native void nativeSetCursorVisible(boolean visible);
    @FastNative
    public native void nativeSetScreenOffsetYRelativeToCursor(boolean cond);
    @FastNative
    public native void nativeSetMagnifierZoom(float magnifierZoom);
    @FastNative
    public native void nativeUpdateWindowContent(int id);
    @FastNative
    public native void nativeReparentWindow(int id, int parentId);
    @FastNative
    public native void nativePause();
    @FastNative
    public native void nativeResume();
    @FastNative
    public native void nativeStop();
    @FastNative
    public native void nativeAddDirectContent(int windowId, Drawable drawable, GPUImage gpuImage);
    @FastNative
    public native void nativeUpdateDirectContent(int windowId, int drawableId, short xOff, short yOff);
    @FastNative
    public native void nativeRemoveDirectContent(int windowId, int pixmapId);
    public native void nativeSetPerformanceMode(boolean enabled);
    public native void nativeSetPresentAtRefreshRate(boolean enabled);
    public native void nativeQueueEvent(Runnable action);

    @Keep
    public void onNativeFramePresented(int windowId) {
        if (windowId != fpsWindowId) return;
        if (hudRef != null) hudRef.onFrame();
        if (classicHudRef != null) classicHudRef.update();
    }

    @Override
    public void requestRender() {
    }

    @Override
    public void queueEvent(Runnable action) {
        if (action != null) nativeQueueEvent(action);
    }

    public void setFpsLimit(int fps) {
        this.fpsLimit = fps;
    }

    public int getFpsLimit() {
        return fpsLimit;
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        nativeChangeSurface(width, height);
    }

    @Override
    public void forceCleanup() {
        nativeStop();
    }

    @Override
    public void setFpsWindowId(int windowId) {
        this.fpsWindowId = windowId;
    }

    @Override
    public void setPipMode(boolean pipMode) {
        this.pipMode = pipMode;
    }

    @Override
    public void setFrameRating(Object frameRating) {
        if (frameRating instanceof WinlatorHUD) hudRef = (WinlatorHUD) frameRating;
        else if (frameRating instanceof FrameRating) classicHudRef = (FrameRating) frameRating;
    }
}
