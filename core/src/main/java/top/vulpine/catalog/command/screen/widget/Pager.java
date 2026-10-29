package top.vulpine.catalog.command.screen.widget;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.List;
import java.util.function.IntFunction;

import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * One page of a list.
 *
 * @param shown the 1-based number of the page shown
 * @param pages how many pages there are
 * @param size  how many items one page holds
 */
public record Pager(int shown, int pages, int size) {

    /**
     * @param total how many items the list has
     * @param page  the page asked for. Out of range pages are clamped.
     */
    public static Pager of(int total, int size, int page) {
        return new Pager(clamp(page, (total + size - 1) / size), Math.max((total + size - 1) / size, 1), size);
    }

    /**
     * A page of a list whose pages differ in size. {@link #slice} does not apply.
     *
     * @param page  the page asked for. Out of range pages are clamped.
     * @param pages how many pages there are
     */
    public static Pager at(int page, int pages) {
        return new Pager(clamp(page, pages), Math.max(pages, 1), 0);
    }

    private static int clamp(int page, int pages) {
        return Math.min(Math.max(page, 1), Math.max(pages, 1));
    }

    /**
     * The items on the page shown.
     */
    public <T> List<T> slice(List<T> items) {

        int first = Math.min((shown - 1) * size, items.size());
        return items.subList(first, Math.min(first + size, items.size()));
    }

    /**
     * A screen's last line: its buttons, then the page number and the arrows to the pages either side.
     *
     * @param row      the screen's own buttons
     * @param previous the hover of the arrow to the page before
     * @param next     the hover of the arrow to the page after
     * @param command  the command that opens a page
     */
    public Component footer(List<Component> row, String previous, String next, IntFunction<String> command) {

        TextComponent.Builder footer = line().append(buttons(row));

        if (pages == 1) {
            return footer.build();
        }

        if (!row.isEmpty()) {
            footer.append(Component.text("   "));
        }

        return footer.append(Component.text(shown, TEXT))
                .append(Component.text("/" + pages + " ", MUTED))
                .append(arrow("«", shown > 1, command.apply(shown - 1), previous + ": page " + (shown - 1)))
                .append(Component.space())
                .append(arrow("»", shown < pages, command.apply(shown + 1), next + ": page " + (shown + 1)))
                .build();
    }

    /**
     * An arrow to another page. Grey and inert when there is no page that way.
     */
    private static Component arrow(String glyph, boolean usable, String command, String description) {
        return usable ? icon(glyph, BRAND, command, description) : Component.text("[" + glyph + "]", MUTED);
    }

}
