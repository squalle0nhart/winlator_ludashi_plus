#pragma once

#include <cmath>
#include <algorithm>

class ViewTransformation {
public:
    int   viewOffsetX  = 0;
    int   viewOffsetY  = 0;
    int   viewWidth    = 0;
    int   viewHeight   = 0;
    float aspect       = 1.f;
    float sceneScaleX  = 1.f;
    float sceneScaleY  = 1.f;
    float sceneOffsetX = 0.f;
    float sceneOffsetY = 0.f;

    void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight);
};
