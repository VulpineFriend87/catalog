package top.vulpine.catalog;

import top.vulpine.catalog.tracking.model.TrackingDefaults;

/**
 * The settings Catalog reads from the platform's config file.
 */
public interface Configuration {

    /**
     * @return the Modrinth token, or an empty string for none
     */
    String modrinthToken();

    /**
     * @return whether recognised jars are tracked on startup
     */
    boolean autoTrack();

    /**
     * @return what a plugin starts with when it is adopted or installed
     */
    TrackingDefaults defaults();

    /**
     * @return minutes between update checks, 0 to check only on startup
     */
    int checkIntervalMinutes();

    /**
     * @return days a removal stays in the trash, 0 to keep it forever
     */
    int trashRetentionDays();

}
