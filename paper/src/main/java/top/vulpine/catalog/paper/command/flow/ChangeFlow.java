package top.vulpine.catalog.paper.command.flow;

import net.kyori.adventure.text.Component;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.history.Event;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.install.Installer;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.paper.command.Change;
import top.vulpine.catalog.paper.command.Press;
import top.vulpine.catalog.paper.command.Screen;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.paper.command.screen.widget.Format;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.model.UpdateCandidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * Installing, updating and switching builds, with the question asked first.
 */
public final class ChangeFlow {

    private final Catalog catalog;

    private final Chat chat;

    private final Confirmations confirmations;

    private final Lookup lookup;

    private final Screens screens;

    private final Redraw redraw;

    public ChangeFlow(Catalog catalog, Chat chat, Confirmations confirmations, Lookup lookup, Screens screens, Redraw redraw) {
        this.catalog = catalog;
        this.chat = chat;
        this.confirmations = confirmations;
        this.lookup = lookup;
        this.screens = screens;
        this.redraw = redraw;
    }

    /**
     * Installs a plugin, or moves an installed one to another build.
     *
     * @param incompatible whether builds for other Minecraft versions are accepted
     * @param data         what the button carried, or null when the command was typed
     */
    public void runInstall(Viewer viewer, String query, String named, boolean incompatible,
                            String data) {

        try {

            ModrinthProject project = catalog.project(query);

            if (project == null) {
                send(viewer, Outcomes.unknownPlugin(query));
                return;
            }

            TrackedPlugin tracked = catalog.getTracking().byProjectId(project.id());
            InstalledJar untracked = tracked == null ? catalog.getLibrary().untrackedJar(project.id()) : null;

            if (untracked != null) {
                send(viewer, Outcomes.installedUntracked(project.title(), untracked.fileName()));
                return;
            }

            if (tracked != null && named == null) {
                send(viewer, Outcomes.alreadyInstalled(tracked));
                return;
            }

            if (tracked != null && tracked.isPinned()) {
                send(viewer, Outcomes.stillHeld(tracked.displayName()));
                return;
            }

            ModrinthVersion version = lookup.build(project, named, incompatible);

            if (version == null) {
                send(viewer, named == null
                        ? Outcomes.noBuild(project.title())
                        : Outcomes.noVersion(project.title(), named));
                return;
            }

            String notFor = runsHere(version) ? null : catalog.gameVersion();

            ReleaseChannel follow = version.versionType() == null
                    ? lookup.defaultChannel() : version.versionType();

            DependencyResolver.Resolution resolution = catalog.dependenciesOf(version);
            String here = new Screen.ChangeScreen("install", project.slug(), version.id()).token();

            boolean asks = tracked != null || notFor != null || needsAnswer(resolution)
                    || Press.redrawing(data, here);

            if (asks && !confirmations.confirmed(viewer, "install:" + project.id() + ":" + version.id(), data)) {

                Change change = tracked == null ? Change.INSTALL
                        : isOlder(version, tracked) ? Change.ROLL_BACK : Change.SWITCH;

                screens.showChange(viewer, change, project, tracked, version, notFor, resolution, data, here,
                        "/catalog install " + project.slug() + " " + version.id()
                                + (notFor != null ? " --incompatible" : ""));
                return;
            }

            List<Installer.Pending> needed = Press.alone(data)
                    ? List.of() : pendingFor(resolution.missing(), project);

            if (tracked != null) {

                if (!needed.isEmpty()) {
                    catalog.install(needed, viewer.name());
                }

                boolean rollback = isOlder(version, tracked);

                catalog.setChannel(tracked, follow, viewer.name());
                catalog.stage(tracked, version, viewer.name(),
                        rollback ? Event.ROLLED_BACK : Event.SWITCHED);

                redraw.screen(viewer, Press.screenOf(data));
                send(viewer, Outcomes.staged(tracked.displayName(), version.versionNumber()));

                // Keeps the next check from downloading the newer build again.
                if (rollback && tracked.autoUpdate()) {
                    catalog.setAutoUpdate(tracked, false, viewer.name());
                    send(viewer, Outcomes.autoUpdateTurnedOff(tracked.versionNumber(), Format.key(tracked)));
                }

                return;
            }

            List<Installer.Pending> pending = new ArrayList<>(needed);
            pending.add(new Installer.Pending(project, version, follow, true, null));
            catalog.install(pending, viewer.name());

            redraw.screen(viewer, Press.screenOf(data));
            send(viewer, pending.size() == 1
                    ? Outcomes.installed(project.title(), version.versionNumber())
                    : Outcomes.installedWith(project.title(), version.versionNumber(),
                            needed.stream().map(one -> one.project().title()).toList()));

        } catch (Exception e) {
            send(viewer, Outcomes.failed(Chat.rootMessage(e)));
        }
    }

    /**
     * Whether a build has missing requirements or conflicts.
     */
    private static boolean needsAnswer(DependencyResolver.Resolution resolution) {
        return !resolution.missing().isEmpty() || !resolution.conflicts().isEmpty();
    }

    /**
     * Whether a build declares the Minecraft version this server runs.
     */
    private boolean runsHere(ModrinthVersion version) {
        return version.gameVersions() != null && version.gameVersions().contains(catalog.gameVersion());
    }

    /**
     * Downloads the update for one plugin.
     *
     * @param data what the button carried, or null when the command was typed
     */
    public void runUpdate(Viewer viewer, TrackedPlugin tracked, String data) {

        try {

            UpdateCandidate candidate = candidateFor(tracked);

            if (candidate == null) {
                send(viewer, Outcomes.noUpdate(tracked.displayName()));
                return;
            }

            ModrinthProject project = catalog.project(tracked.projectId());
            DependencyResolver.Resolution resolution = catalog.dependenciesOf(candidate.version());

            // Asks when the new build has missing requirements.
            if (project != null) {

                String here = new Screen.ChangeScreen("update", project.slug(), null).token();

                if ((needsAnswer(resolution) || Press.redrawing(data, here)) && !confirmations.confirmed(viewer,
                        "update:" + project.id() + ":" + candidate.version().id(), data)) {

                    screens.showChange(viewer, Change.UPDATE, project, tracked, candidate.version(), null,
                            resolution, data, here,
                            "/catalog update " + project.slug());
                    return;
                }
            }

            if (!Press.alone(data) && !resolution.missing().isEmpty()) {
                catalog.install(pendingFor(resolution.missing(), project), viewer.name());
            }

            catalog.stage(candidate, viewer.name());

            redraw.screen(viewer, Press.screenOf(data));
            send(viewer, Outcomes.staged(tracked.displayName(), candidate.to()));

        } catch (Exception e) {
            send(viewer, Outcomes.failed(Chat.rootMessage(e)));
        }
    }

    public void updateAll(Viewer viewer, String data) {

        chat.runAsync(viewer, () -> {

            try {

                catalog.refreshUpdates();
                List<UpdateCandidate> candidates = catalog.openUpdates();

                if (candidates.isEmpty()) {
                    send(viewer, Outcomes.upToDate());
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

                        catalog.stage(candidate, viewer.name());
                        staged++;
                    } catch (Exception e) {
                        // One plugin failing is not a reason to abandon the rest of the queue, and
                        // the reason is worth more after the list than buried above it.
                        failures.add(Outcomes.stageFailed(candidate.plugin().displayName(),
                                Chat.rootMessage(e)));
                    }
                }

                redraw.screen(viewer, Press.screenOf(data));

                if (staged > 0) {
                    send(viewer, Outcomes.stagedAll(staged));
                }

                for (Component failure : failures) {
                    send(viewer, failure);
                }

            } catch (Exception e) {
                send(viewer, Outcomes.failed(Chat.rootMessage(e)));
            }
        });
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
                    ? lookup.defaultChannel() : requirement.available().versionType();

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

    /**
     * Whether a build is older than what is installed.
     */
    private static boolean isOlder(ModrinthVersion version, TrackedPlugin tracked) {

        return tracked.datePublished() != null && version.datePublished() != null
                && version.datePublished().isBefore(tracked.datePublished());
    }

}
