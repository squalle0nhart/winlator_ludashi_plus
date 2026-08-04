#include <jni.h>
#include <android/bitmap.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define WHITE 0x00ffffff
#define BLACK 0x00000000
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Drawable", __VA_ARGS__)

enum GCFunction {
    GCF_CLEAR, GCF_AND, GCF_AND_REVERSE, GCF_COPY, GCF_AND_INVERTED, GCF_NO_OP,
    GCF_XOR, GCF_OR, GCF_NOR, GCF_EQUIV, GCF_INVERT, GCF_OR_REVERSE,
    GCF_COPY_INVERTED, GCF_OR_INVERTED, GCF_NAND, GCF_SET
};

static int packColor(int8_t r, int8_t g, int8_t b) {
    return ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
}

static void unpackColor(int color, uint8_t *bgra) {
    bgra[2] = (color >> 16) & 0xff;
    bgra[1] = (color >> 8) & 0xff;
    bgra[0] = color & 0xff;
    bgra[3] = 0xff;
}

static int8_t getBit(const uint8_t *line, int x) {
    return (line[x >> 3] & (1 << (x & 7))) ? 1 : 0;
}

static int getBitmapBytePad(int width) {
    return ((width + 31) >> 5) << 2;
}

static int setPixelOp(int srcColor, int dstColor, enum GCFunction gcFunction) {
    switch (gcFunction) {
        case GCF_CLEAR: return BLACK;
        case GCF_AND: return srcColor & dstColor;
        case GCF_AND_REVERSE: return srcColor & ~dstColor;
        case GCF_COPY: return srcColor;
        case GCF_AND_INVERTED: return ~srcColor & dstColor;
        case GCF_XOR: return srcColor ^ dstColor;
        case GCF_OR: return srcColor | dstColor;
        case GCF_NOR: return ~srcColor & ~dstColor;
        case GCF_EQUIV: return ~srcColor ^ dstColor;
        case GCF_INVERT: return ~dstColor;
        case GCF_OR_REVERSE: return srcColor | ~dstColor;
        case GCF_COPY_INVERTED: return ~srcColor;
        case GCF_OR_INVERTED: return ~srcColor | dstColor;
        case GCF_NAND: return ~srcColor | ~dstColor;
        case GCF_SET: return WHITE;
        case GCF_NO_OP:
        default: return dstColor;
    }
}

static bool lockAHB(AHardwareBuffer *buffer, uint64_t usage, void **address) {
    if (!buffer || !address) return false;
    *address = NULL;
    int result = AHardwareBuffer_lock(buffer, usage, -1, NULL, address);
    if (result != 0 || !*address) {
        LOGE("AHardwareBuffer_lock failed: %d", result);
        return false;
    }
    return true;
}

static void unlockAHB(AHardwareBuffer *buffer) {
    if (buffer) AHardwareBuffer_unlock(buffer, NULL);
}

static void copyRows(uint8_t *dst, short dstX, short dstY, short dstStride,
        const uint8_t *src, short srcX, short srcY, short srcStride,
        short width, short height) {
    size_t rowBytes = (size_t) width * 4;
    for (short y = 0; y < height; y++) {
        memmove(dst + ((size_t) dstX + (size_t) (y + dstY) * dstStride) * 4,
                src + ((size_t) srcX + (size_t) (y + srcY) * srcStride) * 4,
                rowBytes);
    }
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_drawBitmap(JNIEnv *env, jclass clazz,
        jshort width, jshort height, jobject srcData, jshort dstStride, jlong dstPtr) {
    (void) clazz;
    uint8_t *src = (*env)->GetDirectBufferAddress(env, srcData);
    int32_t *dst = NULL;
    AHardwareBuffer *dstBuffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    if (!src || !lockAHB(dstBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &dst)) return;

    int srcStride = getBitmapBytePad(width);
    for (short y = 0; y < height; y++) {
        int32_t *dstRow = dst + (size_t) y * dstStride;
        for (short x = 0; x < width; x++) dstRow[x] = getBit(src, x) ? WHITE : BLACK;
        src += srcStride;
    }
    unlockAHB(dstBuffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_copyAreaFromBuffer(JNIEnv *env, jclass clazz,
        jshort srcX, jshort srcY, jshort dstX, jshort dstY, jshort width, jshort height,
        jshort srcStride, jshort dstStride, jobject srcData, jlong dstPtr) {
    (void) clazz;
    uint8_t *src = (*env)->GetDirectBufferAddress(env, srcData);
    uint8_t *dst = NULL;
    AHardwareBuffer *dstBuffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    if (!src || !lockAHB(dstBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &dst)) return;
    copyRows(dst, dstX, dstY, dstStride, src, srcX, srcY, srcStride, width, height);
    unlockAHB(dstBuffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_copyAreaToBuffer(JNIEnv *env, jclass clazz,
        jshort srcX, jshort srcY, jshort dstX, jshort dstY, jshort width, jshort height,
        jshort srcStride, jshort dstStride, jlong srcPtr, jobject dstData) {
    (void) clazz;
    uint8_t *dst = (*env)->GetDirectBufferAddress(env, dstData);
    uint8_t *src = NULL;
    AHardwareBuffer *srcBuffer = (AHardwareBuffer *)(uintptr_t) srcPtr;
    if (!dst || !lockAHB(srcBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, (void **) &src)) return;
    copyRows(dst, dstX, dstY, dstStride, src, srcX, srcY, srcStride, width, height);
    unlockAHB(srcBuffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_copyAreaAHB(JNIEnv *env, jclass clazz,
        jshort srcX, jshort srcY, jshort dstX, jshort dstY, jshort width, jshort height,
        jshort srcStride, jshort dstStride, jlong srcPtr, jlong dstPtr) {
    (void) env;
    (void) clazz;
    AHardwareBuffer *srcBuffer = (AHardwareBuffer *)(uintptr_t) srcPtr;
    AHardwareBuffer *dstBuffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    uint8_t *src = NULL;
    uint8_t *dst = NULL;

    if (srcBuffer == dstBuffer) {
        if (!lockAHB(srcBuffer,
                AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                (void **) &src)) return;
        dst = src;
        if (dstY > srcY && dstY < srcY + height) {
            size_t rowBytes = (size_t) width * 4;
            for (int y = height - 1; y >= 0; y--) {
                memmove(dst + ((size_t) dstX + (size_t) (y + dstY) * dstStride) * 4,
                        src + ((size_t) srcX + (size_t) (y + srcY) * srcStride) * 4,
                        rowBytes);
            }
        } else {
            copyRows(dst, dstX, dstY, dstStride, src, srcX, srcY, srcStride, width, height);
        }
        unlockAHB(srcBuffer);
        return;
    }

    if (!lockAHB(srcBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, (void **) &src)) return;
    if (!lockAHB(dstBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &dst)) {
        unlockAHB(srcBuffer);
        return;
    }
    copyRows(dst, dstX, dstY, dstStride, src, srcX, srcY, srcStride, width, height);
    unlockAHB(dstBuffer);
    unlockAHB(srcBuffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_copyAreaOp(JNIEnv *env, jclass clazz,
        jshort srcX, jshort srcY, jshort dstX, jshort dstY, jshort width, jshort height,
        jshort srcStride, jshort dstStride, jlong srcPtr, jlong dstPtr, jint gcFunction) {
    (void) env;
    (void) clazz;
    AHardwareBuffer *srcBuffer = (AHardwareBuffer *)(uintptr_t) srcPtr;
    AHardwareBuffer *dstBuffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    uint8_t *src = NULL;
    uint8_t *dst = NULL;
    bool sameBuffer = srcBuffer == dstBuffer;

    if (sameBuffer) {
        if (!lockAHB(srcBuffer,
                AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                (void **) &src)) return;
        dst = src;
    } else {
        if (!lockAHB(srcBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, (void **) &src)) return;
        if (!lockAHB(dstBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &dst)) {
            unlockAHB(srcBuffer);
            return;
        }
    }

    for (short y = 0; y < height; y++) {
        for (short x = 0; x < width; x++) {
            size_t i = ((size_t) x + srcX + (size_t) (y + srcY) * srcStride) * 4;
            size_t j = ((size_t) x + dstX + (size_t) (y + dstY) * dstStride) * 4;
            int srcColor = (src[i] << 16) | (src[i + 1] << 8) | src[i + 2];
            int dstColor = (dst[j] << 16) | (dst[j + 1] << 8) | dst[j + 2];
            dstColor = setPixelOp(srcColor, dstColor, (enum GCFunction) gcFunction);
            dst[j] = (dstColor >> 16) & 0xff;
            dst[j + 1] = (dstColor >> 8) & 0xff;
            dst[j + 2] = dstColor & 0xff;
        }
    }

    if (!sameBuffer) unlockAHB(dstBuffer);
    unlockAHB(srcBuffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_fillRectAHB(JNIEnv *env, jclass clazz,
        jshort x, jshort y, jshort width, jshort height, jint color, jshort stride,
        jlong dstPtr) {
    (void) env;
    (void) clazz;
    AHardwareBuffer *buffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    uint8_t *data = NULL;
    if (!lockAHB(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &data)) return;

    uint8_t bgra[4];
    unpackColor(color, bgra);
    size_t rowSize = (size_t) width * 4;
    uint8_t *row = malloc(rowSize);
    if (!row) {
        unlockAHB(buffer);
        return;
    }
    for (size_t i = 0; i < rowSize; i += 4) memcpy(row + i, bgra, 4);
    for (short rowIndex = 0; rowIndex < height; rowIndex++) {
        memcpy(data + ((size_t) x + (size_t) (rowIndex + y) * stride) * 4, row, rowSize);
    }
    free(row);
    unlockAHB(buffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_drawLineAHB(JNIEnv *env, jclass clazz,
        jshort x0, jshort y0, jshort x1, jshort y1, jint color, jshort lineWidth,
        jshort stride, jlong dstPtr) {
    (void) env;
    (void) clazz;
    AHardwareBuffer *buffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    uint8_t *data = NULL;
    if (!lockAHB(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &data)) return;

    uint8_t bgra[4];
    unpackColor(color, bgra);
    int dx = abs(x1 - x0);
    int dy = -abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;
    int error = dx + dy;

    while (true) {
        for (short py = 0; py < lineWidth; py++) {
            for (short px = 0; px < lineWidth; px++) {
                memcpy(data + ((size_t) (x0 + px) + (size_t) (y0 + py) * stride) * 4,
                        bgra, 4);
            }
        }
        if (x0 == x1 && y0 == y1) break;
        int twiceError = error * 2;
        if (twiceError >= dy) { error += dy; x0 += sx; }
        if (twiceError <= dx) { error += dx; y0 += sy; }
    }
    unlockAHB(buffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_drawAlphaMaskedBitmapAHB(JNIEnv *env,
        jclass clazz, jbyte foreRed, jbyte foreGreen, jbyte foreBlue, jbyte backRed,
        jbyte backGreen, jbyte backBlue, jlong srcPtr, jshort srcStride, jlong maskPtr,
        jshort maskStride, jshort width, jshort height, jshort dstStride, jlong dstPtr) {
    (void) env;
    (void) clazz;
    AHardwareBuffer *srcBuffer = (AHardwareBuffer *)(uintptr_t) srcPtr;
    AHardwareBuffer *maskBuffer = (AHardwareBuffer *)(uintptr_t) maskPtr;
    AHardwareBuffer *dstBuffer = (AHardwareBuffer *)(uintptr_t) dstPtr;
    int32_t *src = NULL;
    int32_t *mask = NULL;
    int32_t *dst = NULL;

    if (!lockAHB(srcBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, (void **) &src)) return;
    if (!lockAHB(maskBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, (void **) &mask)) {
        unlockAHB(srcBuffer);
        return;
    }
    if (!lockAHB(dstBuffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, (void **) &dst)) {
        unlockAHB(maskBuffer);
        unlockAHB(srcBuffer);
        return;
    }

    int foreColor = packColor(foreRed, foreGreen, foreBlue);
    int backColor = packColor(backRed, backGreen, backBlue);
    for (short y = 0; y < height; y++) {
        for (short x = 0; x < width; x++) {
            size_t srcIndex = (size_t) x + (size_t) y * srcStride;
            size_t maskIndex = (size_t) x + (size_t) y * maskStride;
            size_t dstIndex = (size_t) x + (size_t) y * dstStride;
            dst[dstIndex] = mask[maskIndex] == WHITE
                    ? (src[srcIndex] == WHITE ? foreColor : backColor) | 0xff000000
                    : 0;
        }
    }
    unlockAHB(dstBuffer);
    unlockAHB(maskBuffer);
    unlockAHB(srcBuffer);
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Pixmap_toBitmap(JNIEnv *env, jclass clazz,
        jshort colorStride, jlong colorPtr, jshort maskStride, jlong maskPtr,
        jobject bitmap) {
    (void) clazz;
    AHardwareBuffer *colorBuffer = (AHardwareBuffer *)(uintptr_t) colorPtr;
    AHardwareBuffer *maskBuffer = (AHardwareBuffer *)(uintptr_t) maskPtr;
    uint8_t *color = NULL;
    uint8_t *mask = NULL;
    uint8_t *pixels = NULL;
    bool bitmapLocked = false;

    if (!lockAHB(colorBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, (void **) &color)) return;
    if (maskBuffer && !lockAHB(maskBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
            (void **) &mask)) goto cleanup;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) goto cleanup;
    if (AndroidBitmap_lockPixels(env, bitmap, (void **) &pixels) < 0) goto cleanup;
    bitmapLocked = true;

    for (uint32_t y = 0; y < info.height; y++) {
        uint8_t *dstRow = pixels + (size_t) y * info.stride;
        const uint8_t *colorRow = color + (size_t) y * colorStride * 4;
        const uint8_t *maskRow = mask ? mask + (size_t) y * maskStride * 4 : NULL;
        for (uint32_t x = 0; x < info.width; x++) {
            size_t srcIndex = (size_t) x * 4;
            size_t dstIndex = (size_t) x * 4;
            dstRow[dstIndex + 2] = colorRow[srcIndex];
            dstRow[dstIndex + 1] = colorRow[srcIndex + 1];
            dstRow[dstIndex] = colorRow[srcIndex + 2];
            dstRow[dstIndex + 3] = maskRow ? maskRow[srcIndex] : colorRow[srcIndex + 3];
        }
    }

cleanup:
    if (bitmapLocked) AndroidBitmap_unlockPixels(env, bitmap);
    if (maskBuffer && mask) unlockAHB(maskBuffer);
    unlockAHB(colorBuffer);
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_xserver_Drawable_allocate(JNIEnv *env, jobject object,
        jint width, jint height, jint format) {
    if (width <= 0 || height <= 0) return 0;
    AHardwareBuffer_Desc desc;
    memset(&desc, 0, sizeof(desc));
    desc.width = (uint32_t) width;
    desc.height = (uint32_t) height;
    desc.layers = 1;
    desc.format = (uint32_t) format;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN
            | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN
            | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE
            | AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY;

    AHardwareBuffer *buffer = NULL;
    if (AHardwareBuffer_allocate(&desc, &buffer) != 0 || !buffer) {
        LOGE("AHardwareBuffer_allocate failed for %dx%d format=%d", width, height, format);
        return 0;
    }

    AHardwareBuffer_Desc actual;
    AHardwareBuffer_describe(buffer, &actual);
    void *address = NULL;
    if (lockAHB(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, &address)) {
        memset(address, 0, (size_t) actual.stride * actual.height * 4);
        unlockAHB(buffer);
    }

    jclass drawableClass = (*env)->GetObjectClass(env, object);
    jfieldID strideField = drawableClass
            ? (*env)->GetFieldID(env, drawableClass, "stride", "S") : NULL;
    if (!strideField) {
        AHardwareBuffer_release(buffer);
        return 0;
    }
    (*env)->SetShortField(env, object, strideField, (jshort) actual.stride);
    return (jlong)(uintptr_t) buffer;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_release(JNIEnv *env, jobject object, jlong ptr) {
    (void) env;
    (void) object;
    AHardwareBuffer *buffer = (AHardwareBuffer *)(uintptr_t) ptr;
    if (buffer) AHardwareBuffer_release(buffer);
}

JNIEXPORT jobject JNICALL
Java_com_winlator_cmod_xserver_Drawable_lockBuffer(JNIEnv *env, jobject object, jlong ptr) {
    (void) object;
    AHardwareBuffer *buffer = (AHardwareBuffer *)(uintptr_t) ptr;
    void *address = NULL;
    if (!lockAHB(buffer,
            AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
            &address)) return NULL;
    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(buffer, &desc);
    jobject byteBuffer = (*env)->NewDirectByteBuffer(env, address,
            (jlong) desc.stride * desc.height * 4);
    if (!byteBuffer) unlockAHB(buffer);
    return byteBuffer;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_xserver_Drawable_unlockBuffer(JNIEnv *env, jobject object,
        jlong ptr) {
    (void) env;
    (void) object;
    unlockAHB((AHardwareBuffer *)(uintptr_t) ptr);
}
