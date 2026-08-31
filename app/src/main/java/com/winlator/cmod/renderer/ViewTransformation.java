package com.winlator.cmod.renderer;

public class ViewTransformation {
    public static final int FULLSCREEN_OFF = 0;
    public static final int FULLSCREEN_FIT = 1;
    public static final int FULLSCREEN_STRETCH = 2;
    public static final int FULLSCREEN_FILL = 3;
    public static final int FULLSCREEN_INTEGER = 4;

    public int viewOffsetX;
    public int viewOffsetY;
    public int viewWidth;
    public int viewHeight;
    public float aspect;
    public float sceneScaleX;
    public float sceneScaleY;
    public float sceneOffsetX;
    public float sceneOffsetY;

    public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight) {
        update(outerWidth, outerHeight, innerWidth, innerHeight, FULLSCREEN_FIT);
    }

    public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight, int fullscreenMode) {
        float scaleX = (float)outerWidth / innerWidth;
        float scaleY = (float)outerHeight / innerHeight;
        if (fullscreenMode == FULLSCREEN_FILL) aspect = Math.max(scaleX, scaleY);
        else if (fullscreenMode == FULLSCREEN_INTEGER)
            aspect = Math.max(1.0f, (float)Math.floor(Math.min(scaleX, scaleY)));
        else aspect = Math.min(scaleX, scaleY);
        viewWidth = (int)Math.ceil(innerWidth * aspect);
        viewHeight = (int)Math.ceil(innerHeight * aspect);
        viewOffsetX = (int)((outerWidth - innerWidth * aspect) * 0.5f);
        viewOffsetY = (int)((outerHeight - innerHeight * aspect) * 0.5f);

        sceneScaleX = (innerWidth * aspect) / outerWidth;
        sceneScaleY = (innerHeight * aspect) / outerHeight;
        sceneOffsetX = (innerWidth - innerWidth * sceneScaleX) * 0.5f;
        sceneOffsetY = (innerHeight - innerHeight * sceneScaleY) * 0.5f;
    }
}
