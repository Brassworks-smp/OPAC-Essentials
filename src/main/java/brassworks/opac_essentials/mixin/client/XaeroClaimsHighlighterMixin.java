package brassworks.opac_essentials.mixin.client;

import brassworks.opac_essentials.claims.search.client.ClaimSearchClient;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xaero.map.mods.pac.highlight.ClaimsHighlighter",
        priority = 2000, remap = false)
public abstract class XaeroClaimsHighlighterMixin {
    @Inject(
            method = "calculateRegionHash",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void opacEssentials$includeSelectedClusterInHash(
            ResourceKey<Level> dimension,
            int regionX,
            int regionZ,
            CallbackInfoReturnable<Integer> callback) {
        int revision = ClaimSearchClient.getSelectedClusterHighlightRevision();
        callback.setReturnValue(callback.getReturnValue() ^ revision * 0x9E3779B9);
    }
}
