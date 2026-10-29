package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import top.vulpine.catalog.command.screen.widget.Pager;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

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
     * <p>Newest removal first.</p>
     */
    public static List<Component> trash(List<TrashEntry> entries, int retentionDays, int page) {

        Pager pager = Pager.of(entries.size(), BIN, page);

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title("Trash", BRAND))
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

        for (TrashEntry entry : pager.slice(entries)) {
            out.add(trashRow(entry));
        }

        out.add(Component.empty());

        out.add(pager.footer(List.of(
                        button("Empty", from("/catalog trash delete all", new Screen.TrashScreen().token()),
                                DANGER, "Delete everything in the trash"),
                        button("Plugins", "/catalog list", MUTED, "Back to the plugin list")),
                "Newer", "Older", shown -> "/catalog trash --page " + shown));

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
                .append(button("Restore", restoreCommand(entry, new Screen.TrashScreen().token()), BRAND,
                        "Put " + entry.displayName() + " back"))
                .append(Component.space())
                .append(icon("×", DANGER, from("/catalog trash delete " + entry.storedAs(),
                                new Screen.TrashScreen().token()),
                        "Delete " + entry.displayName() + " permanently"))
                .build();
    }

    public static String restoreCommand(TrashEntry entry, String here) {
        return from("/catalog trash restore " + entry.storedAs(), here);
    }

    public static List<Component> confirmEmpty(int count) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title("Empty the trash", DANGER))
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
                                Press.on(new Screen.TrashScreen().token())),
                        DANGER, "Delete them now"))
                .append(Component.space())
                .append(button("Cancel", "/catalog trash", MUTED, "Keep them"))
                .build());

        return out;
    }

}
