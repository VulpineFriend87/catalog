package top.vulpine.catalog.paper.notify;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import top.vulpine.catalog.command.service.JoinNews;
import top.vulpine.catalog.paper.PaperViewer;

/**
 * Tells staff about updates when they join.
 */
public final class JoinNotifier implements Listener {

    private final JoinNews news;

    public JoinNotifier(JoinNews news) {
        this.news = news;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        news.greet(new PaperViewer(player), player::isOnline);
    }

}
