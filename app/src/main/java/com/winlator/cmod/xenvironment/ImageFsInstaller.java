package com.winlator.cmod.xenvironment;

import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DownloadProgressDialog;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ImageFsInstaller {
    public static final byte LATEST_VERSION = 22;
    private static final String TAG = "ImageFsInstaller";
    
    public abstract interface onInstallationFinish {
        public void call();
    }

    private static void resetContainerImgVersions(Context context) {
        ContainerManager manager = new ContainerManager(context);
        for (Container container : manager.getContainers()) {
            container.putExtra("imgVersion", null);
            container.saveData();
        }
    }

    public static boolean installWineArchive(final Context context, String version, File archiveFile) {
        File rootDir = ImageFs.find(context).getRootDir();
        File outFile = new File(rootDir, "opt/" + version);
        FileUtils.delete(outFile);
        outFile.mkdirs();
        boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, archiveFile, outFile);
        if (!success) success = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, archiveFile, outFile);
        if (!success) FileUtils.delete(outFile);
        return success;
    }

    public static void installWineFromAssets(final DownloadProgressDialog dialog, final MainActivity activity) {
        String[] versions = activity.getResources().getStringArray(R.array.wine_entries);
        File rootDir = ImageFs.find(activity).getRootDir();
        final byte compressionRatio = 22;

        activity.runOnUiThread(() -> dialog.setMessage(R.string.installing_wine_files));

        for (String version : versions) {
            File outFile = new File(rootDir, "opt/" + version);
            outFile.mkdirs();
            final String zstdAsset = version + ".tar.zst";
            final String xzAsset = version + ".txz";
            final boolean useZstd = FileUtils.getSize(activity, zstdAsset) > 0;
            final long assetSize = FileUtils.getSize(activity, useZstd ? zstdAsset : xzAsset);
            final long contentLength = (long)(assetSize * (100.0f / compressionRatio));
            AtomicLong totalSizeRef = new AtomicLong();

            OnExtractFileListener progressListener = (file, size) -> {
                if (size > 0) {
                    long totalSize = totalSizeRef.addAndGet(size);
                    final int progress = (int)(((float)totalSize / contentLength) * 100);
                    activity.runOnUiThread(() -> dialog.setProgress(progress));
                }
                return file;
            };

            boolean success = useZstd && TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, activity, zstdAsset, outFile, progressListener);
            if (!success) {
                totalSizeRef.set(0);
                success = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, activity, xzAsset, outFile, progressListener);
            }
            if (!success) {
                Log.e(TAG, "Failed to extract bundled wine runtime for " + version);
            }
         }
    }

    public static void installDriversFromAssets(final DownloadProgressDialog dialog, final MainActivity activity) {
        
        activity.runOnUiThread(() -> dialog.setMessage(R.string.installing_wine_files));
        AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(activity);
        String[] adrenotoolsAssetDrivers = activity.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries);

        for (String driver : adrenotoolsAssetDrivers) {
            final byte compressionRatio = 22;
            final long contentLength = (long)(FileUtils.getSize(activity, adrenotoolsManager.getAssetPath(driver)) * (100.0f / compressionRatio));
            AtomicLong totalSizeRef = new AtomicLong();
            adrenotoolsManager.extractDriverFromResources(driver, (file, size) -> {
                if (size > 0) {
                    long totalSize = totalSizeRef.addAndGet(size);
                    final int progress = (int)(((float)totalSize / contentLength) * 100);
                    activity.runOnUiThread(() -> dialog.setProgress(progress));
                }
                return file;
            });
         }   
    }

    public static void installFromAssets(final MainActivity activity, onInstallationFinish callback) {
        AppUtils.keepScreenOn(activity);
        ImageFs imageFs = ImageFs.find(activity);
        File rootDir = imageFs.getRootDir();

        SettingsFragment.resetEmulatorsVersion(activity);

        final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
        dialog.show(R.string.installing_system_files);
        
        Executors.newSingleThreadExecutor().execute(() -> {
            clearRootDir(rootDir);
            final byte compressionRatio = 22;
            final String zstdAsset = "imagefs.tar.zst";
            final String xzAsset = "imagefs.txz";
            final boolean useZstd = FileUtils.getSize(activity, zstdAsset) > 0;
            final long assetSize = FileUtils.getSize(activity, useZstd ? zstdAsset : xzAsset);
            final long contentLength = (long)(assetSize * (100.0f / compressionRatio));
            AtomicLong totalSizeRef = new AtomicLong();

            OnExtractFileListener progressListener = (file, size) -> {
                if (size > 0) {
                    long totalSize = totalSizeRef.addAndGet(size);
                    final int progress = (int)(((float)totalSize / contentLength) * 100);
                    activity.runOnUiThread(() -> dialog.setProgress(progress));
                }
                return file;
            };

            boolean success = useZstd && TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, activity, zstdAsset, rootDir, progressListener);
            if (!success) {
                totalSizeRef.set(0);
                success = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, activity, xzAsset, rootDir, progressListener);
            }

            if (success) {
                installWineFromAssets(dialog, activity);
                installDriversFromAssets(dialog, activity);
                imageFs.createImgVersionFile(LATEST_VERSION);
                FileUtils.symlink("libSDL2-2.0.so", new File(imageFs.getLibDir(), "libSDL2-2.0.so.0").getAbsolutePath());
                resetContainerImgVersions(activity);
            }
            else {
                Log.e(TAG, "Failed to extract imagefs from bundled assets");
                AppUtils.showToast(activity, R.string.unable_to_install_system_files);
            }
            
            dialog.closeOnUiThread();
            activity.runOnUiThread(() -> {if (callback != null) callback.call();});
        });
    }

    public static boolean installIfNeeded(final MainActivity activity, onInstallationFinish callback) {
        ImageFs imageFs = ImageFs.find(activity);
        
        if (!imageFs.isValid() || imageFs.getVersion() < LATEST_VERSION || !hasBundledWineRuntime(activity, imageFs)) {
            installFromAssets(activity, callback);
            return true;
        }    
        
        return false;
    }

    private static boolean hasBundledWineRuntime(Context context, ImageFs imageFs) {
        File rootDir = imageFs.getRootDir();
        String[] versions = context.getResources().getStringArray(R.array.wine_entries);
        for (String version : versions) {
            File wineDir = new File(rootDir, "opt/" + version);
            File libWineDir = new File(wineDir, "lib/wine");
            File prefixPack = new File(wineDir, "prefixPack.txz");
            if (!wineDir.isDirectory() || (!libWineDir.isDirectory() && !prefixPack.isFile())) {
                Log.w(TAG, "Missing bundled wine runtime for " + version + " under " + wineDir.getAbsolutePath());
                return false;
            }
        }
        return true;
    }

    private static void clearOptDir(File optDir) {
        File[] files = optDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals("installed-wine")) continue;
                FileUtils.delete(file);
            }
        }
    }

    private static void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String name = file.getName();
                        if (name.equals("home")) {
                            continue;
                        }
                    }
                    FileUtils.delete(file);
                }
            }
        }
        else rootDir.mkdirs();
    }
}
