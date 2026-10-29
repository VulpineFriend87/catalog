package top.vulpine.catalog.trash;

import top.vulpine.catalog.history.Event;
import top.vulpine.catalog.history.History;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.install.InstallException;
import top.vulpine.catalog.platform.ExitTasks;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.tracking.TrackingException;
import top.vulpine.catalog.tracking.TrackingStore;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.tracking.model.TrackingDefaults;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Uninstalls and restores plugins.
 */
public final class Removals {

    private final Platform platform;
    private final TrashBin trash;
    private final TrackingStore tracking;
    private final Supplier<TrackingDefaults> defaults;
    private final Instant startedAt;
    private final History history;

    /**
     * Deletes the jars that were locked when they were removed.
     *
     * <p>Not {@link java.io.File#deleteOnExit()}. A removal can be undone and {@code deleteOnExit}
     * cannot be called off.</p>
     */
    private final ExitTasks exitTasks;

    public Removals(Platform platform, TrashBin trash, TrackingStore tracking,
                    Supplier<TrackingDefaults> defaults, Instant startedAt, History history,
                    ExitTasks exitTasks) {
        this.platform = platform;
        this.trash = trash;
        this.tracking = tracking;
        this.defaults = defaults;
        this.startedAt = startedAt;
        this.history = history;
        this.exitTasks = exitTasks;
    }

    /**
     * Moves a plugin's jar to the trash and stops tracking it.
     *
     * @param plugin the plugin to remove
     * @param by     who asked
     * @return what was binned, or null if there was no file to bin
     */
    public TrashBin.Result uninstall(TrackedPlugin plugin, String by) throws TrackingException {

        Path jar = platform.pluginsFolder().resolve(plugin.fileName());
        TrashBin.Result result = null;

        if (Files.isRegularFile(jar)) {

            result = trash.bin(jar, plugin, by);

            if (!result.deleted()) {
                exitTasks.delete(jar);
            }
        }

        tracking.remove(plugin.projectId());
        tracking.save();

        history.add(HistoryEntry.trashed(plugin, by));
        return result;
    }

    /**
     * Cancels the build staged for a removed plugin.
     *
     * @param plugin the plugin being removed
     * @return true if nothing is left staged
     */
    public boolean cancelStagedFor(TrackedPlugin plugin) {
        return platform.cancelStaged(stagedName(plugin));
    }

    /**
     * Everything currently in the trash. Newest first.
     */
    public List<TrashEntry> list() {
        return trash.list();
    }

    /**
     * One removal by the name it is filed under.
     *
     * @param storedAs the id carried by an undo button
     * @return the entry, or null if it has already been restored or pruned
     */
    public TrashEntry find(String storedAs) {
        return trash.find(storedAs);
    }

    /**
     * Restores a removed plugin and tracks it again.
     *
     * @param entry what to restore
     * @param by    who asked
     * @return the plugin Catalog is tracking again, or null if it was never tracked
     * @throws InstallException if the jar is gone, or something already occupies its file name
     */
    public TrackedPlugin restore(TrashEntry entry, String by) throws TrackingException {

        if (entry.projectId() != null && tracking.byProjectId(entry.projectId()) != null) {
            throw new InstallException(entry.displayName() + " is already installed.");
        }

        Path target = platform.pluginsFolder().resolve(entry.fileName());

        // A locked jar was never moved. Undoing its removal calls the deletion off.
        if (exitTasks.withdraw(target)) {
            trash.discard(entry);
        } else {
            trash.restore(entry, target);
        }

        return track(entry, by);
    }

    /**
     * Rebuilds the tracking record a restored plugin had from what was saved at removal.
     *
     * <p>Whether a restart is needed depends on when the removal happened.</p>
     */
    private TrackedPlugin track(TrashEntry entry, String by) throws TrackingException {

        if (entry.projectId() == null) {
            return null;
        }

        TrackedPlugin tracked = entry.plugin() != null ? asRemoved(entry) : rebuilt(entry, by);

        tracked.pendingLoad(!removedWhileRunning(entry.removedAt()));

        tracking.put(tracked);
        tracking.save();

        history.add(HistoryEntry.restored(tracked, by));
        return tracked;
    }

    /**
     * The record as it was removed without its pending restart.
     */
    private static TrackedPlugin asRemoved(TrashEntry entry) {

        TrackedPlugin tracked = entry.plugin();

        tracked.fileName(entry.fileName());
        tracked.pendingRenameTo(null);
        tracked.pendingRestart(false);
        tracked.stagedAs(null);
        tracked.stagedVersionId(null);
        tracked.stagedVersionNumber(null);
        tracked.stagedPublished(null);
        tracked.stagedBy(null);

        return tracked;
    }

    /**
     * A record rebuilt from a removal made before the whole record was kept.
     *
     * <p>Missing settings fall back to the defaults.</p>
     */
    private TrackedPlugin rebuilt(TrashEntry entry, String by) {

        TrackingDefaults configured = defaults.get();
        TrackedPlugin tracked = new TrackedPlugin();

        tracked.projectId(entry.projectId());
        tracked.slug(entry.slug());
        tracked.name(entry.name());
        tracked.versionId(entry.versionId());
        tracked.versionNumber(entry.versionNumber());
        tracked.datePublished(entry.datePublished());
        tracked.fileName(entry.fileName());
        tracked.sha512(entry.sha512());
        tracked.channel(entry.channel() == null ? configured.channel() : entry.channel());
        tracked.autoUpdate(configured.autoUpdate());
        tracked.installedBy(by);
        tracked.installedAt(Instant.now());

        return tracked;
    }

    /**
     * Whether a build that has just been written to the plugins folder is already loaded.
     *
     * @param sha512 the hash of the build being installed
     * @return true if a removal from this session took the same build away
     */
    public boolean stillRunning(String sha512) {

        if (sha512 == null) {
            return false;
        }

        for (TrashEntry entry : trash.list()) {

            if (sha512.equalsIgnoreCase(entry.sha512()) && removedWhileRunning(entry.removedAt())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether a removal happened while this server has been up. The plugin it took away is then
     * still loaded.
     */
    private boolean removedWhileRunning(Instant removedAt) {
        return removedAt != null && !removedAt.isBefore(startedAt);
    }

    /**
     * Claims a jar left in place because this server had it locked.
     *
     * @param jar the file being written over
     * @return true if the pending deletion was called off
     */
    public boolean claimPendingDelete(Path jar) {
        return exitTasks.withdraw(jar);
    }

    /**
     * Deletes one removal permanently.
     *
     * @param entry what to delete
     * @param by    who asked
     */
    public void discard(TrashEntry entry, String by) {
        trash.discard(entry);
        history.add(HistoryEntry.deleted(entry, by));
    }

    /**
     * Deletes every removal permanently.
     *
     * @param by who asked
     * @return how many were deleted
     */
    public int empty(String by) {

        List<String> names = new ArrayList<>();

        for (TrashEntry entry : trash.list()) {
            names.add(entry.displayName());
        }

        int gone = trash.empty();

        if (gone > 0) {
            history.add(HistoryEntry.many(Event.TRASH_EMPTIED, gone, names, by));
        }

        return gone;
    }

    /**
     * Drops removals older than the given window.
     *
     * @param days how long a removal is kept, or zero to keep them forever
     * @return how many were dropped
     */
    public int prune(int days) {
        return days <= 0 ? 0 : trash.prune(Duration.ofDays(days), Instant.now());
    }


    /**
     * Where a staged build is sitting.
     *
     * <p>Falls back to the installed name for records from before builds were staged under their
     * published name.</p>
     *
     * @param plugin the plugin to look up
     * @return the file name a staged build would have
     */
    public static String stagedName(TrackedPlugin plugin) {
        return plugin.stagedAs() != null ? plugin.stagedAs() : plugin.fileName();
    }

}
