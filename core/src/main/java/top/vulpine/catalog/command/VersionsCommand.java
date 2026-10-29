package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.annotation.Switch;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.Errors;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.command.screen.VersionsPage;
import top.vulpine.catalog.command.service.Chat;
import top.vulpine.catalog.command.service.Confirmations;
import top.vulpine.catalog.command.service.Lookup;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * {@code /catalog versions}.
 */
public final class VersionsCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Chat chat;

    private final Confirmations confirmations;

    private final Lookup lookup;

    public VersionsCommand(Catalog catalog, ClickContext context, Chat chat, Confirmations confirmations, Lookup lookup) {
        this.catalog = catalog;
        this.context = context;
        this.chat = chat;
        this.confirmations = confirmations;
        this.lookup = lookup;
    }

    @Subcommand("versions")
    @Description("The newest release, beta and alpha for this server")
    @RequiresPermission("command.info")
    public void versions(Viewer viewer,
                         @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                         @Switch("all") boolean all,
                         @Switch("incompatible") boolean incompatible,
                         @Flag("page") @Default("1") int page) {

        String data = context.take(viewer);

        confirmations.abandon(viewer);

        chat.runAsync(viewer, () -> {

            try {

                TrackedPlugin tracked = lookup.resolve(query);
                ModrinthProject project = catalog.project(tracked != null
                        ? tracked.projectId() : query);

                if (project == null) {
                    send(viewer, Outcomes.unknownPlugin(query));
                    return;
                }

                TrackedPlugin installed = catalog.getTracking().byProjectId(project.id());
                boolean untracked = installed == null
                        && catalog.getLibrary().untrackedJar(project.id()) != null;
                List<ModrinthVersion> builds = incompatible ? catalog.allVersions(project.id())
                        : catalog.compatibleVersions(project.id());

                if (all) {
                    send(viewer, VersionsPage.allVersions(project, builds, installed, untracked,
                            catalog.gameVersion(), catalog.gameVersions(), incompatible, page,
                            Press.screenOf(data)));
                    return;
                }

                send(viewer, VersionsPage.versions(project, catalog.gameVersion(), catalog.gameVersions(),
                        newestOfEachChannel(builds), installed, untracked, incompatible,
                        Press.screenOf(data)));

            } catch (Exception e) {
                send(viewer, Outcomes.unreachable(Errors.rootMessage(e)));
            }
        });
    }

    /**
     * The newest build of each channel.
     *
     * @param compatible the compatible builds sorted newest first
     * @return the newest build of each channel that has one
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

}
