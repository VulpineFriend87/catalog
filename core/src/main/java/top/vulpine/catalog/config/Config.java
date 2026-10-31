package top.vulpine.catalog.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.network.RedisNetwork;
import top.vulpine.catalog.tracking.model.TrackingDefaults;
import top.vulpine.commons.log.LogLevel;

@Header("Catalog Configuration - By Vulpine (https://vulpine.top)")
@Header("")
@Header("Catalog manages plugins published on Modrinth. It identifies every jar by its")
@Header("hash, so it always knows exactly what you have installed. Anything it does not")
@Header("recognise is left completely alone.")
@Header("")
public class Config extends OkaeriConfig {

    @CustomKey("modrinth")
    public Modrinth modrinth = new Modrinth();

    public static class Modrinth extends OkaeriConfig {

        @Comment("A Modrinth personal access token. Optional: it raises the request limit")
        @Comment("and allows private projects to be read.")
        @CustomKey("token")
        public String token = "";

    }

    @CustomKey("tracking")
    public Tracking tracking = new Tracking();

    public static class Tracking extends OkaeriConfig {

        @Comment("Recognise and start tracking plugins automatically on startup.")
        @Comment("Disabling this stops new jars being adopted.")
        @CustomKey("auto_track")
        public boolean autoTrack = true;

        @Comment("Applied to a plugin when it is first adopted or installed. Changing these")
        @Comment("later does not affect plugins that are already tracked, only new ones.")
        @CustomKey("defaults")
        public Defaults defaults = new Defaults();

        public static class Defaults extends OkaeriConfig {

            @Comment("Which builds to consider: RELEASE, BETA or ALPHA.")
            @Comment("Channels are cumulative, so BETA also accepts releases.")
            @CustomKey("channel")
            public ReleaseChannel channel = ReleaseChannel.RELEASE;

            @Comment("Whether Catalog installs updates on its own. Off by default.")
            @CustomKey("auto_update")
            public boolean autoUpdate = false;

            @Comment("How long to wait after a version is published before installing it")
            @Comment("automatically. Avoids installing a release the author hotfixes shortly after.")
            @CustomKey("soak_minutes")
            public int soakMinutes = 120;

        }

    }

    @CustomKey("updates")
    public Updates updates = new Updates();

    public static class Updates extends OkaeriConfig {

        @Comment("How often to check for updates, in minutes.")
        @Comment("Set to 0 to only check when the server starts.")
        @CustomKey("check_interval_minutes")
        public int checkIntervalMinutes = 180;

        @Comment("Notify players with catalog.notify about updates when they join.")
        @CustomKey("notify_on_join")
        public boolean notifyOnJoin = true;

    }

    @CustomKey("trash")
    public Trash trash = new Trash();

    public static class Trash extends OkaeriConfig {

        @Comment("How long a removed plugin stays in the trash before it is deleted.")
        @Comment("Old removals are cleared out when the server starts. Set to 0 to keep them forever.")
        @CustomKey("retention_days")
        public int retentionDays = 30;

    }

    @CustomKey("network")
    public Network network = new Network();

    public static class Network extends OkaeriConfig {

        @Comment("Share this server's plugins with the other Catalog servers through Redis.")
        @CustomKey("enabled")
        public boolean enabled = false;

        @Comment("This server's name on the network. Lowercase letters, digits, - and _.")
        @Comment("Every server needs its own.")
        @CustomKey("server_name")
        public String serverName = "";

        @Comment("A secret of at least 16 characters, the same on every server. Inventories signed")
        @Comment("with another secret are ignored.")
        @CustomKey("secret")
        public String secret = "";

        @Comment("How many days a server that went offline stays listed. 0 lists only online servers.")
        @CustomKey("offline_days")
        public int offlineDays = 7;

        @Comment("The Redis server every Catalog server connects to.")
        @CustomKey("redis")
        public Redis redis = new Redis();

    }

    public static class Redis extends OkaeriConfig {

        @CustomKey("host")
        public String host = "localhost";

        @CustomKey("port")
        public int port = 6379;

        @Comment("Leave empty for the default user.")
        @CustomKey("username")
        public String username = "";

        @Comment("Leave empty when there is none.")
        @CustomKey("password")
        public String password = "";

        @CustomKey("database")
        public int database = 0;

        @CustomKey("tls")
        public boolean tls = false;

        /**
         * @return where and how to connect
         */
        public RedisNetwork.Address address() {
            return new RedisNetwork.Address(host, port, username, password, database, tls);
        }

    }

    @Comment("Log level for this plugin. Can be: DEBUG, INFO, WARN, ERROR.")
    @Comment("Leave as it is if you don't know what to choose.")
    @CustomKey("log_level")
    public LogLevel logLevel = LogLevel.INFO;

    /**
     * @return what a plugin starts with when it is adopted or installed
     */
    public TrackingDefaults defaults() {

        return TrackingDefaults.builder()
                .channel(tracking.defaults.channel)
                .autoUpdate(tracking.defaults.autoUpdate)
                .soakMinutes(tracking.defaults.soakMinutes)
                .build();
    }

}
