package top.vulpine.catalog.paper.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Trail")
class TrailTest {

    @Test
    @DisplayName("a screen opened from nothing is its own trail")
    void noParent() {
        assertEquals("info:luckperms", Trail.within("info:luckperms", null));
        assertNull(Trail.parent("info:luckperms"));
    }

    @Test
    @DisplayName("the current screen and its parent read back")
    void currentAndParent() {

        String trail = Trail.within("versions:luckperms", Trail.within("deps:luckperms@abc", "info:luckperms"));

        assertEquals("versions:luckperms", Trail.current(trail));
        assertEquals("deps:luckperms@abc<info:luckperms", Trail.parent(trail));
        assertEquals("info:luckperms", Trail.parent(Trail.parent(trail)));
    }

    @Test
    @DisplayName("the oldest screens past the depth are dropped")
    void depth() {

        String trail = "a";

        for (String screen : new String[]{"b", "c", "d", "e", "f"}) {
            trail = Trail.within(screen, trail);
        }

        assertEquals("f<e<d<c", trail);
    }

    @Test
    @DisplayName("nothing carried has no screen")
    void nothing() {
        assertNull(Trail.current(null));
        assertNull(Trail.parent(null));
    }

}
