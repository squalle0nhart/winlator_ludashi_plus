#include "displayx.hpp"
#include "stb_image.h"
#include <cstring>

extern JNICache cache;
extern JNIXServer xserver;
extern WindowManager windowManager;
extern CursorManager cursorManager;

DisplayX displayX;

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeInit(JNIEnv *env, jobject thiz, jobject context, jobject xServer, jfloat refreshRate, jboolean performanceMode, jboolean presentAtRefreshRate) {
    jobject windowManagerObj = env->GetObjectField(xServer, cache.windowManager);
    jobject inputDeviceManagerObj = env->GetObjectField(xServer, cache.inputDeviceManager);
    jobject rootWindowObj = env->GetObjectField(windowManagerObj, cache.rootWindow);

    auto rootWindow = std::make_unique<struct Window>();

    rootWindow->id = env->GetIntField(rootWindowObj, cache.windowID);
    rootWindow->width = env->CallShortMethod(rootWindowObj, cache.windowGetWidth);
    rootWindow->height = env->CallShortMethod(rootWindowObj, cache.windowGetHeight);
    rootWindow->x = env->CallShortMethod(rootWindowObj, cache.windowGetX);
    rootWindow->y = env->CallShortMethod(rootWindowObj, cache.windowGetY);
    rootWindow->zOrder = -1;

    jstring className = (jstring)env->CallObjectMethod(rootWindowObj, cache.windowGetClassName);
    const char *chars = env->GetStringUTFChars(className, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(className, chars);
    rootWindow->className = str;

    auto drawable = std::make_unique<struct Drawable>();
    jobject drawableObj = env->CallObjectMethod(rootWindowObj, cache.windowGetContent);
    drawable->id = env->GetIntField(drawableObj, cache.drawableID);
    drawable->textureId = -1;
    drawable->width = env->GetShortField(drawableObj, cache.drawableWidth);
    drawable->height = env->GetShortField(drawableObj, cache.drawableHeight);
    drawable->ahb = (AHardwareBuffer *)env->GetLongField(drawableObj, cache.drawableAHB);
    drawable->stride = env->GetShortField(drawableObj, cache.drawableStride);
    drawable->format = env->GetIntField(drawableObj, cache.drawableFormat);
    drawable->data = nullptr;
    drawable->isDirty = false;
    drawable->isDirectContent = false;
    drawable->isDisplayX = false;
    drawable->sizeChanged = false;
    drawable->drawableObj = env->NewGlobalRef(drawableObj);
    rootWindow->drawable = std::move(drawable);

    env->DeleteLocalRef(drawableObj);

    rootWindow->cursor = nullptr;
    rootWindow->parent = nullptr;
    rootWindow->mapped = true;
    rootWindow->inputOutput = true;
    rootWindow->enabled = true;
    rootWindow->control = nullptr;
    rootWindow->currentDirectContent = nullptr;

    jobject attributes = env->GetObjectField(rootWindowObj, cache.windowAttributes);
    rootWindow->attributes = env->NewGlobalRef(attributes);
    rootWindow->windowObj = env->NewGlobalRef(rootWindowObj);

    env->DeleteLocalRef(rootWindowObj);
    env->DeleteLocalRef(attributes);

    windowManager.setRootWindow(rootWindow.get());
    windowManager.addWindow(rootWindow->id, std::move(rootWindow));

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getAssets = env->GetMethodID(contextClass, "getAssets", "()Landroid/content/res/AssetManager;");
    jobject assetManagerObject = env->CallObjectMethod(context, getAssets);
    AAssetManager *assetManager = AAssetManager_fromJava(env, assetManagerObject);

    AAsset *cursorAsset = AAssetManager_open(assetManager, "cursor.png", AASSET_MODE_BUFFER);
    if (!cursorAsset) return;

    off_t len = AAsset_getLength(cursorAsset);
    const unsigned char *data =
        static_cast<const unsigned char *>(AAsset_getBuffer(cursorAsset));

    int w, h, channels;
    unsigned char *cursorData =
        stbi_load_from_memory(data, len, &w, &h, &channels, 4);
    AAsset_close(cursorAsset);
    if (!cursorData) return;

    auto cursorDrawable = std::make_unique<struct Drawable>();
    cursorDrawable->id = -1;
    cursorDrawable->textureId = -1;
    cursorDrawable->isDirectContent = false;
    cursorDrawable->isDisplayX = false;
    cursorDrawable->format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    cursorDrawable->width = w;
    cursorDrawable->height = h;
    cursorDrawable->data = nullptr;
    cursorDrawable->isDirty = false;
    cursorDrawable->sizeChanged = false;
    cursorDrawable->drawableObj = nullptr;
    cursorDrawable->ahb = nullptr;

    AHardwareBuffer_Desc desc{};
    desc.width = w;
    desc.height = h;
    desc.format = cursorDrawable->format;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
                 AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN |
                 AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;

    int ret = AHardwareBuffer_allocate(&desc, &cursorDrawable->ahb);
    if (ret != 0 || !cursorDrawable->ahb) {
        stbi_image_free(cursorData);
        return;
    }

    AHardwareBuffer_Desc outDesc{};
    AHardwareBuffer_describe(cursorDrawable->ahb, &outDesc);
    cursorDrawable->stride = outDesc.stride;

    uint8_t *dst = nullptr;
    ret = AHardwareBuffer_lock(cursorDrawable->ahb,
        AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr,
        reinterpret_cast<void **>(&dst));
    if (ret != 0 || !dst) {
        AHardwareBuffer_release(cursorDrawable->ahb);
        stbi_image_free(cursorData);
        return;
    }

    for (int y = 0; y < h; ++y) {
        memcpy(dst + y * cursorDrawable->stride * 4,
               cursorData + y * cursorDrawable->width * 4,
               cursorDrawable->width * 4);
    }
    AHardwareBuffer_unlock(cursorDrawable->ahb, nullptr);
    stbi_image_free(cursorData);

    auto rootCursor = std::make_unique<struct Cursor>();
    rootCursor->id = cursorDrawable->id;
    rootCursor->image = std::move(cursorDrawable);
    rootCursor->hotspotX = 0;
    rootCursor->hotspotY = 0;
    rootCursor->visible = true;
    rootCursor->cursorObj = nullptr;

    cursorManager.setRootCursor(std::move(rootCursor));

    xserver.windowManager = env->NewGlobalRef(windowManagerObj);
    xserver.inputDeviceManager = env->NewGlobalRef(inputDeviceManagerObj);
    xserver.xserver = env->NewGlobalRef(xServer);
    xserver.displayXView = env->NewGlobalRef(thiz);
    xserver.refreshRate = refreshRate > 1.0f ? refreshRate : 60.0f;

    env->DeleteLocalRef(windowManagerObj);
    env->DeleteLocalRef(inputDeviceManagerObj);

    displayX.windowManager = &windowManager;
    displayX.cursorManager = &cursorManager;
    displayX.cache = &cache;
    displayX.xServer = &xserver;

    displayX.setPerformanceMode(performanceMode);
    displayX.setPresentAtRefreshRate(presentAtRefreshRate);
    displayX.start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeCreateWindow(JNIEnv *env, jobject thiz, jobject windowObj, jint parentId) {
    auto window = std::make_unique<struct Window>();
    window->id = env->GetIntField(windowObj, cache.windowID);
    window->width = env->CallShortMethod(windowObj, cache.windowGetWidth);
    window->height = env->CallShortMethod(windowObj, cache.windowGetHeight);
    window->x = env->CallShortMethod(windowObj, cache.windowGetX);
    window->y = env->CallShortMethod(windowObj, cache.windowGetY);
    window->zOrder = 100;

    jstring className = (jstring)env->CallObjectMethod(windowObj, cache.windowGetClassName);
    const char *chars = env->GetStringUTFChars(className, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(className, chars);
    window->className = str;

    bool isInputOutput = env->CallBooleanMethod(windowObj, cache.windowIsInputOutput);
    window->inputOutput = isInputOutput;
    window->drawable = nullptr;

    if (isInputOutput) {
        auto drawable = std::make_unique<struct Drawable>();
        jobject drawableObj = env->CallObjectMethod(windowObj, cache.windowGetContent);
        drawable->id = env->GetIntField(drawableObj, cache.drawableID);
        drawable->textureId = -1;
        drawable->width = env->GetShortField(drawableObj, cache.drawableWidth);
        drawable->height = env->GetShortField(drawableObj, cache.drawableHeight);
        drawable->ahb = (AHardwareBuffer *)env->GetLongField(drawableObj, cache.drawableAHB);
        drawable->stride = env->GetShortField(drawableObj, cache.drawableStride);
        drawable->format = env->GetIntField(drawableObj, cache.drawableFormat);
        drawable->data = nullptr;
        drawable->isDirty = false;
        drawable->isDirectContent = false;
        drawable->isDisplayX = false;
        drawable->sizeChanged = false;
        drawable->drawableObj = env->NewGlobalRef(drawableObj);
        window->drawable = std::move(drawable);
        env->DeleteLocalRef(drawableObj);
    }

    window->cursor = nullptr;
    window->mapped = false;
    window->parent = nullptr;
    window->enabled = true;
    window->control = nullptr;
    window->currentDirectContent = nullptr;

    jobject attributes = env->GetObjectField(windowObj, cache.windowAttributes);
    window->attributes = env->NewGlobalRef(attributes);
    window->windowObj = env->NewGlobalRef(windowObj);

    env->DeleteLocalRef(attributes);

    if (parentId > -1) {
        auto parent = windowManager.getWindow(parentId);
        if (!parent) return;
        window->parent = parent;
        window->zOrder += static_cast<int>(parent->children.size());
        parent->children.push_back(window.get());
    }

    displayX.queueEvent([ptr = window.get()] { displayX.createWindowControl(ptr); });

    windowManager.addWindow(window->id, std::move(window));
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeMapWindow(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->mapped = true;
    window->enabled = env->CallBooleanMethod(window->attributes,
                                              cache.windowAttributesIsEnabled);
    displayX.queueEvent([window] { displayX.mapWindow(window); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeUnmapWindow(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->mapped = false;
    displayX.queueEvent([window] { displayX.unmapWindow(window); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeDestroyWindow(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    displayX.queueEvent([window] {
        displayX.destroyWindowControl(window);
        windowManager.deleteWindow(nullptr, window);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeCreateCursor(JNIEnv *env, jobject thiz, jobject cursorObj) {
    auto drawable = std::make_unique<struct Drawable>();
    jobject drawableObj = env->GetObjectField(cursorObj, cache.cursorImage);
    drawable->id = env->GetIntField(drawableObj, cache.drawableID);
    drawable->width = env->GetShortField(drawableObj, cache.drawableWidth);
    drawable->height = env->GetShortField(drawableObj, cache.drawableHeight);
    drawable->ahb = (AHardwareBuffer *)env->GetLongField(drawableObj, cache.drawableAHB);
    drawable->stride = env->GetShortField(drawableObj, cache.drawableStride);
    drawable->format = env->GetIntField(drawableObj, cache.drawableFormat);
    drawable->data = nullptr;
    drawable->isDirectContent = false;
    drawable->isDisplayX = false;
    drawable->isDirty = false;
    drawable->textureId = -1;
    drawable->sizeChanged = false;
    drawable->drawableObj = env->NewGlobalRef(drawableObj);

    env->DeleteLocalRef(drawableObj);

    auto cursor = std::make_unique<struct Cursor>();
    cursor->id = env->GetIntField(cursorObj, cache.cursorID);
    cursor->image = std::move(drawable);
    cursor->hotspotX = env->GetIntField(cursorObj, cache.cursorHotspotX);
    cursor->hotspotY = env->GetIntField(cursorObj, cache.cursorHotspotY);
    cursor->visible = env->CallBooleanMethod(cursorObj, cache.cursorIsVisible);
    cursor->cursorObj = env->NewGlobalRef(cursorObj);

    cursorManager.addCursor(cursor->id, std::move(cursor));
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeFreeCursor(JNIEnv *env, jobject thiz, jint id) {
    auto cursor = cursorManager.getCursor(id);
    if (!cursor) return;

    displayX.queueEvent([cursor] {
        JNIEnv *threadEnv = cache.getEnv();
        displayX.updateCursor(nullptr);
        cursorManager.removeCursor(threadEnv, cursor);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeBindCursor(JNIEnv *env, jobject thiz, jint windowId, jint cursorId, jboolean visible) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;
    auto cursor = cursorManager.getCursor(cursorId);
    if (!cursor) return;

    cursor->visible = visible;
    cursor->image->isDirty = true;

    window->cursor = cursor;
    for (auto &child : window->children)
        child->cursor = cursor;

    displayX.queueEvent([cursor] { displayX.updateCursor(cursor); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativePointerMove(JNIEnv *env, jobject thiz, jint posX, jint posY) {
    cursorManager.pointer.posX = posX;
    cursorManager.pointer.posY = posY;
    displayX.requestCursorUpdate();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeChangeWindowZOrder(JNIEnv *env, jobject thiz, jint stackMode, jint id, jint siblingId) {
    auto window = windowManager.getWindow(id);
    auto sibling = windowManager.getWindow(siblingId);
    if (!window) return;

    windowManager.changeZOrder(stackMode, window, sibling);
    displayX.queueEvent([window, sibling, stackMode] {
        displayX.changeZOrder(window, sibling, stackMode);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeUpdateWindowGeometry(JNIEnv *env, jobject thiz, jint id, jint width, jint height, jint x, jint y, jboolean resized) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->width = width;
    window->height = height;
    window->x = x;
    window->y = y;

    if (resized && window->inputOutput) {
        if (window->drawable->drawableObj)
            env->DeleteGlobalRef(window->drawable->drawableObj);
        jobject drawableObj = env->CallObjectMethod(window->windowObj, cache.windowGetContent);
        window->drawable->drawableObj = env->NewGlobalRef(drawableObj);
        window->drawable->width = width;
        window->drawable->height = height;
        window->drawable->ahb = (AHardwareBuffer *)env->GetLongField(drawableObj, cache.drawableAHB);
        window->drawable->stride = env->GetShortField(drawableObj, cache.drawableStride);
        window->drawable->format = env->GetIntField(drawableObj, cache.drawableFormat);
        window->drawable->data = nullptr;
        window->drawable->sizeChanged = true;
        env->DeleteLocalRef(drawableObj);
    }

    displayX.queueEvent([window, resized] { displayX.changeGeometry(window, (bool)resized); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeUpdateWindowContent(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->drawable->isDirty = false;
    displayX.requestWindowUpdate(window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeReparentWindow(JNIEnv *env, jobject thiz, jint id, jint parentId) {
    auto window = windowManager.getWindow(id);
    auto parent = windowManager.getWindow(parentId);

    if (!window || !parent) return;

    windowManager.reparentWindow(window, parent);
    displayX.queueEvent([window, parent] { displayX.reparentWindow(window, parent); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeToggleFullscreen(JNIEnv *env, jobject thiz) {
    displayX.queueEvent([] { displayX.toggleFullscreen(); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeSetCursorVisible(JNIEnv *env, jobject thiz, jboolean visible) {
    displayX.cursorVisible = visible;
    displayX.queueEvent([] { displayX.showCursor(); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeSetScreenOffsetYRelativeToCursor(JNIEnv *env, jobject thiz, jboolean cond) {
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeSetMagnifierZoom(JNIEnv *env, jobject thiz, float magnifierZoom) {
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeCreateSurface(JNIEnv *env, jobject thiz, jobject surface) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    displayX.createSurface(window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeDestroySurface(JNIEnv *env, jobject thiz) {
    displayX.destroySurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeChangeSurface(JNIEnv *env, jobject thiz, jint width, jint height) {
    displayX.changeSurface(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativePause(JNIEnv *env, jobject thiz) {
    displayX.pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeResume(JNIEnv *env, jobject thiz) {
    displayX.resume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeStop(JNIEnv *env, jobject thiz) {
    displayX.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeSetPerformanceMode(JNIEnv *env, jobject thiz, jboolean enabled) {
    displayX.setPerformanceMode(enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeSetPresentAtRefreshRate(JNIEnv *env, jobject thiz, jboolean enabled) {
    displayX.setPresentAtRefreshRate(enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeQueueEvent(JNIEnv *env, jobject thiz, jobject action) {
    if (!action) return;
    jobject globalAction = env->NewGlobalRef(action);
    displayX.queueEvent([globalAction] {
        JNIEnv *threadEnv = cache.getEnv();
        jclass actionClass = threadEnv->GetObjectClass(globalAction);
        jmethodID run = threadEnv->GetMethodID(actionClass, "run", "()V");
        if (run) threadEnv->CallVoidMethod(globalAction, run);
        if (threadEnv->ExceptionCheck()) threadEnv->ExceptionClear();
        threadEnv->DeleteLocalRef(actionClass);
        threadEnv->DeleteGlobalRef(globalAction);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeAddDirectContent(JNIEnv *env, jobject thiz, jint windowId, jobject drawableObj, jobject gpuImageObj) {
    AHardwareBuffer *hardwareBuffer = (AHardwareBuffer *)env->GetLongField(gpuImageObj, cache.gpuImageHardwareBufferPtr);
    if (!hardwareBuffer) return;

    auto window = windowManager.getWindow(windowId);
    if (!window) return;

    auto drawable = std::make_unique<struct Drawable>();
    drawable->id = env->GetIntField(drawableObj, cache.drawableID);
    drawable->textureId = -1;
    drawable->width = env->GetShortField(drawableObj, cache.drawableWidth);
    drawable->height = env->GetShortField(drawableObj, cache.drawableHeight);
    drawable->ahb = (AHardwareBuffer *)env->GetLongField(drawableObj, cache.drawableAHB);
    drawable->stride = env->GetShortField(drawableObj, cache.drawableStride);
    drawable->format = env->GetIntField(drawableObj, cache.drawableFormat);
    drawable->data = nullptr;
    drawable->isDirty = false;
    drawable->format = env->GetIntField(gpuImageObj, cache.gpuImageFormat);
    drawable->sizeChanged = false;
    drawable->ahb = hardwareBuffer;
    drawable->isDirectContent = true;
    drawable->isDisplayX = false;
    drawable->drawableObj = env->NewGlobalRef(drawableObj);

    window->currentDirectContent = nullptr;
    window->directContents[drawable->id] = std::move(drawable);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeUpdateDirectContent(JNIEnv *env, jclass obj, jint windowId, jint drawableId, jshort xOff, jshort yOff) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;

    auto directContent = window->directContents[drawableId].get();
    if (!directContent) return;

    window->currentDirectContent = directContent;
    window->directContentOffsetX = xOff;
    window->directContentOffsetY = yOff;
    displayX.requestWindowUpdate(window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_widget_DisplayXServerView_nativeRemoveDirectContent(JNIEnv *env, jclass obj, jint windowId, jint drawableId) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;

    auto it = window->directContents.find(drawableId);
    if (it == window->directContents.end()) return;

    Drawable *drawable = it->second.get();

    if (drawable->drawableObj)
        env->DeleteGlobalRef(drawable->drawableObj);

    if (window->currentDirectContent == drawable)
        window->currentDirectContent = nullptr;

    window->directContents.erase(it);
}
