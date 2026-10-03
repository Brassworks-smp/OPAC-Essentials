package brassworks.opac_essentials.claims.purchase.client;

import brassworks.opac_essentials.claims.menu.client.OpacMainMenuScreen;
import brassworks.opac_essentials.claims.purchase.ClaimPurchaseSettingsPayload;
import net.minecraft.client.Minecraft;

public final class ClaimPurchaseClientState {
    private static String currencyItemId = "minecraft:emerald";
    private static String currencyName = "Emerald";
    private static int pricePerClaim = 1;
    private static int currentClaims;
    private static int claimCount;
    private static int forceloadCount;
    private static int forceloadLimit;
    private static String partyName = "No party";
    private static String partyOwner = "N/A";
    private static boolean partyNameEditable;
    private static int partyMembers;
    private static int partyAllies;
    private static int partyInvites;
    private static int claimColor = 0xFFFFFF;

    private ClaimPurchaseClientState() {
    }

    public static String currencyItemId() {
        return currencyItemId;
    }

    public static String currencyName() {
        return currencyName;
    }

    public static int pricePerClaim() {
        return pricePerClaim;
    }

    public static int currentClaims() {
        return currentClaims;
    }

    public static int claimCount() {
        return claimCount;
    }

    public static int forceloadCount() {
        return forceloadCount;
    }

    public static int forceloadLimit() {
        return forceloadLimit;
    }

    public static String partyName() {
        return partyName;
    }

    public static String partyOwner() {
        return partyOwner;
    }

    public static boolean partyNameEditable() {
        return partyNameEditable;
    }

    public static int partyMembers() {
        return partyMembers;
    }

    public static int partyAllies() {
        return partyAllies;
    }

    public static int partyInvites() {
        return partyInvites;
    }

    public static int claimColor() {
        return claimColor;
    }

    public static void handle(ClaimPurchaseSettingsPayload payload) {
        currencyItemId = payload.currencyItemId();
        currencyName = payload.currencyName();
        pricePerClaim = payload.pricePerClaim();
        currentClaims = payload.currentClaims();
        claimCount = payload.claimCount();
        forceloadCount = payload.forceloadCount();
        forceloadLimit = payload.forceloadLimit();
        partyName = payload.partyName();
        partyOwner = payload.partyOwner();
        partyNameEditable = payload.partyNameEditable();
        partyMembers = payload.partyMembers();
        partyAllies = payload.partyAllies();
        partyInvites = payload.partyInvites();
        claimColor = payload.claimColor();
        if (Minecraft.getInstance().screen instanceof OpacMainMenuScreen screen) {
            screen.applyPurchaseSettings(payload);
        }
    }
}
