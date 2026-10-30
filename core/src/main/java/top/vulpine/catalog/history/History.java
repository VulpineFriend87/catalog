package top.vulpine.catalog.history;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.json.Json;
import top.vulpine.commons.log.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes {@code history.json}.
 *
 * <p>Newest first and capped. A failed write never fails an action.</p>
 */
public final class History {

    private static final Type ENTRY_LIST = new TypeToken<List<HistoryEntry>>() {
    }.getType();

    /** How many entries are kept before the oldest is dropped. */
    public static final int LIMIT = 200;

    private final Path file;
    private final List<HistoryEntry> entries = new ArrayList<>();

    public History(Path file) {
        this.file = file;
    }

    /**
     * Loads the file. Replaces what is held.
     */
    public void load() {

        entries.clear();

        if (!Files.isRegularFile(file)) {
            return;
        }

        String content;

        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.warn(CatalogAction.TRACK, "Could not read " + file.getFileName()
                    + ", history starts empty.");
            return;
        }

        if (content.isBlank()) {
            return;
        }

        try {

            List<HistoryEntry> read = Json.gson().fromJson(content, ENTRY_LIST);

            if (read != null) {
                for (HistoryEntry entry : read) {
                    if (entry != null && entry.at() != null && entry.event() != null) {
                        entries.add(entry);
                    }
                }
            }

        } catch (JsonSyntaxException e) {
            Logger.warn(CatalogAction.TRACK, "Could not parse " + file.getFileName()
                    + ", history starts empty.");
        }
    }

    /**
     * Records one event.
     *
     * <p>A failed write is logged and dropped.</p>
     *
     * @param entry what happened
     */
    public void add(HistoryEntry entry) {

        synchronized (entries) {

            entries.add(0, entry);

            while (entries.size() > LIMIT) {
                entries.remove(entries.size() - 1);
            }
        }

        try {
            save();
        } catch (IOException e) {
            Logger.warn(CatalogAction.TRACK, "Could not write " + file.getFileName() + ": "
                    + e.getMessage());
        }
    }

    /**
     * Everything recorded. Newest first.
     */
    public List<HistoryEntry> all() {

        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    /**
     * The entries matching both filters. Newest first.
     *
     * @param projectId only this plugin, or null for every plugin
     * @param by        only this author, or null for everyone. "catalog" is what Catalog did
     *                  automatically.
     * @return the matching entries
     */
    public List<HistoryEntry> filter(String projectId, String by) {
        return filter(all(), projectId, by);
    }

    /**
     * The entries of any log matching both filters.
     *
     * @param projectId only this plugin, or null for every plugin
     * @param by        only this author, or null for everyone. "catalog" is what Catalog did
     *                  automatically.
     */
    public static List<HistoryEntry> filter(List<HistoryEntry> entries, String projectId, String by) {

        List<HistoryEntry> matching = new ArrayList<>();
        boolean wantsCatalog = by != null && by.equalsIgnoreCase("catalog");

        for (HistoryEntry entry : entries) {

            if (projectId != null && !projectId.equals(entry.projectId())) {
                continue;
            }

            if (by != null) {

                if (wantsCatalog) {
                    if (entry.byPerson()) {
                        continue;
                    }
                } else if (!entry.byPerson()
                        || !entry.by().toLowerCase(Locale.ROOT).equals(by.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }

            matching.add(entry);
        }

        return Collections.unmodifiableList(matching);
    }

    /**
     * The project a name, slug or id refers to among the plugins this log mentions.
     *
     * <p>Finds plugins that are no longer installed.</p>
     *
     * @param query what was typed
     * @return the project id, or null when the log has never mentioned it
     */
    public String projectIdFor(String query) {
        return projectIdFor(all(), query);
    }

    /**
     * The project a name, slug or id refers to among the plugins any log mentions.
     *
     * @return the project id, or null when the log has never mentioned it
     */
    public static String projectIdFor(List<HistoryEntry> entries, String query) {

        if (query == null || query.isBlank()) {
            return null;
        }

        String wanted = query.trim();

        for (HistoryEntry entry : entries) {

            if (entry.projectId() == null) {
                continue;
            }

            if (wanted.equalsIgnoreCase(entry.name()) || wanted.equalsIgnoreCase(entry.slug())
                    || wanted.equalsIgnoreCase(entry.projectId())) {
                return entry.projectId();
            }
        }

        return null;
    }

    /**
     * Every plugin this log mentions. Newest first.
     */
    public List<String> plugins() {

        List<String> names = new ArrayList<>();

        for (HistoryEntry entry : all()) {

            String name = entry.slug() != null ? entry.slug() : entry.name();

            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }

        return names;
    }

    /**
     * @return when the oldest kept entry happened, or null when there are none
     */
    public Instant oldest() {

        List<HistoryEntry> all = all();
        return all.isEmpty() ? null : all.get(all.size() - 1).at();
    }

    private void save() throws IOException {

        Json.write(file, all(), ENTRY_LIST);
    }

}
