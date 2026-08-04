package com.winlator.cmod.xserver;

import android.graphics.Bitmap;

public class Pixmap extends XResource {
    public final Drawable drawable;

    public Pixmap(Drawable drawable) {
        super(drawable.id);
        this.drawable = drawable;
    }

    public Bitmap toBitmap(Pixmap maskPixmap) {
        Drawable mask = maskPixmap != null ? maskPixmap.drawable : null;
        Drawable first = mask == null || Drawable.comesBefore(drawable, mask)
                ? drawable : mask;
        Drawable second = first == drawable ? mask : drawable;
        synchronized (first.renderLock) {
            if (second != null) {
                synchronized (second.renderLock) {
                    return toBitmapLocked(mask);
                }
            }
            return toBitmapLocked(null);
        }
    }

    private Bitmap toBitmapLocked(Drawable mask) {
        boolean relockColor = drawable.unlockImportedBufferForNativeAccess();
        boolean relockMask = mask != null && mask != drawable
                && mask.unlockImportedBufferForNativeAccess();
        try {
            Bitmap bitmap = Bitmap.createBitmap(drawable.width, drawable.height,
                    Bitmap.Config.ARGB_8888);
            toBitmap(drawable.getStride(), drawable.backingAHB,
                    mask != null ? mask.getStride() : 0,
                    mask != null ? mask.backingAHB : 0, bitmap);
            return bitmap;
        } finally {
            drawable.restoreImportedBufferAfterNativeAccess(relockColor);
            if (mask != null && mask != drawable) {
                mask.restoreImportedBufferAfterNativeAccess(relockMask);
            }
        }
    }

    private static native void toBitmap(short colorStride, long colorData, short maskStride,
            long maskData, Bitmap bitmap);
}
