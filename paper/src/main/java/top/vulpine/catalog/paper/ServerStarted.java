package top.vulpine.catalog.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import top.vulpine.catalog.tracking.EnableCheck;

/**
 * Tells the enable check when the server has finished starting.
 */
public final class ServerStarted implements Listener {

    private final EnableCheck check;

    public ServerStarted(EnableCheck check) {
        this.check = check;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {

        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            check.started();
        }
    }

}
