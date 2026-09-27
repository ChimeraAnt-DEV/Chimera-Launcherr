package org.chimeramc.client.core.versions;

public class VersionProfileMetadata {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public String profileId;
    public String directoryName;
    public String versionName;
    public String displayName;
    public boolean versionIsolation;
    public boolean launchVertically;
    public boolean shaderCompatEnabled;
    public boolean inGamePackChangerEnabled;
    public boolean installed;
    public String packageName;
    public long updatedAt;
}
