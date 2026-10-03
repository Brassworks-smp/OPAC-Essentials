package brassworks.opac_essentials.party.menu.client;

import brassworks.opac_essentials.client.ResponsiveBrassScreen;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork;
import brassworks.opac_essentials.claims.permission.client.OpacEssentialsUiTheme;
import brassworks.opac_essentials.party.menu.network.PartyMenuActionPayload;
import brassworks.opac_essentials.party.menu.network.PartyMenuStatePayload;
import gg.essential.elementa.UIComponent;
import gg.essential.elementa.components.UIContainer;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.PixelConstraint;
import gg.essential.elementa.constraints.RelativeConstraint;
import gg.essential.elementa.constraints.SubtractiveConstraint;
import gg.essential.universal.UMatrixStack;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.Minecraft;
import net.swzo.brass.ui.BrassThemes;
import net.swzo.brass.ui.Colors;
import net.swzo.brass.ui.kit.base.BrassAccent;
import net.swzo.brass.ui.kit.base.BrassChrome;
import net.swzo.brass.ui.kit.input.BrassButton;
import net.swzo.brass.ui.kit.input.BrassSearchField;
import net.swzo.brass.ui.kit.input.BrassSquareButton;
import net.swzo.brass.ui.kit.input.BrassToggle;
import net.swzo.brass.ui.kit.layout.BrassScrollArea;
import net.swzo.brass.ui.kit.media.BrassIcons;
import net.swzo.brass.ui.kit.media.BrassPlayerHead;
import net.swzo.brass.ui.kit.surface.BrassModal;
import net.swzo.brass.ui.kit.surface.BrassPanel;
import net.swzo.brass.ui.kit.surface.BrassTooltip;
import net.swzo.brass.ui.kit.surface.BrassWindow;
import net.swzo.brass.ui.kit.text.BrassLabel;
import net.swzo.brass.ui.kit.text.BrassTextInput;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PartyManagementScreen extends ResponsiveBrassScreen {
    private static final int FRAME_WIDTH = 520;
    private static final int FRAME_HEIGHT = 320;
    private static final int FRAME_HORIZONTAL_MARGIN = 28;
    private static final int FRAME_VERTICAL_MARGIN = 24;
    private static final int MIN_FRAME_WIDTH = 330;
    private static final int MIN_FRAME_HEIGHT = 230;
    private static final int HEAD_SIZE = 24;
    private static final long REFRESH_INTERVAL = 5000L;

    private PartyMenuStatePayload snapshot;
    private BrassTextInput partyNameInput;
    private BrassToggle chatToggle;
    private BrassLabel statusLabel;
    private BrassLabel summaryLabel;
    private BrassButton partyActionButton;
    private BrassScrollArea memberScroll;
    private BrassScrollArea playerScroll;
    private final List<UIComponent> renderedHeads = new ArrayList<>();
    private final Map<UUID, BrassPlayerHead> memberHeads = new HashMap<>();
    private List<UUID> visibleMemberIds = List.of();
    private UUID selectedMemberId;
    private BrassButton promoteButton;
    private BrassButton demoteButton;
    private BrassButton transferButton;
    private BrassButton removeButton;
    private String query = "";
    private String pendingQuery = "";
    private String previousThemeId;
    private String previousAccentHex;
    private boolean themeApplied;
    private long nextRefreshAt;
    private int renderedMemberColumns;
    private int renderedPlayerColumns;

    public PartyManagementScreen(PartyMenuStatePayload snapshot) {
        super(
                FRAME_WIDTH,
                FRAME_HEIGHT,
                FRAME_HORIZONTAL_MARGIN,
                FRAME_VERTICAL_MARGIN,
                new Color(0, 0, 0, 72)
        );
        this.snapshot = snapshot;
        this.selectedMemberId = ownerId(snapshot);
    }

    @Override
    public void afterInitialization() {
        super.afterInitialization();
        if (!themeApplied) {
            previousThemeId = BrassThemes.INSTANCE.getCurrentId();
            previousAccentHex = BrassThemes.INSTANCE.getAccentHex();
            themeApplied = true;
        }
        OpacEssentialsUiTheme.INSTANCE.applyParty();

        if (!snapshot.hasParty()) {
            showCreationPrompt();
            return;
        }

        BrassWindow frame = new BrassWindow(
                "OPAC",
                "parties",
                callback(this::closeScreen),
                21,
                false,
                (float) MIN_FRAME_WIDTH,
                (float) MIN_FRAME_HEIGHT
        );
        frame.setX(new CenterConstraint());
        frame.setY(new CenterConstraint());
        frame.setWidth(responsiveWindowWidthConstraint());
        frame.setHeight(responsiveWindowHeightConstraint());
        frame.setChildOf(getBackground());

        BrassSquareButton close = new BrassSquareButton(
                BrassIcons.INSTANCE.getNONE(),
                BrassAccent.Companion.getDANGER(),
                null,
                callback(frame::requestClose)
        );
        close.setEntranceEnabled(false);
        close.setX(new PixelConstraint(7f, true));
        close.setY(new PixelConstraint(4f));
        close.setWidth(new PixelConstraint(18f));
        close.setHeight(new PixelConstraint(10f));
        close.setChildOf(frame);
        BrassTooltip.INSTANCE.attach(
                close,
                "Close",
                null,
                BrassAccent.Companion.getDANGER(),
                true,
                null
        );

        buildPartyContent(frame.getContent());
        nextRefreshAt = System.currentTimeMillis() + REFRESH_INTERVAL;
    }

    @Override
    public void onDrawScreen(UMatrixStack matrixStack, int mouseX,
                             int mouseY, float partialTicks) {
        if (snapshot.hasParty()) {
            boolean rebuild = false;
            if (!pendingQuery.equals(query)) {
                query = pendingQuery;
                rebuild = true;
            }
            if (headsPerRow(memberScroll) != renderedMemberColumns
                    || headsPerRow(playerScroll) != renderedPlayerColumns) {
                rebuild = true;
            }
            if (rebuild) {
                rebuildHeads();
            }
        }
        long now = System.currentTimeMillis();
        if (now >= nextRefreshAt) {
            nextRefreshAt = now + REFRESH_INTERVAL;
            send(PartyMenuActionPayload.Action.REFRESH, null, "");
        }
        super.onDrawScreen(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onScreenClose() {
        detachHeadTooltips();
        super.onScreenClose();
        if (themeApplied) {
            BrassThemes.INSTANCE.apply(previousThemeId, previousAccentHex);
        }
    }

    public void applySnapshot(PartyMenuStatePayload next) {
        if (snapshot.hasParty() != next.hasParty()) {
            if (next.hasParty()) {
                Minecraft.getInstance().setScreen(
                        new PartyManagementScreen(next)
                );
            } else {
                closeScreen();
            }
            return;
        }
        boolean headsChanged = !snapshot.members().equals(next.members())
                || !snapshot.players().equals(next.players());
        boolean managementChanged = canManageParty(snapshot)
                != canManageParty(next);
        snapshot = next;
        if (managementChanged) {
            Minecraft.getInstance().setScreen(
                    new PartyManagementScreen(next)
            );
            return;
        }
        if (chatToggle != null) {
            chatToggle.setSilently(next.chatEnabled());
        }
        if (partyNameInput != null && !partyNameInput.getFocused()) {
            partyNameInput.setTextSilently(next.partyName());
        }
        refreshLiveControls();
        if (headsChanged) {
            rebuildHeads();
        }
    }

    private void buildPartyContent(UIComponent body) {
        boolean canManageParty = canManageParty();
        int memberPanelBottomInset = canManageParty ? 157 : 113;
        partyNameInput = new BrassTextInput(
                snapshot.partyName(),
                "Party name",
                textCallback(value -> refreshLiveControls())
        );
        partyNameInput.setActive(snapshot.owner());
        partyNameInput.setOnSubmit(textCallback(value -> renameParty()));
        partyNameInput.onFocusLost(component -> {
            renameParty();
            return Unit.INSTANCE;
        });
        partyNameInput.setX(new PixelConstraint(8f));
        partyNameInput.setY(new PixelConstraint(7f));
        partyNameInput.setWidth(new SubtractiveConstraint(
                new RelativeConstraint(1f),
                new PixelConstraint(112f)
        ));
        partyNameInput.setHeight(new PixelConstraint(18f));
        partyNameInput.setChildOf(body);

        BrassLabel chatLabel = label(
                "Party chat",
                Colors.INSTANCE.getUI_TEXT_DARK(),
                0.86f,
                body,
                46,
                13,
                true
        );
        chatLabel.setEntranceEnabled(false);

        chatToggle = new BrassToggle(
                snapshot.chatEnabled(),
                toggleCallback(enabled -> send(
                        PartyMenuActionPayload.Action.TOGGLE_CHAT,
                        null,
                        ""
                ))
        );
        chatToggle.setX(new PixelConstraint(8f, true));
        chatToggle.setY(new PixelConstraint(9f));
        chatToggle.setWidth(new PixelConstraint(30f));
        chatToggle.setHeight(new PixelConstraint(16f));
        chatToggle.setChildOf(body);

        statusLabel = label(
                "",
                Colors.INSTANCE.getUI_TEXT(),
                0.9f,
                body,
                8,
                33,
                false
        );
        summaryLabel = label(
                "",
                Colors.INSTANCE.getUI_TEXT_DARK(),
                0.82f,
                body,
                8,
                45,
                false
        );

        BrassSearchField search = new BrassSearchField(
                "Search members or players...",
                0.12f,
                textCallback(value -> pendingQuery = value)
        );
        search.setX(new PixelConstraint(8f));
        search.setY(new PixelConstraint(58f));
        search.setWidth(new SubtractiveConstraint(
                new RelativeConstraint(1f),
                new PixelConstraint(16f)
        ));
        search.setHeight(new PixelConstraint(17f));
        search.setChildOf(body);

        BrassPanel memberPanel = new BrassPanel(
                "MEMBERS",
                4f,
                4f,
                true,
                BrassPanel.Layout.FREE,
                false
        );
        memberPanel.setX(new PixelConstraint(8f));
        memberPanel.setY(new PixelConstraint(81f));
        memberPanel.setWidth(new SubtractiveConstraint(
                new RelativeConstraint(0.5f),
                new PixelConstraint(12f)
        ));
        memberPanel.setHeight(new SubtractiveConstraint(
                new RelativeConstraint(1f),
                new PixelConstraint((float) memberPanelBottomInset)
        ));
        memberPanel.setChildOf(body);

        memberScroll = new BrassScrollArea(3f, false);
        memberScroll.setWidth(new RelativeConstraint(1f));
        memberScroll.setHeight(new RelativeConstraint(1f));
        memberScroll.setChildOf(memberPanel.getContent());

        if (canManageParty) {
            UIContainer memberControls = new UIContainer();
            memberControls.setX(new PixelConstraint(8f));
            memberControls.setY(new PixelConstraint(26f, true));
            memberControls.setWidth(new SubtractiveConstraint(
                    new RelativeConstraint(0.5f),
                    new PixelConstraint(12f)
            ));
            memberControls.setHeight(new PixelConstraint(48f));
            memberControls.setChildOf(body);

            promoteButton = button(
                    "Promote",
                    BrassAccent.Companion.getBRASS(),
                    () -> sendSelectedMemberAction(
                            PartyMenuActionPayload.Action.PROMOTE
                    )
            );
            positionMemberButton(promoteButton, memberControls, false, 0);

            demoteButton = button(
                    "Demote",
                    BrassAccent.Companion.getDEFAULT(),
                    () -> sendSelectedMemberAction(
                            PartyMenuActionPayload.Action.DEMOTE
                    )
            );
            positionMemberButton(demoteButton, memberControls, true, 0);

            transferButton = button(
                    "Transfer",
                    BrassAccent.Companion.getDEFAULT(),
                    this::confirmSelectedMemberTransfer
            );
            positionMemberButton(transferButton, memberControls, false, 24);

            removeButton = button(
                    "Remove",
                    BrassAccent.Companion.getDANGER(),
                    this::confirmSelectedMemberRemoval
            );
            positionMemberButton(removeButton, memberControls, true, 24);
        }

        BrassPanel playerPanel = new BrassPanel(
                "ADD PLAYERS",
                4f,
                4f,
                true,
                BrassPanel.Layout.FREE,
                false
        );
        playerPanel.setX(new PixelConstraint(8f, true));
        playerPanel.setY(new PixelConstraint(81f));
        playerPanel.setWidth(new SubtractiveConstraint(
                new RelativeConstraint(0.5f),
                new PixelConstraint(12f)
        ));
        playerPanel.setHeight(new SubtractiveConstraint(
                new RelativeConstraint(1f),
                new PixelConstraint(113f)
        ));
        playerPanel.setChildOf(body);

        playerScroll = new BrassScrollArea(3f, false);
        playerScroll.setWidth(new RelativeConstraint(1f));
        playerScroll.setHeight(new RelativeConstraint(1f));
        playerScroll.setChildOf(playerPanel.getContent());

        partyActionButton = button(
                snapshot.owner() ? "Disband party" : "Leave party",
                BrassAccent.Companion.getDANGER(),
                this::confirmPartyExit
        );
        partyActionButton.setX(new PixelConstraint(8f, true));
        partyActionButton.setY(new PixelConstraint(8f, true));
        partyActionButton.setWidth(new PixelConstraint(100f));
        partyActionButton.setHeight(new PixelConstraint(18f));
        partyActionButton.setChildOf(body);

        refreshLiveControls();
        rebuildHeads();
    }

    private void rebuildHeads() {
        if (memberScroll == null || playerScroll == null) {
            return;
        }
        detachHeadTooltips();
        memberHeads.clear();
        memberScroll.clear();
        playerScroll.clear();

        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<PartyMenuStatePayload.MemberEntry> members = snapshot.members()
                .stream()
                .filter(member -> matches(member.name(), member.rank(), filter))
                .toList();
        visibleMemberIds = members.stream()
                .map(PartyMenuStatePayload.MemberEntry::id)
                .toList();
        if (canManageParty() && !visibleMemberIds.contains(selectedMemberId)) {
            UUID owner = ownerId(snapshot);
            selectedMemberId = visibleMemberIds.contains(owner)
                    ? owner
                    : visibleMemberIds.stream().findFirst().orElse(null);
        }
        List<PartyMenuStatePayload.PlayerEntry> players = snapshot.players()
                .stream()
                .filter(player -> matches(
                        player.name(),
                        player.invited() ? "invited" : "available",
                        filter
                ))
                .toList();
        renderedMemberColumns = headsPerRow(memberScroll);
        renderedPlayerColumns = headsPerRow(playerScroll);

        if (members.isEmpty()) {
            label(
                    snapshot.members().isEmpty() ? "No members" : "No results",
                    Colors.INSTANCE.getUI_TEXT_DARK(),
                    0.86f,
                    memberScroll.getContent(),
                    3,
                    4,
                    false
            );
        } else {
            for (int index = 0; index < members.size(); index++) {
                addMemberHead(index, renderedMemberColumns, members.get(index));
            }
        }

        if (!snapshot.canManageMembers()) {
            label(
                    "Moderator rank required",
                    Colors.INSTANCE.getUI_TEXT_DARK(),
                    0.82f,
                    playerScroll.getContent(),
                    3,
                    4,
                    false
            );
        } else if (players.isEmpty()) {
            label(
                    snapshot.players().isEmpty() ? "No players available" : "No results",
                    Colors.INSTANCE.getUI_TEXT_DARK(),
                    0.86f,
                    playerScroll.getContent(),
                    3,
                    4,
                    false
            );
        } else {
            for (int index = 0; index < players.size(); index++) {
                addPlayerHead(index, renderedPlayerColumns, players.get(index));
            }
        }
        syncMemberSelection();
        refreshMemberControls();
    }

    private void addMemberHead(int index, int columns,
                               PartyMenuStatePayload.MemberEntry member) {
        boolean manageable = canManageParty();
        boolean selected = manageable
                && member.id().equals(selectedMemberId);
        BrassAccent accent = selected
                ? BrassAccent.Companion.getBRASS()
                : BrassAccent.Companion.getDEFAULT();
        BrassPlayerHead head = new BrassPlayerHead(
                member.name(),
                (float) HEAD_SIZE,
                BrassPlayerHead.Source.GAME,
                false,
                null
        );
        head.setAccent(accent);
        head.setClickable(manageable);
        UIContainer cell = new UIContainer();
        cell.setX(new PixelConstraint((float) (1 + index % columns * 31)));
        cell.setY(new PixelConstraint((float) (1 + index / columns * 31)));
        cell.setWidth(new PixelConstraint(28f));
        cell.setHeight(new PixelConstraint(28f));
        cell.setChildOf(memberScroll.getContent());

        head.setX(new PixelConstraint(2f));
        head.setY(new PixelConstraint(2f));
        head.setChildOf(cell);
        memberHeads.put(member.id(), head);
        head.onMouseClickConsumer(event -> {
            if (event.getMouseButton() == 0 && canManageParty()) {
                selectMember(member.id());
            }
        });
        BrassTooltip.INSTANCE.attachLazy(
                head,
                textSupplier(member::name),
                textSupplier(() -> memberTooltip(member, canManageParty())),
                accent,
                true,
                null
        );
        renderedHeads.add(head);
    }

    private void addPlayerHead(int index, int columns,
                               PartyMenuStatePayload.PlayerEntry player) {
        BrassAccent accent = player.invited()
                ? BrassAccent.Companion.getDANGER()
                : BrassAccent.Companion.getBRASS();
        BrassPlayerHead head = new BrassPlayerHead(
                player.name(),
                (float) HEAD_SIZE,
                BrassPlayerHead.Source.GAME,
                false,
                null
        );
        head.setAccent(accent);
        head.setClickable(snapshot.canManageMembers());
        head.setActive(snapshot.canManageMembers());
        head.setX(new PixelConstraint((float) (3 + index % columns * 31)));
        head.setY(new PixelConstraint((float) (3 + index / columns * 31)));
        head.setChildOf(playerScroll.getContent());
        head.onMouseClickConsumer(event -> {
            if (event.getMouseButton() == 0 && snapshot.canManageMembers()) {
                send(
                        player.invited()
                                ? PartyMenuActionPayload.Action.CANCEL_INVITE
                                : PartyMenuActionPayload.Action.INVITE,
                        player.id(),
                        ""
                );
            }
        });
        BrassTooltip.INSTANCE.attachLazy(
                head,
                textSupplier(player::name),
                textSupplier(() -> player.invited()
                        ? "Invited, click to cancel"
                        : "Click to invite"),
                accent,
                true,
                null
        );
        renderedHeads.add(head);
    }

    private void showCreationPrompt() {
        showCreationPrompt(getBackground(), () -> {
            if (Minecraft.getInstance().screen == this) {
                closeScreen();
            }
        });
    }

    static void showCreationPrompt(UIComponent root, Runnable onClose) {
        BrassModal modal = new BrassModal(
                "Create a party?",
                260f,
                112f,
                false,
                true,
                callback(onClose)
        );
        BrassLabel question = label(
                "Would you like to create one now?",
                Colors.INSTANCE.getUI_TEXT(),
                1f,
                modal.getBody(),
                4,
                16,
                false
        );
        question.setEntranceEnabled(false);
        BrassButton cancel = button(
                "Not now",
                BrassAccent.Companion.getDEFAULT(),
                modal::dismiss
        );
        BrassButton create = button(
                "Create party",
                BrassAccent.Companion.getBRASS(),
                () -> {
                    modal.dismiss();
                    ClaimPermissionsNetwork.sendToServer(
                            new PartyMenuActionPayload(
                                    PartyMenuActionPayload.Action.CREATE,
                                    null,
                                    ""
                            )
                    );
                }
        );
        modal.footer(cancel, create).show(root);
    }

    private void showConfirmation(String title, String message,
                                  String actionLabel,
                                  PartyMenuActionPayload.Action action,
                                  UUID target) {
        BrassModal modal = new BrassModal(
                title,
                310f,
                120f,
                false,
                true,
                callback(() -> {
                })
        );
        label(
                message,
                Colors.INSTANCE.getUI_TEXT(),
                0.9f,
                modal.getBody(),
                4,
                16,
                false
        );
        BrassButton cancel = button(
                "Cancel",
                BrassAccent.Companion.getDEFAULT(),
                modal::dismiss
        );
        BrassButton confirm = button(
                actionLabel,
                BrassAccent.Companion.getDANGER(),
                () -> {
                    modal.dismiss();
                    send(action, target, "");
                }
        );
        modal.footer(cancel, confirm).show(getBackground());
    }

    private void positionMemberButton(BrassButton button, UIComponent parent,
                                      boolean alignOpposite, int y) {
        button.setX(new PixelConstraint(0f, alignOpposite));
        button.setY(new PixelConstraint((float) y));
        button.setWidth(new SubtractiveConstraint(
                new RelativeConstraint(0.5f),
                new PixelConstraint(4f)
        ));
        button.setHeight(new PixelConstraint(18f));
        button.setChildOf(parent);
    }

    private void selectMember(UUID memberId) {
        selectedMemberId = memberId;
        syncMemberSelection();
        refreshMemberControls();
    }

    private void syncMemberSelection() {
        memberHeads.forEach((id, head) -> head.setAccent(
                id.equals(selectedMemberId)
                        ? BrassAccent.Companion.getBRASS()
                        : BrassAccent.Companion.getDEFAULT()
        ));
    }

    private PartyMenuStatePayload.MemberEntry selectedMember() {
        if (selectedMemberId == null) {
            return null;
        }
        return snapshot.members().stream()
                .filter(member -> member.id().equals(selectedMemberId))
                .findFirst()
                .orElse(null);
    }

    private void sendSelectedMemberAction(
            PartyMenuActionPayload.Action action) {
        PartyMenuStatePayload.MemberEntry member = selectedMember();
        if (member != null) {
            send(action, member.id(), "");
        }
    }

    private void confirmSelectedMemberTransfer() {
        PartyMenuStatePayload.MemberEntry member = selectedMember();
        if (member == null) {
            return;
        }
        showConfirmation(
                "Transfer ownership?",
                "Make " + member.name() + " the new party owner?",
                "Transfer",
                PartyMenuActionPayload.Action.TRANSFER,
                member.id()
        );
    }

    private void confirmSelectedMemberRemoval() {
        PartyMenuStatePayload.MemberEntry member = selectedMember();
        if (member == null) {
            return;
        }
        showConfirmation(
                "Remove party member?",
                "Remove " + member.name() + " from the party?",
                "Remove",
                PartyMenuActionPayload.Action.KICK,
                member.id()
        );
    }

    private void refreshMemberControls() {
        if (promoteButton == null) {
            return;
        }
        PartyMenuStatePayload.MemberEntry member = selectedMember();
        if (member == null) {
            promoteButton.setActive(false);
            demoteButton.setActive(false);
            transferButton.setActive(false);
            removeButton.setActive(false);
            return;
        }
        promoteButton.setActive(snapshot.canManageRanks() && !member.owner()
                && !"ADMIN".equals(member.rank()));
        demoteButton.setActive(snapshot.canManageRanks() && !member.owner()
                && !"MEMBER".equals(member.rank()));
        transferButton.setActive(snapshot.owner() && !member.owner());
        removeButton.setActive(snapshot.canManageMembers()
                && !member.owner()
                && !isLocalPlayer(member.id()));
    }

    private void refreshLiveControls() {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(
                snapshot.partyName() + "  |  Owner: " + snapshot.ownerName()
        );
        long invited = snapshot.players().stream()
                .filter(PartyMenuStatePayload.PlayerEntry::invited)
                .count();
        summaryLabel.setText(
                snapshot.members().size() + " members  |  "
                        + snapshot.allyCount() + " allies  |  "
                        + invited + " invites  |  Rank: "
                        + displayRank(snapshot.selfRank())
        );
        partyActionButton.setLabel(
                snapshot.owner() ? "Disband party" : "Leave party"
        );
        partyNameInput.setActive(snapshot.owner());
        refreshMemberControls();
    }

    private void renameParty() {
        String name = partyNameInput.getText().trim();
        if (snapshot.owner() && !name.equals(snapshot.partyName())
                && validPartyName(name)) {
            send(PartyMenuActionPayload.Action.RENAME, null, name);
        }
    }

    private void confirmPartyExit() {
        if (snapshot.owner()) {
            showConfirmation(
                    "Disband party?",
                    "This permanently removes the party.",
                    "Disband",
                    PartyMenuActionPayload.Action.DISBAND,
                    null
            );
        } else {
            showConfirmation(
                    "Leave party?",
                    "You will lose access to the party.",
                    "Leave",
                    PartyMenuActionPayload.Action.LEAVE,
                    null
            );
        }
    }

    private void closeScreen() {
        Minecraft.getInstance().setScreen(null);
    }

    private void send(PartyMenuActionPayload.Action action, UUID target,
                      String value) {
        ClaimPermissionsNetwork.sendToServer(
                new PartyMenuActionPayload(action, target, value)
        );
    }

    private void detachHeadTooltips() {
        for (UIComponent head : renderedHeads) {
            BrassTooltip.INSTANCE.detach(head);
        }
        renderedHeads.clear();
    }

    private int headsPerRow(BrassScrollArea scroll) {
        if (scroll == null) {
            return 1;
        }
        return Math.max(
                1,
                (int) Math.floor((scroll.getWidth() - 27f) / 31f) + 1
        );
    }

    private boolean canManageParty() {
        return canManageParty(snapshot);
    }

    private static boolean canManageParty(PartyMenuStatePayload payload) {
        return payload.owner()
                || payload.canManageMembers()
                || payload.canManageRanks();
    }

    private static UUID ownerId(PartyMenuStatePayload payload) {
        return payload.members().stream()
                .filter(PartyMenuStatePayload.MemberEntry::owner)
                .map(PartyMenuStatePayload.MemberEntry::id)
                .findFirst()
                .orElse(null);
    }

    private static boolean matches(String name, String detail,
                                   String filter) {
        return filter.isEmpty()
                || name.toLowerCase(Locale.ROOT).contains(filter)
                || detail.toLowerCase(Locale.ROOT).contains(filter);
    }

    private static String memberTooltip(
            PartyMenuStatePayload.MemberEntry member, boolean manageable) {
        String owner = member.owner() ? "Party owner, " : "";
        String online = member.online() ? "online" : "offline";
        String select = manageable ? "  |  Click to select" : "";
        return owner + displayRank(member.rank()) + "  |  " + online
                + select;
    }

    private static String displayRank(String rank) {
        if (rank == null || rank.isBlank()) {
            return "Member";
        }
        String value = rank.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static boolean validPartyName(String value) {
        if (value.isEmpty() || value.length() > 100) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetter(character)
                    && !Character.isDigit(character)
                    && " _'\"!?,-&%*():".indexOf(character) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocalPlayer(UUID playerId) {
        return Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getUUID().equals(playerId);
    }

    private static BrassButton button(String label, BrassAccent accent,
                                      Runnable action) {
        BrassButton button = new BrassButton(label, accent, callback(action));
        button.setChrome(BrassChrome.FLAT);
        button.setEntranceEnabled(false);
        return button;
    }

    private static BrassLabel label(String text, Color color, float scale,
                                    UIComponent parent, int x, int y,
                                    boolean alignOpposite) {
        BrassLabel label = new BrassLabel(text, color, true, scale);
        label.setX(new PixelConstraint((float) x, alignOpposite));
        label.setY(new PixelConstraint((float) y));
        label.setChildOf(parent);
        return label;
    }

    private static Function0<Unit> callback(Runnable action) {
        return () -> {
            action.run();
            return Unit.INSTANCE;
        };
    }

    private static Function1<String, Unit> textCallback(
            Consumer<String> consumer) {
        return value -> {
            consumer.accept(value);
            return Unit.INSTANCE;
        };
    }

    private static Function1<Boolean, Unit> toggleCallback(
            Consumer<Boolean> consumer) {
        return value -> {
            consumer.accept(value);
            return Unit.INSTANCE;
        };
    }

    private static Function0<String> textSupplier(Supplier<String> supplier) {
        return supplier::get;
    }
}
