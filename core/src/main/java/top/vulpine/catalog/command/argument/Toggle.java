package top.vulpine.catalog.command.argument;

/**
 * On or off, as an argument.
 */
public enum Toggle {

    ON,
    OFF;

    /**
     * @return true when this is {@link #ON}
     */
    public boolean on() {
        return this == ON;
    }

}
