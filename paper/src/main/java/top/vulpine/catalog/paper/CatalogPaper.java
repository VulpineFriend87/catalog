package top.vulpine.catalog.paper;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.command.Commands;
import top.vulpine.catalog.command.navigation.ClickCommand;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.service.JoinNews;
import top.vulpine.catalog.config.Config;
import top.vulpine.commons.log.LogLevel;
import top.vulpine.commons.log.Logger;
import top.vulpine.commons.text.Colorize;
import top.vulpine.commons.text.Dialect;

import java.io.File;

/**
 * Catalog for Paper, Purpur, and Folia.
 */
@Getter
public final class CatalogPaper extends JavaPlugin {

    private static final String MODRINTH = "https://modrinth.com/plugin/catalog";

    private static final int PLUGIN_ID = 33849;

    private static final String MINIMUM = "1.18.2";

    private boolean started;

    private Config configuration;
    private FoliaLib foliaLib;
    private Catalog catalog;

    @Override
    public void onEnable() {

        if (isOlderThan(running(), MINIMUM)) {
            getLogger().severe("Catalog needs Minecraft " + MINIMUM + " or newer, this server runs "
                    + running() + ".");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!isPaper()) {
            getLogger().severe("Catalog needs Paper or a fork of it, such as Purpur or Folia.");
            getLogger().severe("Latest version: " + MODRINTH);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Colorize.init(Dialect.MODERN);
        Logger.builder().logger(getComponentLogger()).build();
        this.started = true;

        if (!loadConfiguration()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.foliaLib = new FoliaLib(this);
        Logger.debug(CatalogAction.SETUP, "Scheduling through FoliaLib, detected platform: "
                + foliaLib.getImplType() + ".");

        this.catalog = new Catalog(new PaperPlatform(this, foliaLib.getScheduler(), getFile().toPath(),
                this::loadConfiguration),
                () -> configuration);
        if (!catalog.start()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Lamp<BukkitCommandActor> lamp = BukkitLamp.builder(this)
                .senderResolver(PaperViewer.RESOLVER)
                .permissionForAnnotation(RequiresPermission.class, annotation ->
                        actor -> new PaperViewer(actor.sender()).has(annotation.value()))
                .dependency(Catalog.class, catalog)
                .build();

        ClickContext clicks = new ClickContext();

        lamp.register(new Commands(catalog, clicks).registries().toArray());
        lamp.register(new ClickCommand(clicks));
        clicks.dispatcher((actor, line) -> lamp.dispatch((BukkitCommandActor) actor, line));

        getServer().getPluginManager().registerEvents(new ServerStarted(catalog.getEnableCheck()), this);
        getServer().getPluginManager().registerEvents(new JoinNotifier(new JoinNews(catalog)), this);

        Logger.debug(CatalogAction.SETUP, "Initializing metrics...");
        new Metrics(this, PLUGIN_ID);
    }

    @Override
    public void onDisable() {

        // Everything below needs Adventure. A refused start never loads it.
        if (!started) {
            return;
        }

        if (catalog != null) {
            catalog.stop();
        }

        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }

        Logger.close();
    }

    private void applyLogging() {

        Logger.builder()
                .logger(getComponentLogger())
                .level(configuration.logLevel)
                .trace(configuration.logLevel == LogLevel.DEBUG ? getDataFolder() : null)
                .build();
    }

    public boolean loadConfiguration() {

        try {
            configuration = ConfigManager.create(Config.class, it -> {
                it.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
                it.withBindFile(new File(getDataFolder(), "config.yml"));
                it.withRemoveOrphans(true);
                it.saveDefaults();
                it.load(true);
            });
        } catch (Exception e) {
            Logger.error(CatalogAction.CONFIG, "Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        applyLogging();
        return true;
    }

    public PlatformScheduler getScheduler() {
        return foliaLib.getScheduler();
    }

    private static boolean isPaper() {

        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * The Minecraft version this server runs.
     *
     * <p>Read from {@code getBukkitVersion}. Paper added {@code getMinecraftVersion} later.</p>
     */
    private String running() {
        return getServer().getBukkitVersion().split("-")[0];
    }

    /**
     * Whether one Minecraft version is older than another.
     *
     * <p>Compared number by number. Paper went from 1.21 to 26.</p>
     *
     * @param version what the server reports
     * @param minimum the oldest Catalog supports
     * @return whether the server is below it. False for anything unreadable.
     */
    static boolean isOlderThan(String version, String minimum) {

        String[] here = version.split("\\.");
        String[] least = minimum.split("\\.");

        for (int i = 0; i < least.length; i++) {

            int mine;

            try {
                mine = i < here.length ? Integer.parseInt(here[i].trim()) : 0;
            } catch (NumberFormatException e) {
                // An unreadable version is not a reason to refuse to start.
                return false;
            }

            if (mine != Integer.parseInt(least[i])) {
                return mine < Integer.parseInt(least[i]);
            }
        }

        return false;
    }

}
