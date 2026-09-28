package org.chimeramc.client.core.versions;

import android.os.Parcel;
import android.os.Parcelable;

import org.chimeramc.client.util.LauncherStorage;

import java.io.File;

public class GameVersion implements Parcelable {
    public String directoryName;
    public String versionCode;
    public String displayName;
    public File versionDir;
    public boolean isInstalled;
    public String packageName;
    public boolean needsRepair;
    public boolean onlyVersionTxt;
    public boolean onlyAbiList;
    public boolean isExtractFalse;
    public String abiList;
    public boolean versionIsolation;
    public boolean launchVertically;
    public boolean shaderCompatEnabled;
    /** When true, the in-game Mod Menu exposes a resource-pack changer for this instance. */
    public boolean inGamePackChangerEnabled;
    public final File modsDir;

    public GameVersion(String directoryName, String displayName, String versionCode, File versionDir, boolean isOfficial, String packageName, String abiList) {
        this.directoryName = directoryName;
        this.displayName = displayName;
        this.versionCode = versionCode;
        this.versionDir = versionDir;
        this.isInstalled = isOfficial;
        this.packageName = packageName;
        this.needsRepair = false;
        this.onlyVersionTxt = false;
        this.onlyAbiList = false;
        this.isExtractFalse = false;
        this.abiList = abiList;
        this.versionIsolation = !isOfficial;
        this.launchVertically = false;
        this.shaderCompatEnabled = false;
        this.modsDir = versionDir == null ? null : new File(versionDir, LauncherStorage.PROFILE_MODS_DIR);
    }

    protected GameVersion(Parcel in) {
        directoryName = in.readString();
        displayName = in.readString();
        versionCode = in.readString();
        String versionDirPath = in.readString();
        versionDir = versionDirPath == null ? null : new File(versionDirPath);
        isInstalled = in.readByte() != 0;
        packageName = in.readString();
        needsRepair = in.readByte() != 0;
        onlyVersionTxt = in.readByte() != 0;
        onlyAbiList = in.readByte() != 0;
        isExtractFalse = in.readByte()!= 0;
        abiList = in.readString();
        versionIsolation = in.readByte() != 0;
        launchVertically = in.readByte() != 0;
        shaderCompatEnabled = in.readByte() != 0;
        String modsDirPath = in.readString();
        modsDir = modsDirPath == null ? null : new File(modsDirPath);
        inGamePackChangerEnabled = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(directoryName);
        dest.writeString(displayName);
        dest.writeString(versionCode);
        dest.writeString(versionDir == null ? null : versionDir.getAbsolutePath());
        dest.writeByte((byte) (isInstalled ? 1 : 0));
        dest.writeString(packageName);
        dest.writeByte((byte) (needsRepair ? 1 : 0));
        dest.writeByte((byte) (onlyVersionTxt ? 1 : 0));
        dest.writeByte((byte) (onlyAbiList ? 1 : 0));
        dest.writeByte((byte) (isExtractFalse? 1 : 0));
        dest.writeString(abiList);
        dest.writeByte((byte) (versionIsolation ? 1 : 0));
        dest.writeByte((byte) (launchVertically ? 1 : 0));
        dest.writeByte((byte) (shaderCompatEnabled ? 1 : 0));
        dest.writeString(modsDir == null ? null : modsDir.getAbsolutePath());
        dest.writeByte((byte) (inGamePackChangerEnabled ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<GameVersion> CREATOR = new Creator<>() {
        @Override
        public GameVersion createFromParcel(Parcel in) {
            return new GameVersion(in);
        }

        @Override
        public GameVersion[] newArray(int size) {
            return new GameVersion[size];
        }
    };

    public String getStorageProfileId() {
        if (isInstalled) return LauncherStorage.INSTALLED_MINECRAFT_PROFILE_ID;
        return LauncherStorage.sanitizeProfileId(directoryName);
    }

    /**
     * Identity is the instance directory, not the object reference.
     *
     * <p>A {@code GameVersion} reaches a screen either from {@code VersionManager}'s own list or
     * re-materialised from an {@code Intent} extra. Without this, {@code equals} was reference
     * equality, so a delete performed from the settings screen compared the parceled copy against
     * the manager's instance, never matched, and left the deleted version selected — which is why
     * it kept showing as "last played" after being removed.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameVersion)) return false;
        GameVersion other = (GameVersion) o;
        if (versionDir != null && other.versionDir != null) {
            return versionDir.getAbsolutePath().equals(other.versionDir.getAbsolutePath());
        }
        if (directoryName != null && other.directoryName != null) {
            return directoryName.equals(other.directoryName);
        }
        return versionCode != null && versionCode.equals(other.versionCode);
    }

    @Override
    public int hashCode() {
        if (versionDir != null) return versionDir.getAbsolutePath().hashCode();
        if (directoryName != null) return directoryName.hashCode();
        return versionCode == null ? 0 : versionCode.hashCode();
    }
}
