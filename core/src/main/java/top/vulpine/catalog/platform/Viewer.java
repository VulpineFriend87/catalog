package top.vulpine.catalog.platform;

import net.kyori.adventure.text.Component;

/**
 * A player or the console a screen is shown to.
 */
public interface Viewer {

    /**
     * @return the name buttons and confirmations are remembered under
     */
    String name();

    /**
     * @return false for the console
     */
    boolean isPlayer();

    /**
     * @param node a node below {@code catalog.} such as {@code command.list}
     * @return whether the viewer holds it, a wildcard above it, or any node below it
     */
    boolean has(String node);

    void send(Component line);

}
