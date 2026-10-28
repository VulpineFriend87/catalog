package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.command.navigation.Screen;
import top.vulpine.catalog.command.navigation.Trail;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthProject;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;

import java.util.ArrayList;
import java.util.List;

import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * What a build declares.
 */
public final class DependenciesPage {

    private DependenciesPage() {
    }

    public static List<Component> dependencies(ModrinthProject project, ModrinthVersion version,
                                               List<DependencyResolver.Requirement> rows, String from) {

        String key = project.slug();
        String here = Trail.within(new Screen.DependenciesScreen(key, version.id()).token(), from);

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text(project.title(), BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text(" " + version.versionNumber(), TEXT))
                .append(Component.text("  dependencies", MUTED))
                .build());

        int missing = 0;

        for (DependencyResolver.Requirement row : rows) {
            if (row.blocking()) {
                missing++;
            }
        }

        if (missing > 0) {
            out.add(line()
                    .append(Component.text(INDENT))
                    .append(Component.text(missing, PENDING))
                    .append(Component.text(" required missing", MUTED))
                    .build());
        }

        out.add(Component.empty());

        if (rows.isEmpty()) {
            out.add(Component.text(INDENT + "Declares none", MUTED));
        }

        for (DependencyResolver.Requirement row : rows) {
            out.add(dependencyRow(row, here));
        }

        out.add(Component.empty());
        out.add(line()
                .append(Component.text(INDENT))
                .append(backOrInfo(from, key))
                .build());

        return out;
    }

    public static Component dependencyRow(DependencyResolver.Requirement row, String here) {

        TextComponent.Builder line = line().append(Component.text(INDENT));

        if (row.conflicting()) {
            return line
                    .append(Component.text("  "))
                    .append(Component.text(row.name(), DANGER))
                    .append(Component.text("  incompatible, installed", MUTED))
                    .build();
        }

        boolean optional = row.type() == DependencyType.OPTIONAL;
        String kind = optional ? "  optional" : "  required";

        if (row.installed()) {
            return line
                    .append(Component.text("✔ ", DONE))
                    .append(Component.text(row.name(), TEXT))
                    .append(Component.text(kind, MUTED))
                    .append(Component.text(row.version() == null ? "" : "  " + row.version(), MUTED))
                    .build();
        }

        line.append(Component.text("  "))
                .append(Component.text(row.name(), optional ? MUTED : PENDING))
                .append(Component.text(kind, MUTED))
                .append(Component.space());

        if (row.available() == null) {
            return line.append(Component.text(" no build for this server", DANGER)).build();
        }

        return line
                .append(button("Install", from("/catalog install " + row.slug(), here), BRAND,
                        "Install " + row.name() + " " + row.version()))
                .append(Component.space())
                .append(Permitted.unless("/catalog install " + row.slug(),
                        button("Versions", from("/catalog versions " + row.slug(), here), MUTED,
                                "Choose a build of " + row.name()),
                        button("Versions", from("/catalog versions " + row.slug(), here), MUTED,
                                "The newest release, beta and alpha of " + row.name())))
                .build();
    }

}
