package top.vulpine.catalog.tracking.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tracked plugin.
 *
 * <p>Serialized to {@code tracked.json}.</p>
 */
@Getter
@Setter
@Accessors(fluent = true)
public final class TrackedPlugin {

    public static final int INHERIT_SOAK = -1;

    private String projectId;
    private String slug;
    private String name;

    private String versionId;
    private String versionNumber;
    private Instant datePublished;

    /** The build the last update replaced. A rollback returns to it. */
    private String previousVersionId;
    private String previousVersionNumber;
    private Instant previousPublished;

    /** The file in {@code plugins/} as it is named right now. */
    private String fileName;

    /** The canonical name still to be applied after a rename that waits for shutdown. */
    private String pendingRenameTo;

    /** Set when an update is staged. Cleared once the restart that applied it is verified. */
    private boolean pendingRestart;

    /**
     * What the waiting staged build is called inside the update folder.
     */
    private String stagedAs;

    /**
     * Which build is staged.
     */
    private String stagedVersionId;
    private String stagedVersionNumber;

    /** When the staged build was published. A newer build is compared against it. */
    private Instant stagedPublished;

    /**
     * Who queued the staged build. Absent means Catalog did.
     */
    private String stagedBy;

    /**
     * Set when Catalog wrote this jar into the plugins folder. Cleared by the first startup that
     * sees it.
     */
    private boolean pendingLoad;

    private String sha512;

    private ReleaseChannel channel = ReleaseChannel.RELEASE;

    private boolean autoUpdate;

    private int soakMinutes = INHERIT_SOAK;

    /** Holds this plugin at that version when set. Manual updates are refused too. */
    private String pinnedVersionId;

    /** File name to destination directory for versions that ship more than the plugin jar. */
    private Map<String, String> extraFiles = new LinkedHashMap<>();

    private String installedBy;
    private Instant installedAt;

    /**
     * False when this was installed for another plugin's requirement.
     */
    private boolean explicit = true;

    /**
     * Starts tracking a version that has just been identified or installed.
     *
     * @param version   the version the file on disk turned out to be
     * @param fileName  the name of that file in the plugins folder
     * @param sha512    its hash
     * @param channel   the channel this plugin should follow from now on
     * @param installedBy who to record in the history
     * @return a new record with the defaults applied
     */
    public static TrackedPlugin of(ModrinthVersion version, String fileName, String sha512,
                                   ReleaseChannel channel, String installedBy) {

        TrackedPlugin tracked = new TrackedPlugin();

        tracked.projectId = version.projectId();
        tracked.versionId = version.id();
        tracked.versionNumber = version.versionNumber();
        tracked.datePublished = version.datePublished();
        tracked.fileName = fileName;
        tracked.sha512 = sha512;
        tracked.channel = channel;
        tracked.installedBy = installedBy;
        tracked.installedAt = Instant.now();

        return tracked;
    }

    /**
     * Records that this plugin is now a different version of the same project.
     *
     * @param version the version now on disk
     * @param fileName the file it lives in
     * @param sha512  its hash
     */
    public void moveTo(ModrinthVersion version, String fileName, String sha512) {

        if (versionId != null && !versionId.equals(version.id())) {
            this.previousVersionId = versionId;
            this.previousVersionNumber = versionNumber;
            this.previousPublished = datePublished;
        }

        this.versionId = version.id();
        this.versionNumber = version.versionNumber();
        this.datePublished = version.datePublished();
        this.fileName = fileName;
        this.sha512 = sha512;
    }

    /**
     * Whether there is a previous build older than the installed one to roll back to.
     */
    public boolean canRollBack() {
        return previousVersionId != null && (previousPublished == null || datePublished == null
                || previousPublished.isBefore(datePublished));
    }

    /**
     * Whether anything about this plugin is waiting for the server to restart.
     *
     * @return true if a build is staged or a fresh install has not been loaded yet
     */
    public boolean awaitingRestart() {
        return pendingRestart || pendingLoad;
    }

    /**
     * Whether this plugin is held at one version.
     *
     * @return true if a pin is set
     */
    public boolean isPinned() {
        return pinnedVersionId != null;
    }

    /**
     * Holds this plugin at the installed version.
     */
    public void pinToCurrent() {
        this.pinnedVersionId = versionId;
    }

    /**
     * The display name for messages.
     *
     * @return the name, else the slug, else the project id
     */
    public String displayName() {

        if (name != null && !name.isBlank()) {
            return name;
        }

        if (slug != null && !slug.isBlank()) {
            return slug;
        }

        return projectId;
    }

    @Override
    public String toString() {
        return "TrackedPlugin(" + displayName() + " " + versionNumber + ", " + channel + ")";
    }

}
