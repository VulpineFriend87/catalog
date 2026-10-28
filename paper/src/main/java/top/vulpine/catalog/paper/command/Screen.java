package top.vulpine.catalog.paper.command;

import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * A screen a button can be pressed on, and reopened from its token.
 */
public sealed interface Screen {

    /**
     * @return what a button carries to say it was pressed here
     */
    String token();

    /**
     * @return the command that opens this screen
     */
    String reopen();

    /**
     * Hands this screen to the visitor method for its kind.
     */
    void accept(Visitor visitor);

    /**
     * Something done differently for each screen.
     */
    interface Visitor {

        void list(ListScreen screen);

        void trash(TrashScreen screen);

        void info(InfoScreen screen);

        void settings(SettingsScreen screen);

        void dependencies(DependenciesScreen screen);

        void change(ChangeScreen screen);

    }

    /**
     * @param token a token from {@link #token}
     * @return the screen it names, or null when it names none
     */
    static Screen parse(String token) {

        if (token == null) {
            return null;
        }

        if (token.equals(ListScreen.NAME) || token.startsWith(ListScreen.NAME + ":")) {
            return ListScreen.parse(token);
        }

        if (token.equals(TrashScreen.NAME)) {
            return new TrashScreen();
        }

        if (token.startsWith(InfoScreen.PREFIX)) {
            return new InfoScreen(token.substring(InfoScreen.PREFIX.length()));
        }

        if (token.startsWith(SettingsScreen.PREFIX)) {
            return new SettingsScreen(token.substring(SettingsScreen.PREFIX.length()));
        }

        if (token.startsWith(DependenciesScreen.PREFIX)) {
            return DependenciesScreen.parse(token);
        }

        if (token.startsWith(ChangeScreen.PREFIX)) {
            return ChangeScreen.parse(token);
        }

        return null;
    }

    /**
     * Every managed plugin, narrowed by filters.
     */
    record ListScreen(Set<ListFilter> filters) implements Screen {

        private static final String NAME = "list";

        @Override
        public String token() {

            if (filters.isEmpty()) {
                return NAME;
            }

            StringJoiner joined = new StringJoiner(",", NAME + ":", "");

            for (ListFilter filter : filters) {
                joined.add(filter.label());
            }

            return joined.toString();
        }

        @Override
        public String reopen() {
            return "/catalog list" + ListFilter.switches(filters);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.list(this);
        }

        private static ListScreen parse(String token) {

            Set<ListFilter> filters = EnumSet.noneOf(ListFilter.class);

            if (token.length() > NAME.length()) {
                for (String label : token.substring(NAME.length() + 1).split(",")) {
                    for (ListFilter filter : ListFilter.values()) {
                        if (filter.label().equals(label)) {
                            filters.add(filter);
                        }
                    }
                }
            }

            return new ListScreen(filters);
        }

    }

    /**
     * What has been removed.
     */
    record TrashScreen() implements Screen {

        private static final String NAME = "trash";

        @Override
        public String token() {
            return NAME;
        }

        @Override
        public String reopen() {
            return "/catalog trash";
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.trash(this);
        }

    }

    /**
     * One project's page.
     */
    record InfoScreen(String slug) implements Screen {

        private static final String PREFIX = "info:";

        @Override
        public String token() {
            return PREFIX + slug;
        }

        @Override
        public String reopen() {
            return "/catalog info " + slug;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.info(this);
        }

    }

    /**
     * One plugin's settings.
     */
    record SettingsScreen(String slug) implements Screen {

        private static final String PREFIX = "settings:";

        @Override
        public String token() {
            return PREFIX + slug;
        }

        @Override
        public String reopen() {
            return "/catalog settings " + slug;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.settings(this);
        }

    }

    /**
     * What one build declares.
     *
     * @param versionId the build, or null for the one the command picks
     */
    record DependenciesScreen(String slug, String versionId) implements Screen {

        private static final String PREFIX = "deps:";

        @Override
        public String token() {
            return PREFIX + slug + (versionId == null ? "" : "@" + versionId);
        }

        @Override
        public String reopen() {
            return "/catalog dependencies " + slug + (versionId == null ? "" : " " + versionId);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.dependencies(this);
        }

        private static DependenciesScreen parse(String token) {

            String about = token.substring(PREFIX.length());
            int at = about.indexOf('@');

            return at < 0 ? new DependenciesScreen(about, null)
                    : new DependenciesScreen(about.substring(0, at), about.substring(at + 1));
        }

    }

    /**
     * The question asked before an install, update or switch.
     *
     * @param verb      the subcommand that asked: install or update
     * @param versionId the build, or null when none was named
     */
    record ChangeScreen(String verb, String slug, String versionId) implements Screen {

        private static final String PREFIX = "do:";

        @Override
        public String token() {
            return PREFIX + verb + ":" + slug + (versionId == null ? "" : "@" + versionId);
        }

        @Override
        public String reopen() {
            return "/catalog " + verb + " " + slug + (versionId == null ? "" : " " + versionId);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.change(this);
        }

        private static ChangeScreen parse(String token) {

            String rest = token.substring(PREFIX.length());
            int colon = rest.indexOf(':');

            if (colon < 0) {
                return null;
            }

            String about = rest.substring(colon + 1);
            int at = about.indexOf('@');

            return at < 0 ? new ChangeScreen(rest.substring(0, colon), about, null)
                    : new ChangeScreen(rest.substring(0, colon), about.substring(0, at),
                    about.substring(at + 1));
        }

    }

}
