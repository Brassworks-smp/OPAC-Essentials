package brassworks.opac_essentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommandConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> CLAIMS_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> PARTY_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> CLAIM_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> UNCLAIM_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> PARTY_CHAT_COMMAND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Command names used by OPAC - Essentials.",
                "Changing a command name requires a full server restart."
        ).push("commands");

        CLAIMS_COMMAND = defineCommand(
                builder, "claims", "claims",
                "Root alias for Open Parties and Claims claim commands."
        );
        PARTY_COMMAND = defineCommand(
                builder, "party", "party",
                "Root alias for Open Parties and Claims party commands."
        );
        CLAIM_COMMAND = defineCommand(
                builder, "claim", "claim",
                "Claims the chunk in which the executing player is standing."
        );
        UNCLAIM_COMMAND = defineCommand(
                builder, "unclaim", "unclaim",
                "Unclaims the chunk in which the executing player is standing."
        );
        PARTY_CHAT_COMMAND = defineCommand(
                builder, "party_chat", "pchat",
                "Root command for OPAC - Essentials party chat."
        );

        builder.pop();
        SPEC = builder.build();
    }

    private CommandConfig() {
    }

    private static ModConfigSpec.ConfigValue<String> defineCommand(
            ModConfigSpec.Builder builder,
            String path,
            String defaultValue,
            String description
    ) {
        return builder.comment(
                description,
                "Use 1-32 lowercase letters, numbers, underscores or hyphens."
        ).define(path, defaultValue, CommandConfig::isValidCommandName);
    }

    private static boolean isValidCommandName(Object value) {
        return value instanceof String commandName
                && commandName.matches("[a-z0-9_-]{1,32}");
    }
}
