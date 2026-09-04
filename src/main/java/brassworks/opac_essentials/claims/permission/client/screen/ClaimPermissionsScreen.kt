package brassworks.opac_essentials.claims.permission.client.screen

import brassworks.opac_essentials.claims.permission.client.OpacEssentialsUiTheme
import brassworks.opac_essentials.claims.permission.client.modal.AddClaimPermissionModal
import brassworks.opac_essentials.claims.permission.client.modal.BulkPermissionModal
import brassworks.opac_essentials.claims.permission.client.modal.PermissionDraftEntry
import brassworks.opac_essentials.claims.permission.client.modal.PermissionPlayersModal
import brassworks.opac_essentials.claims.permission.client.modal.PermissionSubject
import brassworks.opac_essentials.claims.permission.client.modal.PermissionTargetRef
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsBatchPayload
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload
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
import net.swzo.brass.ui.BrassScreen
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassDismissable
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
import org.lwjgl.glfw.GLFW
import java.util.LinkedHashMap
import java.util.Locale

class ClaimPermissionsScreen(
    initialPayload: ClaimPermissionsSyncPayload,
) : BrassScreen(backdropColor = OpacEssentialsUiTheme.BACKDROP) {
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
    private lateinit var detailContent: UIContainer
    private lateinit var statusLabel: BrassLabel
    private lateinit var doneButton: BrassButton
    private lateinit var addButton: BrassButton
    private lateinit var bulkButton: BrassButton
    private lateinit var frame: BrassWindow

    init {
        OpacEssentialsUiTheme.apply()
        buildUi()
        refreshTargets(selectFirst = true)
        refreshStatus()
        OpacEssentialsUiTheme.disableAnimations(background)
    }

    private fun buildUi() {
        frame = BrassWindow(
            title = "OPAC",
            subtitle = "claims  /  permissions",
            onClose = ::finishClose,
            controls = false,
            minW = MIN_WINDOW_WIDTH,
            minH = MIN_WINDOW_HEIGHT,
        ).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            width = basicWidthConstraint { component ->
                minOf(WINDOW_WIDTH, (component.parent.getWidth() - 28f).coerceAtLeast(MIN_WINDOW_WIDTH))
            }
            height = basicHeightConstraint { component ->
                minOf(WINDOW_HEIGHT, (component.parent.getHeight() - 24f).coerceAtLeast(MIN_WINDOW_HEIGHT))
            }
        } childOf background

        BrassSquareButton(BrassIcons.NONE, BrassAccent.DANGER) { requestClose() }.also {
            it.entranceEnabled = false
        }.constrain {
            x = 7.pixels(true)
            y = 4.pixels()
            width = 18.pixels()
            height = 10.pixels()
        } childOf frame

        val rootScroll = ScrollComponent(
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
                maxOf(component.parent.getHeight(), MIN_CONTENT_HEIGHT)
            }
        } childOf rootScroll

        val search = BrassSearchField("Search targets or players...") { value ->
            query = value.trim().lowercase(Locale.ROOT)
            refreshTargets(selectFirst = false)
        }.constrain {
            x = 12.pixels()
            y = 10.pixels()
            width = 100.percent() - 166.pixels()
            height = 18.pixels()
        } childOf content
        search.onSearchNow = { value ->
            query = value.trim().lowercase(Locale.ROOT)
            refreshTargets(selectFirst = false)
        }

        addButton = BrassButton("+ Add", BrassAccent.BRASS) {
            openAddPermission()
        }.constrain {
            x = 100.percent() - 146.pixels()
            y = 10.pixels()
            width = 64.pixels()
            height = 18.pixels()
        } childOf content

        bulkButton = BrassButton("Bulk edit") {
            BulkPermissionModal(payload, draftEntries.values, ::applyBulk).show(background)
        }.constrain {
            x = 100.percent() - 76.pixels()
            y = 10.pixels()
            width = 64.pixels()
            height = 18.pixels()
        } childOf content

        val targetPanel = BrassPanel(
            title = "PERMISSION TARGETS",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 12.pixels()
            y = 38.pixels()
            width = 100.percent() - 24.pixels()
            height = 90.pixels()
        } childOf content

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
                maxOf(component.parent.getWidth(), 4f + visibleTargets.size * TARGET_STEP)
            }
            height = 100.percent()
        } childOf targetScroll

        val detailPanel = BrassPanel(
            title = "SELECTED TARGET",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 12.pixels()
            y = 136.pixels()
            width = 100.percent() - 24.pixels()
            height = 100.percent() - 172.pixels()
        } childOf content
        detailHost = detailPanel.content
        detailContent = UIContainer().constrain {
            width = 100.percent()
            height = 100.percent()
        } childOf detailHost

        statusLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 12.pixels()
            y = 100.percent() - 23.pixels()
        } childOf content

        doneButton = BrassButton("Done", BrassAccent.BRASS) { saveChanges(closeAfter = true) }.constrain {
            x = 100.percent() - 82.pixels()
            y = 100.percent() - 30.pixels()
            width = 70.pixels()
            height = 18.pixels()
        } childOf content
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
            if (shouldClose) finishClose()
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
            !hasDescendant(window) { it is BrassDismissable }
        ) {
            requestClose()
            return
        }
        super.onKeyPressed(keyCode, typedChar, modifiers)
    }

    private fun refreshTargets(selectFirst: Boolean) {
        updateVisibleTargets(selectFirst)
        rebuildTargetStrip()
        rebuildDetails()
    }

    private fun openAddPermission() {
        if (savingChanges) return
        AddClaimPermissionModal(
            payload,
            draftEntries.values.toList(),
            ::stageAddedPermission,
        ).show(background)
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
            ).also {
                it.chrome = BrassChrome.FLAT
                it.clickable = true
                it.entranceEnabled = false
                it.onMouseClick { openAddPermission() }
            }.constrain {
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
            if (!savingChanges && selectedKey != group.key) {
                selectedKey = group.key
                syncTargetTileSelection()
                rebuildDetails()
            }
        }.also {
            it.selectable = true
            it.selected = selected
            it.chrome = BrassChrome.FLAT
            it.centered = false
            it.entranceEnabled = false
            it.clickable = false
        }.constrain {
            x = (2f + index * TARGET_STEP).pixels()
            y = 1.pixels()
            width = TARGET_WIDTH.pixels()
            height = TARGET_HEIGHT.pixels()
        } childOf targetContent
        targetTiles[group.key] = tile

        val content = UIContainer().constrain {
            x = 3.pixels()
            y = 3.pixels()
            width = 100.percent() - 6.pixels()
            height = 100.percent() - 6.pixels()
        } childOf tile

        when (parseTarget(group.key.target)) {
            ClaimPermissionTarget.BLOCK,
            ClaimPermissionTarget.BLOCK_ENTITY -> BrassBlockPreview(group.key.targetId, tooltip = true).constrain {
                x = CenterConstraint()
                y = 0.pixels()
                width = 25.pixels()
                height = 25.pixels()
            } childOf content

            ClaimPermissionTarget.ENTITY -> BrassEntity(group.key.targetId, tooltip = true).constrain {
                x = CenterConstraint()
                y = 0.pixels()
                width = 27.pixels()
                height = 27.pixels()
            } childOf content

            ClaimPermissionTarget.TRAIN -> BrassItem(TRAIN_ICON_ITEM, tooltip = true).constrain {
                x = CenterConstraint()
                y = 0.pixels()
                width = 25.pixels()
                height = 25.pixels()
            } childOf content

            ClaimPermissionTarget.THROWABLE -> BrassItem(group.key.targetId, tooltip = true).constrain {
                x = CenterConstraint()
                y = 1.pixels()
                width = 25.pixels()
                height = 25.pixels()
            } childOf content
        }

        BrassLabel(targetDisplayName(group.key), Colors.UI_TEXT).constrain {
            x = CenterConstraint()
            y = 31.pixels()
        } childOf content

        BrassLabel(tileSummary(group), Colors.UI_TEXT_DARK, scale = 0.7f).constrain {
            x = CenterConstraint()
            y = 44.pixels()
        } childOf content
        OpacEssentialsUiTheme.disableAnimations(tile)
    }

    private fun syncTargetTileSelection() {
        targetTiles.forEach { (key, tile) ->
            val selected = key == selectedKey
            tile.selected = selected
            tile.accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    private fun rebuildDetails() {
        val previous = detailContent
        detailContent = UIContainer().constrain {
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
            ).also {
                it.chrome = BrassChrome.FLAT
                it.clickable = true
                it.entranceEnabled = false
                it.onMouseClick { openAddPermission() }
            }.constrain {
                width = 100.percent()
                height = 100.percent()
            } childOf detailContent
            OpacEssentialsUiTheme.disableAnimations(detailContent)
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
            y = 1.pixels()
            width = 84.pixels()
            height = 18.pixels()
        } childOf detailContent

        BrassButton("Delete", BrassAccent.DANGER) {
            showTargetDeletionConfirmation(key)
        }.constrain {
            x = 100.percent() - 66.pixels()
            y = 1.pixels()
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
        OpacEssentialsUiTheme.disableAnimations(detailContent)
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

            ClaimPermissionTarget.TRAIN -> BrassBlockPreview(
                TRAIN_ICON_ITEM,
                spin = 12f,
                tooltip = true,
            ).constrain {
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
        val subjects = group.effectiveSubjects()
        val enabledCount = subjects.count { group.hasAction(it, action) }
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
            group.effectiveSubjects().forEach { subject ->
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
            showClose = false,
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
            showClose = false,
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
        val currentSubjects = group.effectiveSubjects()
        val commonActions = supportedActions(parseTarget(key.target)).filter { action ->
            currentSubjects.all { group.hasAction(it, action) }
        }
        val fallbackActions = group.entries.map { parseAction(it.action) }.distinct()
        val inheritedActions = commonActions.ifEmpty { fallbackActions }
        draftEntries.entries.removeIf { (_, entry) ->
            entry.targetRef() == key.toRef() &&
                subjects.none { it.matches(entry.subject) }
        }
        subjects.forEach { subject ->
            if (group.entries.none { it.subject.matches(subject) }) {
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
                        putDraft(PermissionDraftEntry(target.target, target.targetId, action.name, subject))
                    } else {
                        draftEntries.entries.removeIf { (_, entry) ->
                            entry.targetRef() == target &&
                                    entry.action == action.name &&
                                    (subject.isAll || entry.subject.matches(subject))
                        }
                    }
                }
            }
        }
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun putDraft(entry: PermissionDraftEntry) {
        val canonicalSubject = draftEntries.values
            .firstOrNull {
                it.targetRef() == entry.targetRef() &&
                    it.subject.matches(entry.subject)
            }
            ?.subject
            ?: entry.subject
        val normalizedEntry = entry.copy(subject = canonicalSubject)
        if (draftEntries.values.any { it.covers(normalizedEntry) }) {
            return
        }
        draftEntries[normalizedEntry.key()] = normalizedEntry
    }

    private fun saveChanges(closeAfter: Boolean) {
        if (savingChanges) return
        val removed = pendingRemovals()
            .map { it.networkEntry(false) }
        val added = pendingAdditions()
            .map { it.networkEntry(true) }
        val changes = removed + added
        if (changes.isEmpty()) {
            if (closeAfter) finishClose()
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
            finishClose()
            return
        }
        val confirmation = BrassModal(
            title = "Are you sure?",
            width = 336f,
            height = 132f,
            showClose = false,
            dismissOnEscape = true,
        )
        confirmation.body { host ->
            BrassLabel("You have unsaved permission changes.", Colors.UI_TEXT).constrain {
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
                finishClose()
            },
            BrassButton("Save", BrassAccent.BRASS) {
                confirmation.dismiss()
                saveChanges(closeAfter = true)
            },
        ).show(background)
    }

    private fun finishClose() {
        onClose()
    }

    private fun setInteractive(active: Boolean) {
        addButton.active = active
        bulkButton.active = active
        doneButton.active = active
        targetTiles.values.forEach { it.active = active }
        permissionCheckboxes.values.forEach { it.active = active }
    }

    private fun isDirty(): Boolean = pendingChangeCount() > 0

    private fun pendingRemovals(): List<PermissionDraftEntry> =
        serverEntries.values.filter { serverEntry ->
            draftEntries.values.none { it.matches(serverEntry) }
        }

    private fun pendingAdditions(): List<PermissionDraftEntry> =
        draftEntries.values.filter { draftEntry ->
            serverEntries.values.none { it.matches(draftEntry) }
        }

    private fun pendingChangeCount(): Int = pendingRemovals().size + pendingAdditions().size

    private fun hasDescendant(root: UIComponent, predicate: (UIComponent) -> Boolean): Boolean =
        root.children.any { child -> predicate(child) || hasDescendant(child, predicate) }

    private fun refreshStatus() {
        val targetCount = draftEntries.values.map(PermissionDraftEntry::targetRef).distinct().size
        val changeCount = pendingChangeCount()
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

    private fun targetDisplayName(key: TargetKey): String =
        if (parseTarget(key.target) == ClaimPermissionTarget.TRAIN) {
            "Trains"
        } else {
            shortTargetName(key.targetId)
        }

    private fun tileSummary(group: TargetGroup): String {
        val actions = supportedActions(parseTarget(group.key.target))
        val enabled = actions.count(group::hasActionForAll)
        val actionText = if (enabled == actions.size) "All" else "$enabled/${actions.size}"
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
        fun subjects(): List<PermissionSubject> = buildList {
            entries.map(PermissionDraftEntry::subject).forEach { subject ->
                if (none { it.matches(subject) }) add(subject)
            }
        }

        fun effectiveSubjects(): List<PermissionSubject> {
            val subjects = subjects()
            return if (subjects.any(PermissionSubject::isAll)) {
                listOf(PermissionSubject.ALL)
            } else {
                subjects
            }
        }

        fun hasAction(subject: PermissionSubject, action: ClaimPermissionAction): Boolean =
            entries.any {
                it.action == action.name &&
                    it.subject.matches(subject)
            }

        fun hasActionForAll(action: ClaimPermissionAction): Boolean =
            effectiveSubjects().all { hasAction(it, action) }
    }

    private data class PermissionSlot(
        val key: TargetKey,
        val action: ClaimPermissionAction,
    )

    companion object {
        private const val WINDOW_WIDTH = 600f
        private const val WINDOW_HEIGHT = 360f
        private const val MIN_WINDOW_WIDTH = 270f
        private const val MIN_WINDOW_HEIGHT = 180f
        private const val MIN_CONTENT_HEIGHT = 270f
        private const val TARGET_WIDTH = 90f
        private const val TARGET_HEIGHT = 58f
        private const val TARGET_STEP = 96f
        private const val TRAIN_ICON_ITEM = "create:track"

        private fun entriesFromPayload(
            payload: ClaimPermissionsSyncPayload,
        ): LinkedHashMap<String, PermissionDraftEntry> = LinkedHashMap(
            payload.entries()
                .map(PermissionDraftEntry::fromSync)
                .associateBy(PermissionDraftEntry::key),
        )

        @JvmStatic
        fun supportedActions(target: ClaimPermissionTarget): List<ClaimPermissionAction> =
            if (target == ClaimPermissionTarget.TRAIN) {
                listOf(
                    ClaimPermissionAction.TRAVEL,
                    ClaimPermissionAction.INTERACT,
                    ClaimPermissionAction.CONTROL,
                )
            } else {
                ClaimPermissionAction.entries.filter(target::supports)
            }

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
            ClaimPermissionTarget.TRAIN -> "Trains"
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
