package top.vulpine.catalog.paper;

import com.tcoded.folialib.impl.PlatformScheduler;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import top.vulpine.catalog.install.InstallException;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.update.model.ServerPlatform;
import top.vulpine.catalog.update.model.ServerTarget;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Paper, Purpur and Folia, as Catalog sees them.
 */
public final class PaperPlatform implements Platform {

    private final JavaPlugin plugin;

    private final PlatformScheduler scheduler;

    /** The jar Catalog was loaded from. */
    private final Path ownJar;

    /** Reads the configuration again, false when it could not be read. */
    private final BooleanSupplier reload;

    public PaperPlatform(JavaPlugin plugin, PlatformScheduler scheduler, Path ownJar, BooleanSupplier reload) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.ownJar = ownJar;
        this.reload = reload;
    }

    @Override
    public ServerTarget target() {

        return ServerTarget.builder()
                .platform(detectPlatform())
                .gameVersion(plugin.getServer().getMinecraftVersion())
                .javaVersion(Runtime.version().feature())
                .build();
    }

    private static ServerPlatform detectPlatform() {

        if (isPresent("io.papermc.paper.threadedregions.RegionizedServer")) {
            return ServerPlatform.FOLIA;
        }

        if (isPresent("org.purpurmc.purpur.PurpurConfig")) {
            return ServerPlatform.PURPUR;
        }

        return ServerPlatform.PAPER;
    }

    private static boolean isPresent(String className) {

        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * The folder Catalog manages, which is the parent of its own data folder.
     */
    @Override
    public Path pluginsFolder() {
        return plugin.getDataFolder().getParentFile().toPath();
    }

    @Override
    public String notEnabled(String name) {

        Plugin running = plugin.getServer().getPluginManager().getPlugin(name);

        return running == null ? "not loaded"
                : !running.isEnabled() ? "disabled during startup" : null;
    }

    @Override
    public boolean reload() {
        return reload.getAsBoolean();
    }

    @Override
    public List<String> authors() {
        return plugin.getDescription().getAuthors();
    }

    @Override
    public Path dataFolder() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public Path ownJar() {
        return ownJar;
    }

    @Override
    public String ownFileName() {
        return ownJar.getFileName().toString();
    }

    @Override
    public String version() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String stagingName() {
        return plugin.getServer().getUpdateFolderFile().getName();
    }

    @Override
    public void applyAtRestart(Path staged, String fileName) {

        Path folder = updateFolder();

        try {
            Files.createDirectories(folder);
            Files.move(staged, folder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
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

    private Path updateFolder() {
        return plugin.getServer().getUpdateFolderFile().toPath();
    }

    @Override
    public boolean isEnabled(Path jar) {

        Path wanted = jar.toAbsolutePath().normalize();

        for (Plugin running : plugin.getServer().getPluginManager().getPlugins()) {
            if (running.isEnabled() && wanted.equals(jarOf(running))) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the jar a plugin's classes were loaded from, or null when it cannot be told
     */
    private static Path jarOf(Plugin plugin) {

        try {
            CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException | SecurityException e) {
            return null;
        }
    }

    @Override
    public void async(Runnable task) {
        scheduler.runAsync(wrapped -> task.run());
    }

    @Override
    public void repeat(Runnable task, Duration period) {
        scheduler.runTimerAsync(task, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

}
