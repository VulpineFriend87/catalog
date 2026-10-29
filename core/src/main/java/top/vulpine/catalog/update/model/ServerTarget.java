package top.vulpine.catalog.update.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The platform, Minecraft version and Java version of this server.
 *
 * <p>Built by the platform module.</p>
 */
@Getter
@Builder
@Accessors(fluent = true)
public final class ServerTarget {

    @NonNull
    private final ServerPlatform platform;

    /**
     * The exact Minecraft version, or null on a proxy.
     */
    private final String gameVersion;

    /** The Java feature version this server runs on. */
    private final int javaVersion;

    /**
     * The loaders to ask Modrinth for.
     *
     * @return this platform and everything it is a superset of
     */
    public List<String> loaders() {
        return platform.loaders();
    }

    /**
     * The game versions to accept: this one only.
     *
     * @return the acceptable versions, or null to accept every version
     */
    public List<String> gameVersions() {
        return gameVersion == null ? null : List.of(gameVersion);
    }

    @Override
    public String toString() {
        return platform.id() + (gameVersion == null ? "" : " " + gameVersion) + " on Java "
                + javaVersion;
    }

}
