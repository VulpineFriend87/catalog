package top.vulpine.catalog.tracking.model;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import top.vulpine.catalog.jar.model.InstalledJar;

import java.util.Collections;
import java.util.List;

/**
 * What one pass of reconciliation found.
 */
@Getter
@Builder
@Accessors(fluent = true)
public final class ReconcileReport {

    /** Recognized on Modrinth and now tracked. */
    @Builder.Default
    private final List<TrackedPlugin> adopted = Collections.emptyList();

    /** The jar was replaced by hand with another version of the same project. */
    @Builder.Default
    private final List<TrackedPlugin> moved = Collections.emptyList();

    /** An update Catalog staged is now the jar on disk. */
    @Builder.Default
    private final List<TrackedPlugin> applied = Collections.emptyList();

    /** Installed by Catalog and loaded by the server for the first time on this start. */
    @Builder.Default
    private final List<TrackedPlugin> loaded = Collections.emptyList();

    /**
     * An update Catalog staged is still not the jar on disk after a restart.
     *
     * <p>The server did not take the file from the update folder.</p>
     */
    @Builder.Default
    private final List<TrackedPlugin> notApplied = Collections.emptyList();

    /** Same file contents under a new name. */
    @Builder.Default
    private final List<TrackedPlugin> renamed = Collections.emptyList();

    /** Given back a missing publish date. */
    @Builder.Default
    private final List<TrackedPlugin> completed = Collections.emptyList();

    /** No longer installed. Tracking stopped. */
    @Builder.Default
    private final List<TrackedPlugin> removed = Collections.emptyList();

    /** Not on Modrinth. Listed and never touched. */
    @Builder.Default
    private final List<InstalledJar> unknown = Collections.emptyList();

    /** On the ignore list. */
    @Builder.Default
    private final List<InstalledJar> ignored = Collections.emptyList();

    /**
     * A second jar for a project that is already tracked.
     */
    @Builder.Default
    private final List<InstalledJar> conflicting = Collections.emptyList();

    /**
     * Recognized on Modrinth but left untracked because auto-tracking is off.
     */
    @Builder.Default
    private final List<InstalledJar> notAdopted = Collections.emptyList();

    /**
     * Whether anything changed.
     *
     * @return true if tracking state was modified
     */
    public boolean hasChanges() {
        return !adopted.isEmpty() || !moved.isEmpty() || !renamed.isEmpty()
                || !removed.isEmpty() || !applied.isEmpty() || !completed.isEmpty();
    }

    /**
     * Whether anything happened that someone should look at.
     *
     * @return true if there is something worth a warning
     */
    public boolean needsAttention() {
        return !conflicting.isEmpty() || !notApplied.isEmpty();
    }

}
