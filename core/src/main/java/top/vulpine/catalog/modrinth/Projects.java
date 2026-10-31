package top.vulpine.catalog.modrinth;

import top.vulpine.catalog.Errors;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.install.InstallException;
import top.vulpine.catalog.modrinth.model.Dependency;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.ReleaseChannel;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.platform.Platform;
import top.vulpine.catalog.tracking.TrackingStore;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.update.model.ServerTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asks Modrinth about the projects this server can run.
 *
 * <p>Every method blocks, so none may be called on the server main thread.</p>
 */
public final class Projects {

    private final Platform platform;
    private final ModrinthClient modrinth;
    private final TrackingStore tracking;

    public Projects(Platform platform, ModrinthClient modrinth, TrackingStore tracking) {
        this.platform = platform;
        this.modrinth = modrinth;
        this.tracking = tracking;
    }

    /**
     * Looks a project up by its id or slug.
     *
     * @param idOrSlug what to look for
     * @return the project, or null if there is no such thing
     */
    public ModrinthProject project(String idOrSlug) {

        try {
            return modrinth.project(idOrSlug).join();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Searches Modrinth for what this server can run.
     *
     * @param query  the search text
     * @param limit  how many results to return
     * @param offset where to start
     * @return one page of results
     */
    public SearchResults search(String query, int limit, int offset) {

        ServerTarget target = platform.target();
        return search(query, limit, offset, target.loaders(),
                target.gameVersion() == null ? List.of() : List.of(target.gameVersion()));
    }

    /**
     * Searches Modrinth for what runs on any of the given loaders and Minecraft versions.
     *
     * @param gameVersions the versions to accept, or none to accept every version
     */
    public SearchResults search(String query, int limit, int offset, Collection<String> loaders,
                                Collection<String> gameVersions) {

        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:plugin"));
        facets.add(loaders.stream().map(loader -> "categories:" + loader).toList());

        if (!gameVersions.isEmpty()) {
            facets.add(gameVersions.stream().map(version -> "versions:" + version).toList());
        }

        return modrinth.search(query, facets, limit, offset).join();
    }

    /**
     * Every build of a project this server can run on any channel.
     *
     * @param idOrSlug the project to list
     * @return the compatible versions sorted newest first
     */
    public List<ModrinthVersion> compatibleVersions(String idOrSlug) {
        return versions(idOrSlug, platform.target().gameVersions());
    }

    /**
     * Every build of a project for this server's platform on any Minecraft version.
     *
     * @param idOrSlug the project to list
     * @return the versions sorted newest first
     */
    public List<ModrinthVersion> allVersions(String idOrSlug) {
        return versions(idOrSlug, null);
    }

    private List<ModrinthVersion> versions(String idOrSlug, List<String> gameVersions) {

        ServerTarget target = platform.target();

        for (List<String> tier : target.platform().loaderTiers()) {

            List<ModrinthVersion> versions;

            try {
                versions = modrinth.versions(idOrSlug, tier, gameVersions).join();
            } catch (Exception e) {
                throw new InstallException("Could not reach Modrinth: " + Errors.rootMessage(e), e);
            }

            if (!versions.isEmpty()) {
                List<ModrinthVersion> sorted = new ArrayList<>(versions);
                sorted.sort(Comparator.comparing(ModrinthVersion::datePublished).reversed());
                return sorted;
            }
        }

        return List.of();
    }

    /**
     * The build an install would fetch for a project.
     *
     * @param idOrSlug the project to look at
     * @return the build to offer, or null when nothing published runs here
     */
    public ModrinthVersion installTarget(String idOrSlug) {
        return installTarget(compatibleVersions(idOrSlug));
    }

    /**
     * The build an install would fetch: the newest stable one, or the newest of anything when the
     * project has never published a stable build for this server.
     *
     * @param compatible what this server can run sorted newest first
     * @return the build to offer, or null when the list is empty
     */
    public static ModrinthVersion installTarget(List<ModrinthVersion> compatible) {

        for (ModrinthVersion version : compatible) {
            if (version.versionType() == ReleaseChannel.RELEASE) {
                return version;
            }
        }

        return compatible.isEmpty() ? null : compatible.get(0);
    }

    /**
     * What a build depends on.
     *
     * @param version the build being considered
     * @return the resolution with required dependencies followed through the whole graph
     */
    public DependencyResolver.Resolution dependenciesOf(ModrinthVersion version) {

        DependencyResolver resolver = new DependencyResolver(
                this::installTarget,
                projectId -> tracking.byProjectId(projectId) != null);

        return resolver.resolve(version);
    }

    /**
     * The named projects a build declares of one type.
     *
     * @param version the build, or null for none
     * @return the declared projects. No builds to install are looked up.
     */
    public List<DependencyResolver.Requirement> declared(ModrinthVersion version, DependencyType type) {

        if (version == null) {
            return List.of();
        }

        List<DependencyResolver.Requirement> declared = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Dependency dependency : version.dependenciesOf(type)) {

            String projectId = dependency.projectId();

            if (projectId != null && seen.add(projectId)) {
                declared.add(new DependencyResolver.Requirement(projectId, null,
                        tracking.byProjectId(projectId) != null, type));
            }
        }

        return named(declared);
    }

    /**
     * Puts the project's title and slug and the installed build on each requirement.
     *
     * @return the requirements in the same order. They keep their ids when Modrinth cannot be
     *         reached.
     */
    public List<DependencyResolver.Requirement> named(List<DependencyResolver.Requirement> requirements) {

        if (requirements.isEmpty()) {
            return requirements;
        }

        Set<String> ids = new LinkedHashSet<>();

        for (DependencyResolver.Requirement requirement : requirements) {
            ids.add(requirement.projectId());
        }

        Map<String, ModrinthProject> projects = byId(ids);
        List<DependencyResolver.Requirement> named = new ArrayList<>();

        for (DependencyResolver.Requirement requirement : requirements) {

            ModrinthProject project = projects.get(requirement.projectId());
            TrackedPlugin installed = tracking.byProjectId(requirement.projectId());

            named.add(requirement.named(
                    project != null ? project.title() : requirement.projectId(),
                    project != null ? project.slug() : requirement.projectId(),
                    installed != null ? installed.versionNumber() : requirement.version()));
        }

        return named;
    }

    /**
     * Several projects in one request.
     *
     * @return the projects keyed by id. Empty when Modrinth cannot be reached.
     */
    public Map<String, ModrinthProject> byId(Collection<String> ids) {

        Map<String, ModrinthProject> projects = new HashMap<>();

        if (ids.isEmpty()) {
            return projects;
        }

        try {
            for (ModrinthProject project : modrinth.projects(ids).join()) {
                projects.put(project.id(), project);
            }
        } catch (Exception ignored) {
            // The ids are still enough to name and act on a row.
        }

        return projects;
    }

}
