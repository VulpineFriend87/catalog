package top.vulpine.catalog.paper.command.screen.widget;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import top.vulpine.catalog.paper.command.ClickContext;
import top.vulpine.catalog.paper.command.ListFilter;
import top.vulpine.catalog.paper.command.Press;
import top.vulpine.catalog.paper.command.Trail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;

/**
 * The pieces screens are built from.
 */
public final class Widgets {

    public static final String INDENT = "  ";

    private Widgets() {
    }

    public static Component field(String label, Component value) {

        return line()
                .append(Component.text(INDENT + label + ":  ", MUTED))
                .append(value)
                .build();
    }

    /**
     * Back to the screen this one was opened from, or the info page when the command was typed.
     */
    public static Component backOrInfo(String from, String key) {
        return from == null || from.isEmpty()
                ? button("Info", "/catalog info " + key, MUTED, "Open the info page")
                : button("Back", backTo(from, "/catalog info " + key), MUTED, "Back");
    }

    /**
     * The command that reopens the screen something was launched from, with its own trail.
     *
     * @param from     the trail a payload carried, or null when there was none
     * @param fallback where to go when nothing was carried
     */
    public static String backTo(String from, String fallback) {

        String screen = Trail.current(from);
        String command = screen == null || screen.isEmpty() ? null : reopen(screen);

        if (command == null) {
            return fallback;
        }

        // A confirmation reopened from itself is redrawn, never confirmed.
        return from(command, screen.startsWith(ClickContext.CHANGE) ? from : Trail.parent(from));
    }

    /**
     * @param screen a screen token
     * @return the command that opens it, or null when it is not a screen that can be reopened
     */
    private static String reopen(String screen) {

        Set<ListFilter> filters = ListFilter.parse(screen);

        if (filters != null) {
            return "/catalog list" + ListFilter.switches(filters);
        }

        if (screen.equals(ClickContext.TRASH)) {
            return "/catalog trash";
        }

        if (screen.startsWith(ClickContext.INFO)) {
            return "/catalog info " + screen.substring(ClickContext.INFO.length());
        }

        if (screen.startsWith(ClickContext.SETTINGS)) {
            return "/catalog settings " + screen.substring(ClickContext.SETTINGS.length());
        }

        if (screen.startsWith(ClickContext.DEPENDENCIES)) {
            String[] about = ClickContext.dependenciesOf(screen);
            return "/catalog dependencies " + about[0] + (about[1] == null ? "" : " " + about[1]);
        }

        if (screen.startsWith(ClickContext.CHANGE)) {
            String[] change = ClickContext.changeOf(screen);
            return "/catalog " + change[0] + " " + change[1] + (change[2] == null ? "" : " " + change[2]);
        }

        return null;
    }

    public static Component buttons(List<Component> row) {

        TextComponent.Builder out = line().append(Component.text(INDENT));

        for (int i = 0; i < row.size(); i++) {

            if (i > 0) {
                out.append(Component.space());
            }

            out.append(row.get(i));
        }

        return out.build();
    }

    public static Component button(String label, String command, TextColor colour, String description) {

        boolean complete = !command.endsWith(" ");

        return Component.text("[", MUTED)
                .append(Component.text(label, colour))
                .append(Component.text("]", MUTED))
                .clickEvent(complete ? ClickEvent.runCommand(command) : ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(explain(description, command)))
                .insertion(ClickContext.strip(command).trim());
    }

    public static Component icon(String glyph, TextColor colour, String command, String description) {

        return Component.text("[", MUTED)
                .append(Component.text(glyph, colour))
                .append(Component.text("]", MUTED))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(explain(description, command)))
                .insertion(ClickContext.strip(command).trim());
    }

    /**
     * Rewrites a screen for somewhere that cannot click.
     *
     * <p>Every widget carries the command it runs, so a console reads the command in place of a
     * button it has no way to press.</p>
     *
     * @param component the screen as a player would see it
     * @return the same screen with each button replaced by its command
     */
    public static Component typed(Component component) {

        String insertion = component.insertion();

        if (insertion != null && !insertion.isEmpty()) {
            return Component.text("[", MUTED)
                    .append(Component.text(insertion, labelColour(component)))
                    .append(Component.text("]", MUTED));
        }

        if (component.children().isEmpty()) {
            return component;
        }

        List<Component> children = new ArrayList<>();

        for (Component child : component.children()) {
            children.add(typed(child));
        }

        return component.children(children);
    }

    /**
     * The colour a widget reads as, which is on its label rather than its brackets.
     */
    private static TextColor labelColour(Component widget) {

        for (Component child : widget.children()) {
            if (child.color() != null && !MUTED.equals(child.color())) {
                return child.color();
            }
        }

        return widget.color() == null ? BRAND : widget.color();
    }

    public static Component explain(String description, String command) {

        return Component.text(description, TEXT)
                .append(Component.newline())
                .append(Component.text(ClickContext.strip(command).trim(), MUTED));
    }

    /**
     * Tags a command with the screen it is being offered from.
     */
    public static String from(String command, String screen) {
        return ClickContext.press(command, screen);
    }

    /**
     * The button that answers a confirmation, carrying everything the press that asked it carried.
     */
    public static String confirming(String command, Press asked) {
        return from(command, (asked == null ? Press.on(null) : asked).confirming().data());
    }

    public static TextComponent.Builder line() {
        return Component.text();
    }

}
