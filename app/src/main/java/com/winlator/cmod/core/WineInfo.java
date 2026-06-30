package com.winlator.cmod.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.xenvironment.ImageFs;

import java.util.Locale;

public class WineInfo implements Parcelable {
    public static final WineInfo MAIN_WINE_VERSION = new WineInfo("proton", "9.0", "arm64ec");
    public final String version;
    public final String type;
    public String subversion;
    public final String path;
    private String arch;

    public WineInfo(String type, String version, String arch) {
        this.type = type;
        this.version = version;
        this.subversion = null;
        this.arch = arch;
        this.path = null;
    }

    public WineInfo(String type, String version, String subversion, String arch, String path) {
        this.type = type;
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.arch = arch;
        this.path = path;
    }

    public WineInfo(String type, String version, String arch, String path) {
        this.type = type;
        this.version = version;
        this.arch = arch;
        this.path = path;
    }

    private WineInfo(Parcel in) {
        type = in.readString();
        version = in.readString();
        subversion = in.readString();
        arch = in.readString();
        path = in.readString();
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public boolean isWin64() {
        return arch.equals("x86_64") || arch.equals("arm64ec");
    }

    public boolean isArm64EC() { return arch.equals("arm64ec"); }

    public String identifier() {
        if (type.equals("proton"))
            return "proton-" + fullVersion() + "-"+ arch;
        else
            return "wine-" + fullVersion() + "-" + arch;
    }

    public String fullVersion() {
        return version+(subversion != null ? "-"+subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        if (type.equals("proton"))
            return "Proton "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
        else
            return "Wine "+fullVersion()+(this == MAIN_WINE_VERSION ? " (Custom)" : "");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(arch);
        dest.writeString(path);
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, ContentsManager contentsManager, String identifier) {
        ImageFs imageFs = ImageFs.find(context);
        Log.d("WineInfo", "Creating WineInfo from identifier " + identifier);

        if (identifier.equals(MAIN_WINE_VERSION.identifier())) return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, imageFs.getRootDir().getPath() + "/opt/" + MAIN_WINE_VERSION.identifier());

        ContentProfile wineProfile = contentsManager.getProfileByEntryName(identifier);
        String normalizedIdentifier = normalizeIdentifier(identifier, wineProfile);

        WineDescriptor descriptor = parseIdentifier(normalizedIdentifier.toLowerCase());
        if (descriptor == null) {
            Log.w("WineInfo", "Falling back to main Wine version because identifier could not be parsed: " + normalizedIdentifier);
            return new WineInfo(MAIN_WINE_VERSION.type, MAIN_WINE_VERSION.version, MAIN_WINE_VERSION.arch, imageFs.getRootDir().getPath() + "/opt/" + MAIN_WINE_VERSION.identifier());
        }

        String path = "";
        String[] wineVersions = context.getResources().getStringArray(R.array.wine_entries);
        for (String wineVersion : wineVersions) {
            if (wineVersion.equals(descriptor.identifier)) {
                path = imageFs.getRootDir().getPath() + "/opt/" + descriptor.identifier;
                break;
            }
        }
        if (path.isEmpty() && ProtonPackageManager.isInstalled(context, descriptor.identifier)) {
            path = imageFs.getRootDir().getPath() + "/opt/" + descriptor.identifier;
        }

        if (wineProfile != null && (wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE || wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON)) {
            path = contentsManager.getInstallDir(context, wineProfile).getPath();
        }

        return new WineInfo(descriptor.type, descriptor.version, descriptor.subversion, descriptor.arch, path);
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null ||wineVersion.equals(MAIN_WINE_VERSION.identifier());
    }

    private static String normalizeIdentifier(String identifier, ContentProfile wineProfile) {
        String normalizedFromProfile = normalizeProfileIdentifier(wineProfile);
        if (normalizedFromProfile != null) return normalizedFromProfile;

        String normalized = extractIdentifier(identifier);
        if (normalized != null) return normalized;

        return identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
    }

    private static String normalizeProfileIdentifier(ContentProfile wineProfile) {
        if (wineProfile == null) return null;
        if (wineProfile.type != ContentProfile.ContentType.CONTENT_TYPE_WINE
                && wineProfile.type != ContentProfile.ContentType.CONTENT_TYPE_PROTON) {
            return null;
        }

        String normalized = extractIdentifier(wineProfile.verName);
        if (normalized != null) return normalized;

        String prefix = wineProfile.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON ? "proton-" : "wine-";
        return extractIdentifier(prefix + wineProfile.verName);
    }

    private static String extractIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isEmpty()) return null;

        String candidate = rawIdentifier.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        int typeIndex = candidate.indexOf("proton-");
        if (typeIndex < 0) typeIndex = candidate.indexOf("wine-");
        if (typeIndex > 0) candidate = candidate.substring(typeIndex);

        for (String arch : new String[] {"arm64ec", "x86_64", "x86"}) {
            int archIndex = candidate.indexOf("-" + arch);
            if (archIndex < 0) continue;

            String normalized = candidate.substring(0, archIndex + arch.length() + 1);
            if (normalized.startsWith("proton-proton-")) {
                normalized = normalized.substring("proton-".length());
            } else if (normalized.startsWith("wine-wine-")) {
                normalized = normalized.substring("wine-".length());
            }
            return normalized;
        }
        return null;
    }

    private static WineDescriptor parseIdentifier(String identifier) {
        int firstDash = identifier.indexOf('-');
        int lastDash = identifier.lastIndexOf('-');
        if (firstDash <= 0 || lastDash <= firstDash + 1 || lastDash >= identifier.length() - 1) return null;

        String type = identifier.substring(0, firstDash);
        String arch = identifier.substring(lastDash + 1);
        if (!type.equals("wine") && !type.equals("proton")) return null;
        if (!arch.equals("x86") && !arch.equals("x86_64") && !arch.equals("arm64ec")) return null;

        String versionSegment = identifier.substring(firstDash + 1, lastDash);
        if (versionSegment.isEmpty()) return null;

        String version;
        String subversion = null;
        int versionDash = versionSegment.indexOf('-');
        if (versionDash >= 0) {
            version = versionSegment.substring(0, versionDash);
            subversion = versionSegment.substring(versionDash + 1);
        } else {
            version = versionSegment;
        }

        if (version.isEmpty()) return null;
        if (subversion != null && subversion.isEmpty()) subversion = null;
        return new WineDescriptor(type, version, subversion, arch);
    }

    private static class WineDescriptor {
        final String type;
        final String version;
        final String subversion;
        final String arch;
        final String identifier;

        WineDescriptor(String type, String version, String subversion, String arch) {
            this.type = type;
            this.version = version;
            this.subversion = subversion;
            this.arch = arch;
            this.identifier = type + "-" + version + (subversion != null ? "-" + subversion : "") + "-" + arch;
        }
    }
}
