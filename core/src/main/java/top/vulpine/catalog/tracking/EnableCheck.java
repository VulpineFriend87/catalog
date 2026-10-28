package top.vulpine.catalog.tracking;

import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.history.History;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.modrinth.ModrinthClient;
import top.vulpine.catalog.modrinth.ModrinthException;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.commons.log.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks, once the server has started, that every plugin this start updated or installed is enabled.
 *
 * <p>Runs when both the server has finished starting and Catalog's first scan has finished, in
 * whichever order they arrive.</p>
 */
public final class EnableCheck {

    private final Platform platform;

    private final Library library;

    private final History history;

    private final ModrinthClient modrinth;

    private boolean started;
    private boolean scanned;
    private boolean done;

    private volatile List<Failure> failures = List.of();

    public EnableCheck(Platform platform, Library library, History history, ModrinthClient modrinth) {
        this.platform = platform;
        this.library = library;
        this.history = history;
        this.modrinth = modrinth;
    }

    /**
     * A plugin the server did not enable.
     *
     * @param updated         false for a first install
     * @param reason          what the server reported
     * @param previousRemoved true when the build a rollback would return to is gone from Modrinth
     */
    public record Failure(TrackedPlugin plugin, boolean updated, String reason, boolean previousRemoved) {

        /**
         * @return whether a rollback can be offered at all
         */
        public boolean canRollBack() {
            return updated && plugin.canRollBack();
        }
    }

    /**
     * @return the plugins this start did not enable that are still not enabled
     */
    public List<Failure> failures() {

        List<Failure> still = new ArrayList<>();

        for (Failure failure : failures) {
            if (!platform.isEnabled(platform.pluginsFolder().resolve(failure.plugin().fileName()))) {
                still.add(failure);
            }
        }

        if (still.size() != failures.size()) {
            failures = List.copyOf(still);
        }

        return failures;
    }

    /**
     * Called once the server has finished starting.
     */
    public void started() {

        synchronized (this) {
            started = true;
        }

        runWhenReady();
    }

    /**
     * Called once Catalog's first scan has finished.
     */
    public void scanned() {

        synchronized (this) {
            scanned = true;
        }

        runWhenReady();
    }

    private void runWhenReady() {

        synchronized (this) {
            if (!started || !scanned || done) {
                return;
            }
            done = true;
        }

        platform.async(this::check);
    }

    private void check() {

        List<Failure> found = new ArrayList<>();

        for (TrackedPlugin tracked : library.applied()) {
            check(tracked, true, found);
        }

        for (TrackedPlugin tracked : library.loaded()) {
            check(tracked, false, found);
        }

        failures = List.copyOf(found);
    }

    private void check(TrackedPlugin tracked, boolean updated, List<Failure> found) {

        String name = library.declaredName(tracked);

        if (name == null) {
            return;
        }

        String reason = platform.notEnabled(name);

        if (reason == null) {
            return;
        }

        Failure failure = new Failure(tracked, updated, reason,
                updated && tracked.canRollBack() && removed(tracked.previousVersionId()));

        found.add(failure);
        history.add(HistoryEntry.notEnabled(tracked, updated, reason));

        Logger.warn(CatalogAction.UPDATE, tracked.displayName() + " " + tracked.versionNumber()
                + " did not enable after the " + (updated ? "update" : "install") + "."
                + (failure.canRollBack() ? " Previous build: " + tracked.previousVersionNumber() + "."
                : ""));
    }

    /**
     * @return true only when Modrinth says the build no longer exists, never on a network error
     */
    private boolean removed(String versionId) {

        try {
            modrinth.version(versionId).join();
            return false;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return cause instanceof ModrinthException modrinth && modrinth.statusCode() == 404;
        }
    }

}
