package top.vulpine.catalog.history;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.time.Instant;
import java.util.List;

/**
 * One history entry.
 *
 * <p>Serialized to {@code history.json} and bound by field name.</p>
 */
@Getter
@Builder
@Accessors(fluent = true)
public final class HistoryEntry {

    private Instant at;

    private Event event;

    /** Absent when the event is not about one plugin. */
    private String projectId;

    /** The name the plugin had when this happened. */
    private String name;

    private String slug;

    /** Absent when Catalog did it automatically. */
    private String by;

    private String from;

    private String to;

    private String channel;

    /** The new value of a setting. */
    private String value;

    /** How many plugins one row stands for. */
    private int count;

    /** The names behind {@link #count}. */
    private List<String> names;

    /**
     * @return true if a person did this
     */
    public boolean byPerson() {
        return by != null && !by.isBlank();
    }

    private static HistoryEntryBuilder about(TrackedPlugin plugin, String by) {
        return builder()
                .at(Instant.now())
                .projectId(plugin.projectId())
                .name(plugin.displayName())
                .slug(plugin.slug())
                .by(by);
    }

    /**
     * @param requiredBy the plugin that pulled this one in, or null when it was asked for outright
     */
    public static HistoryEntry installed(TrackedPlugin plugin, String by, String requiredBy) {
        return about(plugin, by)
                .event(requiredBy == null ? Event.INSTALLED : Event.INSTALLED_AS_DEPENDENCY)
                .to(plugin.versionNumber())
                .channel(plugin.channel() == null ? null : plugin.channel().apiName())
                .value(requiredBy)
                .build();
    }

    public static HistoryEntry staged(TrackedPlugin plugin, ModrinthVersion version, String by,
                                      Event as) {
        return about(plugin, by)
                .event(as)
                .from(plugin.versionNumber())
                .to(version.versionNumber())
                .channel(version.versionType() == null ? null : version.versionType().apiName())
                .build();
    }

    public static HistoryEntry cancelled(TrackedPlugin plugin, String by) {
        return about(plugin, by)
                .event(Event.UPDATE_CANCELLED)
                .from(plugin.versionNumber())
                .build();
    }

    public static HistoryEntry trashed(TrackedPlugin plugin, String by) {
        return about(plugin, by)
                .event(Event.TRASHED)
                .from(plugin.versionNumber())
                .build();
    }

    public static HistoryEntry restored(TrackedPlugin plugin, String by) {
        return about(plugin, by)
                .event(Event.RESTORED)
                .to(plugin.versionNumber())
                .build();
    }

    public static HistoryEntry heldBack(TrackedPlugin plugin, ModrinthVersion version, int missing,
                                        List<String> names) {
        return about(plugin, null)
                .event(Event.UPDATE_HELD_BACK)
                .from(plugin.versionNumber())
                .to(version.versionNumber())
                .count(missing)
                .names(names)
                .build();
    }

    public static HistoryEntry autoUpdateFailed(TrackedPlugin plugin, ModrinthVersion version,
                                                String reason) {
        return about(plugin, null)
                .event(Event.AUTO_UPDATE_FAILED)
                .from(plugin.versionNumber())
                .to(version.versionNumber())
                .value(reason)
                .build();
    }

    /**
     * A downloaded build a restart left in the update folder.
     */
    public static HistoryEntry notApplied(TrackedPlugin plugin) {
        return about(plugin, null)
                .event(Event.UPDATE_NOT_APPLIED)
                .from(plugin.versionNumber())
                .build();
    }

    /**
     * A plugin the server did not enable after an update or install.
     *
     * @param updated false for a first install, true for an update, switch or rollback
     * @param reason  what the server reported
     */
    public static HistoryEntry notEnabled(TrackedPlugin plugin, boolean updated, String reason) {
        return about(plugin, null)
                .event(updated ? Event.UPDATE_NOT_ENABLED : Event.INSTALL_NOT_ENABLED)
                .from(updated ? plugin.previousVersionNumber() : null)
                .to(plugin.versionNumber())
                .value(reason)
                .build();
    }

    /**
     * A downloaded build that left the update folder without a restart applying it.
     */
    public static HistoryEntry lost(TrackedPlugin plugin) {
        return about(plugin, null)
                .event(Event.UPDATE_LOST)
                .from(plugin.versionNumber())
                .build();
    }

    /**
     * A removal deleted for good. The name comes from the trash entry.
     */
    public static HistoryEntry deleted(TrashEntry entry, String by) {
        return builder()
                .at(Instant.now())
                .event(Event.DELETED)
                .projectId(entry.projectId())
                .name(entry.displayName())
                .slug(entry.slug())
                .from(entry.versionNumber())
                .by(by)
                .build();
    }

    public static HistoryEntry setting(TrackedPlugin plugin, Event event, String value, String by) {
        return about(plugin, by).event(event).value(value).build();
    }

    /**
     * A staged build the restart put in place.
     *
     * <p>Nobody is credited.</p>
     */
    public static HistoryEntry applied(TrackedPlugin plugin) {
        return about(plugin, null)
                .event(Event.UPDATES_APPLIED)
                .to(plugin.versionNumber())
                .build();
    }

    public static HistoryEntry letGo(TrackedPlugin plugin, String by) {
        return about(plugin, by).event(Event.UNTRACKED).to(plugin.versionNumber()).build();
    }

    /**
      * A plugin the scan or a person started tracking.
      */
    public static HistoryEntry adopted(TrackedPlugin plugin, String by) {
        return about(plugin, by).event(Event.ADOPTED).to(plugin.versionNumber()).build();
    }

    public static HistoryEntry found(TrackedPlugin plugin, Event event) {
        return about(plugin, null).event(event).to(plugin.versionNumber()).build();
    }

    public static HistoryEntry many(Event event, int count, List<String> names, String by) {
        return builder()
                .at(Instant.now())
                .event(event)
                .count(count)
                .names(names)
                .by(by)
                .build();
    }

}
