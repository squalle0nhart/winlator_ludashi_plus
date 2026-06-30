package com.winlator.cmod.renderer;

import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;

import java.nio.ByteBuffer;

public class DirectScanout {
    private static final String TAG = "DirectScanout";

    static {
        System.loadLibrary("direct_scanout");
    }

    private long nativeHandle = 0;
    private SurfaceControl gameSC;
    private SurfaceControl cursorSC;
    private Surface gameSurface;
    private Surface cursorSurface;
    private boolean swapRB = false;

    private native long nativeInit();
    private native void nativeSetWindows(long handle, Surface game, Surface cursor);
    private native void nativeSetFallbackWindow(long handle, Surface surface);
    private native void nativeSetBuffer(long handle, long ahbPtr, int x, int y, int w, int h, int fenceFd);
    private native void nativeSetCursorImage(long handle, ByteBuffer pixels, short w, short h, short stride);
    private native void nativeSetCursorPos(long handle, short x, short y, short hotX, short hotY);
    private native void nativeApplyPendingCursor(long handle);
    private native void nativeSetDst(long handle, int x, int y, int w, int h);
    private native void nativeSetSurfaceSize(long handle, int w, int h);
    private native void nativeSetContainerSize(long handle, int w, int h);
    private native boolean nativeIsActive(long handle);
    private native boolean nativeIsGameFrameDelivered(long handle);
    private native void nativeDestroy(long handle);

    public synchronized void enable(SurfaceControl parent, int containerW, int containerH,
            float targetFps, boolean swapRB) {
        this.swapRB = swapRB;
        if (nativeHandle == 0) nativeHandle = nativeInit();

        if (Build.VERSION.SDK_INT < 29 || parent == null) {
            return;
        }
        try {
            releaseScanoutSurfaces();

            gameSC = new SurfaceControl.Builder()
                    .setParent(parent).setName("winlator_game").setOpaque(true).build();
            gameSurface = new Surface(gameSC);
            cursorSC = new SurfaceControl.Builder()
                    .setParent(parent).setName("winlator_cursor").setFormat(1).build();
            cursorSurface = new Surface(cursorSC);

            SurfaceControl.Transaction txn = new SurfaceControl.Transaction()
                    .setLayer(gameSC, 1)
                    .setLayer(cursorSC, 2)
                    .setVisibility(gameSC, true)
                    .setVisibility(cursorSC, true);

            if (Build.VERSION.SDK_INT >= 30) {
                txn.setFrameRate(gameSC, targetFps, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }
            txn.apply();

            applyScanoutSwapTransform();

            if (nativeHandle != 0) {
                nativeSetContainerSize(nativeHandle, containerW, containerH);
                nativeSetWindows(nativeHandle, gameSurface, cursorSurface);
            }
        } catch (Exception e) {
            Log.w(TAG, "Child SC build failed: " + e);
        }
    }

    public synchronized void disable() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                SurfaceControl.Transaction txn = new SurfaceControl.Transaction();
                if (gameSC != null) txn.setVisibility(gameSC, false);
                if (cursorSC != null) txn.setVisibility(cursorSC, false);
                txn.apply();
            } catch (Exception ignored) {
            }
        }
        releaseScanoutSurfaces();
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    public synchronized void present(long ahbPtr, int x, int y, int w, int h, int fence) {
        if (nativeHandle != 0) nativeSetBuffer(nativeHandle, ahbPtr, x, y, w, h, fence);
    }

    public synchronized void setCursorImage(ByteBuffer pixels, short w, short h, short stride) {
        if (nativeHandle != 0) nativeSetCursorImage(nativeHandle, pixels, w, h, stride);
    }

    public synchronized void setCursorPos(short x, short y, short hotX, short hotY) {
        if (nativeHandle != 0) nativeSetCursorPos(nativeHandle, x, y, hotX, hotY);
    }

    public synchronized void setDst(int x, int y, int w, int h) {
        if (nativeHandle != 0) nativeSetDst(nativeHandle, x, y, w, h);
    }

    public synchronized void setSurfaceSize(int w, int h) {
        if (nativeHandle != 0) nativeSetSurfaceSize(nativeHandle, w, h);
    }

    public synchronized void setContainerSize(int w, int h) {
        if (nativeHandle != 0) nativeSetContainerSize(nativeHandle, w, h);
    }

    public synchronized boolean isActive() {
        return nativeHandle != 0 && nativeIsActive(nativeHandle);
    }

    public synchronized boolean isGameFrameDelivered() {
        return nativeHandle != 0 && nativeIsGameFrameDelivered(nativeHandle);
    }

    private void releaseScanoutSurfaces() {
        if (gameSurface != null) {
            gameSurface.release();
            gameSurface = null;
        }
        if (cursorSurface != null) {
            cursorSurface.release();
            cursorSurface = null;
        }
        if (gameSC != null) {
            gameSC.release();
            gameSC = null;
        }
        if (cursorSC != null) {
            cursorSC.release();
            cursorSC = null;
        }
    }

    private void applyScanoutSwapTransform() {
        if (gameSC == null || Build.VERSION.SDK_INT < 29) return;
        try {
            SurfaceControl.Transaction txn = new SurfaceControl.Transaction();
            float[] matrix = swapRB
                    ? new float[]{0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f}
                    : new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
            float[] translation = new float[]{0f, 0f, 0f};
            java.lang.reflect.Method setColorTransform = SurfaceControl.Transaction.class.getMethod(
                    "setColorTransform",
                    SurfaceControl.class,
                    float[].class,
                    float[].class
            );
            setColorTransform.invoke(txn, gameSC, matrix, translation);
            txn.apply();
            txn.close();
        } catch (Exception e) {
            Log.w(TAG, "Scanout color transform unavailable: " + e);
        }
    }
}
