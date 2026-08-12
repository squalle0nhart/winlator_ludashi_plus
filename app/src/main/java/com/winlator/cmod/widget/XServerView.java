package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.renderer.ASurfaceRenderer;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.DisplayXRenderer;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XServer;

import dalvik.annotation.optimization.FastNative;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("ViewConstructor")
public class XServerView extends FrameLayout {
    private HostRenderer renderer;
    private SurfaceView vulkanSurfaceView;
    private GLSurfaceView glSurfaceView;
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
    private final XServer xServer;

    public XServerView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    public void initRenderer(boolean vulkan) {
        initRenderer(vulkan ? "vulkan" : "gl");
    }

    public void initRenderer(String rendererType) {
        boolean vulkan = "vulkan".equalsIgnoreCase(rendererType);
        boolean surfaceFlinger = "surfaceflinger".equalsIgnoreCase(rendererType);
        boolean displayX = "displayx".equalsIgnoreCase(rendererType);

        if (displayX) {
            vulkanSurfaceView = new SurfaceView(getContext());
            vulkanSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(vulkanSurfaceView);
            renderer = new DisplayXRenderer(this, xServer, vulkanSurfaceView);
        } else if (surfaceFlinger) {
            vulkanSurfaceView = new SurfaceView(getContext());
            vulkanSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(vulkanSurfaceView);
            final ASurfaceRenderer asrRenderer = new ASurfaceRenderer(this, xServer);
            renderer = asrRenderer;
            vulkanSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    asrRenderer.onSurfaceCreated(holder.getSurface());
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    asrRenderer.onSurfaceChanged(holder.getSurface(), width, height);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    asrRenderer.onSurfaceDestroyed();
                }
            });
        } else if (vulkan) {
            vulkanSurfaceView = new SurfaceView(getContext());
            vulkanSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(vulkanSurfaceView);
            renderer = new VulkanRenderer(this, xServer);
            vulkanSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    ((VulkanRenderer) renderer).onSurfaceCreated(holder.getSurface());
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    ((VulkanRenderer) renderer).onSurfaceChanged(width, height);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    ((VulkanRenderer) renderer).onSurfaceDestroyed();
                }
            });
        } else {
            glSurfaceView = new GLSurfaceView(getContext());
            glSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            glSurfaceView.setEGLContextClientVersion(3);
            glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
            glSurfaceView.setPreserveEGLContextOnPause(true);
            GLRenderer glRenderer = new GLRenderer(this, xServer);
            renderer = glRenderer;
            glSurfaceView.setRenderer(glRenderer);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            glSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {}

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    glRenderer.onSurfaceDestroyed();
                }
            });
            addView(glSurfaceView);
        }
    }

    public HostRenderer getRenderer() {
        return renderer;
    }

    public SurfaceHolder getHolder() {
        return vulkanSurfaceView != null ? vulkanSurfaceView.getHolder() : null;
    }

    public void requestRender() {
        if (glSurfaceView != null) glSurfaceView.requestRender();
        else if (renderer != null) renderer.requestRender();
    }

    public void queueEvent(Runnable r) {
        if (glSurfaceView != null) glSurfaceView.queueEvent(r);
        else eventExecutor.execute(r);
    }

    public void onPause() {
        if (glSurfaceView != null) glSurfaceView.onPause();
        else if (renderer instanceof DisplayXRenderer) ((DisplayXRenderer) renderer).onPause();
    }

    public void onResume() {
        if (glSurfaceView != null) glSurfaceView.onResume();
        else if (renderer instanceof DisplayXRenderer) ((DisplayXRenderer) renderer).onResume();
    }

    public Object getSurfaceControl() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (vulkanSurfaceView != null) {
                return vulkanSurfaceView.getSurfaceControl();
            }
            if (glSurfaceView != null) {
                return glSurfaceView.getSurfaceControl();
            }
        }
        return null;
    }

    static { System.loadLibrary("winlator"); }

    @FastNative public native void nativeCreateSurface(android.view.Surface surface);
    @FastNative public native void nativeDestroySurface();
    @FastNative public native void nativeInit(Context context, XServer xServer);
    @FastNative public native void nativeChangeSurface(int width, int height);
    @FastNative public native void nativeCreateWindow(Window window, int parentId);
    @FastNative public native void nativeDestroyWindow(int id);
    @FastNative public native void nativeCreateCursor(Cursor cursor);
    @FastNative public native void nativeFreeCursor(int id);
    @FastNative public native void nativeBindCursor(int windowId, int cursorId, boolean visible);
    @FastNative public native void nativeMapWindow(int id);
    @FastNative public native void nativeUnmapWindow(int id);
    @FastNative public native void nativeChangeWindowZOrder(int stackMode, int id, int siblingId);
    @FastNative public native void nativeUpdateWindowGeometry(int id, int width, int height, int x, int y, boolean resized);
    @FastNative public native void nativePointerMove(int x, int y);
    @FastNative public native void nativeToggleFullscreen();
    @FastNative public native void nativeSetCursorVisible(boolean visible);
    @FastNative public native void nativeSetScreenOffsetYRelativeToCursor(boolean enabled);
    @FastNative public native void nativeSetMagnifierZoom(float zoom);
    @FastNative public native void nativeSetUnviewableWMClass(String name);
    @FastNative public native void nativeSetWindowClassName(int id, String className);
    @FastNative public native void nativeUpdatePointWindow(int id);
    @FastNative public native void nativeUpdateWindowContent(int id);
    @FastNative public native void nativeReparentWindow(int id, int parentId);
    @FastNative public native void nativePause();
    @FastNative public native void nativeResume();
    @FastNative public native void nativeStop();
    @FastNative public native void nativeAddDirectContent(int windowId, Drawable drawable);
    @FastNative public native void nativeUpdateDirectContent(int windowId, int drawableId);
    @FastNative public native void nativeRemoveDirectContent(int windowId, int pixmapId);
}
