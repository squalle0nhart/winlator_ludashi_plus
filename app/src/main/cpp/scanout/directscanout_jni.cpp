#include <android/native_window_jni.h>
#include <jni.h>

#include "ScanoutContext.h"

static inline ScanoutContext* ctx(jlong handle) {
    return reinterpret_cast<ScanoutContext*>(handle);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeInit(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new ScanoutContext());
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetWindows(
        JNIEnv* env, jobject, jlong handle, jobject gameSurface, jobject cursorSurface) {
    auto* c = ctx(handle);
    if (!c) return;
    ANativeWindow* gw = ANativeWindow_fromSurface(env, gameSurface);
    ANativeWindow* cw = ANativeWindow_fromSurface(env, cursorSurface);
    if (!gw || !cw) {
        if (gw) ANativeWindow_release(gw);
        if (cw) ANativeWindow_release(cw);
        c->initFromWindow();
        return;
    }
    c->initFromWindows(gw, cw);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetFallbackWindow(
        JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto* c = ctx(handle);
    if (!c) return;
    ANativeWindow* win = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    c->setFallbackWindow(win);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetBuffer(
        JNIEnv*, jobject, jlong handle, jlong ahbPtr,
        jint x, jint y, jint w, jint h, jint fenceFd) {
    auto* c = ctx(handle);
    if (c && ahbPtr)
        c->setBuffer(reinterpret_cast<AHardwareBuffer*>(ahbPtr), x, y, w, h, fenceFd);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetCursorImage(
        JNIEnv* env, jobject, jlong handle, jobject buf, jshort w, jshort h, jshort stride) {
    auto* c = ctx(handle);
    if (!c || !buf) return;
    void* px = env->GetDirectBufferAddress(buf);
    if (!px || env->GetDirectBufferCapacity(buf) < (jlong) w * h * 4) return;
    if (c->setCursorImage(px, w, h, stride)) c->applyPendingCursor();
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetCursorPos(
        JNIEnv*, jobject, jlong handle, jshort x, jshort y, jshort hotX, jshort hotY) {
    auto* c = ctx(handle);
    if (!c) return;
    if (c->setCursorPos(x, y, hotX, hotY)) c->applyPendingCursor();
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeApplyPendingCursor(
        JNIEnv*, jobject, jlong handle) {
    if (auto* c = ctx(handle)) c->applyPendingCursor();
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetDst(
        JNIEnv*, jobject, jlong handle, jint x, jint y, jint w, jint h) {
    if (auto* c = ctx(handle)) c->setDst(x, y, w, h);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetSurfaceSize(
        JNIEnv*, jobject, jlong handle, jint w, jint h) {
    if (auto* c = ctx(handle)) c->setSurfaceSize(w, h);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeSetContainerSize(
        JNIEnv*, jobject, jlong handle, jint w, jint h) {
    if (auto* c = ctx(handle)) c->setContainerSize(w, h);
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeIsActive(JNIEnv*, jobject, jlong handle) {
    auto* c = ctx(handle);
    return c ? (jboolean) c->isActive() : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeIsGameFrameDelivered(
        JNIEnv*, jobject, jlong handle) {
    auto* c = ctx(handle);
    return c ? (jboolean) c->isGameFrameDelivered() : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_renderer_DirectScanout_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    auto* c = ctx(handle);
    if (!c) return;
    c->destroy();
    delete c;
}

}
