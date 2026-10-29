package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import top.vulpine.catalog.command.screen.widget.Pager;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.modrinth.GameVersions;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The builds of a project.
 */
public final class VersionsPage {

    private VersionsPage() {
    }

    /**
     * The newest build of each channel to pick one from.
     *
     * @param untracked    whether the project's jar is in the plugins folder but untracked
     * @param incompatible whether builds for other Minecraft versions are included
     */
    public static List<Component> versions(ModrinthProject project, String gameVersion,
                                           GameVersions names,
                                           Map<ReleaseChannel, ModrinthVersion> newest,
                                           TrackedPlugin installed, boolean untracked,
                                           boolean incompatible, String from) {

        List<Component> out = head(project, incompatible ? "newest" : "newest for " + gameVersion,
                incompatible, newest.isEmpty());

        for (ReleaseChannel channel : ReleaseChannel.values()) {

            ModrinthVersion version = newest.get(channel);

            if (version != null) {
                out.add(versionRow(project, version, true, installed, untracked, gameVersion, names,
                        from));
            }
        }

        out.add(Component.empty());

        out.add(line()
                .append(Component.text(INDENT))
                .append(backOrInfo(from, project.slug()))
                .append(Component.space())
                .append(button("All builds", from(allPage(project, incompatible, 1), from), MUTED,
                        "Every build, newest first"))
                .build());

        return out;
    }

    /**
     * Every build of a project. Newest first.
     *
     * @param untracked    whether the project's jar is in the plugins folder but untracked
     * @param incompatible whether builds for other Minecraft versions are included
     * @param from         the trail the newest builds were opened from
     */
    public static List<Component> allVersions(ModrinthProject project, List<ModrinthVersion> versions,
                                              TrackedPlugin installed, boolean untracked,
                                              String gameVersion, GameVersions names,
                                              boolean incompatible, int page, String from) {

        Pager pager = Pager.of(versions.size(), EVERY, page);

        List<Component> out = head(project,
                versions.size() + " builds" + (incompatible ? "" : " for " + gameVersion),
                incompatible, versions.isEmpty());

        for (ModrinthVersion version : pager.slice(versions)) {
            out.add(versionRow(project, version, false, installed, untracked, gameVersion, names, from));
        }

        out.add(Component.empty());

        String newest = from("/catalog versions " + project.slug()
                + (incompatible ? " --incompatible" : ""), from);

        out.add(pager.footer(List.of(button("Back", newest, MUTED, "The newest release, beta and alpha")),
                "Newer", "Older", shown -> from(allPage(project, incompatible, shown), from)));

        return out;
    }

    /**
     * The title of a builds screen and the notices under it.
     *
     * @param empty whether there are no builds to list
     */
    private static List<Component> head(ModrinthProject project, String subtitle, boolean incompatible,
                                        boolean empty) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title(project.title(), BRAND))
                .append(Component.text("  " + subtitle, MUTED))
                .build());

        if (incompatible) {
            out.add(Component.text(INDENT + "Any Minecraft version. Most will not load.", PENDING));
        }

        out.add(Component.empty());

        if (empty) {
            out.add(Component.text(INDENT + (incompatible ? "This project has published nothing"
                    : "No build for this server"), MUTED));
        }

        return out;
    }

    private static String allPage(ModrinthProject project, boolean incompatible, int page) {
        return "/catalog versions " + project.slug() + " --all"
                + (incompatible ? " --incompatible" : "") + (page > 1 ? " --page " + page : "");
    }

    /** How many builds one page of the full list shows. */
    private static final int EVERY = 12;

    /**
     * @param channelFirst whether the row starts with the channel
     */
    private static Component versionRow(ModrinthProject project, ModrinthVersion version,
                                        boolean channelFirst, TrackedPlugin installed,
                                        boolean untracked, String gameVersion, GameVersions names,
                                        String from) {

        boolean current = installed != null && version.id().equals(installed.versionId());
        boolean staged = installed != null && version.id().equals(installed.stagedVersionId())
                && installed.pendingRestart();
        boolean waiting = installed != null && installed.pendingRestart();
        boolean runs = version.gameVersions() != null && version.gameVersions().contains(gameVersion);

        Component info = Component.text(version.versionNumber(), TEXT)
                .append(Component.newline())
                .append(Component.text("published  ", MUTED))
                .append(Component.text(ago(version.datePublished()), TEXT))
                .append(Component.newline())
                .append(Component.text("for  ", MUTED))
                .append(Component.text(names.describe(version.gameVersions()), TEXT));

        Component hover = info
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text(staged ? "Waiting for a restart"
                        : current && waiting ? "Cancel the downloaded update and stay here"
                        : current ? "Already installed"
                        : untracked ? "Installed, untracked"
                        : !runs ? "Use it anyway. This build does not declare " + gameVersion
                        : (installed == null ? "Install this build" : "Switch to this build")
                                + (version.versionType() == null ? ""
                                : " and follow the " + version.versionType().apiName() + " channel"),
                        TEXT));

        Component number = Component.text(version.versionNumber(),
                current ? MUTED : runs ? TEXT : PENDING);

        TextComponent.Builder row = line().append(Component.text(INDENT));

        if (channelFirst) {
            row.append(channel(version.versionType())).append(Component.text("  ")).append(number);
        } else {
            row.append(number).append(Component.text("  ")).append(channel(version.versionType()));
        }

        if (staged) {
            row.append(Component.text("  restart", PENDING));
        } else if (current) {
            row.append(Component.text("  installed", DONE));
        }

        // Choosing a build returns to the screen the list was opened from.
        String here = from != null ? from : new Screen.InfoScreen(project.slug()).token();

        // While a build is waiting the installed row cancels it.
        String command = staged ? null
                : current && waiting ? from("/catalog cancel " + project.slug(), here)
                : current || untracked ? null
                : from("/catalog install " + project.slug() + " " + version.id()
                        + (runs ? "" : " --incompatible"), here);

        if (command == null) {
            return row.hoverEvent(HoverEvent.showText(hover))
                    .clickEvent(ClickEvent.suggestCommand("/catalog info " + project.slug()))
                    .build();
        }

        return Permitted.link(row.build(), command, hover, info);
    }

    public static Component channel(ReleaseChannel channel) {

        if (channel == null) {
            return Component.empty();
        }

        return Component.text(channel.apiName(), switch (channel) {
            case RELEASE -> DONE;
            case BETA -> PENDING;
            case ALPHA -> DANGER;
        });
    }

}
