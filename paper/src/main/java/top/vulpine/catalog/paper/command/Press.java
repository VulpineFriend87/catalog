package top.vulpine.catalog.paper.command;

/**
 * Everything a button carries besides its command.
 *
 * @param screen    the trail of the screen the button was pressed on, or null when it was not
 *                  offered from a screen
 * @param confirmed whether this press is the answer to a confirmation
 * @param answer    {@link ClickContext#ALONE}, or null for the default of installing what is needed
 */
public record Press(String screen, boolean confirmed, String answer) {

    /**
     * @param screen the trail of the screen the button is offered from, or null
     * @return a plain press from that screen
     */
    public static Press on(String screen) {
        return new Press(screen, false, null);
    }

    /**
     * Reads what a button carried.
     *
     * @param data the payload {@link ClickContext#take} returned
     * @return what it carried, or null when the command was typed
     */
    public static Press of(String data) {

        if (data == null) {
            return null;
        }

        String rest = data;
        boolean confirmed = rest.startsWith(ClickContext.CONFIRM);

        if (confirmed) {
            rest = rest.substring(ClickContext.CONFIRM.length());
        }

        String answer = null;

        if (rest.startsWith(ClickContext.ALONE)) {
            answer = ClickContext.ALONE;
            rest = rest.substring(ClickContext.ALONE.length());
        }

        return new Press(rest.isEmpty() ? null : rest, confirmed, answer);
    }

    /**
     * The screen a payload points at.
     */
    public static String screenOf(String data) {
        Press press = of(data);
        return press == null ? null : press.screen();
    }

    /**
     * Which answer to the dependency question a payload carries.
     */
    public static String intent(String data) {
        Press press = of(data);
        return press == null ? null : press.answer();
    }

    /**
     * Whether a payload asks to install without what is required.
     */
    public static boolean alone(String data) {
        return ClickContext.ALONE.equals(intent(data));
    }

    /**
     * Whether a payload redraws the confirmation screen {@code here}. A redraw never confirms.
     */
    public static boolean redrawing(String data, String here) {
        Press press = of(data);
        return press != null && !press.confirmed() && here.equals(Trail.current(press.screen()));
    }

    /**
     * @return this press, as the confirmation of the question it raised
     */
    public Press confirming() {
        return new Press(screen, true, answer);
    }

    /**
     * @param answer what was chosen on the confirmation screen
     * @return this press, carrying that answer
     */
    public Press answering(String answer) {
        return new Press(screen, confirmed, answer);
    }

    /**
     * @return the payload, in the form {@link #of} reads back
     */
    public String data() {
        return (confirmed ? ClickContext.CONFIRM : "")
                + (answer == null ? "" : answer)
                + (screen == null ? "" : screen);
    }

}
