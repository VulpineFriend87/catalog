package top.vulpine.catalog.network.model;

import top.vulpine.catalog.update.model.ServerPlatform;

import java.util.List;

/**
 * A server on the network as this one sees it.
 *
 * @param online    whether it holds its name right now
 * @param inventory what it published last
 */
public record Peer(String name, boolean online, Inventory inventory) {

    /**
     * Whether any of its plugins has an update on offer.
     */
    public boolean hasUpdates() {

        for (Inventory.Plugin plugin : inventory.plugins()) {
            if (plugin.update() != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the platform it runs, or null when this Catalog does not know it
     */
    public ServerPlatform platform() {

        for (ServerPlatform platform : ServerPlatform.values()) {
            if (platform.id().equals(inventory.platform())) {
                return platform;
            }
        }

        return null;
    }

    /**
     * Whether something published for these loaders and Minecraft versions runs here.
     *
     * @param gameVersions the Minecraft versions it declares. A proxy accepts any.
     */
    public boolean runs(List<String> loaders, List<String> gameVersions) {

        ServerPlatform platform = platform();

        if (platform == null || loaders == null || loaders.stream().noneMatch(platform.loaders()::contains)) {
            return false;
        }

        return platform.isProxy() || gameVersions == null || gameVersions.contains(inventory.serverVersion());
    }

}
