package top.vulpine.catalog.paper.command;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Sized;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.annotation.Switch;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.history.Event;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.install.Installer;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchHit;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.modrinth.model.TeamMember;
import top.vulpine.catalog.paper.CatalogPaper;
import top.vulpine.catalog.paper.EnableCheck;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.screen.ChangePage;
import top.vulpine.catalog.paper.command.screen.DependenciesPage;
import top.vulpine.catalog.paper.command.screen.HelpPage;
import top.vulpine.catalog.paper.command.screen.HistoryPage;
import top.vulpine.catalog.paper.command.screen.InfoPage;
import top.vulpine.catalog.paper.command.screen.ListPage;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.paper.command.screen.RemovePage;
import top.vulpine.catalog.paper.command.screen.SearchPage;
import top.vulpine.catalog.paper.command.screen.SettingsPage;
import top.vulpine.catalog.paper.command.screen.TrashPage;
import top.vulpine.catalog.paper.command.screen.VersionsPage;
import top.vulpine.catalog.paper.command.screen.widget.Format;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.TrashBin;
import top.vulpine.catalog.trash.model.TrashEntry;
import top.vulpine.catalog.update.model.UpdateCandidate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@code /catalog} command.
 */
@Command({"catalog", "ctlg", "cata", "ctl", "clg"})
public final class MainCommand {

    /** The permission each subcommand needs, which decides the buttons a viewer sees. */
    public static final CommandPermissions PERMISSIONS = CommandPermissions.of(MainCommand.class);

    /** How long a removal stays confirmable before it has to be asked for again. */
    private static final Duration CONFIRM_WINDOW = Duration.ofSeconds(30);

    /**
     * Word cap on a free-form argument that shares a command with a flag.
     *
     * <p>Lamp gives the last parameter the rest of the input, and flags have to be last, so a query
     * and {@code --page} cannot both be greedy. A capped list is non-greedy at registration and
     * still consumes every remaining word at parse time.</p>
     */
    private static final int MAX_WORDS = 12;

    /** How long a command waits before saying it is still waiting. */
    private static final Duration PATIENCE = Duration.ofSeconds(2);

    private final CatalogPaper paper;

    private final Catalog catalog;

    private final Map<String, Pending> confirmations = new ConcurrentHashMap<>();

    /** Which screen the button that ran this command was on. */
    private final ClickContext context;

    public MainCommand(CatalogPaper paper, Catalog catalog, ClickContext context) {
        this.paper = paper;
        this.catalog = catalog;
        this.context = context;
    }

    @Description("Information about Catalog")
    @RequiresPermission(CommandPermissions.ANY)
    public void about(CommandSender sender) {

        List<String> authors = paper.getDescription().getAuthors();

        send(sender, HelpPage.about(paper.getDescription().getVersion(),
                authors.isEmpty() ? "Vulpine" : String.join(", ", authors)));
    }

    @Subcommand("help")
    @Description("Every command")
    @RequiresPermission(CommandPermissions.ANY)
    public void help(CommandSender sender) {
        send(sender, HelpPage.help(command -> PERMISSIONS.allows(sender, command)));
    }

    /**
     * Runs a command's work off the main thread, with a notice once it takes {@link #PATIENCE}.
     */
    private void runAsync(CommandSender sender, Runnable work) {

        paper.getScheduler().runAsync(task -> {

            AtomicBoolean done = new AtomicBoolean();

            WrappedTask waiting = paper.getScheduler().runLaterAsync(() -> {
                if (!done.get()) {
                    send(sender, Outcomes.stillWaiting());
                }
            }, PATIENCE.toMillis(), TimeUnit.MILLISECONDS);

            try {
                work.run();
            } finally {
                done.set(true);
                waiting.cancel();
            }
        });
    }

    // Four switches at most. Past four, Lamp's Brigadier tree only chains them in declaration order,
    // so the client stops suggesting, and marks as wrong, any switch typed after a later one.
    @Subcommand("list")
    @Description("Managed plugins")
    @RequiresPermission("command.list")
    public void list(CommandSender sender,
                     @Switch("updates") boolean updates,
                     @Switch("restart") boolean restart,
                     @Switch("held") boolean held,
                     @Switch(value = "untracked", shorthand = 'n') boolean untracked) {

        // A button on the list only changes the filters, so the answer from Modrinth it already
        // showed is still the current one.
        boolean refresh = ListFilter.parse(context.take(sender)) == null;

        Set<ListFilter> filters = EnumSet.noneOf(ListFilter.class);

        if (updates) {
            filters.add(ListFilter.UPDATES);
        }

        if (restart) {
            filters.add(ListFilter.RESTART);
        }

        if (held) {
            filters.add(ListFilter.HELD);
        }

        if (untracked) {
            filters.add(ListFilter.UNTRACKED);
        }

        runAsync(sender, () -> showList(sender, refresh, filters));
    }

    @Subcommand("info")
    @Description("Details for any plugin")
    @RequiresPermission("command.info")
    public void info(CommandSender sender,
                     @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {
        runAsync(sender, () -> showProject(sender, query));
    }

    @Subcommand("search")
    @Description("Find plugins on Modrinth")
    @RequiresPermission("command.search")
    public void search(CommandSender sender, @Named("query") @Sized(max = MAX_WORDS) List<String> query,
                       @Flag("page") @Default("1") int page) {
        runSearch(sender, String.join(" ", query), Math.max(page, 1));
    }

    @Subcommand("install")
    @Description("Install a plugin from Modrinth")
    @RequiresPermission("command.install")
    public void install(CommandSender sender, @Named("plugin") @Single String query,
                        @Optional @Named("version") String wanted,
                        @Switch("incompatible") boolean incompatible) {

        String data = context.take(sender);

        String named = wanted == null || ClickContext.strip(wanted).isEmpty()
                ? null : ClickContext.strip(wanted);

        runAsync(sender, () -> runInstall(sender, query, named, incompatible, data));
    }

    /**
     * Installs a plugin, or moves an installed one to another build.
     *
     * @param incompatible whether builds for other Minecraft versions are accepted
     * @param data         what the button carried, or null when the command was typed
     */
    private void runInstall(CommandSender sender, String query, String named, boolean incompatible,
                            String data) {

        try {

            ModrinthProject project = catalog.project(query);

            if (project == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            TrackedPlugin tracked = catalog.getTracking().byProjectId(project.id());
            InstalledJar untracked = tracked == null ? catalog.getLibrary().untrackedJar(project.id()) : null;

            if (untracked != null) {
                send(sender, Outcomes.installedUntracked(project.title(), untracked.fileName()));
                return;
            }

            if (tracked != null && named == null) {
                send(sender, Outcomes.alreadyInstalled(tracked));
                return;
            }

            if (tracked != null && tracked.isPinned()) {
                send(sender, Outcomes.stillHeld(tracked.displayName()));
                return;
            }

            ModrinthVersion version = build(project, named, incompatible);

            if (version == null) {
                send(sender, named == null
                        ? Outcomes.noBuild(project.title())
                        : Outcomes.noVersion(project.title(), named));
                return;
            }

            String notFor = runsHere(version) ? null : catalog.gameVersion();

            ReleaseChannel follow = version.versionType() == null
                    ? defaultChannel() : version.versionType();

            DependencyResolver.Resolution resolution = catalog.dependenciesOf(version);
            String here = ClickContext.change("install", project.slug(), version.id());

            boolean asks = tracked != null || notFor != null || needsAnswer(resolution)
                    || redrawing(data, here);

            if (asks && !confirmed(sender, "install:" + project.id() + ":" + version.id(), data)) {

                Change change = tracked == null ? Change.INSTALL
                        : isOlder(version, tracked) ? Change.ROLL_BACK : Change.SWITCH;

                showChange(sender, change, project, tracked, version, notFor, resolution, data, here,
                        "/catalog install " + project.slug() + " " + version.id()
                                + (notFor != null ? " --incompatible" : ""));
                return;
            }

            List<Installer.Pending> needed = alone(data)
                    ? List.of() : pendingFor(resolution.missing(), project);

            if (tracked != null) {

                if (!needed.isEmpty()) {
                    catalog.install(needed, sender.getName());
                }

                boolean rollback = isOlder(version, tracked);

                catalog.setChannel(tracked, follow, sender.getName());
                catalog.stage(tracked, version, sender.getName(),
                        rollback ? Event.ROLLED_BACK : Event.SWITCHED);

                redraw(sender, screen(data));
                send(sender, Outcomes.staged(tracked.displayName(), version.versionNumber()));

                // Keeps the next check from downloading the newer build again.
                if (rollback && tracked.autoUpdate()) {
                    catalog.setAutoUpdate(tracked, false, sender.getName());
                    send(sender, Outcomes.autoUpdateTurnedOff(tracked.versionNumber(), key(tracked)));
                }

                return;
            }

            List<Installer.Pending> pending = new ArrayList<>(needed);
            pending.add(new Installer.Pending(project, version, follow, true, null));
            catalog.install(pending, sender.getName());

            redraw(sender, screen(data));
            send(sender, pending.size() == 1
                    ? Outcomes.installed(project.title(), version.versionNumber())
                    : Outcomes.installedWith(project.title(), version.versionNumber(),
                            needed.stream().map(one -> one.project().title()).toList()));

        } catch (Exception e) {
            send(sender, Outcomes.failed(rootMessage(e)));
        }
    }

    /**
     * Whether a build has missing requirements or conflicts.
     */
    private static boolean needsAnswer(DependencyResolver.Resolution resolution) {
        return !resolution.missing().isEmpty() || !resolution.conflicts().isEmpty();
    }

    /**
     * Whether this call redraws the confirmation screen. A redraw never confirms.
     */
    private static boolean redrawing(String data, String here) {
        Press press = Press.of(data);
        return press != null && !press.confirmed() && here.equals(Trail.current(press.screen()));
    }

    private static boolean alone(String data) {
        Press press = Press.of(data);
        return press != null && ClickContext.ALONE.equals(press.answer());
    }

    @Subcommand("versions")
    @Description("The newest release, beta and alpha for this server")
    @RequiresPermission("command.info")
    public void versions(CommandSender sender,
                         @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                         @Switch("all") boolean all,
                         @Switch("incompatible") boolean incompatible,
                         @Flag("page") @Default("1") int page) {

        String data = context.take(sender);

        abandonConfirmation(sender);

        runAsync(sender, () -> {

            try {

                TrackedPlugin tracked = resolve(query);
                ModrinthProject project = catalog.project(tracked != null
                        ? tracked.projectId() : query);

                if (project == null) {
                    send(sender, Outcomes.unknownPlugin(query));
                    return;
                }

                TrackedPlugin installed = catalog.getTracking().byProjectId(project.id());
                boolean untracked = installed == null
                        && catalog.getLibrary().untrackedJar(project.id()) != null;
                List<ModrinthVersion> builds = incompatible ? catalog.allVersions(project.id())
                        : catalog.compatibleVersions(project.id());

                if (all) {
                    send(sender, VersionsPage.allVersions(project, builds, installed, untracked,
                            catalog.gameVersion(), catalog.gameVersions(), incompatible, page,
                            screen(data)));
                    return;
                }

                send(sender, VersionsPage.versions(project, catalog.gameVersion(), catalog.gameVersions(),
                        newestOfEachChannel(builds), installed, untracked, incompatible,
                        screen(data)));

            } catch (Exception e) {
                send(sender, Outcomes.unreachable(rootMessage(e)));
            }
        });
    }

    /**
     * The newest build of each channel.
     *
     * @param compatible the compatible builds, newest first
     * @return the newest of each channel, channels with none absent
     */
    private static Map<ReleaseChannel, ModrinthVersion> newestOfEachChannel(
            List<ModrinthVersion> compatible) {

        Map<ReleaseChannel, ModrinthVersion> newest = new EnumMap<>(ReleaseChannel.class);

        for (ModrinthVersion version : compatible) {
            if (version.versionType() != null) {
                newest.putIfAbsent(version.versionType(), version);
            }
        }

        return newest;
    }

    /**
     * The named build, or the one an install would fetch.
     *
     * @param wanted       a version id or number, or null for the default
     * @param incompatible whether builds for other Minecraft versions count
     * @return the build, or null when there is none
     */
    private ModrinthVersion build(ModrinthProject project, String wanted, boolean incompatible) {

        ModrinthVersion version = incompatible ? choose(catalog.allVersions(project.id()), wanted) : null;

        // The two lists can come from different loaders.
        return version != null ? version : choose(catalog.compatibleVersions(project.id()), wanted);
    }

    /**
     * Whether a build declares the Minecraft version this server runs.
     */
    private boolean runsHere(ModrinthVersion version) {
        return version.gameVersions() != null && version.gameVersions().contains(catalog.gameVersion());
    }

    /**
     * Finds the build someone named.
     *
     * @param wanted the id or version number, or null to take what installing would default to
     */
    private static ModrinthVersion choose(List<ModrinthVersion> compatible, String wanted) {

        if (wanted == null) {
            return Projects.installTarget(compatible);
        }

        for (ModrinthVersion version : compatible) {
            if (wanted.equals(version.id()) || wanted.equalsIgnoreCase(version.versionNumber())) {
                return version;
            }
        }

        return null;
    }

    /**
     * Reloads config.yml.
     *
     * <p>Only the settings are reloaded and not the tracking state.</p>
     */
    @Subcommand("reload")
    @Description("Reload the configuration")
    @RequiresPermission("command.reload")
    public void reload(CommandSender sender) {

        send(sender, paper.loadConfiguration()
                ? Outcomes.reloaded()
                : Outcomes.configFailed());
    }

    @Subcommand("settings")
    @Description("View the settings menu for a plugin")
    @RequiresPermission("command.settings")
    public void settings(CommandSender sender,
                         @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        showSettings(sender, tracked, screen(context.take(sender)));
    }

    @Subcommand("auto")
    @Description("Whether a plugin updates itself automatically")
    @RequiresPermission("command.settings")
    public void auto(CommandSender sender,
                     @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                     @Named("state") Toggle state) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        catalog.setAutoUpdate(tracked, state.on(), sender.getName());
        done(sender, context.take(sender), Outcomes.autoSet(tracked.displayName(), state.on()));
    }

    @Subcommand("soak")
    @Description("How long a build must be public before this plugin automatically installs it")
    @RequiresPermission("command.settings")
    public void soak(CommandSender sender,
                     @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                     @Named("window") String window) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        Integer minutes = parseSoak(window);

        if (minutes == null) {
            send(sender, Outcomes.badSoak());
            return;
        }

        catalog.setSoak(tracked, minutes, sender.getName());
        done(sender, context.take(sender), Outcomes.soakSet(tracked.displayName(), minutes,
                catalog.defaultSoakMinutes()));
    }

    /**
     * Reads a soak window.
     *
     * @param window minutes, or a value suffixed with m or h, or "default" to follow the config
     * @return the window in minutes, {@link TrackedPlugin#INHERIT_SOAK} for the default, or null if
     *         it could not be read
     */
    private static Integer parseSoak(String window) {

        String value = ClickContext.strip(window).trim().toLowerCase(Locale.ROOT);

        if (value.equals("default") || value.equals("inherit")) {
            return TrackedPlugin.INHERIT_SOAK;
        }

        int scale = value.endsWith("h") ? 60 : 1;

        if (value.endsWith("h") || value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
        }

        try {
            return Math.max(Integer.parseInt(value.trim()), 0) * scale;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reports what an action did, and redraws the screen it was taken on.
     *
     * @param data    what the button carried, or null when the command was typed
     * @param outcome the one line saying what happened
     */
    private void done(CommandSender sender, String data, Component outcome) {

        runAsync(sender, () -> {
            redraw(sender, screen(data));
            send(sender, outcome);
        });
    }

    @Subcommand("channel")
    @Description("Which builds a plugin should follow")
    @RequiresPermission("command.channel")
    public void channel(CommandSender sender,
                        @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                        @Named("channel") ReleaseChannel channel) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        catalog.setChannel(tracked, channel, sender.getName());
        done(sender, context.take(sender), Outcomes.channelSet(tracked.displayName(), channel));
    }

    @Subcommand("update")
    @Description("Download an update and apply it at the next restart")
    @RequiresPermission("command.update")
    public void update(CommandSender sender,
                       @Named("plugin") @SuggestWith(Suggestions.Updatable.class) String query) {

        String wanted = ClickContext.strip(query);
        String data = context.take(sender);

        if (wanted.equalsIgnoreCase("all")) {
            updateAll(sender, data);
            return;
        }

        TrackedPlugin tracked = resolve(wanted);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(wanted));
            return;
        }

        runAsync(sender, () -> runUpdate(sender, tracked, data));
    }

    /**
     * Downloads the update for one plugin.
     *
     * @param data what the button carried, or null when the command was typed
     */
    private void runUpdate(CommandSender sender, TrackedPlugin tracked, String data) {

        try {

            UpdateCandidate candidate = candidateFor(tracked);

            if (candidate == null) {
                send(sender, Outcomes.noUpdate(tracked.displayName()));
                return;
            }

            ModrinthProject project = catalog.project(tracked.projectId());
            DependencyResolver.Resolution resolution = catalog.dependenciesOf(candidate.version());

            // Asks when the new build has missing requirements.
            if (project != null) {

                String here = ClickContext.change("update", project.slug(), null);

                if ((needsAnswer(resolution) || redrawing(data, here)) && !confirmed(sender,
                        "update:" + project.id() + ":" + candidate.version().id(), data)) {

                    showChange(sender, Change.UPDATE, project, tracked, candidate.version(), null,
                            resolution, data, here,
                            "/catalog update " + project.slug());
                    return;
                }
            }

            if (!alone(data) && !resolution.missing().isEmpty()) {
                catalog.install(pendingFor(resolution.missing(), project), sender.getName());
            }

            catalog.stage(candidate, sender.getName());

            redraw(sender, screen(data));
            send(sender, Outcomes.staged(tracked.displayName(), candidate.to()));

        } catch (Exception e) {
            send(sender, Outcomes.failed(rootMessage(e)));
        }
    }

    @Subcommand("history")
    @Description("What Catalog has done")
    @RequiresPermission("command.history")
    public void history(CommandSender sender,
                        @Optional @Flag(value = "plugin", shorthand = 'l')
                        @SuggestWith(Suggestions.Logged.class) String which,
                        @Optional @Flag("author") String author,
                        @Optional @Flag("event") @SuggestWith(Suggestions.Kinds.class) String event,
                        @Flag("page") @Default("1") int page) {

        context.take(sender);

        runAsync(sender, () -> {

            String projectId = null;
            String label = null;
            StringBuilder command = new StringBuilder("/catalog history");

            if (which != null && !which.isBlank()) {

                // Resolved against the log rather than the tracking file, so a plugin that has
                // since been removed can still be looked up.
                projectId = catalog.loggedProjectId(which);

                if (projectId == null) {
                    send(sender, Outcomes.unknownPlugin(which));
                    return;
                }

                label = which;
                command.append(" --plugin ").append(which);
            }

            if (author != null && !author.isBlank()) {
                label = label == null ? author : label + ", " + author;
                command.append(" --author ").append(author);
            }

            List<HistoryEntry> entries = catalog.history(projectId, author);

            if (event != null && !event.isBlank()) {

                HistoryKind kind = HistoryKind.named(event);

                if (kind == null) {
                    send(sender, Outcomes.unknownKind(event));
                    return;
                }

                entries = entries.stream().filter(entry -> kind.covers(entry.event())).toList();
                label = label == null ? kind.label() : label + ", " + kind.label();
                command.append(" --event ").append(kind.label());
            }

            send(sender, HistoryPage.history(entries, Math.max(page, 1), label, command.toString()));
        });
    }

    @Subcommand("cancel")
    @Description("Drop a downloaded update")
    @RequiresPermission("command.update")
    public void cancel(CommandSender sender,
                       @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        String data = context.take(sender);

        if (!tracked.pendingRestart()) {
            send(sender, Outcomes.nothingStaged(tracked.displayName()));
            return;
        }

        runAsync(sender, () -> {

            if (!catalog.cancelUpdate(tracked, sender.getName())) {
                send(sender, Outcomes.failed("Could not delete the downloaded build for "
                        + tracked.displayName() + ", see the console."));
                return;
            }

            redraw(sender, screen(data));
            send(sender, Outcomes.cancelled(tracked.displayName()));
        });
    }

    private void updateAll(CommandSender sender, String data) {

        runAsync(sender, () -> {

            try {

                catalog.refreshUpdates();
                List<UpdateCandidate> candidates = catalog.openUpdates();

                if (candidates.isEmpty()) {
                    send(sender, Outcomes.upToDate());
                    return;
                }

                int staged = 0;
                List<Component> failures = new ArrayList<>();

                for (UpdateCandidate candidate : candidates) {

                    try {

                        List<DependencyResolver.Requirement> missing =
                                catalog.missingFor(candidate.version());

                        if (!missing.isEmpty()) {
                            failures.add(Outcomes.needsDependencies(
                                    candidate.plugin().displayName(), missing.size()));
                            continue;
                        }

                        catalog.stage(candidate, sender.getName());
                        staged++;
                    } catch (Exception e) {
                        // One plugin failing is not a reason to abandon the rest of the queue, and
                        // the reason is worth more after the list than buried above it.
                        failures.add(Outcomes.stageFailed(candidate.plugin().displayName(),
                                rootMessage(e)));
                    }
                }

                redraw(sender, screen(data));

                if (staged > 0) {
                    send(sender, Outcomes.stagedAll(staged));
                }

                for (Component failure : failures) {
                    send(sender, failure);
                }

            } catch (Exception e) {
                send(sender, Outcomes.failed(rootMessage(e)));
            }
        });
    }

    @Subcommand("uninstall")
    @Description("Moves a plugin to the trash")
    @RequiresPermission("command.uninstall")
    public void uninstall(CommandSender sender,
                          @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        if (catalog.isSelf(tracked)) {
            send(sender, Outcomes.cannotRemoveSelf(tracked.displayName()));
            return;
        }

        String data = context.take(sender);
        String name = tracked.displayName();

        runAsync(sender, () -> {

            try {

                // Removing is normally offered with an undo rather than a confirmation. That stops
                // being enough when something else needs it: an undo cannot help a server that
                // already failed to start.
                if (!confirmed(sender, "remove:" + tracked.projectId(), data)) {

                    List<TrackedPlugin> dependents = catalog.dependentsOf(tracked);

                    if (!dependents.isEmpty()) {
                        send(sender, RemovePage.confirmRemove(tracked, dependents, Press.of(data)));
                        return;
                    }
                }

                catalog.noticeLoaded();
                boolean loaded = !tracked.pendingLoad();

                TrashBin.Result result = catalog.uninstall(tracked, sender.getName());

                redraw(sender, screen(data));
                send(sender, Outcomes.removed(name, loaded, result == null ? null : result.entry(),
                        screen(data)));

            } catch (Exception e) {
                send(sender, Outcomes.failed(rootMessage(e)));
            }
        });
    }

    @Subcommand("dependencies")
    @Description("What a plugin declares it needs")
    @RequiresPermission("command.info")
    public void dependencies(CommandSender sender,
                             @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                             @Optional @Named("version") String wanted) {

        String data = context.take(sender);

        runAsync(sender, () -> showDependencies(sender, query, wanted, screen(data)));
    }

    /**
     * Draws what one build declares it needs.
     */
    private void showDependencies(CommandSender sender, String query, String wanted, String from) {

        try {

            ModrinthProject project = projectFor(query);

            if (project == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            ModrinthVersion version = build(project, wanted, wanted != null);

            if (version == null) {
                send(sender, wanted == null
                        ? Outcomes.noBuild(project.title())
                        : Outcomes.noVersion(project.title(), wanted));
                return;
            }

            DependencyResolver.Resolution resolution = catalog.dependenciesOf(version);

            List<DependencyResolver.Requirement> declared = new ArrayList<>(resolution.required());
            declared.addAll(resolution.optional());
            declared.addAll(resolution.conflicts());

            send(sender, DependenciesPage.dependencies(project, version, catalog.named(declared), from));

        } catch (Exception e) {
            send(sender, Outcomes.failed(rootMessage(e)));
        }
    }

    /**
     * Draws the screen that asks before an install, update or switch.
     *
     * @param installed the plugin as tracked now, or null for a first install
     * @param here      this screen, so a dependency installed from it comes back to it
     * @param command   what confirming runs
     */
    private void showChange(CommandSender sender, Change change, ModrinthProject project,
                            TrackedPlugin installed, ModrinthVersion version, String notFor,
                            DependencyResolver.Resolution resolution, String data, String here,
                            String command) {

        List<DependencyResolver.Requirement> needs = new ArrayList<>(resolution.missing());
        needs.addAll(resolution.conflicts());

        Press asked = Press.of(data);

        // Its buttons lead to where it was asked from, not back here.
        if (asked != null && here.equals(Trail.current(asked.screen()))) {
            String parent = Trail.parent(asked.screen());
            asked = Press.on(parent != null ? parent : ClickContext.INFO + project.slug());
        }

        String from = installed == null ? null : installed.versionNumber();
        String replaces = installed != null && installed.pendingRestart()
                ? installed.stagedVersionNumber() : null;

        send(sender, ChangePage.change(change, project.title(), project.slug(), from, replaces, version,
                notFor, catalog.named(needs), asked, Trail.within(here, asked == null ? null : asked.screen()), command));
    }

    /**
     * What to install for each missing requirement.
     */
    private List<Installer.Pending> pendingFor(List<DependencyResolver.Requirement> missing,
                                                  ModrinthProject root) {

        Map<String, ModrinthProject> projects = projectsFor(missing);
        List<Installer.Pending> pending = new ArrayList<>();

        for (DependencyResolver.Requirement requirement : missing) {

            ModrinthProject project = projects.get(requirement.projectId());

            if (project == null || requirement.available() == null) {
                continue;
            }

            ReleaseChannel follow = requirement.available().versionType() == null
                    ? defaultChannel() : requirement.available().versionType();

            pending.add(new Installer.Pending(project, requirement.available(), follow, false,
                    root == null ? null : root.title()));
        }

        return pending;
    }

    private Map<String, ModrinthProject> projectsFor(List<DependencyResolver.Requirement> requirements) {

        Set<String> ids = new LinkedHashSet<>();

        for (DependencyResolver.Requirement requirement : requirements) {
            ids.add(requirement.projectId());
        }

        return byId(ids);
    }

    private Map<String, ModrinthProject> byId(Set<String> ids) {

        Map<String, ModrinthProject> projects = new HashMap<>();

        if (ids.isEmpty()) {
            return projects;
        }

        try {
            for (ModrinthProject project : catalog.getModrinth().projects(ids).join()) {
                projects.put(project.id(), project);
            }
        } catch (Exception ignored) {
            // The ids are still enough to name and act on a row.
        }

        return projects;
    }

    /**
     * Which answer to the dependency question a payload carries.
     */
    private static String intent(String data) {
        Press press = Press.of(data);
        return press == null ? null : press.answer();
    }

    @Subcommand("trash")
    @Description("Show plugins you have removed")
    @RequiresPermission("command.trash")
    public void trash(CommandSender sender, @Flag("page") @Default("1") int page) {
        runAsync(sender, () -> showTrash(sender, Math.max(page, 1)));
    }

    private void showTrash(CommandSender sender, int page) {
        abandonConfirmation(sender);
        send(sender, TrashPage.trash(catalog.trashed(),
                paper.getConfiguration().trash.retentionDays, page));
    }

    /**
     * Restores a trashed plugin.
     */
    @Subcommand("trash restore")
    @Description("Put a removed plugin back")
    @RequiresPermission("command.trash")
    public void restore(CommandSender sender,
                        @Named("removal") @SuggestWith(Suggestions.Trashed.class) String query) {

        String data = context.take(sender);

        runAsync(sender, () -> {

            try {

                TrashEntry entry = resolveTrashed(query);

                if (entry == null) {
                    send(sender, Outcomes.nothingToRestore());
                    return;
                }

                TrackedPlugin tracked = catalog.restore(entry, sender.getName());

                redraw(sender, screen(data));
                send(sender, Outcomes.restored(entry.displayName(), tracked != null));

            } catch (Exception e) {
                send(sender, Outcomes.failed(rootMessage(e)));
            }
        });
    }

    /**
     * Deletes a trash entry permanently (or all of them).
     */
    @Subcommand("trash delete")
    @Description("Delete a removal permanently")
    @RequiresPermission("command.trash")
    public void delete(CommandSender sender,
                       @Named("removal") @SuggestWith(Suggestions.Discardable.class) String query) {

        String data = context.take(sender);

        if ("all".equalsIgnoreCase(ClickContext.strip(query).trim())) {
            emptyTrash(sender, data);
            return;
        }

        runAsync(sender, () -> {

            try {

                TrashEntry entry = resolveTrashed(query);

                if (entry == null) {
                    send(sender, Outcomes.nothingToRestore());
                    return;
                }

                catalog.discardTrashed(entry, sender.getName());

                redraw(sender, screen(data));
                send(sender, Outcomes.discarded(entry.displayName()));

            } catch (Exception e) {
                send(sender, Outcomes.failed(rootMessage(e)));
            }
        });
    }

    private void emptyTrash(CommandSender sender, String data) {

        runAsync(sender, () -> {

            try {

                int waiting = catalog.trashed().size();

                if (waiting == 0) {
                    send(sender, Outcomes.trashAlreadyEmpty());
                    return;
                }

                if (!confirmed(sender, "empty", data)) {
                    send(sender, TrashPage.confirmEmpty(waiting));
                    return;
                }

                int deleted = catalog.emptyTrash(sender.getName());

                redraw(sender, screen(data));
                send(sender, Outcomes.emptied(deleted));

            } catch (Exception e) {
                send(sender, Outcomes.failed(rootMessage(e)));
            }
        });
    }

    /**
     * Finds a trashed plugin by file name and plugin name.
     */
    private TrashEntry resolveTrashed(String query) {

        String wanted = ClickContext.strip(query).trim();
        TrashEntry exact = catalog.trashed(wanted);

        if (exact != null) {
            return exact;
        }

        String lowered = wanted.toLowerCase(Locale.ROOT);

        for (TrashEntry entry : catalog.trashed()) {

            if (matches(entry.name(), lowered)
                    || matches(entry.slug(), lowered)
                    || matches(entry.fileName(), lowered)
                    || matches(entry.projectId(), lowered)) {
                return entry;
            }
        }

        return null;
    }

    @Subcommand("untrack")
    @Description("Stops managing a plugin, leaving the jar alone")
    @RequiresPermission("command.untrack")
    public void untrack(CommandSender sender,
                        @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        String data = context.take(sender);

        runAsync(sender, () -> {

            TrackedPlugin tracked = resolve(query);

            if (tracked == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            String name = tracked.displayName();

            // A staged build is already in the update folder, so leaving it there would update a
            // plugin Catalog has just been told to stop following.
            if (tracked.pendingRestart()) {
                catalog.cancelUpdate(tracked, sender.getName());
            }

            catalog.untrack(tracked, sender.getName());
            done(sender, data, Outcomes.untracked(name));
        });
    }

    @Subcommand("track")
    @Description("Manages a plugin Catalog was told to leave alone")
    @RequiresPermission("command.untrack")
    public void track(CommandSender sender,
                      @Named("jar") @SuggestWith(Suggestions.Untracked.class) String query) {

        String data = context.take(sender);

        runAsync(sender, () -> {

            InstalledJar jar = resolveUntracked(query);

            if (jar == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            if (!catalog.track(jar, sender.getName())) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            done(sender, data, Outcomes.tracked(Format.shownName(jar)));
        });
    }

    /**
     * Finds an untracked jar by the name the list shows, or by its file name.
     *
     * @param query what was typed
     * @return the jar, or null if no untracked jar answers to it
     */
    private InstalledJar resolveUntracked(String query) {

        String wanted = query.trim();

        for (InstalledJar jar : catalog.untracked()) {
            if (jar.fileName().equalsIgnoreCase(wanted)
                    || Format.shownName(jar).equalsIgnoreCase(wanted)) {
                return jar;
            }
        }

        return null;
    }

    @Subcommand("hold")
    @Description("Freeze a plugin at its installed version")
    @RequiresPermission("command.hold")
    public void hold(CommandSender sender,
                     @Named("plugin") @SuggestWith(Suggestions.Holdable.class) String query) {
        setHeld(sender, query, true);
    }

    @Subcommand("unhold")
    @Description("Let a plugin be updated again")
    @RequiresPermission("command.hold")
    public void unhold(CommandSender sender,
                       @Named("plugin") @SuggestWith(Suggestions.Held.class) String query) {
        setHeld(sender, query, false);
    }

    private void setHeld(CommandSender sender, String query, boolean held) {

        TrackedPlugin tracked = resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        catalog.setHeld(tracked, held, sender.getName());
        done(sender, context.take(sender), Outcomes.held(tracked.displayName(), held));
    }

    // --- shared ------------------------------------------------------------------------

    /**
     * Draws the screen the button was on again, and then states the action taken.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param data the trail of the screen the button was on, or null
     */
    private void redraw(CommandSender sender, String data) {

        String screen = Trail.current(data);
        String from = Trail.parent(data);

        if (screen == null) {
            return;
        }

        Set<ListFilter> filters = ListFilter.parse(screen);

        if (filters != null) {
            showList(sender, true, filters);
        } else if (screen.equals(ClickContext.TRASH)) {
            showTrash(sender, 1);
        } else if (screen.startsWith(ClickContext.DEPENDENCIES)) {
            String[] about = ClickContext.dependenciesOf(screen);
            showDependencies(sender, about[0], about[1], from);
        } else if (screen.startsWith(ClickContext.CHANGE)) {

            String[] change = ClickContext.changeOf(screen);

            if (!change[0].equals("update")) {
                runInstall(sender, change[1], change[2], true, data);
                return;
            }

            TrackedPlugin tracked = resolve(change[1]);

            if (tracked != null) {
                runUpdate(sender, tracked, data);
            }
        } else if (screen.startsWith(ClickContext.INFO)) {
            showProject(sender, screen.substring(ClickContext.INFO.length()));
        } else if (screen.startsWith(ClickContext.SETTINGS)) {

            TrackedPlugin tracked = resolve(screen.substring(ClickContext.SETTINGS.length()));

            if (tracked != null) {
                showSettings(sender, tracked, from);
            }
        }
    }

    /**
     * Renders one plugin's settings.
     */
    private void showSettings(CommandSender sender, TrackedPlugin tracked, String from) {
        abandonConfirmation(sender);
        send(sender, SettingsPage.settings(tracked, catalog.defaultSoakMinutes(), from));
    }

    private static String key(TrackedPlugin plugin) {
        return plugin.slug() != null ? plugin.slug() : plugin.displayName();
    }

    /**
     * Forgets a removal waiting to be confirmed.
     *
     * <p>Called whenever a screen is drawn, which is what makes Cancel actually cancel: the button
     * navigates away, and without this the confirmation would still be armed, so pressing the same
     * remove button again inside the window would go straight through without asking.</p>
     */
    private void abandonConfirmation(CommandSender sender) {
        confirmations.remove(sender.getName());
    }

    /**
     * Whether to go ahead, or to ask first.
     *
     * @param action identifies precisely what is being confirmed
     * @param data   the payload the command arrived with, or null if it was typed
     * @return true to go ahead, false when the caller should show a confirmation instead
     */
    private boolean confirmed(CommandSender sender, String action, String data) {

        if (data != null) {
            return Press.of(data).confirmed();
        }

        Pending pending = confirmations.get(sender.getName());

        if (pending != null && !pending.expired() && pending.action().equals(action)) {
            confirmations.remove(sender.getName());
            return true;
        }

        confirmations.put(sender.getName(), new Pending(action, Instant.now()));
        return false;
    }

    /**
     * The screen a payload points at.
     */
    private static String screen(String data) {
        Press press = Press.of(data);
        return press == null ? null : press.screen();
    }

    /**
     * Renders the plugin list.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param refresh whether to ask Modrinth again first
     */
    private void showList(CommandSender sender, boolean refresh, Set<ListFilter> filters) {

        abandonConfirmation(sender);

        if (catalog.getTracking().size() == 0 && catalog.untracked().isEmpty()) {
            send(sender, Outcomes.nothingTracked());
            return;
        }

        if (refresh) {

            try {
                catalog.refreshUpdates();
            } catch (Exception ignored) {
                // Shown under the list header.
            }
        }

        catalog.noticeLoaded();

        Map<String, EnableCheck.Failure> notEnabled = new HashMap<>();

        for (EnableCheck.Failure failure : paper.getEnableCheck().failures()) {
            notEnabled.put(failure.plugin().projectId(), failure);
        }

        send(sender, ListPage.list(catalog.getTracking().all(), catalog.updatesByProject(), notEnabled,
                catalog.ownFileName(), catalog.untracked(), filters,
                catalog.getUpdates().checkedAt(), catalog.getUpdates().unreachable()));
    }

    /**
     * Builds and sends the project page.
     */
    private void showProject(CommandSender sender, String query) {

        abandonConfirmation(sender);
        catalog.noticeLoaded();

        try {

            ModrinthProject project = projectFor(query);

            if (project == null) {
                send(sender, Outcomes.unknownPlugin(query));
                return;
            }

            TrackedPlugin tracked = catalog.getTracking().byProjectId(project.id());

            ReleaseChannel channel = tracked != null ? tracked.channel() : defaultChannel();

            List<ModrinthVersion> compatible = compatibleOrEmpty(project.id());
            ModrinthVersion latest = newestOn(compatible, channel);

            send(sender, InfoPage.project(project, author(project.id()), latest,
                    Projects.installTarget(compatible), tracked,
                    tracked == null ? catalog.getLibrary().untrackedJar(project.id()) : null,
                    catalog.isSelf(tracked),
                    catalog.declared(latest, DependencyType.REQUIRED),
                    catalog.declared(latest, DependencyType.OPTIONAL),
                    catalog.platformLoaders(), catalog.gameVersions()));

        } catch (Exception e) {
            send(sender, Outcomes.unreachable(rootMessage(e)));
        }
    }

    private void runSearch(CommandSender sender, String query, int page) {
        runAsync(sender, () -> showSearch(sender, query, page));
    }

    /**
     * Renders a page of search results.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     */
    private void showSearch(CommandSender sender, String query, int page) {

        abandonConfirmation(sender);

        try {

            SearchResults results = catalog.search(query, SearchPage.PAGE,
                    (page - 1) * SearchPage.PAGE);

            // Text search does not match project ids, so a query that found nothing and could
            // be one is worth one direct lookup before giving up.
            if (results.hits().isEmpty() && !query.contains(" ")) {
                showProject(sender, query);
                return;
            }

            send(sender, SearchPage.search(query, results, page, trackedProjectIds()));

        } catch (Exception e) {
            send(sender, Outcomes.unreachable(rootMessage(e)));
        }
    }

    /**
     * The update offered for a plugin checked again if the cache doesn't have it.
     */
    private UpdateCandidate candidateFor(TrackedPlugin tracked) {

        UpdateCandidate candidate = catalog.updatesByProject().get(tracked.projectId());

        if (candidate != null) {
            return candidate;
        }

        catalog.refreshUpdates();
        return catalog.updatesByProject().get(tracked.projectId());
    }

    private ModrinthProject firstSearchHit(String query) {

        SearchResults results = catalog.search(query, 1, 0);

        if (results.hits().isEmpty()) {
            return null;
        }

        SearchHit hit = results.hits().get(0);
        return catalog.project(hit.slug() != null ? hit.slug() : hit.projectId());
    }

    /**
     * Who to credit for a project.
     */
    private String author(String projectId) {

        try {
            return TeamMember.credit(catalog.getModrinth().members(projectId).join());
        } catch (Exception e) {
            return null;
        }
    }

    private List<ModrinthVersion> compatibleOrEmpty(String projectId) {

        try {
            return catalog.compatibleVersions(projectId);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static ModrinthVersion newestOn(List<ModrinthVersion> versions, ReleaseChannel channel) {

        for (ModrinthVersion version : versions) {
            if (version.versionType() == null || channel.accepts(version.versionType())) {
                return version;
            }
        }

        return null;
    }

    /**
     * Whether a build is older than what is installed.
     */
    private static boolean isOlder(ModrinthVersion version, TrackedPlugin tracked) {

        return tracked.datePublished() != null && version.datePublished() != null
                && version.datePublished().isBefore(tracked.datePublished());
    }

    /**
     * Whether a build is newer than what is installed.
     */
    private Set<String> trackedProjectIds() {

        Set<String> ids = new HashSet<>();

        for (TrackedPlugin tracked : catalog.getTracking().all()) {
            ids.add(tracked.projectId());
        }

        return ids;
    }

    private ReleaseChannel defaultChannel() {
        return paper.getConfiguration().tracking.defaults.channel;
    }

    /**
     * A project found by slug or id.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param query what was typed or clicked, payload and all
     * @return the project, or null when nothing matched
     */
    private ModrinthProject projectFor(String query) {

        String wanted = ClickContext.strip(query).trim();
        TrackedPlugin tracked = resolve(wanted);

        ModrinthProject project = catalog.project(tracked != null ? tracked.projectId() : wanted);
        return project != null ? project : firstSearchHit(wanted);
    }

    /**
     * Finds a tracked plugin.
     *
     * <p>An exact match on the display name, slug or project id wins; otherwise the first whose
     * name starts with what was typed, so partial names work.</p>
     */
    private TrackedPlugin resolve(String query) {

        String wanted = ClickContext.strip(query).toLowerCase(Locale.ROOT);
        List<TrackedPlugin> all = catalog.getTracking().all();

        for (TrackedPlugin tracked : all) {
            if (matches(tracked.displayName(), wanted)
                    || matches(tracked.slug(), wanted)
                    || matches(tracked.projectId(), wanted)) {
                return tracked;
            }
        }

        for (TrackedPlugin tracked : all) {
            if (tracked.displayName() != null
                    && tracked.displayName().toLowerCase(Locale.ROOT).startsWith(wanted)) {
                return tracked;
            }
        }

        return null;
    }

    private static boolean matches(String value, String wanted) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(wanted);
    }

    private static String rootMessage(Throwable error) {

        Throwable cause = error;

        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }

        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static void send(CommandSender sender, List<Component> lines) {

        sender.sendMessage(Component.empty());

        for (Component line : lines) {

            Component shown = PERMISSIONS.shown(sender, line);

            if (shown != null) {
                sender.sendMessage(shown);
            }
        }
    }

    private static void send(CommandSender sender, Component line) {
        send(sender, List.of(line));
    }

    /**
     * @param action what is waiting to be confirmed, identified precisely enough that confirming
     *               one thing can never carry out another
     */
    private record Pending(String action, Instant asked) {

        private boolean expired() {
            return Duration.between(asked, Instant.now()).compareTo(CONFIRM_WINDOW) > 0;
        }

    }


}
