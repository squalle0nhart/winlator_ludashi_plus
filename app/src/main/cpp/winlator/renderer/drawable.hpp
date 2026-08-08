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
    bool isDisplayX;
    void *data;
    jobject drawableObj;
    AHardwareBuffer *ahb;
    int sync_fence;
};