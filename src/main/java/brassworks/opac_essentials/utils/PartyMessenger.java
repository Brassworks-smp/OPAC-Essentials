package brassworks.opac_essentials.utils;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class PartyMessenger {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PartyMessenger() {
    }

    public static int sendPartyMessage(ServerPlayer sender, String text) {
        return sendPartyMessage(sender.createCommandSourceStack(), text);
    }

    public static int sendPartyMessage(CommandSourceStack source, String text) {
        String command = "opm " + text;
        try {
            return source.getServer()
                    .getCommands()
                    .getDispatcher()
                    .execute(command, source);
        } catch (CommandSyntaxException exception) {
            source.sendFailure(ComponentUtils.fromMessage(exception.getRawMessage()));
            return 0;
        } catch (RuntimeException exception) {
            LOGGER.error("[OPAC Essentials] /opm failed.", exception);
            String message = exception.getMessage();
            source.sendFailure(Component.literal(
                    message == null || message.isBlank()
                            ? exception.getClass().getSimpleName()
                            : message
            ));
            return 0;
        }
    }
}
