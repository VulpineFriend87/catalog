package top.vulpine.catalog.modrinth.model;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * One Minecraft version Modrinth knows, from {@code /tag/game_version}.
 */
@Getter
@Accessors(fluent = true)
public final class GameVersionTag {

    private String version;

    /** {@code release}, {@code snapshot}, {@code alpha} or {@code beta}. */
    private String versionType;

    public boolean isRelease() {
        return "release".equals(versionType);
    }

}
