package top.vulpine.catalog;

import lombok.Getter;
import top.vulpine.catalog.config.Config;
import top.vulpine.catalog.history.Event;
import top.vulpine.catalog.history.History;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.install.Downloader;
import top.vulpine.catalog.install.InstallException;
import top.vulpine.catalog.install.Installer;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.json.Json;
import top.vulpine.catalog.modrinth.GameVersions;
import top.vulpine.catalog.modrinth.ModrinthClient;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.network.Network;
import top.vulpine.catalog.network.Presence;
import top.vulpine.catalog.network.RedisNetwork;
import top.vulpine.catalog.network.model.Inventory;
import top.vulpine.catalog.platform.ExitTasks;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.tracking.Dependents;
import top.vulpine.catalog.tracking.EnableCheck;
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
import top.vulpine.catalog.update.model.ServerTarget;
import top.vulpine.catalog.update.model.UpdateCandidate;
import top.vulpine.commons.log.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Catalog's services for one platform.
 */
@Getter
public final class Catalog {

    /** What a server name on the network may hold. */
    private static final Pattern SERVER_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final Platform platform;

    private final Supplier<Config> configuration;

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
    private EnableCheck enableCheck;

    /** Null while the network is off. */
    private volatile Network network;
    private volatile Presence presence;

    /** Every Minecraft version Modrinth knows. Loaded on first use. */
    private volatile GameVersions gameVersions;

    /**
     * @param configuration the current configuration
     */
    public Catalog(Platform platform, Supplier<Config> configuration) {
        this.platform = platform;
        this.configuration = configuration;
    }

    /**
     * Loads the state, wires the services and starts the first scan.
     *
     * @return false when the state files could not be read
     */
    public boolean start() {

        banner();

        Path data = platform.dataFolder();

        this.modrinth = ModrinthClient.builder()
                .userAgent("VulpineFriend87/Catalog/" + platform.version() + " (vulpine.top)")
                .token(configuration.get().modrinth.token)
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
                () -> configuration.get().tracking.defaults.soakMinutes, projects::dependenciesOf,
                projects::named, history, () -> library.index());
        this.library = new Library(platform, modrinth, tracking, ignored, this::defaults,
                () -> configuration.get().tracking.autoTrack, history);
        this.dependents = new Dependents(tracking, hashes -> modrinth.identify(hashes).join());
        this.enableCheck = new EnableCheck(platform, library, history, modrinth);

        Runtime.getRuntime().addShutdownHook(new Thread(this::exit, "Catalog exit tasks"));

        platform.async(this::index);
        platform.async(this::pruneTrash);
        scheduleUpdateChecks();

        if (configuration.get().network.enabled) {
            platform.async(this::joinNetwork);
        }

        return true;
    }

    public void stop() {

        if (presence != null) {
            presence.leave();
        }

        if (network != null) {
            network.close();
        }

        if (modrinth != null) {
            modrinth.close();
        }
    }

    /**
     * Connects to Redis and takes this server's name.
     */
    private void joinNetwork() {

        Config.Network settings = configuration.get().network;
        String name = settings.serverName.trim();

        if (!SERVER_NAME.matcher(name).matches()) {
            Logger.error(CatalogAction.NETWORK, "network.server_name must be up to 32 lowercase letters,"
                    + " digits, - and _. The network is off.");
            return;
        }

        try {
            network = RedisNetwork.connect(settings.redis, settings.password,
                    Duration.ofDays(Math.max(settings.offlineDays, 0)));
        } catch (RuntimeException e) {
            Logger.error(CatalogAction.NETWORK, "Could not connect to Redis: " + Errors.rootMessage(e)
                    + ". The network is off.");
            return;
        }

        presence = new Presence(network, name, platform::later, platform::repeat, () -> publish(name));
        presence.join();
    }

    /**
     * Sends this server's inventory to the network.
     */
    private void publish(String name) {

        ServerTarget target = platform.target();

        Inventory inventory = Inventory.builder()
                .server(name)
                .platform(target.platform().id())
                .minecraft(target.gameVersion())
                .catalog(platform.version())
                .publishedAt(Instant.now())
                .plugins(Inventory.plugins(tracking.all(), updatesByProject()))
                .untracked(library.untracked().size())
                .build();

        network.publish(name, Json.gson().toJson(inventory)).whenComplete((done, error) -> {
            if (error != null) {
                Logger.warn(CatalogAction.NETWORK, "Could not publish " + name + ": " + Errors.rootMessage(error));
            }
        });
    }

    private void banner() {

        String[] banner = {
                "",
                "<light_purple>    ┏┓     ┓",
                "<light_purple>    ┃ ┏┓╋┏┓┃┏┓┏┓",
                "<light_purple>    ┗┛┗┻┗┗┻┗┗┛┗┫",
                "<light_purple>               ┛",
                "",
                "<white>    By <light_purple>" + String.join(", ", platform.authors()),
                "<white>    Version: <light_purple>" + platform.version(),
                ""
        };

        for (String line : banner) {
            Logger.system(line);
        }
    }

    /**
     * Queues the builds waiting at {@link Platform#stagedAtExit} over their jars and runs the exit
     * tasks.
     */
    private void exit() {

        Path folder = platform.pluginsFolder();

        for (TrackedPlugin plugin : tracking.pendingRestart()) {

            Path staged = plugin.stagedAs() == null ? null : platform.stagedAtExit(plugin.stagedAs());

            if (staged != null && Files.exists(staged)) {
                exitTasks.replace(staged, folder.resolve(plugin.fileName()),
                        folder.resolve(plugin.stagedAs()));
            }
        }

        exitTasks.run();
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

    private void index() {

        downloader.clean();

        boolean indexed = library.index();
        enableCheck.scanned();

        if (indexed) {
            updates.check();
        }
    }

    private void scheduleUpdateChecks() {

        int minutes = configuration.get().updates.checkIntervalMinutes;

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

        int days = configuration.get().trash.retentionDays;
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
     * Asks Modrinth what is out of date.
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
     * What Catalog has done. Newest first.
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
     * @return every plugin the history mentions
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
     * @return the named projects a build declares of one type
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
     * Blocks on Modrinth, so it must be called off the main thread.
     *
     * @return the projects keyed by id
     */
    public Map<String, ModrinthProject> projects(Collection<String> ids) {
        return projects.byId(ids);
    }

    /**
     * Blocks on Modrinth the first time, so it must be called off the main thread.
     *
     * @return the Minecraft versions Modrinth knows
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
     * @return the loaders this server can use sorted most specific first
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
     * Cancels the staged build of a plugin.
     *
     * @param plugin the plugin whose staged build to cancel
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
     * Installs several builds atomically.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param pending what to install in the order to record it
     * @param by      who asked
     * @return the tracking records in the same order
     * @throws InstallException if any download or check fails. Nothing is written.
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
     * Restores a removed plugin and tracks it again.
     *
     * @param entry what to restore
     * @param by    who asked
     * @return the plugin Catalog is tracking again, or null if it was never tracked
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
     * Stops managing a plugin.
     *
     * @param plugin the plugin to untrack
     * @param by     who asked
     */
    public void untrack(TrackedPlugin plugin, String by) {
        library.untrack(plugin, by);

        // The scan fills the untracked list.
        library.index();
    }

    /**
     * Tracks a jar again and rescans the plugins folder.
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
     * Clears {@code pendingLoad} on fresh installs that are already enabled.
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
     * Runs {@code work} and logs the {@link TrackingException} it throws.
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
