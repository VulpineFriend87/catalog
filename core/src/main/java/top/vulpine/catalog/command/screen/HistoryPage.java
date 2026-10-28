package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    /** How many entries one page of the history shows. */
    private static final int EVENTS = 12;

    private HistoryPage() {
    }

    /**
     * What Catalog did, grouped by the day it happened.
     *
     * @param entries what to show, newest first
     * @param page    which page, from one
     * @param filter  what the list was narrowed to, shown in the header, or null when it was not
     * @param command the command that produced this list, which the pager adds a page to
     */
    public static List<Component> history(List<HistoryEntry> entries, int page, String filter,
                                          String command) {

        int pages = Math.max((entries.size() + EVENTS - 1) / EVENTS, 1);
        int shown = Math.min(Math.max(page, 1), pages);
        int first = (shown - 1) * EVENTS;

        List<Component> out = new ArrayList<>();

        TextComponent.Builder header = line()
                .append(Component.text("Catalog", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  history", MUTED));

        out.add(header.build());

        if (filter != null) {
            out.add(ListPage.filtersLine(filter, "/catalog history"));
        }

        if (entries.isEmpty()) {
            out.add(Component.empty());
            out.add(Component.text(INDENT + "Nothing recorded yet", MUTED));
            return out;
        }

        LocalDate day = null;

        for (HistoryEntry entry : entries.subList(first, Math.min(first + EVENTS, entries.size()))) {

            LocalDate on = LocalDate.ofInstant(entry.at(), ZoneId.systemDefault());

            if (!on.equals(day)) {
                day = on;
                out.add(Component.empty());
                out.add(Component.text(INDENT + dayName(on), BRAND));
            }

            out.add(historyRow(entry));
        }

        out.add(Component.empty());

        TextComponent.Builder footer = line()
                .append(Component.text(INDENT + "page ", MUTED))
                .append(Component.text(shown, TEXT))
                .append(Component.text(" of " + pages + "  ", MUTED));

        if (shown > 1) {
            footer.append(button("Newer", command + " --page " + (shown - 1), MUTED,
                    "Page " + (shown - 1))).append(Component.space());
        }

        if (shown < pages) {
            footer.append(button("Older", command + " --page " + (shown + 1), MUTED,
                    "Page " + (shown + 1))).append(Component.space());
        }

        footer.append(button("Plugins", "/catalog list", MUTED, "Back to the plugin list"));

        out.add(footer.build());

        return out;
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

    private static Component historyRow(HistoryEntry entry) {

        TextComponent.Builder row = line().append(Component.text(INDENT + INDENT));

        if (entry.name() != null) {
            row.append(Component.text(entry.name(), TEXT)).append(Component.text("  "));
        }

        row.append(Component.text(said(entry), colourOf(entry.event())));

        Component detail = historyDetail(entry);

        // A row narrows the list to its own plugin, which is how the filter is found without a
        // button for it anywhere.
        if (entry.slug() == null) {
            return row.hoverEvent(HoverEvent.showText(detail)).build();
        }

        return Permitted.link(row.build(), "/catalog history --plugin " + entry.slug(), detail, detail);
    }

    /**
     * What an event reads as: red for something gone, amber for something owed, green for something
     * that landed.
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
     * The sentence a row shows, which states the fact and leaves the rest to the hover.
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

    private static Component historyDetail(HistoryEntry entry) {

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

        // The restart applied it, so there is nobody to name. Whoever queued it is on the row
        // that queued it.
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
                    .append(Component.text("/catalog history --plugin " + entry.slug(), MUTED));
        }

        return hover.build();
    }

    private static String when(Instant at) {
        return DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault()).format(at);
    }

}
