package top.vulpine.catalog.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.modrinth.ModrinthException;
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
public final class EnableCheck implements Listener {

    private final CatalogPaper paper;

    private final Catalog catalog;

    private boolean started;
    private boolean scanned;
    private boolean done;

    private volatile List<Failure> failures = List.of();

    public EnableCheck(CatalogPaper paper, Catalog catalog) {
        this.paper = paper;
        this.catalog = catalog;
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
            if (!catalog.getPlatform().isEnabled(catalog.getPlatform().pluginsFolder().resolve(failure.plugin().fileName()))) {
                still.add(failure);
            }
        }

        if (still.size() != failures.size()) {
            failures = List.copyOf(still);
        }

        return failures;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {

        if (event.getType() != ServerLoadEvent.LoadType.STARTUP) {
            return;
        }

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

        catalog.getPlatform().async(this::check);
    }

    private void check() {

        List<Failure> found = new ArrayList<>();

        for (TrackedPlugin tracked : catalog.getLibrary().applied()) {
            check(tracked, true, found);
        }

        for (TrackedPlugin tracked : catalog.getLibrary().loaded()) {
            check(tracked, false, found);
        }

        failures = List.copyOf(found);
    }

    private void check(TrackedPlugin tracked, boolean updated, List<Failure> found) {

        String name = catalog.getLibrary().declaredName(tracked);

        if (name == null) {
            return;
        }

        Plugin running = paper.getServer().getPluginManager().getPlugin(name);
        String reason = running == null ? "not loaded"
                : !running.isEnabled() ? "disabled during startup" : null;

        if (reason == null) {
            return;
        }

        Failure failure = new Failure(tracked, updated, reason,
                updated && tracked.canRollBack() && removed(tracked.previousVersionId()));

        found.add(failure);
        catalog.getHistory().add(HistoryEntry.notEnabled(tracked, updated, reason));

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
            catalog.getModrinth().version(versionId).join();
            return false;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return cause instanceof ModrinthException modrinth && modrinth.statusCode() == 404;
        }
    }

}
