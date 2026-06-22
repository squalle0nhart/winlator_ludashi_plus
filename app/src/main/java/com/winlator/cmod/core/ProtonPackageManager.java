package com.winlator.cmod.core;

import android.content.Context;

import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ProtonPackageManager {
    public static final String DEFAULT_IDENTIFIER = "proton-9.0-arm64ec";
    private static final String RELEASE_BASE_URL = "https://github.com/Other-backup/winlator-imagefs/releases/download/protons-zst-latest/";

    public static class PackageInfo {
        public final String identifier;
        public final String title;
        public final String fileName;
        public final long[] partSizes;
        public final String directUrl;

        public PackageInfo(String identifier, String title, String fileName, long[] partSizes) {
            this(identifier, title, fileName, partSizes, null);
        }

        public PackageInfo(String identifier, String title, String fileName, long[] partSizes, String directUrl) {
            this.identifier = identifier;
            this.title = title;
            this.fileName = fileName;
            this.partSizes = partSizes;
            this.directUrl = directUrl;
        }
    }

    private static final List<PackageInfo> PACKAGES = Arrays.asList(
            new PackageInfo("proton-9.0-arm64ec", "Proton 9 arm64ec", "proton-9.0-arm64ec.tar.zst", new long[]{52428800L, 14659103L}),
            new PackageInfo("proton-10-arm64ec", "Proton 10 arm64ec", "proton-10-arm64ec.tar.zst", new long[]{52428800L, 52428800L, 52428800L, 52428800L, 7195940L})
    );

    public static List<PackageInfo> getPackages() {
        return new ArrayList<>(PACKAGES);
    }

    public static PackageInfo getPackage(String identifier) {
        for (PackageInfo packageInfo : PACKAGES)
            if (packageInfo.identifier.equals(identifier)) return packageInfo;
        return null;
    }

    public static boolean isKnownPackage(String identifier) {
        return getPackage(identifier) != null;
    }

    public static File getInstallDir(Context context, String identifier) {
        return new File(ImageFs.find(context).getRootDir(), "opt/" + identifier);
    }

    public static boolean isInstalled(Context context, String identifier) {
        File installDir = getInstallDir(context, identifier);
        File[] files = installDir.listFiles();
        return installDir.isDirectory() && files != null && files.length > 0;
    }

    public static List<String> getInstalledIdentifiers(Context context) {
        ArrayList<String> identifiers = new ArrayList<>();
        for (PackageInfo packageInfo : PACKAGES)
            if (isInstalled(context, packageInfo.identifier)) identifiers.add(packageInfo.identifier);
        return identifiers;
    }

    public static boolean downloadPackage(PackageInfo packageInfo, File output, Callback<Integer> progressCallback) {
        long totalSize = 0;
        for (long size : packageInfo.partSizes) totalSize += size;
        long downloadedSize = 0;
        try (FileOutputStream outputStream = new FileOutputStream(output)) {
            for (int i = 0; i < packageInfo.partSizes.length; i++) {
                String address = packageInfo.directUrl != null
                        ? packageInfo.directUrl
                        : RELEASE_BASE_URL + packageInfo.fileName + "." + String.format("%02d", i);
                URLConnection connection = new URL(address).openConnection();
                connection.connect();
                try (InputStream inputStream = connection.getInputStream()) {
                    byte[] data = new byte[8192];
                    int count;
                    while ((count = inputStream.read(data)) != -1) {
                        outputStream.write(data, 0, count);
                        downloadedSize += count;
                        if (totalSize > 0) progressCallback.call(Math.min(100, (int)(downloadedSize * 100 / totalSize)));
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean installPackage(Context context, String identifier, File archiveFile) {
        return ImageFsInstaller.installWineArchive(context, identifier, archiveFile);
    }

    public static void deletePackage(Context context, String identifier) {
        FileUtils.delete(getInstallDir(context, identifier));
    }
}
