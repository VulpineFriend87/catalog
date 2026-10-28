package top.vulpine.catalog.paper.command.screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import top.vulpine.catalog.modrinth.model.SearchHit;
import top.vulpine.catalog.modrinth.model.SearchResults;
import top.vulpine.catalog.paper.command.Permitted;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static top.vulpine.catalog.paper.command.screen.widget.Format.*;
import static top.vulpine.catalog.paper.command.screen.widget.Palette.*;
import static top.vulpine.catalog.paper.command.screen.widget.Widgets.*;

/**
 * Search results.
 */
public final class SearchPage {

    /** How many search results one page shows. */
    public static final int PAGE = 9;

    private SearchPage() {
    }

    public static List<Component> search(String query, SearchResults results, int page,
                                         Set<String> installedProjects) {

        List<Component> out = new ArrayList<>();

        out.add(line()
                .append(Component.text("Search", BRAND).decorate(TextDecoration.BOLD))
                .append(Component.text("  " + query, TEXT))
                .append(Component.text("  " + results.totalHits() + " results", MUTED))
                .build());

        out.add(Component.empty());

        if (results.hits().isEmpty()) {
            out.add(Component.text(INDENT + "No results", MUTED));
            return out;
        }

        for (SearchHit hit : results.hits()) {
            out.add(hit(hit, installedProjects.contains(hit.projectId())));
        }

        int pages = Math.max((results.totalHits() + PAGE - 1) / PAGE, 1);

        out.add(Component.empty());

        TextComponent.Builder footer = line()
                .append(Component.text(INDENT + "page ", MUTED))
                .append(Component.text(page, TEXT))
                .append(Component.text(" of " + pages + "  ", MUTED));

        if (page > 1) {
            footer.append(button("Back", "/catalog search " + query + " --page " + (page - 1),
                    MUTED, "Page " + (page - 1))).append(Component.space());
        }

        if (results.hasMore()) {
            footer.append(button("Next", "/catalog search " + query + " --page " + (page + 1),
                    BRAND, "Page " + (page + 1)));
        }

        out.add(footer.build());

        return out;
    }

    private static Component hit(SearchHit result, boolean installed) {

        Component info = Component.text(result.title(), TEXT)
                .append(Component.newline())
                .append(Component.text(result.description() == null ? "" : result.description(), MUTED))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text(result.author() == null ? "" : "by " + result.author(), MUTED));

        String command = "/catalog info " + result.slug();

        return Permitted.link(line()
                        .append(Component.text(INDENT))
                        .append(Component.text(result.title(), TEXT))
                        .append(Component.text("  " + compact(result.downloads()), MUTED))
                        .append(installed ? Component.text("  installed", DONE) : Component.empty())
                        .build(),
                from(command, null),
                info.append(Component.newline()).append(Component.newline())
                        .append(explain("Open the plugin page", command)), info);
    }

}
