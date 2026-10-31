package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.command.screen.widget.Pager;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.network.model.Inventory;
import top.vulpine.catalog.network.model.Peer;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * Where a plugin is installed across the network.
 */
public final class ServersPage {

    /** How many servers one page shows. */
    private static final int SERVERS = 12;

    private ServersPage() {
    }

    /**
     * The button on a plugin's info page. The hover says where it is installed.
     *
     * @param self the name of this server
     * @param here the trail of the info page
     */
    public static Component serversButton(ModrinthProject project, List<Peer> peers, String self,
                                          String here) {

        TextComponent.Builder hover = Component.text()
                .append(Component.text("On " + installedOn(project, peers) + " of " + peers.size()
                        + " servers", TEXT));

        for (Peer peer : ordered(peers, self)) {

            Inventory.Plugin plugin = find(peer, project);

            hover.append(Component.newline())
                    .append(Component.text("  " + peer.name() + "  ", TEXT))
                    .append(!peer.online() ? Component.text("offline", MUTED)
                            : plugin == null ? Component.text("not installed", MUTED)
                            : Component.text(plugin.version(), MUTED))
                    .append(plugin != null && plugin.update() != null
                            ? Component.text("  ↑ " + plugin.update(), BRAND) : Component.empty());
        }

        String command = from("/catalog servers " + project.slug(), here);

        hover.append(Component.newline()).append(Component.newline())
                .append(explain("Manage it on each server", command));

        return button("Servers", command, BRAND, "").hoverEvent(HoverEvent.showText(hover.build()));
    }

    /**
     * One row per server with what can be done to the plugin there.
     *
     * @param self      the name of this server
     * @param local     the plugin as tracked on this server, or null when it is not installed here
     * @param update    whether this server offers an update for it
     * @param ownPlugin whether the plugin is Catalog itself
     * @param from      the trail the tab was opened from, or null when the command was typed
     */
    public static List<Component> servers(ModrinthProject project, List<Peer> peers, String self,
                                          TrackedPlugin local, boolean update, boolean ownPlugin,
                                          String from, int page) {

        String key = project.slug();
        String here = Trail.within(new Screen.ServersScreen(key).token(), from);
        List<Peer> ordered = ordered(peers, self);
        Pager pager = Pager.of(ordered.size(), SERVERS, page);

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title(project.title(), BRAND))
                .append(Component.text("  on " + installedOn(project, peers) + " of " + peers.size()
                        + " servers", MUTED))
                .build());

        out.add(Component.empty());

        for (Peer peer : pager.slice(ordered)) {
            out.add(peer.name().equals(self) ? localRow(project, peer, local, update, ownPlugin, here)
                    : remoteRow(project, peer));
        }

        out.add(Component.empty());
        out.add(pager.footer(List.of(backOrInfo(from, key)), "Previous", "Next",
                shown -> from("/catalog servers " + key + " --page " + shown, from)));

        return out;
    }

    private static Component localRow(ModrinthProject project, Peer peer, TrackedPlugin local,
                                      boolean update, boolean ownPlugin, String here) {

        String key = project.slug();

        if (local != null) {

            List<Component> actions = new ArrayList<>();

            if (update) {
                actions.add(button("Update", from("/catalog update " + key, here), BRAND,
                        "Download it for the next restart"));
            }

            actions.add(button("Switch", from("/catalog versions " + key, here), MUTED,
                    "Choose a different version"));
            actions.add(button("Settings", from("/catalog settings " + key, here), MUTED,
                    "Open the settings page"));

            if (!ownPlugin) {
                actions.add(button("Remove", from("/catalog uninstall " + key, here), DANGER,
                        "Move to the trash"));
            }

            return row(tick(local.versionNumber(), local.channel().apiName(), local.isPinned(),
                    local.awaitingRestart()), Component.text(peer.name(), BRAND), actions);
        }

        if (!peer.runs(project.loaders(), project.gameVersions())) {
            return row(null, Component.text(peer.name(), BRAND),
                    List.of(Component.text("not for this server", MUTED)));
        }

        return row(null, Component.text(peer.name(), BRAND), List.of(
                button("Install", from("/catalog install " + key, here), BRAND, "Install it on this server"),
                button("Versions", from("/catalog versions " + key, here), MUTED,
                        "Choose a build to install")));
    }

    private static Component remoteRow(ModrinthProject project, Peer peer) {

        if (!peer.online()) {
            return row(null, Component.text(peer.name(), MUTED), List.of(Component.text("offline", MUTED)));
        }

        Component name = Component.text(peer.name(), TEXT);
        Inventory.Plugin plugin = find(peer, project);

        if (plugin != null) {

            List<Component> actions = new ArrayList<>();

            if (plugin.update() != null) {
                actions.add(later("Update", BRAND));
            }

            actions.addAll(List.of(later("Switch", MUTED), later("Settings", MUTED),
                    later("Remove", DANGER)));

            return row(tick(plugin.version(), plugin.channel().apiName(), plugin.held(), plugin.waiting()),
                    name, actions);
        }

        if (!peer.runs(project.loaders(), project.gameVersions())) {
            return row(null, name, List.of(Component.text("not for this server", MUTED)));
        }

        return row(null, name, List.of(later("Install", BRAND), later("Versions", MUTED)));
    }

    /**
     * A server's row. The tick shows when the plugin is installed there.
     *
     * @param tick the tick, or null when it is not installed there
     */
    private static Component row(Component tick, Component name, List<Component> rest) {

        TextComponent.Builder row = line()
                .append(Component.text(INDENT))
                .append(tick == null ? Component.text("   ") : tick)
                .append(name);

        for (Component part : rest) {
            row.append(Component.space()).append(part);
        }

        return row.build();
    }

    /**
     * The tick that says it is installed. The hover holds the details.
     */
    private static Component tick(String version, String channel, boolean held, boolean waiting) {

        TextComponent.Builder hover = Component.text()
                .append(Component.text(version == null ? "unknown version" : version, TEXT))
                .append(Component.text("  " + channel, MUTED));

        if (held) {
            hover.append(Component.newline()).append(Component.text("held", MUTED));
        }

        if (waiting) {
            hover.append(Component.newline()).append(Component.text("waiting for a restart", PENDING));
        }

        return Component.text("✔ ", DONE).hoverEvent(HoverEvent.showText(hover.build()));
    }

    /**
     * A button for another server. It does nothing until actions reach other servers.
     */
    private static Component later(String label, TextColor colour) {
        return Component.text("[", MUTED)
                .append(Component.text(label, colour))
                .append(Component.text("]", MUTED))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Not available on other servers yet", MUTED)));
    }

    private static Inventory.Plugin find(Peer peer, ModrinthProject project) {

        for (Inventory.Plugin plugin : peer.inventory().plugins()) {
            if (project.id().equals(plugin.projectId())) {
                return plugin;
            }
        }

        return null;
    }

    private static long installedOn(ModrinthProject project, List<Peer> peers) {
        return peers.stream().filter(peer -> find(peer, project) != null).count();
    }

    /**
     * This server first. The others follow by name.
     */
    private static List<Peer> ordered(List<Peer> peers, String self) {

        List<Peer> out = new ArrayList<>();

        for (Peer peer : peers) {
            if (peer.name().equals(self)) {
                out.add(peer);
            }
        }

        for (Peer peer : peers) {
            if (!peer.name().equals(self)) {
                out.add(peer);
            }
        }

        return out;
    }

}
