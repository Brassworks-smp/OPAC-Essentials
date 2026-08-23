package brassworks.opac_essentials.claims.permission.client.modal

import brassworks.opac_essentials.claims.permission.client.screen.ClaimPermissionsScreen

import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.util.EnumSet
import java.util.Locale

class AddClaimPermissionModal(
    private val payload: ClaimPermissionsSyncPayload,
    private val onAdd: (
        PermissionTargetRef,
        List<ClaimPermissionAction>,
        List<PermissionSubject>,
    ) -> Unit,
) {
    private val modal = BrassModal(
        title = "OPAC",
        width = MODAL_WIDTH,
        height = MODAL_HEIGHT,
        showClose = true,
        dismissOnEscape = true,
    )

    private var target = ClaimPermissionTarget.BLOCK
    private val selectedActions = EnumSet.of(ClaimPermissionAction.INTERACT)
    private val actionCheckboxes = mutableMapOf<ClaimPermissionAction, BrassCheckbox>()
    private var allPlayers = true

    private var targetInputHovered = false
    private var targetSuggestionsHovered = false
    private var playerInputHovered = false
    private var playerSuggestionsHovered = false
    private var currentTargetSuggestions: List<String> = emptyList()
    private var currentPlayerSuggestions: List<String> = emptyList()

    private lateinit var categoryOptions: UIContainer
    private lateinit var targetInput: BrassTextInput
    private lateinit var targetSuggestions: UIContainer
    private lateinit var actionOptions: UIContainer
    private lateinit var allPlayersCheckbox: BrassCheckbox
    private lateinit var playerInput: BrassTextInput
    private lateinit var playerSuggestions: UIContainer
    private lateinit var validationLabel: BrassLabel
    private lateinit var submitButton: BrassButton

    init {
        ClaimPermissionTarget.prepareRegisteredIds()
        buildUi()
        rebuildCategories()
        rebuildActions()
        refreshTargetSuggestions()
        refreshPlayerSuggestions()
        refreshValidation()
    }

    fun show(root: UIComponent): AddClaimPermissionModal = apply {
        modal.show(root)
    }

    private fun buildUi() {
        BrassLabel("/ claims / permissions / add", Colors.UI_TEXT_DARK).also {
            it.entranceEnabled = false
        }.constrain {
            x = 43.pixels()
            y = 6.pixels()
        } childOf modal.popup

        modal.body { host ->
            BrassLabel("Add permissions to ${payload.scopeName()}", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 1.pixels()
            } childOf host

            BrassLabel("CATEGORY", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 21.pixels()
            } childOf host

            categoryOptions = UIContainer().constrain {
                x = 2.pixels()
                y = 34.pixels()
                width = 100.percent() - 4.pixels()
                height = 20.pixels()
            } childOf host

            BrassLabel("TARGET ID", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 62.pixels()
            } childOf host

            targetInput = BrassTextInput(
                initial = "",
                placeholder = "minecraft:acacia_button",
            ) { value ->
                refreshTargetSuggestions(value)
                refreshValidation()
            }.constrain {
                x = 2.pixels()
                y = 75.pixels()
                width = 100.percent() - 4.pixels()
                height = 19.pixels()
            } childOf host

            targetSuggestions = UIContainer().constrain {
                x = 2.pixels()
                y = 98.pixels()
                width = 100.percent() - 4.pixels()
                height = 0.pixels()
            } childOf host

            BrassLabel("ACTIONS", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 109.pixels()
            } childOf host

            actionOptions = UIContainer().constrain {
                x = 2.pixels()
                y = 123.pixels()
                width = 100.percent() - 4.pixels()
                height = 22.pixels()
            } childOf host

            BrassLabel("PLAYERS", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 155.pixels()
            } childOf host

            allPlayersCheckbox = BrassCheckbox(initial = true) { checked ->
                allPlayers = checked
                playerInput.active = !checked
                if (checked) dismissPlayerSuggestions()
                refreshValidation()
            }.constrain {
                x = 2.pixels()
                y = 170.pixels()
                width = 14.pixels()
                height = 14.pixels()
            } childOf host

            BrassLabel("All players", Colors.UI_TEXT).constrain {
                x = 22.pixels()
                y = 172.pixels()
            } childOf host

            playerInput = BrassTextInput(
                initial = "",
                placeholder = "Alex, Steve",
            ) { value ->
                refreshPlayerSuggestions(value)
                refreshValidation()
            }.constrain {
                x = 112.pixels()
                y = 167.pixels()
                width = 100.percent() - 114.pixels()
                height = 19.pixels()
            } childOf host
            playerInput.active = false

            playerSuggestions = UIContainer().constrain {
                x = 112.pixels()
                y = 190.pixels()
                width = 100.percent() - 114.pixels()
                height = 0.pixels()
            } childOf host

            validationLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
                x = 2.pixels()
                y = 100.percent() - 14.pixels()
            } childOf host

            host.removeChild(targetSuggestions)
            host.addChild(targetSuggestions)
            host.removeChild(playerSuggestions)
            host.addChild(playerSuggestions)
        }

        submitButton = BrassButton("Add permission", BrassAccent.BRASS) { submit() }
        modal.footer(
            BrassButton("Cancel") { modal.dismiss() },
            submitButton,
        )

        installSuggestionDismissal()
    }

    private fun installSuggestionDismissal() {
        targetInput.onMouseEnter { targetInputHovered = true }
        targetInput.onMouseLeave { targetInputHovered = false }
        targetSuggestions.onMouseEnter { targetSuggestionsHovered = true }
        targetSuggestions.onMouseLeave { targetSuggestionsHovered = false }
        playerInput.onMouseEnter { playerInputHovered = true }
        playerInput.onMouseLeave { playerInputHovered = false }
        playerSuggestions.onMouseEnter { playerSuggestionsHovered = true }
        playerSuggestions.onMouseLeave { playerSuggestionsHovered = false }

        targetInput.onFocusLost {
            if (!targetSuggestionsHovered) dismissTargetSuggestions()
        }
        playerInput.onFocusLost {
            if (!playerSuggestionsHovered) dismissPlayerSuggestions()
        }

        targetInput.onSubmit = {
            dismissTargetSuggestions()
            targetInput.loseFocus()
            refreshValidation()
        }
        playerInput.onSubmit = {
            dismissPlayerSuggestions()
            playerInput.loseFocus()
            refreshValidation()
        }

        modal.popup.onMouseClick {
            if (!targetInputHovered && !targetSuggestionsHovered) {
                dismissTargetSuggestions()
                targetInput.loseFocus()
            }
            if (!playerInputHovered && !playerSuggestionsHovered) {
                dismissPlayerSuggestions()
                playerInput.loseFocus()
            }
        }
    }

    private fun rebuildCategories() {
        categoryOptions.clearChildren()
        ClaimPermissionTarget.entries.forEachIndexed { index, option ->
            val selected = option == target
            BrassButton(
                label = ClaimPermissionsScreen.displayTargetName(option.name),
                accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT,
            ) {
                selectTarget(option)
            }.also {
                it.selectable = true
                it.selected = selected
            }.constrain {
                x = (index * 25).percent()
                width = 25.percent() - 3.pixels()
                height = 20.pixels()
            } childOf categoryOptions
        }
    }

    private fun selectTarget(option: ClaimPermissionTarget) {
        if (option == target) return
        target = option

        val supported = ClaimPermissionsScreen.supportedActions(target)
        selectedActions.retainAll(supported.toSet())
        if (selectedActions.isEmpty() && supported.isNotEmpty()) {
            selectedActions.add(supported.first())
        }

        val currentId = ResourceLocation.tryParse(targetInput.text.trim())
        if (currentId == null || !target.isRegistered(currentId)) {
            targetInput.value = ""
        }

        rebuildCategories()
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

            BrassLabel(ClaimPermissionsScreen.displayActionName(action), Colors.UI_TEXT).constrain {
                x = 20.pixels()
                y = CenterConstraint()
            } childOf row
        }
    }

    private fun refreshTargetSuggestions(value: String = targetInput.text) {
        targetSuggestions.clearChildren()
        val query = value.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) {
            currentTargetSuggestions = emptyList()
            targetSuggestions.constrain { height = 0.pixels() }
            return
        }

        currentTargetSuggestions = target.registeredIds()
            .map(ResourceLocation::toString)
            .filter { id -> matchesRegistryId(id, query) }
            .sorted(compareBy<String> { matchRank(it, query) }.thenBy { it })
            .limit(MAX_TARGET_SUGGESTIONS.toLong())
            .toList()

        targetSuggestions.constrain {
            height = (currentTargetSuggestions.size * 14).pixels()
        }

        currentTargetSuggestions.forEachIndexed { index, id ->
            BrassButton(id) {
                targetInput.value = id
                dismissTargetSuggestions()
                targetInput.loseFocus()
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
        if (allPlayers) {
            currentPlayerSuggestions = emptyList()
            playerSuggestions.constrain { height = 0.pixels() }
            return
        }
        val query = value.substringAfterLast(',').trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            currentPlayerSuggestions = emptyList()
            playerSuggestions.constrain { height = 0.pixels() }
            return
        }
        val selectedNames = value.substringBeforeLast(',', "")
            .split(',')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        currentPlayerSuggestions = Minecraft.getInstance().connection?.onlinePlayers
            ?.map { it.profile.name }
            ?.filter { it.lowercase(Locale.ROOT).startsWith(query) }
            ?.filter { it.lowercase(Locale.ROOT) !in selectedNames }
            ?.distinctBy { it.lowercase(Locale.ROOT) }
            ?.take(MAX_PLAYER_SUGGESTIONS)
            ?: emptyList()

        playerSuggestions.constrain {
            height = (currentPlayerSuggestions.size * 14).pixels()
        }

        currentPlayerSuggestions.forEachIndexed { index, name ->
            BrassButton(name) {
                val prefix = playerInput.text.substringBeforeLast(',', "").trim()
                playerInput.value = if (prefix.isEmpty()) name else "$prefix, $name"
                dismissPlayerSuggestions()
                refreshValidation()
            }.constrain {
                y = (index * 14).pixels()
                width = 100.percent()
                height = 13.pixels()
            } childOf playerSuggestions
        }
    }

    private fun dismissTargetSuggestions() {
        currentTargetSuggestions = emptyList()
        targetSuggestions.clearChildren()
        targetSuggestions.constrain { height = 0.pixels() }
    }

    private fun dismissPlayerSuggestions() {
        currentPlayerSuggestions = emptyList()
        playerSuggestions.clearChildren()
        playerSuggestions.constrain { height = 0.pixels() }
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

        val playerCount = if (allPlayers) 1 else playerNames().size
        val permissionCount = selectedActions.count { target.supports(it) } * playerCount
        validationLabel.text = "$permissionCount staged permission changes"
        validationLabel.tint = Colors.UI_TEXT_DARK
        submitButton.active = true
    }

    private fun validationProblem(): String? {
        val rawId = targetInput.text.trim()
        if (rawId.isBlank()) return "Choose or enter a target ID."
        val targetId = ResourceLocation.tryParse(rawId) ?: return "Invalid registry ID."
        if (!target.isRegistered(targetId)) return "That registry ID does not exist in this category."
        if (selectedActions.none(target::supports)) return "Select at least one action."
        if (!allPlayers && playerNames().isEmpty()) return "Choose All players or enter player names."
        return null
    }

    private fun playerNames(): List<String> = playerInput.text
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }

    private fun submit() {
        if (validationProblem() != null) {
            refreshValidation()
            return
        }

        val targetId = ResourceLocation.tryParse(targetInput.text.trim()) ?: return
        val actions = ClaimPermissionsScreen.supportedActions(target)
            .filter(selectedActions::contains)
        val subjects = if (allPlayers) {
            listOf(PermissionSubject.ALL)
        } else {
            playerNames().map(::permissionSubject)
        }
        onAdd(PermissionTargetRef(target.name, targetId.toString()), actions, subjects)
        modal.dismiss()
    }

    private fun matchesRegistryId(id: String, query: String): Boolean {
        val path = id.substringAfter(':', id)
        return id.startsWith(query) || path.startsWith(query) || id.contains(query)
    }

    private fun matchRank(id: String, query: String): Int = when {
        id.equals(query, ignoreCase = true) -> 0
        id.startsWith(query) -> 1
        id.substringAfter(':', id).startsWith(query) -> 2
        else -> 3
    }

    companion object {
        private const val MODAL_WIDTH = 430f
        private const val MODAL_HEIGHT = 310f
        private const val MAX_TARGET_SUGGESTIONS = 3
        private const val MAX_PLAYER_SUGGESTIONS = 3
    }
}
