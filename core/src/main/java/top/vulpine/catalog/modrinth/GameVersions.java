package top.vulpine.catalog.modrinth;

import top.vulpine.catalog.modrinth.model.GameVersionTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the Minecraft versions a build declares as short ranges.
 *
 * <p>{@code 1.8.9–26.3} is an unbroken stretch of releases. {@code 1.21.x} is every release of
 * one minor version. The newest snapshot goes first.</p>
 */
public final class GameVersions {

    /** Every version Modrinth knows. Newest first. */
    private final List<GameVersionTag> known;

    /** Every release Modrinth knows. Newest first. */
    private final List<String> releases = new ArrayList<>();

    /** How many releases each minor version has. */
    private final Map<String, Integer> minorSizes = new HashMap<>();

    public GameVersions(List<GameVersionTag> known) {

        this.known = List.copyOf(known);

        for (GameVersionTag tag : known) {
            if (tag.isRelease()) {
                releases.add(tag.version());
                minorSizes.merge(minor(tag.version()), 1, Integer::sum);
            }
        }
    }

    /**
     * @return the versions joined with a comma, or the declared list when Modrinth knows none of them
     */
    public String describe(Collection<String> declared) {

        if (declared == null || declared.isEmpty()) {
            return "unknown";
        }

        Set<String> wanted = new HashSet<>(declared);
        Set<String> placed = new HashSet<>(releases);
        List<String> out = new ArrayList<>();

        // The newest version declared if it is not a release.
        for (GameVersionTag tag : known) {

            if (!wanted.contains(tag.version())) {
                continue;
            }

            if (!tag.isRelease()) {
                out.add(tag.version());
            }

            break;
        }

        for (GameVersionTag tag : known) {
            if (!tag.isRelease()) {
                placed.add(tag.version());
            }
        }

        for (List<String> run : runs(wanted)) {
            out.add(name(run));
        }

        for (String version : declared) {
            if (!placed.contains(version)) {
                out.add(version);
            }
        }

        return String.join(", ", out);
    }

    /**
     * @param run consecutive declared releases sorted newest first
     */
    private String name(List<String> run) {

        String newest = run.get(0);
        String oldest = run.get(run.size() - 1);

        if (run.size() == 1) {
            return newest;
        }

        if (minor(newest).equals(minor(oldest)) && run.size() == minorSizes.get(minor(newest))) {
            return minor(newest) + ".x";
        }

        return oldest + "–" + newest;
    }

    /**
     * The unbroken stretches of declared releases. Newest first.
     */
    private List<List<String>> runs(Set<String> wanted) {

        List<List<String>> runs = new ArrayList<>();
        List<String> run = new ArrayList<>();

        for (String version : releases) {

            if (wanted.contains(version)) {
                run.add(version);
                continue;
            }

            if (!run.isEmpty()) {
                runs.add(run);
                run = new ArrayList<>();
            }
        }

        if (!run.isEmpty()) {
            runs.add(run);
        }

        return runs;
    }

    /**
     * @return the first two numbers of a release: {@code 1.21} for 1.21.4, {@code 26.2} for 26.2
     */
    private static String minor(String release) {

        String[] parts = release.split("\\.");
        return parts.length < 2 ? release : parts[0] + "." + parts[1];
    }

}
