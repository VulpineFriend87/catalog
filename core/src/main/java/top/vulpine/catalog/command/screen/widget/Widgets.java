package top.vulpine.catalog.command.screen.widget;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.command.permission.Permitted;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Palette.*;

/**
 * The buttons, lines and fields screens use.
 */
public final class Widgets {

    public static final String INDENT = "  ";

    private Widgets() {
    }

    /**
     * Bold text at the head of a screen.
     */
    public static Component title(String text, TextColor colour) {
        return Component.text(text, colour).decorate(TextDecoration.BOLD);
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
     * The command that reopens the screen something was launched from.
     *
     * @param from     the trail a payload carried, or null when there was none
     * @param fallback where to go when nothing was carried
     */
    public static String backTo(String from, String fallback) {

        Screen screen = Screen.parse(Trail.current(from));

        if (screen == null) {
            return fallback;
        }

        // A confirmation reopened from itself is redrawn and never confirmed.
        return from(screen.reopen(), screen instanceof Screen.ChangeScreen ? from : Trail.parent(from));
    }

    /**
     * A command aimed at another server.
     *
     * <p>{@code --server} goes last. The client only accepts flags in the order they are declared.</p>
     *
     * @param server the server it runs on, or null for this one
     */
    public static String on(String command, String server) {
        return server == null ? command : command + " --server " + server;
    }

    /**
     * The title of a screen and the server it shows when that is another one.
     *
     * @param server the server it shows, or null for this one
     */
    public static TextComponent.Builder head(String title, String server) {

        TextComponent.Builder head = line().append(title(title, BRAND));
        return server == null ? head : head.append(Component.text(" · " + server, BRAND));
    }

    /**
     * The button that opens the same screen on another server.
     *
     * @param screen what {@code /catalog switch} reopens: list, history or trash
     * @param server the server the screen shows, or null for this one
     */
    public static Component switchServer(String screen, String server) {
        return button("Switch server", on("/catalog switch " + screen, server), MUTED, "Show another server");
    }

    /**
     * A value that can be picked. The command runs when it is clicked.
     */
    public static Component choice(String label, String command, String description) {
        return Permitted.link(Component.text(label, MUTED).append(Component.text("  ", MUTED)),
                command, explain(description, command), null);
    }

    /**
     * The value currently picked.
     */
    public static Component chosen(String label) {
        return Component.text(label, BRAND).append(Component.text("  ", MUTED));
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
     * Rewrites a screen for a viewer that cannot click.
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
     * The colour of a widget's label.
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
     * The command of a button that answers a confirmation.
     */
    public static String confirming(String command, Press asked) {
        return from(command, (asked == null ? Press.on(null) : asked).confirming().data());
    }

    public static TextComponent.Builder line() {
        return Component.text();
    }

}
