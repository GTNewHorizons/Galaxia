package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyAssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

final class OrbitalAssetManagementWidget extends ParentWidget<OrbitalAssetManagementWidget> {

    interface Callbacks {

        boolean isCreativeBuildModeEnabled();

        boolean isGT5AutomationAvailable();

        boolean canCreateBaseStation(OrbitalCelestialBody body);

        boolean canCreateAutomatedStation(OrbitalCelestialBody body);

        boolean canCreateAutomatedOutpost(OrbitalCelestialBody body);

        boolean hasStoredConstructionResources(CelestialManagedAsset asset);

        boolean isManageableStationAsset(CelestialManagedAsset asset);

        String formatAssetDisplayName(CelestialManagedAsset asset);

        String buildConstructionInventorySummary(CelestialManagedAsset asset);

        String formatAssetKind(CelestialAssetKind kind);

        String formatAssetLocation(CelestialAssetLocation location);

        void drawAssetIcon(CelestialAssetKind kind, int x, int y, int size, float alpha);

        void closeAssetManagement();

        void createBaseStation(OrbitalCelestialBody body);

        void triggerAssetCreation(OrbitalCelestialBody body, CelestialAssetKind kind, boolean openManagementFirst);

        void openPendingAssetRename(CelestialManagedAsset asset);

        void openPendingConstructionCancellation(CelestialManagedAsset asset);

        void openPendingResourceTransfer(CelestialManagedAsset asset);

        void openPendingAssetManagement(CelestialManagedAsset asset);

        void openPendingAssetDestruction(CelestialManagedAsset asset);

        void confirmPendingAssetCreation();

        void dismissPendingAssetCreation();

        void closePendingAssetRename();

        void confirmPendingAssetRename();

        void dismissPendingAssetDestruction();

        void advancePendingAssetDestruction();

        void dismissPendingConstructionCancellation();

        void confirmPendingConstructionCancellation();

        void dismissPendingResourceTransfer();

        void sendPendingResourceTransfer(StationTransferTarget target);

        void closePendingAssetManagement();

        void dismissPendingModalByOutsideClick();

        void showActionStatus(String message);
    }

    private static final int MODAL_MAX_WIDTH = 520;
    private static final int MODAL_MAX_HEIGHT = 420;
    private static final int MODAL_MARGIN_X = 80;
    private static final int MODAL_MARGIN_Y = 60;
    private static final int HEADER_HEIGHT = 28;
    private static final int CONTENT_TOP = 54;
    private static final int CONTENT_PADDING = 10;
    private static final int CONTENT_SCROLLBAR_GAP = 14;
    private static final int ROW_HEIGHT = 42;
    private static final int ROW_SPACING = 6;
    private static final int ICON_BUTTON_SIZE = 22;
    private static final int FOOTER_BUTTON_HEIGHT = 20;

    private final OrbitalAssetUiState state;
    private final Callbacks callbacks;

    private String lastStructureSignature = "";
    private String lastContentSignature = "";
    private int modalLeft;
    private int modalTop;
    private int modalRight;
    private int modalBottom;
    private int scrollLeft;
    private int scrollTop;
    private int scrollRight;
    private int scrollBottom;
    private ScrollWidget<?> activeScrollWidget;
    private ScrollWidget<?> mainScrollWidget;
    private ParentWidget<?> mainScrollContent;
    private VerticalScrollData mainScrollData;
    private int mainContentWidth;
    private int mainContentHeight;

    OrbitalAssetManagementWidget(OrbitalAssetUiState state, Callbacks callbacks) {
        this.state = state;
        this.callbacks = callbacks;
        setEnabled(false);
        background(drawable((context, x, y, width, height) -> Gui.drawRect(x, y, x + width, y + height, 0xAA09111B)));
    }

    boolean isPointInModal(int localX, int localY) {
        return shouldShowOverlay()
            && localX >= modalLeft
            && localX <= modalRight
            && localY >= modalTop
            && localY <= modalBottom;
    }

    boolean isPointInScrollViewport(int localX, int localY) {
        return shouldShowPanel()
            && localX >= scrollLeft
            && localX <= scrollRight
            && localY >= scrollTop
            && localY <= scrollBottom;
    }

    ButtonRect getRenameInputBounds() {
        PendingAssetRenameLayout layout = getPendingAssetRenameLayout();
        return layout == null ? null : layout.inputField;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        boolean visible = shouldShowOverlay();
        if (!visible) {
            if (isEnabled()) {
                removeAll();
                scheduleResize();
            }
            clearBounds();
            clearMainPanelState();
            activeScrollWidget = null;
            lastStructureSignature = "";
            lastContentSignature = "";
            setEnabled(false);
            return;
        }

        setEnabled(true);
        String structureSignature = buildStructureSignature();
        if (!structureSignature.equals(lastStructureSignature)) {
            rebuildChildren();
            lastStructureSignature = structureSignature;
            lastContentSignature = buildContentSignature();
            return;
        }

        if (shouldShowPanel()) {
            String contentSignature = buildContentSignature();
            if (!contentSignature.equals(lastContentSignature)) {
                refreshMainPanelContent();
                lastContentSignature = contentSignature;
            }
        }
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!shouldShowOverlay()) {
            return;
        }
        super.drawBackground(context, widgetTheme);
    }

    private boolean shouldShowOverlay() {
        return state.isAssetManagementOpen();
    }

    private boolean shouldShowPanel() {
        return state.isAssetManagementOpen() && !state.hasBlockingModal();
    }

    private String buildStructureSignature() {
        if (!shouldShowOverlay()) {
            return "";
        }

        OrbitalCelestialBody body = state.assetManagementBody;
        StringBuilder signature = new StringBuilder(256);
        signature.append(getAvailableOverlayWidth())
            .append('|')
            .append(getAvailableOverlayHeight())
            .append('|')
            .append(body.id())
            .append('|')
            .append(body.displayName())
            .append('|')
            .append(callbacks.isCreativeBuildModeEnabled())
            .append('|')
            .append(callbacks.isGT5AutomationAvailable())
            .append('|')
            .append(callbacks.canCreateBaseStation(body))
            .append('|')
            .append(callbacks.canCreateAutomatedStation(body))
            .append('|')
            .append(callbacks.canCreateAutomatedOutpost(body))
            .append('|')
            .append(state.pendingAssetCreation != null)
            .append('|')
            .append(state.pendingAssetDestruction != null ? state.pendingAssetDestruction.armed : false)
            .append('|')
            .append(state.pendingConstructionCancellation != null)
            .append('|')
            .append(state.pendingResourceTransfer != null)
            .append('|')
            .append(state.pendingAssetManagement != null)
            .append('|')
            .append(state.pendingAssetRename != null);

        if (state.pendingAssetCreation != null) {
            signature.append('|')
                .append(state.pendingAssetCreation.kind)
                .append('|')
                .append(state.pendingAssetCreation.displayName)
                .append('|')
                .append(state.pendingAssetCreation.requiredResources.size());
        }
        if (state.pendingAssetDestruction != null) {
            signature.append('|').append(state.pendingAssetDestruction.asset.assetId());
        }
        if (state.pendingConstructionCancellation != null) {
            signature.append('|').append(state.pendingConstructionCancellation.asset.assetId());
        }
        if (state.pendingAssetManagement != null) {
            signature.append('|').append(state.pendingAssetManagement.asset.assetId());
        }
        if (state.pendingAssetRename != null) {
            signature.append('|').append(state.pendingAssetRename.asset.assetId());
        }
        if (state.pendingResourceTransfer != null) {
            signature.append('|')
                .append(state.pendingResourceTransfer.asset.assetId())
                .append('|')
                .append(state.pendingResourceTransfer.targets.size());
            for (StationTransferTarget target : state.pendingResourceTransfer.targets) {
                signature.append('|').append(target.assetId);
            }
        }

        return signature.toString();
    }

    private String buildContentSignature() {
        if (!shouldShowPanel()) {
            return "";
        }

        OrbitalCelestialBody body = state.assetManagementBody;
        CelestialBodyAssetState assetState = CelestialAssetStore.getState(body.id());
        StringBuilder signature = new StringBuilder(512);
        for (CelestialManagedAsset asset : assetState.assets()) {
            signature.append('|')
                .append(asset.assetId())
                .append(':')
                .append(asset.displayName())
                .append(':')
                .append(asset.kind())
                .append(':')
                .append(asset.status())
                .append(':')
                .append(asset.location())
                .append(':')
                .append(callbacks.buildConstructionInventorySummary(asset));
        }
        return signature.toString();
    }

    private void rebuildChildren() {
        clearMainPanelState();
        activeScrollWidget = null;
        removeAll();
        clearBounds();

        OrbitalCelestialBody body = state.assetManagementBody;
        if (body == null) {
            return;
        }

        child(createBackdropButton());

        if (state.hasBlockingModal()) {
            buildPendingModal();
            return;
        }

        buildMainPanel(body);
        refreshMainPanelContent();
    }

    private void buildMainPanel(OrbitalCelestialBody body) {
        ModalBounds bounds = calculateManagementBounds();
        updateModalBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);

        int modalWidth = bounds.right - bounds.left;
        int modalHeight = bounds.bottom - bounds.top;
        int contentHeight = modalHeight - CONTENT_TOP - 12;
        int contentWidth = modalWidth - (CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP;

        scrollLeft = bounds.left + CONTENT_PADDING;
        scrollTop = bounds.top + CONTENT_TOP;
        scrollRight = scrollLeft + contentWidth;
        scrollBottom = scrollTop + contentHeight;

        mainContentWidth = contentWidth;
        mainContentHeight = contentHeight;

        ParentWidget<?> modal = createModalRoot(bounds);
        modal.child(createTitleText("Manage Assets").pos(12, 10));

        int titleRight = 12 + Minecraft.getMinecraft().fontRenderer.getStringWidth("Manage Assets");
        int assetNameMaxWidth = Math.max(0, modalWidth - 40 - (titleRight + 24));
        if (assetNameMaxWidth > 0) {
            String assetName = trimToWidth(body.displayName(), assetNameMaxWidth);
            int assetNameWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(assetName);
            int assetNameX = Math.max(titleRight + 12, modalWidth - 40 - assetNameWidth);
            modal.child(createBodyText(assetName, 0xFFD9E0FF).pos(assetNameX, 10));
        }

        modal.child(createGlyphButton(AssetManagerButtonGlyph.CLOSE, "Close", true, callbacks::closeAssetManagement).pos(modalWidth - 28, 6));
        modal.child(
            createAssetKindButton(
                CelestialAssetKind.STATION,
                "Create Station",
                callbacks.canCreateBaseStation(body),
                () -> callbacks.createBaseStation(body)).pos(14, 30));
        modal.child(
            createAssetKindButton(
                CelestialAssetKind.AUTOMATED_STATION,
                "Create Automated Station",
                callbacks.canCreateAutomatedStation(body),
                () -> callbacks.triggerAssetCreation(body, CelestialAssetKind.AUTOMATED_STATION, false)).pos(42, 30));
        modal.child(
            createAssetKindButton(
                CelestialAssetKind.AUTOMATED_OUTPOST,
                "Create Automated Outpost",
                callbacks.canCreateAutomatedOutpost(body),
                () -> callbacks.triggerAssetCreation(body, CelestialAssetKind.AUTOMATED_OUTPOST, false)).pos(70, 30));

        if (!callbacks.isGT5AutomationAvailable()) {
            modal.child(createBodyText("GT5U required for automated assets", 0xFF9AA7B8).pos(104, 36));
        }

        VerticalScrollData scrollData = new VerticalScrollData();
        mainScrollData = scrollData;
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(CONTENT_PADDING, CONTENT_TOP)
            .widthRelOffset(1f, -(CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP)
            .heightRelOffset(1f, -(CONTENT_TOP + 12))
            .background(drawable((context, x, y, width, height) -> Gui.drawRect(x, y, x + width, y + height, 0x3318273A)));
        activeScrollWidget = scroll;
        mainScrollWidget = scroll;

        ParentWidget<?> content = new ParentWidget<>().widthRel(1f).height(contentHeight);
        mainScrollContent = content;
        scroll.child(content);
        modal.child(scroll);
        child(modal);
    }

    private void refreshMainPanelContent() {
        if (!shouldShowPanel() || mainScrollContent == null || mainScrollWidget == null || mainScrollData == null) {
            return;
        }

        OrbitalCelestialBody body = state.assetManagementBody;
        if (body == null) {
            return;
        }

        CelestialBodyAssetState assetState = CelestialAssetStore.getState(body.id());
        int contentScrollSize = Math.max(mainContentHeight, computeContentHeight(assetState));
        mainScrollData.setScrollSize(contentScrollSize);
        mainScrollContent.removeAll();
        mainScrollContent.widthRel(1f).height(contentScrollSize);
        populateContent(mainScrollContent, mainContentWidth, assetState);
        mainScrollContent.scheduleResize();
        mainScrollWidget.scheduleResize();
    }

    private void buildPendingModal() {
        activeScrollWidget = null;
        scrollLeft = scrollTop = scrollRight = scrollBottom = 0;

        if (state.pendingAssetCreation != null) {
            buildPendingAssetCreationModal();
            return;
        }
        if (state.pendingAssetDestruction != null) {
            buildPendingAssetDestructionModal();
            return;
        }
        if (state.pendingConstructionCancellation != null) {
            buildPendingConstructionCancellationModal();
            return;
        }
        if (state.pendingResourceTransfer != null) {
            buildPendingResourceTransferModal();
            return;
        }
        if (state.pendingAssetManagement != null) {
            buildPendingAssetManagementModal();
            return;
        }
        if (state.pendingAssetRename != null) {
            buildPendingAssetRenameModal();
        }
    }

    private void buildPendingAssetCreationModal() {
        PendingAssetCreationLayout layout = getPendingAssetCreationLayout();
        if (layout == null) {
            return;
        }

        updateModalBounds(layout.left, layout.top, layout.right, layout.bottom);
        ParentWidget<?> modal = createModalRoot(layout.left, layout.top, layout.right, layout.bottom);

        modal.child(createAssetIconWidget(state.pendingAssetCreation.kind, 1.0f).pos(12, 10).size(18, 18));
        modal.child(createTitleText("Confirm " + callbacks.formatAssetKind(state.pendingAssetCreation.kind)).pos(36, 10));
        modal.child(createBodyText(state.pendingAssetCreation.displayName, 0xFFD9E0FF).pos(36, 28));
        modal.child(createSectionText("Required resources").pos(12, 52));

        int resourceY = 68;
        for (CelestialAssetRequirement requirement : state.pendingAssetCreation.requiredResources) {
            modal.child(
                createBodyText("- " + requirement.amount() + " " + requirement.displayName(), 0xFFD9E0FF)
                    .pos(16, resourceY));
            resourceY += 12;
        }

        modal.child(createFooterButton("Cancel", true, callbacks::dismissPendingAssetCreation).pos(
            layout.cancelButton.left - layout.left,
            layout.cancelButton.top - layout.top).size(buttonWidth(layout.cancelButton), FOOTER_BUTTON_HEIGHT));
        modal.child(createFooterButton("Confirm", true, callbacks::confirmPendingAssetCreation).pos(
            layout.confirmButton.left - layout.left,
            layout.confirmButton.top - layout.top).size(buttonWidth(layout.confirmButton), FOOTER_BUTTON_HEIGHT));

        child(modal);
    }

    private void buildPendingAssetRenameModal() {
        PendingAssetRenameLayout layout = getPendingAssetRenameLayout();
        if (layout == null) {
            return;
        }

        updateModalBounds(layout.left, layout.top, layout.right, layout.bottom);
        ParentWidget<?> modal = createModalRoot(layout.left, layout.top, layout.right, layout.bottom);

        modal.child(createTitleText("Rename Asset").pos(12, 10));
        modal.child(createBodyText(callbacks.formatAssetDisplayName(state.pendingAssetRename.asset), 0xFFD9E0FF).pos(12, 28));
        modal.child(createBodyText("New name", 0xFF9AA7B8).pos(14, 42));
        modal.child(
            drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, 0xFF0F1621);
                Gui.drawRect(x, y, x + width, y + 1, 0xFF7FB6FF);
                Gui.drawRect(x, y + height - 1, x + width, y + height, 0xFF7FB6FF);
                Gui.drawRect(x, y, x + 1, y + height, 0xFF7FB6FF);
                Gui.drawRect(x + width - 1, y, x + width, y + height, 0xFF7FB6FF);
            }).asWidget().pos(layout.inputField.left - layout.left, layout.inputField.top - layout.top)
                .size(buttonWidth(layout.inputField), layout.inputField.bottom - layout.inputField.top));

        modal.child(createFooterButton("Cancel", true, callbacks::closePendingAssetRename).pos(
            layout.cancelButton.left - layout.left,
            layout.cancelButton.top - layout.top).size(buttonWidth(layout.cancelButton), FOOTER_BUTTON_HEIGHT));
        modal.child(createFooterButton("Confirm", true, callbacks::confirmPendingAssetRename).pos(
            layout.confirmButton.left - layout.left,
            layout.confirmButton.top - layout.top).size(buttonWidth(layout.confirmButton), FOOTER_BUTTON_HEIGHT));

        child(modal);
    }

    private void buildPendingAssetDestructionModal() {
        PendingAssetDestructionLayout layout = getPendingAssetDestructionLayout();
        if (layout == null) {
            return;
        }

        updateModalBounds(layout.left, layout.top, layout.right, layout.bottom);
        ParentWidget<?> modal = createModalRoot(
            layout.left,
            layout.top,
            layout.right,
            layout.bottom,
            0xFF1A1012,
            0xFFD14A4A,
            -1);
        int modalWidth = layout.right - layout.left;

        modal.child(createCenteredLargeText("THIS IS IRREVERSIBLE", 1.45f, 0xFFFF5A5A).pos(12, 16).size(modalWidth - 24, 22));
        modal.child(createBodyText("You are about to destroy:", 0xFFD9E0FF).pos(18, 52));
        modal.child(createBodyText(callbacks.formatAssetDisplayName(state.pendingAssetDestruction.asset), 0xFFFFFFFF).pos(18, 68));
        modal.child(
            createBodyText(
                state.pendingAssetDestruction.armed ? "Click Destroy again to confirm."
                    : "Press Destroy to arm confirmation.",
                0xFFFFB3B3).pos(18, 92));

        modal.child(createFooterButton("Cancel", true, callbacks::dismissPendingAssetDestruction).pos(
            layout.cancelButton.left - layout.left,
            layout.cancelButton.top - layout.top).size(buttonWidth(layout.cancelButton), FOOTER_BUTTON_HEIGHT));
        modal.child(createDangerFooterButton("Destroy", callbacks::advancePendingAssetDestruction).pos(
            layout.destroyButton.left - layout.left,
            layout.destroyButton.top - layout.top).size(buttonWidth(layout.destroyButton), FOOTER_BUTTON_HEIGHT));

        child(modal);
    }

    private void buildPendingConstructionCancellationModal() {
        PendingConstructionCancellationLayout layout = getPendingConstructionCancellationLayout();
        if (layout == null) {
            return;
        }

        updateModalBounds(layout.left, layout.top, layout.right, layout.bottom);
        ParentWidget<?> modal = createModalRoot(layout.left, layout.top, layout.right, layout.bottom, 0xFF121B28, 0xFFE6B35A);

        modal.child(createTitleText("Cancel Construction?").pos(12, 10));
        modal.child(
            createBodyText(callbacks.formatAssetDisplayName(state.pendingConstructionCancellation.asset), 0xFFD9E0FF)
                .pos(12, 28));
        modal.child(createBodyText("Stored resources will be moved into deconstruction recovery.", 0xFFFFD59A).pos(12, 54));

        modal.child(createFooterButton("Cancel", true, callbacks::dismissPendingConstructionCancellation).pos(
            layout.cancelButton.left - layout.left,
            layout.cancelButton.top - layout.top).size(buttonWidth(layout.cancelButton), FOOTER_BUTTON_HEIGHT));
        modal.child(createFooterButton("Confirm", true, callbacks::confirmPendingConstructionCancellation).pos(
            layout.confirmButton.left - layout.left,
            layout.confirmButton.top - layout.top).size(buttonWidth(layout.confirmButton), FOOTER_BUTTON_HEIGHT));

        child(modal);
    }

    private void buildPendingResourceTransferModal() {
        PendingResourceTransferLayout layout = getPendingResourceTransferLayout();
        if (layout == null) {
            return;
        }

        updateModalBounds(layout.left, layout.top, layout.right, layout.bottom);
        ParentWidget<?> modal = createModalRoot(layout.left, layout.top, layout.right, layout.bottom);

        modal.child(createTitleText("Send Resources To").pos(12, 10));
        modal.child(createBodyText(callbacks.formatAssetDisplayName(state.pendingResourceTransfer.asset), 0xFFD9E0FF).pos(12, 28));
        modal.child(createBodyText("Requires an orbital rocket with enough capacity.", 0xFF9AA7B8).pos(12, 46));
        modal.child(createFooterButton("Close", true, callbacks::dismissPendingResourceTransfer).pos(
            layout.closeButton.left - layout.left,
            layout.closeButton.top - layout.top).size(buttonWidth(layout.closeButton), FOOTER_BUTTON_HEIGHT));

        if (layout.rows.isEmpty()) {
            modal.child(createBodyText("No stations available in this system", 0xFF9AA7B8).pos(16, 74));
            child(modal);
            return;
        }

        for (TransferTargetRow row : layout.rows) {
            modal.child(
                drawable((context, x, y, width, height) -> Gui.drawRect(x, y, x + width, y + height, 0x55213144)).asWidget()
                    .pos(row.left - layout.left, row.top - layout.top).size(row.right - row.left, row.bottom - row.top));
            modal.child(createAssetIconWidget(CelestialAssetKind.STATION, 1.0f).pos(row.left - layout.left + 10, row.top - layout.top + 9).size(16, 16));
            modal.child(createBodyText(row.target.displayName, 0xFFFFFFFF).pos(row.left - layout.left + 32, row.top - layout.top + 6));
            modal.child(createBodyText(row.target.hostBodyName, 0xFFD9E0FF).pos(row.left - layout.left + 32, row.top - layout.top + 18));
            modal.child(createFooterButton("Send", true, () -> callbacks.sendPendingResourceTransfer(row.target)).pos(
                row.sendButton.left - layout.left,
                row.sendButton.top - layout.top).size(buttonWidth(row.sendButton), FOOTER_BUTTON_HEIGHT));
        }

        child(modal);
    }

    private void buildPendingAssetManagementModal() {
        PendingAssetManagementLayout layout = getPendingAssetManagementLayout();
        if (layout == null) {
            return;
        }

        updateModalBounds(layout.left, layout.top, layout.right, layout.bottom);
        ParentWidget<?> modal = createModalRoot(layout.left, layout.top, layout.right, layout.bottom);

        modal.child(createAssetIconWidget(state.pendingAssetManagement.asset.kind(), 1.0f).pos(12, 10).size(18, 18));
        modal.child(createTitleText("Manage Station").pos(36, 10));
        modal.child(
            createBodyText(callbacks.formatAssetDisplayName(state.pendingAssetManagement.asset), 0xFFD9E0FF).pos(36, 28));
        modal.child(createBodyText("This panel is not implemented yet.", 0xFF9AA7B8).pos(14, 62));
        modal.child(createFooterButton("Close", true, callbacks::closePendingAssetManagement).pos(
            layout.closeButton.left - layout.left,
            layout.closeButton.top - layout.top).size(buttonWidth(layout.closeButton), FOOTER_BUTTON_HEIGHT));

        child(modal);
    }

    private ModalBounds calculateManagementBounds() {
        int availableWidth = getAvailableOverlayWidth();
        int availableHeight = getAvailableOverlayHeight();
        int width = Math.min(MODAL_MAX_WIDTH, availableWidth - MODAL_MARGIN_X);
        int height = Math.min(MODAL_MAX_HEIGHT, availableHeight - MODAL_MARGIN_Y);
        int left = (availableWidth - width) / 2;
        int top = (availableHeight - height) / 2;
        return new ModalBounds(left, top, left + width, top + height);
    }

    private PendingAssetCreationLayout getPendingAssetCreationLayout() {
        if (state.pendingAssetCreation == null) {
            return null;
        }

        int width = 320;
        int height = 150 + Math.max(0, state.pendingAssetCreation.requiredResources.size() - 2) * 12;
        ModalBounds bounds = createCenteredModalBounds(width, height);
        return new PendingAssetCreationLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            createLeftFooterButton(bounds, 110),
            createRightFooterButton(bounds, 110));
    }

    private PendingAssetRenameLayout getPendingAssetRenameLayout() {
        if (state.pendingAssetRename == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(340, 126);
        return new PendingAssetRenameLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new ButtonRect(bounds.left + 14, bounds.top + 54, bounds.right - 14, bounds.top + 76),
            createLeftFooterButton(bounds, 110),
            createRightFooterButton(bounds, 110));
    }

    private PendingAssetDestructionLayout getPendingAssetDestructionLayout() {
        if (state.pendingAssetDestruction == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(360, 150);
        ButtonRect leftButton = createLeftFooterButton(bounds, 130);
        ButtonRect rightButton = createRightFooterButton(bounds, 130);
        return state.pendingAssetDestruction.armed
            ? new PendingAssetDestructionLayout(bounds.left, bounds.top, bounds.right, bounds.bottom, rightButton, leftButton)
            : new PendingAssetDestructionLayout(bounds.left, bounds.top, bounds.right, bounds.bottom, leftButton, rightButton);
    }

    private PendingConstructionCancellationLayout getPendingConstructionCancellationLayout() {
        if (state.pendingConstructionCancellation == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(360, 124);
        return new PendingConstructionCancellationLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            createLeftFooterButton(bounds, 130),
            createRightFooterButton(bounds, 130));
    }

    private PendingResourceTransferLayout getPendingResourceTransferLayout() {
        if (state.pendingResourceTransfer == null) {
            return null;
        }

        int width = 420;
        int height = Math.min(280, 120 + state.pendingResourceTransfer.targets.size() * 42);
        ModalBounds bounds = createCenteredModalBounds(width, height);
        List<TransferTargetRow> rows = new ArrayList<>();
        int rowTop = bounds.top + 66;
        for (int i = 0; i < state.pendingResourceTransfer.targets.size(); i++) {
            StationTransferTarget target = state.pendingResourceTransfer.targets.get(i);
            int currentTop = rowTop + i * 42;
            int currentBottom = currentTop + 36;
            rows.add(new TransferTargetRow(
                target,
                bounds.left + 14,
                currentTop,
                bounds.right - 14,
                currentBottom,
                new ButtonRect(bounds.right - 92, currentTop + 8, bounds.right - 20, currentTop + 28)));
        }

        return new PendingResourceTransferLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new ButtonRect(bounds.right - 96, bounds.top + 8, bounds.right - 18, bounds.top + 28),
            rows);
    }

    private PendingAssetManagementLayout getPendingAssetManagementLayout() {
        if (state.pendingAssetManagement == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(360, 150);
        return new PendingAssetManagementLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            createRightFooterButton(bounds, 110));
    }

    private ModalBounds createCenteredModalBounds(int width, int height) {
        int left = (getAvailableOverlayWidth() - width) / 2;
        int top = (getAvailableOverlayHeight() - height) / 2;
        return new ModalBounds(left, top, left + width, top + height);
    }

    private ButtonRect createLeftFooterButton(ModalBounds bounds, int width) {
        return new ButtonRect(bounds.left + 18, bounds.bottom - 34, bounds.left + 18 + width, bounds.bottom - 14);
    }

    private ButtonRect createRightFooterButton(ModalBounds bounds, int width) {
        return new ButtonRect(bounds.right - 18 - width, bounds.bottom - 34, bounds.right - 18, bounds.bottom - 14);
    }

    private int computeContentHeight(CelestialBodyAssetState assetState) {
        List<CelestialManagedAsset> constructionAssets = getConstructionAssets(assetState.assets());
        List<CelestialManagedAsset> deployedAssets = getOperationalAssets(assetState.assets());

        int y = 0;
        if (!constructionAssets.isEmpty()) {
            y += 16;
            y += constructionAssets.size() * ROW_HEIGHT + Math.max(0, constructionAssets.size() - 1) * ROW_SPACING;
            y += 4;
        }
        y += 16;
        if (deployedAssets.isEmpty()) {
            y += 24;
        } else {
            y += deployedAssets.size() * ROW_HEIGHT + Math.max(0, deployedAssets.size() - 1) * ROW_SPACING;
            y += 8;
        }
        return y;
    }

    private void populateContent(ParentWidget<?> content, int contentWidth, CelestialBodyAssetState assetState) {
        List<CelestialManagedAsset> constructionAssets = getConstructionAssets(assetState.assets());
        List<CelestialManagedAsset> deployedAssets = getOperationalAssets(assetState.assets());

        int y = 0;
        if (!constructionAssets.isEmpty()) {
            content.child(createSectionText("Construction").pos(4, y));
            y += 16;
            for (CelestialManagedAsset constructionAsset : constructionAssets) {
                content.child(createConstructionRow(constructionAsset, contentWidth - 8).pos(4, y));
                y += ROW_HEIGHT + ROW_SPACING;
            }
            y += 4;
        }

        content.child(createSectionText("Assets").pos(4, y));
        y += 16;

        if (deployedAssets.isEmpty()) {
            content.child(createBodyText("No deployed assets", 0xFF9AA7B8).pos(8, y));
            return;
        }

        for (CelestialManagedAsset deployedAsset : deployedAssets) {
            content.child(createAssetRow(deployedAsset, contentWidth - 8).pos(4, y));
            y += ROW_HEIGHT + ROW_SPACING;
        }
    }

    private ParentWidget<?> createConstructionRow(CelestialManagedAsset asset, int rowWidth) {
        ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -8).height(ROW_HEIGHT)
            .background(drawable((context, x, y, width, height) -> Gui.drawRect(x, y, x + width, y + height, 0x55213144)));

        row.child(createAssetIconWidget(asset.kind(), 1.0f).pos(10, 9).size(16, 16));

        boolean deconstruction = asset.status() == CelestialAssetStatus.DECONSTRUCTION;
        int actionButtonsWidth = ICON_BUTTON_SIZE;
        int textWidth = rowWidth - 32 - actionButtonsWidth - 16;

        row.child(createNameButton(asset, textWidth).pos(32, 4));
        row.child(
            createBodyText((deconstruction ? "Stored: " : "Inventory: ") + callbacks.buildConstructionInventorySummary(asset), 0xFFD9E0FF)
                .pos(32, 18).width(textWidth));

        row.child(
            createGlyphButton(
                deconstruction ? AssetManagerButtonGlyph.SEND : AssetManagerButtonGlyph.CANCEL,
                deconstruction ? "Send To..." : "Cancel Build",
                true,
                () -> handleConstructionAction(asset)).pos(rowWidth - 34, 9));

        return row;
    }

    private ParentWidget<?> createAssetRow(CelestialManagedAsset asset, int rowWidth) {
        ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -8).height(ROW_HEIGHT)
            .background(drawable((context, x, y, width, height) -> Gui.drawRect(x, y, x + width, y + height, 0x55213144)));

        row.child(createAssetIconWidget(asset.kind(), 1.0f).pos(10, 9).size(16, 16));

        boolean manageable = callbacks.isManageableStationAsset(asset);
        int actionButtonsWidth = manageable ? (ICON_BUTTON_SIZE * 2 + 4) : ICON_BUTTON_SIZE;
        int textWidth = rowWidth - 32 - actionButtonsWidth - 16;

        row.child(createNameButton(asset, textWidth).pos(32, 4));
        row.child(
            createBodyText(
                trimToWidth(
                    callbacks.formatAssetKind(asset.kind()) + " | " + callbacks.formatAssetLocation(asset.location()),
                    textWidth),
                0xFFD9E0FF).pos(32, 16).width(textWidth));

        int buttonX = rowWidth - 34;
        if (manageable) {
            row.child(
                createGlyphButton(
                    AssetManagerButtonGlyph.MANAGE,
                    "Manage",
                    true,
                    () -> callbacks.openPendingAssetManagement(asset)).pos(buttonX - 28, 9));
        }
        row.child(
            createGlyphButton(
                AssetManagerButtonGlyph.DESTROY,
                "Destroy",
                true,
                () -> callbacks.openPendingAssetDestruction(asset)).pos(buttonX, 9));

        return row;
    }

    private ButtonWidget<?> createNameButton(CelestialManagedAsset asset, int width) {
        String text = trimToWidth(callbacks.formatAssetDisplayName(asset), Math.max(8, width));
        return new ScrollAwareButtonWidget().size(Math.max(8, width), 12)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY)
            .overlay(IKey.str(text).alignment(Alignment.CenterLeft).color(0xFFFFFFFF).shadow(true))
            .hoverOverlay(IKey.str(text).alignment(Alignment.CenterLeft).color(0xFF8CE4FF).shadow(true))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) {
                    return true;
                }
                callbacks.openPendingAssetRename(asset);
                return true;
            });
    }

    private boolean forwardActiveScroll(UpOrDown direction, int amount) {
        return activeScrollWidget != null && activeScrollWidget.onMouseScroll(direction, amount);
    }

    private ButtonWidget<?> createBackdropButton() {
        return new ButtonWidget<>().pos(0, 0)
            .widthRel(1f)
            .heightRel(1f)
            .background(IDrawable.EMPTY)
            .hoverBackground(IDrawable.EMPTY)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) {
                    return true;
                }
                if (state.hasBlockingModal()) {
                    callbacks.dismissPendingModalByOutsideClick();
                } else {
                    callbacks.closeAssetManagement();
                }
                return true;
            });
    }

    private ParentWidget<?> createModalRoot(ModalBounds bounds) {
        return createModalRoot(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF121B28, 0xFF59BFD9);
    }

    private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom) {
        return createModalRoot(left, top, right, bottom, 0xFF121B28, 0xFF59BFD9);
    }

    private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom, int backgroundColor, int accentColor) {
        return createModalRoot(left, top, right, bottom, backgroundColor, accentColor, 0xFF22324A);
    }

    private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom, int backgroundColor, int accentColor,
        int headerColor) {
        ParentWidget<?> modal = new ParentWidget<>().pos(left, top)
            .size(right - left, bottom - top);
        PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
            .widthRel(1f)
            .heightRel(1f)
            .background(createModalBackgroundDrawable(backgroundColor, headerColor));
        modal.child(backgroundLayer);
        modal.child(WidgetOutline.create(backgroundLayer, 3, accentColor));
        return modal;
    }

    private TextWidget<?> createTitleText(String text) {
        return new TextWidget<>(text).color(0xFFFFFFFF).shadow(true);
    }

    private TextWidget<?> createSectionText(String text) {
        return new TextWidget<>(text).color(0xFF5A63FF).shadow(true);
    }

    private TextWidget<?> createBodyText(String text, int color) {
        return new TextWidget<>(text).color(color).shadow(true);
    }

    private ButtonWidget<?> createAssetKindButton(CelestialAssetKind kind, String tooltip, boolean enabled, Runnable action) {
        return createIconButton(kind, AssetManagerButtonGlyph.NONE, tooltip, enabled, action);
    }

    private ButtonWidget<?> createGlyphButton(AssetManagerButtonGlyph glyph, String tooltip, boolean enabled,
        Runnable action) {
        return createIconButton(null, glyph, tooltip, enabled, action);
    }

    private ButtonWidget<?> createIconButton(CelestialAssetKind iconKind, AssetManagerButtonGlyph glyph, String tooltip,
        boolean enabled, Runnable action) {
        ButtonWidget<?> button = new ScrollAwareButtonWidget().size(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
            .background(createButtonBackground(enabled, false))
            .hoverBackground(createButtonBackground(enabled, true))
            .tooltip(t -> t.addLine(tooltip))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) {
                    return true;
                }
                if (!enabled) {
                    return true;
                }
                action.run();
                return true;
            });
        if (iconKind != null) {
            button.overlay(createAssetIconDrawable(iconKind, enabled ? 1.0f : 0.45f));
        } else {
            button.overlay(createGlyphDrawable(glyph, enabled ? 0xFFFFFFFF : 0xFF94A0AF));
        }
        return button;
    }

    private ButtonWidget<?> createFooterButton(String label, boolean enabled, Runnable action) {
        return createTextButton(label, enabled, action, false);
    }

    private ButtonWidget<?> createDangerFooterButton(String label, Runnable action) {
        return createTextButton(label, true, action, true);
    }

    private ButtonWidget<?> createTextButton(String label, boolean enabled, Runnable action, boolean danger) {
        return new ScrollAwareButtonWidget().background(createTextButtonBackground(enabled, false, danger))
            .hoverBackground(createTextButtonBackground(enabled, true, danger))
            .overlay(IKey.str(label).alignment(Alignment.Center).color(enabled ? 0xFFFFFFFF : 0xFF94A0AF).shadow(true))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) {
                    return true;
                }
                if (!enabled) {
                    return true;
                }
                action.run();
                return true;
            });
    }

    private IDrawable createButtonBackground(boolean enabled, boolean hovered) {
        int bg = !enabled ? 0xFF243041 : hovered ? 0xFF3A5678 : 0xFF2D435D;
        int border = enabled ? 0xFF7FB6FF : 0xFF556577;
        return createRectFrameDrawable(bg, border);
    }

    private IDrawable createTextButtonBackground(boolean enabled, boolean hovered, boolean danger) {
        if (danger) {
            int bg = hovered ? 0xFF6D252D : 0xFF5A1E24;
            return createRectFrameDrawable(bg, 0xFFFF5A5A);
        }
        return createButtonBackground(enabled, hovered);
    }

    private IDrawable createRectFrameDrawable(int backgroundColor, int borderColor) {
        return drawable((context, x, y, width, height) -> {
            Gui.drawRect(x, y, x + width, y + height, backgroundColor);
            Gui.drawRect(x, y, x + width, y + 1, borderColor);
            Gui.drawRect(x, y + height - 1, x + width, y + height, borderColor);
            Gui.drawRect(x, y, x + 1, y + height, borderColor);
            Gui.drawRect(x + width - 1, y, x + width, y + height, borderColor);
        });
    }

    private IDrawable createAssetIconDrawable(CelestialAssetKind kind, float alpha) {
        return drawable((context, x, y, width, height) -> callbacks.drawAssetIcon(kind, x + (width - 14) / 2, y + (height - 14) / 2, 14, alpha));
    }

    private Widget<?> createAssetIconWidget(CelestialAssetKind kind, float alpha) {
        return createAssetIconDrawable(kind, alpha).asWidget();
    }

    private IDrawable createGlyphDrawable(AssetManagerButtonGlyph glyph, int color) {
        return drawable((context, x, y, width, height) -> drawGlyph(x, y, width, height, glyph, color));
    }

    private Widget<?> createCenteredLargeText(String text, float scale, int color) {
        return drawable((context, x, y, width, height) -> {
            Minecraft mc = Minecraft.getMinecraft();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + width / 2f, y, 0);
            GlStateManager.scale(scale, scale, 1f);
            float textWidth = mc.fontRenderer.getStringWidth(text);
            mc.fontRenderer.drawStringWithShadow(text, Math.round(-textWidth / 2f), 0, color);
            GlStateManager.popMatrix();
        }).asWidget();
    }

    private void drawGlyph(int x, int y, int width, int height, AssetManagerButtonGlyph glyph, int color) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        switch (glyph) {
            case CANCEL:
            case DESTROY:
            case CLOSE:
                drawGlyphX(centerX, centerY, 5, color);
                break;
            case SEND:
                drawGlyphSend(centerX, centerY, color);
                break;
            case MANAGE:
                drawGlyphManage(centerX, centerY, color);
                break;
            case NONE:
                break;
        }
    }

    private void drawGlyphX(int centerX, int centerY, int radius, int color) {
        for (int i = -radius; i <= radius; i++) {
            Gui.drawRect(centerX + i, centerY + i, centerX + i + 1, centerY + i + 1, color);
            Gui.drawRect(centerX + i, centerY - i, centerX + i + 1, centerY - i + 1, color);
        }
    }

    private void drawGlyphSend(int centerX, int centerY, int color) {
        Gui.drawRect(centerX - 5, centerY - 1, centerX + 3, centerY + 1, color);
        Gui.drawRect(centerX + 2, centerY - 3, centerX + 3, centerY + 4, color);
        Gui.drawRect(centerX + 3, centerY - 2, centerX + 4, centerY + 3, color);
        Gui.drawRect(centerX + 4, centerY - 1, centerX + 5, centerY + 2, color);
        Gui.drawRect(centerX + 5, centerY, centerX + 6, centerY + 1, color);
    }

    private void drawGlyphManage(int centerX, int centerY, int color) {
        Gui.drawRect(centerX - 5, centerY - 4, centerX + 6, centerY - 3, color);
        Gui.drawRect(centerX - 5, centerY, centerX + 6, centerY + 1, color);
        Gui.drawRect(centerX - 5, centerY + 4, centerX + 6, centerY + 5, color);
    }

    private IDrawable createModalBackgroundDrawable(int backgroundColor, int headerColor) {
        return drawable((context, x, y, width, height) -> {
            Gui.drawRect(x, y, x + width, y + height, backgroundColor);
            if (headerColor >= 0) {
                Gui.drawRect(x, y, x + width, y + HEADER_HEIGHT, headerColor);
            }
        });
    }

    private List<CelestialManagedAsset> getConstructionAssets(List<CelestialManagedAsset> assets) {
        List<CelestialManagedAsset> matching = new ArrayList<>();
        for (CelestialManagedAsset asset : assets) {
            if (asset.status() == CelestialAssetStatus.CONSTRUCTION_SITE
                || asset.status() == CelestialAssetStatus.DECONSTRUCTION) {
                matching.add(asset);
            }
        }
        return matching;
    }

    private List<CelestialManagedAsset> getOperationalAssets(List<CelestialManagedAsset> assets) {
        List<CelestialManagedAsset> matching = new ArrayList<>();
        for (CelestialManagedAsset asset : assets) {
            if (asset.status() == CelestialAssetStatus.OPERATIONAL) {
                matching.add(asset);
            }
        }
        return matching;
    }

    private void handleConstructionAction(CelestialManagedAsset asset) {
        if (asset.status() == CelestialAssetStatus.DECONSTRUCTION) {
            callbacks.openPendingResourceTransfer(asset);
            return;
        }
        if (callbacks.isCreativeBuildModeEnabled()) {
            CelestialAssetStore.cancelConstruction(asset.assetId());
            callbacks.showActionStatus("Construction canceled");
            return;
        }
        if (callbacks.hasStoredConstructionResources(asset)) {
            callbacks.openPendingConstructionCancellation(asset);
            return;
        }
        CelestialAssetStore.cancelConstruction(asset.assetId());
        callbacks.showActionStatus("Construction canceled");
    }

    private void updateModalBounds(int left, int top, int right, int bottom) {
        modalLeft = left;
        modalTop = top;
        modalRight = right;
        modalBottom = bottom;
    }

    private void clearBounds() {
        modalLeft = modalTop = modalRight = modalBottom = 0;
        scrollLeft = scrollTop = scrollRight = scrollBottom = 0;
    }

    private int getAvailableOverlayWidth() {
        int width = getArea().width;
        if (hasParent()) {
            width = Math.max(width, getParentArea().width - Math.max(0, getArea().rx));
        }
        return width;
    }

    private int getAvailableOverlayHeight() {
        int height = getArea().height;
        if (hasParent()) {
            height = Math.max(height, getParentArea().height - Math.max(0, getArea().ry));
        }
        return height;
    }

    private void clearMainPanelState() {
        mainScrollWidget = null;
        mainScrollContent = null;
        mainScrollData = null;
        mainContentWidth = 0;
        mainContentHeight = 0;
    }

    private int buttonWidth(ButtonRect rect) {
        return rect.right - rect.left;
    }

    private String trimToWidth(String text, int width) {
        return Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, width);
    }

    private IDrawable drawable(DrawCommand drawCommand) {
        return (context, x, y, width, height, widgetTheme) -> drawCommand.draw(context, x, y, width, height);
    }

    private final class PassiveRow extends ParentWidget<PassiveRow> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }

    private final class PassiveLayer extends ParentWidget<PassiveLayer> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }

    private final class ScrollAwareButtonWidget extends ButtonWidget<ScrollAwareButtonWidget> {

        @Override
        public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
            return super.onMouseScroll(scrollDirection, amount) || forwardActiveScroll(scrollDirection, amount);
        }
    }

    @FunctionalInterface
    private interface DrawCommand {

        void draw(GuiContext context, int x, int y, int width, int height);
    }

    private enum AssetManagerButtonGlyph {
        NONE,
        CLOSE,
        CANCEL,
        SEND,
        DESTROY,
        MANAGE
    }
}
