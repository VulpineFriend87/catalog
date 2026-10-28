package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.paper.command.Permitted;
import top.vulpine.catalog.paper.command.Screen;
import top.vulpine.catalog.paper.command.Trail;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.paper.command.screen.widget.Format.*;
import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

/**
 * One plugin's settings.
 */
public final class SettingsPage {

    private static final int[] SOAK_PRESETS = {0, 30, 120, 360, 1440};

    private SettingsPage() {
    }

    public static List<Component> settings(TrackedPlugin plugin, int defaultSoak, String from) {

        String key = key(plugin);
        String here = Trail.within(new Screen.SettingsScreen(key).token(), from);

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text(plugin.displayName(), BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  settings", MUTED))
                .build());

        out.add(line()
                .append(Component.text(INDENT + plugin.versionNumber(), MUTED))
                .append(Component.text(" · ", MUTED))
                .append(VersionsPage.channel(plugin.channel()))
                .build());

        out.add(Component.empty());

        out.add(setting("Channel", channelChoices(plugin, key, here)));
        out.add(setting("Auto-update", autoChoices(plugin, key, here)));

        // Only meaningful when Catalog is the one deciding to install.
        if (plugin.autoUpdate()) {
            out.add(setting("Soak", soakChoices(plugin, key, defaultSoak, here)));
        }

        out.add(setting("Updates", holdChoices(plugin, key, here)));

        out.add(Component.empty());

        // Said on the line rather than only in the hover, because the one thing anyone fears here
        // is that it deletes the file.
        out.add(line()
                .append(Component.text(INDENT))
                .append(button("Untrack", from("/catalog untrack " + key, here), DANGER,
                        "Stop tracking " + plugin.displayName()))
                .append(Component.text("  the jar is not removed", MUTED))
                .build());

        out.add(Component.empty());

        out.add(line()
                .append(Component.text(INDENT))
                .append(backOrInfo(from, key))
                .build());

        return out;
    }

    private static Component channelChoices(TrackedPlugin plugin, String key, String here) {

        TextComponent.Builder out = Component.text();

        for (ReleaseChannel channel : ReleaseChannel.values()) {
            out.append(choice(channel.apiName(), "/catalog channel " + key + " " + channel.apiName(),
                    channel == plugin.channel(),
                    channel == ReleaseChannel.RELEASE
                            ? "Only offer stable builds"
                            : "Offer " + channel.apiName() + " builds, and anything more stable",
                    here));
        }

        return out.build();
    }

    private static Component autoChoices(TrackedPlugin plugin, String key, String here) {

        return Component.text()
                .append(choice("on", "/catalog auto " + key + " on", plugin.autoUpdate(),
                        "Install updates automatically", here))
                .append(choice("off", "/catalog auto " + key + " off", !plugin.autoUpdate(),
                        "Only update manually", here))
                .build();
    }

    private static Component soakChoices(TrackedPlugin plugin, String key, int defaultSoak,
                                         String here) {

        boolean inherits = plugin.soakMinutes() == TrackedPlugin.INHERIT_SOAK;

        TextComponent.Builder out = Component.text()
                .append(choice("default", "/catalog soak " + key + " default", inherits,
                        "Follow the config, currently " + soakLabel(defaultSoak), here));

        boolean custom = !inherits;

        for (int minutes : SOAK_PRESETS) {

            boolean selected = !inherits && plugin.soakMinutes() == minutes;
            custom &= !selected;

            out.append(choice(soakLabel(minutes), "/catalog soak " + key + " " + minutes, selected,
                    minutes == 0
                            ? "Install as soon as a build appears"
                            : "Wait " + soakLabel(minutes) + " after a build is published", here));
        }

        // A window someone typed that is not one of the presets still has to be visible.
        if (custom) {
            out.append(chosen(soakLabel(plugin.soakMinutes())));
        }

        return out.build();
    }

    private static Component holdChoices(TrackedPlugin plugin, String key, String here) {

        return Component.text()
                .append(choice("offered", "/catalog unhold " + key, !plugin.isPinned(),
                        "Let this plugin be updated", here))
                .append(choice("held", "/catalog hold " + key, plugin.isPinned(),
                        "Freeze it at " + plugin.versionNumber() + " and stop offering updates", here))
                .build();
    }

    private static Component choice(String label, String command, boolean selected,
                                    String description, String here) {

        if (selected) {
            return chosen(label);
        }

        return Permitted.link(Component.text(label, MUTED).append(Component.text("  ", MUTED)),
                from(command, here), explain(description, command), null);
    }

    /** The value a setting currently holds. */
    private static Component chosen(String label) {
        return Component.text(label, BRAND).append(Component.text("  ", MUTED));
    }

    private static Component setting(String label, Component values) {

        return line()
                .append(Component.text(INDENT + label + ":  ", MUTED))
                .append(values)
                .build();
    }

}
