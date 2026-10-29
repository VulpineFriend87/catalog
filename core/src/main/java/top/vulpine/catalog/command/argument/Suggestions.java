package top.vulpine.catalog.command.argument;

import org.jetbrains.annotations.NotNull;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.node.ExecutionContext;
import revxrsal.commands.stream.StringStream;
import top.vulpine.catalog.Catalog;
import top.vulpine.catalog.command.screen.widget.Format;
import top.vulpine.catalog.jar.model.InstalledJar;
import top.vulpine.catalog.tracking.model.TrackedPlugin;
import top.vulpine.catalog.trash.model.TrashEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Tab completion for Catalog's arguments.
 *
 * <p>Lamp builds these with their no-arg constructor. They get Catalog from
 * {@code context.lamp().dependency}.</p>
 */
public final class Suggestions {

    private Suggestions() {}

    /** Every jar Catalog could manage but is not. */
    public static final class Untracked implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {

            List<String> names = new ArrayList<>();

            for (InstalledJar jar : context.lamp().dependency(Catalog.class).untracked()) {
                names.add(Format.shownName(jar));
            }

            return matching(context, names);
        }

    }

    /** Every plugin the history mentions. */
    public static final class Logged implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {
            return matching(context, context.lamp().dependency(Catalog.class).loggedPlugins());
        }

    }

    /** The kinds of event the history can be narrowed to. */
    public static final class Kinds implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {

            List<String> kinds = new ArrayList<>();

            for (HistoryKind kind : HistoryKind.values()) {
                kinds.add(kind.label());
            }

            return matching(context, kinds);
        }

    }

    /** Every managed plugin. */
    public static final class Tracked implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {
            return matching(context, names(context, plugin -> true));
        }

    }

    /** The plugins with an update waiting and {@code all}. */
    public static final class Updatable implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {

            Set<String> waiting = context.lamp().dependency(Catalog.class).updatesByProject().keySet();
            List<String> names = names(context, plugin -> waiting.contains(plugin.projectId()));

            if (!names.isEmpty()) {
                names.add(0, "all");
            }

            return matching(context, names);
        }

    }

    /** Only the plugins that are not held yet. */
    public static final class Holdable implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {
            return matching(context, names(context, plugin -> !plugin.isPinned()));
        }

    }

    /** Only the plugins that are held. */
    public static final class Held implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {
            return matching(context, names(context, TrackedPlugin::isPinned));
        }

    }

    /** The trashed plugins by the name they were removed under. */
    public static final class Trashed implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {

            List<String> names = new ArrayList<>();

            for (TrashEntry entry : context.lamp().dependency(Catalog.class).trashed()) {
                names.add(quoted(entry.displayName()));
            }

            return matching(context, names);
        }

    }

    /** The trashed plugins and {@code all}. */
    public static final class Discardable implements SuggestionProvider<CommandActor> {

        @Override
        public Collection<String> getSuggestions(@NotNull ExecutionContext<CommandActor> context) {

            List<String> names = new ArrayList<>();

            for (TrashEntry entry : context.lamp().dependency(Catalog.class).trashed()) {
                names.add(quoted(entry.displayName()));
            }

            if (!names.isEmpty()) {
                names.add(0, "all");
            }

            return matching(context, names);
        }

    }

    /**
     * Narrows a list of completions to what has been typed so far.
     *
     * <p>Lamp sends server-side suggestions without filtering them by the input.</p>
     */
    private static Collection<String> matching(ExecutionContext<CommandActor> context,
                                               List<String> names) {

        StringStream input = context.input();
        return matching(input.hasRemaining() ? input.peekRemaining() : "", names);
    }

    /**
     * @param typed what has been written for this argument with or without quotes
     * @param names every completion the command could offer
     * @return the ones that start with what was typed
     */
    static List<String> matching(String typed, List<String> names) {

        String wanted = unquoted(typed).toLowerCase(Locale.ROOT);

        if (wanted.isEmpty()) {
            return names;
        }

        List<String> matched = new ArrayList<>();

        for (String name : names) {
            if (unquoted(name).toLowerCase(Locale.ROOT).startsWith(wanted)) {
                matched.add(name);
            }
        }

        return matched;
    }

    /**
     * Drops the opening quote of a name with a space.
     */
    private static String unquoted(String value) {

        String trimmed = value.trim();
        return trimmed.startsWith("\"") ? trimmed.substring(1) : trimmed;
    }

    private static List<String> names(ExecutionContext<CommandActor> context, Predicate<TrackedPlugin> filter) {

        Catalog catalog = context.lamp().dependency(Catalog.class);
        List<String> names = new ArrayList<>();

        for (TrackedPlugin tracked : catalog.getTracking().all()) {

            if (filter.test(tracked)) {
                names.add(suggestionFor(tracked));
            }
        }

        return names;
    }

    /**
     * The name a plugin is listed under.
     */
    private static String suggestionFor(TrackedPlugin tracked) {

        String name = tracked.displayName();

        if (name.indexOf(' ') >= 0 && name.indexOf('"') >= 0) {
            return tracked.slug() != null ? tracked.slug() : name;
        }

        return quoted(name);
    }

    private static String quoted(String name) {

        if (name.indexOf(' ') < 0 || name.indexOf('"') >= 0) {
            return name;
        }

        return '"' + name + '"';
    }

}
