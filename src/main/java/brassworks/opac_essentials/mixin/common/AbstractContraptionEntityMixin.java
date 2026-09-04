package brassworks.opac_essentials.mixin.common;

import brassworks.opac_essentials.compat.openpac.OpacCreateDisassemblyBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Pseudo
@Mixin(
        targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity",
        priority = 1_000_002,
        remap = false
)
public abstract class AbstractContraptionEntityMixin {
    @Unique
    private long opacEssentials$lastDisassemblyMessageTick = Long.MIN_VALUE;

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, remap = false)
    private void opacEssentials$preflightDisassembly(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide() || !entity.isAlive()) {
            return;
        }

        OpacCreateDisassemblyBridge.Result result = OpacCreateDisassemblyBridge.check(entity.level(), this);
        if (result.allowed()) {
            return;
        }

        callback.cancel();
        opacEssentials$notifyPlayer(entity, result);
    }

    @Unique
    private void opacEssentials$notifyPlayer(Entity entity, OpacCreateDisassemblyBridge.Result result) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        if (opacEssentials$lastDisassemblyMessageTick != Long.MIN_VALUE
                && now - opacEssentials$lastDisassemblyMessageTick < 40L) {
            return;
        }
        opacEssentials$lastDisassemblyMessageTick = now;

        UUID controllerId = OpacCreateDisassemblyBridge.controllingPlayer(this).orElse(null);
        Player player = controllerId == null ? null : level.getPlayerByUUID(controllerId);
        if (player == null) {
            player = level.getNearestPlayer(
                    entity.getX(), entity.getY(), entity.getZ(), 32.0D, false
            );
        }
        if (player == null) {
            return;
        }

        BlockPos deniedPos = result.deniedPos();
        if (deniedPos != null) {
            player.sendSystemMessage(Component.translatable(
                    "message.opac_essentials.create_disassembly_protected",
                    deniedPos.getX(), deniedPos.getY(), deniedPos.getZ()
            ));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.opac_essentials.create_disassembly_check_failed"
            ));
        }
    }
}
