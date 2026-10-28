package top.vulpine.catalog.paper.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Screen")
class ScreenTest {

    @Test
    @DisplayName("every screen reads back from its token")
    void roundTrip() {

        List<Screen> screens = List.of(
                new Screen.ListScreen(EnumSet.noneOf(ListFilter.class)),
                new Screen.ListScreen(EnumSet.of(ListFilter.UPDATES, ListFilter.HELD)),
                new Screen.TrashScreen(),
                new Screen.InfoScreen("luckperms"),
                new Screen.SettingsScreen("luckperms"),
                new Screen.DependenciesScreen("chunkyborder", null),
                new Screen.DependenciesScreen("chunkyborder", "asaBBItO"),
                new Screen.ChangeScreen("install", "luckperms", "abc"),
                new Screen.ChangeScreen("update", "luckperms", null));

        for (Screen screen : screens) {
            assertEquals(screen, Screen.parse(screen.token()), screen.token());
        }
    }

    @Test
    @DisplayName("a screen reopens with the command that opened it")
    void reopen() {
        assertEquals("/catalog list --updates", new Screen.ListScreen(Set.of(ListFilter.UPDATES)).reopen());
        assertEquals("/catalog dependencies chunkyborder asaBBItO",
                new Screen.DependenciesScreen("chunkyborder", "asaBBItO").reopen());
        assertEquals("/catalog install luckperms abc", new Screen.ChangeScreen("install", "luckperms", "abc").reopen());
    }

    @Test
    @DisplayName("a token that names no screen reads as nothing")
    void unknown() {
        assertNull(Screen.parse(null));
        assertNull(Screen.parse("listing"));
        assertNull(Screen.parse("-"));
    }

}
