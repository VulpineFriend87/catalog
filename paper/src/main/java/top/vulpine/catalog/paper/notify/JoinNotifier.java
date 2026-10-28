package top.vulpine.catalog.paper.notify;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import top.vulpine.catalog.command.service.JoinNews;
import top.vulpine.catalog.paper.CatalogPaper;
import top.vulpine.catalog.paper.PaperViewer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Tells staff about updates when they join.
 */
public final class JoinNotifier implements Listener {

    private static final Duration DELAY = Duration.ofSeconds(3);

    /** How many times to wait for the first update check before giving up on this join. */
    private static final int ATTEMPTS = 10;

    private final CatalogPaper paper;

    private final JoinNews news;

    public JoinNotifier(CatalogPaper paper, JoinNews news) {
        this.paper = paper;
        this.news = news;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        if (!paper.getConfiguration().updates.notifyOnJoin || !new PaperViewer(player).has("notify")) {
            return;
        }

        later(player, ATTEMPTS);
    }

    private void later(Player player, int attempts) {
        paper.getScheduler().runAtEntityLater(player, () -> tell(player, attempts),
                DELAY.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void tell(Player player, int attempts) {

        if (!player.isOnline()) {
            return;
        }

        // No answer before the first check.
        if (!news.ready()) {

            if (attempts > 1) {
                later(player, attempts - 1);
            }

            return;
        }

        news.tell(new PaperViewer(player));
    }

}
