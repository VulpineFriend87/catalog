package top.vulpine.catalog.command.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.vulpine.catalog.command.Commands;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CommandPermissions")
class CommandPermissionsTest {

    private static final CommandPermissions PERMISSIONS = Commands.PERMISSIONS;

    @Test
    @DisplayName("a subcommand needs the node on its method")
    void subcommandNode() {
        assertEquals("command.install", PERMISSIONS.nodeFor("/catalog install luckperms"));
        assertEquals("command.list", PERMISSIONS.nodeFor("/catalog list --updates"));
        assertEquals("command.history", PERMISSIONS.nodeFor("/catalog history --event failed"));
    }

    @Test
    @DisplayName("the longest subcommand wins")
    void longestSubcommand() {
        assertEquals("command.trash", PERMISSIONS.nodeFor("/catalog trash restore 123-LuckPerms.jar"));
        assertEquals("command.trash", PERMISSIONS.nodeFor("/catalog trash"));
    }

    @Test
    @DisplayName("about and help need any command node")
    void anyNode() {
        assertEquals(CommandPermissions.ANY, PERMISSIONS.nodeFor("/catalog"));
        assertEquals(CommandPermissions.ANY, PERMISSIONS.nodeFor("/catalog help"));
    }

    @Test
    @DisplayName("aliases and button wrappers resolve to the same node")
    void aliasesAndWrappers() {
        assertEquals("command.uninstall", PERMISSIONS.nodeFor("/ctlg uninstall luckperms"));
        assertEquals("command.uninstall",
                PERMISSIONS.nodeFor("/catalog-do info:luckperms /catalog uninstall luckperms"));
    }

    @Test
    @DisplayName("another plugin's command needs nothing from Catalog")
    void foreignCommand() {
        assertNull(PERMISSIONS.nodeFor("/plugins"));
    }

    @Test
    @DisplayName("every subcommand has a node")
    void everySubcommandCovered() {

        for (String sub : Set.of("list", "info", "search", "install", "versions", "reload",
                "settings", "auto", "soak", "channel", "update", "history", "cancel", "uninstall",
                "dependencies", "trash", "untrack", "track", "hold", "unhold")) {
            assertTrue(PERMISSIONS.nodeFor("/catalog " + sub) != null
                    && !PERMISSIONS.nodeFor("/catalog " + sub).equals(CommandPermissions.ANY), sub);
        }
    }

}
