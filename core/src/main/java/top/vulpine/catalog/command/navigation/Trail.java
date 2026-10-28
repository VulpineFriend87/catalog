package top.vulpine.catalog.command.navigation;

import java.util.Arrays;

/**
 * A screen token followed by the screens it was opened from, newest first.
 *
 * <p>{@code settings:luckperms<info:luckperms} is the settings page opened from the info page. A
 * screen reopened by Back or redrawn after an action gets its parent back from here.</p>
 */
public final class Trail {

    /** Separates a screen from the one it was opened from. */
    private static final String WITHIN = "<";

    /** How many screens a trail keeps, the current one included. */
    static final int DEPTH = 4;

    private Trail() {
    }

    /**
     * @param screen a screen token
     * @param from   the trail it was opened from, or null
     * @return the screen followed by its trail, the oldest screens past {@link #DEPTH} dropped
     */
    public static String within(String screen, String from) {

        if (from == null || from.isEmpty()) {
            return screen;
        }

        String[] screens = (screen + WITHIN + from).split(WITHIN);
        return String.join(WITHIN, Arrays.copyOf(screens, Math.min(screens.length, DEPTH)));
    }

    /**
     * @return the screen a trail is on, or null for none
     */
    public static String current(String trail) {

        if (trail == null) {
            return null;
        }

        int at = trail.indexOf(WITHIN);
        return at < 0 ? trail : trail.substring(0, at);
    }

    /**
     * @return the trail the current screen was opened from, or null when it was not opened from one
     */
    public static String parent(String trail) {

        if (trail == null) {
            return null;
        }

        int at = trail.indexOf(WITHIN);
        return at < 0 ? null : trail.substring(at + 1);
    }

}
