package brassworks.opac_essentials.claims.purchase;

import brassworks.opac_essentials.config.EssentialsConfig;
import net.minecraft.resources.ResourceLocation;

public final class ClaimPurchaseRules {
    public static final int MAX_PURCHASE = 256;

    private ClaimPurchaseRules() {
    }

    public static ResourceLocation currencyItem() {
        return ResourceLocation.parse(EssentialsConfig.CLAIM_CURRENCY_ITEM.get());
    }

    public static int pricePerClaim() {
        return EssentialsConfig.CLAIM_PRICE_PER_CLAIM.get();
    }
}
