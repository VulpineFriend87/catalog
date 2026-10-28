package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.paper.command.ClickContext;
import top.vulpine.catalog.paper.command.Press;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.paper.command.screen.widget.Format.*;
import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

/**
 * The trash.
 */
public final class TrashPage {

    /** How many removals one page of the trash shows. */
    private static final int BIN = 10;

    private TrashPage() {
    }

    /**
     * What has been removed and can still be restored.
     *
     * <p>Ordered by when it was removed rather than by name: the thing somebody wants back is
     * almost always the last thing they got rid of.</p>
     */
    public static List<Component> trash(List<TrashEntry> entries, int retentionDays, int page) {

        int pages = Math.max((entries.size() + BIN - 1) / BIN, 1);
        int shown = Math.min(Math.max(page, 1), pages);
        int first = (shown - 1) * BIN;

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Trash", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  " + entries.size(), TEXT))
                .append(Component.text(" removed", MUTED))
                .build());

        if (entries.isEmpty()) {
            out.add(Component.empty());
            out.add(Component.text(INDENT + "Nothing has been removed", MUTED));
            return out;
        }

        out.add(Component.text(INDENT + (retentionDays > 0
                ? "Deleted after " + retentionDays + " days."
                : "Kept until you delete them."), MUTED));

        out.add(Component.empty());

        for (TrashEntry entry : entries.subList(first, Math.min(first + BIN, entries.size()))) {
            out.add(trashRow(entry));
        }

        out.add(Component.empty());

        TextComponent.Builder footer = line()
                .append(Component.text(INDENT + "page ", MUTED))
                .append(Component.text(shown, TEXT))
                .append(Component.text(" of " + pages + "  ", MUTED));

        if (shown > 1) {
            footer.append(button("Newer »", "/catalog trash --page " + (shown - 1), MUTED,
                    "Page " + (shown - 1))).append(Component.space());
        }

        if (shown < pages) {
            footer.append(button("« Older", "/catalog trash --page " + (shown + 1), MUTED,
                    "Page " + (shown + 1))).append(Component.space());
        }

        footer.append(button("Empty", from("/catalog trash delete all", ClickContext.TRASH),
                        DANGER, "Delete everything in the trash"))
                .append(Component.space())
                .append(button("Plugins", "/catalog list", MUTED, "Back to the plugin list"));

        out.add(footer.build());

        return out;
    }

    private static Component trashRow(TrashEntry entry) {

        Component hover = Component.text(entry.displayName(), TEXT)
                .append(Component.newline())
                .append(Component.text(entry.fileName(), MUTED))
                .append(entry.versionNumber() == null ? Component.empty()
                        : Component.newline().append(Component.text(entry.versionNumber(), MUTED)))
                .append(entry.removedBy() == null ? Component.empty()
                        : Component.newline().append(Component.text("removed by " + entry.removedBy(), MUTED)))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text(entry.projectId() == null
                        ? "Not a Modrinth plugin. Restoring only puts the file back."
                        : "Restoring puts it back and tracks it again.", MUTED));

        return line()
                .append(Component.text(INDENT))
                .append(Component.text(entry.displayName(), TEXT).hoverEvent(HoverEvent.showText(hover)))
                .append(entry.versionNumber() == null ? Component.empty()
                        : Component.text("  " + entry.versionNumber(), MUTED))
                .append(Component.text("  " + ago(entry.removedAt()), MUTED))
                .append(Component.space())
                .append(button("Restore", restoreCommand(entry, ClickContext.TRASH), BRAND,
                        "Put " + entry.displayName() + " back"))
                .append(Component.space())
                .append(icon("×", DANGER, from("/catalog trash delete " + entry.storedAs(),
                                ClickContext.TRASH),
                        "Delete " + entry.displayName() + " permanently"))
                .build();
    }

    public static String restoreCommand(TrashEntry entry, String here) {
        return from("/catalog trash restore " + entry.storedAs(), here);
    }

    public static List<Component> confirmEmpty(int count) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Empty the trash", DANGER).decorate(TextDecoration.BOLD))
                .build());

        out.add(line()
                .append(Component.text(INDENT))
                .append(Component.text(count, TEXT))
                .append(Component.text(count == 1 ? " removal. This cannot be undone."
                        : " removals. This cannot be undone.", MUTED))
                .build());

        out.add(Component.empty());

        out.add(line()
                .append(Component.text(INDENT))
                .append(button("Confirm", confirming("/catalog trash delete all",
                                Press.on(ClickContext.TRASH)),
                        DANGER, "Delete them now"))
                .append(Component.space())
                .append(button("Cancel", "/catalog trash", MUTED, "Keep them"))
                .build());

        return out;
    }

}
