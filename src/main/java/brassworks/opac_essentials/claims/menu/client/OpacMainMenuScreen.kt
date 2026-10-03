package brassworks.opac_essentials.claims.menu.client

import brassworks.opac_essentials.client.ResponsiveBrassScreen
import brassworks.opac_essentials.claims.permission.client.OpacEssentialsUiTheme
import brassworks.opac_essentials.claims.menu.ClaimQuickConfigPayload
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork
import brassworks.opac_essentials.claims.permission.network.OpenSelectedClaimPermissionsPayload
import brassworks.opac_essentials.claims.purchase.ClaimPurchasePayload
import brassworks.opac_essentials.claims.purchase.ClaimPurchaseRules
import brassworks.opac_essentials.claims.purchase.ClaimPurchaseSettingsPayload
import brassworks.opac_essentials.claims.purchase.client.ClaimPurchaseClientState
import brassworks.opac_essentials.party.menu.client.PartyMenuClientPayloadHandler
import brassworks.opac_essentials.party.menu.network.PartyMenuActionPayload
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.plus
import gg.essential.universal.UMatrixStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.input.BrassSlider
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassWindow
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.awt.Color
import java.util.Locale

class OpacMainMenuScreen(
    private val originalMenu: Screen,
    private val parent: Screen?,
) : ResponsiveBrassScreen(
    WINDOW_WIDTH,
    WINDOW_HEIGHT,
    WINDOW_HORIZONTAL_MARGIN,
    WINDOW_VERTICAL_MARGIN,
    Color(0, 0, 0, 72),
) {
    private var purchaseAmount = 1
    private var currencyItemId = ClaimPurchaseClientState.currencyItemId()
    private var currencyName = ClaimPurchaseClientState.currencyName()
    private var pricePerClaim = ClaimPurchaseClientState.pricePerClaim()
    private var currentClaims = ClaimPurchaseClientState.currentClaims()
    private var claimCount = ClaimPurchaseClientState.claimCount()
    private var forceloadCount = ClaimPurchaseClientState.forceloadCount()
    private var forceloadLimit = ClaimPurchaseClientState.forceloadLimit()
    private var partyName = ClaimPurchaseClientState.partyName()
    private var partyOwner = ClaimPurchaseClientState.partyOwner()
    private var partyNameEditable = ClaimPurchaseClientState.partyNameEditable()
    private var partyMembers = ClaimPurchaseClientState.partyMembers()
    private var partyAllies = ClaimPurchaseClientState.partyAllies()
    private var partyInvites = ClaimPurchaseClientState.partyInvites()
    private var claimColor = ClaimPurchaseClientState.claimColor()
    private var nextPurchaseRefreshAt = 0L
    private lateinit var previousThemeId: String
    private var previousAccentHex: String? = null
    private var themeApplied = false
    private var uiReady = false
    private lateinit var partyNameInput: BrassTextInput
    private lateinit var ownerLabel: BrassLabel
    private lateinit var partyStatsLabel: BrassLabel
    private lateinit var claimStatsLabel: BrassLabel
    private lateinit var forceloadStatsLabel: BrassLabel
    private lateinit var claimColorTag: BrassTag
    private lateinit var unitPriceLabel: BrassLabel
    private lateinit var costLabel: BrassLabel
    private lateinit var purchaseButton: BrassButton

    override fun afterInitialization() {
        super.afterInitialization()
        uiReady = false
        if (!themeApplied) {
            previousThemeId = BrassThemes.currentId
            previousAccentHex = BrassThemes.accentHex
            themeApplied = true
        }
        OpacEssentialsUiTheme.applyMainMenu()
        buildUi()
        uiReady = true
        refreshOverview()
        refreshPurchase()
        ClaimPermissionsNetwork.sendToServer(ClaimPurchasePayload(0))
        OpacEssentialsUiTheme.disableAnimations(background)
    }

    private fun buildUi() {
        val frame = BrassWindow(
            title = "OPAC",
            onClose = ::returnToParent,
            controls = false,
            minW = 320f,
            minH = 250f,
        ).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            width = responsiveWindowWidthConstraint()
            height = responsiveWindowHeightConstraint()
        } childOf background

        BrassSquareButton(BrassIcons.NONE, BrassAccent.DANGER) { returnToParent() }.also {
            it.entranceEnabled = false
        }.constrain {
            x = 7.pixels(true)
            y = 4.pixels()
            width = 18.pixels()
            height = 10.pixels()
        } childOf frame

        val scroll = ScrollComponent(
            emptyString = "",
            horizontalScrollEnabled = false,
            verticalScrollEnabled = true,
        ).constrain {
            width = 100.percent()
            height = 100.percent()
        } childOf frame.content

        val content = UIContainer().constrain {
            width = 100.percent()
            height = basicHeightConstraint { component ->
                maxOf(component.parent.getHeight(), CONTENT_HEIGHT)
            }
        } childOf scroll

        BrassButton("Player Config") {
            runOriginalAction(setOf("config"), 0)
        }.also {
            it.chrome = BrassChrome.FLAT
        }.constrain {
            x = 10.pixels()
            y = 10.pixels()
            width = 34.percent() - 13.pixels()
            height = 20.pixels()
        } childOf content

        BrassButton("Permissions") {
            ClaimPermissionsNetwork.sendToServer(OpenSelectedClaimPermissionsPayload())
            Minecraft.getInstance().setScreen(null)
        }.also {
            it.chrome = BrassChrome.FLAT
        }.constrain {
            x = 34.percent()
            y = 10.pixels()
            width = 32.percent()
            height = 20.pixels()
        } childOf content

        BrassButton("Parties") {
            PartyMenuClientPayloadHandler.requestOpen(this)
            ClaimPermissionsNetwork.sendToServer(
                PartyMenuActionPayload(
                    PartyMenuActionPayload.Action.OPEN,
                    null,
                    "",
                ),
            )
        }.also {
            it.chrome = BrassChrome.FLAT
        }.constrain {
            x = 66.percent() + 3.pixels()
            y = 10.pixels()
            width = 34.percent() - 13.pixels()
            height = 20.pixels()
        } childOf content

        partyNameInput = BrassTextInput(
            initial = partyName,
            placeholder = "Party name",
        ) {
            refreshOverview()
        }.constrain {
            x = 10.pixels()
            y = 38.pixels()
            width = 100.percent() - 20.pixels()
            height = 19.pixels()
        } childOf content
        partyNameInput.onSubmit = { savePartyName() }
        partyNameInput.onFocusLost { savePartyName() }

        ownerLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 10.pixels()
            y = 65.pixels()
        } childOf content

        partyStatsLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 10.pixels()
            y = 77.pixels()
        } childOf content

        claimStatsLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 10.pixels()
            y = 89.pixels()
        } childOf content

        forceloadStatsLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 10.pixels()
            y = 101.pixels()
        } childOf content

        val claimColorTitle = BrassLabel(
            "Claim Color:",
            Colors.UI_TEXT_DARK,
            scale = 0.86f,
        ).constrain {
            x = 10.pixels()
            y = 113.pixels()
        } childOf content

        claimColorTag = BrassTag(claimColor.toHexColor(), claimColorValue()).constrain {
            x = basicXConstraint { claimColorTitle.getRight() + 2f }
            y = 112.pixels()
        } childOf content

        BrassButton("") {
            openClaimColorPicker()
        }.also {
            it.chrome = BrassChrome.NONE
            it.entranceEnabled = false
        }.constrain {
            x = basicXConstraint { claimColorTag.getLeft() }
            y = 110.pixels()
            width = basicWidthConstraint { claimColorTag.getWidth() }
            height = 16.pixels()
        } childOf content

        val purchase = BrassPanel(
            title = "BUY EXTRA CLAIMS",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 10.pixels()
            y = 142.pixels()
            width = 100.percent() - 20.pixels()
            height = 101.pixels()
        } childOf content

        unitPriceLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = CenterConstraint()
        } childOf purchase.content

        BrassSlider(
            min = 1f,
            max = ClaimPurchaseRules.MAX_PURCHASE.toFloat(),
            initial = purchaseAmount.toFloat(),
            step = 1f,
            format = { value ->
                val amount = value.toInt()
                "$amount ${if (amount == 1) "claim" else "claims"}"
            },
            onChange = { value ->
                purchaseAmount = value.toInt()
                refreshPurchase()
            },
        ).constrain {
            x = 8.pixels()
            y = 14.pixels()
            width = 100.percent() - 16.pixels()
            height = 18.pixels()
        } childOf purchase.content

        costLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = CenterConstraint()
            y = 37.pixels()
        } childOf purchase.content

        purchaseButton = BrassButton("Purchase", BrassAccent.BRASS) {
            ClaimPermissionsNetwork.sendToServer(ClaimPurchasePayload(purchaseAmount))
            Minecraft.getInstance().setScreen(null)
        }.constrain {
            x = CenterConstraint()
            y = 49.pixels()
            width = 126.pixels()
            height = 20.pixels()
        } childOf purchase.content
    }

    override fun onDrawScreen(
        matrixStack: UMatrixStack,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        val now = System.currentTimeMillis()
        if (uiReady && now >= nextPurchaseRefreshAt) {
            nextPurchaseRefreshAt = now + PURCHASE_REFRESH_INTERVAL
            refreshPurchase()
        }
        super.onDrawScreen(matrixStack, mouseX, mouseY, partialTicks)
    }

    override fun onScreenClose() {
        uiReady = false
        super.onScreenClose()
        if (themeApplied) {
            BrassThemes.apply(previousThemeId, previousAccentHex)
        }
    }

    private fun refreshPurchase() {
        if (!uiReady) return
        unitPriceLabel.text = "$pricePerClaim x $currencyName per claim"
        val cost = purchaseAmount * pricePerClaim
        val available = availableCurrency()
        costLabel.text = "$cost x $currencyName  |  Total Claims: $currentClaims"
        val canPurchase = available >= cost && isCurrencyAvailable()
        purchaseButton.accent = if (canPurchase) BrassAccent.BRASS else BrassAccent.DEFAULT
        purchaseButton.active = canPurchase
    }

    private fun refreshOverview() {
        if (!uiReady) return
        ownerLabel.text = "Owner: ${compact(partyOwner, 40)}"
        partyStatsLabel.text =
            "Members: $partyMembers  |  Allies: $partyAllies  |  Invites: $partyInvites"
        claimStatsLabel.text = "Claims: $claimCount / ${formatLimit(currentClaims)}"
        forceloadStatsLabel.text =
            "Forceloads: $forceloadCount / ${formatLimit(forceloadLimit)}"
        claimColorTag.text = claimColor.toHexColor()
        claimColorTag.tint = claimColorValue()
        partyNameInput.active = partyNameEditable
    }

    fun applyPurchaseSettings(payload: ClaimPurchaseSettingsPayload) {
        currencyItemId = payload.currencyItemId()
        currencyName = payload.currencyName()
        pricePerClaim = payload.pricePerClaim()
        currentClaims = payload.currentClaims()
        claimCount = payload.claimCount()
        forceloadCount = payload.forceloadCount()
        forceloadLimit = payload.forceloadLimit()
        partyName = payload.partyName()
        partyOwner = payload.partyOwner()
        partyNameEditable = payload.partyNameEditable()
        partyMembers = payload.partyMembers()
        partyAllies = payload.partyAllies()
        partyInvites = payload.partyInvites()
        claimColor = payload.claimColor()
        if (uiReady) {
            partyNameInput.setTextSilently(partyName)
            refreshOverview()
            refreshPurchase()
        }
    }

    private fun savePartyName() {
        val name = partyNameInput.value.trim()
        if (!partyNameEditable || name == partyName || !isValidPartyName(name)) return
        ClaimPermissionsNetwork.sendToServer(
            ClaimQuickConfigPayload(
                ClaimQuickConfigPayload.Operation.PARTY_NAME,
                name,
                0,
            )
        )
    }

    private fun openClaimColorPicker() {
        var selected = Color(claimColor and 0xFFFFFF)
        val modal = BrassModal("Claim Color", 190f, 190f)
        BrassColorPicker(selected) { color ->
            selected = color
        }.constrain {
            x = 1.pixels()
            y = 1.pixels()
            width = 100.percent() - 2.pixels()
            height = 100.percent() - 2.pixels()
        } childOf modal.body
        val cancel = BrassButton("Cancel") {
            modal.dismiss()
        }.also {
            it.chrome = BrassChrome.FLAT
        }
        val save = BrassButton("Save", BrassAccent.BRASS) {
            ClaimPermissionsNetwork.sendToServer(
                ClaimQuickConfigPayload(
                    ClaimQuickConfigPayload.Operation.CLAIM_COLOR,
                    "",
                    selected.rgb and 0xFFFFFF,
                )
            )
            modal.dismiss()
        }.also {
            it.chrome = BrassChrome.FLAT
        }
        modal.footer(cancel, save).show(background)
    }

    private fun isValidPartyName(value: String): Boolean =
        value.isNotEmpty() && value.length <= 100 && value.all { character ->
            character.isLetter() || character in '0'..'9' ||
                    character in " _'\"!?,-&%*():"
        }

    private fun compact(value: String, maxLength: Int): String =
        if (value.length <= maxLength) value else value.take(maxLength - 3) + "..."

    private fun formatLimit(value: Int): String =
        if (value == Int.MAX_VALUE) "Unlimited" else value.toString()

    private fun Int.toHexColor(): String =
        String.format(Locale.ROOT, "%06X", this and 0xFFFFFF)

    private fun claimColorValue(): Color = Color(claimColor and 0xFFFFFF)

    private fun runOriginalAction(keywords: Set<String>, fallbackIndex: Int) {
        val minecraft = Minecraft.getInstance()
        originalMenu.init(minecraft, width, height)
        val buttons = originalMenu.children().filterIsInstance<AbstractButton>()
        val target = buttons.firstOrNull { button ->
            val label = button.message.string.lowercase(Locale.ROOT)
            keywords.any { keyword -> label.contains(keyword) }
        } ?: buttons.getOrNull(fallbackIndex)
        target?.onPress()
    }

    private fun availableCurrency(): Int {
        val player = Minecraft.getInstance().player ?: return 0
        val currencyId = ResourceLocation.tryParse(currencyItemId) ?: return 0
        return player.inventory.items.sumOf { stack ->
            if (BuiltInRegistries.ITEM.getKey(stack.item) == currencyId) stack.count else 0
        }
    }

    private fun isCurrencyAvailable(): Boolean {
        val currencyId = ResourceLocation.tryParse(currencyItemId) ?: return false
        return BuiltInRegistries.ITEM.get(currencyId) != Items.AIR
    }

    private fun returnToParent() {
        Minecraft.getInstance().setScreen(parent)
    }

    companion object {
        private const val WINDOW_WIDTH = 430f
        private const val WINDOW_HEIGHT = 270f
        private const val WINDOW_HORIZONTAL_MARGIN = 28f
        private const val WINDOW_VERTICAL_MARGIN = 24f
        private const val CONTENT_HEIGHT = 250f
        private const val PURCHASE_REFRESH_INTERVAL = 250L
    }
}
