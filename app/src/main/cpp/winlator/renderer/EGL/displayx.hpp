#pragma once

#include <android/hardware_buffer.h>
#include <android/native_window.h>

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <queue>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "renderer_jni.hpp"
#include "window.hpp"
#include "effect_composer.hpp"
#include "cursor.hpp"

class DisplayX {
    private:
        enum class State {
            NONE,
            PAUSE,
            RESUME,
            CREATE_SURFACE,
            DESTROY_SURFACE,
            CHANGE_SURFACE
        };

        struct DisplayXLock {
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

        struct ClientConnection {
            std::mutex mutex;
            int fd = -1;
        };

        struct PresentRequest {
            AHardwareBuffer *buffer = nullptr;
            Drawable *drawable = nullptr;
            int syncFence = -1;
            uint64_t presentId = UINT64_MAX;
            uint8_t swapchainId = 0;
            int windowId = -1;
            bool displayXBuffer = false;
            bool directContent = false;
            bool fenceSubmitted = false;
            std::shared_ptr<ClientConnection> client;
        };

        class PresentQueue {
            private:
                std::queue<std::unique_ptr<PresentRequest>> queue;
                std::unordered_set<int> pendingWindowUpdates;

            public:
                bool push(std::unique_ptr<PresentRequest>& request) {
                    if (!request) return false;
                    if (!request->displayXBuffer &&
                        !pendingWindowUpdates.insert(request->windowId).second)
                        return false;
                    queue.push(std::move(request));
                    return true;
                }

                std::unique_ptr<PresentRequest> pop() {
                    if (queue.empty()) return nullptr;
                    auto request = std::move(queue.front());
                    queue.pop();
                    if (!request->displayXBuffer)
                        pendingWindowUpdates.erase(request->windowId);
                    return request;
                }

                bool empty() const {
                    return queue.empty();
                }
        };

        struct DisplayXSwapchain {
            uint8_t id = 0;
            int windowId = -1;
            std::shared_ptr<ClientConnection> client;
            std::vector<std::unique_ptr<Drawable>> images;
        };

        struct OnCompleteContext {
            DisplayX *owner = nullptr;
            std::vector<std::unique_ptr<PresentRequest>> requests;
        };

        JNIEnv *eventEnv = nullptr;
        int surfaceWidth = 0;
        int surfaceHeight = 0;
        void *choreographer = nullptr;
        ANativeWindow *nativeWindow = nullptr;

        void *performanceHintManager = nullptr;
        void *performanceHintSession = nullptr;

        DisplayXLock eventLock;
        DisplayXLock presentLock;

        void *windowTransaction = nullptr;
        void *cursorTransaction = nullptr;
        PresentQueue presentRequests;
        std::queue<std::function<void()>> eventQueue;

        std::thread eventThread;
        std::thread networkThread;
        std::thread presentThread;

        State state = State::NONE;
        std::atomic_bool started{false};
        std::atomic_bool paused{false};
        std::atomic_bool stopped{false};
        std::atomic_bool hasSurface{false};
        std::atomic_bool cursorUpdate{false};
        std::atomic_bool surfaceChanged{false};
        std::atomic_bool performanceMode{true};
        std::atomic_bool presentAtRefreshRate{true};
        std::atomic_bool backPressure{false};

        bool requestUpdate = false;
        int fullscreenMode = 0;
        int eventsPending = 0;
        int networkWakeFd = -1;

        void eventThreadLoop();
        void networkThreadLoop();
        void presentThreadLoop();
        static void onFrameCallback64(int64_t frameTimeNanos, void *data);
        static void onCompleteCallback(void *context, void *stats);
        static void releasePresentRequest(std::unique_ptr<PresentRequest> request);
        int64_t getCurrentTimeNanos();
        bool isPerformanceHintAPIAvailable() const;

        void createRootWindowControl();
        void createRootCursorControl();
        void resizeRootWindow();
        void destroyRootWindowControl();
        void destroyRootCursorControl();
        void restoreControlState();

    public:
        WindowManager *windowManager = nullptr;
        CursorManager *cursorManager = nullptr;
        JNIXServer *xServer = nullptr;
        JNICache *cache = nullptr;
        EffectComposer *effectComposer = nullptr;

        bool cursorVisible = false;

        void start();
        void createSurface(ANativeWindow *window);
        void changeSurface(int width, int height);
        void destroySurface();
        void stop();
        void pause();
        void resume();

        void queueEvent(std::function<void()> func);
        void requestWindowUpdate(Window *window);
        void requestCursorUpdate();
        void updateCursorPosition();

        void createWindowControl(Window *window);
        void destroyWindowControl(Window *window);
        void mapWindow(Window *window);
        void unmapWindow(Window *window);
        void changeGeometry(Window *window, bool resized);
        void changeZOrder(Window *window, Window *sibling, int stackMode);
        void reparentWindow(Window *window, Window *parent);
        void updateCursor(Cursor *cursor);
        void showCursor();
        void setFullscreenMode(int mode);
        void setPerformanceMode(bool enabled);
        void setPresentAtRefreshRate(bool enabled);
        void setBackPressure(bool enabled);
};
