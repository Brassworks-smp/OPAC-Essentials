package brassworks.opac_essentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommandConfig {
    public static final ModConfigSpec SPEC = EssentialsConfig.SPEC;
    public static final ModConfigSpec.ConfigValue<String> CLAIMS_COMMAND =
            EssentialsConfig.CLAIMS_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> PARTY_COMMAND =
            EssentialsConfig.PARTY_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> CLAIM_COMMAND =
            EssentialsConfig.CLAIM_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> UNCLAIM_COMMAND =
            EssentialsConfig.UNCLAIM_COMMAND;
    public static final ModConfigSpec.ConfigValue<String> PARTY_CHAT_COMMAND =
            EssentialsConfig.PARTY_CHAT_COMMAND;
    public static final ModConfigSpec.BooleanValue PROTECT_TRAIN_CONTROLS =
            EssentialsConfig.PROTECT_TRAIN_CONTROLS;

    private CommandConfig() {
    }
}
