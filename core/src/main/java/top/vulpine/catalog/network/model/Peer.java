package top.vulpine.catalog.network.model;

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

}
