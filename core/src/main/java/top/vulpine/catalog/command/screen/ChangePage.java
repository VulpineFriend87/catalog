package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.navigation.Press;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.command.screen.Change;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.modrinth.model.DependencyType;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

/**
 * The question asked before an install, update or switch.
 */
public final class ChangePage {

    private ChangePage() {
    }

    /**
     * The confirmation screen for an install, update or switch.
     *
     * @param from     the version installed now, or null for a first install
     * @param replaces the build already downloaded, or null when there is none
     * @param notFor   the Minecraft version the build does not declare, or null when it does
     * @param needs    the missing required plugins and the conflicts
     * @param asked    the press that raised the question
     * @param here     this screen
     * @param command  what confirming runs
     */
    public static List<Component> change(Change change, String name, String slug, String from,
                                         String replaces, ModrinthVersion version, String notFor,
                                         List<DependencyResolver.Requirement> needs, Press asked, String here,
                                         String command) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(title(change.verb() + " ", PENDING))
                .append(title(name, TEXT))
                .build());

        TextComponent.Builder builds = line().append(Component.text(INDENT));

        if (from != null) {
            builds.append(Component.text(from, MUTED)).append(Component.text(" → ", MUTED));
        }

        out.add(builds
                .append(Component.text(version.versionNumber(), TEXT))
                .append(Component.text("  " + (version.versionType() == null ? ""
                        : version.versionType().apiName()), MUTED))
                .build());

        if (replaces != null) {
            out.add(Component.text(INDENT + "Replaces " + replaces + ", downloaded.", MUTED));
        }

        out.add(Component.text(INDENT + "Applied on restart."
                + (change == Change.ROLL_BACK ? " Config and data are not rolled back with it." : ""),
                MUTED));

        if (notFor != null) {
            out.add(Component.text(INDENT + "Not marked compatible with " + notFor, DANGER));
        }

        int missing = 0;
        boolean reachable = true;

        for (DependencyResolver.Requirement row : needs) {
            if (row.blocking()) {
                missing++;
                reachable &= row.available() != null;
            }
        }

        if (!needs.isEmpty()) {

            out.add(Component.empty());

            for (DependencyResolver.Requirement row : needs) {
                out.add(dependencyRow(row, here));
            }
        }

        out.add(Component.empty());

        Press press = asked == null ? Press.on(null) : asked;
        String action = change.verb().toLowerCase(Locale.ROOT);
        List<Component> row = new ArrayList<>();

        // Hidden when a requirement has no build for this server.
        if (reachable) {
            row.add(button("Confirm", confirming(command, press), PENDING, missing == 0
                    ? change.verb() + " " + name
                    : "Install the " + missing + " required, then " + action + " " + name));
        }

        if (missing > 0) {
            row.add(button("Without dependencies",
                    confirming(command, press.answering(ClickContext.ALONE)), MUTED,
                    change.verb() + " " + name + " without what it requires"));
        }

        row.add(button("Cancel", backTo(press.screen(), change == Change.INSTALL
                        || change == Change.UPDATE ? "/catalog info " + slug : "/catalog versions " + slug),
                MUTED, "Leave it as it is"));

        out.add(buttons(row));

        return out;
    }

    /**
     * One declared project and its state on this server.
     */
    private static Component dependencyRow(DependencyResolver.Requirement row, String here) {

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
