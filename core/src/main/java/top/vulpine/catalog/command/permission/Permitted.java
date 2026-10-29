package top.vulpine.catalog.command.permission;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Shapes a screen for the commands a viewer may run.
 *
 * <p>Buttons carry their command in their insertion. Clickable rows are made with {@link #link}.
 * What a viewer who cannot act sees instead is declared with {@link #unless}. Click events are
 * never read back: Adventure 5 changed them incompatibly.</p>
 */
public final class Permitted {

    private static final Map<Component, Link> LINKS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<Component, Instead> INSTEAD = Collections.synchronizedMap(new WeakHashMap<>());

    private record Link(String command, Component denied) {
    }

    private record Instead(String requires, Component component) {
    }

    private Permitted() {
    }

    /**
     * Makes a row run a command when clicked, or suggest it when it ends in a space.
     *
     * @param hover  what the hover says
     * @param denied what it says instead to a viewer who cannot run the command, or null for none
     */
    public static Component link(Component row, String command, Component hover, Component denied) {

        Component linked = row
                .clickEvent(command.endsWith(" ") ? ClickEvent.suggestCommand(command)
                        : ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hover));

        LINKS.put(linked, new Link(command, denied));
        return linked;
    }

    /**
     * @param requires the command the shown component leads to
     * @param instead  what a viewer who cannot run it sees
     */
    public static Component unless(String requires, Component shown, Component instead) {
        INSTEAD.put(shown, new Instead(requires, instead));
        return shown;
    }

    /**
     * Swaps what is declared with {@link #unless} and removes the buttons and clicks the viewer
     * cannot use.
     *
     * @param allowed whether the viewer may run a command
     * @return the screen for them, or null when the component itself is a button they cannot use
     */
    public static Component filter(Component component, Predicate<String> allowed) {

        Instead instead = INSTEAD.get(component);

        if (instead != null && !allowed.test(instead.requires())) {
            component = instead.component();
        }

        String insertion = component.insertion();

        if (insertion != null && !insertion.isEmpty()) {
            return allowed.test(insertion) ? component : null;
        }

        Link link = LINKS.get(component);

        if (link != null && !allowed.test(link.command())) {
            component = component.clickEvent(null)
                    .hoverEvent(link.denied() == null ? null : HoverEvent.showText(link.denied()));
        }

        if (component.children().isEmpty()) {
            return component;
        }

        List<Component> children = new ArrayList<>();
        boolean dropSpace = false;

        for (Component child : component.children()) {

            Component kept = filter(child, allowed);

            if (kept == null) {

                // The space between two buttons goes with the one removed.
                if (!children.isEmpty() && isSpace(children.get(children.size() - 1))) {
                    children.remove(children.size() - 1);
                } else {
                    dropSpace = true;
                }

                continue;
            }

            if (dropSpace && isSpace(kept)) {
                dropSpace = false;
                continue;
            }

            dropSpace = false;
            children.add(kept);
        }

        return component.children(children);
    }

    /**
     * @return true when a component has no text but whitespace, or is null
     */
    public static boolean isBlank(Component component) {

        if (component == null) {
            return true;
        }

        if (component instanceof TextComponent text && !text.content().isBlank()) {
            return false;
        }

        for (Component child : component.children()) {
            if (!isBlank(child)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isSpace(Component component) {
        return component instanceof TextComponent text && text.content().equals(" ")
                && text.children().isEmpty();
    }

}
