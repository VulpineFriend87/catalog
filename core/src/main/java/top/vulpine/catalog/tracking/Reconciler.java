package top.vulpine.catalog.tracking;

import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.jar.model.ScanResult;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.tracking.model.ReconcileReport;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.tracking.model.TrackingDefaults;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles the tracking file with the plugins folder.
 *
 * <p>Free of I/O. The caller does the scan and the bulk call to Modrinth and passes both in.</p>
 */
public final class Reconciler {

    /** Recorded as the installer of plugins the scan adopts. */
    public static final String AUTO_TRACK = "auto-track";

    private final TrackingStore store;
    private final IgnoreList ignoreList;
    private final TrackingDefaults defaults;
    private final boolean adoptNewPlugins;

    public Reconciler(TrackingStore store, IgnoreList ignoreList, TrackingDefaults defaults) {
        this(store, ignoreList, defaults, true);
    }

    /**
     * @param adoptNewPlugins false to leave recognized but untracked jars alone
     */
    public Reconciler(TrackingStore store, IgnoreList ignoreList, TrackingDefaults defaults,
                      boolean adoptNewPlugins) {
        this.store = store;
        this.ignoreList = ignoreList;
        this.defaults = defaults;
        this.adoptNewPlugins = adoptNewPlugins;
    }

    /**
     * Reconciles the store against a scan in place.
     *
     * <p>The caller saves.</p>
     *
     * @param scan       what the plugins folder holds right now
     * @param identified hash to Modrinth version for every hash in the scan
     * @return what changed
     */
    public ReconcileReport reconcile(ScanResult scan, Map<String, ModrinthVersion> identified) {

        List<TrackedPlugin> adopted = new ArrayList<>();
        List<InstalledJar> unknown = new ArrayList<>();
        List<InstalledJar> ignored = new ArrayList<>();
        List<InstalledJar> conflicting = new ArrayList<>();
        List<InstalledJar> notAdopted = new ArrayList<>();

        Changes changes = new Changes();
        Set<String> claimed = new HashSet<>();
        List<TrackedPlugin> unsettled = new ArrayList<>();

        // Two rounds. Exact matches are settled for every plugin before any weaker match is tried.
        // Otherwise one plugin could claim a jar another owns byte for byte.
        for (TrackedPlugin tracked : store.all()) {

            TrackedPlugin left = settle(tracked, scan, identified, claimed, changes);

            if (left != null) {
                unsettled.add(left);
            }
        }

        for (TrackedPlugin left : unsettled) {
            settleByProject(left, scan, identified, claimed, changes);
        }

        for (InstalledJar jar : scan.jars()) {

            if (claimed.contains(jar.fileName())) {
                continue;
            }

            ModrinthVersion version = identified.get(jar.sha512());
            String projectId = version == null ? null : version.projectId();

            if (ignoreList.contains(projectId, jar.sha512())) {
                ignored.add(jar);
                continue;
            }

            if (version == null) {
                unknown.add(jar);
                continue;
            }

            if (store.byProjectId(projectId) != null) {
                conflicting.add(jar);
                continue;
            }

            if (!adoptNewPlugins) {
                notAdopted.add(jar);
                continue;
            }

            adopted.add(adopt(version, jar));
        }

        return ReconcileReport.builder()
                .adopted(adopted)
                .moved(changes.moved)
                .applied(changes.applied)
                .loaded(changes.loaded)
                .notApplied(changes.notApplied)
                .renamed(changes.renamed)
                .removed(changes.removed)
                .completed(changes.completed)
                .unknown(unknown)
                .ignored(ignored)
                .conflicting(conflicting)
                .notAdopted(notAdopted)
                .build();
    }

    /** What became of the plugins already tracked. */
    private static final class Changes {

        private final List<TrackedPlugin> moved = new ArrayList<>();
        private final List<TrackedPlugin> applied = new ArrayList<>();
        private final List<TrackedPlugin> loaded = new ArrayList<>();
        private final List<TrackedPlugin> notApplied = new ArrayList<>();
        private final List<TrackedPlugin> renamed = new ArrayList<>();
        private final List<TrackedPlugin> removed = new ArrayList<>();
        private final List<TrackedPlugin> completed = new ArrayList<>();

    }

    /**
     * Works out what became of one tracked plugin.
     *
     * <p>Matched by hash first and only then by file name.</p>
     */
    private TrackedPlugin settle(TrackedPlugin tracked, ScanResult scan,
                             Map<String, ModrinthVersion> identified, Set<String> claimed,
                             Changes changes) {

        InstalledJar sameContent = tracked.sha512() == null ? null : scan.byHash(tracked.sha512());

        if (sameContent != null) {

            if (!sameContent.fileName().equals(tracked.fileName())) {
                tracked.fileName(sameContent.fileName());
                changes.renamed.add(tracked);
            }

            // The same bytes as before the restart that should have replaced them. The staged
            // build is still waiting. The flag stays set and the update is reported as not applied.
            if (tracked.pendingRestart()) {
                changes.notApplied.add(tracked);
            }

            // A fresh install's jar was always the right one.
            if (tracked.pendingLoad()) {
                changes.loaded.add(tracked);
            }
            tracked.pendingLoad(false);

            complete(tracked, identified.get(sameContent.sha512()), changes);

            claimed.add(sameContent.fileName());
            return null;
        }

        InstalledJar sameName = tracked.fileName() == null ? null : scan.byFileName(tracked.fileName());

        if (sameName == null) {
            return tracked;
        }

        ModrinthVersion replacement = identified.get(sameName.sha512());

        if (replacement != null && tracked.projectId().equals(replacement.projectId())) {

            // A staged update becoming the jar on disk is the same event as somebody swapping it by hand.
            boolean wasStaged = tracked.pendingRestart();

            tracked.moveTo(replacement, sameName.fileName(), sameName.sha512());
            tracked.pendingRestart(false);
            tracked.stagedAs(null);
            tracked.pendingLoad(false);

            claimed.add(sameName.fileName());
            (wasStaged ? changes.applied : changes.moved).add(tracked);
            return null;
        }

        // The file now holds something else. The file name is left to the jar now there.
        // The plugin goes to the second round in case it is on disk under another name.
        return tracked;
    }

    /**
     * Last resort: finds the plugin by its Modrinth project id.
     *
     * <p>Reached when both the contents and the file name changed. Only jars nobody else claimed are
     * considered. If two are left over for the same project, the first is taken and the other is
     * reported as a duplicate.</p>
     */
    private void settleByProject(TrackedPlugin tracked, ScanResult scan,
                                 Map<String, ModrinthVersion> identified, Set<String> claimed,
                                 Changes changes) {

        for (InstalledJar jar : scan.jars()) {

            if (claimed.contains(jar.fileName())) {
                continue;
            }

            ModrinthVersion version = identified.get(jar.sha512());

            if (version == null || !tracked.projectId().equals(version.projectId())) {
                continue;
            }

            boolean wasStaged = tracked.pendingRestart();

            tracked.moveTo(version, jar.fileName(), jar.sha512());
            tracked.pendingRestart(false);
            tracked.stagedAs(null);
            tracked.pendingLoad(false);

            claimed.add(jar.fileName());
            (wasStaged ? changes.applied : changes.moved).add(tracked);
            return;
        }

        store.remove(tracked.projectId());
        changes.removed.add(tracked);
    }

    /**
     * Fills in the publish date a record is missing.
     */
    private static void complete(TrackedPlugin tracked, ModrinthVersion known, Changes changes) {

        if (known == null || !known.id().equals(tracked.versionId())
                || tracked.datePublished() != null || known.datePublished() == null) {
            return;
        }

        tracked.datePublished(known.datePublished());
        changes.completed.add(tracked);
    }

    private TrackedPlugin adopt(ModrinthVersion version, InstalledJar jar) {

        TrackedPlugin plugin = TrackedPlugin.of(version, jar.fileName(), jar.sha512(),
                defaults.channel(), AUTO_TRACK);

        plugin.autoUpdate(defaults.autoUpdate());
        plugin.soakMinutes(defaults.soakMinutes());

        store.put(plugin);

        return plugin;
    }

}
