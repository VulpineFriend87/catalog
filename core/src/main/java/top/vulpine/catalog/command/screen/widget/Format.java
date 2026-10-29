package top.vulpine.catalog.command.screen.widget;

import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.tracking.model.TrackedPlugin;

import java.time.Duration;
import java.time.Instant;

/**
 * Numbers, times and names as screens write them.
 */
public final class Format {

    private Format() {
    }

    /**
     * The name an untracked jar is shown under.
     *
     * @param jar the file
     * @return the name from its plugin.yml, or the file name
     */
    public static String shownName(InstalledJar jar) {

        String name = jar.info() == null ? null : jar.info().pluginName();
        return name == null || name.isBlank() ? jar.fileName() : name;
    }

    public static String quoted(String value) {
        return value.contains(" ") ? "\"" + value + "\"" : value;
    }

    public static String soakLabel(int minutes) {

        if (minutes <= 0) {
            return "none";
        }

        if (minutes < 60) {
            return minutes + "m";
        }

        return minutes % 60 == 0 ? (minutes / 60) + "h" : (minutes / 60) + "h" + (minutes % 60) + "m";
    }

    /**
     * The hover line naming the downloaded build an update would replace.
     */
    public static String replaces(TrackedPlugin plugin) {
        return plugin.pendingRestart() && plugin.stagedVersionNumber() != null
                ? "\nReplaces " + plugin.stagedVersionNumber() : "";
    }

    public static String key(TrackedPlugin plugin) {
        return plugin.slug() != null ? plugin.slug() : plugin.displayName();
    }

    public static String compact(int count) {

        if (count >= 1_000_000) {
            return Math.round(count / 100_000.0) / 10.0 + "M";
        }

        if (count >= 10_000) {
            return (count / 1_000) + "K";
        }

        if (count >= 1_000) {
            return Math.round(count / 100.0) / 10.0 + "K";
        }

        return String.valueOf(count);
    }

    public static String ago(Instant when) {

        if (when == null) {
            return "unknown";
        }

        long minutes = Duration.between(when, Instant.now()).toMinutes();

        if (minutes < 60) {
            return Math.max(minutes, 0) + "m ago";
        }

        long hours = minutes / 60;
        return hours < 24 ? hours + "h ago" : (hours / 24) + "d ago";
    }

}
