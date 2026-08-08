#include <sys/socket.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/un.h>
#include <string.h>
#include <thread>
#include <mutex>

#include <android/api-level.h>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/surface_control.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include "displayx.hpp"

using PFNASURFACETRANSACTIONSETPOSITION = void (*)(ASurfaceTransaction*, ASurfaceControl*, int32_t, int32_t);
using PFNASURFACETRANSACTIONSETBUFFER = void (*)(ASurfaceTransaction*, ASurfaceControl*, AHardwareBuffer*, int);
using PFNASURFACETRANSACTIONSETGEOMETRY = void (*)(ASurfaceTransaction*, ASurfaceControl*, const ARect&, const ARect&, int32_t);
using PFNASURFACETRANSACTIONSETZORDER = void (*)(ASurfaceTransaction*, ASurfaceControl*, int32_t);
using PFNASURFACETRANSACTIONSETVISIBILITY = void (*)(ASurfaceTransaction*, ASurfaceControl*, enum ASurfaceTransactionVisibility);
using PFNASURFACETRANSACTIONSETBUFFERALPHA = void (*)(ASurfaceTransaction*, ASurfaceControl*, float);
using PFNASURFACETRANSACTIONSTATSGETPRESENTFENCEFD = int (*)(ASurfaceTransactionStats*);
using PFNASURFACETRANSACTIONREPARENT = void (*)(ASurfaceTransaction*, ASurfaceControl*, ASurfaceControl*);
// Keep the app-wide minSdk at 26 while resolving these API 29/31 symbols at
// runtime. Referring to the NDK callback typedefs directly makes clang reject
// the translation unit for an android-26 target even though no symbol is
// linked eagerly.
using SurfaceTransactionCallback = void (*)(void*, ASurfaceTransactionStats*);
using PFNASURFACETRANSACTIONSETONCOMPLETE = void (*)(ASurfaceTransaction*, void*, SurfaceTransactionCallback);
using PFNASURFACETRANSACTIONSETONCOMMIT = void (*)(ASurfaceTransaction*, void*, SurfaceTransactionCallback);
using PFNASURFACETRANSACTIONSETENABLEBACKPRESSURE = void (*)(ASurfaceTransaction*, ASurfaceControl*, bool);
using PFNASURFACETRANSACTIONCREATE = ASurfaceTransaction* (*)();
using PFNASURFACETRANSACTIONDELETE = void (*)(ASurfaceTransaction*);
using PFNASURFACETRANSACTIONAPPLY = void (*)(ASurfaceTransaction*);

using PFNASURFACECONTROLACQUIRE = void (*)(ASurfaceControl*);
using PFNASURFACECONTROLRELEASE = void (*)(ASurfaceControl*);
using PFNASURFACECONTROLCREATE = ASurfaceControl* (*)(ASurfaceControl*, const char*);
using PFNASURFACECONTROLCREATEFROMWINDOW = ASurfaceControl* (*)(ANativeWindow*, const char*);

using PFNACHOREOGRAPHERGETINSTANCE = AChoreographer* (*)();
using PFNACHOREOGRAPHERPOSTFRAMECALLBACK64 = void (*)(AChoreographer*, AChoreographer_frameCallback64, void*);

using PFNAPERFORMANCEHINTGETMANAGER = APerformanceHintManager* (*)();
using PFNAPERFORMANCEHINTCREATESESSION = APerformanceHintSession* (*)(APerformanceHintManager*, const int32_t*, size_t, int64_t);
using PFNAPERFORMANCEHINTREPORTACTUALWORKDURATION = int (*)(APerformanceHintSession*, int64_t);
using PFNAPERFORMANCEHINTUPDATETARGETWORKDURATION = int (*)(APerformanceHintSession*, int64_t);
using PFNAPERFORMANCEHINTCLOSESESSION = void (*)(APerformanceHintSession*);

static PFNASURFACETRANSACTIONSETPOSITION pfnASurfaceTransactionSetPosition = nullptr;
static PFNASURFACETRANSACTIONSETBUFFER pfnASurfaceTransactionSetBuffer = nullptr;
static PFNASURFACETRANSACTIONSETGEOMETRY pfnASurfaceTransactionSetGeometry = nullptr;
static PFNASURFACETRANSACTIONSETZORDER pfnASurfaceTransactionSetZOrder = nullptr;
static PFNASURFACETRANSACTIONSETVISIBILITY pfnASurfaceTransactionSetVisibility = nullptr;
static PFNASURFACETRANSACTIONSETBUFFERALPHA pfnASurfaceTransactionSetBufferAlpha = nullptr;
static PFNASURFACETRANSACTIONREPARENT pfnASurfaceTransactionReparent = nullptr;
static PFNASURFACETRANSACTIONSETONCOMPLETE pfnASurfaceTransactionSetOnComplete = nullptr;
static PFNASURFACETRANSACTIONSETONCOMMIT pfnASurfaceTransactionSetOnCommit = nullptr;
static PFNASURFACETRANSACTIONSETENABLEBACKPRESSURE pfnASurfaceTransactionSetEnableBackPressure = nullptr;
static PFNASURFACETRANSACTIONSTATSGETPRESENTFENCEFD pfnASurfaceTransactionStatsGetPresentFenceFd = nullptr;
static PFNASURFACETRANSACTIONCREATE pfnASurfaceTransactionCreate = nullptr;
static PFNASURFACETRANSACTIONDELETE pfnASurfaceTransactionDelete = nullptr;
static PFNASURFACETRANSACTIONAPPLY pfnASurfaceTransactionApply = nullptr;

static PFNASURFACECONTROLACQUIRE pfnASurfaceControlAcquire = nullptr;
static PFNASURFACECONTROLRELEASE pfnASurfaceControlRelease = nullptr;
static PFNASURFACECONTROLCREATE pfnASurfaceControlCreate = nullptr;
static PFNASURFACECONTROLCREATEFROMWINDOW pfnASurfaceControlCreateFromWindow = nullptr;

static PFNACHOREOGRAPHERGETINSTANCE pfnAChoreographerGetInstance = nullptr;
static PFNACHOREOGRAPHERPOSTFRAMECALLBACK64 pfnAChoreographerPostFrameCallback64 = nullptr;

static PFNAPERFORMANCEHINTGETMANAGER pfnAPerformanceHintGetManager = nullptr;
static PFNAPERFORMANCEHINTCREATESESSION pfnAPerformanceHintCreateSession = nullptr;
static PFNAPERFORMANCEHINTREPORTACTUALWORKDURATION pfnAPerformanceHintReportActualWorkDuration = nullptr;
static PFNAPERFORMANCEHINTUPDATETARGETWORKDURATION pfnAPerformanceHintUpdateTargetWorkDuration = nullptr;
static PFNAPERFORMANCEHINTCLOSESESSION pfnAPerformanceHintCloseSession = nullptr;

void DisplayX::onFrameCallback64(int64_t frameTimeNanos, void* data) {
    auto *self = reinterpret_cast<DisplayX *>(data);
   
    if (!self->env) {
        self->env = self->cache->getEnv();
    }

    if (self->cursorUpdate && self->cursorManager->control && !self->paused) {
        self->updateCursorPosition();
        self->cursorUpdate = false;
    }
    
    pfnAChoreographerPostFrameCallback64(self->choreographer, DisplayX::onFrameCallback64, self);
}

static void sendFD(int& socket, int fd) {
    std::vector<char> control_buffer(CMSG_SPACE(sizeof(int)));

    char dummy = 0;
    struct iovec iov{};
    iov.iov_len = 1;
    iov.iov_base = &dummy;

    struct msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = control_buffer.data();
    msg.msg_controllen = control_buffer.size();
                                                                                                             struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(int));
    
    *reinterpret_cast<int*>(CMSG_DATA(cmsg)) = fd;

    sendmsg(socket, &msg, 0);
}

static int readFD(int& socket) {
    std::vector<char> msg_contents(1);
    struct iovec iov{};
    iov.iov_base = msg_contents.data();
    iov.iov_len = msg_contents.size();
            
    std::vector<char> control_buf(CMSG_SPACE(sizeof(int)));
    struct msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = control_buf.data();
    msg.msg_controllen = control_buf.size();
            
    recvmsg(socket, &msg, MSG_WAITALL);
            
    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    int fd = *reinterpret_cast<int*>(CMSG_DATA(cmsg));
            
    return fd;
}

void DisplayX::networkThreadLoop() {
    static constexpr int ADD_CLIENT_SWAPCHAIN = 1;
    static constexpr int PRESENT_IMAGE = 2;
    static constexpr int DESTROY_CLIENT_SWAPCHAIN = 3;
    
    std::array<struct epoll_event, 2> events;
    int n;
    int res;
    int efd;
    int server_fd;
    std::unordered_map<uint8_t, std::unique_ptr<DisplayXSwapchain>> clientSwapchains;
    
    server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) 
        printf("Failed to create native rendering socket");
                
    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    memcpy(addr.sun_path + 1, "displayx", strlen("displayx"));
    addr.sun_path[0] = '\0';
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen("displayx");
    res = bind(server_fd, (struct sockaddr *)&addr, len);
    if (res < 0)
        printf("Failed to bind native rendering socket");
               
    res = listen(server_fd, 1);
    if (res < 0)
        printf("Failed to listent to native rendering socket");
                
    efd = epoll_create1(0);
    struct epoll_event event{};
    event.data.fd = server_fd;
    event.events = EPOLLIN;
            
    epoll_ctl(efd, EPOLL_CTL_ADD, server_fd, &event);
           
    while ((n = epoll_wait(efd, events.data(), 2, -1))) {
        if (stopped) {
            close(server_fd);
            clientSwapchains.erase(clientSwapchains.begin(), clientSwapchains.end());
            return;
        }
        
        for (int i = 0; i < n; i++) {
            if (events[i].data.fd == server_fd) {
                if (events[i].events & EPOLLIN) {
                    printf("Received new client connection");
                    int client_fd = accept(server_fd, nullptr, nullptr);
                    struct epoll_event event{};
                    event.data.fd = client_fd;
                    event.events = EPOLLIN;
                    epoll_ctl(efd, EPOLL_CTL_ADD, client_fd, &event);
                }
            } 
            else {
                if (events[i].events & (EPOLLERR | EPOLLHUP)) {
                    printf("Client has disconnected");
                    epoll_ctl(efd, EPOLL_CTL_DEL, events[i].data.fd, nullptr);
                    close(events[i].data.fd);
                    clientSwapchains.erase(clientSwapchains.begin(), clientSwapchains.end());
                    continue;
                }
                
                if (events[i].events & EPOLLIN) {
                    int request_code;
                    int size = read(events[i].data.fd, &request_code, 4);
                    if (size <= 0)
                        continue;
                            
                    switch (request_code) {
                        case ADD_CLIENT_SWAPCHAIN:
                        {
                            uint8_t id;
                            uint32_t imageCount;
                            uint32_t windowId;
                                    
                            read(events[i].data.fd, &id, 1);
                            read(events[i].data.fd, &imageCount, 4);
                            read(events[i].data.fd, &windowId, 4);
                            
                            auto window = windowManager->getWindow(windowId);
                            if (!window)
                                continue;
                                    
                            printf("Received new swapchain from client, id %d images %d", id, imageCount);
                            
                            auto swapchain = std::make_unique<DisplayXSwapchain>();
                            swapchain->id = id;
                            swapchain->window = window;
                            swapchain->images.resize(imageCount);
                                    
                            for (uint32_t j = 0; j < imageCount; j++) {
                                auto drawable = std::make_unique<Drawable>();
                                drawable->id = -1;
                                drawable->textureId = -1;
                                drawable->width = window->width;
                                drawable->height = window->height;
                                drawable->data = nullptr;
                                drawable->isDirty = false;
                                drawable->format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
                                drawable->sizeChanged = false;
                                
                                AHardwareBuffer_recvHandleFromUnixSocket(events[i].data.fd, &drawable->ahb);
                                AHardwareBuffer_Desc outDesc{};
                                AHardwareBuffer_describe(drawable->ahb, &outDesc);
                                drawable->stride = outDesc.stride;
                                drawable->isDirectContent = false;
                                drawable->isDisplayX = true;
                                drawable->drawableObj = nullptr;
                                drawable->sync_fence = -1;
                                
                                swapchain->images[j] = std::move(drawable);
                            }
                             
                            clientSwapchains[id] = std::move(swapchain);
                            break;
                        }    
                        case PRESENT_IMAGE:
                        {
                            uint8_t id;
                            int index;
                            int fence;
                            uint64_t present_id;
                                    
                            read(events[i].data.fd, &id, 1);
                            read(events[i].data.fd, &index, 4);
                            
                            fence = readFD(events[i].data.fd);
                            
                            read(events[id].data.fd, &present_id, 8);
                            
                            auto swapchain = clientSwapchains[id].get();
                            if (!swapchain)
                                continue;
                            
                            auto drawable = swapchain->images.at(index).get();
                            if (!drawable)
                                continue;
                                
                            auto lock = presentLock.lock();    
                            
                            auto presentRequest = std::make_unique<PresentRequest>();
                            presentRequest->drawable = drawable;
                            presentRequest->sync_fence = fence;
                            presentRequest->presentId = present_id;
                            presentRequest->clientFd = events[id].data.fd;
                            presentRequest->window = swapchain->window;
                            presentRequest->swapchainId = id;
                            
                            presentRequests.push(std::move(presentRequest));
                            
                            presentLock.notify();
                            break;
                        }    
                        case DESTROY_CLIENT_SWAPCHAIN: {
                            uint8_t id;
                            read(events[i].data.fd, &id, 1);
                            
                            auto swapchain = clientSwapchains[id].get();
                            if (!swapchain)
                                continue;
                            
                            swapchain->window->currentDirectContent = nullptr;
                            clientSwapchains.erase(id);
                            break;
                        }
                        default:
                            break;            
                    }
                }
            }
        }
    }
}

void DisplayX::eventThreadLoop() {
    bool restoreState = false;
    
    while (true) {
        std::function<void()> func = nullptr;
        
        auto lock = eventLock.lock();
        eventLock.wait(lock, [&]{ 
            return stopped || state != State::NONE || !eventQueue.empty();
        });
        
        if (stopped) {
            printf("Received state STOP");
            stopped = true;
            presentLock.notify();
            return;
        }
        
        auto currState = state;
        state = State::NONE;
        
        if (currState == State::PAUSE) {
            printf("Received state PAUSE");
            paused = true;
            eventLock.notify();
        }
            
        if (currState == State::RESUME) {
            printf("Received state RESUME");
            paused = false;
            restoreState = true;
            eventLock.notify();
        }
        
        if (currState == State::CREATE_SURFACE) {
            printf("Received state CREATE_SURFACE");
            createRootWindowControl();
            createRootCursorControl();
            hasSurface = true;
            eventLock.notify();
        }
            
        if (currState == State::CHANGE_SURFACE) {
            printf("Received state CHANGE_SURFACE");
            resizeRootWindow();
            surfaceChanged = true;
            eventLock.notify();
        }
            
        if (hasSurface && restoreState) {
            printf("Restoring state after resume");
            restoreControlState();
            restoreState = false;
        }
            
        if (currState == State::DESTROY_SURFACE) {
            printf("Received state DESTROY_SURFACE");
            hasSurface = false;
            surfaceChanged = false;
            destroyRootCursorControl();
            destroyRootWindowControl();
            eventLock.notify();
        }
        
        if (!eventQueue.empty() && hasSurface && surfaceChanged && !paused) {
            func = eventQueue.front();
            eventQueue.pop();
        }
        
        if (func) {
            func();
        }
    }
}

int64_t DisplayX::getCurrentTimeNanos() {
    struct timespec ts{};
    
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

void DisplayX::onCommitCallback(void *context, ASurfaceTransactionStats *stats) {
    auto *self = reinterpret_cast<DisplayX *>(context);
    if (!self->isPerformanceHintAPIAvailable())
        return;
    
    if (self->previousReportedWorkTime == 0) {
        auto currentTime = self->getCurrentTimeNanos();
        self->previousReportedWorkTime = currentTime;
        return;
    }     
   
    auto currentTime = self->getCurrentTimeNanos();
    auto elapsed = currentTime - self->previousReportedWorkTime;
    pfnAPerformanceHintReportActualWorkDuration(self->performanceHintSession, elapsed);
    self->previousReportedWorkTime = currentTime;
}

void DisplayX::onCompleteCallback(void *context, ASurfaceTransactionStats *stats) {
    std::unique_ptr<PresentRequest> request(static_cast<PresentRequest *>(context));
    if (request->presentId >= 0) {
        int requestCode = 4;
        write(request->clientFd, &requestCode, 4);
        write(request->clientFd, &request->swapchainId, 1);
        write(request->clientFd, &request->presentId, 8);
    }
}

void DisplayX::presentThreadLoop() {
    ASurfaceTransaction *presentTransaction = pfnASurfaceTransactionCreate();
    JNIEnv *env = cache->getEnv();
    auto lastPresentRequestTimeNanos = 0;
    
    if (isPerformanceHintAPIAvailable()) {
        auto lock = presentLock.lock();
        performanceHintManager = pfnAPerformanceHintGetManager();
        float targetFloat = this->perfMode ? xServer->refreshRate * 100.0f : xServer->refreshRate;
        int64_t targetWorkDuration = static_cast<int64_t>(1000000000.0f / targetFloat);
    
        int tid = gettid();
        std::vector<int32_t> tids{tid};
        performanceHintSession = pfnAPerformanceHintCreateSession(performanceHintManager,
            tids.data(), tids.size(), targetWorkDuration);
    }     
    
    while(true) {
        auto lock = presentLock.lock();
        
        presentLock.wait(lock, [&]{ 
            return stopped || (eventQueue.empty() && !presentRequests.empty() && hasSurface && surfaceChanged && !paused);
        });
        
        if (stopped)
            break;
        
        auto presentRequest = std::move(presentRequests.front());
        presentRequests.pop();
        lock.unlock();
        
        auto window = presentRequest->window;
        if (!window || !window->control) continue;
        
        auto drawable = presentRequest->drawable;
        if (!drawable) {
            continue;
        }
        
        if (!window->enabled) {
            pfnASurfaceTransactionSetBuffer(presentTransaction, window->control, nullptr, presentRequest->sync_fence);
        }
        else {
            pfnASurfaceTransactionSetBuffer(presentTransaction, window->control, drawable->ahb, presentRequest->sync_fence);
            pfnASurfaceTransactionSetOnCommit(presentTransaction, this, DisplayX::onCommitCallback);
            if (drawable->isDisplayX) {
                auto *ptr = presentRequest.release();
                pfnASurfaceTransactionSetOnComplete(presentTransaction, ptr, DisplayX::onCompleteCallback);
                env->CallVoidMethod(xServer->xserverDisplayActivity, cache->updateFrameRating, window->windowObj);
            }    
        }
        
        pfnASurfaceTransactionApply(presentTransaction);
    }
    
    if (isPerformanceHintAPIAvailable()) {
        pfnAPerformanceHintCloseSession(performanceHintSession);
    }
}

void DisplayX::start() {
    void* handle = dlopen("libandroid.so", RTLD_NOW);
    pfnASurfaceTransactionSetPosition = reinterpret_cast<PFNASURFACETRANSACTIONSETPOSITION>(dlsym(handle,"ASurfaceTransaction_setPosition"));
    pfnASurfaceTransactionSetBuffer = reinterpret_cast<PFNASURFACETRANSACTIONSETBUFFER>(dlsym(handle,"ASurfaceTransaction_setBuffer"));
    pfnASurfaceTransactionSetGeometry = reinterpret_cast<PFNASURFACETRANSACTIONSETGEOMETRY>(dlsym(handle,"ASurfaceTransaction_setGeometry"));
    pfnASurfaceTransactionSetZOrder = reinterpret_cast<PFNASURFACETRANSACTIONSETZORDER>(dlsym(handle,"ASurfaceTransaction_setZOrder"));
    pfnASurfaceTransactionSetVisibility = reinterpret_cast<PFNASURFACETRANSACTIONSETVISIBILITY>(dlsym(handle,"ASurfaceTransaction_setVisibility"));
    pfnASurfaceTransactionReparent = reinterpret_cast<PFNASURFACETRANSACTIONREPARENT>(dlsym(handle,"ASurfaceTransaction_reparent"));
    pfnASurfaceTransactionSetOnComplete = reinterpret_cast<PFNASURFACETRANSACTIONSETONCOMPLETE>(dlsym(handle,"ASurfaceTransaction_setOnComplete"));
    pfnASurfaceTransactionSetOnCommit = reinterpret_cast<PFNASURFACETRANSACTIONSETONCOMMIT>(dlsym(handle,"ASurfaceTransaction_setOnCommit"));
    pfnASurfaceTransactionSetEnableBackPressure = reinterpret_cast<PFNASURFACETRANSACTIONSETENABLEBACKPRESSURE>(dlsym(handle,"ASurfaceTransaction_setEnableBackPressure"));
    pfnASurfaceTransactionSetBufferAlpha = reinterpret_cast<PFNASURFACETRANSACTIONSETBUFFERALPHA>(dlsym(handle, "ASurfaceTransaction_setBufferAlpha"));
    pfnASurfaceTransactionStatsGetPresentFenceFd = reinterpret_cast<PFNASURFACETRANSACTIONSTATSGETPRESENTFENCEFD>(dlsym(handle, "ASurfaceTransactionStats_getPresentFenceFd"));
    pfnASurfaceTransactionCreate = reinterpret_cast<PFNASURFACETRANSACTIONCREATE>(dlsym(handle,"ASurfaceTransaction_create"));
    pfnASurfaceTransactionDelete = reinterpret_cast<PFNASURFACETRANSACTIONDELETE>(dlsym(handle,"ASurfaceTransaction_delete"));
    pfnASurfaceTransactionApply = reinterpret_cast<PFNASURFACETRANSACTIONAPPLY>(dlsym(handle,"ASurfaceTransaction_apply"));

    pfnASurfaceControlAcquire = reinterpret_cast<PFNASURFACECONTROLACQUIRE>(dlsym(handle,"ASurfaceControl_acquire"));
    pfnASurfaceControlRelease = reinterpret_cast<PFNASURFACECONTROLRELEASE>(dlsym(handle,"ASurfaceControl_release"));
    pfnASurfaceControlCreate = reinterpret_cast<PFNASURFACECONTROLCREATE>(dlsym(handle,"ASurfaceControl_create"));
    pfnASurfaceControlCreateFromWindow = reinterpret_cast<PFNASURFACECONTROLCREATEFROMWINDOW>(dlsym(handle,"ASurfaceControl_createFromWindow"));

    pfnAChoreographerGetInstance = reinterpret_cast<PFNACHOREOGRAPHERGETINSTANCE>(dlsym(handle,"AChoreographer_getInstance"));
    pfnAChoreographerPostFrameCallback64 = reinterpret_cast<PFNACHOREOGRAPHERPOSTFRAMECALLBACK64>(dlsym(handle,"AChoreographer_postFrameCallback64"));
    
    pfnAPerformanceHintGetManager = reinterpret_cast<PFNAPERFORMANCEHINTGETMANAGER>(dlsym(handle, "APerformanceHint_getManager"));
    pfnAPerformanceHintCreateSession = reinterpret_cast<PFNAPERFORMANCEHINTCREATESESSION>(dlsym(handle, "APerformanceHint_createSession"));
    pfnAPerformanceHintReportActualWorkDuration = reinterpret_cast<PFNAPERFORMANCEHINTREPORTACTUALWORKDURATION>(dlsym(handle, "APerformanceHint_reportActualWorkDuration"));
    pfnAPerformanceHintUpdateTargetWorkDuration = reinterpret_cast<PFNAPERFORMANCEHINTUPDATETARGETWORKDURATION>(dlsym(handle, "APerformanceHint_updateTargetWorkDuration"));
    pfnAPerformanceHintCloseSession = reinterpret_cast<PFNAPERFORMANCEHINTCLOSESESSION>(dlsym(handle, "APerformanceHint_closeSession"));
        
    eventThread = std::thread(&DisplayX::eventThreadLoop, this);
    networkThread = std::thread(&DisplayX::networkThreadLoop, this);
    presentThread = std::thread(&DisplayX::presentThreadLoop, this);
   
    this->choreographer = pfnAChoreographerGetInstance();
    pfnAChoreographerPostFrameCallback64(this->choreographer, DisplayX::onFrameCallback64, this);
}

void DisplayX::stop() {
    stopped = true;
    eventLock.notify();
}

void DisplayX::pause() {
    auto lock = eventLock.lock();
    state = State::PAUSE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE; });
}

void DisplayX::resume() {
    auto lock = eventLock.lock();
    state = State::RESUME;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE; });
}

void DisplayX::createSurface(ANativeWindow *window) {
    auto lock = eventLock.lock();
    this->native_window = window;
    state = State::CREATE_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE; });
}

void DisplayX::changeSurface(int width, int height) {
    auto lock = eventLock.lock();
    this->surfaceWidth = width;
    this->surfaceHeight = height;
    state = State::CHANGE_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE; });
}

void DisplayX::destroySurface() {
    auto lock = eventLock.lock();
    this->native_window = nullptr;
    state = State::DESTROY_SURFACE;
    eventLock.notify();
    eventLock.wait(lock, [&]{ return state == State::NONE; });
}

void DisplayX::queueEvent(std::function<void()> func) {
    auto lock = eventLock.lock();
    eventQueue.push(func);
    eventLock.notify();
}

void DisplayX::requestWindowUpdate(Drawable *drawable, Window *window) {
    auto lock = presentLock.lock();
    
    auto presentRequest = std::make_unique<PresentRequest>();
    presentRequest->drawable = drawable;
    presentRequest->sync_fence = -1;
    presentRequest->presentId = -1;
    presentRequest->clientFd = -1;
    presentRequest->window = window;
    
    presentRequests.push(std::move(presentRequest));
    
    presentLock.notify();
}

void DisplayX::requestCursorUpdate() {
    if (!cursorVisible) return;
    
    this->cursorUpdate = true;
}

void DisplayX::createWindowControl(Window *window) {
    if (!window->parent || !window->inputOutput) return;
        
    window->control = pfnASurfaceControlCreate(window->parent->control, "displayx");
    if (pfnASurfaceControlAcquire)    
        pfnASurfaceControlAcquire(window->control);
    
    pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    
    if (pfnASurfaceTransactionSetPosition) {
        pfnASurfaceTransactionSetPosition(windowTransaction, window->control, window->x, window->y);
    }
    else {
        ARect src{};
        ARect dst = {
            .left = window->x,
            .top = window->y,
            .right = window->x + window->width,
            .bottom = window->y + window->height
        };
        pfnASurfaceTransactionSetGeometry(windowTransaction, window->control, src, dst, 0);
    }  
    pfnASurfaceTransactionApply(windowTransaction);
}

void DisplayX::destroyWindowControl(Window *window) {
    if (!window) return;
    if (!window->control) return;
 
    pfnASurfaceControlRelease(window->control);
    window->control = nullptr;
}

void DisplayX::mapWindow(Window *window) {
    if (!window->control) return;
    
    if (!strcmp(window->className.c_str(), windowManager->getUnviewableWMClass().c_str()))
        window->enabled = false;
    
    pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, ASURFACE_TRANSACTION_VISIBILITY_SHOW);
    pfnASurfaceTransactionApply(windowTransaction);
}

void DisplayX::unmapWindow(Window *window) {
    if (!window->control) return;
    
    pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    pfnASurfaceTransactionApply(windowTransaction);
}

void DisplayX::changeGeometry(Window *window, bool resized) {
    if (!window->control) return;
    
    int ret;
    
    if (resized) {
        window->drawable->sizeChanged = false;
        pfnASurfaceTransactionSetBuffer(windowTransaction, window->control, window->drawable->ahb, -1);
    }
    
    if (pfnASurfaceTransactionSetPosition) {
        pfnASurfaceTransactionSetPosition(windowTransaction, window->control, window->x, window->y);
    }
    else {
        ARect src{};
        ARect dst = {
            .left = window->x,
            .top = window->y,
            .right = window->x + window->width,
            .bottom = window->y + window->height
        };
        pfnASurfaceTransactionSetGeometry(windowTransaction, window->control, src, dst, 0);
    }
    
    pfnASurfaceTransactionApply(windowTransaction);
}

void DisplayX::updateCursor(Window *window) {
    int ret;
    
    auto cursor = window->cursor;
    if (!cursor) return;
   
    pfnASurfaceTransactionSetBuffer(cursorTransaction, cursorManager->control, cursor->image->ahb, -1);
    pfnASurfaceTransactionSetVisibility(cursorTransaction, cursorManager->control, (cursor->visible && cursorVisible) ?  ASURFACE_TRANSACTION_VISIBILITY_SHOW : ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    pfnASurfaceTransactionApply(cursorTransaction);
}

void DisplayX::updateCursorPosition() {
    if (!cursorManager->control) return;
    
    jobject pointWindowObj = env->CallObjectMethod(xServer->inputDeviceManager, cache->getPointWindow);
    jint id = env->GetIntField(pointWindowObj, cache->windowID);
    auto pointWindow = windowManager->getWindow(id);
    auto cursor = (pointWindow != nullptr) ? pointWindow->cursor : nullptr;
    auto rootCursor = cursorManager->getRootCursor();
    int x = std::clamp(cursorManager->pointer.posX, 0, windowManager->getRootWindow()->width - 1);
    int y = std::clamp(cursorManager->pointer.posY, 0, windowManager->getRootWindow()->height - 1);
    
    if (cursorVisible || (cursor && cursor->visible)) {
        if (repostCursor) {
            if (cursor != nullptr) {
                pfnASurfaceTransactionSetBuffer(cursorTransaction, cursorManager->control, cursor->image->ahb, -1);
            }
            else {
                pfnASurfaceTransactionSetBuffer(cursorTransaction, cursorManager->control, rootCursor->image->ahb, -1);
            }
            repostCursor = false;
        }
        
        if (pfnASurfaceTransactionSetPosition) {
            pfnASurfaceTransactionSetPosition(cursorTransaction, cursorManager->control, x, y);
        }
        else {
            auto& cursorImage = (cursor != nullptr) ? cursor->image : rootCursor->image;
            ARect src{};
            ARect dst = {
                .left = x,
                .top = y,
                .right = x + cursorImage->width,
                .bottom = y + cursorImage->height
            };
            pfnASurfaceTransactionSetGeometry(cursorTransaction, cursorManager->control, src, dst, 0);
        }
        
        pfnASurfaceTransactionApply(cursorTransaction);
    }
    else {
        pfnASurfaceTransactionSetVisibility(cursorTransaction, cursorManager->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
        pfnASurfaceTransactionApply(cursorTransaction);
    }
}

void DisplayX::createRootCursorControl() {
    int ret;
    
    auto rootCursor = cursorManager->getRootCursor();
    auto rootWindow = windowManager->getRootWindow();
    if (!rootCursor || !rootWindow) return;
    
    cursorManager->control = pfnASurfaceControlCreate(rootWindow->control, "displayx");
    if (pfnASurfaceControlAcquire)
        pfnASurfaceControlAcquire(cursorManager->control);
    
    cursorTransaction = pfnASurfaceTransactionCreate();
}

void DisplayX::drawRootCursor() {
    if (!cursorVisible) return;
    
    auto rootCursor = cursorManager->getRootCursor();
    if (!cursorManager) return;
    
    pfnASurfaceTransactionSetBuffer(cursorTransaction, cursorManager->control, rootCursor->image->ahb, -1);
    pfnASurfaceTransactionSetVisibility(cursorTransaction, cursorManager->control, ASURFACE_TRANSACTION_VISIBILITY_SHOW);
    pfnASurfaceTransactionSetZOrder(cursorTransaction, cursorManager->control, INT32_MAX);
    pfnASurfaceTransactionApply(cursorTransaction);
}

void DisplayX::reparentWindow(Window *window, Window *parent) {
    if (!window->control) return;
    
    pfnASurfaceTransactionReparent(windowTransaction, window->control, parent->control);
    pfnASurfaceTransactionApply(windowTransaction);
}

bool DisplayX::isPerformanceHintAPIAvailable() {
    return pfnAPerformanceHintGetManager &&
           pfnAPerformanceHintCreateSession &&
           pfnAPerformanceHintUpdateTargetWorkDuration &&
           pfnAPerformanceHintReportActualWorkDuration &&
           pfnAPerformanceHintCloseSession;
}

void DisplayX::createRootWindowControl() {
    int ret;
    
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    
    rootWindow->control = pfnASurfaceControlCreateFromWindow(this->native_window, "displayx");
    if (pfnASurfaceControlAcquire)      
        pfnASurfaceControlAcquire(rootWindow->control);
 
    windowTransaction = pfnASurfaceTransactionCreate();
}

void DisplayX::destroyRootWindowControl() {
    this->native_window = nullptr;
    pfnASurfaceTransactionDelete(windowTransaction);
    
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    if (!rootWindow->control) return;

    pfnASurfaceControlRelease(rootWindow->control);
    rootWindow->control = nullptr;
}

void DisplayX::destroyRootCursorControl() {
    pfnASurfaceTransactionDelete(cursorTransaction);
    
    auto rootCursor = cursorManager->getRootCursor();
    if (!rootCursor) return;
    
    if (rootCursor->image->ahb) {
        AHardwareBuffer_release(rootCursor->image->ahb);
        rootCursor->image->ahb = nullptr;
    }
    
    if (!cursorManager->control) return;

    pfnASurfaceControlRelease(cursorManager->control);
    cursorManager->control = nullptr;
}

void DisplayX::resizeRootWindow() {
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    
    viewTransformation.update(surfaceWidth, surfaceHeight, rootWindow->width, rootWindow->height);
    
    ARect src{};
    ARect dst{};
    
    if (fullscreen) {
        src.left = viewTransformation.viewOffsetX;
        src.top = viewTransformation.viewOffsetY;
        src.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        src.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
        
        dst.left = 0;
        dst.top = 0;
        dst.right = surfaceWidth;
        dst.bottom = surfaceHeight;
    }
    else {
        src.left = 0;
        src.top = 0;
        src.right = surfaceWidth;
        src.bottom = surfaceHeight;
        
        dst.left = viewTransformation.viewOffsetX;
        dst.top = viewTransformation.viewOffsetY;
        dst.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        dst.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
    }
    
    pfnASurfaceTransactionSetGeometry(windowTransaction, rootWindow->control, src, dst, 0);
    pfnASurfaceTransactionSetBuffer(windowTransaction, rootWindow->control, rootWindow->drawable->ahb, -1);
    pfnASurfaceTransactionApply(windowTransaction);
}

void DisplayX::restoreControlState() {
    const auto& windowTree = windowManager->getWindowTree();
    
    for (const auto& entry : windowTree) {
        auto window = entry.second.get();
        if (window == windowManager->getRootWindow()) continue;
        if (!window->control || !window->parent->control) continue;
        
        pfnASurfaceTransactionReparent(windowTransaction, window->control, window->parent->control);
        pfnASurfaceTransactionSetVisibility(windowTransaction, window->control, window->mapped ? ASURFACE_TRANSACTION_VISIBILITY_SHOW : ASURFACE_TRANSACTION_VISIBILITY_HIDE);
        
        if (pfnASurfaceTransactionSetPosition) {
            pfnASurfaceTransactionSetPosition(windowTransaction, window->control, window->x, window->y);
        }
        else {  
            ARect src{};
            ARect dst = {
                .left = window->x,
                .top = window->y,
                .right = window->x + window->width,
                .bottom = window->y + window->height
            };
            pfnASurfaceTransactionSetGeometry(windowTransaction, window->control, src, dst, 0);        
        }
        
        pfnASurfaceTransactionSetBuffer(windowTransaction, window->control, window->enabled ? window->drawable->ahb : nullptr, -1);
        pfnASurfaceTransactionApply(windowTransaction);
    }
    
    repostCursor = true;
    cursorUpdate = true;
}

void DisplayX::toggleFullscreen() {
    auto rootWindow = windowManager->getRootWindow();
    if (!rootWindow) return;
    
    fullscreen = !fullscreen;
    
    ARect src{};
    ARect dst{};
    
    if (fullscreen) {
        src.left = viewTransformation.viewOffsetX;
        src.top = viewTransformation.viewOffsetY;
        src.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        src.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
        
        dst.left = 0;
        dst.top = 0;
        dst.right = surfaceWidth;
        dst.bottom = surfaceHeight;
    }
    else {
        src.left = 0;
        src.top = 0;
        src.right = surfaceWidth;
        src.bottom = surfaceHeight;
        
        dst.left = viewTransformation.viewOffsetX;
        dst.top = viewTransformation.viewOffsetY;
        dst.right = viewTransformation.viewOffsetX + viewTransformation.viewWidth;
        dst.bottom = viewTransformation.viewOffsetY + viewTransformation.viewHeight;
    }
    
    pfnASurfaceTransactionSetGeometry(windowTransaction, rootWindow->control, src, dst, 0);
    pfnASurfaceTransactionApply(windowTransaction);
}

void DisplayX::setPerformanceMode(bool perfMode) {
    this->perfMode = perfMode;
    if (isPerformanceHintAPIAvailable()) {
        auto lock = presentLock.lock();
        float targetFloat = this->perfMode ? xServer->refreshRate * 100.0f : xServer->refreshRate;
        int64_t targetWorkDuration = static_cast<int64_t>(1000000000.0f / targetFloat);
    
        int tid = gettid();
        std::vector<int32_t> tids{tid};
        pfnAPerformanceHintUpdateTargetWorkDuration(performanceHintSession, targetWorkDuration);
    }
}
