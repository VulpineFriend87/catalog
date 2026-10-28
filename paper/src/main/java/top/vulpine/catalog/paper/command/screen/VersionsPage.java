package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.modrinth.GameVersions;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.paper.command.Permitted;
import top.vulpine.catalog.paper.command.Screen;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static top.vulpine.catalog.paper.command.screen.widget.Format.*;
import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

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

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text(project.title(), BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text(incompatible ? "  newest" : "  newest for " + gameVersion, MUTED))
                .build());

        if (incompatible) {
            out.add(Component.text(INDENT + "Any Minecraft version. Most will not load.", PENDING));
        }

        out.add(Component.empty());

        if (newest.isEmpty()) {
            out.add(Component.text(INDENT + (incompatible ? "This project has published nothing"
                    : "No build for this server"), MUTED));
        }

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
     * Every build of a project, newest first.
     *
     * @param untracked    whether the project's jar is in the plugins folder but untracked
     * @param incompatible whether builds for other Minecraft versions are included
     * @param from         the trail the newest builds were opened from
     */
    public static List<Component> allVersions(ModrinthProject project, List<ModrinthVersion> versions,
                                              TrackedPlugin installed, boolean untracked,
                                              String gameVersion, GameVersions names,
                                              boolean incompatible, int page, String from) {

        int pages = Math.max((versions.size() + EVERY - 1) / EVERY, 1);
        int shown = Math.min(Math.max(page, 1), pages);
        int first = (shown - 1) * EVERY;

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text(project.title(), BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  " + versions.size() + " builds"
                        + (incompatible ? "" : " for " + gameVersion), MUTED))
                .build());

        if (incompatible) {
            out.add(Component.text(INDENT + "Any Minecraft version. Most will not load.", PENDING));
        }

        out.add(Component.empty());

        if (versions.isEmpty()) {
            out.add(Component.text(INDENT + (incompatible ? "This project has published nothing"
                    : "No build for this server"), MUTED));
        }

        for (ModrinthVersion version : versions.subList(first, Math.min(first + EVERY, versions.size()))) {
            out.add(versionRow(project, version, false, installed, untracked, gameVersion, names, from));
        }

        out.add(Component.empty());

        TextComponent.Builder footer = line()
                .append(Component.text(INDENT + "page ", MUTED))
                .append(Component.text(shown, TEXT))
                .append(Component.text(" of " + pages + "  ", MUTED));

        if (shown > 1) {
            footer.append(button("Newer", from(allPage(project, incompatible, shown - 1), from), MUTED,
                    "Page " + (shown - 1))).append(Component.space());
        }

        if (shown < pages) {
            footer.append(button("Older", from(allPage(project, incompatible, shown + 1), from), MUTED,
                    "Page " + (shown + 1))).append(Component.space());
        }

        footer.append(button("Newest", from("/catalog versions " + project.slug()
                + (incompatible ? " --incompatible" : ""), from), MUTED, "The newest release, beta and alpha"));

        out.add(footer.build());

        return out;
    }

    private static String allPage(ModrinthProject project, boolean incompatible, int page) {
        return "/catalog versions " + project.slug() + " --all"
                + (incompatible ? " --incompatible" : "") + (page > 1 ? " --page " + page : "");
    }

    /** How many builds one page of the full list shows. */
    private static final int EVERY = 12;

    /**
     * @param channelFirst whether the channel leads the row, as in the newest of each channel
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

        // While a build is waiting, the installed row is the way back out of it.
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
