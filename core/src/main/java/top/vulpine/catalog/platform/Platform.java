package top.vulpine.catalog.platform;

import top.vulpine.catalog.update.model.ServerTarget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * What Catalog needs from the server it runs on.
 *
 * <p>Paper applies builds from an update folder at boot. Velocity has none and replaces the jar
 * where it sits.</p>
 */
public interface Platform {

    /**
     * @return the folder holding the jars Catalog manages
     */
    Path pluginsFolder();

    /**
     * @return the loaders, game version and Java version to ask Modrinth about
     */
    ServerTarget target();

    /**
     * @return the file name of Catalog's own jar
     */
    String ownFileName();

    /**
     * @return the name of the place a staged build waits
     */
    String stagingName();

    /**
     * Puts a downloaded build where it replaces the installed one at the next restart.
     *
     * @param staged   the downloaded file to move
     * @param fileName the name it should take
     */
    void applyAtRestart(Path staged, String fileName);

    /**
     * @param fileName the name a build was staged under
     * @return true if it is still waiting
     */
    boolean isStaged(String fileName);

    /**
     * Drops a staged build so the next restart does not apply it.
     *
     * @param fileName the name it was staged under
     * @return true if it is gone
     */
    boolean cancelStaged(String fileName);

    /**
     * @param jar a jar in the plugins folder
     * @return true if a plugin loaded from it is enabled right now
     */
    boolean isEnabled(Path jar);

    /**
     * @param name the name a plugin declares
     * @return why that plugin is not enabled, or null when it is
     */
    String notEnabled(String name);

    /**
     * Reads the configuration again.
     *
     * @return false when it could not be read
     */
    boolean reload();

    /**
     * @return who made Catalog
     */
    List<String> authors();

    /**
     * @return the folder Catalog keeps its own files in
     */
    Path dataFolder();

    /**
     * @return Catalog's own jar
     */
    Path ownJar();

    /**
     * @return Catalog's version
     */
    String version();

    /**
     * Runs a task off the main thread.
     */
    void async(Runnable task);

    /**
     * Runs a task off the main thread every {@code period}.
     */
    void repeat(Runnable task, Duration period);

}
