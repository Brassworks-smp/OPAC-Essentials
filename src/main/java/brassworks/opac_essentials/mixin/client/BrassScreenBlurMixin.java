package brassworks.opac_essentials.mixin.client;

import brassworks.opac_essentials.claims.permission.client.screen.ClaimPermissionsScreen;
import gg.essential.universal.UMatrixStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.swzo.brass.ui.BrassScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BrassScreen.class, remap = false)
public abstract class BrassScreenBlurMixin extends Screen {
    protected BrassScreenBlurMixin(Component title) {
        super(title);
    }

    @Inject(
            method = "onDrawScreen(Lgg/essential/universal/UMatrixStack;IIF)V",
            at = @At("HEAD")
    )
    private void opacEssentials$blurBackground(
            UMatrixStack matrixStack,
            int mouseX,
            int mouseY,
            float partialTicks,
            CallbackInfo callbackInfo
    ) {
        if ((Object) this instanceof ClaimPermissionsScreen) {
            this.renderBlurredBackground(partialTicks);
        }
    }
}
