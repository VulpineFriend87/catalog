package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The result lines of actions.
 */
public final class Outcomes {

    private Outcomes() {
    }

    /**
     * The line after a rollback that turned auto-update off.
     *
     * @param from the build rolled back from
     * @param key  what the settings command takes
     */
    public static Component autoUpdateTurnedOff(String from, String key) {

        String command = "/catalog settings " + key;

        Component info = Component.text(
                "Turned off by the rollback, so " + from + " is not downloaded again", TEXT);

        return Permitted.link(Component.text("Auto-update was turned off", MUTED), command,
                info.append(Component.newline()).append(explain("Open settings", command)), info);
    }

    public static Component staged(String name, String version) {
        return line()
                .append(Component.text(name + " " + version, TEXT))
                .append(Component.text(" downloaded, applies on restart", MUTED))
                .build();
    }

    public static Component stagedAll(int count) {
        return line()
                .append(Component.text(count, DONE))
                .append(Component.text(count == 1 ? " update downloaded, applies on restart"
                        : " updates downloaded, apply on restart", MUTED))
                .build();
    }

    public static Component installedUntracked(String name, String fileName) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" is installed, untracked  ", MUTED))
                .append(button("Track", "/catalog track " + quoted(fileName), BRAND, "Track " + name))
                .build();
    }

    public static Component alreadyInstalled(TrackedPlugin plugin) {
        return line()
                .append(Component.text(plugin.displayName(), TEXT))
                .append(Component.text(" is already installed  ", MUTED))
                .append(button("Versions", "/catalog versions " + key(plugin), BRAND,
                        "Pick a different build to switch to"))
                .build();
    }

    /**
     * @param dependencies the names of the plugins installed with it
     */
    public static Component installedWith(String name, String version, List<String> dependencies) {

        TextComponent.Builder out = line().append(Component.text(name + " " + version, TEXT));
        int named = Math.min(dependencies.size(), NAMED);

        for (int i = 0; i < named; i++) {
            boolean last = i == named - 1 && dependencies.size() <= NAMED;
            out.append(Component.text(last ? " and " : ", ", MUTED))
                    .append(Component.text(dependencies.get(i), TEXT));
        }

        if (dependencies.size() > NAMED) {
            out.append(Component.text(" and " + (dependencies.size() - NAMED) + " more", MUTED));
        }

        return out.append(Component.text(" installed, load on restart", MUTED)).build();
    }

    /** How many installed dependencies are named before the rest are counted. */
    private static final int NAMED = 3;

    public static Component installed(String name, String version) {
        return line()
                .append(Component.text(name + " " + version, TEXT))
                .append(Component.text(" installed, loads on restart", MUTED))
                .build();
    }

    /**
     * @param loaded false for a fresh install the server has not loaded yet
     */
    public static Component removed(String name, boolean loaded, TrashEntry entry, String from) {

        TextComponent.Builder out = line()
                .append(Component.text(name, TEXT))
                .append(Component.text(loaded ? " moved to trash, unloads on restart" : " moved to trash",
                        MUTED));

        if (entry != null) {
            out.append(Component.space()).append(button("Undo", TrashPage.restoreCommand(entry, from), BRAND,
                    "Put " + name + " back"));
        }

        return out.build();
    }

    public static Component restored(String name, boolean tracked) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(tracked ? " restored" : " restored, not tracked", MUTED))
                .build();
    }

    public static Component nothingToRestore() {
        return Component.text("That removal is not in the trash any more", MUTED);
    }

    public static Component discarded(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" deleted", MUTED))
                .build();
    }

    public static Component emptied(int count) {
        return line()
                .append(Component.text(count, TEXT))
                .append(Component.text(count == 1 ? " removal deleted"
                        : " removals deleted", MUTED))
                .build();
    }

    public static Component trashAlreadyEmpty() {
        return Component.text("The trash is already empty", MUTED);
    }

    public static Component channelSet(String name, ReleaseChannel channel) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" now follows ", MUTED))
                .append(Component.text(channel.apiName(), BRAND))
                .build();
    }

    public static Component autoSet(String name, boolean on) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(on ? " automatically updates itself"
                        : " only updates manually", MUTED))
                .build();
    }

    public static Component soakSet(String name, int minutes, int defaultSoak) {

        boolean inherits = minutes == TrackedPlugin.INHERIT_SOAK;

        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" waits ", MUTED))
                .append(Component.text(soakLabel(inherits ? defaultSoak : minutes), BRAND))
                .append(Component.text(" before updating itself", MUTED))
                .build();
    }

    public static Component held(String name, boolean held) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(held ? " held at this version" : " no longer held", MUTED))
                .build();
    }

    public static Component untracked(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" untracked, the jar is not removed", MUTED))
                .build();
    }

    public static Component tracked(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" is now tracked", MUTED))
                .build();
    }

    public static Component reloaded() {
        return Component.text("Configuration reloaded", MUTED);
    }

    public static Component upToDate() {
        return Component.text("Nothing to update", MUTED);
    }

    public static Component noUpdate(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" is already up to date", MUTED))
                .build();
    }

    public static Component stillWaiting() {
        return Component.text("Still waiting for Modrinth", MUTED);
    }

    public static Component unknownKind(String typed) {
        return line()
                .append(Component.text("No event kind called ", DANGER))
                .append(Component.text(typed, TEXT))
                .build();
    }

    public static Component unknownPlugin(String query) {
        return line()
                .append(Component.text("No plugin found for ", DANGER))
                .append(Component.text(query, TEXT))
                .build();
    }

    public static Component cannotRemoveSelf(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" cannot remove itself. Delete the jar by hand.", MUTED))
                .build();
    }

    public static Component nothingTracked() {
        return Component.text("No plugins tracked", MUTED);
    }

    public static Component stillHeld(String name) {
        return Component.text(name + " is held at its current version. Unhold it first.", DANGER);
    }

    public static Component noBuild(String title) {
        return Component.text(title + " has no build for this server", DANGER);
    }

    public static Component noVersion(String title, String named) {
        return Component.text("No build of " + title + " called " + named, DANGER);
    }

    public static Component unreachable(String reason) {
        return Component.text("Could not reach Modrinth: " + reason, DANGER);
    }

    public static Component configFailed() {
        return Component.text("Could not read config.yml, check the console for errors.", DANGER);
    }

    public static Component badSoak() {
        return Component.text("Invalid soak time. Valid examples: 30m, 2h, 12h", DANGER);
    }

    public static Component stageFailed(String name, String reason) {
        return Component.text(name + ": " + reason, DANGER);
    }

    public static Component cancelled(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" update dropped", MUTED))
                .build();
    }

    public static Component nothingStaged(String name) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" has no update to cancel", MUTED))
                .build();
    }

    public static Component needsDependencies(String name, int missing) {
        return line()
                .append(Component.text(name, TEXT))
                .append(Component.text(" needs " + missing + " plugin"
                        + (missing == 1 ? "" : "s") + " that are not installed", MUTED))
                .build();
    }

    public static Component failed(String reason) {
        return Component.text(reason, DANGER);
    }

}
