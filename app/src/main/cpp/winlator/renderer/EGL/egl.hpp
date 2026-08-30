#pragma once

#include <android/native_window.h>
#include <android/hardware_buffer.h>
#include <android/log.h>

#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <string>
#include <algorithm>
#include <thread>
#include <functional>
#include <queue>
#include <cmath>
#include <condition_variable>
#include <atomic>

#include "shader.hpp"
#include "renderer_jni.hpp"
#include "window.hpp"
#include "view_transformation.hpp"
#include "xform.hpp"

#define LOG_TAG "EGLRenderer"
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

class EGLRenderer {
    private:
        struct RenderableWindow {
            int rootX;
            int rootY;
            Drawable *content;
            Window *window;
        };

        enum class State {
            NONE,
            PAUSE,
            RESUME,
            CREATE_SURFACE,
            DESTROY_SURFACE,
            CHANGE_SURFACE
        };

        struct RenderLock {
           std::condition_variable cv;
           std::mutex mutex;

           std::unique_lock<std::mutex> lock() {
               return std::unique_lock<std::mutex>(mutex);
           }

           template<typename Predicate>
           void wait(std::unique_lock<std::mutex>& lock, Predicate pred) {
               cv.wait(lock, pred);
           }

           void notify() {
               cv.notify_all();
           }
        };

        EGLDisplay display = EGL_NO_DISPLAY;
        EGLConfig config = nullptr;
        EGLSurface surface = EGL_NO_SURFACE;
        EGLContext context = EGL_NO_CONTEXT;
        DrawableShader *drawableShader = nullptr;
        ANativeWindow *window = nullptr;
        std::vector<std::unique_ptr<struct RenderableWindow>> renderableWindows;
        std::thread renderingThread;
        ViewTransformation viewTransformation;
        RenderLock renderLock;
        State state = State::NONE;
        std::atomic<bool> stopped{false};
        std::atomic<bool> requestUpdate{false};
        std::queue<std::function<void()>> eventQueue;
        int surfaceWidth = 0;
        int surfaceHeight = 0;
        bool fullscreen = false;
        bool viewportNeedsUpdate = true;
        float tmpXForm1[6] = {1, 0, 0, 1, 0, 0};
        float tmpXForm2[6] = {1, 0, 0, 1, 0, 0};

        void renderingThreadLoop();
        void renderDrawable(Drawable *drawable, int x, int y, bool isWindow);
        EGLBoolean drawFrame();
        void renderWindows();
        void destroyEGLSurface();
        void destroyEGLContext();
        void renderCursor();
        void renderDrawable(int textureId, int length, float xform[], bool isFromWindow);
        void updateTextureDrawable(int textureId, int width, int height, void *data);
        int allocateTexture(int width, int height);
        int allocateTextureDirect(AHardwareBuffer* hardwareBuffer);
        int reallocateTexture(int id, int width, int height);
        void init();
        void createEGLSurface(ANativeWindow *window);
        void collectRenderableWindows(Window *window, int x, int y);

    public:
        bool screenOffsetYRelativeToCursor = false;
        bool toggleFullscreen = false;
        bool magnifierEnabled = true;
        float magnifierZoom = 1.0f;
        bool cursorVisible = true;
        int filterMode = 0;
        WindowManager *windowManager;
        CursorManager *cursorManager;
        JNICache *cache;
        JNIXServer *xServer;

        EGLRenderer() {}
        EGLRenderer(WindowManager *manager, CursorManager *cm, JNICache *c) : windowManager(manager), cursorManager(cm), cache(c) {}

        void updateScene();
        void start();
        void stop();
        void pause();
        void resume();
        void updateWindowPosition(Window *window);
        void queueEvent(std::function<void()> func);
        void requestRenderer();
        void destroyTexture(int textureId);
        void destroySurface();
        void createSurface(ANativeWindow *window);
        void changeSurface(int width, int height);
};
