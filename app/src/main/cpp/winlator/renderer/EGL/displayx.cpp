#include "displayx.hpp"

#include <android/api-level.h>
#include <android/log.h>
#include <android/rect.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <climits>
#include <cstring>
#include <dlfcn.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "DisplayX"
#define DX_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define DX_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr int8_t SURFACE_VISIBILITY_HIDE = 0;
static constexpr int8_t SURFACE_VISIBILITY_SHOW = 1;
static constexpr int8_t SURFACE_TRANSPARENCY_OPAQUE = 2;

using SCCreateFromWindowFn = void* (*)(ANativeWindow*, const char*);
using SCCreateFn = void* (*)(void*, const char*);
using SCAcquireFn = void (*)(void*);
using SCReleaseFn = void (*)(void*);
using STCreateFn = void* (*)();
using STDeleteFn = void (*)(void*);
using STApplyFn = void (*)(void*);
using STSetBufferFn = void (*)(void*, void*, AHardwareBuffer*, int);
using STSetGeometryFn = void (*)(void*, void*, const ARect&, const ARect&, int32_t);
using STSetPositionFn = void (*)(void*, void*, int32_t, int32_t);
using STSetVisibilityFn = void (*)(void*, void*, int8_t);
using STSetZOrderFn = void (*)(void*, void*, int32_t);
using STReparentFn = void (*)(void*, void*, void*);
using STSetTransparencyFn = void (*)(void*, void*, int8_t);
using STSetBackPressureFn = void (*)(void*, void*, bool);
using TransactionCallback = void (*)(void*, void*);
using STSetCallbackFn = void (*)(void*, void*, TransactionCallback);
using ChoreographerGetFn = void* (*)();
using ChoreographerPostFn = void (*)(void*, void (*)(int64_t, void*), void*);
using PerformanceGetManagerFn = void* (*)();
using PerformanceCreateSessionFn = void* (*)(void*, const int32_t*, size_t, int64_t);
using PerformanceReportFn = int (*)(void*, int64_t);
using PerformanceUpdateFn = int (*)(void*, int64_t);
using PerformanceCloseFn = void (*)(void*);

static SCCreateFromWindowFn pSCCreateFromWindow = nullptr;
static SCCreateFn pSCCreate = nullptr;
static SCAcquireFn pSCAcquire = nullptr;
static SCReleaseFn pSCRelease = nullptr;
static STCreateFn pSTCreate = nullptr;
static STDeleteFn pSTDelete = nullptr;
static STApplyFn pSTApply = nullptr;
static STSetBufferFn pSTSetBuffer = nullptr;
static STSetGeometryFn pSTSetGeometry = nullptr;
static STSetPositionFn pSTSetPosition = nullptr;
static STSetVisibilityFn pSTSetVisibility = nullptr;
static STSetZOrderFn pSTSetZOrder = nullptr;
static STReparentFn pSTReparent = nullptr;
static STSetTransparencyFn pSTSetTransparency = nullptr;
static STSetBackPressureFn pSTSetBackPressure = nullptr;
static STSetCallbackFn pSTSetOnComplete = nullptr;
static ChoreographerGetFn pChoreographerGet = nullptr;
static ChoreographerPostFn pChoreographerPost = nullptr;
static PerformanceGetManagerFn pPerformanceGetManager = nullptr;
static PerformanceCreateSessionFn pPerformanceCreateSession = nullptr;
static PerformanceReportFn pPerformanceReport = nullptr;
static PerformanceUpdateFn pPerformanceUpdate = nullptr;
static PerformanceCloseFn pPerformanceClose = nullptr;

static bool loadDisplayXApi() {
    static std::once_flag once;
    static bool available = false;
    std::call_once(once, [] {
        if (android_get_device_api_level() < 29) {
            DX_ERROR("SurfaceControl requires Android 10 or newer");
            return;
        }

        void *handle = dlopen("libandroid.so", RTLD_NOW | RTLD_NOLOAD);
        if (!handle) handle = dlopen("libandroid.so", RTLD_NOW);
        if (!handle) {
            DX_ERROR("Could not load libandroid.so: %s", dlerror());
            return;
        }

#define DX_SYMBOL(name, type) reinterpret_cast<type>(dlsym(handle, name))
        pSCCreateFromWindow = DX_SYMBOL("ASurfaceControl_createFromWindow", SCCreateFromWindowFn);
        pSCCreate = DX_SYMBOL("ASurfaceControl_create", SCCreateFn);
        pSCAcquire = DX_SYMBOL("ASurfaceControl_acquire", SCAcquireFn);
        pSCRelease = DX_SYMBOL("ASurfaceControl_release", SCReleaseFn);
        pSTCreate = DX_SYMBOL("ASurfaceTransaction_create", STCreateFn);
        pSTDelete = DX_SYMBOL("ASurfaceTransaction_delete", STDeleteFn);
        pSTApply = DX_SYMBOL("ASurfaceTransaction_apply", STApplyFn);
        pSTSetBuffer = DX_SYMBOL("ASurfaceTransaction_setBuffer", STSetBufferFn);
        pSTSetGeometry = DX_SYMBOL("ASurfaceTransaction_setGeometry", STSetGeometryFn);
        pSTSetPosition = DX_SYMBOL("ASurfaceTransaction_setPosition", STSetPositionFn);
        pSTSetVisibility = DX_SYMBOL("ASurfaceTransaction_setVisibility", STSetVisibilityFn);
        pSTSetZOrder = DX_SYMBOL("ASurfaceTransaction_setZOrder", STSetZOrderFn);
        pSTReparent = DX_SYMBOL("ASurfaceTransaction_reparent", STReparentFn);
        pSTSetTransparency = DX_SYMBOL("ASurfaceTransaction_setBufferTransparency", STSetTransparencyFn);
        pSTSetBackPressure = DX_SYMBOL("ASurfaceTransaction_setEnableBackPressure", STSetBackPressureFn);
        pSTSetOnComplete = DX_SYMBOL("ASurfaceTransaction_setOnComplete", STSetCallbackFn);
        pChoreographerGet = DX_SYMBOL("AChoreographer_getInstance", ChoreographerGetFn);
        pChoreographerPost = DX_SYMBOL("AChoreographer_postFrameCallback64", ChoreographerPostFn);
        pPerformanceGetManager = DX_SYMBOL("APerformanceHint_getManager", PerformanceGetManagerFn);
        pPerformanceCreateSession = DX_SYMBOL("APerformanceHint_createSession", PerformanceCreateSessionFn);
        pPerformanceReport = DX_SYMBOL("APerformanceHint_reportActualWorkDuration", PerformanceReportFn);
        pPerformanceUpdate = DX_SYMBOL("APerformanceHint_updateTargetWorkDuration", PerformanceUpdateFn);
        pPerformanceClose = DX_SYMBOL("APerformanceHint_closeSession", PerformanceCloseFn);
#undef DX_SYMBOL

        available = pSCCreateFromWindow && pSCCreate && pSCRelease &&
                    pSTCreate && pSTDelete && pSTApply && pSTSetBuffer &&
                    pSTSetGeometry && pSTSetVisibility && pSTSetZOrder &&
                    pSTReparent && pSTSetOnComplete &&
                    pChoreographerGet && pChoreographerPost;
        if (!available) DX_ERROR("Required SurfaceControl symbols are missing");
    });
    return available;
}

static bool readExact(int fd, void *data, size_t size) {
    auto *bytes = static_cast<uint8_t *>(data);
    while (size > 0) {
        ssize_t count = recv(fd, bytes, size, 0);
        if (count == 0) return false;
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        bytes += count;
        size -= static_cast<size_t>(count);
    }
    return true;
}

static int readFd(int fd) {
    char byte = 0;
    iovec iov{&byte, 1};
    std::array<char, CMSG_SPACE(sizeof(int))> control{};
    msghdr message{};
    message.msg_iov = &iov;
    message.msg_iovlen = 1;
    message.msg_control = control.data();
    message.msg_controllen = control.size();

    ssize_t result;
    do {
        result = recvmsg(fd, &message, MSG_WAITALL);
    } while (result < 0 && errno == EINTR);
    if (result != 1) return -1;

    cmsghdr *header = CMSG_FIRSTHDR(&message);
    if (!header || header->cmsg_level != SOL_SOCKET ||
        header->cmsg_type != SCM_RIGHTS ||
        header->cmsg_len < CMSG_LEN(sizeof(int))) {
        return -1;
    }
    int received = -1;
    memcpy(&received, CMSG_DATA(header), sizeof(received));
    return received;
}

static bool sendExactNoSignal(int fd, const void *data, size_t size) {
    auto *bytes = static_cast<const uint8_t *>(data);
    while (size > 0) {
        ssize_t count = send(fd, bytes, size, MSG_NOSIGNAL);
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        bytes += count;
        size -= static_cast<size_t>(count);
    }
    return true;
}

static uint64_t swapchainKey(int clientFd, uint8_t id) {
    return (static_cast<uint64_t>(static_cast<uint32_t>(clientFd)) << 8) | id;
}

void DisplayX::onFrameCallback64(int64_t, void *data) {
    auto *self = static_cast<DisplayX *>(data);
    if (!self || self->stopped) return;

    if (self->cursorUpdate && self->hasSurface && !self->paused)
        self->eventLock.notify();

    {
        auto lock = self->presentLock.lock();
        if (!self->presentRequests.empty() && self->presentAtRefreshRate) {
            self->requestUpdate = true;
            self->presentLock.notify();
        }
    }

    if (!self->stopped && self->choreographer)
        pChoreographerPost(self->choreographer, DisplayX::onFrameCallback64, self);
}

void DisplayX::networkThreadLoop() {
    static constexpr int ADD_CLIENT_SWAPCHAIN = 1;
    static constexpr int PRESENT_IMAGE = 2;
    static constexpr int DESTROY_CLIENT_SWAPCHAIN = 3;
    static constexpr uint32_t MAX_SWAPCHAIN_IMAGES = 16;

    std::unordered_map<int, std::shared_ptr<ClientConnection>> clients;
    std::unordered_map<uint64_t, std::unique_ptr<DisplayXSwapchain>> swapchains;

    auto destroySwapchain = [](std::unique_ptr<DisplayXSwapchain> swapchain) {
        if (!swapchain) return;
        for (auto &image : swapchain->images) {
            if (image && image->ahb) {
                AHardwareBuffer_release(image->ahb);
                image->ahb = nullptr;
            }
        }
    };

    int serverFd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (serverFd < 0) {
        DX_ERROR("Could not create presentation socket: %s", strerror(errno));
        return;
    }

    sockaddr_un address{};
    address.sun_family = AF_UNIX;
    const char *socketName = "displayx";
    memcpy(address.sun_path + 1, socketName, strlen(socketName));
    socklen_t addressLength = offsetof(sockaddr_un, sun_path) + 1 + strlen(socketName);
    if (bind(serverFd, reinterpret_cast<sockaddr *>(&address), addressLength) < 0 ||
        listen(serverFd, 4) < 0) {
        DX_ERROR("Could not bind/listen on presentation socket: %s", strerror(errno));
        close(serverFd);
        return;
    }

    int epollFd = epoll_create1(EPOLL_CLOEXEC);
    if (epollFd < 0) {
        close(serverFd);
        return;
    }

    epoll_event serverEvent{};
    serverEvent.data.fd = serverFd;
    serverEvent.events = EPOLLIN;
    epoll_ctl(epollFd, EPOLL_CTL_ADD, serverFd, &serverEvent);

    epoll_event wakeEvent{};
    wakeEvent.data.fd = networkWakeFd;
    wakeEvent.events = EPOLLIN;
    epoll_ctl(epollFd, EPOLL_CTL_ADD, networkWakeFd, &wakeEvent);

    auto closeClient = [&](int fd) {
        epoll_ctl(epollFd, EPOLL_CTL_DEL, fd, nullptr);
        auto clientIt = clients.find(fd);
        if (clientIt != clients.end()) {
            std::lock_guard<std::mutex> guard(clientIt->second->mutex);
            if (clientIt->second->fd >= 0) close(clientIt->second->fd);
            clientIt->second->fd = -1;
            clients.erase(clientIt);
        } else {
            close(fd);
        }

        for (auto it = swapchains.begin(); it != swapchains.end();) {
            if (static_cast<int>(it->first >> 8) == fd) {
                destroySwapchain(std::move(it->second));
                it = swapchains.erase(it);
            } else {
                ++it;
            }
        }
    };

    std::array<epoll_event, 16> events{};
    while (!stopped) {
        int count = epoll_wait(epollFd, events.data(), events.size(), -1);
        if (count < 0) {
            if (errno == EINTR) continue;
            break;
        }

        for (int i = 0; i < count && !stopped; ++i) {
            int fd = events[i].data.fd;
            if (fd == networkWakeFd) {
                uint64_t value;
                (void)read(networkWakeFd, &value, sizeof(value));
                continue;
            }

            if (fd == serverFd) {
                int clientFd = accept4(serverFd, nullptr, nullptr, SOCK_CLOEXEC);
                if (clientFd >= 0) {
                    auto client = std::make_shared<ClientConnection>();
                    client->fd = clientFd;
                    clients[clientFd] = client;
                    epoll_event clientEvent{};
                    clientEvent.data.fd = clientFd;
                    clientEvent.events = EPOLLIN | EPOLLRDHUP;
                    epoll_ctl(epollFd, EPOLL_CTL_ADD, clientFd, &clientEvent);
                    DX_LOG("Vulkan presentation client connected");
                }
                continue;
            }

            if (events[i].events & (EPOLLERR | EPOLLHUP | EPOLLRDHUP)) {
                closeClient(fd);
                continue;
            }

            int requestCode = 0;
            if (!readExact(fd, &requestCode, sizeof(requestCode))) {
                closeClient(fd);
                continue;
            }

            if (requestCode == ADD_CLIENT_SWAPCHAIN) {
                uint8_t id = 0;
                uint32_t imageCount = 0;
                uint32_t windowId = 0;
                if (!readExact(fd, &id, sizeof(id)) ||
                    !readExact(fd, &imageCount, sizeof(imageCount)) ||
                    !readExact(fd, &windowId, sizeof(windowId)) ||
                    imageCount == 0 || imageCount > MAX_SWAPCHAIN_IMAGES ||
                    !windowManager->getWindow(static_cast<int>(windowId))) {
                    closeClient(fd);
                    continue;
                }

                auto swapchain = std::make_unique<DisplayXSwapchain>();
                swapchain->id = id;
                swapchain->windowId = static_cast<int>(windowId);
                swapchain->client = clients[fd];
                swapchain->images.reserve(imageCount);

                bool valid = true;
                for (uint32_t imageIndex = 0; imageIndex < imageCount; ++imageIndex) {
                    auto drawable = std::make_unique<Drawable>();
                    drawable->id = -1;
                    drawable->textureId = -1;
                    drawable->isDirectContent = false;
                    drawable->isDisplayX = true;
                    drawable->drawableObj = nullptr;
                    drawable->data = nullptr;
                    drawable->ahb = nullptr;
                    if (AHardwareBuffer_recvHandleFromUnixSocket(fd, &drawable->ahb) != 0 ||
                        !drawable->ahb) {
                        valid = false;
                        break;
                    }
                    AHardwareBuffer_Desc description{};
                    AHardwareBuffer_describe(drawable->ahb, &description);
                    drawable->width = static_cast<int>(description.width);
                    drawable->height = static_cast<int>(description.height);
                    drawable->stride = static_cast<int>(description.stride);
                    drawable->format = static_cast<int>(description.format);
                    swapchain->images.push_back(std::move(drawable));
                }

                if (!valid) {
                    destroySwapchain(std::move(swapchain));
                    closeClient(fd);
                    continue;
                }

                uint64_t key = swapchainKey(fd, id);
                auto old = swapchains.find(key);
                if (old != swapchains.end()) {
                    destroySwapchain(std::move(old->second));
                    swapchains.erase(old);
                }
                swapchains[key] = std::move(swapchain);
                DX_LOG("Registered swapchain %u with %u images", id, imageCount);
            } else if (requestCode == PRESENT_IMAGE) {
                uint8_t id = 0;
                int32_t imageIndex = -1;
                uint64_t presentId = UINT64_MAX;
                if (!readExact(fd, &id, sizeof(id)) ||
                    !readExact(fd, &imageIndex, sizeof(imageIndex))) {
                    closeClient(fd);
                    continue;
                }
                int fence = readFd(fd);
                if (!readExact(fd, &presentId, sizeof(presentId))) {
                    if (fence >= 0) close(fence);
                    closeClient(fd);
                    continue;
                }

                auto swapchainIt = swapchains.find(swapchainKey(fd, id));
                if (swapchainIt == swapchains.end() || imageIndex < 0 ||
                    static_cast<size_t>(imageIndex) >= swapchainIt->second->images.size()) {
                    if (fence >= 0) close(fence);
                    continue;
                }

                Drawable *drawable = swapchainIt->second->images[imageIndex].get();
                auto request = std::make_unique<PresentRequest>();
                request->buffer = drawable->ahb;
                AHardwareBuffer_acquire(request->buffer);
                request->syncFence = fence;
                request->presentId = presentId;
                request->swapchainId = id;
                request->windowId = swapchainIt->second->windowId;
                request->displayXBuffer = true;
                request->client = swapchainIt->second->client;

                auto lock = presentLock.lock();
                presentRequests.push(std::move(request));
                if (!presentAtRefreshRate) presentLock.notify();
            } else if (requestCode == DESTROY_CLIENT_SWAPCHAIN) {
                uint8_t id = 0;
                if (!readExact(fd, &id, sizeof(id))) {
                    closeClient(fd);
                    continue;
                }
                uint64_t key = swapchainKey(fd, id);
                auto it = swapchains.find(key);
                if (it != swapchains.end()) {
                    destroySwapchain(std::move(it->second));
                    swapchains.erase(it);
                }
            } else {
                DX_ERROR("Unknown presentation request %d", requestCode);
                closeClient(fd);
            }
        }
    }

    for (auto &entry : swapchains) destroySwapchain(std::move(entry.second));
    swapchains.clear();
    std::vector<int> clientFds;
    clientFds.reserve(clients.size());
    for (const auto &entry : clients) clientFds.push_back(entry.first);
    for (int fd : clientFds) closeClient(fd);
    close(epollFd);
    close(serverFd);
    DX_LOG("Presentation socket stopped");
}

void DisplayX::eventThreadLoop() {
    bool restoreState = false;
    eventEnv = cache->getEnv();

    while (true) {
        std::function<void()> function;
        auto lock = eventLock.lock();
        eventLock.wait(lock, [&] {
            return stopped || state != State::NONE || !eventQueue.empty() ||
                   (cursorUpdate && hasSurface && !paused);
        });

        if (stopped) break;

        State currentState = state;
        state = State::NONE;
        if (currentState == State::PAUSE) {
            paused = true;
            eventLock.notify();
        } else if (currentState == State::RESUME) {
            paused = false;
            restoreState = true;
            eventLock.notify();
        } else if (currentState == State::CREATE_SURFACE) {
            createRootWindowControl();
            hasSurface = windowTransaction != nullptr;
            eventLock.notify();
        } else if (currentState == State::CHANGE_SURFACE) {
            if (hasSurface) {
                resizeRootWindow();
                surfaceChanged = true;
            }
            eventLock.notify();
        } else if (currentState == State::DESTROY_SURFACE) {
            hasSurface = false;
            surfaceChanged = false;
            destroyRootWindowControl();
            eventLock.notify();
        }

        if (hasSurface && restoreState) {
            restoreControlState();
            restoreState = false;
        }

        if (!eventQueue.empty() && hasSurface && surfaceChanged && !paused) {
            function = std::move(eventQueue.front());
            eventQueue.pop();
        }
        bool updatePointer = cursorUpdate && hasSurface && !paused;
        lock.unlock();

        if (function) {
            function();
            auto presentGuard = presentLock.lock();
            if (eventsPending > 0) --eventsPending;
            presentLock.notify();
        }

        if (updatePointer) {
            updateCursorPosition();
            cursorUpdate = false;
        }
    }

    cache->detachEnv(eventEnv);
    eventEnv = nullptr;
    DX_LOG("Event thread stopped");
}

int64_t DisplayX::getCurrentTimeNanos() {
    timespec time{};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return static_cast<int64_t>(time.tv_sec) * 1000000000LL + time.tv_nsec;
}

void DisplayX::releasePresentRequest(std::unique_ptr<PresentRequest> request) {
    if (!request) return;
    if (request->syncFence >= 0 && !request->fenceSubmitted)
        close(request->syncFence);
    if (request->buffer) {
        AHardwareBuffer_release(request->buffer);
        request->buffer = nullptr;
    }
}

void DisplayX::onCompleteCallback(void *context, void *) {
    std::unique_ptr<OnCompleteContext> complete(
        static_cast<OnCompleteContext *>(context));
    if (!complete) return;

    JNIEnv *env = nullptr;
    bool attached = false;
    DisplayX *self = complete->owner;
    if (!complete->requests.empty()) {
        if (self->cache && self->cache->vm) {
            jint result = self->cache->vm->GetEnv(
                reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
            if (result == JNI_EDETACHED &&
                self->cache->vm->AttachCurrentThread(&env, nullptr) == JNI_OK)
                attached = true;
        }
    }

    for (auto &request : complete->requests) {
        if (request->client) {
            std::lock_guard<std::mutex> guard(request->client->mutex);
            int fd = request->client->fd;
            if (fd >= 0) {
                int responseCode = 4;
                sendExactNoSignal(fd, &responseCode, sizeof(responseCode));
                sendExactNoSignal(fd, &request->swapchainId, sizeof(request->swapchainId));
                sendExactNoSignal(fd, &request->presentId, sizeof(request->presentId));
            }
        }

        if (env && self && self->xServer && self->xServer->displayXView &&
            self->cache->displayXOnFramePresented) {
            env->CallVoidMethod(self->xServer->displayXView,
                                self->cache->displayXOnFramePresented,
                                request->windowId);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        releasePresentRequest(std::move(request));
    }

    if (attached && self) self->cache->vm->DetachCurrentThread();
}

void DisplayX::presentThreadLoop() {
    void *presentTransaction = pSTCreate();
    if (!presentTransaction) return;

    if (isPerformanceHintAPIAvailable() && performanceMode) {
        performanceHintManager = pPerformanceGetManager();
        if (performanceHintManager) {
            float targetRate = std::max(1.0f, xServer->refreshRate) * 100.0f;
            int64_t targetDuration =
                static_cast<int64_t>(1000000000.0f / targetRate);
            int32_t tid = static_cast<int32_t>(syscall(SYS_gettid));
            performanceHintSession = pPerformanceCreateSession(
                performanceHintManager, &tid, 1, targetDuration);
        }
    }

    while (true) {
        auto lock = presentLock.lock();
        presentLock.wait(lock, [&] {
            bool presentationReady = presentAtRefreshRate
                ? requestUpdate && !presentRequests.empty()
                : !presentRequests.empty();
            return stopped || (eventsPending == 0 && presentationReady &&
                               hasSurface && surfaceChanged && !paused);
        });
        if (stopped) break;

        std::queue<std::unique_ptr<PresentRequest>> requests;
        while (!presentRequests.empty())
            requests.push(presentRequests.pop());
        if (presentAtRefreshRate) requestUpdate = false;
        lock.unlock();
        int64_t workStarted = getCurrentTimeNanos();

        auto complete = std::make_unique<OnCompleteContext>();
        complete->owner = this;
        bool transactionChanged = false;
        while (!requests.empty()) {
            auto request = std::move(requests.front());
            requests.pop();
            Window *window = windowManager->getWindow(request->windowId);
            if (!window || !window->control || !request->buffer) {
                releasePresentRequest(std::move(request));
                continue;
            }

            if (window->enabled) {
                pSTSetBuffer(presentTransaction, window->control,
                             request->buffer, request->syncFence);
                request->fenceSubmitted = true;
                request->syncFence = -1;
            } else {
                if (request->syncFence >= 0) close(request->syncFence);
                request->syncFence = -1;
                pSTSetBuffer(presentTransaction, window->control, nullptr, -1);
            }
            transactionChanged = true;

            if (window->enabled && pSTSetTransparency &&
                (request->displayXBuffer || request->directContent)) {
                pSTSetTransparency(presentTransaction, window->control,
                                   SURFACE_TRANSPARENCY_OPAQUE);
            }

            if (request->displayXBuffer)
                complete->requests.push_back(std::move(request));
            else
                releasePresentRequest(std::move(request));
        }

        if (!transactionChanged) continue;
        if (!complete->requests.empty())
            pSTSetOnComplete(presentTransaction, complete.release(),
                             DisplayX::onCompleteCallback);
        pSTApply(presentTransaction);
        if (performanceMode && performanceHintSession && pPerformanceReport) {
            int64_t workDuration = getCurrentTimeNanos() - workStarted;
            if (workDuration > 0)
                pPerformanceReport(performanceHintSession, workDuration);
        }
    }

    while (!presentRequests.empty())
        releasePresentRequest(presentRequests.pop());
    if (performanceHintSession && pPerformanceClose)
        pPerformanceClose(performanceHintSession);
    performanceHintSession = nullptr;
    performanceHintManager = nullptr;
    pSTDelete(presentTransaction);
    DX_LOG("Present thread stopped");
}

bool DisplayX::isPerformanceHintAPIAvailable() const {
    return pPerformanceGetManager && pPerformanceCreateSession &&
           pPerformanceReport && pPerformanceUpdate && pPerformanceClose;
}

void DisplayX::start() {
    if (started || !loadDisplayXApi()) return;
    stopped = false;
    paused = false;
    hasSurface = false;
    surfaceChanged = false;
    requestUpdate = false;
    eventsPending = 0;

    networkWakeFd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    if (networkWakeFd < 0) {
        DX_ERROR("Could not create network wake event");
        return;
    }

    started = true;
    eventThread = std::thread(&DisplayX::eventThreadLoop, this);
    networkThread = std::thread(&DisplayX::networkThreadLoop, this);
    presentThread = std::thread(&DisplayX::presentThreadLoop, this);

    choreographer = pChoreographerGet();
    if (choreographer)
        pChoreographerPost(choreographer, DisplayX::onFrameCallback64, this);
}

void DisplayX::stop() {
    if (!started.exchange(false)) return;
    stopped = true;
    eventLock.notify();
    presentLock.notify();
    if (networkWakeFd >= 0) {
        uint64_t value = 1;
        (void)write(networkWakeFd, &value, sizeof(value));
    }

    if (eventThread.joinable()) eventThread.join();
    if (networkThread.joinable()) networkThread.join();
    if (presentThread.joinable()) presentThread.join();

    destroyRootCursorControl();
    destroyRootWindowControl();
    if (networkWakeFd >= 0) close(networkWakeFd);
    networkWakeFd = -1;
    choreographer = nullptr;
}

void DisplayX::pause() {
    if (!started) return;
    auto lock = eventLock.lock();
    state = State::PAUSE;
    eventLock.notify();
    eventLock.wait(lock, [&] { return stopped || state == State::NONE; });
}

void DisplayX::resume() {
    if (!started) return;
    auto lock = eventLock.lock();
    state = State::RESUME;
    eventLock.notify();
    eventLock.wait(lock, [&] { return stopped || state == State::NONE; });
}

void DisplayX::createSurface(ANativeWindow *window) {
    if (!started || !window) {
        if (window) ANativeWindow_release(window);
        return;
    }
    auto lock = eventLock.lock();
    nativeWindow = window;
    state = State::CREATE_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&] { return stopped || state == State::NONE; });
}

void DisplayX::changeSurface(int width, int height) {
    if (!started) return;
    auto lock = eventLock.lock();
    surfaceWidth = width;
    surfaceHeight = height;
    state = State::CHANGE_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&] { return stopped || state == State::NONE; });
}

void DisplayX::destroySurface() {
    if (!started) return;
    auto lock = eventLock.lock();
    state = State::DESTROY_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&] { return stopped || state == State::NONE; });
}

void DisplayX::queueEvent(std::function<void()> function) {
    if (!started || !function) return;
    auto lock = eventLock.lock();
    {
        auto presentGuard = presentLock.lock();
        ++eventsPending;
    }
    eventQueue.push(std::move(function));
    eventLock.notify();
}

void DisplayX::requestWindowUpdate(Window *window) {
    if (!started || !window) return;
    Drawable *drawable = window->currentDirectContent
        ? window->currentDirectContent : window->drawable.get();
    if (!drawable || !drawable->ahb) return;

    auto request = std::make_unique<PresentRequest>();
    request->buffer = drawable->ahb;
    AHardwareBuffer_acquire(request->buffer);
    request->windowId = window->id;
    request->directContent = drawable->isDirectContent;

    auto lock = presentLock.lock();
    presentRequests.push(std::move(request));
    if (!presentAtRefreshRate) presentLock.notify();
}

void DisplayX::requestCursorUpdate() {
    if (!cursorVisible || !started) return;
    cursorUpdate = true;
    eventLock.notify();
}

void DisplayX::createWindowControl(Window *window) {
    if (!window || !window->parent || !window->parent->control ||
        !window->inputOutput || !windowTransaction) return;

    window->control = pSCCreate(window->parent->control, "displayx-window");
    if (!window->control) return;
    if (pSCAcquire) pSCAcquire(window->control);
    if (pSTSetBackPressure)
        pSTSetBackPressure(windowTransaction, window->control, false);
    pSTSetZOrder(windowTransaction, window->control, window->zOrder);
    pSTSetVisibility(windowTransaction, window->control, SURFACE_VISIBILITY_HIDE);

    if (pSTSetPosition) {
        pSTSetPosition(windowTransaction, window->control, window->x, window->y);
    } else {
        ARect source{};
        ARect destination{window->x, window->y,
                          window->x + window->width,
                          window->y + window->height};
        pSTSetGeometry(windowTransaction, window->control,
                       source, destination, 0);
    }
    pSTApply(windowTransaction);
}

void DisplayX::destroyWindowControl(Window *window) {
    if (!window || !window->control) return;
    pSCRelease(window->control);
    window->control = nullptr;
}

void DisplayX::mapWindow(Window *window) {
    if (!window || !window->control || !windowTransaction) return;
    pSTSetVisibility(windowTransaction, window->control, SURFACE_VISIBILITY_SHOW);
    pSTApply(windowTransaction);
}

void DisplayX::unmapWindow(Window *window) {
    if (!window || !window->control || !windowTransaction) return;
    pSTSetVisibility(windowTransaction, window->control, SURFACE_VISIBILITY_HIDE);
    pSTApply(windowTransaction);
}

void DisplayX::changeGeometry(Window *window, bool resized) {
    if (!window || !window->control || !windowTransaction) return;
    if (resized) pSTSetBuffer(windowTransaction, window->control, nullptr, -1);

    if (pSTSetPosition) {
        pSTSetPosition(windowTransaction, window->control, window->x, window->y);
    } else {
        ARect source{};
        ARect destination{window->x, window->y,
                          window->x + window->width,
                          window->y + window->height};
        pSTSetGeometry(windowTransaction, window->control,
                       source, destination, 0);
    }
    pSTApply(windowTransaction);
}

void DisplayX::changeZOrder(Window *window, Window *sibling, int stackMode) {
    if (!window || !window->control || !windowTransaction) return;

    if (sibling) {
        window->zOrder =
            stackMode == 1 ? sibling->zOrder + 1 : sibling->zOrder - 1;
    } else if (window->parent) {
        if (stackMode == 1) {
            int maximum = 0;
            for (Window *child : window->parent->children) {
                if (child->zOrder > maximum) maximum = child->zOrder;
            }
            window->zOrder = maximum + 1;
        } else {
            int minimum = 0;
            for (Window *child : window->parent->children) {
                if (child->zOrder < minimum) minimum = child->zOrder;
            }
            window->zOrder = minimum - 1;
        }
    }

    pSTSetZOrder(windowTransaction, window->control, window->zOrder);
    pSTApply(windowTransaction);
}

void DisplayX::reparentWindow(Window *window, Window *parent) {
    if (!window || !parent || !window->control || !parent->control ||
        !windowTransaction) return;
    pSTReparent(windowTransaction, window->control, parent->control);
    pSTApply(windowTransaction);
}

void DisplayX::updateCursor(Cursor *cursor) {
    if (!cursorTransaction || !cursorManager->control) return;
    AHardwareBuffer *buffer = cursor && cursor->visible && cursorVisible
        ? cursor->image->ahb : nullptr;
    pSTSetBuffer(cursorTransaction, cursorManager->control, buffer, -1);
    pSTSetVisibility(cursorTransaction, cursorManager->control,
                     buffer ? SURFACE_VISIBILITY_SHOW : SURFACE_VISIBILITY_HIDE);
    pSTApply(cursorTransaction);
}

void DisplayX::updateCursorPosition() {
    if (!eventEnv || !cursorManager->control || !cursorTransaction) return;
    jobject pointWindowObject =
        eventEnv->CallObjectMethod(xServer->inputDeviceManager, cache->getPointWindow);
    Window *pointWindow = nullptr;
    if (pointWindowObject) {
        jint id = eventEnv->GetIntField(pointWindowObject, cache->windowID);
        pointWindow = windowManager->getWindow(id);
        eventEnv->DeleteLocalRef(pointWindowObject);
    }

    Cursor *cursor = pointWindow ? pointWindow->cursor : nullptr;
    Cursor *rootCursor = cursorManager->getRootCursor();
    Cursor *activeCursor = cursor ? cursor : rootCursor;
    if (!activeCursor) return;

    int x = std::clamp(cursorManager->pointer.posX - activeCursor->hotspotX,
                       0, std::max(0, windowManager->getRootWindow()->width - 1));
    int y = std::clamp(cursorManager->pointer.posY - activeCursor->hotspotY,
                       0, std::max(0, windowManager->getRootWindow()->height - 1));

    if (cursorVisible || (cursor && cursor->visible)) {
        if (pSTSetPosition) {
            pSTSetPosition(cursorTransaction, cursorManager->control, x, y);
        } else {
            ARect source{};
            ARect destination{x, y, x + activeCursor->image->width,
                              y + activeCursor->image->height};
            pSTSetGeometry(cursorTransaction, cursorManager->control,
                           source, destination, 0);
        }
        pSTSetVisibility(cursorTransaction, cursorManager->control,
                         SURFACE_VISIBILITY_SHOW);
        pSTApply(cursorTransaction);
    } else {
        pSTSetVisibility(cursorTransaction, cursorManager->control,
                         SURFACE_VISIBILITY_HIDE);
        pSTApply(cursorTransaction);
    }
}

void DisplayX::createRootCursorControl() {
    Window *root = windowManager->getRootWindow();
    if (!root || !root->control || cursorManager->control) return;
    cursorManager->control = pSCCreate(root->control, "displayx-cursor");
    if (!cursorManager->control) return;
    if (pSCAcquire) pSCAcquire(cursorManager->control);
    cursorTransaction = pSTCreate();
    if (cursorTransaction && pSTSetBackPressure)
        pSTSetBackPressure(cursorTransaction, cursorManager->control, false);
}

void DisplayX::showCursor() {
    if (!cursorManager->control) createRootCursorControl();
    Cursor *rootCursor = cursorManager->getRootCursor();
    if (!rootCursor || !cursorManager->control || !cursorTransaction) return;
    pSTSetBuffer(cursorTransaction, cursorManager->control,
                 rootCursor->image->ahb, -1);
    pSTSetVisibility(cursorTransaction, cursorManager->control,
                     cursorVisible ? SURFACE_VISIBILITY_SHOW
                                   : SURFACE_VISIBILITY_HIDE);
    pSTSetZOrder(cursorTransaction, cursorManager->control, INT32_MAX);
    pSTApply(cursorTransaction);
}

void DisplayX::createRootWindowControl() {
    Window *root = windowManager->getRootWindow();
    if (!root || !nativeWindow) return;
    root->control = pSCCreateFromWindow(nativeWindow, "displayx-root");
    if (!root->control) return;
    if (pSCAcquire) pSCAcquire(root->control);
    windowTransaction = pSTCreate();
    root->zOrder = -1;
    if (windowTransaction && pSTSetBackPressure)
        pSTSetBackPressure(windowTransaction, root->control, false);
}

void DisplayX::destroyRootWindowControl() {
    if (windowTransaction) {
        pSTDelete(windowTransaction);
        windowTransaction = nullptr;
    }
    Window *root = windowManager ? windowManager->getRootWindow() : nullptr;
    if (root && root->control) {
        pSCRelease(root->control);
        root->control = nullptr;
    }
    if (nativeWindow) {
        ANativeWindow_release(nativeWindow);
        nativeWindow = nullptr;
    }
}

void DisplayX::destroyRootCursorControl() {
    if (cursorTransaction) {
        pSTDelete(cursorTransaction);
        cursorTransaction = nullptr;
    }
    if (cursorManager && cursorManager->control) {
        pSCRelease(cursorManager->control);
        cursorManager->control = nullptr;
    }
}

void DisplayX::resizeRootWindow() {
    Window *root = windowManager->getRootWindow();
    if (!root || !root->control || !windowTransaction ||
        surfaceWidth <= 0 || surfaceHeight <= 0) return;

    viewTransformation.update(surfaceWidth, surfaceHeight,
                              root->width, root->height);
    ARect source{};
    ARect destination{};
    if (fullscreen) {
        source = {viewTransformation.viewOffsetX,
                  viewTransformation.viewOffsetY,
                  viewTransformation.viewOffsetX + viewTransformation.viewWidth,
                  viewTransformation.viewOffsetY + viewTransformation.viewHeight};
        destination = {0, 0, surfaceWidth, surfaceHeight};
    } else {
        source = {0, 0, surfaceWidth, surfaceHeight};
        destination = {viewTransformation.viewOffsetX,
                       viewTransformation.viewOffsetY,
                       viewTransformation.viewOffsetX + viewTransformation.viewWidth,
                       viewTransformation.viewOffsetY + viewTransformation.viewHeight};
    }

    pSTSetGeometry(windowTransaction, root->control, source, destination, 0);
    pSTSetBuffer(windowTransaction, root->control, root->drawable->ahb, -1);
    pSTSetZOrder(windowTransaction, root->control, root->zOrder);
    pSTApply(windowTransaction);
}

void DisplayX::restoreControlState() {
    Window *root = windowManager->getRootWindow();
    if (!root || !root->control || !windowTransaction) return;
    for (auto &entry : windowManager->getWindowTree()) {
        Window *window = entry.second.get();
        if (window == root || !window->control || !window->parent ||
            !window->parent->control) continue;
        pSTReparent(windowTransaction, window->control, window->parent->control);
        pSTSetZOrder(windowTransaction, window->control, window->zOrder);
        pSTSetVisibility(windowTransaction, window->control,
                         window->mapped ? SURFACE_VISIBILITY_SHOW
                                        : SURFACE_VISIBILITY_HIDE);
    }
    pSTApply(windowTransaction);

    if (cursorManager->control && cursorTransaction) {
        pSTReparent(cursorTransaction, cursorManager->control, root->control);
        pSTApply(cursorTransaction);
    }
}

void DisplayX::toggleFullscreen() {
    fullscreen = !fullscreen;
    resizeRootWindow();
}

void DisplayX::setPerformanceMode(bool enabled) {
    performanceMode = enabled;
}

void DisplayX::setPresentAtRefreshRate(bool enabled) {
    auto lock = presentLock.lock();
    presentAtRefreshRate = enabled;
    if (!enabled && !presentRequests.empty()) presentLock.notify();
}
