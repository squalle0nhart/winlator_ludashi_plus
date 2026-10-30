package com.winlator.cmod.widget;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.renderer.ViewTransformation;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.CursorManager;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.InputDeviceManager;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;
import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class XServerView extends XServerRendererView implements SurfaceHolder.Callback, WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener, CursorManager.OnCursorModificationListener {
    static final String RENDERER_LOG_STRING = "Renderer";
    private Context context;
    private int surfaceWidth;
    private int surfaceHeight;
    private int fullscreenMode = ViewTransformation.FULLSCREEN_OFF;
    private String[] unviewableWMClasses;
    private boolean screenOffsetYRelativeToCursor = false;
    private boolean magnifierEnabled = true;
    private boolean viewportNeedsUpdate = true;
    private float magnifierZoom = 1.0f;
    private boolean cursorVisible = true;
    private int fpsWindowId = -1;
    private WinlatorHUD hudRef;
    private FrameRating classicHudRef;
    private boolean pipMode;
    private volatile float fpsLimit;
    private volatile boolean destroyed;
    
    public XServerView(Context context, XServer xserver) {
        super(context, xserver);
        this.context = context;
        xServer.setXServerView(this);
        getHolder().addCallback(this);
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
        xServer.cursorManager.addOnCursorModificationListener(this);
        nativeInit(this.context, xServer);
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (destroyed) return;
        nativeCreateSurface(getHolder().getSurface());
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (destroyed) return;
        nativeDestroySurface();
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (destroyed) return;
        nativeChangeSurface(width, height);
    }
    
    public void onDestroy() {
        if (destroyed) return;
        destroyed = true;
        nativeStop();
    }
    
    public void onPause() {
        if (destroyed) return;
        nativePause();
    }
    
    public void onResume() {
        if (destroyed) return;
        nativeResume();
    }
    
    public void toggleFullscreen() {
        setFullscreenMode(isFullscreen()
                ? ViewTransformation.FULLSCREEN_OFF : ViewTransformation.FULLSCREEN_STRETCH);
    }
    
    public void setCursorVisible(boolean cursorVisible) {
        this.cursorVisible = cursorVisible;
        nativeSetCursorVisible(cursorVisible);
    }

    public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
        this.screenOffsetYRelativeToCursor = screenOffsetYRelativeToCursor;
        nativeSetScreenOffsetYRelativeToCursor(screenOffsetYRelativeToCursor);
    }

    public boolean isFullscreen() {
        return fullscreenMode != ViewTransformation.FULLSCREEN_OFF;
    }

    @Override
    public int getFullscreenMode() {
        return fullscreenMode;
    }

    @Override
    public void setFullscreenMode(int mode) {
        if (mode < ViewTransformation.FULLSCREEN_OFF || mode > ViewTransformation.FULLSCREEN_INTEGER)
            mode = ViewTransformation.FULLSCREEN_OFF;
        if (mode == fullscreenMode) return;
        fullscreenMode = mode;
        nativeSetFullscreenMode(mode);
    }

    public float getMagnifierZoom() {
        return magnifierZoom;
    }

    public void setMagnifierZoom(float magnifierZoom) {
        this.magnifierZoom = magnifierZoom;
        nativeSetMagnifierZoom(magnifierZoom);
    }

    public void setUnviewableWMClass(String unviewableWMName) {
        setUnviewableWMClasses(unviewableWMName);
    }

    @Override
    public void setUnviewableWMClasses(String... classes) {
        unviewableWMClasses = classes;
        nativeSetUnviewableWMClass(classes != null && classes.length > 0 ? classes[0] : "");
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
        if (hideUnviewableWindow(window, window.getClassName(), false)) return;
        nativeMapWindow(window.id);
    }

    private boolean hideUnviewableWindow(Window window, String wmClass, boolean unmap) {
        if (unviewableWMClasses == null || wmClass == null) return false;
        for (String cls : unviewableWMClasses) {
            if (cls == null || cls.isEmpty()) continue;
            if (wmClass.toLowerCase(java.util.Locale.ROOT).contains(cls.toLowerCase(java.util.Locale.ROOT))) {
                if (window.attributes.isEnabled()) window.disableAllDescendants();
                if (unmap) nativeUnmapWindow(window.id);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onUnmapWindow(Window window) {
        nativeUnmapWindow(window.id);
    }

    @Override
    public void onChangeWindowZOrder(Window.StackMode stackMode, Window window, Window sibling) {
        nativeChangeWindowZOrder(stackMode == Window.StackMode.ABOVE ? 1 : 0, window.id, (sibling != null) ? sibling.id : -1);
    }

    
    @Override
    public void onUpdateWindowContent(Window window) {
        nativeUpdateWindowContent(window.id);
    }
    
    @Override 
    public void onUpdateWindowContentDirect(Window window, Drawable drawable) {
        reportFrame(window.id);
        nativeUpdateDirectContent(window.id, drawable.id);
        notifyRendererFrame(window);
    }

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable, short xOff, short yOff) {
        reportFrame(window.id);
        nativeUpdateDirectContent(window.id, drawable.id);
        notifyRendererFrame(window);
    }

    private void reportFrame(int windowId) {
        if (windowId != fpsWindowId) return;
        if (hudRef != null) hudRef.onFrame();
        if (classicHudRef != null) classicHudRef.update();
    }

    private void notifyRendererFrame(Window window) {
        if (window != null && context instanceof XServerDisplayActivity)
            ((XServerDisplayActivity) context).onRendererFrame(window);
    }

    @Override
    public void addDirectContent(int windowId, Drawable drawable, GPUImage gpuImage) {
        nativeAddDirectContent(windowId, drawable);
    }

    @Override
    public void requestRender() {
        postInvalidateOnAnimation();
    }

    @Override
    public void queueEvent(Runnable action) {
        if (action != null) post(action);
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        nativeChangeSurface(width, height);
    }

    @Override
    public void forceCleanup() {
        onDestroy();
    }

    @Override
    public void setFpsWindowId(int windowId) {
        fpsWindowId = windowId;
    }

    @Override
    public void setPipMode(boolean pipMode) {
        this.pipMode = pipMode;
    }

    @Override
    public void setFrameRating(Object frameRating) {
        hudRef = frameRating instanceof WinlatorHUD ? (WinlatorHUD) frameRating : null;
        classicHudRef = frameRating instanceof FrameRating ? (FrameRating) frameRating : null;
    }

    public void setShowFPS(boolean showFPS) {
        if (!destroyed) nativeSetShowFPS(showFPS);
    }

    public void setFilterMode(int mode) {
        if (!destroyed) nativeSetFilterMode(mode);
    }

    public void setSharpness(float sharpness) {
        if (!destroyed) nativeSetSharpness(sharpness);
    }

    @Override
    public void setFpsLimit(float fps) {
        fpsLimit = Math.max(0f, Math.min(1000f, fps));
    }

    @Override
    public float getFpsLimit() {
        return fpsLimit;
    }

    @Override
    public void onUpdateWindowGeometry(final Window window, boolean resized) {
        nativeUpdateWindowGeometry(window.id, window.getWidth(), window.getHeight(), window.getX(), window.getY(), resized, window);
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
    public void onModifyWindowProperty(Window window, Property property) {
        if (property.nameAsString().equals("WM_CLASS")) {
            nativeSetWindowClassName(window.id, property.toString());
            hideUnviewableWindow(window, property.toString(), true);
        }
    }
    
    @Override
    public void onReparentWindow(Window window, Window newParent, short x, short y) {
        nativeReparentWindow(window.id, newParent.id, x, y);
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
    
    static {
        System.loadLibrary("winlator");
    }
    
    @FastNative
    public native void nativeCreateSurface(Surface surface);
    @FastNative
    public native void nativeDestroySurface();
    @FastNative
    public native void nativeInit(Context context, XServer xserver);
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
    public native void nativeUpdateWindowGeometry(int id, int width, int height, int x, int y, boolean resized, Window window);
    @CriticalNative
    public native static void nativePointerMove(int x, int y);
    @FastNative
    public native void nativeSetFullscreenMode(int mode);
    @FastNative
    public native void nativeSetCursorVisible(boolean visible);
    @FastNative
    public native void nativeSetShowFPS(boolean showFPS);
    @FastNative
    public native void nativeSetScreenOffsetYRelativeToCursor(boolean cond);
    @FastNative
    public native void nativeSetMagnifierZoom(float magnifierZoom);
    @FastNative
    public native void nativeSetFilterMode(int mode);
    @FastNative
    public native void nativeSetSharpness(float sharpness);
    @FastNative
    public native void nativeSetUnviewableWMClass(String unviewableWMName);
    @FastNative
    public native void nativeSetWindowClassName(int id, String className);
    @FastNative
    public native void nativeUpdatePointWindow(int id);
    @CriticalNative
    public native static void nativeUpdateWindowContent(int id);
    @FastNative
    public native void nativeReparentWindow(int id, int parentId, short x, short y);
    @FastNative
    public native void nativePause();
    @FastNative
    public native void nativeResume();
    @FastNative
    public native void nativeStop();
    @FastNative
    public native void nativeAddDirectContent(int windowId, Drawable drawable);
    @CriticalNative
    public native static void nativeUpdateDirectContent(int windowId, int drawableId);
    @FastNative
    public native void nativeRemoveDirectContent(int windowId, int pixmapId);
    @FastNative
    public native void nativeSetCompositeRedirected(int windowId, boolean redirected);
    @FastNative
    public native void nativeCompositeRedirect(int srcDrawableId, int dstDrawableId, short dstX, short dstY);
}
