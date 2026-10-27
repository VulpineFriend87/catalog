package top.vulpine.catalog.modrinth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.catalog.json.Json;
import top.vulpine.catalog.modrinth.model.GameVersionTag;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GameVersions")
class GameVersionsTest {

    /** What Modrinth knew on 2026-10-26, newest first, trimmed to what the tests need. */
    private static final GameVersions KNOWN = new GameVersions(tags(
            "26.4-snapshot-5:snapshot", "26.4-snapshot-4:snapshot",
            "26.3", "26.3-pre-1:snapshot", "26.2", "26.1.2", "26.1.1", "26.1",
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4",
            "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16"));

    @Test
    @DisplayName("an unbroken stretch across minor versions reads as one range")
    void stretchAcrossMinorVersions() {

        List<String> declared = new ArrayList<>(List.of("26.3", "26.2", "26.1", "26.1.1", "26.1.2", "1.18.2"));
        declared.addAll(range("1.21", 11));
        declared.addAll(range("1.20", 6));
        declared.addAll(range("1.19", 4));

        assertEquals("1.18.2–26.3", KNOWN.describe(declared));
    }

    @Test
    @DisplayName("every release of one minor version reads as .x")
    void wholeMinorVersion() {
        assertEquals("1.20.x", KNOWN.describe(range("1.20", 6)));
    }

    @Test
    @DisplayName("an unbroken part of a minor version reads as a range")
    void partOfAMinorVersion() {
        assertEquals("1.16.3–1.16.5", KNOWN.describe(List.of("1.16.3", "1.16.4", "1.16.5")));
    }

    @Test
    @DisplayName("the newest snapshot leads when it is newer than every release")
    void newestSnapshotFirst() {
        assertEquals("26.4-snapshot-5, 26.3",
                KNOWN.describe(List.of("26.3", "26.4-snapshot-4", "26.4-snapshot-5")));
    }

    @Test
    @DisplayName("a gap splits a minor version into ranges")
    void gapInAMinorVersion() {
        assertEquals("1.21.9–1.21.11, 1.21.4",
                KNOWN.describe(List.of("1.21.4", "1.21.9", "1.21.10", "1.21.11")));
    }

    @Test
    @DisplayName("versions Modrinth does not know are listed as declared")
    void unknownVersions() {
        assertEquals("1.21.4, 1.21.5", new GameVersions(List.of()).describe(List.of("1.21.4", "1.21.5")));
    }

    private static List<String> range(String minor, int patches) {

        List<String> out = new ArrayList<>(List.of(minor));

        for (int patch = 1; patch <= patches; patch++) {
            out.add(minor + "." + patch);
        }

        return out;
    }

    private static List<GameVersionTag> tags(String... versions) {

        List<GameVersionTag> out = new ArrayList<>();

        for (String version : versions) {
            String[] parts = version.split(":");
            out.add(Json.gson().fromJson("{\"version\": \"%s\", \"version_type\": \"%s\"}"
                    .formatted(parts[0], parts.length > 1 ? parts[1] : "release"), GameVersionTag.class));
        }

        return out;
    }

}
