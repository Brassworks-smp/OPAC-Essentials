package brassworks.opac_essentials.claims.permission.client.screen

import brassworks.opac_essentials.client.ResponsiveBrassScreen
import brassworks.opac_essentials.claims.permission.client.OpacEssentialsUiTheme
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionMutationPayload
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassWindow
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.util.EnumSet
import java.util.Locale

class AddClaimPermissionScreen(
    private val parent: Screen,
    private val payload: ClaimPermissionsSyncPayload,
) : ResponsiveBrassScreen(
    WINDOW_WIDTH,
    WINDOW_HEIGHT,
    WINDOW_HORIZONTAL_MARGIN,
    WINDOW_VERTICAL_MARGIN,
    OpacEssentialsUiTheme.BACKDROP,
) {

    private var target = ClaimPermissionTarget.BLOCK
    private val selectedActions = EnumSet.of(ClaimPermissionAction.INTERACT)
    private val actionCheckboxes = mutableMapOf<ClaimPermissionAction, BrassCheckbox>()
    private var saving = false
    private lateinit var previousThemeId: String
    private var previousAccentHex: String? = null
    private var themeApplied = false

    private lateinit var categoryOptions: UIContainer
    private lateinit var targetInput: BrassTextInput
    private lateinit var targetSuggestions: UIContainer
    private lateinit var actionOptions: UIContainer
    private lateinit var playerInput: BrassTextInput
    private lateinit var playerSuggestions: UIContainer
    private lateinit var validationLabel: BrassLabel
    private lateinit var submitButton: BrassButton

    override fun afterInitialization() {
        super.afterInitialization()
        if (!themeApplied) {
            previousThemeId = BrassThemes.currentId
            previousAccentHex = BrassThemes.accentHex
            themeApplied = true
        }
        OpacEssentialsUiTheme.apply()
        ClaimPermissionTarget.prepareRegisteredIds()
        buildUi()
        rebuildCategories()
        rebuildActions()
        refreshTargetSuggestions()
        refreshPlayerSuggestions()
        refreshValidation()
        OpacEssentialsUiTheme.disableAnimations(background)
    }

    override fun onScreenClose() {
        super.onScreenClose()
        if (themeApplied) {
            BrassThemes.apply(previousThemeId, previousAccentHex)
        }
    }

    private fun buildUi() {
        val frame = BrassWindow(
            title = "OPAC",
            subtitle = "Claims  /  Permissions  /  Add",
            onClose = ::returnToParent,
            controls = false,
            minW = 340f,
            minH = 330f,
        ).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            width = responsiveWindowWidthConstraint()
            height = responsiveWindowHeightConstraint()
        } childOf background

        BrassLabel("Add permissions to ${payload.scopeName()}", Colors.UI_TEXT_DARK).constrain {
            x = 12.pixels()
            y = 8.pixels()
        } childOf frame.content

        val formPanel = BrassPanel(
            title = "NEW PERMISSION",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 12.pixels()
            y = 27.pixels()
            width = 100.percent() - 24.pixels()
            height = 100.percent() - 63.pixels()
        } childOf frame.content

        BrassLabel("CATEGORY", Colors.UI_TEXT_DARK).constrain {
            y = 0.pixels()
        } childOf formPanel.content

        categoryOptions = UIContainer().constrain {
            y = 13.pixels()
            width = 100.percent()
            height = 20.pixels()
        } childOf formPanel.content

        BrassLabel("TARGET ID", Colors.UI_TEXT_DARK).constrain {
            y = 42.pixels()
        } childOf formPanel.content

        targetInput = BrassTextInput(
            initial = "",
            placeholder = "minecraft:acacia_button",
        ) { value ->
            refreshTargetSuggestions(value)
            refreshValidation()
        }.constrain {
            y = 55.pixels()
            width = 100.percent()
            height = 19.pixels()
        } childOf formPanel.content

        targetSuggestions = UIContainer().constrain {
            y = 78.pixels()
            width = 100.percent()
            height = 43.pixels()
        } childOf formPanel.content

        BrassLabel("ACTIONS", Colors.UI_TEXT_DARK).constrain {
            y = 126.pixels()
        } childOf formPanel.content

        actionOptions = UIContainer().constrain {
            y = 140.pixels()
            width = 100.percent()
            height = 22.pixels()
        } childOf formPanel.content

        BrassLabel("PLAYER", Colors.UI_TEXT_DARK).constrain {
            y = 170.pixels()
        } childOf formPanel.content

        playerInput = BrassTextInput(
            initial = "all",
            placeholder = "all or player name",
        ) { value ->
            refreshPlayerSuggestions(value)
            refreshValidation()
        }.constrain {
            y = 183.pixels()
            width = 100.percent()
            height = 19.pixels()
        } childOf formPanel.content

        playerSuggestions = UIContainer().constrain {
            y = 206.pixels()
            width = 100.percent()
            height = 31.pixels()
        } childOf formPanel.content

        validationLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.9f).constrain {
            y = 100.percent() - 14.pixels()
        } childOf formPanel.content

        BrassButton("Cancel", BrassAccent.DEFAULT) { returnToParent() }.constrain {
            x = 12.pixels()
            y = 100.percent() - 27.pixels()
            width = 72.pixels()
            height = 18.pixels()
        } childOf frame.content

        submitButton = BrassButton("Add permission", BrassAccent.BRASS) { submit() }.constrain {
            x = 100.percent() - 106.pixels()
            y = 100.percent() - 27.pixels()
            width = 94.pixels()
            height = 18.pixels()
        } childOf frame.content
    }

    private fun rebuildCategories() {
        categoryOptions.clearChildren()
        val options = ClaimPermissionTarget.availableTargets()
        BrassTabSwitch(
            options = options.map { ClaimPermissionsScreen.displayTargetName(it.name) },
            initialIndex = options.indexOf(target).coerceAtLeast(0),
            equalWidths = true,
        ) { index ->
            selectTarget(options[index])
        }.also { tabs ->
            tabs.children.filterIsInstance<BrassButton>().forEach { button ->
                button.chrome = BrassChrome.FLAT
            }
        }.constrain {
            width = 100.percent() -
                    (options.size - 1).coerceAtLeast(0).pixels()
            height = 20.pixels()
        } childOf categoryOptions
    }

    private fun selectTarget(option: ClaimPermissionTarget) {
        if (option == target) return
        target = option

        val supported = ClaimPermissionsScreen.supportedActions(target)
        selectedActions.retainAll(supported.toSet())
        if (selectedActions.isEmpty() && supported.isNotEmpty()) {
            selectedActions.add(supported.first())
        }

        val trainId = target.registeredIds().findFirst().orElse(null)
        val currentId = ResourceLocation.tryParse(targetInput.text.trim())
        targetInput.value = when {
            target == ClaimPermissionTarget.TRAIN && trainId != null -> trainId.toString()
            currentId == null || !target.isRegistered(currentId) -> ""
            else -> targetInput.text
        }
        targetInput.active = target != ClaimPermissionTarget.TRAIN

        rebuildActions()
        refreshTargetSuggestions()
        refreshValidation()
    }

    private fun rebuildActions() {
        actionOptions.clearChildren()
        actionCheckboxes.clear()
        val supported = ClaimPermissionsScreen.supportedActions(target)

        supported.forEachIndexed { index, action ->
            val row = UIContainer().constrain {
                x = (index * 100f / supported.size).percent()
                width = (100f / supported.size).percent()
                height = 22.pixels()
            } childOf actionOptions

            val checkbox = BrassCheckbox(initial = selectedActions.contains(action)) { checked ->
                if (checked) selectedActions.add(action) else selectedActions.remove(action)
                refreshValidation()
            }.constrain {
                x = 1.pixels()
                y = CenterConstraint()
                width = 14.pixels()
                height = 14.pixels()
            } childOf row
            actionCheckboxes[action] = checkbox

            BrassLabel(ClaimPermissionsScreen.displayName(action.name), Colors.UI_TEXT).constrain {
                x = 20.pixels()
                y = CenterConstraint()
            } childOf row
        }
    }

    private fun refreshTargetSuggestions(value: String = targetInput.text) {
        targetSuggestions.clearChildren()
        val query = value.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return

        target.registeredIds()
            .map(ResourceLocation::toString)
            .filter { id -> matchesRegistryId(id, query) && !id.equals(query, ignoreCase = true) }
            .sorted(compareBy<String> { matchRank(it, query) }.thenBy { it })
            .limit(MAX_TARGET_SUGGESTIONS.toLong())
            .toList()
            .forEachIndexed { index, id ->
                BrassButton(id) {
                    targetInput.value = id
                    targetSuggestions.clearChildren()
                    refreshValidation()
                }.constrain {
                    y = (index * 14).pixels()
                    width = 100.percent()
                    height = 13.pixels()
                } childOf targetSuggestions
            }
    }

    private fun refreshPlayerSuggestions(value: String = playerInput.text) {
        playerSuggestions.clearChildren()
        val query = value.trim().lowercase(Locale.ROOT)
        val names = sortedSetOf(String.CASE_INSENSITIVE_ORDER, "all")
        Minecraft.getInstance().connection?.onlinePlayers?.forEach { player ->
            names.add(player.profile.name)
        }

        names.asSequence()
            .filter { it.lowercase(Locale.ROOT).startsWith(query) }
            .filter { !it.equals(query, ignoreCase = true) }
            .take(MAX_PLAYER_SUGGESTIONS)
            .forEachIndexed { index, name ->
                BrassButton(name) {
                    playerInput.value = name
                    playerSuggestions.clearChildren()
                    refreshValidation()
                }.constrain {
                    y = (index * 14).pixels()
                    width = 100.percent()
                    height = 13.pixels()
                } childOf playerSuggestions
            }
    }

    private fun refreshValidation() {
        if (!::validationLabel.isInitialized || !::submitButton.isInitialized) return

        val message = validationProblem()
        if (message != null) {
            validationLabel.text = message
            validationLabel.tint = if (targetInput.text.isBlank()) Colors.UI_TEXT_DARK else Colors.DANGER
            submitButton.active = false
            return
        }

        val player = playerInput.text.trim().ifBlank { "all" }
        val count = selectedActions.count { target.supports(it) }
        validationLabel.text = "Ready: $count ${if (count == 1) "permission" else "permissions"} for $player"
        validationLabel.tint = Colors.UI_TEXT_DARK
        submitButton.active = !saving
    }

    private fun validationProblem(): String? {
        val rawId = targetInput.text.trim()
        if (rawId.isBlank()) return "Choose or enter a target ID."

        val targetId = ResourceLocation.tryParse(rawId)
            ?: return "Invalid registry ID."
        if (!target.isRegistered(targetId)) return "That registry ID does not exist in this category."
        if (selectedActions.none(target::supports)) return "Select at least one action."
        return null
    }

    private fun submit() {
        if (saving || validationProblem() != null) {
            refreshValidation()
            return
        }

        val targetId = ResourceLocation.tryParse(targetInput.text.trim()) ?: return
        val actions = ClaimPermissionsScreen.supportedActions(target)
            .filter(selectedActions::contains)
        val playerName = playerInput.text.trim().ifBlank { "all" }

        saving = true
        actionCheckboxes.values.forEach { it.active = false }
        submitButton.active = false
        validationLabel.text = if (actions.size == 1) "Saving permission…" else "Saving ${actions.size} permissions…"
        validationLabel.tint = Colors.UI_TEXT_DARK

        ClaimPermissionsNetwork.sendToServer(
            ClaimPermissionMutationPayload(
                ClaimPermissionMutationPayload.Operation.ADD,
                payload.claimOwner(),
                payload.subConfigIndex(),
                target.name,
                targetId.toString(),
                actions.joinToString(",") { it.name },
                "",
                "",
                playerName,
            ),
        )
    }

    private fun returnToParent() {
        Minecraft.getInstance().setScreen(parent)
    }

    private fun matchesRegistryId(id: String, query: String): Boolean {
        val path = id.substringAfter(':', id)
        return id.startsWith(query) || path.startsWith(query) || id.contains(query)
    }

    private fun matchRank(id: String, query: String): Int = when {
        id.startsWith(query) -> 0
        id.substringAfter(':', id).startsWith(query) -> 1
        else -> 2
    }

    companion object {
        private const val WINDOW_WIDTH = 430f
        private const val WINDOW_HEIGHT = 370f
        private const val WINDOW_HORIZONTAL_MARGIN = 32f
        private const val WINDOW_VERTICAL_MARGIN = 24f
        private const val MAX_TARGET_SUGGESTIONS = 3
        private const val MAX_PLAYER_SUGGESTIONS = 2
    }
}
