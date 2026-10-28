package top.vulpine.catalog.command.permission;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.catalog.command.screen.Outcomes;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Permitted")
class PermittedTest {

    private static Component button(String label, String command) {
        return Component.text("[", NamedTextColor.GRAY)
                .append(Component.text(label, NamedTextColor.WHITE))
                .append(Component.text("]", NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand(command))
                .insertion(command);
    }

    private static String plain(Component component, Predicate<String> allowed) {
        return PlainTextComponentSerializer.plainText().serialize(Permitted.filter(component, allowed));
    }

    private static final Predicate<String> NO_UNINSTALL = command -> !command.contains("uninstall");

    @Test
    @DisplayName("a button the viewer cannot use is removed with its space")
    void removesButton() {

        Component row = Component.text("  LuckPerms")
                .append(Component.space())
                .append(button("↑", "/catalog update luckperms"))
                .append(Component.space())
                .append(button("×", "/catalog uninstall luckperms"));

        assertEquals("  LuckPerms [↑]", plain(row, NO_UNINSTALL));
    }

    @Test
    @DisplayName("a row the viewer cannot act on keeps its text, loses its click and the action")
    void rowLosesClick() {

        Component row = Outcomes.autoUpdateTurnedOff("5.5.72", "luckperms");
        Component permitted = Permitted.filter(row, command -> !command.contains("settings"));

        assertEquals("Auto-update was turned off",
                PlainTextComponentSerializer.plainText().serialize(permitted));
        assertNull(permitted.clickEvent());
        assertFalse(PlainTextComponentSerializer.plainText()
                .serialize((Component) permitted.hoverEvent().value()).contains("Open settings"));
    }

    @Test
    @DisplayName("a row the viewer can act on is left alone")
    void rowKeepsClick() {

        Component row = Outcomes.autoUpdateTurnedOff("5.5.72", "luckperms");

        assertEquals(row, Permitted.filter(row, command -> true));
    }

    @Test
    @DisplayName("the first button of a row takes the space after it")
    void removesFirstButton() {

        Component row = Component.text("  ")
                .append(button("Remove", "/catalog uninstall luckperms"))
                .append(Component.space())
                .append(button("Versions", "/catalog versions luckperms"));

        assertEquals("  [Versions]", plain(row, NO_UNINSTALL));
    }

    @Test
    @DisplayName("a viewer who cannot run what a button leads to sees what it declares instead")
    void swapsWhatIsDeclared() {

        Component row = Component.text("  ").append(Permitted.unless("/catalog install luckperms",
                button("Switch", "/catalog versions luckperms"),
                button("Versions", "/catalog versions luckperms")));

        assertEquals("  [Versions]", plain(row, command -> !command.contains("install")));
        assertEquals("  [Switch]", plain(row, command -> true));
    }

}
