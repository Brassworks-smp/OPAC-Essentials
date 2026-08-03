package brassworks.opac_essentials.claims.permission.client.screen

import brassworks.opac_essentials.claims.permission.client.modal.*

import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsBatchPayload
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.BrassScreen
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassDismissable
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassSearchField
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.media.BrassBlockPreview
import net.swzo.brass.ui.kit.media.BrassEntity
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassItem
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassWindow
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextField
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.LinkedHashMap
import java.util.Locale

class ClaimPermissionsScreen(
    initialPayload: ClaimPermissionsSyncPayload,
) : BrassScreen(backdropColor = Color(8, 9, 10, 148)) {
    private var payload = initialPayload
    private var serverEntries = entriesFromPayload(initialPayload)
    private var draftEntries = LinkedHashMap(serverEntries)
    private var query = ""
    private var selectedKey: TargetKey? = null
    private var visibleTargets: List<TargetGroup> = emptyList()
    private var savingChanges = false
    private var closeAfterSave = false

    private val permissionCheckboxes = mutableMapOf<PermissionSlot, BrassCheckbox>()
    private val targetTiles = mutableMapOf<TargetKey, BrassButton>()

    private lateinit var targetContent: UIContainer
    private lateinit var detailHost: UIContainer
    private lateinit var detailContent: DetailTransitionLayer
    private lateinit var statusLabel: BrassLabel
    private lateinit var doneButton: BrassButton
    private lateinit var addButton: BrassButton
    private lateinit var bulkButton: BrassButton

    init {
        BrassThemes.apply("dark", "#8B5CF6")
        buildUi()
        refreshTargets(selectFirst = true)
        refreshStatus()
    }

    private fun buildUi() {
        val frame = BrassWindow(
            title = "OPAC",
            subtitle = "claims  /  permissions",
            onClose = ::requestClose,
            controls = false,
            minW = 320f,
            minH = 250f,
        ).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            width = basicWidthConstraint { component ->
                minOf(WINDOW_WIDTH, (component.parent.getWidth() - 32f).coerceAtLeast(300f))
            }
            height = basicHeightConstraint { component ->
                minOf(WINDOW_HEIGHT, (component.parent.getHeight() - 24f).coerceAtLeast(230f))
            }
        } childOf background

        BrassSquareButton(BrassIcons.NONE, BrassAccent.DANGER) { requestClose() }.also {
            it.entranceEnabled = false
        }.constrain {
            x = 7.pixels(true)
            y = 5.pixels()
            width = 18.pixels()
            height = 10.pixels()
        } childOf frame

        val search = BrassSearchField("Search targets or players...") { value ->
            query = value.trim().lowercase(Locale.ROOT)
            refreshTargets(selectFirst = false)
        }.constrain {
            x = 12.pixels()
            y = 10.pixels()
            width = 100.percent() - 158.pixels()
            height = 18.pixels()
        } childOf frame.content
        search.onSearchNow = { value ->
            query = value.trim().lowercase(Locale.ROOT)
            refreshTargets(selectFirst = false)
        }

        addButton = BrassButton("+ Add", BrassAccent.BRASS) {
            AddClaimPermissionModal(payload, ::stageAddedPermission).show(background)
        }.constrain {
            x = 100.percent() - 146.pixels()
            y = 10.pixels()
            width = 64.pixels()
            height = 18.pixels()
        } childOf frame.content

        bulkButton = BrassButton("Bulk edit") {
            BulkPermissionModal(payload, draftEntries.values, ::applyBulk).show(background)
        }.constrain {
            x = 100.percent() - 76.pixels()
            y = 10.pixels()
            width = 64.pixels()
            height = 18.pixels()
        } childOf frame.content

        val targetPanel = BrassPanel(
            title = "PERMISSION TARGETS",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 12.pixels()
            y = 38.pixels()
            width = 100.percent() - 24.pixels()
            height = 98.pixels()
        } childOf frame.content

        val targetScroll = ScrollComponent(
            emptyString = "",
            horizontalScrollEnabled = true,
            verticalScrollEnabled = false,
        ).constrain {
            width = 100.percent()
            height = 100.percent()
        } childOf targetPanel.content

        targetContent = UIContainer().constrain {
            width = basicWidthConstraint { component ->
                maxOf(component.parent.getWidth(), visibleTargets.size * TARGET_STEP)
            }
            height = 100.percent()
        } childOf targetScroll

        val detailPanel = BrassPanel(
            title = "SELECTED TARGET",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 12.pixels()
            y = 144.pixels()
            width = 100.percent() - 24.pixels()
            height = 100.percent() - 180.pixels()
        } childOf frame.content
        detailHost = detailPanel.content
        detailContent = DetailTransitionLayer(0f).constrain {
            width = 100.percent()
            height = 100.percent()
        } childOf detailHost

        statusLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 12.pixels()
            y = 100.percent() - 23.pixels()
        } childOf frame.content

        doneButton = BrassButton("Done", BrassAccent.BRASS) { saveChanges(closeAfter = true) }.constrain {
            x = 100.percent() - 82.pixels()
            y = 100.percent() - 26.pixels()
            width = 70.pixels()
            height = 18.pixels()
        } childOf frame.content
    }

    fun applySync(nextPayload: ClaimPermissionsSyncPayload) {
        payload = nextPayload
        if (savingChanges && nextPayload.error()) {
            savingChanges = false
            closeAfterSave = false
            setInteractive(true)
            refreshStatus()
            return
        }

        if (savingChanges || !isDirty()) {
            serverEntries = entriesFromPayload(nextPayload)
            draftEntries = LinkedHashMap(serverEntries)
            savingChanges = false
            val shouldClose = closeAfterSave
            closeAfterSave = false
            setInteractive(true)
            refreshTargets(selectFirst = selectedKey == null)
            refreshStatus()
            if (shouldClose) onClose()
            return
        }

        refreshStatus()
    }

    override fun onKeyPressed(
        keyCode: Int,
        typedChar: Char,
        modifiers: UKeyboard.Modifiers?,
    ) {
        if (
            keyCode == GLFW.GLFW_KEY_ESCAPE &&
            isDirty() &&
            !hasDescendant(window) { it is BrassDismissable } &&
            !hasDescendant(window) { it is BrassTextField && it.focused }
        ) {
            requestClose()
            return
        }
        super.onKeyPressed(keyCode, typedChar, modifiers)
    }

    private fun refreshTargets(selectFirst: Boolean) {
        val previousSelection = selectedKey
        updateVisibleTargets(selectFirst)
        rebuildTargetStrip()
        rebuildDetails(if (previousSelection != null && previousSelection != selectedKey) 10f else 0f)
    }

    private fun updateVisibleTargets(selectFirst: Boolean) {
        visibleTargets = groupedTargets().filter(::matchesQuery)
        if (selectFirst && selectedKey == null) {
            selectedKey = visibleTargets.firstOrNull()?.key
        } else if (visibleTargets.none { it.key == selectedKey }) {
            selectedKey = visibleTargets.firstOrNull()?.key
        }
    }

    private fun groupedTargets(): List<TargetGroup> = draftEntries.values
        .groupBy { TargetKey(it.target, it.targetId) }
        .map { (key, entries) -> TargetGroup(key, entries) }
        .sortedWith(compareBy<TargetGroup> { it.key.target }.thenBy { it.key.targetId })

    private fun matchesQuery(group: TargetGroup): Boolean {
        if (query.isEmpty()) return true
        return group.key.targetId.lowercase(Locale.ROOT).contains(query) ||
                group.key.target.lowercase(Locale.ROOT).contains(query) ||
                group.entries.any {
                    it.action.lowercase(Locale.ROOT).contains(query) ||
                            it.subject.displayName().lowercase(Locale.ROOT).contains(query)
                }
    }

    private fun rebuildTargetStrip() {
        targetContent.clearChildren()
        targetTiles.clear()
        if (visibleTargets.isEmpty()) {
            BrassEmptyState(
                BrassIcons.SEARCH,
                "No permission targets",
                if (query.isEmpty()) "Add a target to get started" else "Try a shorter search term",
            ).constrain {
                width = 100.percent()
                height = 100.percent()
            } childOf targetContent
            return
        }
        visibleTargets.forEachIndexed(::addTargetTile)
    }

    private fun addTargetTile(index: Int, group: TargetGroup) {
        val selected = group.key == selectedKey
        val tile = BrassButton(
            label = "",
            accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT,
        ) {
            if (selectedKey != group.key) {
                val oldIndex = visibleTargets.indexOfFirst { it.key == selectedKey }
                val newIndex = visibleTargets.indexOfFirst { it.key == group.key }
                selectedKey = group.key
                syncTargetTileSelection()
                rebuildDetails(if (newIndex >= oldIndex) 10f else -10f)
            }
        }.also {
            it.selectable = true
            it.selected = selected
        }.constrain {
            x = (index * TARGET_STEP).pixels()
            y = 1.pixels()
            width = TARGET_WIDTH.pixels()
            height = 66.pixels()
        } childOf targetContent
        targetTiles[group.key] = tile

        when (parseTarget(group.key.target)) {
            ClaimPermissionTarget.BLOCK,
            ClaimPermissionTarget.BLOCK_ENTITY -> BrassBlockPreview(group.key.targetId, tooltip = true).constrain {
                x = CenterConstraint()
                y = 3.pixels()
                width = 27.pixels()
                height = 27.pixels()
            } childOf tile

            ClaimPermissionTarget.ENTITY -> BrassEntity(group.key.targetId, tooltip = true).constrain {
                x = CenterConstraint()
                y = 2.pixels()
                width = 29.pixels()
                height = 29.pixels()
            } childOf tile

            ClaimPermissionTarget.THROWABLE -> BrassItem(group.key.targetId, tooltip = true).constrain {
                x = CenterConstraint()
                y = 4.pixels()
                width = 25.pixels()
                height = 25.pixels()
            } childOf tile
        }

        BrassLabel(shortTargetName(group.key.targetId), Colors.UI_TEXT).constrain {
            x = CenterConstraint()
            y = 38.pixels()
        } childOf tile

        BrassLabel(tileSummary(group), Colors.UI_TEXT_DARK, scale = 0.7f).constrain {
            x = CenterConstraint()
            y = 52.pixels()
        } childOf tile
        disableEntrances(tile)
    }

    private fun syncTargetTileSelection() {
        targetTiles.forEach { (key, tile) ->
            val selected = key == selectedKey
            tile.selected = selected
            tile.accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    private fun disableEntrances(component: UIComponent) {
        if (component is BrassWidget) component.entranceEnabled = false
        component.children.forEach(::disableEntrances)
    }

    private fun rebuildDetails(slideFrom: Float = 0f) {
        val previous = detailContent
        detailContent = DetailTransitionLayer(slideFrom).constrain {
            width = 100.percent()
            height = 100.percent()
        } childOf detailHost
        detailHost.removeChild(previous)
        permissionCheckboxes.clear()

        val key = selectedKey
        val group = visibleTargets.firstOrNull { it.key == key }
        if (key == null || group == null) {
            BrassEmptyState(
                BrassIcons.INFO,
                "Select a target",
                "Its actions and players will appear here",
            ).constrain {
                width = 100.percent()
                height = 100.percent()
            } childOf detailContent
            disableEntrances(detailContent)
            return
        }

        addLargePreview(key)
        BrassLabel(key.targetId, Colors.UI_TEXT_HOVER).constrain {
            x = 48.pixels()
            y = 2.pixels()
        } childOf detailContent

        BrassLabel(
            "${displayTargetName(key.target)}  /  ${subjectSummary(group)}",
            Colors.UI_TEXT_DARK,
        ).constrain {
            x = 48.pixels()
            y = 16.pixels()
        } childOf detailContent

        BrassButton("Edit players") {
            PermissionPlayersModal(key.toRef(), group.entries) { subjects ->
                applyPlayers(key, subjects)
            }.show(background)
        }.constrain {
            x = 100.percent() - 154.pixels()
            y = 3.pixels()
            width = 84.pixels()
            height = 18.pixels()
        } childOf detailContent

        BrassButton("Delete", BrassAccent.DANGER) {
            showTargetDeletionConfirmation(key)
        }.constrain {
            x = 100.percent() - 66.pixels()
            y = 3.pixels()
            width = 64.pixels()
            height = 18.pixels()
        } childOf detailContent

        BrassLabel("PERMISSIONS", Colors.UI_TEXT_DARK).constrain {
            y = 49.pixels()
        } childOf detailContent

        val target = parseTarget(key.target)
        val permissions = BrassFlow(
            gapX = 8f,
            gapY = 6f,
            itemHeight = 22f,
            stretch = true,
        ).constrain {
            y = 63.pixels()
            width = 100.percent()
        } childOf detailContent

        supportedActions(target).forEach { action ->
            permissions.add(permissionCheckboxRow(group, action), 132f)
        }
        permissions.constrain {
            height = basicHeightConstraint { permissions.contentHeight() }
        }
        disableEntrances(detailContent)
    }

    private fun addLargePreview(key: TargetKey) {
        when (parseTarget(key.target)) {
            ClaimPermissionTarget.BLOCK,
            ClaimPermissionTarget.BLOCK_ENTITY -> BrassBlockPreview(key.targetId, spin = 12f, tooltip = true).constrain {
                width = 38.pixels()
                height = 36.pixels()
            } childOf detailContent

            ClaimPermissionTarget.ENTITY -> BrassEntity(key.targetId, spin = 12f, tooltip = true).constrain {
                width = 38.pixels()
                height = 36.pixels()
            } childOf detailContent

            ClaimPermissionTarget.THROWABLE -> BrassItem(key.targetId, tooltip = true).constrain {
                x = 4.pixels()
                y = 2.pixels()
                width = 30.pixels()
                height = 30.pixels()
            } childOf detailContent
        }
    }

    private fun permissionCheckboxRow(
        group: TargetGroup,
        action: ClaimPermissionAction,
    ): UIContainer {
        val slot = PermissionSlot(group.key, action)
        val subjects = group.subjects()
        val enabledCount = group.entries
            .filter { it.action == action.name }
            .map { it.subject.key() }
            .distinct()
            .size
        val row = UIContainer()
        val checkbox = BrassCheckbox(
            initial = subjects.isNotEmpty() && enabledCount == subjects.size,
        ) { enabled ->
            setPermission(group.key, action, enabled)
        }.constrain {
            x = 2.pixels()
            y = CenterConstraint()
            width = 14.pixels()
            height = 14.pixels()
        } childOf row
        permissionCheckboxes[slot] = checkbox

        val suffix = if (enabledCount in 1 until subjects.size) " · $enabledCount/${subjects.size}" else ""
        BrassLabel("${displayActionName(action)}$suffix", Colors.UI_TEXT).constrain {
            x = 22.pixels()
            y = CenterConstraint()
        } childOf row
        return row
    }

    private fun setPermission(key: TargetKey, action: ClaimPermissionAction, enabled: Boolean) {
        if (savingChanges) return
        val group = groupedTargets().firstOrNull { it.key == key } ?: return
        val actionEntries = group.entries.filter { it.action == action.name }
        if (!enabled && actionEntries.size == group.entries.size) {
            permissionCheckboxes[PermissionSlot(key, action)]?.setSilently(true)
            showDeletionConfirmation(key, action)
            return
        }
        applyActionChange(group, action, enabled)
    }

    private fun applyActionChange(
        group: TargetGroup,
        action: ClaimPermissionAction,
        enabled: Boolean,
    ) {
        if (enabled) {
            group.subjects().forEach { subject ->
                putDraft(PermissionDraftEntry(
                    group.key.target,
                    group.key.targetId,
                    action.name,
                    subject,
                ))
            }
        } else {
            draftEntries.entries.removeIf { (_, entry) ->
                entry.targetRef() == group.key.toRef() && entry.action == action.name
            }
        }
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun showDeletionConfirmation(key: TargetKey, action: ClaimPermissionAction) {
        val confirmation = BrassModal(
            title = "Remove permission target?",
            width = 318f,
            height = 128f,
            showClose = true,
            dismissOnEscape = true,
        )
        confirmation.body { host ->
            BrassLabel("No actions would remain for ${shortTargetName(key.targetId)}.", Colors.UI_TEXT).constrain {
                x = 4.pixels()
                y = 12.pixels()
            } childOf host
            BrassLabel("The deletion remains staged until Done is pressed.", Colors.WARN, scale = 0.86f).constrain {
                x = 4.pixels()
                y = 34.pixels()
            } childOf host
        }
        confirmation.footer(
            BrassButton("Keep permission") { confirmation.dismiss() },
            BrassButton("Delete target", BrassAccent.DANGER) {
                val group = groupedTargets().firstOrNull { it.key == key }
                if (group != null) applyActionChange(group, action, false)
                confirmation.dismiss()
            },
        ).show(background)
    }

    private fun showTargetDeletionConfirmation(key: TargetKey) {
        val confirmation = BrassModal(
            title = "Delete permission target?",
            width = 318f,
            height = 128f,
            showClose = true,
            dismissOnEscape = true,
        )
        confirmation.body { host ->
            BrassLabel("Delete ${shortTargetName(key.targetId)} and all its permissions?", Colors.UI_TEXT).constrain {
                x = 4.pixels()
                y = 12.pixels()
            } childOf host
            BrassLabel("The deletion remains staged until Done is pressed.", Colors.WARN, scale = 0.86f).constrain {
                x = 4.pixels()
                y = 34.pixels()
            } childOf host
        }
        confirmation.footer(
            BrassButton("Cancel") { confirmation.dismiss() },
            BrassButton("Delete target", BrassAccent.DANGER) {
                draftEntries.entries.removeIf { (_, entry) ->
                    entry.targetRef() == key.toRef()
                }
                confirmation.dismiss()
                refreshTargets(selectFirst = false)
                refreshStatus()
            },
        ).show(background)
    }

    private fun stageAddedPermission(
        target: PermissionTargetRef,
        actions: List<ClaimPermissionAction>,
        subjects: List<PermissionSubject>,
    ) {
        if (subjects.any(PermissionSubject::isAll)) {
            actions.forEach { action ->
                removeNamedEntries(target, action)
            }
        }
        actions.forEach { action ->
            subjects.forEach { subject ->
                putDraft(PermissionDraftEntry(target.target, target.targetId, action.name, subject))
            }
        }
        selectedKey = TargetKey(target.target, target.targetId)
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun applyPlayers(key: TargetKey, subjects: List<PermissionSubject>) {
        val group = groupedTargets().firstOrNull { it.key == key } ?: return
        val currentSubjects = group.subjects()
        val commonActions = supportedActions(parseTarget(key.target)).filter { action ->
            group.entries.count { it.action == action.name } == currentSubjects.size
        }
        val fallbackActions = group.entries.map { parseAction(it.action) }.distinct()
        val inheritedActions = commonActions.ifEmpty { fallbackActions }
        val selectedKeys = subjects.map(PermissionSubject::key).toSet()

        draftEntries.entries.removeIf { (_, entry) ->
            entry.targetRef() == key.toRef() && entry.subject.key() !in selectedKeys
        }
        subjects.forEach { subject ->
            if (group.entries.none { it.subject.key() == subject.key() }) {
                inheritedActions.forEach { action ->
                    putDraft(PermissionDraftEntry(key.target, key.targetId, action.name, subject))
                }
            }
        }
        if (subjects.any(PermissionSubject::isAll)) {
            draftEntries.entries.removeIf { (_, entry) ->
                entry.targetRef() == key.toRef() && !entry.subject.isAll
            }
        }
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun applyBulk(
        targets: Set<PermissionTargetRef>,
        actions: Set<ClaimPermissionAction>,
        subjects: List<PermissionSubject>,
        enabled: Boolean,
    ) {
        targets.forEach { target ->
            actions.filter(parseTarget(target.target)::supports).forEach { action ->
                subjects.forEach { subject ->
                    if (enabled) {
                        if (subject.isAll) removeNamedEntries(target, action)
                        putDraft(PermissionDraftEntry(target.target, target.targetId, action.name, subject))
                    } else {
                        draftEntries.entries.removeIf { (_, entry) ->
                            entry.targetRef() == target &&
                                    entry.action == action.name &&
                                    entry.subject.key() == subject.key()
                        }
                    }
                }
            }
        }
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun removeNamedEntries(target: PermissionTargetRef, action: ClaimPermissionAction) {
        draftEntries.entries.removeIf { (_, entry) ->
            entry.targetRef() == target && entry.action == action.name && !entry.subject.isAll
        }
    }

    private fun putDraft(entry: PermissionDraftEntry) {
        draftEntries[entry.key()] = entry
    }

    private fun saveChanges(closeAfter: Boolean) {
        if (savingChanges) return
        val removed = serverEntries.filterKeys { it !in draftEntries }
            .values
            .map { it.networkEntry(false) }
        val added = draftEntries.filterKeys { it !in serverEntries }
            .values
            .map { it.networkEntry(true) }
        val changes = removed + added
        if (changes.isEmpty()) {
            if (closeAfter) onClose()
            return
        }

        savingChanges = true
        closeAfterSave = closeAfter
        setInteractive(false)
        refreshStatus()
        ClaimPermissionsNetwork.sendToServer(
            ClaimPermissionsBatchPayload(
                ClaimPermissionsBatchPayload.Operation.APPLY_CHANGES,
                payload.claimOwner(),
                payload.subConfigIndex(),
                false,
                changes,
                emptyList(),
                emptyList(),
            ),
        )
    }

    private fun requestClose() {
        if (!isDirty()) {
            onClose()
            return
        }
        val confirmation = BrassModal(
            title = "Unsaved permission changes",
            width = 336f,
            height = 132f,
            showClose = true,
            dismissOnEscape = true,
        )
        confirmation.body { host ->
            BrassLabel("Save your staged permission changes before closing?", Colors.UI_TEXT).constrain {
                x = 3.pixels()
                y = 14.pixels()
            } childOf host
            BrassLabel("Discard closes without changing the claim.", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
                x = 3.pixels()
                y = 36.pixels()
            } childOf host
        }
        confirmation.footer(
            BrassButton("Cancel") { confirmation.dismiss() },
            BrassButton("Discard", BrassAccent.DANGER) {
                confirmation.dismiss()
                onClose()
            },
            BrassButton("Save", BrassAccent.BRASS) {
                confirmation.dismiss()
                saveChanges(closeAfter = true)
            },
        ).show(background)
    }

    private fun setInteractive(active: Boolean) {
        addButton.active = active
        bulkButton.active = active
        doneButton.active = active
        permissionCheckboxes.values.forEach { it.active = active }
        targetTiles.values.forEach { it.active = active }
    }

    private fun isDirty(): Boolean = serverEntries.keys != draftEntries.keys

    private fun hasDescendant(root: UIComponent, predicate: (UIComponent) -> Boolean): Boolean =
        root.children.any { child -> predicate(child) || hasDescendant(child, predicate) }

    private fun refreshStatus() {
        val targetCount = draftEntries.values.map(PermissionDraftEntry::targetRef).distinct().size
        val changeCount = serverEntries.keys.subtract(draftEntries.keys).size +
                draftEntries.keys.subtract(serverEntries.keys).size
        val scope = if (payload.adminOverride()) {
            "Admin · ${payload.claimOwnerName()}"
        } else {
            payload.scopeName()
        }
        statusLabel.text = when {
            savingChanges -> "Saving $changeCount changes…"
            payload.error() -> "Error: ${payload.status()} · $changeCount unsaved"
            changeCount > 0 -> "$changeCount unsaved · $scope · ${draftEntries.size} grants / $targetCount targets"
            payload.status().isNotBlank() -> "${payload.status()} · $scope"
            else -> "$scope · ${draftEntries.size} grants / $targetCount targets"
        }
        statusLabel.tint = when {
            payload.error() -> Colors.DANGER
            changeCount > 0 -> Colors.WARN
            else -> Colors.UI_TEXT_DARK
        }
        doneButton.label = if (changeCount > 0) "Save & Done" else "Done"
    }

    private fun shortTargetName(id: String): String {
        val readable = id.substringAfter(':', id)
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
        return if (readable.length <= 15) readable else readable.take(14) + "…"
    }

    private fun tileSummary(group: TargetGroup): String {
        val supported = supportedActions(parseTarget(group.key.target)).size
        val enabled = group.entries.map { it.action }.distinct().size
        val actionText = if (enabled == supported) "All" else "$enabled/$supported"
        return "$actionText · ${subjectSummary(group)}"
    }

    private fun subjectSummary(group: TargetGroup): String {
        val subjects = group.subjects()
        return when {
            subjects.any(PermissionSubject::isAll) -> "All players"
            subjects.size == 1 -> subjects.first().displayName()
            else -> "${subjects.size} players"
        }
    }

    private data class TargetKey(
        val target: String,
        val targetId: String,
    ) {
        fun toRef(): PermissionTargetRef = PermissionTargetRef(target, targetId)
    }

    private data class TargetGroup(
        val key: TargetKey,
        val entries: List<PermissionDraftEntry>,
    ) {
        fun subjects(): List<PermissionSubject> = entries
            .map(PermissionDraftEntry::subject)
            .distinctBy(PermissionSubject::key)
    }

    private data class PermissionSlot(
        val key: TargetKey,
        val action: ClaimPermissionAction,
    )

    private class DetailTransitionLayer(slideFrom: Float) : UIContainer() {
        private val slide = BrassEased(slideFrom, speed = 20f).apply {
            target = 0f
        }

        override fun draw(matrixStack: UMatrixStack) {
            val offset = slide.advance()
            matrixStack.push()
            matrixStack.translate(offset, 0f, 0f)
            try {
                super.draw(matrixStack)
            } finally {
                matrixStack.pop()
            }
        }
    }

    companion object {
        private const val WINDOW_WIDTH = 580f
        private const val WINDOW_HEIGHT = 350f
        private const val TARGET_WIDTH = 96f
        private const val TARGET_STEP = 104f

        private fun entriesFromPayload(
            payload: ClaimPermissionsSyncPayload,
        ): LinkedHashMap<String, PermissionDraftEntry> = LinkedHashMap(
            payload.entries()
                .map(PermissionDraftEntry::fromSync)
                .associateBy(PermissionDraftEntry::key),
        )

        @JvmStatic
        fun supportedActions(target: ClaimPermissionTarget): List<ClaimPermissionAction> =
            ClaimPermissionAction.entries.filter(target::supports)

        @JvmStatic
        fun parseTarget(name: String): ClaimPermissionTarget =
            runCatching { ClaimPermissionTarget.valueOf(name) }
                .getOrDefault(ClaimPermissionTarget.BLOCK)

        @JvmStatic
        fun parseAction(name: String): ClaimPermissionAction =
            runCatching { ClaimPermissionAction.valueOf(name) }
                .getOrDefault(ClaimPermissionAction.INTERACT)

        @JvmStatic
        fun displayName(enumName: String): String =
            enumName
                .lowercase(Locale.ROOT)
                .replace('_', ' ')
                .replaceFirstChar { it.titlecase(Locale.ROOT) }

        @JvmStatic
        fun displayTargetName(enumName: String): String = when (parseTarget(enumName)) {
            ClaimPermissionTarget.THROWABLE -> "Thrown item"
            ClaimPermissionTarget.BLOCK_ENTITY -> "Block entity"
            else -> displayName(enumName)
        }

        @JvmStatic
        fun displayActionName(action: ClaimPermissionAction): String = when (action) {
            ClaimPermissionAction.THROWABLE -> "Throw"
            else -> displayName(action.name)
        }
    }
}
