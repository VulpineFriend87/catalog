package top.vulpine.catalog.network.model;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.model.UpdateCandidate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What one server publishes to the network.
 */
@Getter
@Builder
@Accessors(fluent = true)
public final class Inventory {

    private String server;

    /** The platform's Modrinth loader id. */
    private String platform;

    /** The Minecraft version, or null on a proxy. */
    private String minecraft;

    /** The Catalog version the server runs. */
    private String catalog;

    private Instant publishedAt;

    private List<Plugin> plugins;

    /** How many jars Catalog could manage but does not. */
    private int untracked;

    /**
     * @param updates the open updates by project id
     */
    public static List<Plugin> plugins(Collection<TrackedPlugin> tracked, Map<String, UpdateCandidate> updates) {

        List<Plugin> out = new ArrayList<>();

        for (TrackedPlugin plugin : tracked) {

            UpdateCandidate update = updates.get(plugin.projectId());

            out.add(Plugin.builder()
                    .projectId(plugin.projectId())
                    .slug(plugin.slug())
                    .name(plugin.displayName())
                    .version(plugin.versionNumber())
                    .update(update == null ? null : update.to())
                    .channel(plugin.channel())
                    .autoUpdate(plugin.autoUpdate())
                    .held(plugin.isPinned())
                    .waiting(plugin.awaitingRestart())
                    .build());
        }

        return out;
    }

    /**
     * One managed plugin.
     */
    @Getter
    @Builder
    @Accessors(fluent = true)
    public static final class Plugin {

        private String projectId;
        private String slug;
        private String name;
        private String version;

        /** The version on offer, or null when it is up to date. */
        private String update;

        private ReleaseChannel channel;
        private boolean autoUpdate;
        private boolean held;

        /** Whether it waits for a restart. */
        private boolean waiting;

    }

}
