package top.vulpine.catalog.tracking;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import top.vulpine.catalog.json.Json;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The projects and files the scan ignores.
 *
 * <p>Both a project id and a hash are recorded. The project id keeps the plugin ignored across
 * updates. The hash covers jars Modrinth cannot identify.</p>
 */
public final class IgnoreList {

    private static final Type MODEL = new TypeToken<Model>() {
    }.getType();

    private final Path file;
    private Model model = new Model();

    public IgnoreList(Path file) {
        this.file = file;
    }

    public void load() {

        if (!Files.isRegularFile(file)) {
            model = new Model();
            return;
        }

        try {
            Model loaded = Json.gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), MODEL);
            model = loaded == null ? new Model() : loaded;
        } catch (IOException | JsonSyntaxException e) {
            model = new Model();
        }
    }

    /**
     * Writes the list to disk atomically.
     *
     * @throws TrackingException if the file cannot be written
     */
    public void save() {

        try {
            Json.write(file, model, MODEL);
        } catch (IOException e) {
            throw new TrackingException("Could not write " + file.getFileName(), e);
        }
    }

    /**
     * Whether the scan should skip a jar.
     *
     * @param projectId the Modrinth project id, or null if unidentified
     * @param sha512    the hash of the file on disk
     * @return true if this jar must not be adopted
     */
    public boolean contains(String projectId, String sha512) {
        return (projectId != null && model.projectIds.contains(projectId))
                || (sha512 != null && model.hashes.contains(sha512));
    }

    /**
     * Adds a jar to the list.
     *
     * @param projectId the Modrinth project id, or null if unidentified
     * @param sha512    the hash of the file on disk
     */
    public void add(String projectId, String sha512) {

        if (projectId != null) {
            model.projectIds.add(projectId);
        }

        if (sha512 != null) {
            model.hashes.add(sha512);
        }
    }

    /**
     * Removes a jar from the list. The scan may then adopt it again.
     *
     * @param projectId the Modrinth project id, or null
     * @param sha512    the hash, or null
     * @return true if anything was removed
     */
    public boolean remove(String projectId, String sha512) {

        boolean removed = projectId != null && model.projectIds.remove(projectId);

        if (sha512 != null && model.hashes.remove(sha512)) {
            removed = true;
        }

        return removed;
    }

    /**
     * @return the ignored project ids as an unmodifiable set
     */
    public Set<String> projectIds() {
        return java.util.Collections.unmodifiableSet(model.projectIds);
    }

    /**
     * @return how many ids and hashes are held
     */
    public int size() {
        return model.projectIds.size() + model.hashes.size();
    }

    /** The on-disk shape. */
    private static final class Model {
        private Set<String> projectIds = new LinkedHashSet<>();
        private Set<String> hashes = new LinkedHashSet<>();
    }

}
