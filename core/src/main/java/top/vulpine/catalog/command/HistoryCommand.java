package top.vulpine.catalog.command;

import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Flag;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.orphan.OrphanCommand;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.argument.HistoryKind;
import top.vulpine.catalog.command.argument.Suggestions;
import top.vulpine.catalog.command.flow.Chat;
import top.vulpine.catalog.command.navigation.ClickContext;
import top.vulpine.catalog.command.permission.RequiresPermission;
import top.vulpine.catalog.command.screen.HistoryPage;
import top.vulpine.catalog.command.screen.Outcomes;
import top.vulpine.catalog.history.HistoryEntry;
import top.vulpine.catalog.platform.Viewer;

import java.util.List;

import static top.vulpine.catalog.command.flow.Chat.send;

/**
 * {@code /catalog history}.
 */
public final class HistoryCommand implements OrphanCommand {

    private final Catalog catalog;

    private final ClickContext context;

    private final Chat chat;

    public HistoryCommand(Catalog catalog, ClickContext context, Chat chat) {
        this.catalog = catalog;
        this.context = context;
        this.chat = chat;
    }

    @Subcommand("history")
    @Description("What Catalog has done")
    @RequiresPermission("command.history")
    public void history(Viewer viewer,
                        @Optional @Flag(value = "plugin", shorthand = 'l')
                        @SuggestWith(Suggestions.Logged.class) String which,
                        @Optional @Flag("author") String author,
                        @Optional @Flag("event") @SuggestWith(Suggestions.Kinds.class) String event,
                        @Flag("page") @Default("1") int page) {

        context.take(viewer);

        chat.runAsync(viewer, () -> {

            String projectId = null;
            String label = null;
            StringBuilder command = new StringBuilder("/catalog history");

            if (which != null && !which.isBlank()) {

                // Resolved against the log rather than the tracking file, so a plugin that has
                // since been removed can still be looked up.
                projectId = catalog.loggedProjectId(which);

                if (projectId == null) {
                    send(viewer, Outcomes.unknownPlugin(which));
                    return;
                }

                label = which;
                command.append(" --plugin ").append(which);
            }

            if (author != null && !author.isBlank()) {
                label = label == null ? author : label + ", " + author;
                command.append(" --author ").append(author);
            }

            List<HistoryEntry> entries = catalog.history(projectId, author);

            if (event != null && !event.isBlank()) {

                HistoryKind kind = HistoryKind.named(event);

                if (kind == null) {
                    send(viewer, Outcomes.unknownKind(event));
                    return;
                }

                entries = entries.stream().filter(entry -> kind.covers(entry.event())).toList();
                label = label == null ? kind.label() : label + ", " + kind.label();
                command.append(" --event ").append(kind.label());
            }

            send(viewer, HistoryPage.history(entries, Math.max(page, 1), label, command.toString()));
        });
    }

}
