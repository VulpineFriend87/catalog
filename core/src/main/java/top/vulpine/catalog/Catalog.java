package top.vulpine.catalog;

import lombok.Getter;
import top.vulpine.catalog.history.Event;
import top.vulpine.catalog.history.History;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.install.Downloader;
import top.vulpine.catalog.install.InstallException;
import top.vulpine.catalog.install.Installer;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.GameVersions;
import top.vulpine.catalog.modrinth.ModrinthClient;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.platform.ExitTasks;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.tracking.Dependents;
import top.vulpine.catalog.tracking.IgnoreList;
import top.vulpine.catalog.tracking.Library;
import top.vulpine.catalog.tracking.Settings;
import top.vulpine.catalog.tracking.TrackingException;
import top.vulpine.catalog.tracking.TrackingStore;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.tracking.model.TrackingDefaults;
import top.vulpine.catalog.trash.Removals;
import top.vulpine.catalog.trash.TrashBin;
import top.vulpine.catalog.trash.model.TrashEntry;
import top.vulpine.catalog.update.Updates;
import top.vulpine.catalog.update.model.UpdateCandidate;
import top.vulpine.commons.log.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Catalog's services, wired together for one platform.
 */
@Getter
public final class Catalog {

    private final Platform platform;

    private final Supplier<Configuration> configuration;

    /** When this server came up. */
    private final Instant startedAt = Instant.now();

    private ModrinthClient modrinth;
    private TrackingStore tracking;
    private IgnoreList ignored;
    private Downloader downloader;
    private TrashBin trash;
    private History history;
    private Settings settings;
    private Projects projects;
    private ExitTasks exitTasks;
    private Removals removals;
    private Installer installer;
    private Updates updates;
    private Library library;
    private Dependents dependents;

    /** Every Minecraft version Modrinth knows, loaded the first time it is needed. */
    private volatile GameVersions gameVersions;

    /**
     * @param configuration the current configuration, read again after a reload
     */
    public Catalog(Platform platform, Supplier<Configuration> configuration) {
        this.platform = platform;
        this.configuration = configuration;
    }

    /**
     * Loads the state, wires the services and starts the first scan.
     *
     * @param scanned run once the first scan has finished
     * @return false when the state files could not be read
     */
    public boolean start(Runnable scanned) {

        Path data = platform.dataFolder();

        this.modrinth = ModrinthClient.builder()
                .userAgent("VulpineFriend87/Catalog/" + platform.version() + " (vulpine.top)")
                .token(configuration.get().modrinthToken())
                .cacheDirectory(data.resolve("cache"))
                .build();

        if (!loadState(data.resolve("data"))) {
            return false;
        }

        this.downloader = new Downloader(modrinth, data.resolve("staging"));
        this.trash = new TrashBin(data.resolve("trash"));
        this.history = new History(data.resolve("data").resolve("history.json"));
        this.history.load();

        this.settings = new Settings(tracking, this::defaults, history);
        this.projects = new Projects(platform, modrinth, tracking);
        this.exitTasks = new ExitTasks(data, platform.ownJar());
        this.exitTasks.report();

        this.removals = new Removals(platform, trash, tracking, this::defaults, startedAt, history,
                exitTasks);
        this.installer = new Installer(platform, downloader, tracking, removals, this::defaults, history);
        this.updates = new Updates(platform, modrinth, tracking, installer,
                () -> configuration.get().defaults().soakMinutes(), projects::dependenciesOf,
                history, () -> library.index());
        this.library = new Library(platform, modrinth, tracking, ignored, this::defaults,
                () -> configuration.get().autoTrack(), history);
        this.dependents = new Dependents(tracking, hashes -> modrinth.identify(hashes).join());

        Runtime.getRuntime().addShutdownHook(new Thread(exitTasks::run, "Catalog exit tasks"));

        platform.async(() -> index(scanned));
        platform.async(this::pruneTrash);
        scheduleUpdateChecks();

        return true;
    }

    public void stop() {

        if (modrinth != null) {
            modrinth.close();
        }
    }

    private boolean loadState(Path data) {

        tracking = new TrackingStore(data.resolve("tracked.json"));
        ignored = new IgnoreList(data.resolve("ignored.json"));

        try {
            tracking.load();
            ignored.load();
        } catch (TrackingException e) {
            Logger.error(CatalogAction.CONFIG, e.getMessage());
            return false;
        }

        Logger.debug(CatalogAction.SETUP, "Loaded " + tracking.size() + " tracked plugins, "
                + ignored.size() + " ignore entries.");

        return true;
    }

    private void index(Runnable scanned) {

        downloader.clean();

        boolean indexed = library.index();
        scanned.run();

        if (indexed) {
            updates.check();
        }
    }

    private void scheduleUpdateChecks() {

        int minutes = configuration.get().checkIntervalMinutes();

        if (minutes <= 0) {
            Logger.debug(CatalogAction.SETUP, "Periodic update checks are off.");
            return;
        }

        platform.repeat(updates::check, Duration.ofMinutes(minutes));
        Logger.debug(CatalogAction.SETUP, "Checking for updates every " + minutes + " minutes.");
    }

    /**
     * Drops removals older than the configured window.
     */
    private void pruneTrash() {

        int days = configuration.get().trashRetentionDays();
        int dropped = removals.prune(days);

        if (dropped > 0) {
            Logger.debug(CatalogAction.SETUP, "Emptied " + dropped + " removals older than "
                    + days + " days from the trash.");
        }
    }

    private TrackingDefaults defaults() {
        return configuration.get().defaults();
    }

    // Updates

    /**
     * Asks Modrinth what is out of date and remembers the answer.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @return the available updates
     */
    public List<UpdateCandidate> refreshUpdates() {

        try {
            return updates.refresh();
        } catch (TrackingException e) {
            Logger.error(CatalogAction.TRACK, e.getMessage());
            return updates.open();
        }
    }

    public List<UpdateCandidate> openUpdates() {
        return updates.open();
    }

    public Map<String, UpdateCandidate> updatesByProject() {
        return updates.byProject();
    }

    public List<DependencyResolver.Requirement> missingFor(ModrinthVersion version) {
        return updates.missingFor(version);
    }

    // History

    /**
     * What Catalog has done, newest first.
     *
     * @param projectId only this plugin, or null for every plugin
     * @param by        only this author, or null for everyone
     * @return the matching entries
     */
    public List<HistoryEntry> history(String projectId, String by) {
        return history.filter(projectId, by);
    }

    /**
     * @param query what was typed
     * @return the project id the history knows it by, or null if it has never mentioned it
     */
    public String loggedProjectId(String query) {
        return history.projectIdFor(query);
    }

    /**
     * @return every plugin the history mentions, for tab completion
     */
    public List<String> loggedPlugins() {
        return history.plugins();
    }

    // Projects

    public SearchResults search(String query, int limit, int offset) {
        return projects.search(query, limit, offset);
    }

    public ModrinthProject project(String idOrSlug) {
        return projects.project(idOrSlug);
    }

    public ModrinthVersion installTarget(String idOrSlug) {
        return projects.installTarget(idOrSlug);
    }

    public List<ModrinthVersion> compatibleVersions(String idOrSlug) {
        return projects.compatibleVersions(idOrSlug);
    }

    public List<ModrinthVersion> allVersions(String idOrSlug) {
        return projects.allVersions(idOrSlug);
    }

    public DependencyResolver.Resolution dependenciesOf(ModrinthVersion version) {
        return projects.dependenciesOf(version);
    }

    /**
     * Blocks on Modrinth, so it must be called off the main thread.
     *
     * @return the projects a build declares of one type, named
     */
    public List<DependencyResolver.Requirement> declared(ModrinthVersion version, DependencyType type) {
        return projects.declared(version, type);
    }

    /**
     * Blocks on Modrinth, so it must be called off the main thread.
     *
     * @return the requirements under their projects' titles and slugs
     */
    public List<DependencyResolver.Requirement> named(List<DependencyResolver.Requirement> requirements) {
        return projects.named(requirements);
    }

    /**
     * Blocks on Modrinth the first time, so it must be called off the main thread.
     *
     * @return what writes Minecraft versions as ranges
     */
    public GameVersions gameVersions() {

        if (gameVersions == null) {
            try {
                gameVersions = new GameVersions(modrinth.gameVersions().join());
            } catch (Exception e) {
                // Shown as declared until Modrinth answers.
                return new GameVersions(List.of());
            }
        }

        return gameVersions;
    }

    /**
     * @return the exact Minecraft version this server runs
     */
    public String gameVersion() {
        return platform.target().gameVersion();
    }

    /**
     * @return the loader names this server can use, most specific first
     */
    public List<String> platformLoaders() {
        return platform.target().loaders();
    }

    // Installing

    public void stage(UpdateCandidate candidate, String by) {
        saving(() -> installer.stage(candidate, by));
    }

    public void stage(TrackedPlugin plugin, ModrinthVersion version, String by, Event as) {
        saving(() -> installer.stage(plugin, version, by, as));
    }

    /**
     * Drops a staged build so the next restart leaves the plugin as it is.
     *
     * @param plugin the plugin to leave alone
     * @return false when the staged file could not be deleted
     */
    public boolean cancelUpdate(TrackedPlugin plugin, String by) {

        try {
            return installer.cancel(plugin, by);
        } catch (TrackingException e) {
            Logger.error(CatalogAction.TRACK, e.getMessage());
            return false;
        }
    }

    /**
     * Installs several builds together, or none of them.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param pending what to install, in the order it should be recorded
     * @param by      who asked
     * @return the tracking records, in the same order
     * @throws InstallException if any download or check fails, having written nothing
     */
    public List<TrackedPlugin> install(List<Installer.Pending> pending, String by) {

        try {
            return installer.install(pending, by);
        } catch (TrackingException e) {
            throw new InstallException(e.getMessage(), e);
        }
    }

    // Removing

    /**
     * Moves a plugin's jar to the trash and stops tracking it.
     *
     * @param plugin the plugin to remove
     * @param by     who asked
     * @return what was binned, or null if there was no file to bin
     */
    public TrashBin.Result uninstall(TrackedPlugin plugin, String by) {

        if (!removals.cancelStagedFor(plugin)) {
            Logger.warn(CatalogAction.UPDATE, "A downloaded update for " + plugin.displayName()
                    + " is still in the update folder and should be deleted by hand.");
        }

        try {
            return removals.uninstall(plugin, by);
        } catch (TrackingException e) {
            throw new InstallException(e.getMessage(), e);
        }
    }

    /**
     * The installed plugins that require this one.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param plugin the plugin about to be removed
     * @return what would be left without a dependency
     */
    public List<TrackedPlugin> dependentsOf(TrackedPlugin plugin) {
        return dependents.of(plugin);
    }

    public List<TrashEntry> trashed() {
        return removals.list();
    }

    public TrashEntry trashed(String storedAs) {
        return removals.find(storedAs);
    }

    /**
     * Puts a removed plugin back and starts tracking it again.
     *
     * @param entry what to restore
     * @param by    who asked
     * @return the plugin Catalog is tracking again, or null if it was never tracked to begin with
     * @throws InstallException if the jar is gone, or something already occupies its file name
     */
    public TrackedPlugin restore(TrashEntry entry, String by) {

        try {
            return removals.restore(entry, by);
        } catch (TrackingException e) {
            throw new InstallException(e.getMessage(), e);
        }
    }

    public void discardTrashed(TrashEntry entry, String by) {
        removals.discard(entry, by);
    }

    public int emptyTrash(String by) {
        return removals.empty(by);
    }

    // Settings

    public void setChannel(TrackedPlugin plugin, ReleaseChannel channel, String by) {
        saving(() -> settings.channel(plugin, channel, by));
    }

    public void setAutoUpdate(TrackedPlugin plugin, boolean on, String by) {
        saving(() -> settings.autoUpdate(plugin, on, by));
    }

    public void setSoak(TrackedPlugin plugin, int minutes, String by) {
        saving(() -> settings.soak(plugin, minutes, by));
    }

    public int defaultSoakMinutes() {
        return settings.defaultSoakMinutes();
    }

    public void setHeld(TrackedPlugin plugin, boolean held, String by) {
        saving(() -> settings.held(plugin, held, by));
    }

    // Tracking

    /**
     * Stops managing a plugin. The jar is not touched.
     *
     * @param plugin the plugin to untrack
     * @param by     who asked
     */
    public void untrack(TrackedPlugin plugin, String by) {
        library.untrack(plugin, by);

        // The scan is what fills the untracked list.
        library.index();
    }

    /**
     * Puts a jar back under management and rescans, so it appears without a restart.
     *
     * @param jar the file to stop ignoring
     * @return true if it was ignored and now is not
     */
    public boolean track(InstalledJar jar, String by) {

        if (!library.track(jar, by)) {
            return false;
        }

        library.index(by);
        return true;
    }

    /**
     * @return the jars Catalog could manage but is not
     */
    public List<InstalledJar> untracked() {
        return library.untracked();
    }

    /**
     * Clears the restart fresh installs are waiting for once something else has enabled them.
     */
    public void noticeLoaded() {
        saving(library::noticeLoaded);
    }

    /**
     * @return the file name of Catalog's own jar
     */
    public String ownFileName() {
        return platform.ownFileName();
    }

    /**
     * @return true if this record is Catalog itself
     */
    public boolean isSelf(TrackedPlugin plugin) {
        return plugin != null && ownFileName().equals(plugin.fileName());
    }

    /**
     * Runs work that writes the tracking file, logging a failure instead of throwing it.
     */
    private void saving(Save work) {

        try {
            work.run();
        } catch (TrackingException e) {
            Logger.error(CatalogAction.TRACK, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Save {
        void run() throws TrackingException;
    }

}
