package top.vulpine.catalog.command.service;

import net.kyori.adventure.text.Component;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.Errors;
import top.vulpine.catalog.command.argument.ListFilter;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.command.screen.Change;
import top.vulpine.catalog.command.screen.ChangePage;
import top.vulpine.catalog.command.screen.InfoPage;
import top.vulpine.catalog.command.screen.ListPage;
import top.vulpine.catalog.command.screen.NetworkPage;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.command.screen.SearchPage;
import top.vulpine.catalog.command.screen.ServersPage;
import top.vulpine.catalog.command.screen.SettingsPage;
import top.vulpine.catalog.command.screen.TrashPage;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.network.model.Peer;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.EnableCheck;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * Sends the screens commands open.
 */
public final class Screens {

    private final Catalog catalog;

    private final Chat chat;

    private final Confirmations confirmations;

    private final Lookup lookup;

    public Screens(Catalog catalog, Chat chat, Confirmations confirmations, Lookup lookup) {
        this.catalog = catalog;
        this.chat = chat;
        this.confirmations = confirmations;
        this.lookup = lookup;
    }

    /**
     * Sends the question asked before an install, update or switch.
     *
     * @param installed the plugin as tracked now, or null for a first install
     * @param here      this screen
     * @param command   what confirming runs
     */
    public void showChange(Viewer viewer, Change change, ModrinthProject project,
                            TrackedPlugin installed, ModrinthVersion version, String notFor,
                            DependencyResolver.Resolution resolution, String data, String here,
                            String command) {

        List<DependencyResolver.Requirement> needs = new ArrayList<>(resolution.missing());
        needs.addAll(resolution.conflicts());

        Press asked = Press.of(data);

        // Its buttons lead to where it was asked from.
        if (asked != null && here.equals(Trail.current(asked.screen()))) {
            String parent = Trail.parent(asked.screen());
            asked = Press.on(parent != null ? parent : new Screen.InfoScreen(project.slug()).token());
        }

        String from = installed == null ? null : installed.versionNumber();
        String replaces = installed != null && installed.pendingRestart()
                ? installed.stagedVersionNumber() : null;

        send(viewer, ChangePage.change(change, project.title(), project.slug(), from, replaces, version,
                notFor, catalog.named(needs), asked, Trail.within(here, asked == null ? null : asked.screen()), command));
    }

    /**
     * @param server the server whose trash to show, or null for this one
     */
    public void showTrash(Viewer viewer, int page, String server) {

        confirmations.abandon(viewer);
        int retention = catalog.getConfiguration().get().trash.retentionDays;

        if (!isRemote(server)) {
            send(viewer, TrashPage.trash(catalog.trashed(), retention, page, null, networked()));
            return;
        }

        Peer peer = peer(viewer, server);

        if (peer != null) {
            send(viewer, TrashPage.trash(peer.inventory().trash(), retention, page, peer.name(), true));
        }
    }

    /**
     * Sends one plugin's settings.
     */
    public void showSettings(Viewer viewer, TrackedPlugin tracked, String from) {
        confirmations.abandon(viewer);
        send(viewer, SettingsPage.settings(tracked, catalog.defaultSoakMinutes(), from));
    }

    /**
     * Sends the plugin list.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param refresh whether to ask Modrinth again first
     * @param server  the server whose plugins to show, or null for this one
     */
    public void showList(Viewer viewer, boolean refresh, Set<ListFilter> filters, String server) {

        confirmations.abandon(viewer);

        if (isRemote(server)) {

            Peer peer = peer(viewer, server);

            if (peer != null) {
                send(viewer, ListPage.remote(peer));
            }

            return;
        }

        if (catalog.getTracking().size() == 0 && catalog.untracked().isEmpty()) {
            send(viewer, Outcomes.nothingTracked());
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

        for (EnableCheck.Failure failure : catalog.getEnableCheck().failures()) {
            notEnabled.put(failure.plugin().projectId(), failure);
        }

        send(viewer, ListPage.list(catalog.getTracking().all(), catalog.updatesByProject(), notEnabled,
                catalog.ownFileName(), catalog.untracked(), filters,
                catalog.getUpdates().checkedAt(), catalog.getUpdates().unreachable(), networked()));
    }

    /**
     * Sends the servers on the network and the connection to it.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     */
    public void showNetwork(Viewer viewer) {

        confirmations.abandon(viewer);

        if (catalog.getServerName() == null) {
            send(viewer, Outcomes.networkOff());
            return;
        }

        send(viewer, NetworkPage.network(catalog.ping(), peers(), catalog.getServerName(),
                catalog.getPlatform().version()));
    }

    /**
     * Sends the servers to open a screen on.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param screen the screen to reopen: list, history or trash
     * @param shown  the server the screen shows now, or null for this one
     */
    public void showSwitch(Viewer viewer, String screen, String shown) {

        confirmations.abandon(viewer);

        if (catalog.getServerName() == null) {
            send(viewer, Outcomes.networkOff());
            return;
        }

        String self = catalog.getServerName();
        send(viewer, NetworkPage.pick(peers(), self, shown == null ? self : shown, screen));
    }

    /**
     * Whether this server is on the network.
     */
    private boolean networked() {
        return catalog.getServerName() != null;
    }

    /**
     * Whether a {@code --server} value names another server.
     */
    public boolean isRemote(String server) {
        return server != null && !server.equals(catalog.getServerName());
    }

    /**
     * Another server as it last published itself. Tells the viewer when there is none.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @return the server, or null when the network is off or does not know it
     */
    public Peer peer(Viewer viewer, String server) {

        if (catalog.getServerName() == null) {
            send(viewer, Outcomes.networkOff());
            return null;
        }

        for (Peer peer : peers()) {
            if (peer.name().equals(server)) {
                return peer;
            }
        }

        send(viewer, Outcomes.unknownServer(server));
        return null;
    }

    /**
     * The servers on the network, or none when it cannot be read.
     */
    private List<Peer> peers() {

        try {
            return catalog.peers();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Sends a project's page.
     */
    public void showProject(Viewer viewer, String query) {

        confirmations.abandon(viewer);
        catalog.noticeLoaded();

        try {

            ModrinthProject project = lookup.projectFor(query);

            if (project == null) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            TrackedPlugin tracked = catalog.getTracking().byProjectId(project.id());

            ReleaseChannel channel = tracked != null ? tracked.channel() : lookup.defaultChannel();

            List<ModrinthVersion> compatible = lookup.compatibleOrEmpty(project.id());
            ModrinthVersion latest = Lookup.newestOn(compatible, channel);
            ModrinthVersion target = Projects.installTarget(compatible);
            InstalledJar untracked = tracked == null ? catalog.getLibrary().untrackedJar(project.id()) : null;

            // The dependencies of the build the page is about.
            ModrinthVersion described = tracked != null ? lookup.installedBuild(tracked)
                    : untracked != null ? lookup.identified(untracked) : target;

            send(viewer, InfoPage.project(project, lookup.author(project.id()), latest, target, tracked,
                    untracked, catalog.isSelf(tracked),
                    catalog.declared(described, DependencyType.REQUIRED),
                    catalog.declared(described, DependencyType.OPTIONAL),
                    catalog.platformLoaders(), catalog.gameVersions(), serversButton(project)));

        } catch (Exception e) {
            send(viewer, Outcomes.unreachable(Errors.rootMessage(e)));
        }
    }

    /**
     * Sends where a plugin is installed across the network.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     */
    public void showServers(Viewer viewer, String query, String from, int page) {

        confirmations.abandon(viewer);

        if (catalog.getServerName() == null) {
            send(viewer, Outcomes.networkOff());
            return;
        }

        try {

            ModrinthProject project = lookup.projectFor(query);

            if (project == null) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            TrackedPlugin local = catalog.getTracking().byProjectId(project.id());
            boolean update = local != null && !local.pendingLoad()
                    && catalog.updatesByProject().containsKey(project.id());

            send(viewer, ServersPage.servers(project, peers(), catalog.getServerName(), local, update,
                    catalog.isSelf(local), from, page));

        } catch (Exception e) {
            send(viewer, Outcomes.unreachable(Errors.rootMessage(e)));
        }
    }

    /**
     * The button to a plugin's servers, or null while there is no other server to show.
     */
    private Component serversButton(ModrinthProject project) {

        List<Peer> peers = peers();
        return peers.size() > 1 ? ServersPage.serversButton(project, peers, catalog.getServerName(),
                new Screen.InfoScreen(project.slug()).token()) : null;
    }

    public void runSearch(Viewer viewer, String query, int page) {
        chat.runAsync(viewer, () -> showSearch(viewer, query, page));
    }

    /**
     * Sends a page of search results.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     */
    private void showSearch(Viewer viewer, String query, int page) {

        confirmations.abandon(viewer);

        try {

            List<Peer> peers = peers();
            SearchResults results = peers.size() > 1
                    ? catalog.search(query, SearchPage.PAGE, (page - 1) * SearchPage.PAGE, peers)
                    : catalog.search(query, SearchPage.PAGE, (page - 1) * SearchPage.PAGE);

            // Text search does not match project ids. A one-word query that found nothing is tried as one.
            if (results.hits().isEmpty() && !query.contains(" ")) {
                showProject(viewer, query);
                return;
            }

            send(viewer, SearchPage.search(query, results, page, lookup.trackedProjectIds(),
                    peers.size() > 1 ? peers : List.of(), catalog.getServerName()));

        } catch (Exception e) {
            send(viewer, Outcomes.unreachable(Errors.rootMessage(e)));
        }
    }

}
