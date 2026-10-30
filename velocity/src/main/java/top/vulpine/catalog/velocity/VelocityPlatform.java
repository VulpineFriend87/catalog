package top.vulpine.catalog.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import top.vulpine.catalog.install.InstallException;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.update.model.ServerPlatform;
import top.vulpine.catalog.update.model.ServerTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The {@link Platform} of Velocity.
 *
 * <p>Velocity has no update folder. A staged build waits in Catalog's own {@code update} folder
 * and is moved over the installed jar as the JVM exits.</p>
 */
public final class VelocityPlatform implements Platform {

    private final Object plugin;

    private final ProxyServer server;

    private final PluginContainer container;

    private final Path dataFolder;

    /** Reads the configuration again. False when it could not be read. */
    private final BooleanSupplier reload;

    public VelocityPlatform(Object plugin, ProxyServer server, PluginContainer container, Path dataFolder,
                            BooleanSupplier reload) {
        this.plugin = plugin;
        this.server = server;
        this.container = container;
        this.dataFolder = dataFolder;
        this.reload = reload;
    }

    @Override
    public ServerTarget target() {

        return ServerTarget.builder()
                .platform(ServerPlatform.VELOCITY)
                .javaVersion(Runtime.version().feature())
                .build();
    }

    /**
     * The parent of Catalog's data folder.
     */
    @Override
    public Path pluginsFolder() {
        return dataFolder.getParent();
    }

    @Override
    public String ownFileName() {

        Path jar = ownJar();
        return jar == null ? "" : jar.getFileName().toString();
    }

    @Override
    public String stagingName() {
        return pluginsFolder().getParent().relativize(updateFolder()).toString();
    }

    @Override
    public void applyAtRestart(Path staged, String fileName) {

        try {
            Files.createDirectories(updateFolder());
            Files.move(staged, updateFolder().resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new InstallException("Could not prepare the update: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isStaged(String fileName) {
        return Files.exists(updateFolder().resolve(fileName));
    }

    @Override
    public boolean cancelStaged(String fileName) {

        try {
            Files.deleteIfExists(updateFolder().resolve(fileName));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Path stagedAtExit(String fileName) {
        return updateFolder().resolve(fileName);
    }

    private Path updateFolder() {
        return dataFolder.resolve("update");
    }

    /**
     * Every plugin Velocity loaded is enabled.
     */
    @Override
    public boolean isEnabled(Path jar) {

        Path wanted = jar.toAbsolutePath().normalize();

        for (PluginContainer loaded : server.getPluginManager().getPlugins()) {
            if (loaded.getDescription().getSource()
                    .map(source -> source.toAbsolutePath().normalize().equals(wanted))
                    .orElse(false)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String notEnabled(String name) {

        for (PluginContainer loaded : server.getPluginManager().getPlugins()) {

            PluginDescription description = loaded.getDescription();

            if (description.getId().equalsIgnoreCase(name)
                    || description.getName().map(name::equalsIgnoreCase).orElse(false)) {
                return null;
            }
        }

        return "not loaded";
    }

    @Override
    public boolean reload() {
        return reload.getAsBoolean();
    }

    @Override
    public List<String> authors() {
        return container.getDescription().getAuthors();
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
    }

    @Override
    public Path ownJar() {
        return container.getDescription().getSource()
                .map(source -> source.toAbsolutePath().normalize())
                .orElse(null);
    }

    @Override
    public String version() {
        return container.getDescription().getVersion().orElse("unknown");
    }

    /**
     * The proxy's version without the build it was made from.
     */
    @Override
    public String serverVersion() {

        String version = server.getVersion().getVersion();
        int build = version.indexOf(' ');
        return build < 0 ? version : version.substring(0, build);
    }

    @Override
    public void async(Runnable task) {
        server.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public void later(Runnable task, Duration delay) {
        server.getScheduler().buildTask(plugin, task).delay(delay).schedule();
    }

    @Override
    public void repeat(Runnable task, Duration period) {
        server.getScheduler().buildTask(plugin, task).delay(period).repeat(period).schedule();
    }

}
