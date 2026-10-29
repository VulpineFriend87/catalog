package top.vulpine.catalog.update.model;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

/**
 * A newer version of a tracked plugin that this server can run.
 *
 * <p>The game version, loader and release channel were filtered by Modrinth. What only the jar can
 * say is checked after downloading.</p>
 */
@Getter
@Builder
@Accessors(fluent = true)
public final class UpdateCandidate {

    private final TrackedPlugin plugin;

    private final ModrinthVersion version;

    /**
     * Whether the candidate declares this exact platform.
     *
     * <p>On Folia a build that does not declare it is refused.</p>
     */
    private final boolean declaresPlatform;

    /**
     * @return the version currently installed
     */
    public String from() {
        return plugin.versionNumber();
    }

    /**
     * @return the version on offer
     */
    public String to() {
        return version.versionNumber();
    }

    @Override
    public String toString() {
        return plugin.displayName() + " " + from() + " -> " + to();
    }

}
