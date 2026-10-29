package top.vulpine.catalog.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import revxrsal.commands.Lamp;
import revxrsal.commands.command.CommandActor;
import revxrsal.commands.process.SenderResolver;
import top.vulpine.catalog.command.navigation.ClickCommand;
import top.vulpine.catalog.platform.Viewer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Commands")
class CommandsTest {

    /**
     * Lamp checks the shape of every command at registration. Optional parameters, flags and
     * parameters with spaces must come last. A wrong shape keeps the plugin from loading.
     */
    @Test
    @DisplayName("every command has a shape Lamp will accept")
    void registers() {

        // Stands in for the platform.
        SenderResolver<CommandActor> sender = new SenderResolver<>() {

            @Override
            public boolean isSenderType(revxrsal.commands.command.CommandParameter parameter) {
                return Viewer.class.isAssignableFrom(parameter.type());
            }

            @Override
            public Object getSender(Class<?> type, CommandActor actor,
                                    revxrsal.commands.command.ExecutableCommand<CommandActor> command) {
                throw new UnsupportedOperationException("nothing is executed here");
            }
        };

        Lamp<CommandActor> lamp = Lamp.<CommandActor>builder()
                .permissionFactory((annotations, l) -> actor -> true)
                .senderResolver(sender)
                .build();

        // Never dereferenced: registration only reflects over the classes.
        assertDoesNotThrow(() -> {
            lamp.register(new Commands(null, null).registries().toArray());
            lamp.register(new ClickCommand(null));
        });
    }

}
