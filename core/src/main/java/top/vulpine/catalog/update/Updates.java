package top.vulpine.catalog.update;

import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.Errors;
import top.vulpine.catalog.history.History;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.install.Installer;
import top.vulpine.catalog.modrinth.ModrinthClient;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.tracking.TrackingException;
import top.vulpine.catalog.tracking.TrackingStore;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.Removals;
import top.vulpine.catalog.update.model.ServerTarget;
import top.vulpine.catalog.update.model.UpdateCandidate;
import top.vulpine.commons.log.Logger;

import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/**
 * What is out of date and what to install automatically.
 *
 * <p>Every method blocks, so none may be called on the server main thread.</p>
 */
public final class Updates {

    private final Platform platform;
    private final ModrinthClient modrinth;
    private final TrackingStore tracking;
    private final Installer installer;
    private final IntSupplier defaultSoakMinutes;
    private final Function<ModrinthVersion, DependencyResolver.Resolution> dependencies;
    private final UnaryOperator<List<DependencyResolver.Requirement>> named;
    private final History history;

    /** Rescans the plugins folder and settles the tracking file against it. */
    private final BooleanSupplier reconcile;

    private volatile List<UpdateCandidate> lastCheck = List.of();
    private volatile Instant checkedAt;

    /**
     * By project id: why the last automatic update did not happen.
     */
    private final Map<String, String> failed = new ConcurrentHashMap<>();

    /**
     * Why the last check could not reach Modrinth, or null when it did.
     */
    private volatile String unreachable;

    /**
     * By version id: why each build was held back.
     */
    private final Map<String, String> missingByVersion = new ConcurrentHashMap<>();

    /**
     * Version ids whose failed download is already in the history.
     */
    private final Set<String> failuresReported = ConcurrentHashMap.newKeySet();

    public Updates(Platform platform, ModrinthClient modrinth, TrackingStore tracking,
                   Installer installer, IntSupplier defaultSoakMinutes,
                   Function<ModrinthVersion, DependencyResolver.Resolution> dependencies,
                   UnaryOperator<List<DependencyResolver.Requirement>> named,
                   History history, BooleanSupplier reconcile) {
        this.platform = platform;
        this.modrinth = modrinth;
        this.tracking = tracking;
        this.installer = installer;
        this.defaultSoakMinutes = defaultSoakMinutes;
        this.dependencies = dependencies;
        this.named = named;
        this.history = history;
        this.reconcile = reconcile;
    }

    /**
     * The required dependencies a build needs that this server does not have.
     *
     * @param version the build being considered
     * @return what is missing, or an empty list
     */
    public List<DependencyResolver.Requirement> missingFor(ModrinthVersion version) {

        try {
            return dependencies.apply(version).missing();
        } catch (Exception e) {
            // An answer nobody can get is not evidence of a missing dependency.
            Logger.debug(CatalogAction.UPDATE, "Could not resolve dependencies for "
                    + version.versionNumber() + ": " + Errors.rootMessage(e));
            return List.of();
        }
    }

    /**
     * @return the reason each automatic update failed by project id
     */
    public Map<String, String> failures() {
        return Map.copyOf(failed);
    }

    /**
     * @return why the last check could not reach Modrinth, or null when it did
     */
    public String unreachable() {
        return unreachable;
    }

    /**
     * @return when the last check ran, or null if none has
     */
    public Instant checkedAt() {
        return checkedAt;
    }

    /**
     * Asks Modrinth again what is out of date.
     *
     * @return the available updates
     */
    public List<UpdateCandidate> refresh() throws TrackingException {

        noticeStagedApplied();

        ServerTarget target = platform.target();
        Logger.debug(CatalogAction.UPDATE, "Checking against " + target + ", asking for loaders "
                + String.join(", ", target.loaders())
                + (target.gameVersions() == null ? ", any game version."
                        : " and game versions " + String.join(", ", target.gameVersions()) + "."));

        for (TrackedPlugin tracked : tracking.all()) {
            Logger.debug(CatalogAction.UPDATE, "  asking about " + state(tracked));
        }

        try {
            lastCheck = new UpdateChecker(modrinth, tracking).check(target);
        } catch (RuntimeException e) {
            unreachable = Errors.rootMessage(e);
            throw e;
        }

        unreachable = null;
        checkedAt = Instant.now();

        Set<String> offered = new HashSet<>();

        for (UpdateCandidate candidate : lastCheck) {

            offered.add(candidate.plugin().projectId());

            Logger.debug(CatalogAction.UPDATE, "  Modrinth offers " + candidate.plugin().displayName()
                    + " " + candidate.from() + " -> " + candidate.to()
                    + (candidate.plugin().pendingLoad()
                            ? "; hidden from the list and skipped by auto-update until it loads"
                            : ""));
        }

        for (TrackedPlugin tracked : tracking.all()) {

            if (!offered.contains(tracked.projectId())) {
                Logger.debug(CatalogAction.UPDATE, "  no newer build offered for "
                        + tracked.displayName() + (tracked.isPinned() ? ", which is held" : ""));
            }
        }

        return lastCheck;
    }

    /**
     * Checks and installs whatever the policy allows.
     */
    public void check() {

        if (tracking.size() == 0) {
            return;
        }

        List<UpdateCandidate> candidates;

        try {
            candidates = refresh();
        } catch (Exception e) {
            Logger.warn(CatalogAction.UPDATE, "Could not check for updates: " + Errors.rootMessage(e));
            return;
        }

        Logger.debug(CatalogAction.UPDATE, candidates.size() + " update"
                + (candidates.size() == 1 ? "" : "s") + " available.");

        applyAutomatic(candidates);
    }

    /**
     * Runs auto updates.
     */
    private void applyAutomatic(List<UpdateCandidate> candidates) {

        AutoUpdatePolicy policy = new AutoUpdatePolicy(defaultSoakMinutes.getAsInt());
        Instant now = Instant.now();
        List<UpdateCandidate> ready = policy.readyToApply(candidates, now);

        for (UpdateCandidate candidate : candidates) {

            if (ready.contains(candidate)) {
                continue;
            }

            TrackedPlugin waiting = candidate.plugin();
            failed.remove(waiting.projectId());

            Logger.debug(CatalogAction.UPDATE, "Not updating " + waiting.displayName()
                    + " on its own: " + (!waiting.autoUpdate() ? "auto-update is off"
                            : waiting.isPinned() ? "it is held"
                            : waiting.pendingLoad() ? "it has not loaded yet"
                            : policy.soaking(candidate, now)
                                    ? "the build is still soaking, " + policy.soakMinutes(waiting)
                                            + " minutes from " + candidate.version().datePublished()
                            : "the policy declined it"));
        }

        for (UpdateCandidate candidate : ready) {

            List<DependencyResolver.Requirement> missing = missingFor(candidate.version());

            if (!missing.isEmpty()) {
                hold(candidate, missing);
                continue;
            }

            try {

                String replacing = candidate.plugin().pendingRestart()
                        ? candidate.plugin().stagedVersionNumber() : null;

                installer.stage(candidate, null);
                failed.remove(candidate.plugin().projectId());

                Logger.info(CatalogAction.UPDATE, "Updated " + candidate.plugin().displayName()
                        + " " + candidate.from() + " -> " + candidate.to()
                        + (replacing != null ? ", replacing " + replacing + ". Applies"
                                : ", applies") + " on the next restart.");

            } catch (Exception e) {

                String reason = Errors.rootMessage(e);
                failed.put(candidate.plugin().projectId(), reason);

                Logger.warn(CatalogAction.UPDATE, "Could not update "
                        + candidate.plugin().displayName() + ": " + reason);

                if (failuresReported.add(candidate.version().id())) {
                    history.add(HistoryEntry.autoUpdateFailed(candidate.plugin(),
                            candidate.version(), reason));
                }
            }
        }
    }

    /**
     * Holds back an update whose build needs a plugin this server does not have.
     *
     * <p>The update stays on offer in {@code /catalog list}.</p>
     */
    private void hold(UpdateCandidate candidate, List<DependencyResolver.Requirement> missing) {

        String versionId = candidate.version().id();
        String reason = missingByVersion.get(versionId);

        if (reason != null) {
            failed.put(candidate.plugin().projectId(), reason);
            return;
        }

        List<String> names = new ArrayList<>();

        for (DependencyResolver.Requirement requirement : named.apply(missing)) {
            names.add(requirement.name());
        }

        reason = String.join(", ", names) + " required missing";

        missingByVersion.put(versionId, reason);
        failed.put(candidate.plugin().projectId(), reason);

        history.add(HistoryEntry.heldBack(candidate.plugin(), candidate.version(),
                missing.size(), names));

        Logger.warn(CatalogAction.UPDATE, "Not updating " + candidate.plugin().displayName()
                + " to " + candidate.to() + " automatically: it needs " + missing.size()
                + " plugin" + (missing.size() == 1 ? "" : "s")
                + " that are not installed. Use /catalog update "
                + candidate.plugin().displayName() + " to install them.");
    }

    /**
     * The updates from the last check that still apply to the installed plugins.
     *
     * <p>Fresh installs not loaded yet are left out.</p>
     *
     * @return the open update candidates
     */
    public List<UpdateCandidate> open() {

        List<UpdateCandidate> out = new ArrayList<>();

        for (UpdateCandidate candidate : lastCheck) {

            // Staged, removed or switched since the check.
            TrackedPlugin current = tracking.byProjectId(candidate.plugin().projectId());

            if (current == null || current.pendingLoad()
                    || !UpdateChecker.isNewer(current, candidate.version())) {
                continue;
            }

            out.add(UpdateCandidate.builder()
                    .plugin(current)
                    .version(candidate.version())
                    .declaresPlatform(candidate.declaresPlatform())
                    .build());
        }

        return out;
    }

    /**
     * The open updates keyed by project id.
     *
     * @return project id to candidate
     */
    public Map<String, UpdateCandidate> byProject() {

        Map<String, UpdateCandidate> byProject = new HashMap<>();

        for (UpdateCandidate candidate : open()) {
            byProject.put(candidate.plugin().projectId(), candidate);
        }

        return byProject;
    }

    /**
     * Notices a staged build that something else already applied.
     *
     * <p>Paper takes from the update folder in {@code FileProviderSource#checkUpdate} for every
     * plugin file it loads. A reload tool loading one plugin applies its staged build at once.</p>
     */
    private void noticeStagedApplied() throws TrackingException {

        if (stagedAndWaiting().isEmpty()) {
            return;
        }

        // The scan matches on contents and project id. Applying a build and replacing one by hand
        // both rename the jar.
        reconcile.getAsBoolean();

        List<TrackedPlugin> abandoned = stagedAndWaiting();

        if (abandoned.isEmpty()) {
            return;
        }

        // Anything the scan could not account for is gone.
        for (TrackedPlugin plugin : abandoned) {
            history.add(HistoryEntry.lost(plugin));
            plugin.pendingRestart(false);
            plugin.stagedAs(null);
            plugin.stagedVersionId(null);
            plugin.stagedVersionNumber(null);
            plugin.stagedPublished(null);
            plugin.stagedBy(null);
            Logger.warn(CatalogAction.UPDATE, "The build downloaded for " + plugin.displayName()
                    + " is gone from the update folder and was never applied.");
        }

        tracking.save();
    }

    /**
     * The plugins waiting for a restart whose staged build is no longer in the update folder.
     *
     * @return the plugins whose staged build something else applied, or an empty list
     */
    private List<TrackedPlugin> stagedAndWaiting() {

        List<TrackedPlugin> taken = new ArrayList<>();

        for (TrackedPlugin plugin : tracking.pendingRestart()) {
            if (!platform.isStaged(Removals.stagedName(plugin))) {
                taken.add(plugin);
            }
        }

        return taken;
    }

    /**
     * One line with what decides whether a tracked plugin can be updated.
     */
    private static String state(TrackedPlugin tracked) {

        String hash = tracked.sha512();

        return tracked.displayName()
                + " project=" + tracked.projectId()
                + " version=" + tracked.versionNumber() + " (" + tracked.versionId() + ")"
                + " published=" + tracked.datePublished()
                + " channel=" + tracked.channel().apiName()
                + " sha512=" + (hash == null ? "none" : hash.substring(0, Math.min(12, hash.length())))
                + " auto=" + tracked.autoUpdate()
                + " soak=" + tracked.soakMinutes()
                + " held=" + tracked.isPinned()
                + " pendingRestart=" + tracked.pendingRestart()
                + " pendingLoad=" + tracked.pendingLoad();
    }

}
