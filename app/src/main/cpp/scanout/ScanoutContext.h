#pragma once

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/rect.h>

#include <atomic>
#include <cstdint>
#include <mutex>

#ifndef SCANOUT_LOG
#define SCANOUT_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, "Winlator_Scanout", __VA_ARGS__)
#endif

class ScanoutContext {
public:
    bool loadApi();
    void initFromWindow(ANativeWindow* win = nullptr);
    void initFromWindows(ANativeWindow* gameWin, ANativeWindow* cursorWin);
    void destroy();

    void setBuffer(AHardwareBuffer* ahb, int x, int y, int w, int h, int fenceFd = -1);
    bool setCursorImage(void* pixels, short w, short h, short stride);
    bool setCursorPos(short x, short y, short hotX, short hotY);
    void applyPendingCursor();

    void setDst(int x, int y, int w, int h);
    void setContainerSize(int w, int h) { containerWidth = w; containerHeight = h; }
    void setSurfaceSize(int w, int h) { surfaceWidth = w; surfaceHeight = h; }
    void setFallbackWindow(ANativeWindow* win) { window = win; }
    void setVerboseLog(bool v) { verboseLog = v; }

    bool isActive() const { return scanoutActive.load(); }
    bool isGameFrameDelivered() const {
        return gameFrameDelivered.load(std::memory_order_acquire);
    }

    std::atomic<bool> scanoutActive{false};
    std::atomic<bool> gameFrameDelivered{false};

private:
    ANativeWindow* window = nullptr;
    bool verboseLog = true;

    int surfaceWidth = 0;
    int surfaceHeight = 0;
    int containerWidth = 0;
    int containerHeight = 0;

    void* scanoutGameSC = nullptr;
    void* scanoutCursorSC = nullptr;
    void* scanoutCursorBuf = nullptr;
    int32_t scanoutCursorBufW = 0;
    int32_t scanoutCursorBufH = 0;

    void* scanoutTx = nullptr;
    void* scanoutGameTx = nullptr;

    ARect scanoutLastSrc{}, scanoutLastDst{};
    bool scanoutGeoDirty = true;
    bool scanoutVisShown = false;
    bool scanoutApiLoaded = false;

    void* fnSCCreateFromWin = nullptr;
    void* fnSCRelease = nullptr;
    void* fnSTCreate = nullptr;
    void* fnSTDelete = nullptr;
    void* fnSTApply = nullptr;
    void* fnSTSetBuffer = nullptr;
    void* fnSTSetZOrder = nullptr;
    void* fnSTSetVisibility = nullptr;
    void* fnSTSetGeometry = nullptr;

    int32_t scanoutDstX = 0;
    int32_t scanoutDstY = 0;
    int32_t scanoutDstW = 0;
    int32_t scanoutDstH = 0;
    bool gameScVisible = false;

    std::mutex scanoutMutex;

    short pendingCursorX = 0;
    short pendingCursorY = 0;
    short pendingCursorHotX = 0;
    short pendingCursorHotY = 0;
    bool cursorPosDirty = false;
    bool cursorImageDirty = false;
};
