package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.command.screen.widget.Widgets;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

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
                .append(title(plugin.displayName(), BRAND))
                .append(Component.text("  settings", MUTED))
                .build());

        out.add(line()
                .append(Component.text(INDENT + plugin.versionNumber(), MUTED))
                .append(Component.text(" · ", MUTED))
                .append(VersionsPage.channel(plugin.channel()))
                .build());

        out.add(Component.empty());

        out.add(field("Channel", channelChoices(plugin, key, here)));
        out.add(field("Auto-update", autoChoices(plugin, key, here)));

        // Soak only applies to automatic updates.
        if (plugin.autoUpdate()) {
            out.add(field("Soak", soakChoices(plugin, key, defaultSoak, here)));
        }

        out.add(field("Updates", holdChoices(plugin, key, here)));

        out.add(Component.empty());

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

        // A typed window that is not a preset is shown too.
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
        return selected ? chosen(label) : Widgets.choice(label, from(command, here), description);
    }

}
