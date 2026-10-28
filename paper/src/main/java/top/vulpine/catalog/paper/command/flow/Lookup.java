package top.vulpine.catalog.paper.command.flow;

import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.modrinth.Projects;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchHit;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.modrinth.model.TeamMember;
import top.vulpine.catalog.paper.CatalogPaper;
import top.vulpine.catalog.paper.command.ClickContext;
import top.vulpine.catalog.paper.command.screen.widget.Format;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds what a command names.
 */
public final class Lookup {

    private final Catalog catalog;

    private final CatalogPaper paper;

    public Lookup(Catalog catalog, CatalogPaper paper) {
        this.catalog = catalog;
        this.paper = paper;
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
     * Finds a trashed plugin by file name and plugin name.
     */
    public TrashEntry resolveTrashed(String query) {

        String wanted = ClickContext.strip(query).trim();
        TrashEntry exact = catalog.trashed(wanted);

        if (exact != null) {
            return exact;
        }

        String lowered = wanted.toLowerCase(Locale.ROOT);

        for (TrashEntry entry : catalog.trashed()) {

            if (matches(entry.name(), lowered)
                    || matches(entry.slug(), lowered)
                    || matches(entry.fileName(), lowered)
                    || matches(entry.projectId(), lowered)) {
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
     * Whether a build is newer than what is installed.
     */
    public Set<String> trackedProjectIds() {

        Set<String> ids = new HashSet<>();

        for (TrackedPlugin tracked : catalog.getTracking().all()) {
            ids.add(tracked.projectId());
        }

        return ids;
    }

    public ReleaseChannel defaultChannel() {
        return paper.getConfiguration().tracking.defaults.channel;
    }

    /**
     * A project found by slug or id.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param query what was typed or clicked, payload and all
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
     * <p>An exact match on the display name, slug or project id wins; otherwise the first whose
     * name starts with what was typed, so partial names work.</p>
     */
    public TrackedPlugin resolve(String query) {

        String wanted = ClickContext.strip(query).toLowerCase(Locale.ROOT);
        List<TrackedPlugin> all = catalog.getTracking().all();

        for (TrackedPlugin tracked : all) {
            if (matches(tracked.displayName(), wanted)
                    || matches(tracked.slug(), wanted)
                    || matches(tracked.projectId(), wanted)) {
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

    private static boolean matches(String value, String wanted) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(wanted);
    }

}
