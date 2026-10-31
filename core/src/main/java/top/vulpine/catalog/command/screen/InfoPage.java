package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.GameVersions;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.UpdateChecker;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * One project's page.
 */
public final class InfoPage {

    private InfoPage() {
    }

    /**
     * @param author        who to credit, or null when the team could not be fetched
     * @param latest        the newest build on the channel being followed, or null
     * @param installTarget what the install button fetches, or null when nothing runs here
     * @param installed     the plugin as tracked, or null when it is not installed
     * @param untracked     the project's jar when it is in the plugins folder but untracked
     * @param self          whether this is Catalog's own page
     * @param loaders       the loaders this server can use
     * @param servers       the button to the plugin's servers, or null while the network is off
     */
    public static List<Component> project(ModrinthProject project, String author, ModrinthVersion latest,
                                          ModrinthVersion installTarget, TrackedPlugin installed,
                                          InstalledJar untracked, boolean self,
                                          List<DependencyResolver.Requirement> requirements,
                                          List<DependencyResolver.Requirement> optionals,
                                          List<String> loaders, GameVersions names, Component servers) {

        ModrinthVersion offered = latest != null ? latest : installTarget;

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title(project.title(), BRAND))
                .append(status(installed, untracked, UpdateChecker.offers(installed, latest)))
                .hoverEvent(HoverEvent.showText(identity(project, installed, offered)))
                .build());

        if (project.description() != null && !project.description().isBlank()) {
            out.add(Component.text(INDENT + project.description(), TEXT));
        }

        out.add(Component.empty());

        out.add(line()
                .append(Component.text(INDENT))
                .append(author(author))
                .append(Component.text(compact(project.downloads()), BRAND))
                .append(Component.text(" downloads", MUTED))
                .append(Component.text(" · ", MUTED))
                .append(Component.text(compact(project.followers()), BRAND))
                .append(Component.text(" followers", MUTED))
                .build());

        out.add(field("Minecraft", minecraft(project, offered, loaders, names)));

        if (project.categories() != null && !project.categories().isEmpty()) {
            out.add(field("Tags", Component.text(String.join(", ", project.categories()), MUTED)));
        }

        if (!requirements.isEmpty()) {
            out.add(field("Needs", requirements(requirements, PENDING, project.slug())));
        }

        if (!optionals.isEmpty()) {
            out.add(field("Optional", requirements(optionals, MUTED, project.slug())));
        }

        out.add(Component.empty());

        Component actions;

        if (installed == null && untracked != null) {
            actions = untrackedActions(project, untracked);
        } else if (installed == null) {
            actions = installActions(project, installTarget);
        } else {
            actions = installedActions(project, installed, latest, self);
        }

        out.add(servers == null ? actions
                : line().append(actions).append(Component.text("  ")).append(servers).build());

        return out;
    }

    private static Component status(TrackedPlugin installed, InstalledJar untracked, boolean updateAvailable) {

        if (installed == null) {
            return Component.text(untracked != null ? "  untracked" : "  not installed", MUTED);
        }

        if (installed.pendingRestart()) {
            return Component.text("  update downloaded", PENDING);
        }

        if (installed.pendingLoad()) {
            return Component.text("  loads on restart", PENDING);
        }

        if (updateAvailable) {
            return Component.text("  update available", BRAND);
        }

        if (installed.isPinned()) {
            return Component.text("  held", MUTED);
        }

        return Component.text("  installed", DONE);
    }

    private static Component identity(ModrinthProject project, TrackedPlugin installed, ModrinthVersion offered) {

        TextComponent.Builder hover = Component.text()
                .append(Component.text(project.title(), TEXT))
                .append(Component.newline())
                .append(Component.text(project.slug() + "  " + project.id(), MUTED));

        if (project.license() != null && project.license().id() != null) {
            hover.append(Component.newline())
                    .append(Component.text("licence  ", MUTED))
                    .append(Component.text(project.license().name() == null
                            ? project.license().id() : project.license().name(), TEXT));
        }

        if (installed != null) {

            hover.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("installed  ", MUTED))
                    .append(Component.text(String.valueOf(installed.versionNumber()), TEXT))
                    .append(Component.newline())
                    .append(Component.text("channel  ", MUTED))
                    .append(Component.text(installed.channel().apiName(), TEXT))
                    .append(Component.newline())
                    .append(Component.text("file  ", MUTED))
                    .append(Component.text(String.valueOf(installed.fileName()), TEXT));

            if (installed.isPinned()) {
                hover.append(Component.newline())
                        .append(Component.text("held at this version", PENDING));
            }
        }

        if (offered != null) {
            // The newest build that runs here on the followed channel.
            hover.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("newest here  ", MUTED))
                    .append(Component.text(offered.versionNumber(), TEXT))
                    .append(Component.text(offered.versionType() == null ? ""
                            : "  " + offered.versionType().apiName(), MUTED))
                    .append(Component.newline())
                    .append(Component.text("published  ", MUTED))
                    .append(Component.text(ago(offered.datePublished()), TEXT));
        }

        return hover.build();
    }

    private static Component author(String author) {

        if (author == null || author.isBlank()) {
            return Component.empty();
        }

        return Component.text("by ", MUTED)
                .append(Component.text(author, TEXT))
                .append(Component.text(" · ", MUTED));
    }

    /**
     * The versions of the build this page offers, or the whole project's in grey when no build runs
     * here. The hover lists the loaders.
     */
    private static Component minecraft(ModrinthProject project, ModrinthVersion offered, List<String> loaders,
                                       GameVersions names) {

        String versions = names.describe(offered != null ? offered.gameVersions() : project.gameVersions());

        return Component.text(versions, offered != null ? TEXT : MUTED)
                .hoverEvent(HoverEvent.showText(Component.text("Loaders  ", MUTED)
                        .append(loaders(project, loaders))));
    }

    /**
     * @param here the loaders this server can use
     */
    private static Component loaders(ModrinthProject project, List<String> here) {

        List<String> declared = project.loaders();

        if (declared == null || declared.isEmpty()) {
            return Component.text("unknown", MUTED);
        }

        TextComponent.Builder out = Component.text();
        boolean first = true;

        for (String loader : declared) {

            if (!first) {
                out.append(Component.text(", ", MUTED));
            }

            out.append(Component.text(loader, here.contains(loader) ? TEXT : MUTED));
            first = false;
        }

        return out.build();
    }

    /**
     * The declared projects. The ones not installed open their own page.
     *
     * @param missing the colour of the ones not installed
     * @param key     the slug of the page they are listed on
     */
    private static Component requirements(List<DependencyResolver.Requirement> requirements,
                                          TextColor missing, String key) {

        String here = new Screen.InfoScreen(key).token();

        TextComponent.Builder out = Component.text();
        boolean first = true;

        for (DependencyResolver.Requirement requirement : requirements) {

            if (!first) {
                out.append(Component.text(", ", MUTED));
            }

            if (requirement.installed() || requirement.slug() == null) {
                out.append(Component.text(requirement.name(), requirement.installed() ? TEXT : missing)
                        .hoverEvent(HoverEvent.showText(Component.text(
                                requirement.installed() ? "installed" : "not installed",
                                requirement.installed() ? MUTED : missing))));
            } else {

                String command = from("/catalog info " + requirement.slug(), here);

                out.append(Permitted.link(Component.text(requirement.name(), missing), command,
                        Component.text("not installed", missing)
                                .append(Component.newline()).append(Component.newline())
                                .append(explain("Open " + requirement.name(), command)), null));
            }

            first = false;
        }

        return out.build();
    }

    private static Component untrackedActions(ModrinthProject project, InstalledJar untracked) {

        String key = project.slug();
        String here = new Screen.InfoScreen(key).token();

        List<Component> row = new ArrayList<>();

        row.add(button("Track", from("/catalog track " + quoted(untracked.fileName()), here),
                BRAND, "Track " + project.title()));

        row.add(button("Versions", from("/catalog versions " + key, here), MUTED,
                "The newest release, beta and alpha for this server"));

        return buttons(row);
    }

    private static Component installActions(ModrinthProject project, ModrinthVersion installTarget) {

        if (installTarget == null) {
            return line().append(Component.text(INDENT))
                    .append(Component.text("No build for this server", MUTED))
                    .build();
        }

        String key = project.slug();
        String here = new Screen.InfoScreen(key).token();

        List<Component> row = new ArrayList<>();

        row.add(button("Install", from("/catalog install " + key, here), BRAND,
                "Install " + installTarget.versionNumber()));

        String versions = from("/catalog versions " + key, here);

        row.add(Permitted.unless("/catalog install " + key,
                button("Versions", versions, MUTED,
                        "Choose a build: the newest release, beta and alpha for this server"),
                button("Versions", versions, MUTED, "The newest release, beta and alpha for this server")));

        return buttons(row);
    }

    private static Component installedActions(ModrinthProject project, TrackedPlugin installed,
                                              ModrinthVersion latest, boolean self) {

        String key = project.slug();
        String here = new Screen.InfoScreen(key).token();

        List<Component> row = new ArrayList<>();

        if (installed.pendingRestart()) {
            row.add(button("Cancel update", from("/catalog cancel " + key, here), PENDING,
                    "Leave " + installed.versionNumber() + " in place"));
        }

        if (UpdateChecker.offers(installed, latest) && !installed.pendingLoad()) {
            row.add(button("Update", from("/catalog update " + key, here), BRAND,
                    "Download " + latest.versionNumber() + " for the next restart" + replaces(installed)));
        }

        String versions = from("/catalog versions " + key, here);

        row.add(Permitted.unless("/catalog install " + key,
                button("Switch", versions, MUTED, "Choose a different version"),
                button("Versions", versions, MUTED, "The newest release, beta and alpha for this server")));

        row.add(button("Settings", from("/catalog settings " + key, here), MUTED,
                "Open the settings page"));

        row.add(self
                ? Component.empty()
                : button("Remove", from("/catalog uninstall " + key, here), DANGER,
                        "Move to the trash"));

        return buttons(row);
    }

}
