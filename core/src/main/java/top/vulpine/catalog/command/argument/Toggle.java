package top.vulpine.catalog.command.argument;

/**
 * An on/off argument.
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
