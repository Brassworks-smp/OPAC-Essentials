package brassworks.opac_essentials.claims.permission.client.modal

import brassworks.opac_essentials.claims.permission.client.screen.ClaimPermissionsScreen

import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsBatchPayload
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction
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
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
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
    private val modal = BrassModal(
        title = "OPAC",
        width = 456f,
        height = 338f,
        showClose = true,
        dismissOnEscape = true,
    )
    private val targets = entries
        .map(PermissionDraftEntry::targetRef)
        .distinctBy(PermissionTargetRef::key)
        .sortedWith(compareBy<PermissionTargetRef> { it.target }.thenBy { it.targetId })
    private val selectedTargets = targets.toMutableSet()
    private val selectedActions = EnumSet.allOf(ClaimPermissionAction::class.java)
    private val targetCheckboxes = mutableMapOf<PermissionTargetRef, BrassCheckbox>()
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
        BrassLabel("/ claims / permissions / bulk edit", Colors.UI_TEXT_DARK).also {
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
                syncTargetChecks()
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
                height = 122.pixels()
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
                width = 100.percent() - 5.pixels()
                height = (targets.size * 21f).pixels()
            } childOf scroll

            targets.forEachIndexed { index, target ->
                val row = UIContainer().constrain {
                    y = (index * 21).pixels()
                    width = 100.percent()
                    height = 19.pixels()
                } childOf rows

                val checkbox = BrassCheckbox(initial = true) { checked ->
                    if (checked) selectedTargets.add(target) else selectedTargets.remove(target)
                    selectAllTargets.setSilently(
                        targets.isNotEmpty() && selectedTargets.size == targets.size,
                    )
                    refreshState()
                }.constrain {
                    x = 2.pixels()
                    y = CenterConstraint()
                    width = 13.pixels()
                    height = 13.pixels()
                } childOf row
                targetCheckboxes[target] = checkbox

                BrassLabel(targetName(target.targetId), Colors.UI_TEXT).constrain {
                    x = 21.pixels()
                    y = CenterConstraint()
                } childOf row

                BrassLabel(
                    ClaimPermissionsScreen.displayTargetName(target.target),
                    Colors.UI_TEXT_DARK,
                    scale = 0.76f,
                ).constrain {
                    x = 100.percent() - 78.pixels()
                    y = CenterConstraint()
                } childOf row
            }

            BrassLabel("ACTIONS", Colors.UI_TEXT_DARK).constrain {
                x = 2.pixels()
                y = 151.pixels()
            } childOf host

            val actions = UIContainer().constrain {
                x = 2.pixels()
                y = 164.pixels()
                width = 100.percent() - 4.pixels()
                height = 22.pixels()
            } childOf host

            ClaimPermissionAction.entries.forEachIndexed { index, action ->
                val row = UIContainer().constrain {
                    x = (index * 20).percent()
                    width = 20.percent()
                    height = 22.pixels()
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
                y = 195.pixels()
            } childOf host

            allPlayersCheckbox = BrassCheckbox(initial = false) { checked ->
                allPlayers = checked
                playersInput.active = !checked
                refreshState()
            }.constrain {
                x = 2.pixels()
                y = 209.pixels()
                width = 14.pixels()
                height = 14.pixels()
            } childOf host

            BrassLabel("All players", Colors.UI_TEXT).constrain {
                x = 22.pixels()
                y = 211.pixels()
            } childOf host

            playersInput = BrassTextInput(
                initial = "",
                placeholder = "Alex, Steve",
            ) {
                refreshState()
            }.constrain {
                x = 112.pixels()
                y = 206.pixels()
                width = 100.percent() - 114.pixels()
                height = 19.pixels()
            } childOf host

            selectionLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
                x = 2.pixels()
                y = 236.pixels()
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

    private fun syncTargetChecks() {
        targetCheckboxes.forEach { (target, checkbox) ->
            checkbox.setSilently(target in selectedTargets)
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
        selectionLabel.text = problem ?: "$validPairs permission changes selected"
        selectionLabel.tint = if (problem == null) Colors.UI_TEXT_DARK else Colors.WARN
        grantButton.active = problem == null
        removeButton.active = problem == null
    }

    private fun confirmRemoval() {
        val confirmation = BrassModal(
            title = "Remove access in bulk?",
            width = 316f,
            height = 126f,
            showClose = true,
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
        if (!grantButton.active) return
        onApply(selectedTargets, selectedActions, selectedPlayers(), enabled)
        modal.dismiss()
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
        showClose = true,
        dismissOnEscape = true,
    )
    private val availablePlayers = linkedMapOf<String, PermissionSubject>()
    private val selectedPlayers = linkedSetOf<String>()
    private val playerCheckboxes = mutableMapOf<String, BrassCheckbox>()
    private var allPlayers = false
    private lateinit var allPlayersCheckbox: BrassCheckbox
    private lateinit var playerRows: UIContainer
    private lateinit var playerInput: BrassTextInput
    private lateinit var validationLabel: BrassLabel
    private lateinit var applyButton: BrassButton

    init {
        currentEntries.map(PermissionDraftEntry::subject)
            .distinctBy(PermissionSubject::key)
            .forEach { subject ->
                if (subject.isAll) {
                    allPlayers = true
                } else {
                    availablePlayers[subject.key()] = subject
                    selectedPlayers.add(subject.key())
                }
            }
        Minecraft.getInstance().connection?.onlinePlayers?.forEach { player ->
            val subject = PermissionSubject(player.profile.id.toString(), player.profile.name)
            availablePlayers.putIfAbsent(subject.key(), subject)
        }
        buildUi()
        rebuildPlayerRows()
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
            BrassLabel(targetName(target.targetId), Colors.UI_TEXT).constrain {
                x = 2.pixels()
                y = 1.pixels()
            } childOf host

            BrassLabel(
                "Choose who receives this target's selected actions.",
                Colors.UI_TEXT_DARK,
                scale = 0.84f,
            ).constrain {
                x = 2.pixels()
                y = 15.pixels()
            } childOf host

            allPlayersCheckbox = BrassCheckbox(initial = allPlayers) { checked ->
                allPlayers = checked
                syncPlayerChecks()
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

            playerRows = UIContainer().constrain {
                width = 100.percent() - 5.pixels()
                height = (availablePlayers.size * 20f).pixels()
            } childOf scroll

            playerInput = BrassTextInput(
                initial = "",
                placeholder = "Add player name",
            ) {
                refreshState()
            }.constrain {
                x = 2.pixels()
                y = 196.pixels()
                width = 100.percent() - 78.pixels()
                height = 19.pixels()
            } childOf host
            playerInput.onSubmit = { addPlayer() }

            BrassButton("+ Add", BrassAccent.BRASS) { addPlayer() }.constrain {
                x = 100.percent() - 70.pixels()
                y = 196.pixels()
                width = 68.pixels()
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

    private fun rebuildPlayerRows() {
        playerRows.clearChildren()
        playerCheckboxes.clear()
        val sortedPlayers = availablePlayers.values.sortedBy { it.playerName.lowercase(Locale.ROOT) }
        playerRows.constrain { height = (sortedPlayers.size * 20f).pixels() }
        sortedPlayers.forEachIndexed { index, subject ->
            val checkbox = BrassCheckbox(initial = subject.key() in selectedPlayers) { checked ->
                if (checked) selectedPlayers.add(subject.key()) else selectedPlayers.remove(subject.key())
                refreshState()
            }.constrain {
                x = 2.pixels()
                y = (index * 20 + 2).pixels()
                width = 13.pixels()
                height = 13.pixels()
            } childOf playerRows
            checkbox.active = !allPlayers
            playerCheckboxes[subject.key()] = checkbox

            BrassLabel(subject.playerName, Colors.UI_TEXT).constrain {
                x = 21.pixels()
                y = (index * 20 + 3).pixels()
            } childOf playerRows
        }
    }

    private fun syncPlayerChecks() {
        playerCheckboxes.forEach { (key, checkbox) ->
            checkbox.setSilently(key in selectedPlayers)
            checkbox.active = !allPlayers
        }
    }

    private fun addPlayer() {
        val name = playerInput.text.trim()
        if (name.isEmpty()) return
        val subject = permissionSubject(name)
        availablePlayers[subject.key()] = subject
        selectedPlayers.add(subject.key())
        allPlayers = false
        allPlayersCheckbox.setSilently(false)
        playerInput.value = ""
        rebuildPlayerRows()
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
}

private fun targetName(id: String): String = id.substringAfter(':', id)
    .replace('_', ' ')
    .replaceFirstChar { it.titlecase(Locale.ROOT) }
