package top.vulpine.catalog.command.service;

import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.argument.ListFilter;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.command.screen.Change;
import top.vulpine.catalog.command.screen.ChangePage;
import top.vulpine.catalog.command.screen.DependenciesPage;
import top.vulpine.catalog.command.screen.InfoPage;
import top.vulpine.catalog.command.screen.ListPage;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.command.screen.SearchPage;
import top.vulpine.catalog.command.screen.SettingsPage;
import top.vulpine.catalog.command.screen.TrashPage;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchResults;
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
 * Builds a screen from the catalog and sends it.
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
     * Draws what one build declares it needs.
     */
    public void showDependencies(Viewer viewer, String query, String wanted, String from) {

        try {

            ModrinthProject project = lookup.projectFor(query);

            if (project == null) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            ModrinthVersion version = lookup.build(project, wanted, wanted != null);

            if (version == null) {
                send(viewer, wanted == null
                        ? Outcomes.noBuild(project.title())
                        : Outcomes.noVersion(project.title(), wanted));
                return;
            }

            DependencyResolver.Resolution resolution = catalog.dependenciesOf(version);

            List<DependencyResolver.Requirement> declared = new ArrayList<>(resolution.required());
            declared.addAll(resolution.optional());
            declared.addAll(resolution.conflicts());

            send(viewer, DependenciesPage.dependencies(project, version, catalog.named(declared), from));

        } catch (Exception e) {
            send(viewer, Outcomes.failed(Chat.rootMessage(e)));
        }
    }

    /**
     * Draws the screen that asks before an install, update or switch.
     *
     * @param installed the plugin as tracked now, or null for a first install
     * @param here      this screen, so a dependency installed from it comes back to it
     * @param command   what confirming runs
     */
    public void showChange(Viewer viewer, Change change, ModrinthProject project,
                            TrackedPlugin installed, ModrinthVersion version, String notFor,
                            DependencyResolver.Resolution resolution, String data, String here,
                            String command) {

        List<DependencyResolver.Requirement> needs = new ArrayList<>(resolution.missing());
        needs.addAll(resolution.conflicts());

        Press asked = Press.of(data);

        // Its buttons lead to where it was asked from, not back here.
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

    public void showTrash(Viewer viewer, int page) {
        confirmations.abandon(viewer);
        send(viewer, TrashPage.trash(catalog.trashed(),
                catalog.getConfiguration().get().trash.retentionDays, page));
    }

    /**
     * Renders one plugin's settings.
     */
    public void showSettings(Viewer viewer, TrackedPlugin tracked, String from) {
        confirmations.abandon(viewer);
        send(viewer, SettingsPage.settings(tracked, catalog.defaultSoakMinutes(), from));
    }

    /**
     * Renders the plugin list.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param refresh whether to ask Modrinth again first
     */
    public void showList(Viewer viewer, boolean refresh, Set<ListFilter> filters) {

        confirmations.abandon(viewer);

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
                catalog.getUpdates().checkedAt(), catalog.getUpdates().unreachable()));
    }

    /**
     * Builds and sends the project page.
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

            send(viewer, InfoPage.project(project, lookup.author(project.id()), latest,
                    Projects.installTarget(compatible), tracked,
                    tracked == null ? catalog.getLibrary().untrackedJar(project.id()) : null,
                    catalog.isSelf(tracked),
                    catalog.declared(latest, DependencyType.REQUIRED),
                    catalog.declared(latest, DependencyType.OPTIONAL),
                    catalog.platformLoaders(), catalog.gameVersions()));

        } catch (Exception e) {
            send(viewer, Outcomes.unreachable(Chat.rootMessage(e)));
        }
    }

    public void runSearch(Viewer viewer, String query, int page) {
        chat.runAsync(viewer, () -> showSearch(viewer, query, page));
    }

    /**
     * Renders a page of search results.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     */
    private void showSearch(Viewer viewer, String query, int page) {

        confirmations.abandon(viewer);

        try {

            SearchResults results = catalog.search(query, SearchPage.PAGE,
                    (page - 1) * SearchPage.PAGE);

            // Text search does not match project ids, so a query that found nothing and could
            // be one is worth one direct lookup before giving up.
            if (results.hits().isEmpty() && !query.contains(" ")) {
                showProject(viewer, query);
                return;
            }

            send(viewer, SearchPage.search(query, results, page, lookup.trackedProjectIds()));

        } catch (Exception e) {
            send(viewer, Outcomes.unreachable(Chat.rootMessage(e)));
        }
    }

}
