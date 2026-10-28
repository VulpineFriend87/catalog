package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.command.screen.Notice;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The summary staff see when they join.
 */
public final class JoinNotice {

    /** How many names a row shows before the rest is counted. */
    private static final int NOTICE_NAMES = 3;

    /** How many plugins a row's hover lists before the rest is counted. */
    private static final int NOTICE_PLUGINS = 10;

    private JoinNotice() {
    }

    /**
     * One row per fact, most urgent first, then the buttons that act on them.
     *
     * @param updatable whether there is anything Update all would download
     */
    public static List<Component> notice(List<Notice> notices, boolean updatable) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Catalog", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  updates", MUTED))
                .build());

        for (Notice notice : notices) {
            out.add(fact(notice));
        }

        List<Component> row = new ArrayList<>();

        if (updatable) {
            row.add(button("Update all", "/catalog update all", BRAND, "Update all plugins"));
        }

        row.add(button("List", "/catalog list", MUTED, "Open the plugin list"));
        row.add(button("History", "/catalog history", MUTED, "What Catalog has done"));

        out.add(buttons(row));

        return out;
    }

    private static Component fact(Notice notice) {

        List<Notice.Item> plugins = notice.plugins();
        int count = plugins.size();

        String words = switch (notice.kind()) {
            case DID_NOT_ENABLE -> "did not enable";
            case DID_NOT_APPLY -> "did not apply";
            case AUTO_UPDATE_FAILED -> count == 1 ? "auto-update failed" : "auto-updates failed";
            case APPLIED -> "applied";
            case WAITING -> "waiting for a restart";
            case AVAILABLE -> "available";
        };

        TextColor colour = switch (notice.kind()) {
            case DID_NOT_ENABLE, DID_NOT_APPLY, AUTO_UPDATE_FAILED -> PENDING;
            case APPLIED -> DONE;
            case WAITING, AVAILABLE -> BRAND;
        };

        String meaning = switch (notice.kind()) {
            case DID_NOT_ENABLE -> "Not enabled by the server after the last restart";
            case DID_NOT_APPLY -> "Downloaded, not installed by the last restart";
            case AUTO_UPDATE_FAILED -> "Not updated automatically";
            case APPLIED -> "Installed by the last restart";
            case WAITING -> "Downloaded, installs on the next restart";
            case AVAILABLE -> "A newer build is on Modrinth";
        };

        String command = switch (notice.kind()) {
            case APPLIED -> "/catalog history --event update";
            case DID_NOT_ENABLE, DID_NOT_APPLY -> "/catalog history --event failed";
            case AUTO_UPDATE_FAILED, AVAILABLE -> "/catalog list --updates";
            case WAITING -> "/catalog list --restart";
        };

        TextComponent.Builder info = Component.text()
                .append(Component.text(meaning, MUTED))
                .append(Component.newline());

        for (Notice.Item plugin : plugins.subList(0, Math.min(count, NOTICE_PLUGINS))) {

            info.append(Component.newline()).append(Component.text(plugin.name(), TEXT));

            if (plugin.detail() != null) {
                info.append(Component.text("  " + plugin.detail(), MUTED));
            }

            if (plugin.reason() != null) {
                info.append(Component.newline())
                        .append(Component.text(INDENT + "Reason: " + plugin.reason(), MUTED));
            }
        }

        if (count > NOTICE_PLUGINS) {
            info.append(Component.newline())
                    .append(Component.text("and " + (count - NOTICE_PLUGINS) + " more", MUTED));
        }

        Component denied = info.build();
        Component hover = denied.append(Component.newline()).append(Component.newline())
                .append(explain(command.startsWith("/catalog history")
                        ? "Open the history" : "Open the plugin list", command));

        TextComponent.Builder row = line()
                .append(Component.text(INDENT))
                .append(Permitted.link(Component.text(count, colour), command, hover, denied))
                .append(Permitted.link(Component.text(" " + words + "  ", MUTED), command, hover, denied));

        List<Notice.Item> named = plugins.subList(0, Math.min(count, NOTICE_NAMES));

        for (int i = 0; i < named.size(); i++) {

            Notice.Item plugin = named.get(i);

            if (i > 0) {
                row.append(Permitted.link(Component.text(", ", TEXT), command, hover, denied));
            }

            row.append(Permitted.link(Component.text(plugin.name(), TEXT), command, hover, denied));

            if (plugin.rollback() != null) {
                row.append(Component.space()).append(rollbackIcon(plugin.rollback(),
                        plugin.rollbackTo(), plugin.rollbackGone()));
            }
        }

        if (count > NOTICE_NAMES) {
            String more = "/catalog list";
            row.append(Permitted.link(Component.text(" and " + (count - NOTICE_NAMES) + " more", MUTED), more,
                    explain("Open the plugin list", more), null));
        }

        return row.build();
    }

    /**
     * @return the switch back to the build the last update replaced
     */
    public static String rollbackCommand(TrackedPlugin plugin) {
        return "/catalog install " + key(plugin) + " " + plugin.previousVersionId();
    }

    /**
     * The button that switches a plugin back to the build its last update replaced, grey and inert
     * when that build is gone from Modrinth.
     */
    public static Component rollbackIcon(String command, String to, boolean gone) {

        if (gone) {
            return Component.text("[", MUTED)
                    .append(Component.text("«", MUTED))
                    .append(Component.text("]", MUTED))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("The previous build was removed from Modrinth", MUTED)));
        }

        return icon("«", BRAND, command, "Roll back to " + to);
    }

}
