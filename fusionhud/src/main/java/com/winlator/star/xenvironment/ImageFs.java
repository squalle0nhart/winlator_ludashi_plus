package com.winlator.star.xenvironment;

import android.content.Context;

import java.io.File;

/**
 * Minimal standalone stub of Bannerlator's {@code ImageFs}. The full app class models a whole
 * guest root filesystem (Wine prefix, config dir, versioned image) and pulls in {@code FileUtils} /
 * {@code WineInfo}. The HUD only ever calls {@link #find(Context)} and {@link #getTmpDir()} — from
 * {@code FpsCounter.writeSessionSummary}, to pick a writable dir for {@code fps_session.json}.
 *
 * <p>So this stub reproduces just that: {@code getTmpDir()} returns a real, writable, created dir under
 * the app's private files ({@code files/imagefs/usr/tmp}), keeping the session-summary path identical
 * in shape to the app ({@code $ROOT/usr/tmp}). If you sync a HUD change that needs more of {@code ImageFs},
 * port the corresponding methods here.
 */
public class ImageFs {
    private final File rootDir;

    private ImageFs(File rootDir) {
        this.rootDir = rootDir;
    }

    public static ImageFs find(Context context) {
        return new ImageFs(new File(context.getFilesDir(), "imagefs"));
    }

    public static ImageFs find(File rootDir) {
        return new ImageFs(rootDir);
    }

    public File getRootDir() {
        return rootDir;
    }

    /** Writable tmp dir (created on demand), mirroring the app's {@code $ROOT/usr/tmp}. */
    public File getTmpDir() {
        File tmp = new File(rootDir, "usr/tmp");
        if (!tmp.isDirectory()) tmp.mkdirs();
        return tmp;
    }
}
