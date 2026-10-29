package top.vulpine.catalog.command.service;

import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.screen.JoinNotice;
import top.vulpine.catalog.command.screen.Notice;
import top.vulpine.catalog.platform.Viewer;
import top.vulpine.catalog.tracking.EnableCheck;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.model.UpdateCandidate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static top.vulpine.catalog.command.service.Chat.send;

/**
 * Builds the join notice for a viewer.
 *
 * <p>Open updates are repeated on every join. What the last start did is told once per viewer.</p>
 */
public final class JoinNews {

    /** How long after a join the notice is sent. */
    private static final Duration DELAY = Duration.ofSeconds(3);

    /** How many times to wait for the first update check before giving up on a join. */
    private static final int ATTEMPTS = 10;

    private final Catalog catalog;

    /**
     * What each viewer has heard this session.
     */
    private final Map<String, Set<String>> heard = new ConcurrentHashMap<>();

    public JoinNews(Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Sends the join notice shortly after a viewer joins. Waits for the first update check.
     *
     * @param online whether the viewer is still connected
     */
    public void greet(Viewer viewer, BooleanSupplier online) {

        if (catalog.getConfiguration().get().updates.notifyOnJoin && viewer.has("notify")) {
            greet(viewer, online, ATTEMPTS);
        }
    }

    private void greet(Viewer viewer, BooleanSupplier online, int attempts) {

        catalog.getPlatform().later(() -> {

            if (!online.getAsBoolean()) {
                return;
            }

            // No answer before the first check.
            if (catalog.getUpdates().checkedAt() == null) {

                if (attempts > 1) {
                    greet(viewer, online, attempts - 1);
                }

                return;
            }

            tell(viewer);
        }, DELAY);
    }

    /**
     * Sends what this viewer has not heard yet.
     */
    private void tell(Viewer viewer) {

        Set<String> heardBefore = heard.computeIfAbsent(viewer.name(), name -> ConcurrentHashMap.newKeySet());
        Set<String> news = new HashSet<>();

        List<Notice> notices = notices(heardBefore, news);

        if (notices.isEmpty()) {
            return;
        }

        send(viewer, JoinNotice.notice(notices, hasAvailable(notices),
                catalog.getPlatform().target().platform().isProxy()));
        heardBefore.addAll(news);
    }

    /**
     * @param heardBefore the news this viewer has already been told
     * @param news        filled with the news this call reports
     */
    private List<Notice> notices(Set<String> heardBefore, Set<String> news) {

        catalog.noticeLoaded();

        List<Notice> out = new ArrayList<>();

        List<Notice.Item> notEnabled = new ArrayList<>();
        Set<String> failed = new HashSet<>();

        for (EnableCheck.Failure failure : catalog.getEnableCheck().failures()) {

            TrackedPlugin tracked = failure.plugin();
            failed.add(tracked.projectId());
            String key = "not-enabled:" + tracked.projectId() + ":" + tracked.versionId();

            if (heardBefore.contains(key)) {
                continue;
            }

            news.add(key);

            boolean back = failure.canRollBack();

            notEnabled.add(new Notice.Item(tracked.displayName(),
                    back ? tracked.previousVersionNumber() + " → " + tracked.versionNumber()
                            : tracked.versionNumber(),
                    failure.reason(),
                    back ? JoinNotice.rollbackCommand(tracked) : null,
                    tracked.previousVersionNumber(),
                    failure.previousRemoved()));
        }

        add(out, Notice.Kind.DID_NOT_ENABLE, notEnabled);

        Set<TrackedPlugin> missed = new HashSet<>();
        List<Notice.Item> missedNews = new ArrayList<>();

        for (TrackedPlugin tracked : catalog.getLibrary().notApplied()) {

            if (!tracked.pendingRestart()) {
                continue;
            }

            missed.add(tracked);

            String key = "missed:" + tracked.projectId() + ":" + tracked.stagedVersionId();

            if (!heardBefore.contains(key)) {
                news.add(key);
                missedNews.add(new Notice.Item(tracked.displayName(),
                        tracked.versionNumber() + " is still installed"));
            }
        }

        add(out, Notice.Kind.DID_NOT_APPLY, missedNews);

        Map<String, String> failures = catalog.getUpdates().failures();
        List<Notice.Item> autoFailed = new ArrayList<>();
        List<Notice.Item> available = new ArrayList<>();

        for (UpdateCandidate candidate : catalog.openUpdates()) {

            String builds = candidate.from() + " → " + candidate.to();
            String reason = failures.get(candidate.plugin().projectId());

            (reason == null ? available : autoFailed).add(
                    new Notice.Item(candidate.plugin().displayName(), builds, reason));
        }

        add(out, Notice.Kind.AUTO_UPDATE_FAILED, autoFailed);

        List<Notice.Item> applied = new ArrayList<>();

        for (TrackedPlugin tracked : catalog.getLibrary().applied()) {

            String key = "applied:" + tracked.projectId() + ":" + tracked.versionId();

            if (!heardBefore.contains(key) && !failed.contains(tracked.projectId())) {
                news.add(key);
                applied.add(new Notice.Item(tracked.displayName(), tracked.versionNumber()));
            }
        }

        add(out, Notice.Kind.APPLIED, applied);

        List<TrackedPlugin> waiting = new ArrayList<>();

        for (TrackedPlugin tracked : catalog.getTracking().all()) {
            if (tracked.awaitingRestart() && !missed.contains(tracked)) {
                waiting.add(tracked);
            }
        }

        add(out, Notice.Kind.WAITING, names(waiting));
        add(out, Notice.Kind.AVAILABLE, available);

        return out;
    }

    private static void add(List<Notice> out, Notice.Kind kind, List<Notice.Item> plugins) {

        if (plugins.isEmpty()) {
            return;
        }

        List<Notice.Item> sorted = new ArrayList<>(plugins);
        sorted.sort(Comparator.comparing(item -> item.name().toLowerCase(Locale.ROOT)));
        out.add(new Notice(kind, sorted));
    }

    private static List<Notice.Item> names(Iterable<TrackedPlugin> plugins) {

        List<Notice.Item> names = new ArrayList<>();

        for (TrackedPlugin tracked : plugins) {
            names.add(new Notice.Item(tracked.displayName(), null));
        }

        return names;
    }

    private static boolean hasAvailable(List<Notice> notices) {

        for (Notice notice : notices) {
            if (notice.kind() == Notice.Kind.AVAILABLE) {
                return true;
            }
        }

        return false;
    }

}
