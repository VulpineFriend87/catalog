package top.vulpine.catalog.command.argument;

import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.model.UpdateCandidate;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What {@code /catalog list} can be narrowed to.
 */
public enum ListFilter {

    UPDATES,
    RESTART,
    HELD,
    UNTRACKED;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param plugin  a managed plugin
     * @param updates the open updates by project id
     * @return whether this filter keeps it
     */
    public boolean keeps(TrackedPlugin plugin, Map<String, UpdateCandidate> updates) {
        return switch (this) {
            case UPDATES -> updates.containsKey(plugin.projectId()) && !plugin.pendingLoad();
            case RESTART -> plugin.awaitingRestart();
            case HELD -> plugin.isPinned();
            case UNTRACKED -> false;
        };
    }

    /**
     * Keeps a plugin when any filter keeps it. No filters keep every plugin.
     */
    public static boolean keeps(Set<ListFilter> filters, TrackedPlugin plugin,
                                Map<String, UpdateCandidate> updates) {

        if (filters.isEmpty()) {
            return true;
        }

        for (ListFilter filter : filters) {
            if (filter.keeps(plugin, updates)) {
                return true;
            }
        }

        return false;
    }

    public static boolean keepsUntracked(Set<ListFilter> filters) {
        return filters.isEmpty() || filters.contains(UNTRACKED);
    }

    /**
     * @return the switches for these filters with a leading space each
     */
    public static String switches(Set<ListFilter> filters) {

        StringBuilder out = new StringBuilder();

        for (ListFilter filter : filters) {
            out.append(" --").append(filter.label());
        }

        return out.toString();
    }

}
