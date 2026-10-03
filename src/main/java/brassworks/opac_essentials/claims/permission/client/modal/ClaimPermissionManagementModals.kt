package brassworks.opac_essentials.claims.permission.client.modal

import brassworks.opac_essentials.claims.permission.client.screen.ClaimPermissionsScreen
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsBatchPayload
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.plus
import net.minecraft.client.Minecraft
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.util.EnumSet
import java.util.Locale

data class PermissionTargetRef(
    val target: String,
    val targetId: String,
) {
    fun key(): String = "$target|$targetId"

    fun networkEntry(): ClaimPermissionsBatchPayload.TargetEntry =
        ClaimPermissionsBatchPayload.TargetEntry(
            target,
            targetId,
            "",
            "",
            "",
            false,
        )
}

data class PermissionSubject(
    val playerId: String,
    val playerName: String,
) {
    val isAll: Boolean
        get() = playerId.isBlank() &&
            (playerName.isBlank() || playerName.equals("all", true) || playerName.equals("All players", true))

    fun key(): String = when {
        isAll -> "all"
        playerId.isNotBlank() -> playerId.lowercase(Locale.ROOT)
        else -> playerName.lowercase(Locale.ROOT)
    }

    fun displayName(): String = if (isAll) "All players" else playerName

    fun matches(other: PermissionSubject): Boolean {
        if (isAll || other.isAll) return isAll && other.isAll
        val sameId = playerId.isNotBlank() &&
            other.playerId.isNotBlank() &&
            playerId.equals(other.playerId, true)
        val sameName = playerName.isNotBlank() &&
            other.playerName.isNotBlank() &&
            playerName.equals(other.playerName, true)
        return sameId || sameName
    }

    companion object {
        val ALL = PermissionSubject("", "All players")
    }
}

data class PermissionDraftEntry(
    val target: String,
    val targetId: String,
    val action: String,
    val subject: PermissionSubject,
) {
    fun targetRef(): PermissionTargetRef = PermissionTargetRef(target, targetId)

    fun key(): String = "${targetRef().key()}|$action|${subject.key()}"

    fun matches(other: PermissionDraftEntry): Boolean =
        targetRef() == other.targetRef() &&
            action == other.action &&
            subject.matches(other.subject)

    fun covers(other: PermissionDraftEntry): Boolean =
        targetRef() == other.targetRef() &&
            action == other.action &&
            (subject.isAll || subject.matches(other.subject))

    fun networkEntry(enabled: Boolean): ClaimPermissionsBatchPayload.TargetEntry =
        ClaimPermissionsBatchPayload.TargetEntry(
            target,
            targetId,
            subject.playerId,
            if (subject.isAll) "" else subject.playerName,
            action,
            enabled,
        )

    companion object {
        fun fromSync(entry: ClaimPermissionsSyncPayload.Entry): PermissionDraftEntry =
            PermissionDraftEntry(
                entry.target(),
                entry.targetId(),
                entry.action(),
                if (entry.playerId().isBlank()) {
                    PermissionSubject.ALL
                } else {
                    PermissionSubject(entry.playerId(), entry.playerName())
                },
            )
    }
}

fun permissionSubject(name: String): PermissionSubject {
    val trimmed = name.trim()
    if (trimmed.equals("all", true) || trimmed.equals("All players", true)) {
        return PermissionSubject.ALL
    }
    val online = Minecraft.getInstance().connection?.onlinePlayers
        ?.firstOrNull { it.profile.name.equals(trimmed, true) }
    return if (online == null) {
        PermissionSubject("", trimmed)
    } else {
        PermissionSubject(online.profile.id.toString(), online.profile.name)
    }
}

class BulkPermissionModal(
    private val payload: ClaimPermissionsSyncPayload,
    entries: Collection<PermissionDraftEntry>,
    private val onApply: (
        Set<PermissionTargetRef>,
        Set<ClaimPermissionAction>,
        List<PermissionSubject>,
        Boolean,
    ) -> Unit,
) {
    private val existingEntries = entries.toList()
    private val modal = BrassModal(
        title = "OPAC",
        width = 432f,
        height = 322f,
        showClose = false,
        dismissOnEscape = true,
    )
    private val targets = entries
        .map(PermissionDraftEntry::targetRef)
        .distinctBy(PermissionTargetRef::key)
        .sortedWith(compareBy<PermissionTargetRef> { it.target }.thenBy { it.targetId })
    private val selectedTargets = targets.toMutableSet()
    private val selectedActions = EnumSet.allOf(ClaimPermissionAction::class.java)
    private val targetCards = mutableMapOf<PermissionTargetRef, BrassButton>()
    private lateinit var selectAllTargets: BrassCheckbox
    private lateinit var allPlayersCheckbox: BrassCheckbox
    private lateinit var playersInput: BrassTextInput
    private lateinit var selectionLabel: BrassLabel
    private lateinit var grantButton: BrassButton
    private lateinit var removeButton: BrassButton
    private var allPlayers = false

    init {
        buildUi()
        refreshState()
    }

    fun show(root: UIComponent): BulkPermissionModal = apply {
        modal.show(root)
    }

    private fun buildUi() {
        BrassLabel("/  claims  /  permissions  /  bulk edit", Colors.UI_TEXT_DARK).also {
            it.entranceEnabled = false
        }.constrain {
            x = 43.pixels()
            y = 6.pixels()
        } childOf modal.popup

        modal.body { host ->
            BrassLabel("Apply access to several targets, actions and players.", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 1.pixels()
            } childOf host

            selectAllTargets = BrassCheckbox(initial = true) { checked ->
                selectedTargets.clear()
                if (checked) selectedTargets.addAll(targets)
                syncTargetCards()
                refreshState()
            }.constrain {
                x = 100.percent() - 78.pixels()
                y = 1.pixels()
                width = 13.pixels()
                height = 13.pixels()
            } childOf host

            BrassLabel("Select all", Colors.UI_TEXT_DARK).constrain {
                x = 100.percent() - 60.pixels()
                y = 3.5.pixels()
            } childOf host

            val targetPanel = BrassPanel(
                title = "TARGETS",
                layout = BrassPanel.Layout.FREE,
            ).constrain {
                x = 2.pixels()
                y = 20.pixels()
                width = 100.percent() - 4.pixels()
                height = 100.pixels()
            } childOf host

            val scroll = ScrollComponent(
                emptyString = "No permission targets yet",
                horizontalScrollEnabled = false,
                verticalScrollEnabled = true,
            ).constrain {
                width = 100.percent()
                height = 100.percent()
            } childOf targetPanel.content

            val rows = UIContainer().constrain {
                width = 100.percent()
                height = maxOf(6f, targets.size * 25f + 2f).pixels()
            } childOf scroll

            targets.forEachIndexed { index, target ->
                val card = BrassButton(
                    label = "",
                    accent = BrassAccent.BRASS,
                ) {
                    if (target in selectedTargets) {
                        selectedTargets.remove(target)
                    } else {
                        selectedTargets.add(target)
                    }
                    selectAllTargets.setSilently(
                        targets.isNotEmpty() && selectedTargets.size == targets.size,
                    )
                    syncTargetCards()
                    refreshState()
                }.also {
                    it.selectable = true
                    it.selected = true
                    it.chrome = BrassChrome.FLAT
                    it.centered = false
                    it.entranceEnabled = false
                    it.clickable = false
                }.constrain {
                    x = 3.pixels()
                    y = (3f + index * 25f).pixels()
                    width = 100.percent() - 6.pixels()
                    height = 21.pixels()
                } childOf rows
                targetCards[target] = card

                val content = UIContainer().constrain {
                    x = 6.pixels()
                    y = 3.pixels()
                    width = 100.percent() - 12.pixels()
                    height = 100.percent() - 6.pixels()
                } childOf card

                BrassLabel(
                    permissionTargetName(target),
                    Colors.UI_TEXT,
                ).constrain {
                    x = 0.pixels()
                    y = CenterConstraint() + 1.pixels()
                } childOf content

                BrassLabel(
                    ClaimPermissionsScreen.displayTargetName(target.target),
                    Colors.UI_TEXT_DARK,
                    scale = 0.76f,
                ).constrain {
                    x = 0.pixels(true)
                    y = CenterConstraint() + 1.pixels()
                } childOf content
            }

            BrassLabel("ACTIONS", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 129.pixels()
            } childOf host

            val actions = UIContainer().constrain {
                x = 2.pixels()
                y = 142.pixels()
                width = 100.percent() - 4.pixels()
                height = 40.pixels()
            } childOf host

            val actionWidth = 100f / ACTION_COLUMNS
            ClaimPermissionAction.entries.forEachIndexed { index, action ->
                val row = UIContainer().constrain {
                    x = (index % ACTION_COLUMNS * actionWidth).percent()
                    y = (index / ACTION_COLUMNS * 20).pixels()
                    width = actionWidth.percent()
                    height = 20.pixels()
                } childOf actions

                BrassCheckbox(initial = true) { checked ->
                    if (checked) selectedActions.add(action) else selectedActions.remove(action)
                    refreshState()
                }.constrain {
                    x = 1.pixels()
                    y = CenterConstraint()
                    width = 13.pixels()
                    height = 13.pixels()
                } childOf row

                BrassLabel(
                    ClaimPermissionsScreen.displayActionName(action),
                    Colors.UI_TEXT
                ).constrain {
                    x = 18.pixels()
                    y = CenterConstraint() + 1.pixels()
                } childOf row
            }

            BrassLabel("PLAYERS", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 191.pixels()
            } childOf host

            allPlayersCheckbox = BrassCheckbox(initial = false) { checked ->
                allPlayers = checked
                playersInput.active = !checked
                refreshState()
            }.constrain {
                x = 2.pixels()
                y = 207.pixels()
                width = 14.pixels()
                height = 14.pixels()
            } childOf host

            BrassLabel("All players", Colors.UI_TEXT).constrain {
                x = 22.pixels()
                y = 209.pixels()
            } childOf host

            playersInput = BrassTextInput(
                initial = "",
                placeholder = "Alex, Steve",
            ) {
                refreshState()
            }.constrain {
                x = 112.pixels()
                y = 204.pixels()
                width = 100.percent() - 114.pixels()
                height = 19.pixels()
            } childOf host

            selectionLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
                x = 2.pixels()
                y = 234.pixels()
            } childOf host
        }

        removeButton = BrassButton("Remove access", BrassAccent.DANGER) {
            confirmRemoval()
        }
        grantButton = BrassButton("Grant access", BrassAccent.BRASS) {
            submit(true)
        }
        modal.footer(
            BrassButton("Cancel") { modal.dismiss() },
            removeButton,
            grantButton,
        )
    }

    private fun syncTargetCards() {
        targetCards.forEach { (target, card) ->
            val selected = target in selectedTargets
            card.selected = selected
            card.accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    private fun selectedPlayers(): List<PermissionSubject> = if (allPlayers) {
        listOf(PermissionSubject.ALL)
    } else {
        playersInput.text
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .map(::permissionSubject)
    }

    private fun refreshState() {
        if (!::selectionLabel.isInitialized) return
        val players = selectedPlayers()
        val validPairs = selectedTargets.sumOf { target ->
            selectedActions.count { action ->
                ClaimPermissionsScreen.parseTarget(target.target).supports(action)
            }
        } * players.size
        val problem = when {
            selectedTargets.isEmpty() -> "Select at least one target."
            selectedActions.isEmpty() -> "Select at least one action."
            players.isEmpty() -> "Choose All players or enter player names."
            validPairs == 0 -> "No valid permission combinations selected."
            else -> null
        }
        val newGrantCount = if (problem == null) grantCount(players) else 0
        val matchingRemovalCount = if (problem == null) removalCount(players) else 0
        selectionLabel.text = problem
            ?: "$newGrantCount new grants · $matchingRemovalCount matching removals"
        selectionLabel.tint = if (problem == null) Colors.UI_TEXT_DARK else Colors.WARN
        grantButton.active = problem == null && newGrantCount > 0
        removeButton.active = problem == null && matchingRemovalCount > 0
    }

    private fun grantCount(players: List<PermissionSubject>): Int =
        selectedTargets.sumOf { target ->
            selectedActions
                .filter(ClaimPermissionsScreen.parseTarget(target.target)::supports)
                .sumOf { action ->
                    players.count { subject ->
                        val entry = PermissionDraftEntry(
                            target.target,
                            target.targetId,
                            action.name,
                            subject,
                        )
                        existingEntries.none { it.covers(entry) }
                    }
                }
        }

    private fun removalCount(players: List<PermissionSubject>): Int =
        existingEntries.count { entry ->
            entry.targetRef() in selectedTargets &&
                selectedActions.any { it.name == entry.action } &&
                players.any { it.isAll || it.matches(entry.subject) }
        }

    private fun confirmRemoval() {
        val confirmation = BrassModal(
            title = "Remove access in bulk?",
            width = 316f,
            height = 126f,
            showClose = false,
            dismissOnEscape = true,
        )
        confirmation.body { host ->
            BrassLabel("Matching player access will be removed from every selection.", Colors.UI_TEXT).constrain {
                x = 3.pixels()
                y = 14.pixels()
            } childOf host
            BrassLabel("The change remains staged until Done is pressed.", Colors.WARN, scale = 0.86f).constrain {
                x = 3.pixels()
                y = 36.pixels()
            } childOf host
        }
        confirmation.footer(
            BrassButton("Cancel") { confirmation.dismiss() },
            BrassButton("Remove access", BrassAccent.DANGER) {
                confirmation.dismiss()
                submit(false)
            },
        ).show(modal.popup)
    }

    private fun submit(enabled: Boolean) {
        val actionButton = if (enabled) grantButton else removeButton
        if (!actionButton.active) return
        onApply(selectedTargets, selectedActions, selectedPlayers(), enabled)
        modal.dismiss()
    }

    companion object {
        private const val ACTION_COLUMNS = 4
    }
}

class PermissionPlayersModal(
    private val target: PermissionTargetRef,
    currentEntries: Collection<PermissionDraftEntry>,
    private val onApply: (List<PermissionSubject>) -> Unit,
) {
    private val modal = BrassModal(
        title = "OPAC",
        width = 366f,
        height = 310f,
        showClose = false,
        dismissOnEscape = true,
    )
    private val availablePlayers = linkedMapOf<String, PermissionSubject>()
    private val selectedPlayers = linkedSetOf<String>()
    private val playerHeads = mutableMapOf<String, BrassPlayerHead>()
    private var allPlayers = false
    private lateinit var allPlayersCheckbox: BrassCheckbox
    private lateinit var playerGrid: UIContainer
    private lateinit var playerInput: BrassTextInput
    private lateinit var validationLabel: BrassLabel
    private lateinit var applyButton: BrassButton

    init {
        currentEntries.map(PermissionDraftEntry::subject).forEach { subject ->
            if (availablePlayers.values.any { it.matches(subject) }) {
                return@forEach
            }
            if (subject.isAll) {
                allPlayers = true
            } else {
                availablePlayers[subject.key()] = subject
                selectedPlayers.add(subject.key())
            }
        }
        Minecraft.getInstance().connection?.onlinePlayers?.forEach { player ->
            val subject = PermissionSubject(player.profile.id.toString(), player.profile.name)
            if (availablePlayers.values.none { it.matches(subject) }) {
                availablePlayers[subject.key()] = subject
            }
        }
        buildUi()
        rebuildPlayerGrid()
        refreshState()
    }

    fun show(root: UIComponent): PermissionPlayersModal = apply {
        modal.show(root)
    }

    private fun buildUi() {
        BrassLabel("/ claims / permissions / players", Colors.UI_TEXT_DARK).also {
            it.entranceEnabled = false
        }.constrain {
            x = 43.pixels()
            y = 6.pixels()
        } childOf modal.popup

        modal.body { host ->
            BrassLabel(permissionTargetName(target), Colors.UI_TEXT).constrain {
                x = 2.pixels()
                y = 1.pixels()
            } childOf host

            BrassLabel(
                "Selected heads keep access. Click a head to remove it.",
                Colors.UI_TEXT_DARK,
                scale = 0.84f,
            ).constrain {
                x = 2.pixels()
                y = 15.pixels()
            } childOf host

            allPlayersCheckbox = BrassCheckbox(initial = allPlayers) { checked ->
                allPlayers = checked
                syncPlayerHeads()
                refreshState()
            }.constrain {
                x = 2.pixels()
                y = 34.pixels()
                width = 14.pixels()
                height = 14.pixels()
            } childOf host

            BrassLabel("All players", Colors.UI_TEXT).constrain {
                x = 22.pixels()
                y = 36.pixels()
            } childOf host

            val panel = BrassPanel(
                title = "SPECIFIC PLAYERS",
                layout = BrassPanel.Layout.FREE,
            ).constrain {
                x = 2.pixels()
                y = 56.pixels()
                width = 100.percent() - 4.pixels()
                height = 130.pixels()
            } childOf host

            val scroll = ScrollComponent(
                emptyString = "No known players",
                horizontalScrollEnabled = false,
                verticalScrollEnabled = true,
            ).constrain {
                width = 100.percent()
                height = 100.percent()
            } childOf panel.content

            playerGrid = UIContainer().constrain {
                width = 100.percent() - 5.pixels()
                height = playerGridHeight(availablePlayers.size).pixels()
            } childOf scroll

            playerInput = BrassTextInput(
                initial = "",
                placeholder = "Add player name",
            ) {
                refreshState()
            }.constrain {
                x = 2.pixels()
                y = 196.pixels()
                width = 100.percent() - 140.pixels()
                height = 19.pixels()
            } childOf host
            playerInput.onSubmit = { addPlayer() }

            BrassButton("+ Add", BrassAccent.BRASS) { addPlayer() }.constrain {
                x = 100.percent() - 134.pixels()
                y = 196.pixels()
                width = 64.pixels()
                height = 19.pixels()
            } childOf host

            BrassButton("- Remove", BrassAccent.DANGER) { removePlayer() }.constrain {
                x = 100.percent() - 66.pixels()
                y = 196.pixels()
                width = 64.pixels()
                height = 19.pixels()
            } childOf host

            validationLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
                x = 2.pixels()
                y = 225.pixels()
            } childOf host
        }

        applyButton = BrassButton("Apply players", BrassAccent.BRASS) { applySelection() }
        modal.footer(
            BrassButton("Cancel") { modal.dismiss() },
            applyButton,
        )
    }

    private fun rebuildPlayerGrid() {
        playerGrid.clearChildren()
        playerHeads.clear()
        val sortedPlayers = availablePlayers.values
            .sortedBy { it.playerName.lowercase(Locale.ROOT) }
        playerGrid.constrain { height = playerGridHeight(sortedPlayers.size).pixels() }
        sortedPlayers.forEachIndexed { index, subject ->
            val key = subject.key()
            val head = BrassPlayerHead(
                subject.playerName,
                PLAYER_HEAD_SIZE,
                tooltip = true,
            ) {
                if (key in selectedPlayers) {
                    selectedPlayers.remove(key)
                    selectAllPlayersFallback()
                } else {
                    selectedPlayers.add(key)
                }
                syncPlayerHeads()
                refreshState()
            }.constrain {
                x = (3f + index % PLAYER_COLUMNS * PLAYER_CELL_SIZE).pixels()
                y = (3f + index / PLAYER_COLUMNS * PLAYER_CELL_SIZE).pixels()
            } childOf playerGrid
            head.selectable = true
            playerHeads[key] = head
        }
        syncPlayerHeads()
    }

    private fun syncPlayerHeads() {
        playerHeads.forEach { (key, head) ->
            val selected = key in selectedPlayers
            head.selected = selected
            head.accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT
            head.active = !allPlayers
        }
    }

    private fun playerGridHeight(playerCount: Int): Float {
        val rows = (playerCount + PLAYER_COLUMNS - 1) / PLAYER_COLUMNS
        return maxOf(1f, rows * PLAYER_CELL_SIZE + 6f)
    }

    private fun addPlayer() {
        val name = playerInput.text.trim()
        if (name.isEmpty()) return
        val subject = permissionSubject(name)
        val selectedSubject = availablePlayers.values
            .firstOrNull { it.matches(subject) }
            ?: subject.also { availablePlayers[it.key()] = it }
        selectedPlayers.add(selectedSubject.key())
        allPlayers = false
        allPlayersCheckbox.setSilently(false)
        playerInput.value = ""
        rebuildPlayerGrid()
        refreshState()
    }

    private fun removePlayer() {
        val name = playerInput.text.trim()
        if (name.isEmpty() || allPlayers) return
        val subject = permissionSubject(name)
        val selectedSubject = availablePlayers.values
            .firstOrNull { it.matches(subject) }
            ?: return
        if (!selectedPlayers.remove(selectedSubject.key())) return
        selectAllPlayersFallback()
        playerInput.value = ""
        syncPlayerHeads()
        refreshState()
    }

    private fun refreshState() {
        if (!::validationLabel.isInitialized) return
        val count = if (allPlayers) 1 else selectedPlayers.size
        validationLabel.text = when {
            allPlayers -> "All players will receive the selected actions."
            count == 0 -> "Select or add at least one player."
            count == 1 -> "1 player selected."
            else -> "$count players selected."
        }
        validationLabel.tint = if (count == 0) Colors.WARN else Colors.UI_TEXT_DARK
        applyButton.active = count > 0
    }

    private fun applySelection() {
        if (!applyButton.active) return
        val subjects = if (allPlayers) {
            listOf(PermissionSubject.ALL)
        } else {
            selectedPlayers.mapNotNull(availablePlayers::get)
        }
        onApply(subjects)
        modal.dismiss()
    }

    private fun selectAllPlayersFallback() {
        if (selectedPlayers.isNotEmpty()) return
        allPlayers = true
        allPlayersCheckbox.setSilently(true)
    }

    companion object {
        private const val PLAYER_COLUMNS = 8
        private const val PLAYER_CELL_SIZE = 40f
        private const val PLAYER_HEAD_SIZE = 32f
    }
}

private fun permissionTargetName(target: PermissionTargetRef): String =
    if (ClaimPermissionsScreen.parseTarget(target.target) == ClaimPermissionTarget.TRAIN) {
        "Trains"
    } else {
        targetName(target.targetId)
    }

private fun targetName(id: String): String = id.substringAfter(':', id)
    .replace('_', ' ')
    .replaceFirstChar { it.titlecase(Locale.ROOT) }
