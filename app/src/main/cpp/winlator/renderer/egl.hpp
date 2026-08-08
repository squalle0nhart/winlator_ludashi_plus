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

#include "shader.hpp"
#include "renderer_jni.hpp"
#include "window.hpp"
#include "view_transformation.hpp"
#include "xform.hpp"

class EGLRenderer {
    private:
        struct RenderableWindow {
            int rootX;
            int rootY;
            Window *window;
        };
        
        enum class State {
            NONE,
            STOP,
            PAUSE,
            RESUME,
            CREATE_SURFACE,
            DESTROY_SURFACE,
            CHANGE_SURFACE,
            REQUEST_RENDERER,
            RENDER_COMPLETE
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
        
        EGLDisplay display;
        JNIEnv *env;
        EGLConfig config;
        EGLSurface surface = EGL_NO_SURFACE;
        EGLContext context = EGL_NO_CONTEXT;
        DrawableShader *drawableShader;
        ANativeWindow *window;
        std::vector<std::unique_ptr<struct RenderableWindow>> renderableWindows;
        std::thread renderingThread;
        ViewTransformation viewTransformation;
        RenderLock renderLock;
        State state = State::NONE;
        std::queue<std::function<void()>> eventQueue;
        int surfaceWidth;
        int surfaceHeight;
        bool fullscreen = false;
        bool viewportNeedsUpdate = true;
        float tmpXForm1[6] = {1, 0, 0, 1, 0, 0};
        float tmpXForm2[6] = {1, 0, 0, 1, 0, 0};
        
        
        void renderingThreadLoop();
        void renderDrawable(Drawable *drawable, int x, int y, bool isWindow);
        void drawFrame();
        void renderWindows();
        void destroyEGLSurface();
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