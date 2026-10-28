package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The question asked before removing a plugin others require.
 */
public final class RemovePage {

    private RemovePage() {
    }

    /**
     * Asked before removing a plugin that other installed plugins require.
     *
     * @param plugin     what is being removed
     * @param dependents the installed plugins that name it
     * @param asked      the press that asked for the removal
     */
    public static List<Component> confirmRemove(TrackedPlugin plugin, List<TrackedPlugin> dependents,
                                                Press asked) {

        String key = key(plugin);
        String from = asked == null ? null : asked.screen();
        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Remove ", DANGER).decorate(TextDecoration.BOLD))
                .append(Component.text(plugin.displayName(), TEXT).decorate(TextDecoration.BOLD))
                .build());

        out.add(line()
                .append(Component.text(INDENT))
                .append(Component.text(dependents.size(), PENDING))
                .append(Component.text(dependents.size() == 1
                        ? " installed plugin requires it" : " installed plugins require it", MUTED))
                .build());

        List<String> names = new ArrayList<>();

        for (TrackedPlugin dependent : dependents) {
            names.add(dependent.displayName());
        }

        out.add(Component.text(INDENT + String.join(", ", names), TEXT));
        out.add(Component.text(INDENT + "They will not load after the next restart.", MUTED));

        out.add(Component.empty());

        out.add(line()
                .append(Component.text(INDENT))
                .append(button("Remove anyway", confirming("/catalog uninstall " + key, asked),
                        DANGER, "Remove " + plugin.displayName()))
                .append(Component.space())
                .append(button("Cancel", backTo(from, "/catalog info " + key), MUTED, "Keep it"))
                .build());

        return out;
    }

}
