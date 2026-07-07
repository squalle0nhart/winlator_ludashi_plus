package com.winlator.cmod.renderer;

import com.winlator.cmod.container.Container;

public class ViewTransformation {
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
        update(outerWidth, outerHeight, innerWidth, innerHeight, Container.FULLSCREEN_FIT);
    }

    public void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight, int fullscreenMode) {
        float scaleX = (float) outerWidth / innerWidth;
        float scaleY = (float) outerHeight / innerHeight;

        switch (fullscreenMode) {
            case Container.FULLSCREEN_FILL:
                aspect = Math.max(scaleX, scaleY);
                break;
            case Container.FULLSCREEN_INTEGER:
                aspect = Math.max(1.0f, (float) Math.floor(Math.min(scaleX, scaleY)));
                break;
            case Container.FULLSCREEN_OFF:
            case Container.FULLSCREEN_FIT:
            default:
                aspect = Math.min(scaleX, scaleY);
                break;
        }

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
