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
@Mixin(targets = "xaero.map.highlight.ChunkHighlighter",
        priority = 2000, remap = false)
public abstract class XaeroChunkHighlighterMixin {
    private static final String CLAIMS_HIGHLIGHTER =
            "xaero.map.mods.pac.highlight.ClaimsHighlighter";
    private static final int SIGNAL_YELLOW = 0x00FFFFFF;
    private static final int BORDER_THICKNESS = 3;

    @Inject(
            method = "getChunkHighlitColor",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void opacEssentials$outlineSelectedClaimCluster(
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<int[]> callback) {
        if (!CLAIMS_HIGHLIGHTER.equals(getClass().getName())) {
            return;
        }

        int[] colors = callback.getReturnValue();
        if (colors == null
                || colors.length < 256
                || !ClaimSearchClient.isSelectedClusterChunk(
                        dimension.location(),
                        chunkX,
                        chunkZ
                )) {
            return;
        }

        boolean top = !ClaimSearchClient.isSelectedClusterChunk(
                dimension.location(), chunkX, chunkZ - 1
        );
        boolean right = !ClaimSearchClient.isSelectedClusterChunk(
                dimension.location(), chunkX + 1, chunkZ
        );
        boolean bottom = !ClaimSearchClient.isSelectedClusterChunk(
                dimension.location(), chunkX, chunkZ + 1
        );
        boolean left = !ClaimSearchClient.isSelectedClusterChunk(
                dimension.location(), chunkX - 1, chunkZ
        );

        for (int depth = 0; depth < BORDER_THICKNESS; depth++) {
            for (int offset = 0; offset < 16; offset++) {
                if (top) {
                    colors[depth * 16 + offset] = SIGNAL_YELLOW;
                }
                if (right) {
                    colors[offset * 16 + 15 - depth] = SIGNAL_YELLOW;
                }
                if (bottom) {
                    colors[(15 - depth) * 16 + offset] = SIGNAL_YELLOW;
                }
                if (left) {
                    colors[offset * 16 + depth] = SIGNAL_YELLOW;
                }
            }
        }
    }
}
