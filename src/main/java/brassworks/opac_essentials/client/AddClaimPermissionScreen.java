package brassworks.opac_essentials.client;

import brassworks.opac_essentials.network.ClaimPermissionMutationPayload;
import brassworks.opac_essentials.network.ClaimPermissionsNetwork;
import brassworks.opac_essentials.network.ClaimPermissionsSyncPayload;
import brassworks.opac_essentials.permission.ClaimPermissionAction;
import brassworks.opac_essentials.permission.ClaimPermissionTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class AddClaimPermissionScreen extends Screen {
    private static final int PANEL_HEIGHT = 302;
    private static final int SUGGESTION_HEIGHT = 12;
    private static final int MAX_SUGGESTIONS = 10;

    private final Screen parent;
    private final ClaimPermissionsSyncPayload payload;
    private final EnumSet<ClaimPermissionAction> selectedActions =
            EnumSet.of(ClaimPermissionAction.INTERACT);
    private final EnumMap<ClaimPermissionAction, Button> actionButtons =
            new EnumMap<>(ClaimPermissionAction.class);

    private ClaimPermissionTarget target = ClaimPermissionTarget.BLOCK;
    private EditBox targetIdBox;
    private EditBox playerBox;
    private Button targetButton;
    private Button allActionsButton;
    private List<String> targetSuggestions = List.of();
    private List<String> playerSuggestions = List.of();
    private String validationMessage = "";
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int fieldLeft;
    private int fieldWidth;

    public AddClaimPermissionScreen(
            Screen parent,
            ClaimPermissionsSyncPayload payload
    ) {
        super(Component.translatable(
                "screen.opac_essentials.claim_permissions.add_title"
        ));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        super.init();

        ClaimPermissionTarget.prepareRegisteredIds();

        this.panelWidth = Math.min(340, this.width - 32);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = Math.max(
                4,
                (this.height - PANEL_HEIGHT) / 2
        );
        this.fieldLeft = this.panelLeft + 14;
        this.fieldWidth = this.panelWidth - 28;

        this.targetButton = this.addRenderableWidget(
                Button.builder(
                                Component.empty(),
                                button -> cycleTarget()
                        )
                        .bounds(
                                this.fieldLeft,
                                this.panelTop + 38,
                                this.fieldWidth,
                                20
                        )
                        .build()
        );

        this.targetIdBox = this.addRenderableWidget(
                new EditBox(
                        this.font,
                        this.fieldLeft,
                        this.panelTop + 82,
                        this.fieldWidth,
                        20,
                        Component.translatable(
                                "screen.opac_essentials.claim_permissions.target_id"
                        )
                )
        );

        this.targetIdBox.setMaxLength(256);
        this.targetIdBox.setHint(Component.literal(""));
        this.targetIdBox.setResponder(
                value -> updateTargetSuggestions()
        );
        this.targetIdBox.setValue("");

        updateTargetSuggestions();

        int actionGap = 4;
        int actionWidth =
                (this.fieldWidth - actionGap * 3) / 4;

        this.allActionsButton = this.addRenderableWidget(
                Button.builder(
                                Component.empty(),
                                button -> toggleAllActions()
                        )
                        .bounds(
                                this.fieldLeft,
                                this.panelTop + 155,
                                actionWidth,
                                20
                        )
                        .build()
        );

        for (ClaimPermissionAction permissionAction
                : ClaimPermissionAction.values()) {
            Button button = this.addRenderableWidget(
                    Button.builder(
                                    Component.empty(),
                                    ignored -> toggleAction(
                                            permissionAction
                                    )
                            )
                            .bounds(
                                    this.fieldLeft,
                                    this.panelTop + 155,
                                    actionWidth,
                                    20
                            )
                            .build()
            );

            this.actionButtons.put(
                    permissionAction,
                    button
            );
        }

        this.playerBox = this.addRenderableWidget(
                new EditBox(
                        this.font,
                        this.fieldLeft,
                        this.panelTop + 198,
                        this.fieldWidth,
                        20,
                        Component.translatable(
                                "screen.opac_essentials.claim_permissions.player"
                        )
                )
        );

        this.playerBox.setMaxLength(128);
        this.playerBox.setResponder(
                value -> updatePlayerSuggestions()
        );
        this.playerBox.setValue("all");

        updatePlayerSuggestions();

        int halfWidth = (this.fieldWidth - 6) / 2;

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.cancel"),
                                button -> this.onClose()
                        )
                        .bounds(
                                this.fieldLeft,
                                this.panelTop + 264,
                                halfWidth,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "screen.opac_essentials.claim_permissions.add"
                                ),
                                button -> submit()
                        )
                        .bounds(
                                this.fieldLeft + halfWidth + 6,
                                this.panelTop + 264,
                                halfWidth,
                                20
                        )
                        .build()
        );

        updateButtons();
    }

    private void cycleTarget() {
        ClaimPermissionTarget[] targets =
                ClaimPermissionTarget.values();

        this.target = targets[
                (this.target.ordinal() + 1) % targets.length
                ];

        List<ClaimPermissionAction> supported =
                ClaimPermissionsScreen.supportedActions(
                        this.target
                );

        this.selectedActions.retainAll(supported);

        if (this.selectedActions.isEmpty()) {
            this.selectedActions.add(
                    supported.getFirst()
            );
        }

        ResourceLocation current =
                ResourceLocation.tryParse(
                        this.targetIdBox
                                .getValue()
                                .trim()
                );

        if (current == null
                || !this.target.isRegistered(current)) {
            this.targetIdBox.setValue("");
        }

        updateTargetSuggestions();
        updateButtons();
    }

    private void toggleAction(
            ClaimPermissionAction permissionAction
    ) {
        if (!this.target.supports(permissionAction)) {
            return;
        }

        if (!this.selectedActions.remove(
                permissionAction
        )) {
            this.selectedActions.add(
                    permissionAction
            );
        }

        updateButtons();
    }

    private void toggleAllActions() {
        List<ClaimPermissionAction> supported =
                ClaimPermissionsScreen.supportedActions(
                        this.target
                );

        if (this.selectedActions.containsAll(
                supported
        )) {
            this.selectedActions.removeAll(
                    supported
            );
        } else {
            this.selectedActions.addAll(
                    supported
            );
        }

        updateButtons();
    }

    private void updateButtons() {
        this.targetButton.setMessage(
                Component.literal(
                        ClaimPermissionsScreen.displayName(
                                this.target.name()
                        )
                )
        );

        List<ClaimPermissionAction> supported =
                ClaimPermissionsScreen.supportedActions(
                        this.target
                );

        boolean allSelected =
                this.selectedActions.containsAll(
                        supported
                );

        this.allActionsButton.setMessage(
                Component.literal(
                        selectionPrefix(allSelected) + "All"
                )
        );

        int actionGap = 4;
        int actionWidth =
                (this.fieldWidth - actionGap * 3) / 4;
        int visibleIndex = 1;

        for (ClaimPermissionAction permissionAction
                : ClaimPermissionAction.values()) {
            Button button =
                    this.actionButtons.get(
                            permissionAction
                    );

            button.visible =
                    supported.contains(
                            permissionAction
                    );

            if (!button.visible) {
                continue;
            }

            button.setX(
                    this.fieldLeft
                            + visibleIndex
                            * (actionWidth + actionGap)
            );

            button.setMessage(
                    Component.literal(
                            selectionPrefix(
                                    this.selectedActions.contains(
                                            permissionAction
                                    )
                            )
                                    + ClaimPermissionsScreen.displayName(
                                    permissionAction.name()
                            )
                    )
            );

            visibleIndex++;
        }
    }

    private static String selectionPrefix(
            boolean selected
    ) {
        return selected ? "[x] " : "[ ] ";
    }

    private void updateTargetSuggestions() {
        String query = this.targetIdBox
                .getValue()
                .trim()
                .toLowerCase(Locale.ROOT);

        this.targetSuggestions =
                this.target.registeredIds()
                        .map(ResourceLocation::toString)
                        .filter(id ->
                                matchesRegistryId(
                                        id,
                                        query
                                )
                        )
                        .filter(id ->
                                !id.equalsIgnoreCase(
                                        query
                                )
                        )
                        .sorted(
                                Comparator
                                        .comparingInt(
                                                (String id) ->
                                                        matchRank(
                                                                id,
                                                                query
                                                        )
                                        )
                                        .thenComparing(
                                                Comparator.naturalOrder()
                                        )
                        )
                        .limit(MAX_SUGGESTIONS)
                        .toList();

        updateInlineSuggestion(
                this.targetIdBox,
                this.targetSuggestions
        );
    }

    private static boolean matchesRegistryId(
            String id,
            String query
    ) {
        if (query.isEmpty()) {
            return true;
        }

        int separator = id.indexOf(':');
        String path = separator < 0
                ? id
                : id.substring(separator + 1);

        return id.startsWith(query)
                || path.startsWith(query)
                || id.contains(query);
    }

    private static int matchRank(
            String id,
            String query
    ) {
        if (id.startsWith(query)) {
            return 0;
        }

        int separator = id.indexOf(':');
        String path = separator < 0
                ? id
                : id.substring(separator + 1);

        return path.startsWith(query)
                ? 1
                : 2;
    }

    private void updatePlayerSuggestions() {
        Set<String> playerNames =
                new TreeSet<>(
                        String.CASE_INSENSITIVE_ORDER
                );

        playerNames.add("all");

        if (this.minecraft.getConnection() != null) {
            this.minecraft
                    .getConnection()
                    .getOnlinePlayers()
                    .forEach(
                            playerInfo ->
                                    playerNames.add(
                                            playerInfo
                                                    .getProfile()
                                                    .getName()
                                    )
                    );
        }

        String query = this.playerBox
                .getValue()
                .trim()
                .toLowerCase(Locale.ROOT);

        this.playerSuggestions =
                playerNames.stream()
                        .filter(name ->
                                name.toLowerCase(
                                                Locale.ROOT
                                        )
                                        .startsWith(query)
                        )
                        .filter(name ->
                                !name.equalsIgnoreCase(
                                        query
                                )
                        )
                        .limit(MAX_SUGGESTIONS)
                        .toList();

        updateInlineSuggestion(
                this.playerBox,
                this.playerSuggestions
        );
    }

    private static void updateInlineSuggestion(
            EditBox box,
            List<String> suggestions
    ) {
        String value = box.getValue();

        if (!suggestions.isEmpty()
                && suggestions
                .getFirst()
                .regionMatches(
                        true,
                        0,
                        value,
                        0,
                        value.length()
                )) {
            box.setSuggestion(
                    suggestions
                            .getFirst()
                            .substring(
                                    value.length()
                            )
            );
        } else {
            box.setSuggestion(null);
        }
    }

    private void submit() {
        ResourceLocation targetId =
                ResourceLocation.tryParse(
                        this.targetIdBox
                                .getValue()
                                .trim()
                );

        if (targetId == null) {
            this.validationMessage =
                    "Invalid registry ID.";
            return;
        }

        if (!this.target.isRegistered(targetId)) {
            this.validationMessage =
                    "That registry ID does not exist.";
            return;
        }

        List<ClaimPermissionAction> actions =
                ClaimPermissionsScreen
                        .supportedActions(this.target)
                        .stream()
                        .filter(
                                this.selectedActions::contains
                        )
                        .toList();

        if (actions.isEmpty()) {
            this.validationMessage =
                    "Select at least one action.";
            return;
        }

        String playerName =
                this.playerBox
                        .getValue()
                        .trim();

        if (playerName.isEmpty()) {
            playerName = "all";
        }

        String actionNames =
                actions.stream()
                        .map(Enum::name)
                        .collect(
                                Collectors.joining(",")
                        );

        ClaimPermissionsNetwork.sendToServer(
                new ClaimPermissionMutationPayload(
                        ClaimPermissionMutationPayload.Operation.ADD,
                        this.payload.claimOwner(),
                        this.payload.subConfigIndex(),
                        this.target.name(),
                        targetId.toString(),
                        actionNames,
                        "",
                        "",
                        playerName
                )
        );

        this.validationMessage =
                actions.size() == 1
                        ? "Saving permission..."
                        : "Saving "
                          + actions.size()
                          + " permissions...";
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (acceptFirstSuggestion(
                    this.targetIdBox,
                    this.targetSuggestions
            )) {
                return true;
            }

            if (acceptFirstSuggestion(
                    this.playerBox,
                    this.playerSuggestions
            )) {
                return true;
            }
        }

        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers
        );
    }

    private static boolean acceptFirstSuggestion(
            EditBox box,
            List<String> suggestions
    ) {
        if (box.isFocused()
                && !suggestions.isEmpty()) {
            box.setValue(
                    suggestions.getFirst()
            );
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == 0) {
            if (acceptClickedSuggestion(
                    this.targetIdBox,
                    this.targetSuggestions,
                    this.panelTop + 104,
                    mouseX,
                    mouseY
            )) {
                return true;
            }

            if (acceptClickedSuggestion(
                    this.playerBox,
                    this.playerSuggestions,
                    this.panelTop + 220,
                    mouseX,
                    mouseY
            )) {
                return true;
            }
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private boolean acceptClickedSuggestion(
            EditBox box,
            List<String> suggestions,
            int top,
            double mouseX,
            double mouseY
    ) {
        if (!box.isFocused()
                || mouseX < this.fieldLeft
                || mouseX >= this.fieldLeft
                + this.fieldWidth
                || mouseY < top) {
            return false;
        }

        int index = (int) (
                (mouseY - top)
                        / SUGGESTION_HEIGHT
        );

        if (index < 0
                || index >= suggestions.size()) {
            return false;
        }

        box.setValue(
                suggestions.get(index)
        );
        box.setFocused(true);

        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(
                this.parent
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        if (this.targetIdBox.isFocused()) {
            renderSuggestions(
                    graphics,
                    this.targetSuggestions,
                    this.panelTop + 104,
                    mouseX,
                    mouseY
            );
        }

        if (this.playerBox.isFocused()) {
            renderSuggestions(
                    graphics,
                    this.playerSuggestions,
                    this.panelTop + 220,
                    mouseX,
                    mouseY
            );
        }
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.renderBackground(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        graphics.fill(
                this.panelLeft,
                this.panelTop,
                this.panelLeft + this.panelWidth,
                this.panelTop + PANEL_HEIGHT,
                0xE0101010
        );

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                this.panelTop + 10,
                0xFFFFFF
        );

        drawLabel(
                graphics,
                "screen.opac_essentials.claim_permissions.category",
                27
        );

        drawLabel(
                graphics,
                "screen.opac_essentials.claim_permissions.target_id",
                70
        );

        drawLabel(
                graphics,
                "screen.opac_essentials.claim_permissions.action",
                143
        );

        drawLabel(
                graphics,
                "screen.opac_essentials.claim_permissions.player",
                186
        );

        if (!this.validationMessage.isBlank()) {
            graphics.drawCenteredString(
                    this.font,
                    this.validationMessage,
                    this.width / 2,
                    this.panelTop + 289,
                    0xFFAA55
            );
        }
    }

    private void drawLabel(
            GuiGraphics graphics,
            String translationKey,
            int yOffset
    ) {
        graphics.drawString(
                this.font,
                Component.translatable(
                        translationKey
                ),
                this.fieldLeft,
                this.panelTop + yOffset,
                0xA0A0A0,
                false
        );
    }

    private void renderSuggestions(
            GuiGraphics graphics,
            List<String> suggestions,
            int top,
            int mouseX,
            int mouseY
    ) {
        for (int index = 0;
             index < suggestions.size();
             index++) {
            int y = top
                    + index
                    * SUGGESTION_HEIGHT;

            boolean hovered =
                    mouseX >= this.fieldLeft
                            && mouseX
                            < this.fieldLeft
                            + this.fieldWidth
                            && mouseY >= y
                            && mouseY
                            < y
                            + SUGGESTION_HEIGHT;

            graphics.fill(
                    this.fieldLeft,
                    y,
                    this.fieldLeft + this.fieldWidth,
                    y + SUGGESTION_HEIGHT,
                    hovered
                            ? 0xF0404040
                            : 0xF0181818
            );

            String suggestion =
                    this.font.plainSubstrByWidth(
                            suggestions.get(index),
                            this.fieldWidth - 8
                    );

            graphics.drawString(
                    this.font,
                    suggestion,
                    this.fieldLeft + 4,
                    y + 2,
                    0xFFFFFF,
                    false
            );
        }
    }
}