#pragma once

#include <string>
#include <atomic>
#include <unordered_map>
#include <vector>

#include "drawable.hpp"
#include "renderer_jni.hpp"
#include "cursor.hpp"

struct Window {
    int id;
    int width;
    int height;
    int x;
    int y;
    int z_order;
    std::string className;
    bool mapped;
    bool inputOutput;
    bool hasContent;
    std::unique_ptr<struct Drawable> drawable;
    Window *parent;
    Cursor *cursor;
    bool enabled;
    std::vector<Window *> children;
    jobject attributes;
    jobject windowObj;
    std::unordered_map<int, std::unique_ptr<struct Drawable>> directContents;
    Drawable *currentDirectContent;
    // Number of True DisplayX swapchains owned by this window or one of its
    // descendants. DXVK may Present to a parent of the Vulkan surface window,
    // so ordinary X11/DRI3 updates for that branch must not replace its buffer.
    std::atomic_uint32_t displayXSwapchainCount{0};
    ASurfaceControl *control;

    bool hasDirectContents() {
        return !directContents.empty();
    }

    int getRootX() {
        int rootX = x;
        auto window = parent;
        while (window != nullptr) {
            rootX += window->x;
            window = window->parent;
        }
        return rootX;
    }

    int getRootY() {
        int rootY = y;
        auto window = parent;
        while (window != nullptr) {
            rootY += window->y;
            window = window->parent;
        }
        return rootY;
    }
};

struct WindowLock {
    std::mutex mutex;

   std::unique_lock<std::mutex> lock() {
       return std::unique_lock<std::mutex>(mutex);
   }
};

class WindowManager {
    private:
        std::unordered_map<int, std::unique_ptr<struct Window>> windows;
        Window *rootWindow = nullptr;
        std::string unviewableWMClass;

    public:
        WindowLock windowLock;

        WindowManager() {}
        void changeZOrder(int stackMode, Window *window, Window *sibling);
        void disableAllDescendants(Window *window);
        Window *getWindow(int id);
        void addWindow(int id, std::unique_ptr<struct Window> window);
        void deleteWindow(Window *window);
        Window* getRootWindow();
        void setRootWindow(Window *window);
        void reparentWindow(Window *window, Window *parent);
        void setUnviewableWMClass(std::string className);
        std::string getUnviewableWMClass();
        std::unordered_map<int, std::unique_ptr<struct Window>>& getWindowTree();
};
