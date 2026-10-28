package top.vulpine.catalog.platform;

import top.vulpine.catalog.update.model.ServerTarget;

import java.nio.file.Path;
import java.time.Duration;

/**
 * What Catalog needs from the server it runs on.
 *
 * <p>Applying a build is a request rather than a folder, because the platforms do it differently:
 * Paper takes jars from an update folder at boot, Velocity has none and the jar is replaced where
 * it sits.</p>
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
     * @return what to call the place a staged build waits, for log lines
     */
    String stagingName();

    /**
     * Puts a downloaded build where it replaces the installed one at the next restart.
     *
     * @param staged   the downloaded file, which is moved
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
     * Runs a task off the main thread, every {@code period} after the first.
     */
    void repeat(Runnable task, Duration period);

}
