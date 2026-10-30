package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.network.model.Inventory;
import top.vulpine.catalog.network.model.Peer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The state of the network and the servers on it.
 */
public final class NetworkPage {

    private NetworkPage() {
    }

    /**
     * How the connection and every server are doing.
     *
     * @param ping    how long Redis took to answer, or null when it did not
     * @param self    this server's name
     * @param catalog the Catalog version this server runs
     */
    public static List<Component> network(Duration ping, List<Peer> peers, String self, String catalog) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title("Catalog", BRAND))
                .append(Component.text("  network", MUTED))
                .build());

        TextComponent.Builder redis = line().append(Component.text(INDENT + "Redis  ", MUTED));

        if (ping == null) {
            redis.append(Component.text("disconnected", DANGER));
        } else {
            redis.append(Component.text("connected", DONE))
                    .append(Component.text("  " + ping.toMillis() + " ms", MUTED));
        }

        out.add(redis.build());
        out.add(Component.empty());

        for (Peer peer : peers) {
            out.add(auditRow(peer, peer.name().equals(self), catalog));
        }

        out.add(Component.empty());
        out.add(buttons(List.of(button("Plugins", "/catalog list", MUTED, "Back to the plugin list"))));

        return out;
    }

    /**
     * The servers to open a screen on.
     *
     * @param self   this server's name
     * @param shown  the server the screen shows now
     * @param screen the screen to reopen: list, history or trash
     */
    public static List<Component> pick(List<Peer> peers, String self, String shown, String screen) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title("Switch server", BRAND))
                .build());

        out.add(Component.empty());

        for (Peer peer : peers) {
            out.add(pickRow(peer, self, shown, screen));
        }

        out.add(Component.empty());
        out.add(buttons(List.of(button("Back", open(screen, shown, self), MUTED, "Back to " + shown))));

        return out;
    }

    private static Component auditRow(Peer peer, boolean self, String catalog) {

        Inventory inventory = peer.inventory();
        long updates = updates(peer);

        TextComponent.Builder row = line()
                .append(Component.text(INDENT))
                .append(Component.text(peer.name(), self ? BRAND : TEXT)
                        .hoverEvent(HoverEvent.showText(details(peer))))
                .append(peer.online()
                        ? Component.text("  online", DONE)
                        : Component.text("  offline " + ago(inventory.publishedAt()), MUTED))
                .append(Component.text("  " + platform(inventory), MUTED));

        if (updates > 0) {
            row.append(Component.text("  " + updates + (updates == 1 ? " update" : " updates"), BRAND));
        }

        if (inventory.catalog() != null && !inventory.catalog().equals(catalog)) {
            row.append(Component.text("  Catalog " + inventory.catalog(), PENDING)
                    .hoverEvent(HoverEvent.showText(
                            Component.text("This server runs Catalog " + catalog, TEXT))));
        }

        return row.build();
    }

    private static Component pickRow(Peer peer, String self, String shown, String screen) {

        long updates = updates(peer);
        TextComponent.Builder row = line().append(Component.text(INDENT));

        if (peer.name().equals(shown)) {
            row.append(Component.text(peer.name(), BRAND));
        } else if (!peer.online()) {
            row.append(Component.text(peer.name() + "  offline " + ago(peer.inventory().publishedAt()),
                    MUTED));
            return row.build();
        } else {
            String command = open(screen, peer.name(), self);
            row.append(Permitted.link(Component.text(peer.name(), TEXT), command,
                    details(peer).append(Component.newline()).append(Component.newline())
                            .append(explain("Show " + peer.name(), command)), null));
        }

        if (peer.name().equals(self)) {
            row.append(Component.text("  this server", MUTED));
        }

        if (updates > 0) {
            row.append(Component.text("  ↑ " + updates + (updates == 1 ? " update" : " updates"), BRAND));
        }

        return row.build();
    }

    /**
     * The command that opens a screen on a server. This server takes no {@code --server}.
     */
    private static String open(String screen, String server, String self) {
        return on("/catalog " + screen, server.equals(self) ? null : server);
    }

    /**
     * The hover of a server's name.
     */
    private static Component details(Peer peer) {

        Inventory inventory = peer.inventory();

        return Component.text(peer.name(), TEXT)
                .append(Component.newline())
                .append(Component.text(platform(inventory), MUTED))
                .append(Component.newline())
                .append(Component.text("Catalog " + inventory.catalog(), MUTED))
                .append(Component.newline())
                .append(Component.text(inventory.plugins().size() + " plugins, published "
                        + ago(inventory.publishedAt()), MUTED));
    }

    private static String platform(Inventory inventory) {
        return inventory.platform()
                + (inventory.serverVersion() == null ? "" : " " + inventory.serverVersion());
    }

    private static long updates(Peer peer) {
        return peer.inventory().plugins().stream().filter(plugin -> plugin.update() != null).count();
    }

}
