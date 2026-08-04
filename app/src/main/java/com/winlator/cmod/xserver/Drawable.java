package com.winlator.cmod.xserver;

import com.winlator.cmod.core.Callback;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.renderer.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

/**
 * X11 drawable backed by an Android Hardware Buffer.
 *
 * <p>This is the Pipetto modern drawable model adapted to Ludashi's GL, Vulkan and
 * SurfaceFlinger renderer interfaces. CPU drawing operations lock the AHardwareBuffer only for
 * the duration of the operation. Imported DRI3 {@link GPUImage} buffers remain externally owned.
 * Owned buffers are explicitly released when the drawable is destroyed.</p>
 */
public class Drawable extends XResource {
    private static final AtomicLong NEXT_LOCK_ORDER = new AtomicLong();
    public static final int HAL_PIXEL_FORMAT_RGBA_8888 = 1;
    public static final int HAL_PIXEL_FORMAT_BGRA_8888 = 5;

    public final short width;
    public final short height;
    public final Visual visual;
    public final Object renderLock = new Object();
    private final long lockOrder = NEXT_LOCK_ORDER.getAndIncrement();

    public short stride;
    public long backingAHB;
    public int format = HAL_PIXEL_FORMAT_BGRA_8888;

    private Texture texture = new Texture();
    private boolean ownsBackingAHB = true;
    private boolean directScanout = false;
    private Runnable onDrawListener;
    private Callback<Drawable> onDestroyListener;

    static {
        System.loadLibrary("winlator");
    }

    public Drawable(int id, int width, int height, Visual visual) {
        this(id, width, height, visual, HAL_PIXEL_FORMAT_BGRA_8888);
    }

    public Drawable(int id, int width, int height, Visual visual, int format) {
        super(id);
        this.width = (short) width;
        this.height = (short) height;
        this.visual = visual;
        this.format = format;
        backingAHB = allocate(width, height, format);
        if (backingAHB == 0) {
            throw new IllegalStateException("Drawable AHardwareBuffer allocation failed");
        }
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        synchronized (renderLock) {
            if (texture instanceof GPUImage) {
                releaseOwnedBackingBuffer();
                GPUImage gpuImage = (GPUImage) texture;
                backingAHB = gpuImage.getHardwareBufferPtr();
                stride = gpuImage.getStride();
                ownsBackingAHB = false;
            } else if (backingAHB == 0) {
                backingAHB = allocate(width, height, format);
                ownsBackingAHB = true;
            }
            this.texture = texture != null ? texture : new Texture();
        }
    }

    public GPUImage getGPUImage() {
        return texture instanceof GPUImage ? (GPUImage) texture : null;
    }

    public boolean ownsBackingAHB() {
        return ownsBackingAHB;
    }

    public void destroyBackingBuffer() {
        synchronized (renderLock) {
            releaseOwnedBackingBuffer();
            if (!ownsBackingAHB) backingAHB = 0;
        }
    }

    private void releaseOwnedBackingBuffer() {
        if (ownsBackingAHB && backingAHB != 0) {
            release(backingAHB);
            backingAHB = 0;
        }
    }

    public short getStride() {
        GPUImage gpuImage = getGPUImage();
        return gpuImage != null && gpuImage.getStride() > 0 ? gpuImage.getStride() : stride;
    }

    public void setDirectScanout(boolean value) {
        directScanout = value;
    }

    public boolean isDirectScanout() {
        return directScanout;
    }

    public Runnable getOnDrawListener() {
        return onDrawListener;
    }

    public void setOnDrawListener(Runnable onDrawListener) {
        this.onDrawListener = onDrawListener;
    }

    public Callback<Drawable> getOnDestroyListener() {
        return onDestroyListener;
    }

    public void setOnDestroyListener(Callback<Drawable> onDestroyListener) {
        this.onDestroyListener = onDestroyListener;
    }

    public ByteBuffer lockBuffer() {
        GPUImage gpuImage = getGPUImage();
        if (gpuImage != null) {
            gpuImage.lock();
            return gpuImage.getVirtualData();
        }
        return backingAHB != 0 ? lockBuffer(backingAHB) : null;
    }

    public void unlockBuffer() {
        if (getGPUImage() == null && backingAHB != 0) unlockBuffer(backingAHB);
    }

    public void drawImage(short srcX, short srcY, short dstX, short dstY, short width,
            short height, byte depth, ByteBuffer srcData, short totalWidth, short totalHeight) {
        synchronized (renderLock) {
            boolean relock = unlockImportedBufferForNativeAccess();
            try {
                if (depth == 1) {
                    drawBitmap(width, height, srcData, getStride(), backingAHB);
                } else if (depth == 24 || depth == 32) {
                    dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
                    dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
                    if (dstX + width > this.width) width = (short) (this.width - dstX);
                    if (dstY + height > this.height) height = (short) (this.height - dstY);
                    copyAreaFromBuffer(srcX, srcY, dstX, dstY, width, height, totalWidth,
                            getStride(), srcData, backingAHB);
                }
            } finally {
                if (relock) getGPUImage().lock();
                srcData.rewind();
            }
            markDrawn();
        }
    }

    public ByteBuffer getImage(short x, short y, short width, short height) {
        synchronized (renderLock) {
            ByteBuffer dstData = ByteBuffer.allocateDirect(width * height * 4)
                    .order(ByteOrder.LITTLE_ENDIAN);
            x = (short) Mathf.clamp(x, 0, this.width - 1);
            y = (short) Mathf.clamp(y, 0, this.height - 1);
            if (x + width > this.width) width = (short) (this.width - x);
            if (y + height > this.height) height = (short) (this.height - y);

            boolean relock = unlockImportedBufferForNativeAccess();
            try {
                copyAreaToBuffer(x, y, (short) 0, (short) 0, width, height, getStride(),
                        width, backingAHB, dstData);
            } finally {
                if (relock) getGPUImage().lock();
            }
            dstData.rewind();
            return dstData;
        }
    }

    public void copyArea(short srcX, short srcY, short dstX, short dstY, short width,
            short height, Drawable drawable) {
        copyArea(srcX, srcY, dstX, dstY, width, height, drawable,
                GraphicsContext.Function.COPY);
    }

    public void copyArea(short srcX, short srcY, short dstX, short dstY, short width,
            short height, Drawable drawable, GraphicsContext.Function gcFunction) {
        Drawable first = comesBefore(this, drawable) ? this : drawable;
        Drawable second = first == this ? drawable : this;
        synchronized (first.renderLock) {
            synchronized (second.renderLock) {
                dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
                dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
                if (dstX + width > this.width) width = (short) (this.width - dstX);
                if (dstY + height > this.height) height = (short) (this.height - dstY);

                boolean relockSrc = drawable.unlockImportedBufferForNativeAccess();
                boolean relockDst = this != drawable && unlockImportedBufferForNativeAccess();
                try {
                    if (gcFunction == GraphicsContext.Function.COPY) {
                        copyAreaAHB(srcX, srcY, dstX, dstY, width, height,
                                drawable.getStride(), getStride(), drawable.backingAHB, backingAHB);
                    } else {
                        copyAreaOp(srcX, srcY, dstX, dstY, width, height,
                                drawable.getStride(), getStride(), drawable.backingAHB, backingAHB,
                                gcFunction.ordinal());
                    }
                } finally {
                    if (relockSrc) drawable.getGPUImage().lock();
                    if (relockDst) getGPUImage().lock();
                }
                markDrawn();
            }
        }
    }

    public void fillColor(int color) {
        fillRect(0, 0, width, height, color);
    }

    public void fillRect(int x, int y, int width, int height, int color) {
        synchronized (renderLock) {
            x = Mathf.clamp(x, 0, this.width - 1);
            y = Mathf.clamp(y, 0, this.height - 1);
            if (x + width > this.width) width = this.width - x;
            if (y + height > this.height) height = this.height - y;
            boolean relock = unlockImportedBufferForNativeAccess();
            try {
                fillRectAHB((short) x, (short) y, (short) width, (short) height, color,
                        getStride(), backingAHB);
            } finally {
                if (relock) getGPUImage().lock();
            }
            markDrawn();
        }
    }

    public void drawLines(int color, int lineWidth, short... points) {
        for (int i = 2; i < points.length; i += 2) {
            drawLine(points[i - 2], points[i - 1], points[i], points[i + 1], color, lineWidth);
        }
    }

    public void drawLine(int x0, int y0, int x1, int y1, int color, int lineWidth) {
        synchronized (renderLock) {
            x0 = Mathf.clamp(x0, 0, width - lineWidth);
            y0 = Mathf.clamp(y0, 0, height - lineWidth);
            x1 = Mathf.clamp(x1, 0, width - lineWidth);
            y1 = Mathf.clamp(y1, 0, height - lineWidth);
            boolean relock = unlockImportedBufferForNativeAccess();
            try {
                drawLineAHB((short) x0, (short) y0, (short) x1, (short) y1, color,
                        (short) lineWidth, getStride(), backingAHB);
            } finally {
                if (relock) getGPUImage().lock();
            }
            markDrawn();
        }
    }

    public void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue,
            byte backRed, byte backGreen, byte backBlue, Drawable srcDrawable,
            Drawable maskDrawable) {
        synchronized (renderLock) {
            boolean relockSrc = srcDrawable.unlockImportedBufferForNativeAccess();
            boolean relockMask = maskDrawable.unlockImportedBufferForNativeAccess();
            boolean relockDst = unlockImportedBufferForNativeAccess();
            try {
                drawAlphaMaskedBitmapAHB(foreRed, foreGreen, foreBlue, backRed, backGreen,
                        backBlue, srcDrawable.backingAHB, srcDrawable.getStride(),
                        maskDrawable.backingAHB, maskDrawable.getStride(), width, height,
                        getStride(), backingAHB);
            } finally {
                if (relockSrc) srcDrawable.getGPUImage().lock();
                if (relockMask) maskDrawable.getGPUImage().lock();
                if (relockDst) getGPUImage().lock();
            }
            markDrawn();
        }
    }

    public void updateDirect() {
        markDrawn();
    }

    boolean unlockImportedBufferForNativeAccess() {
        GPUImage gpuImage = getGPUImage();
        boolean wasLocked = gpuImage != null && gpuImage.isCpuLocked();
        if (wasLocked) gpuImage.unlock();
        return wasLocked;
    }

    void restoreImportedBufferAfterNativeAccess(boolean wasLocked) {
        GPUImage gpuImage = getGPUImage();
        if (wasLocked && gpuImage != null) gpuImage.lock();
    }

    static boolean comesBefore(Drawable first, Drawable second) {
        return first == second || first.id < second.id
                || (first.id == second.id && first.lockOrder < second.lockOrder);
    }

    private void markDrawn() {
        if (texture != null) texture.setNeedsUpdate(true);
        if (onDrawListener != null) onDrawListener.run();
    }

    private static native void drawBitmap(short width, short height, ByteBuffer srcData,
            short dstStride, long dstAHB);
    private static native void copyAreaFromBuffer(short srcX, short srcY, short dstX,
            short dstY, short width, short height, short srcStride, short dstStride,
            ByteBuffer srcData, long dstAHB);
    private static native void copyAreaToBuffer(short srcX, short srcY, short dstX,
            short dstY, short width, short height, short srcStride, short dstStride,
            long srcAHB, ByteBuffer dstData);
    private static native void copyAreaAHB(short srcX, short srcY, short dstX, short dstY,
            short width, short height, short srcStride, short dstStride, long srcAHB,
            long dstAHB);
    private static native void copyAreaOp(short srcX, short srcY, short dstX, short dstY,
            short width, short height, short srcStride, short dstStride, long srcAHB,
            long dstAHB, int gcFunction);
    private static native void fillRectAHB(short x, short y, short width, short height,
            int color, short stride, long dstAHB);
    private static native void drawLineAHB(short x0, short y0, short x1, short y1, int color,
            short lineWidth, short stride, long dstAHB);
    private static native void drawAlphaMaskedBitmapAHB(byte foreRed, byte foreGreen,
            byte foreBlue, byte backRed, byte backGreen, byte backBlue, long srcAHB,
            short srcStride, long maskAHB, short maskStride, short width, short height,
            short dstStride, long dstAHB);

    private native long allocate(int width, int height, int format);
    private native void release(long ahb);
    private native ByteBuffer lockBuffer(long ahb);
    private native void unlockBuffer(long ahb);
}
