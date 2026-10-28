package top.vulpine.catalog.paper.command;

import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.paper.command.annotation.RequiresPermission;
import top.vulpine.catalog.paper.command.flow.Lookup;
import top.vulpine.catalog.paper.command.flow.Redraw;
import top.vulpine.catalog.paper.command.flow.Screens;
import top.vulpine.catalog.paper.command.screen.Outcomes;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.util.Locale;

import static top.vulpine.catalog.paper.command.flow.Chat.send;

/**
 * One plugin's settings.
 */
public final class SettingsCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Lookup lookup;

    private final Screens screens;

    private final Redraw redraw;

    public SettingsCommand(Catalog catalog, ClickContext context, Lookup lookup, Screens screens, Redraw redraw) {
        this.catalog = catalog;
        this.context = context;
        this.lookup = lookup;
        this.screens = screens;
        this.redraw = redraw;
    }

    @Subcommand("settings")
    @Description("View the settings menu for a plugin")
    @RequiresPermission("command.settings")
    public void settings(CommandSender sender,
                         @Named("plugin") @SuggestWith(Suggestions.Tracked.class) String query) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        screens.showSettings(sender, tracked, Press.screenOf(context.take(sender)));
    }

    @Subcommand("auto")
    @Description("Whether a plugin updates itself automatically")
    @RequiresPermission("command.settings")
    public void auto(CommandSender sender,
                     @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                     @Named("state") Toggle state) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        catalog.setAutoUpdate(tracked, state.on(), sender.getName());
        redraw.then(sender, context.take(sender), Outcomes.autoSet(tracked.displayName(), state.on()));
    }

    @Subcommand("soak")
    @Description("How long a build must be public before this plugin automatically installs it")
    @RequiresPermission("command.settings")
    public void soak(CommandSender sender,
                     @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                     @Named("window") String window) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        Integer minutes = parseSoak(window);

        if (minutes == null) {
            send(sender, Outcomes.badSoak());
            return;
        }

        catalog.setSoak(tracked, minutes, sender.getName());
        redraw.then(sender, context.take(sender), Outcomes.soakSet(tracked.displayName(), minutes,
                catalog.defaultSoakMinutes()));
    }

    /**
     * Reads a soak window.
     *
     * @param window minutes, or a value suffixed with m or h, or "default" to follow the config
     * @return the window in minutes, {@link TrackedPlugin#INHERIT_SOAK} for the default, or null if
     *         it could not be read
     */
    private static Integer parseSoak(String window) {

        String value = ClickContext.strip(window).trim().toLowerCase(Locale.ROOT);

        if (value.equals("default") || value.equals("inherit")) {
            return TrackedPlugin.INHERIT_SOAK;
        }

        int scale = value.endsWith("h") ? 60 : 1;

        if (value.endsWith("h") || value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
        }

        try {
            return Math.max(Integer.parseInt(value.trim()), 0) * scale;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Subcommand("channel")
    @Description("Which builds a plugin should follow")
    @RequiresPermission("command.channel")
    public void channel(CommandSender sender,
                        @Named("plugin") @Single @SuggestWith(Suggestions.Tracked.class) String query,
                        @Named("channel") ReleaseChannel channel) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        catalog.setChannel(tracked, channel, sender.getName());
        redraw.then(sender, context.take(sender), Outcomes.channelSet(tracked.displayName(), channel));
    }

    @Subcommand("hold")
    @Description("Freeze a plugin at its installed version")
    @RequiresPermission("command.hold")
    public void hold(CommandSender sender,
                     @Named("plugin") @SuggestWith(Suggestions.Holdable.class) String query) {
        setHeld(sender, query, true);
    }

    @Subcommand("unhold")
    @Description("Let a plugin be updated again")
    @RequiresPermission("command.hold")
    public void unhold(CommandSender sender,
                       @Named("plugin") @SuggestWith(Suggestions.Held.class) String query) {
        setHeld(sender, query, false);
    }

    private void setHeld(CommandSender sender, String query, boolean held) {

        TrackedPlugin tracked = lookup.resolve(query);

        if (tracked == null) {
            send(sender, Outcomes.unknownPlugin(query));
            return;
        }

        catalog.setHeld(tracked, held, sender.getName());
        redraw.then(sender, context.take(sender), Outcomes.held(tracked.displayName(), held));
    }

}
