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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ClaimPermissionsScreen extends Screen {
    private static final int ROW_HEIGHT = 38;
    private static final int ACTION_WIDTH = 92;
    private static final int REMOVE_WIDTH = 20;

    private final ClaimPermissionsSyncPayload payload;
    private final List<Button> actionButtons = new ArrayList<>();
    private final List<Button> removeButtons = new ArrayList<>();
    private List<ClaimPermissionsSyncPayload.Entry> filteredEntries;
    private EditBox filterBox;
    private int panelLeft;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int visibleRows;
    private int firstVisible;

    public ClaimPermissionsScreen(ClaimPermissionsSyncPayload payload) {
        super(Component.translatable(
                "screen.opac_essentials.claim_permissions.title"
        ));
        this.payload = payload;
        this.filteredEntries = payload.entries();
    }

    @Override
    protected void init() {
        super.init();

        this.actionButtons.clear();
        this.removeButtons.clear();

        this.panelWidth = Math.min(
                560,
                Math.max(280, this.width - 32)
        );
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.listTop = 42;
        this.listBottom = Math.max(
                this.listTop + ROW_HEIGHT,
                this.height - 48
        );
        this.visibleRows = Math.max(
                1,
                (this.listBottom - this.listTop) / ROW_HEIGHT
        );
        this.firstVisible = Math.min(
                this.firstVisible,
                maxFirstVisible()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.done"),
                                button -> this.onClose()
                        )
                        .bounds(
                                this.panelLeft + this.panelWidth - 72,
                                10,
                                68,
                                20
                        )
                        .build()
        );

        this.filterBox = this.addRenderableWidget(
                new EditBox(
                        this.font,
                        this.panelLeft + 6,
                        this.height - 34,
                        Math.max(100, this.panelWidth - 92),
                        20,
                        Component.translatable(
                                "screen.opac_essentials.claim_permissions.filter"
                        )
                )
        );

        this.filterBox.setHint(
                Component.translatable(
                        "screen.opac_essentials.claim_permissions.filter"
                )
        );
        this.filterBox.setResponder(this::filter);

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "screen.opac_essentials.claim_permissions.add"
                                ),
                                button -> this.minecraft.setScreen(
                                        new AddClaimPermissionScreen(
                                                this,
                                                this.payload
                                        )
                                )
                        )
                        .bounds(
                                this.panelLeft + this.panelWidth - 80,
                                this.height - 34,
                                74,
                                20
                        )
                        .build()
        );

        for (int slot = 0; slot < this.visibleRows; slot++) {
            final int rowSlot = slot;
            int rowY = this.listTop + slot * ROW_HEIGHT + 8;

            Button actionButton = this.addRenderableWidget(
                    Button.builder(
                                    Component.empty(),
                                    button -> cycleAction(rowSlot)
                            )
                            .bounds(
                                    actionLeft(),
                                    rowY,
                                    ACTION_WIDTH,
                                    20
                            )
                            .build()
            );

            Button removeButton = this.addRenderableWidget(
                    Button.builder(
                                    Component.literal("×"),
                                    button -> remove(rowSlot)
                            )
                            .bounds(
                                    removeLeft(),
                                    rowY,
                                    REMOVE_WIDTH,
                                    20
                            )
                            .build()
            );

            this.actionButtons.add(actionButton);
            this.removeButtons.add(removeButton);
        }

        updateRowButtons();
    }

    private static final int BUTTON_GAP = 4;
    private static final int RIGHT_PADDING = 8;

    private int removeLeft() {
        return this.panelLeft + this.panelWidth
                - REMOVE_WIDTH
                - RIGHT_PADDING;
    }

    private int actionLeft() {
        return removeLeft()
                - BUTTON_GAP
                - ACTION_WIDTH;
    }

    private void filter(String query) {
        String needle = query
                .trim()
                .toLowerCase(Locale.ROOT);

        this.filteredEntries = needle.isEmpty()
                ? this.payload.entries()
                : this.payload.entries()
                .stream()
                .filter(entry ->
                        entry.targetId()
                                .toLowerCase(Locale.ROOT)
                                .contains(needle)
                        || entry.playerName()
                                .toLowerCase(Locale.ROOT)
                                .contains(needle)
                        || entry.target()
                                .toLowerCase(Locale.ROOT)
                                .contains(needle)
                        || entry.action()
                                .toLowerCase(Locale.ROOT)
                                .contains(needle)
                )
                .toList();

        this.firstVisible = 0;
        updateRowButtons();
    }

    private void updateRowButtons() {
        for (int slot = 0;
             slot < this.actionButtons.size();
             slot++) {
            int entryIndex = this.firstVisible + slot;
            boolean present = entryIndex < this.filteredEntries.size();

            Button actionButton = this.actionButtons.get(slot);
            Button removeButton = this.removeButtons.get(slot);

            actionButton.visible = present;
            removeButton.visible = present;

            if (!present) {
                continue;
            }

            ClaimPermissionsSyncPayload.Entry entry =
                    this.filteredEntries.get(entryIndex);

            ClaimPermissionTarget target =
                    parseTarget(entry.target());

            List<ClaimPermissionAction> actions =
                    supportedActions(target);

            actionButton.setMessage(
                    Component.literal(
                            displayName(entry.action())
                    )
            );
            actionButton.active = actions.size() > 1;
            removeButton.active = true;
        }
    }

    private void cycleAction(int rowSlot) {
        ClaimPermissionsSyncPayload.Entry entry =
                entryAt(rowSlot);

        if (entry == null) {
            return;
        }

        ClaimPermissionTarget target =
                parseTarget(entry.target());

        ClaimPermissionAction current =
                parseAction(entry.action());

        List<ClaimPermissionAction> actions =
                supportedActions(target);

        int currentIndex = actions.indexOf(current);

        if (actions.size() < 2 || currentIndex < 0) {
            return;
        }

        ClaimPermissionAction replacement = actions.get(
                (currentIndex + 1) % actions.size()
        );

        ClaimPermissionsNetwork.sendToServer(
                new ClaimPermissionMutationPayload(
                        ClaimPermissionMutationPayload.Operation.CHANGE_ACTION,
                        this.payload.claimOwner(),
                        this.payload.subConfigIndex(),
                        entry.target(),
                        entry.targetId(),
                        entry.action(),
                        replacement.name(),
                        entry.playerId(),
                        ""
                )
        );
    }

    private void remove(int rowSlot) {
        ClaimPermissionsSyncPayload.Entry entry =
                entryAt(rowSlot);

        if (entry == null) {
            return;
        }

        ClaimPermissionsNetwork.sendToServer(
                new ClaimPermissionMutationPayload(
                        ClaimPermissionMutationPayload.Operation.REMOVE,
                        this.payload.claimOwner(),
                        this.payload.subConfigIndex(),
                        entry.target(),
                        entry.targetId(),
                        entry.action(),
                        "",
                        entry.playerId(),
                        ""
                )
        );
    }

    private ClaimPermissionsSyncPayload.Entry entryAt(int rowSlot) {
        int index = this.firstVisible + rowSlot;

        return index >= 0
                && index < this.filteredEntries.size()
                ? this.filteredEntries.get(index)
                : null;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (mouseX >= this.panelLeft
                && mouseX <= this.panelLeft + this.panelWidth
                && mouseY >= this.listTop
                && mouseY <= this.listBottom) {
            int previous = this.firstVisible;

            if (scrollY < 0.0D) {
                this.firstVisible = Math.min(
                        maxFirstVisible(),
                        this.firstVisible + 1
                );
            } else if (scrollY > 0.0D) {
                this.firstVisible = Math.max(
                        0,
                        this.firstVisible - 1
                );
            }

            if (previous != this.firstVisible) {
                updateRowButtons();
                return true;
            }
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    private int maxFirstVisible() {
        return Math.max(
                0,
                this.filteredEntries.size() - this.visibleRows
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
                4,
                this.panelLeft + this.panelWidth,
                this.height - 8,
                0xB0101010
        );

        graphics.drawCenteredString(
                this.font,
                Component.translatable(
                        "screen.opac_essentials.claim_permissions.title"
                ),
                this.width / 2,
                13,
                0xFFFFFF
        );

        graphics.drawString(
                this.font,
                this.payload.scopeName(),
                this.panelLeft + 8,
                28,
                0xA0A0A0,
                false
        );

        graphics.hLine(
                this.panelLeft + 4,
                this.panelLeft + this.panelWidth - 5,
                this.listTop - 4,
                0xFF808080
        );

        for (int slot = 0;
             slot < this.visibleRows;
             slot++) {
            ClaimPermissionsSyncPayload.Entry entry =
                    entryAt(slot);

            if (entry == null) {
                break;
            }

            renderRow(
                    graphics,
                    entry,
                    slot,
                    mouseX,
                    mouseY
            );
        }

        renderScrollbar(graphics);

        if (this.filteredEntries.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable(
                            "screen.opac_essentials.claim_permissions.empty"
                    ),
                    this.width / 2,
                    this.listTop + 16,
                    0xA0A0A0
            );
        }

        if (!this.payload.status().isBlank()) {
            graphics.drawCenteredString(
                    this.font,
                    this.payload.status(),
                    this.width / 2,
                    this.height - 45,
                    this.payload.error()
                            ? 0xFF5555
                            : 0x55FF55
            );
        }
    }

    private void renderRow(
            GuiGraphics graphics,
            ClaimPermissionsSyncPayload.Entry entry,
            int slot,
            int mouseX,
            int mouseY
    ) {
        int y = this.listTop + slot * ROW_HEIGHT;

        boolean hovered =
                mouseX >= this.panelLeft + 4
                        && mouseX
                        <= this.panelLeft + this.panelWidth - 4
                        && mouseY >= y
                        && mouseY < y + ROW_HEIGHT;

        graphics.fill(
                this.panelLeft + 4,
                y,
                this.panelLeft + this.panelWidth - 4,
                y + ROW_HEIGHT - 2,
                hovered
                        ? 0x80383838
                        : 0x60202020
        );

        ItemStack icon = iconFor(entry);

        graphics.renderItem(
                icon,
                this.panelLeft + 10,
                y + 10
        );

        int textX = this.panelLeft + 34;
        int maxTextWidth = Math.max(
                40,
                actionLeft() - textX - 6
        );

        String targetId = this.font.plainSubstrByWidth(
                entry.targetId(),
                maxTextWidth
        );

        String details =
                displayName(entry.target())
                        + " · "
                        + entry.playerName();

        details = this.font.plainSubstrByWidth(
                details,
                maxTextWidth
        );

        graphics.drawString(
                this.font,
                targetId,
                textX,
                y + 7,
                0xFFFFFF,
                false
        );

        graphics.drawString(
                this.font,
                details,
                textX,
                y + 20,
                0xA0A0A0,
                false
        );
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (this.filteredEntries.size() <= this.visibleRows) {
            return;
        }

        int trackTop = this.listTop;
        int trackBottom = this.listTop
                + this.visibleRows * ROW_HEIGHT
                - 2;
        int trackHeight = trackBottom - trackTop;

        int thumbHeight = Math.max(
                18,
                trackHeight
                        * this.visibleRows
                        / this.filteredEntries.size()
        );

        int travel = trackHeight - thumbHeight;

        int thumbTop = trackTop
                + travel
                * this.firstVisible
                / maxFirstVisible();

        int x = this.panelLeft + this.panelWidth - 4;

        graphics.fill(
                x,
                trackTop,
                x + 2,
                trackBottom,
                0xFF303030
        );

        graphics.fill(
                x,
                thumbTop,
                x + 2,
                thumbTop + thumbHeight,
                0xFFC0C0C0
        );
    }

    private ItemStack iconFor(
            ClaimPermissionsSyncPayload.Entry entry
    ) {
        ResourceLocation id =
                ResourceLocation.tryParse(entry.targetId());

        ClaimPermissionTarget target =
                parseTarget(entry.target());

        if (id != null
                && target == ClaimPermissionTarget.BLOCK
                && BuiltInRegistries.BLOCK.containsKey(id)) {
            Item item =
                    BuiltInRegistries.BLOCK.get(id).asItem();

            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }

        if (id != null
                && target == ClaimPermissionTarget.THROWABLE
                && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);

            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }

        return switch (target) {
            case BLOCK -> new ItemStack(Items.STONE);
            case ENTITY -> new ItemStack(Items.ARMOR_STAND);
            case THROWABLE -> new ItemStack(Items.SNOWBALL);
            case BLOCK_ENTITY -> new ItemStack(Items.CHEST);
        };
    }

    static List<ClaimPermissionAction> supportedActions(
            ClaimPermissionTarget target
    ) {
        return Arrays.stream(ClaimPermissionAction.values())
                .filter(target::supports)
                .toList();
    }

    static ClaimPermissionTarget parseTarget(String name) {
        try {
            return ClaimPermissionTarget.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return ClaimPermissionTarget.BLOCK;
        }
    }

    static ClaimPermissionAction parseAction(String name) {
        try {
            return ClaimPermissionAction.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return ClaimPermissionAction.INTERACT;
        }
    }

    static String displayName(String enumName) {
        String lower = enumName
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');

        if (lower.isEmpty()) {
            return lower;
        }

        return Character.toUpperCase(lower.charAt(0))
                + lower.substring(1);
    }
}