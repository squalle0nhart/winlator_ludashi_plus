#pragma once

#include <mutex>

#include "renderer_jni.hpp"

struct Drawable {
    int id;
    int width;
    int format;
    int height;
    int stride;
    int textureId;
    bool isDirty;
    bool sizeChanged;
    bool isDirectContent;
    bool isDisplayX = false;
    int syncFence = -1;
    void *data;
    jobject drawableObj;
    AHardwareBuffer *ahb;
};
