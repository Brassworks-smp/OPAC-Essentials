package brassworks.opac_essentials.claims.permission.client.screen

import brassworks.opac_essentials.client.ResponsiveBrassScreen
import brassworks.opac_essentials.claims.menu.client.OpacMainMenuScreen
import brassworks.opac_essentials.claims.permission.client.OpacEssentialsUiTheme
import brassworks.opac_essentials.claims.permission.client.modal.AddClaimPermissionModal
import brassworks.opac_essentials.claims.permission.client.modal.BulkPermissionModal
import brassworks.opac_essentials.claims.permission.client.modal.PermissionDraftEntry
import brassworks.opac_essentials.claims.permission.client.modal.PermissionSubject
import brassworks.opac_essentials.claims.permission.client.modal.PermissionTargetRef
import brassworks.opac_essentials.claims.permission.client.modal.permissionEntry
import brassworks.opac_essentials.claims.permission.client.modal.permissionSubject
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
import net.minecraft.client.Minecraft
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassDismissable
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassSearchField
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.media.BrassBlockPreview
import net.swzo.brass.ui.kit.media.BrassEntity
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassItem
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.surface.BrassWindow
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextInput
import org.lwjgl.glfw.GLFW
import java.util.LinkedHashMap
import java.util.Locale

class ClaimPermissionsScreen(
    initialPayload: ClaimPermissionsSyncPayload,
) : ResponsiveBrassScreen(
    WINDOW_WIDTH,
    WINDOW_HEIGHT,
    WINDOW_HORIZONTAL_MARGIN,
    WINDOW_VERTICAL_MARGIN,
    OpacEssentialsUiTheme.BACKDROP,
) {
    private var payload = initialPayload
    private var serverEntries = entriesFromPayload(initialPayload)
    private var draftEntries = LinkedHashMap(serverEntries)
    private var query = ""
    private var selectedCategory: ClaimPermissionTarget? = null
    private var selectedKey: TargetKey? = null
    private var selectedSubject = PermissionSubject.ALL
    private val addedSubjects = mutableMapOf<TargetKey, MutableList<PermissionSubject>>()
    private var addingPlayer = false
    private var playerSuggestions: UIContainer? = null
    private var visibleTargets: List<TargetGroup> = emptyList()
    private var savingChanges = false
    private var closeAfterSave = false
    private var returningToMenu = false
    private lateinit var previousThemeId: String
    private var previousAccentHex: String? = null
    private var themeApplied = false

    private val permissionCheckboxes = mutableMapOf<PermissionSlot, BrassCheckbox>()
    private val targetTiles = mutableMapOf<TargetKey, BrassWidget>()
    private val playerControls = mutableListOf<BrassWidget>()
    private var categoryOptions: List<ClaimPermissionTarget?> = emptyList()
    private lateinit var categoryContent: UIContainer
    private lateinit var categorySwitch: BrassTabSwitch
    private lateinit var targetContent: UIContainer
    private lateinit var detailHost: UIContainer
    private lateinit var detailContent: UIContainer
    private lateinit var statusLabel: BrassLabel
    private lateinit var doneButton: BrassButton
    private lateinit var addButton: BrassButton
    private lateinit var bulkButton: BrassButton
    private lateinit var frame: BrassWindow

    override fun afterInitialization() {
        super.afterInitialization()
        if (!themeApplied) {
            previousThemeId = BrassThemes.currentId
            previousAccentHex = BrassThemes.accentHex
            themeApplied = true
        }
        OpacEssentialsUiTheme.apply()
        buildUi()
        rebuildCategories()
        refreshTargets(selectFirst = true)
        refreshStatus()
        OpacEssentialsUiTheme.disableAnimations(background)
    }

    override fun onScreenClose() {
        targetTiles.values.forEach(BrassTooltip::detach)
        playerControls.forEach(BrassTooltip::detach)
        super.onScreenClose()
        if (themeApplied) {
            BrassThemes.apply(previousThemeId, previousAccentHex)
        }
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
            width = responsiveWindowWidthConstraint()
            height = responsiveWindowHeightConstraint()
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
                val columns = ((component.parent.getWidth() - 48f) / 140f).toInt().coerceIn(1, 3)
                val rows = (3 + columns - 1) / columns
                maxOf(
                    component.parent.getHeight(),
                    MIN_CONTENT_HEIGHT + (rows - 1) * 28f + if (addingPlayer) 26f else 0f,
                )
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

        val categoryHost = UIContainer().constrain {
            x = 12.pixels()
            y = 38.pixels()
            width = 100.percent() - 24.pixels()
            height = 20.pixels()
        } childOf content

        categoryContent = UIContainer().constrain {
            width = 100.percent()
            height = 100.percent()
        } childOf categoryHost

        val targetPanel = BrassPanel(
            title = "PERMISSION TARGETS",
            layout = BrassPanel.Layout.FREE,
        ).constrain {
            x = 12.pixels()
            y = 66.pixels()
            width = 100.percent() - 24.pixels()
            height = 70.pixels()
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
            y = 144.pixels()
            width = 100.percent() - 24.pixels()
            height = 100.percent() - 180.pixels()
        } childOf content
        detailHost = detailPanel.content
        detailContent = UIContainer().constrain {
            y = 4.pixels()
            width = 100.percent()
            height = 100.percent() - 4.pixels()
        } childOf detailHost

        statusLabel = BrassLabel("", Colors.UI_TEXT_DARK, scale = 0.86f).constrain {
            x = 12.pixels()
            y = 100.percent() - 23.pixels()
        } childOf content

        BrassButton("Back") { requestClose(returnToMenu = true) }.also {
            it.chrome = BrassChrome.KEYCAP
            it.entranceEnabled = false
        }.constrain {
            x = 100.percent() - 138.pixels()
            y = 100.percent() - 30.pixels()
            width = 50.pixels()
            height = 18.pixels()
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

    private fun rebuildCategories() {
        categoryContent.clearChildren()
        categoryOptions = buildList<ClaimPermissionTarget?> {
            add(null)
            addAll(ClaimPermissionTarget.availableTargets())
        }
        val labels = categoryOptions.map { option ->
            option?.let { displayTargetName(it.name) } ?: "All"
        }
        categorySwitch = BrassTabSwitch(
            options = labels,
            initialIndex = categoryOptions.indexOf(selectedCategory).coerceAtLeast(0),
            equalWidths = true,
        ) { index ->
            val option = categoryOptions[index]
            if (selectedCategory != option) {
                selectedCategory = option
                refreshTargets(selectFirst = true)
            }
        }.also { tabs ->
            tabs.children.filterIsInstance<BrassButton>().forEach { button ->
                button.chrome = BrassChrome.FLAT
                button.entranceEnabled = false
            }
        }.constrain {
            width = 100.percent() -
                    (categoryOptions.size - 1).coerceAtLeast(0).pixels()
            height = 18.pixels()
        } childOf categoryContent
    }

    private fun syncCategorySelection() {
        val index = categoryOptions.indexOf(selectedCategory)
        if (index >= 0) categorySwitch.setSilently(index)
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
        val previousKey = selectedKey
        visibleTargets = groupedTargets()
            .filter { group ->
                selectedCategory == null ||
                        parseTarget(group.key.target) == selectedCategory
            }
            .filter(::matchesQuery)
        if (selectFirst && selectedKey == null) {
            selectedKey = visibleTargets.firstOrNull()?.key
        } else if (visibleTargets.none { it.key == selectedKey }) {
            selectedKey = visibleTargets.firstOrNull()?.key
        }
        if (selectedKey != previousKey) selectedSubject = PermissionSubject.ALL
    }

    private fun groupedTargets(): List<TargetGroup> = draftEntries.values
        .groupBy { TargetKey(it.target, it.targetId) }
        .map { (key, entries) -> TargetGroup(key, entries) }
        .sortedWith(compareBy<TargetGroup> { it.key.target }.thenBy { it.key.targetId })

    private fun matchesQuery(group: TargetGroup): Boolean {
        return query.isEmpty() || group.key.targetId.lowercase(Locale.ROOT).contains(query) ||
                group.key.target.lowercase(Locale.ROOT).contains(query) ||
                group.entries.any {
                    it.action.lowercase(Locale.ROOT).contains(query) ||
                            it.subject.displayName().lowercase(Locale.ROOT).contains(query)
                }
    }

    private fun rebuildTargetStrip() {
        targetTiles.values.forEach(BrassTooltip::detach)
        targetContent.clearChildren()
        targetTiles.clear()
        if (visibleTargets.isEmpty()) {
            BrassEmptyState(
                BrassIcons.SEARCH,
                "No permission targets",
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
        val previewId = parseTarget(group.key.target).previewId(group.key.targetId)
        val icon = when (parseTarget(group.key.target)) {
            ClaimPermissionTarget.BLOCK,
            ClaimPermissionTarget.BLOCK_ENTITY -> BrassBlockPreview(previewId, tooltip = false)

            ClaimPermissionTarget.ENTITY -> BrassEntity(previewId, tooltip = false)
            ClaimPermissionTarget.TRAIN -> BrassItem(TRAIN_ICON_ITEM, tooltip = false)
            ClaimPermissionTarget.THROWABLE -> BrassItem(previewId, tooltip = false)
        }.also {
            it.selected = selected
            it.accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT
            it.chrome = BrassChrome.KEYCAP
            it.clickable = true
            it.entranceEnabled = false
            it.onMouseClick { event ->
                if (event.mouseButton == 0 && !savingChanges) {
                    selectedKey = group.key
                    selectedSubject = PermissionSubject.ALL
                    addingPlayer = false
                    syncTargetTileSelection()
                    rebuildDetails()
                }
            }
        }.constrain {
            x = (2f + index * TARGET_STEP).pixels()
            y = 1.pixels()
            width = TARGET_SIZE.pixels()
            height = TARGET_SIZE.pixels()
        } childOf targetContent
        targetTiles[group.key] = icon
        BrassTooltip.attach(icon, targetDisplayName(group.key), group.key.targetId)
    }

    private fun syncTargetTileSelection() {
        targetTiles.forEach { (key, tile) ->
            val selected = key == selectedKey
            tile.selected = selected
            tile.accent = if (selected) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    private fun rebuildDetails() {
        playerControls.forEach(BrassTooltip::detach)
        playerControls.clear()
        playerSuggestions = null
        val previous = detailContent
        detailContent = UIContainer().constrain {
            y = 4.pixels()
            width = 100.percent()
            height = 100.percent() - 4.pixels()
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
                y = (-4).pixels()
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

        val subject = selectedSubject
        BrassLabel(
            "${displayTargetName(key.target)}  /  ${subject.displayName()}",
            Colors.UI_TEXT_DARK,
        ).constrain {
            x = 48.pixels()
            y = 16.pixels()
        } childOf detailContent

        if (!subject.isAll) {
            BrassButton("Remove player", BrassAccent.DANGER) {
                removeSubject(group, subject)
            }.constrain {
                x = 100.percent() - 154.pixels()
                y = 1.pixels()
                width = 84.pixels()
                height = 18.pixels()
            } childOf detailContent
        }

        BrassButton("Delete", BrassAccent.DANGER) {
            showTargetDeletionConfirmation(key)
        }.constrain {
            x = 100.percent() - 66.pixels()
            y = 1.pixels()
            width = 64.pixels()
            height = 18.pixels()
        } childOf detailContent

        val hasPlayers = addSubjectStrip(group, subject)
        val permissionsY = 46 + (if (hasPlayers) 36 else 0) + (if (addingPlayer) 24 else 0) +
                (if (hasPlayers || addingPlayer) 3 else 0)

        BrassLabel("PERMISSIONS", Colors.UI_TEXT_DARK).constrain {
            y = permissionsY.pixels()
        } childOf detailContent

        val target = parseTarget(key.target)
        val permissions = BrassFlow(
            gapX = 8f,
            gapY = 6f,
            itemHeight = 22f,
            stretch = true,
        ).constrain {
            y = (permissionsY + 14).pixels()
            width = 100.percent()
        } childOf detailContent

        supportedActions(target).forEach { action ->
            permissions.add(permissionCheckboxRow(group, subject, action), 132f)
        }
        permissions.constrain {
            height = basicHeightConstraint { permissions.contentHeight() }
        }
        playerSuggestions?.let { suggestions ->
            detailContent.removeChild(suggestions)
            detailContent.addChild(suggestions)
        }
        OpacEssentialsUiTheme.disableAnimations(detailContent)
    }

    private fun addSubjectStrip(group: TargetGroup, selected: PermissionSubject): Boolean {
        val subjects = buildList<PermissionSubject> {
            (group.subjects() + addedSubjects[group.key].orEmpty()).forEach { subject ->
                if (!subject.isAll && none { it.matches(subject) }) add(subject)
            }
        }.sortedBy { it.displayName().lowercase(Locale.ROOT) }

        if (subjects.isNotEmpty()) {
            val scroll = ScrollComponent(
                emptyString = "",
                horizontalScrollEnabled = true,
                verticalScrollEnabled = false,
            ).constrain {
                x = 2.pixels()
                y = 46.pixels()
                width = 100.percent() - 4.pixels()
                height = 32.pixels()
            } childOf detailContent
            val content = UIContainer().constrain {
                width = basicWidthConstraint { component ->
                    maxOf(component.parent.getWidth(), subjects.size * PLAYER_STEP)
                }
                height = 100.percent()
            } childOf scroll

            subjects.forEachIndexed { index, subject ->
                val head = BrassPlayerHead(subject.playerName, PLAYER_HEAD_SIZE, tooltip = false) {
                    selectSubject(if (subject.matches(selected)) PermissionSubject.ALL else subject)
                }.also {
                    it.accent = if (subject.matches(selected)) BrassAccent.BRASS else BrassAccent.DEFAULT
                    it.chrome = BrassChrome.KEYCAP
                    it.clickable = true
                    it.entranceEnabled = false
                }.constrain {
                    x = (2f + index * PLAYER_STEP).pixels()
                    y = 2.pixels()
                } childOf content
                BrassTooltip.attach(head, subject.displayName())
                playerControls.add(head)
            }
        }

        val add = BrassButton(if (addingPlayer) "-" else "+", BrassAccent.BRASS) {
            if (!savingChanges) {
                addingPlayer = !addingPlayer
                rebuildDetails()
            }
        }.constrain {
            x = 100.percent() - (if (selected.isAll) 88 else 176).pixels()
            y = 1.pixels()
            width = 18.pixels()
            height = 18.pixels()
        } childOf detailContent
        playerControls.add(add)

        if (addingPlayer) {
            val inputY = if (subjects.isEmpty()) 46 else 82
            val suggestions = UIContainer().constrain {
                x = 2.pixels()
                y = (inputY + 23).pixels()
                width = 100.percent() - 66.pixels()
                height = 0.pixels()
            } childOf detailContent
            playerSuggestions = suggestions
            val suggestionButtons = mutableListOf<BrassButton>()
            var inputHovered = false
            var suggestionsHovered = false
            lateinit var input: BrassTextInput

            fun dismissSuggestions() {
                suggestions.clearChildren()
                suggestions.constrain { height = 0.pixels() }
                playerControls.removeAll(suggestionButtons.toSet())
                suggestionButtons.clear()
            }

            fun refreshSuggestions(value: String) {
                dismissSuggestions()
                val query = value.substringAfterLast(',').trim().lowercase(Locale.ROOT)
                if (query.isEmpty() || savingChanges) return
                val selectedNames = value.substringBeforeLast(',', "")
                    .split(',')
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val names = Minecraft.getInstance().connection?.onlinePlayers
                    ?.map { it.profile.name }
                    ?.filter { it.lowercase(Locale.ROOT).startsWith(query) }
                    ?.filter { it.lowercase(Locale.ROOT) !in selectedNames }
                    ?.distinctBy { it.lowercase(Locale.ROOT) }
                    ?.take(MAX_PLAYER_SUGGESTIONS)
                    ?: emptyList()
                suggestions.constrain { height = (names.size * 14).pixels() }
                names.forEachIndexed { index, name ->
                    val button = BrassButton(name) {
                        if (!savingChanges) {
                            val prefix = input.text.substringBeforeLast(',', "").trim()
                            input.value = if (prefix.isEmpty()) name else "$prefix, $name"
                            dismissSuggestions()
                        }
                    }.also {
                        it.entranceEnabled = false
                    }.constrain {
                        y = (index * 14).pixels()
                        width = 100.percent()
                        height = 13.pixels()
                    } childOf suggestions
                    suggestionButtons.add(button)
                    playerControls.add(button)
                }
            }

            input = BrassTextInput(placeholder = "Player names, comma-separated") { value ->
                refreshSuggestions(value)
            }.constrain {
                x = 2.pixels()
                y = inputY.pixels()
                width = 100.percent() - 66.pixels()
                height = 19.pixels()
            } childOf detailContent
            input.onSubmit = { addSubjects(group.key, input.text) }
            input.onMouseEnter { inputHovered = true }
            input.onMouseLeave { inputHovered = false }
            suggestions.onMouseEnter { suggestionsHovered = true }
            suggestions.onMouseLeave { suggestionsHovered = false }
            input.onFocusLost {
                if (!suggestionsHovered) dismissSuggestions()
            }
            detailContent.onMouseClick {
                if (!inputHovered && !suggestionsHovered) {
                    dismissSuggestions()
                    input.loseFocus()
                }
            }
            val confirm = BrassButton("Add", BrassAccent.BRASS) {
                addSubjects(group.key, input.text)
            }.constrain {
                x = 2.pixels(true)
                y = inputY.pixels()
                width = 58.pixels()
                height = 19.pixels()
            } childOf detailContent
            playerControls.add(input)
            playerControls.add(confirm)
        }
        return subjects.isNotEmpty()
    }

    private fun selectSubject(subject: PermissionSubject) {
        if (savingChanges) return
        selectedSubject = subject
        addingPlayer = false
        rebuildDetails()
    }

    private fun addSubjects(key: TargetKey, value: String) {
        if (savingChanges) return
        val subjects = value.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(::permissionSubject)
            .filterNot(PermissionSubject::isAll)
        if (subjects.isEmpty()) return
        val players = addedSubjects.getOrPut(key) { mutableListOf() }
        subjects.forEach { subject ->
            if (players.none { it.matches(subject) }) players.add(subject)
        }
        selectSubject(subjects.first())
    }

    private fun removeSubject(group: TargetGroup, subject: PermissionSubject) {
        if (savingChanges) return
        if (group.entries.all { it.subject.matches(subject) }) {
            showTargetDeletionConfirmation(group.key)
            return
        }
        draftEntries.entries.removeIf { (_, entry) ->
            entry.targetRef() == group.key.toRef() && entry.subject.matches(subject)
        }
        addedSubjects[group.key]?.removeIf { it.matches(subject) }
        selectedSubject = PermissionSubject.ALL
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun addLargePreview(key: TargetKey) {
        val previewId = parseTarget(key.target).previewId(key.targetId)
        when (parseTarget(key.target)) {
            ClaimPermissionTarget.BLOCK,
            ClaimPermissionTarget.BLOCK_ENTITY -> BrassBlockPreview(previewId, spin = 12f, tooltip = true).constrain {
                width = 38.pixels()
                height = 36.pixels()
            } childOf detailContent

            ClaimPermissionTarget.ENTITY -> BrassEntity(previewId, spin = 12f, tooltip = true).constrain {
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

            ClaimPermissionTarget.THROWABLE -> BrassItem(previewId, tooltip = true).constrain {
                x = 4.pixels()
                y = 2.pixels()
                width = 30.pixels()
                height = 30.pixels()
            } childOf detailContent
        }
    }

    private fun permissionCheckboxRow(
        group: TargetGroup,
        subject: PermissionSubject,
        action: ClaimPermissionAction,
    ): UIContainer {
        val slot = PermissionSlot(group.key, action)
        val entry = permissionEntry(draftEntries.values, group.key.toRef(), action.name, subject)
        val inherited = !subject.isAll && entry?.subject?.isAll == true
        val row = UIContainer()
        val checkbox = BrassCheckbox(
            initial = entry?.allowed == true,
        ) { enabled ->
            setPermission(group.key, subject, action, enabled)
        }.also {
            it.active = !savingChanges
        }.constrain {
            x = 2.pixels()
            y = CenterConstraint()
            width = 14.pixels()
            height = 14.pixels()
        } childOf row
        permissionCheckboxes[slot] = checkbox

        val suffix = if (inherited) " · All" else ""
        BrassLabel("${displayActionName(action)}$suffix", Colors.UI_TEXT).constrain {
            x = 22.pixels()
            y = CenterConstraint()
        } childOf row
        return row
    }

    private fun setPermission(
        key: TargetKey,
        subject: PermissionSubject,
        action: ClaimPermissionAction,
        enabled: Boolean,
    ) {
        if (savingChanges) return
        val group = groupedTargets().firstOrNull { it.key == key } ?: return
        val actionEntries = group.entries.filter {
            it.action == action.name && it.subject.matches(subject)
        }
        if (!enabled && subject.isAll && actionEntries.size == group.entries.size
            && !inheritsGroupPermission(group.key.toRef(), subject, action)
        ) {
            permissionCheckboxes[PermissionSlot(key, action)]?.setSilently(true)
            showDeletionConfirmation(key, subject, action)
            return
        }
        applyActionChange(group, subject, action, enabled)
    }

    private fun inheritsGroupPermission(
        target: PermissionTargetRef,
        subject: PermissionSubject,
        action: ClaimPermissionAction,
    ): Boolean = permissionEntry(
        draftEntries.values.filterNot { it.targetRef() == target },
        target,
        action.name,
        subject,
    )?.allowed == true

    private fun applyActionChange(
        group: TargetGroup,
        subject: PermissionSubject,
        action: ClaimPermissionAction,
        enabled: Boolean,
    ) {
        if (enabled || !subject.isAll || inheritsGroupPermission(group.key.toRef(), subject, action)) {
            putDraft(
                PermissionDraftEntry(
                    group.key.target,
                    group.key.targetId,
                    action.name,
                    subject,
                    enabled,
                )
            )
        } else {
            draftEntries.entries.removeIf { (_, entry) ->
                entry.targetRef() == group.key.toRef() && entry.action == action.name &&
                        entry.subject.matches(subject)
            }
        }
        refreshTargets(selectFirst = false)
        refreshStatus()
    }

    private fun showDeletionConfirmation(
        key: TargetKey,
        subject: PermissionSubject,
        action: ClaimPermissionAction,
    ) {
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
                if (group != null) applyActionChange(group, subject, action, false)
                confirmation.dismiss()
            },
        ).show(background)
    }

    private fun showTargetDeletionConfirmation(key: TargetKey) {
        if (savingChanges) return
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
                selectedSubject = PermissionSubject.ALL
                addedSubjects.remove(key)
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
        selectedCategory = parseTarget(target.target)
        val key = TargetKey(target.target, target.targetId)
        selectedKey = key
        selectedSubject = PermissionSubject.ALL
        addingPlayer = false
        syncCategorySelection()
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
                    if (enabled || !subject.isAll || inheritsGroupPermission(target, subject, action)) {
                        putDraft(PermissionDraftEntry(target.target, target.targetId, action.name, subject, enabled))
                    } else {
                        draftEntries.entries.removeIf { (_, entry) ->
                            entry.targetRef() == target &&
                                    entry.action == action.name &&
                                    entry.subject.matches(subject)
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
        draftEntries.entries.removeIf { (_, existing) ->
            existing.targetRef() == normalizedEntry.targetRef() &&
                    existing.action == normalizedEntry.action &&
                    existing.subject.matches(normalizedEntry.subject)
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

        if (changes.size > ClaimPermissionsBatchPayload.MAX_TARGETS) {
            statusLabel.text = "Too many permission changes. Save smaller batches."
            statusLabel.tint = Colors.DANGER
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

    private fun requestClose(returnToMenu: Boolean = false) {
        returningToMenu = returnToMenu
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
        Minecraft.getInstance().setScreen(
            if (returningToMenu) OpacMainMenuScreen() else null,
        )
    }

    private fun setInteractive(active: Boolean) {
        addButton.active = active
        bulkButton.active = active
        doneButton.active = active
        targetTiles.values.forEach { it.active = active }
        playerControls.forEach { it.active = active }
        categorySwitch.children.filterIsInstance<BrassButton>().forEach { it.active = active }
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
            changeCount > 0 -> "$changeCount unsaved · $scope · ${draftEntries.size} rules / $targetCount targets"
            payload.status().isNotBlank() -> "${payload.status()} · $scope"
            else -> "$scope · ${draftEntries.size} rules / $targetCount targets"
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
        if ('*' in key.targetId) {
            "Group: ${key.targetId}"
        } else if (parseTarget(key.target) == ClaimPermissionTarget.TRAIN) {
            "Trains"
        } else {
            shortTargetName(key.targetId)
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
    }

    private data class PermissionSlot(
        val key: TargetKey,
        val action: ClaimPermissionAction,
    )

    companion object {
        private const val WINDOW_WIDTH = 600f
        private const val WINDOW_HEIGHT = 360f
        private const val MIN_WINDOW_WIDTH = 200f
        private const val MIN_WINDOW_HEIGHT = 120f
        private const val WINDOW_HORIZONTAL_MARGIN = 28f
        private const val WINDOW_VERTICAL_MARGIN = 24f
        private const val MIN_CONTENT_HEIGHT = 330f
        private const val TARGET_SIZE = 32f
        private const val TARGET_STEP = 38f
        private const val PLAYER_HEAD_SIZE = 24f
        private const val PLAYER_STEP = 32f
        private const val MAX_PLAYER_SUGGESTIONS = 3
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
