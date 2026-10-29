package top.vulpine.catalog.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandPermission;
import revxrsal.commands.velocity.VelocityLamp;
import revxrsal.commands.velocity.VelocityVisitors;
import revxrsal.commands.velocity.actor.VelocityCommandActor;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.CatalogAction;
import top.vulpine.catalog.command.Commands;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.service.JoinNews;
import top.vulpine.catalog.config.Config;
import top.vulpine.commons.log.LogLevel;
import top.vulpine.commons.log.Logger;
import top.vulpine.commons.text.Colorize;
import top.vulpine.commons.text.Dialect;

import java.nio.file.Path;

/**
 * Catalog for Velocity.
 */
public final class CatalogVelocity {

    private final ProxyServer server;
    private final ComponentLogger componentLogger;
    private final Path dataFolder;
    private final PluginContainer container;

    private Config configuration;
    private Catalog catalog;

    @Inject
    public CatalogVelocity(ProxyServer server, ComponentLogger componentLogger,
                           @DataDirectory Path dataFolder, PluginContainer container) {
        this.server = server;
        this.componentLogger = componentLogger;
        this.dataFolder = dataFolder.toAbsolutePath();
        this.container = container;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {

        Colorize.init(Dialect.MODERN);
        Logger.builder().logger(componentLogger).build();

        if (!loadConfiguration()) {
            return;
        }

        this.catalog = new Catalog(new VelocityPlatform(this, server, container, dataFolder,
                this::loadConfiguration), () -> configuration);

        if (!catalog.start()) {
            return;
        }

        // A player's /catalog goes on to the backend.
        CommandPermission<VelocityCommandActor> console = VelocityCommandActor::isConsole;

        Lamp<VelocityCommandActor> lamp = VelocityLamp.builder(this, server)
                .senderResolver(VelocityViewer.RESOLVER)
                .permissionFactory((annotations, command) -> console)
                .dependency(Catalog.class, catalog)
                .build();

        lamp.register(new Commands(catalog, new ClickContext()).registries().toArray());
        lamp.accept(VelocityVisitors.brigadier(server));

        server.getEventManager().register(this, new JoinNotifier(new JoinNews(catalog)));

        // Every plugin is loaded by now.
        catalog.getEnableCheck().started();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {

        if (catalog != null) {
            catalog.stop();
        }

        Logger.close();
    }

    private boolean loadConfiguration() {

        try {
            configuration = ConfigManager.create(Config.class, it -> {
                it.withConfigurer(new YamlSnakeYamlConfigurer());
                it.withBindFile(dataFolder.resolve("config.yml"));
                it.withRemoveOrphans(true);
                it.saveDefaults();
                it.load(true);
            });
        } catch (Exception e) {
            Logger.error(CatalogAction.CONFIG, "Failed to load configuration: " + e.getMessage());
            return false;
        }

        Logger.builder()
                .logger(componentLogger)
                .level(configuration.logLevel)
                .trace(configuration.logLevel == LogLevel.DEBUG ? dataFolder.toFile() : null)
                .build();

        return true;
    }

}
