package com.winlator.cmod.renderer;

import com.winlator.cmod.widget.XServerView;

public interface HostRenderer {
    XServerView getXServerView();
    void setRenderingEnabled(boolean enabled);
    void requestRender();
    void forceCleanup();
    void setCursorVisible(boolean visible);
    boolean isCursorVisible();
    void setUnviewableWMClasses(String wmClasses);
    void setFilterMode(int mode);
    void setMagnifierZoom(float zoom);
    float getMagnifierZoom();
    void toggleFullscreen();
    boolean isFullscreen();
    void setFullscreenMode(int mode);
    int getFullscreenMode();
    void setScreenOffsetYRelativeToCursor(boolean b);
    boolean isScreenOffsetYRelativeToCursor();
    void setFpsWindowId(int id);
    void setFrameRating(Object fr);
    int getFpsLimit();
    void setFpsLimit(int limit);
    int getSurfaceWidth();
    int getSurfaceHeight();

    default void setPipMode(boolean pip) {}
    default void onHostSurfaceChanged(int width, int height) {}
}
