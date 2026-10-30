package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import top.vulpine.catalog.command.screen.widget.Pager;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.history.Event;
import top.vulpine.catalog.history.HistoryEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * What Catalog has done.
 */
public final class HistoryPage {

    /** How many lines the entries and day headings of one page take at most. */
    private static final int BODY = 14;

    private HistoryPage() {
    }

    /**
     * What Catalog did grouped by day.
     *
     * @param entries what to show sorted newest first
     * @param page    the page number starting at one
     * @param filter  what the header says the list was narrowed to, or null when it was not
     * @param command the command the pager adds {@code --page} to, without {@code --server}
     * @param server    the server the log belongs to, or null for this one
     * @param networked whether other servers can be shown
     */
    public static List<Component> history(List<HistoryEntry> entries, int page, String filter,
                                          String command, String server, boolean networked) {

        List<List<HistoryEntry>> pages = paged(entries);
        Pager pager = Pager.at(page, pages.size());

        List<Component> out = new ArrayList<>();

        TextComponent.Builder header = head("Catalog", server)
                .append(Component.text("  history", MUTED));

        out.add(header.build());

        if (filter != null) {
            out.add(ListPage.filtersLine(filter, on("/catalog history", server)));
        }

        if (entries.isEmpty()) {
            out.add(Component.empty());
            out.add(Component.text(INDENT + "Nothing recorded yet", MUTED));
            return out;
        }

        LocalDate day = null;

        for (HistoryEntry entry : pages.get(pager.shown() - 1)) {

            LocalDate on = LocalDate.ofInstant(entry.at(), ZoneId.systemDefault());

            if (!on.equals(day)) {
                day = on;
                out.add(Component.empty());
                out.add(Component.text(INDENT + dayName(on), BRAND));
            }

            out.add(historyRow(entry, server));
        }

        out.add(Component.empty());

        List<Component> row = new ArrayList<>();
        row.add(button("Plugins", on("/catalog list", server), MUTED, "Back to the plugin list"));

        if (networked) {
            row.add(switchServer("history", server));
        }

        out.add(pager.footer(row, "Newer", "Older", shown -> on(command + " --page " + shown, server)));

        return out;
    }

    /**
     * Splits the entries into pages of at most {@link #BODY} lines. A day heading takes two.
     */
    private static List<List<HistoryEntry>> paged(List<HistoryEntry> entries) {

        List<List<HistoryEntry>> pages = new ArrayList<>();
        List<HistoryEntry> current = new ArrayList<>();
        LocalDate day = null;
        int lines = 0;

        for (HistoryEntry entry : entries) {

            LocalDate on = LocalDate.ofInstant(entry.at(), ZoneId.systemDefault());
            int needed = on.equals(day) ? 1 : 3;

            if (!current.isEmpty() && lines + needed > BODY) {
                pages.add(current);
                current = new ArrayList<>();
                lines = 0;
                needed = 3;
            }

            current.add(entry);
            lines += needed;
            day = on;
        }

        pages.add(current);
        return pages;
    }

    private static String dayName(LocalDate on) {

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        if (on.equals(today)) {
            return "Today";
        }

        if (on.equals(today.minusDays(1))) {
            return "Yesterday";
        }

        return on.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH));
    }

    private static Component historyRow(HistoryEntry entry, String server) {

        TextComponent.Builder row = line().append(Component.text(INDENT + INDENT));

        if (entry.name() != null) {
            row.append(Component.text(entry.name(), TEXT)).append(Component.text("  "));
        }

        row.append(Component.text(said(entry), colourOf(entry.event())));

        Component detail = historyDetail(entry, server);

        // A row narrows the list to its own plugin.
        if (entry.slug() == null) {
            return row.hoverEvent(HoverEvent.showText(detail)).build();
        }

        return Permitted.link(row.build(), on("/catalog history --plugin " + entry.slug(), server), detail,
                detail);
    }

    /**
     * The colour of an event: {@code DONE} when applied, {@code PENDING} when it needs a restart or
     * attention, {@code DANGER} when removed.
     */
    private static TextColor colourOf(Event event) {

        return switch (event) {
            case INSTALLED, INSTALLED_AS_DEPENDENCY, RESTORED, UPDATES_APPLIED -> DONE;
            case UPDATE_STAGED, SWITCHED, ROLLED_BACK, UPDATE_HELD_BACK, AUTO_UPDATE_FAILED,
                 UPDATE_NOT_APPLIED, UPDATE_LOST, UPDATE_NOT_ENABLED, INSTALL_NOT_ENABLED,
                 REPLACED_BY_HAND -> PENDING;
            case TRASHED, DELETED, TRASH_EMPTIED, NO_LONGER_INSTALLED -> DANGER;
            case ADOPTED, UNTRACKED -> TEXT;
            default -> MUTED;
        };
    }

    /**
     * The sentence a row shows.
     */
    private static String said(HistoryEntry entry) {

        return switch (entry.event()) {
            case INSTALLED -> "installed";
            case INSTALLED_AS_DEPENDENCY -> "installed as a dependency";
            case UPDATE_STAGED -> entry.byPerson() ? "update downloaded" : "auto-update downloaded";
            case UPDATE_CANCELLED -> "update cancelled";
            case SWITCHED -> "switched";
            case ROLLED_BACK -> "rolled back";
            case TRASHED -> "moved to trash";
            case RESTORED -> "restored";
            case DELETED -> "deleted from trash";
            case TRASH_EMPTIED -> "Trash emptied";
            case UPDATES_APPLIED -> entry.name() != null ? "applied"
                    : "Applied " + entry.count() + " downloaded builds";
            case ADOPTED -> entry.name() != null ? "adopted"
                    : "Adopted " + entry.count() + " plugins";
            case UNTRACKED -> "untracked";
            case REPLACED_BY_HAND -> "replaced by hand";
            case NO_LONGER_INSTALLED -> "no longer installed";
            case UPDATE_HELD_BACK -> "auto-update blocked";
            case AUTO_UPDATE_FAILED -> "auto-update failed";
            case UPDATE_NOT_APPLIED -> "update did not apply";
            case UPDATE_LOST -> "update removed from the update folder";
            case UPDATE_NOT_ENABLED -> "update did not enable";
            case INSTALL_NOT_ENABLED -> "install did not enable";
            case HELD -> "held";
            case UNHELD -> "unheld";
            case AUTO_UPDATE -> "auto-update " + entry.value();
            case CHANNEL -> "channel set to " + entry.value();
            case SOAK -> "soak set to " + ("default".equals(entry.value())
                    ? "default" : soakLabel(readInt(entry.value())));
        };
    }

    private static int readInt(String value) {

        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Component historyDetail(HistoryEntry entry, String server) {

        TextComponent.Builder hover = Component.text()
                .append(Component.text(entry.name() == null ? "Catalog" : entry.name(), TEXT));

        if (entry.from() != null && entry.to() != null) {
            hover.append(Component.newline())
                    .append(Component.text(entry.from() + " → " + entry.to(), MUTED));
        } else if (entry.to() != null) {
            hover.append(Component.newline()).append(Component.text(entry.to(), MUTED));
        }

        if (entry.channel() != null) {
            hover.append(Component.newline()).append(Component.text(entry.channel(), MUTED));
        }

        if (entry.event() == Event.INSTALLED_AS_DEPENDENCY && entry.value() != null) {
            hover.append(Component.newline())
                    .append(Component.text("required by " + entry.value(), MUTED));
        }

        String reason = switch (entry.event()) {
            case UPDATE_HELD_BACK -> entry.names() == null || entry.names().isEmpty() ? null
                    : String.join(", ", entry.names()) + " required missing";
            case AUTO_UPDATE_FAILED, UPDATE_NOT_ENABLED, INSTALL_NOT_ENABLED -> entry.value();
            default -> null;
        };

        if (reason != null) {
            hover.append(Component.newline()).append(Component.text("Reason: " + reason, MUTED));
        }

        if (entry.event() == Event.UPDATE_NOT_APPLIED) {
            hover.append(Component.newline())
                    .append(Component.text("Still in the update folder", MUTED));
        } else if (entry.names() != null && !entry.names().isEmpty()) {
            hover.append(Component.newline())
                    .append(Component.text(String.join(", ", entry.names()), MUTED));
        }

        hover.append(Component.newline()).append(Component.newline());

        // Applied by the restart. Whoever queued it is on the row that queued it.
        if (entry.event() != Event.UPDATES_APPLIED) {
            hover.append(Component.text(entry.byPerson() ? "by " + entry.by() : "by Catalog", TEXT))
                    .append(Component.newline());
        }

        hover.append(Component.text(when(entry.at()), MUTED));

        if (entry.slug() != null) {
            hover.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Show only " + entry.name(), TEXT))
                    .append(Component.newline())
                    .append(Component.text(on("/catalog history --plugin " + entry.slug(), server), MUTED));
        }

        return hover.build();
    }

    private static String when(Instant at) {
        return DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault()).format(at);
    }

}
