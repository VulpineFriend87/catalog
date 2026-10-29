package top.vulpine.catalog.modrinth.model;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A single file attached to a Modrinth version.
 *
 * <p>The {@link #primary() primary} file is the plugin jar. Catalog installs only that one.</p>
 *
 * <p>Bound by Gson through field names. There is no constructor.</p>
 */
@Getter
@Accessors(fluent = true)
public final class VersionFile {

    private Hashes hashes;
    private String url;
    private String filename;
    private boolean primary;
    private long size;
    private String fileType;

    /**
     * The SHA-512 of this file.
     *
     * @return the hash, or null if Modrinth did not report one
     */
    public String sha512() {
        return hashes == null ? null : hashes.sha512;
    }

    /**
     * The SHA-1 of this file.
     *
     * @return the hash, or null if Modrinth did not report one
     */
    public String sha1() {
        return hashes == null ? null : hashes.sha1;
    }

    @Getter
    @Accessors(fluent = true)
    public static final class Hashes {
        private String sha1;
        private String sha512;
    }

}
