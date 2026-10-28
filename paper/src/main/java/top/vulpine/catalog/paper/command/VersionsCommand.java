package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
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
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Chat;
import top.vulpine.catalog.paper.command.flow.Confirmations;
import top.vulpine.catalog.paper.command.flow.Lookup;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.paper.command.screen.VersionsPage;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

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
    public void versions(CommandSender sender,
                         @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                         @Switch("all") boolean all,
                         @Switch("incompatible") boolean incompatible,
                         @Flag("page") @Default("1") int page) {

        String data = context.take(sender);

        confirmations.abandon(sender);

        chat.runAsync(sender, () -> {

            try {

                TrackedPlugin tracked = lookup.resolve(query);
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
                            Press.screenOf(data)));
                    return;
                }

                send(sender, VersionsPage.versions(project, catalog.gameVersion(), catalog.gameVersions(),
                        newestOfEachChannel(builds), installed, untracked, incompatible,
                        Press.screenOf(data)));

            } catch (Exception e) {
                send(sender, Outcomes.unreachable(Chat.rootMessage(e)));
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

}
