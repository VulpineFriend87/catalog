package top.vulpine.catalog.command.screen;

import net.kyori.adventure.text.Component;
import top.vulpine.catalog.command.screen.widget.Pager;
import top.vulpine.catalog.command.permission.Permitted;
import top.vulpine.catalog.modrinth.model.SearchHit;
import top.vulpine.catalog.modrinth.model.SearchResults;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static top.vulpine.catalog.command.screen.widget.Format.*;
import static top.vulpine.catalog.command.screen.widget.Palette.*;
import static top.vulpine.catalog.command.screen.widget.Widgets.*;

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
                .append(title("Search", BRAND))
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

        Pager pager = Pager.of(results.totalHits(), PAGE, page);

        if (pager.pages() > 1) {
            out.add(Component.empty());
            out.add(pager.footer(List.of(), "Previous", "Next",
                    shown -> "/catalog search " + query + " --page " + shown));
        }

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
