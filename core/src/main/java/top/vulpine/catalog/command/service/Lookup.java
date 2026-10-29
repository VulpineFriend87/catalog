package top.vulpine.catalog.command.service;

import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.screen.widget.Format;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchHit;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.modrinth.model.TeamMember;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves the plugins, projects and builds commands name.
 */
public final class Lookup {

    private final Catalog catalog;

    public Lookup(Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * The named build, or the one an install would fetch.
     *
     * @param wanted       a version id or number, or null for the default
     * @param incompatible whether builds for other Minecraft versions count
     * @return the build, or null when there is none
     */
    public ModrinthVersion build(ModrinthProject project, String wanted, boolean incompatible) {

        ModrinthVersion version = incompatible ? choose(catalog.allVersions(project.id()), wanted) : null;

        // The two lists can come from different loaders.
        return version != null ? version : choose(catalog.compatibleVersions(project.id()), wanted);
    }

    /**
     * Finds the build someone named.
     *
     * @param wanted the id or version number, or null to take what installing would default to
     */
    private static ModrinthVersion choose(List<ModrinthVersion> compatible, String wanted) {

        if (wanted == null) {
            return Projects.installTarget(compatible);
        }

        for (ModrinthVersion version : compatible) {
            if (wanted.equals(version.id()) || wanted.equalsIgnoreCase(version.versionNumber())) {
                return version;
            }
        }

        return null;
    }

    /**
     * Finds a trashed plugin by its stored name, name, slug, file name or project id.
     */
    public TrashEntry resolveTrashed(String query) {

        String wanted = ClickContext.strip(query).trim();
        TrashEntry exact = catalog.trashed(wanted);

        if (exact != null) {
            return exact;
        }

        for (TrashEntry entry : catalog.trashed()) {

            if (wanted.equalsIgnoreCase(entry.name())
                    || wanted.equalsIgnoreCase(entry.slug())
                    || wanted.equalsIgnoreCase(entry.fileName())
                    || wanted.equalsIgnoreCase(entry.projectId())) {
                return entry;
            }
        }

        return null;
    }

    /**
     * Finds an untracked jar by the name the list shows, or by its file name.
     *
     * @param query what was typed
     * @return the jar, or null if no untracked jar answers to it
     */
    public InstalledJar resolveUntracked(String query) {

        String wanted = query.trim();

        for (InstalledJar jar : catalog.untracked()) {
            if (jar.fileName().equalsIgnoreCase(wanted)
                    || Format.shownName(jar).equalsIgnoreCase(wanted)) {
                return jar;
            }
        }

        return null;
    }

    private ModrinthProject firstSearchHit(String query) {

        SearchResults results = catalog.search(query, 1, 0);

        if (results.hits().isEmpty()) {
            return null;
        }

        SearchHit hit = results.hits().get(0);
        return catalog.project(hit.slug() != null ? hit.slug() : hit.projectId());
    }

    /**
     * Who to credit for a project.
     */
    public String author(String projectId) {

        try {
            return TeamMember.credit(catalog.getModrinth().members(projectId).join());
        } catch (Exception e) {
            return null;
        }
    }

    public List<ModrinthVersion> compatibleOrEmpty(String projectId) {

        try {
            return catalog.compatibleVersions(projectId);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static ModrinthVersion newestOn(List<ModrinthVersion> versions, ReleaseChannel channel) {

        for (ModrinthVersion version : versions) {
            if (version.versionType() == null || channel.accepts(version.versionType())) {
                return version;
            }
        }

        return null;
    }

    /**
     * The project ids of every tracked plugin.
     */
    public Set<String> trackedProjectIds() {

        Set<String> ids = new HashSet<>();

        for (TrackedPlugin tracked : catalog.getTracking().all()) {
            ids.add(tracked.projectId());
        }

        return ids;
    }

    public ReleaseChannel defaultChannel() {
        return catalog.getConfiguration().get().tracking.defaults.channel;
    }

    /**
     * A project found by slug or id.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param query what was typed or clicked with any payload
     * @return the project, or null when nothing matched
     */
    public ModrinthProject projectFor(String query) {

        String wanted = ClickContext.strip(query).trim();
        TrackedPlugin tracked = resolve(wanted);

        ModrinthProject project = catalog.project(tracked != null ? tracked.projectId() : wanted);
        return project != null ? project : firstSearchHit(wanted);
    }

    /**
     * Finds a tracked plugin.
     *
     * <p>An exact display name, slug or project id wins. Otherwise the first name starting with the
     * query.</p>
     */
    public TrackedPlugin resolve(String query) {

        String wanted = ClickContext.strip(query).toLowerCase(Locale.ROOT);
        List<TrackedPlugin> all = catalog.getTracking().all();

        for (TrackedPlugin tracked : all) {
            if (wanted.equalsIgnoreCase(tracked.displayName())
                    || wanted.equalsIgnoreCase(tracked.slug())
                    || wanted.equalsIgnoreCase(tracked.projectId())) {
                return tracked;
            }
        }

        for (TrackedPlugin tracked : all) {
            if (tracked.displayName() != null
                    && tracked.displayName().toLowerCase(Locale.ROOT).startsWith(wanted)) {
                return tracked;
            }
        }

        return null;
    }

}
