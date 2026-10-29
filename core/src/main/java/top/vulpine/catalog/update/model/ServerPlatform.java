package top.vulpine.catalog.update.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The kind of server Catalog runs on and the Modrinth loaders it can run.
 *
 * <p>Modrinth does not widen loaders: asking for {@code purpur} finds nothing published as
 * {@code paper}. Each constant carries itself and everything it is a superset of.</p>
 */
public enum ServerPlatform {

    PAPER("paper", "spigot", "bukkit"),

    PURPUR("purpur", "paper", "spigot", "bukkit"),

    /**
     * Folia refuses plugins whose descriptor does not declare {@code folia-supported}. The loaders
     * below still include Paper's. Authors often forget the {@code folia} tag on Modrinth. The
     * downloaded jar is checked for the field instead.
     */
    FOLIA("folia", "paper", "spigot", "bukkit"),

    VELOCITY("velocity");

    private final List<String> loaders;
    private final List<List<String>> tiers;

    ServerPlatform(String... loaders) {

        this.loaders = List.of(loaders);

        List<List<String>> ladder = new ArrayList<>();

        for (String loader : loaders) {
            ladder.add(List.of(loader));
        }

        this.tiers = Collections.unmodifiableList(ladder);
    }

    public List<List<String>> loaderTiers() {
        return tiers;
    }

    /**
     * Every loader this server can run. Most specific first.
     *
     * @return the loaders
     */
    public List<String> loaders() {
        return loaders;
    }

    /**
     * The loader that names this platform exactly.
     *
     * @return the Modrinth loader id
     */
    public String id() {
        return loaders.get(0);
    }

    /**
     * Whether a version declares this exact platform.
     *
     * @param declared the loaders a Modrinth version declares
     * @return true if the exact platform is named
     */
    public boolean declaredBy(List<String> declared) {
        return declared != null && declared.contains(id());
    }

}
