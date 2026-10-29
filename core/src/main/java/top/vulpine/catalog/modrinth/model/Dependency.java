package top.vulpine.catalog.modrinth.model;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A dependency declared by a Modrinth version.
 *
 * <p>Either {@link #projectId()} or {@link #versionId()} may be null. A dependency points at a
 * whole project or at one exact version.</p>
 */
@Getter
@Accessors(fluent = true)
public final class Dependency {

    private String versionId;
    private String projectId;
    private String fileName;
    private DependencyType dependencyType;

}
