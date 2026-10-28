package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.install.DependencyResolver;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.paper.command.Change;
import top.vulpine.catalog.paper.command.ClickContext;
import top.vulpine.catalog.paper.command.Press;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

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
     * @param needs    the required plugins that are missing, and the conflicts
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
                .append(Component.text(change.verb() + " ", PENDING).decorate(TextDecoration.BOLD))
                .append(Component.text(name, TEXT).decorate(TextDecoration.BOLD))
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
                out.add(DependenciesPage.dependencyRow(row, here));
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

}
