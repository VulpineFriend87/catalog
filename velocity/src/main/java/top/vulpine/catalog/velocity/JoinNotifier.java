package top.vulpine.catalog.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import top.vulpine.catalog.command.service.JoinNews;

/**
 * Tells staff about the proxy's updates when they join.
 */
public final class JoinNotifier {

    private final JoinNews news;

    public JoinNotifier(JoinNews news) {
        this.news = news;
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {

        Player player = event.getPlayer();
        news.greet(new VelocityViewer(player), player::isActive);
    }

}
