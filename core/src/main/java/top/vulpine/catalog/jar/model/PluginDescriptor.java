package top.vulpine.catalog.jar.model;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * The fields of a plugin jar's descriptor file.
 *
 * <p>Every field may be absent.</p>
 */
@Getter
@Builder
@Accessors(fluent = true)
public final class PluginDescriptor {

    private final String pluginName;
    private final String pluginVersion;
    private final String mainClass;
    private final String apiVersion;

    @Builder.Default
    private final Kind kind = Kind.NONE;

    private final int bytecodeMajor;

    /**
     * An empty descriptor for a jar that could not be read.
     *
     * @return a descriptor with everything absent
     */
    public static PluginDescriptor unknown() {
        return PluginDescriptor.builder().build();
    }

    /**
     * The Java feature version this jar was compiled for.
     *
     * <p>Class file major version 61 is Java 17 and 65 is Java 21.</p>
     *
     * @return the Java version, or 0 if no class file could be read
     */
    public int javaVersion() {
        return bytecodeMajor == 0 ? 0 : bytecodeMajor - 44;
    }

    /**
     * Whether the descriptor names a plugin.
     *
     * @return true if a plugin name was found
     */
    public boolean isPlugin() {
        return pluginName != null && !pluginName.isBlank();
    }

    /**
     * Which descriptor file a jar was described by.
     */
    public enum Kind {

        /** {@code plugin.yml} */
        BUKKIT,

        /** {@code paper-plugin.yml} */
        PAPER,

        /** {@code velocity-plugin.json} */
        VELOCITY,

        /** No recognised descriptor. */
        NONE

    }

}
