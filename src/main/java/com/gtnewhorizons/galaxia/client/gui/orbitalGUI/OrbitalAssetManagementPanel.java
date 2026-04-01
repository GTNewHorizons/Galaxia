package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyAssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

final class OrbitalAssetManagementPanel {

    interface Callbacks {

        void closeAssetManagement();

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

        void drawTooltip(String tooltip, int mouseX, int mouseY);

        void createBaseStation(OrbitalCelestialBody body);

        void triggerAssetCreation(OrbitalCelestialBody body, CelestialAssetKind kind, boolean openManagementFirst);

        void openPendingAssetRename(CelestialManagedAsset asset);

        void openPendingConstructionCancellation(CelestialManagedAsset asset);

        void openPendingResourceTransfer(CelestialManagedAsset asset);

        void openPendingAssetManagement(CelestialManagedAsset asset);

        void openPendingAssetDestruction(CelestialManagedAsset asset);

        void showActionStatus(String message);
    }

    private static final int MODAL_MAX_WIDTH = 520;
    private static final int MODAL_MAX_HEIGHT = 420;
    private static final int MODAL_MARGIN_X = 80;
    private static final int MODAL_MARGIN_Y = 60;
    private static final int SITE_ROW_HEIGHT = 42;
    private static final int ROW_SPACING = 6;

    private final Callbacks callbacks;

    OrbitalAssetManagementPanel(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    AssetManagementLayout getLayout(int areaWidth, int areaHeight, OrbitalAssetUiState state) {
        if (!state.isAssetManagementOpen()) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        CelestialBodyAssetState assetState = CelestialAssetStore.getState(state.assetManagementBody.id());
        int width = Math.min(MODAL_MAX_WIDTH, areaWidth - MODAL_MARGIN_X);
        int height = Math.min(MODAL_MAX_HEIGHT, areaHeight - MODAL_MARGIN_Y);
        int left = (areaWidth - width) / 2;
        int top = (areaHeight - height) / 2;
        int right = left + width;
        int bottom = top + height;

        ButtonRect closeButton = new ButtonRect(right - 28, top + 6, right - 10, top + 24);
        ButtonRect createStationButton = new ButtonRect(left + 14, top + 30, left + 36, top + 52);
        ButtonRect createAutomatedStationButton = new ButtonRect(left + 42, top + 30, left + 64, top + 52);
        ButtonRect createOutpostButton = new ButtonRect(left + 70, top + 30, left + 92, top + 52);
        int contentLeft = left + 10;
        int contentTop = top + 54;
        int contentRight = right - 24;
        int contentBottom = bottom - 12;
        int contentViewportHeight = Math.max(1, contentBottom - contentTop);

        List<CelestialManagedAsset> constructionAssets = new ArrayList<>();
        constructionAssets.addAll(getAssetsWithStatus(assetState.assets(), CelestialAssetStatus.CONSTRUCTION_SITE));
        constructionAssets.addAll(getAssetsWithStatus(assetState.assets(), CelestialAssetStatus.DECONSTRUCTION));
        List<CelestialManagedAsset> deployedAssets = getAssetsWithStatus(assetState.assets(), CelestialAssetStatus.OPERATIONAL);

        boolean showConstructionSection = !constructionAssets.isEmpty();
        int siteRowsBaseY = 16;
        int constructionRowsHeight = constructionAssets.isEmpty()
            ? 0
            : constructionAssets.size() * SITE_ROW_HEIGHT + Math.max(0, constructionAssets.size() - 1) * ROW_SPACING;
        int afterConstructionBaseY = showConstructionSection ? siteRowsBaseY + constructionRowsHeight : 0;
        int assetsHeaderBaseY = showConstructionSection ? afterConstructionBaseY + 4 : 0;
        int noAssetsMessageBaseY = assetsHeaderBaseY + 16;
        int assetRowsBaseY = assetsHeaderBaseY + 16;
        int deployedRowsHeight = deployedAssets.isEmpty()
            ? 0
            : deployedAssets.size() * SITE_ROW_HEIGHT + Math.max(0, deployedAssets.size() - 1) * ROW_SPACING;
        int totalContentHeight = deployedAssets.isEmpty()
            ? noAssetsMessageBaseY + 24
            : assetRowsBaseY + deployedRowsHeight + 8;
        int maxScroll = Math.max(0, totalContentHeight - contentViewportHeight);
        state.assetManagementScroll = clamp(state.assetManagementScroll, 0, maxScroll);

        List<ConstructionSiteRow> siteRows = new ArrayList<>();
        for (int i = 0; i < constructionAssets.size(); i++) {
            CelestialManagedAsset site = constructionAssets.get(i);
            int rowTop = contentTop + siteRowsBaseY + i * (SITE_ROW_HEIGHT + ROW_SPACING) - state.assetManagementScroll;
            int rowBottom = rowTop + SITE_ROW_HEIGHT;
            if (rowTop < contentTop || rowBottom > contentBottom) {
                continue;
            }
            int rowLeft = left + 14;
            int rowRight = contentRight;
            int nameWidth = mc.fontRenderer.getStringWidth(callbacks.formatAssetDisplayName(site));
            ButtonRect nameClickArea = new ButtonRect(
                rowLeft + 30,
                rowTop + 4,
                Math.min(rowRight - 42, rowLeft + 32 + nameWidth),
                rowTop + 16);
            siteRows.add(
                new ConstructionSiteRow(
                    site,
                    rowLeft,
                    rowTop,
                    rowRight,
                    rowBottom,
                    nameClickArea,
                    new ButtonRect(rowRight - 34, rowTop + 9, rowRight - 10, rowTop + 33),
                    site.status() == CelestialAssetStatus.DECONSTRUCTION
                        ? ConstructionRowActionType.SEND_RESOURCES
                        : ConstructionRowActionType.CANCEL_BUILD));
        }

        List<AssetRow> assetRows = new ArrayList<>();
        for (int i = 0; i < deployedAssets.size(); i++) {
            CelestialManagedAsset asset = deployedAssets.get(i);
            int rowTop = contentTop + assetRowsBaseY + i * (SITE_ROW_HEIGHT + ROW_SPACING) - state.assetManagementScroll;
            int rowBottom = rowTop + SITE_ROW_HEIGHT;
            if (rowTop < contentTop || rowBottom > contentBottom) {
                continue;
            }
            int rowLeft = left + 14;
            int rowRight = contentRight;
            int nameWidth = mc.fontRenderer.getStringWidth(callbacks.formatAssetDisplayName(asset));
            boolean manageable = callbacks.isManageableStationAsset(asset);
            ButtonRect manageButton = manageable ? new ButtonRect(rowRight - 62, rowTop + 9, rowRight - 38, rowTop + 33) : null;
            assetRows.add(
                new AssetRow(
                    asset,
                    rowLeft,
                    rowTop,
                    rowRight,
                    rowBottom,
                    new ButtonRect(
                        rowLeft + 30,
                        rowTop + 4,
                        Math.min(manageable ? rowRight - 70 : rowRight - 42, rowLeft + 32 + nameWidth),
                        rowTop + 16),
                    manageButton,
                    new ButtonRect(rowRight - 34, rowTop + 9, rowRight - 10, rowTop + 33)));
        }

        return new AssetManagementLayout(
            left,
            top,
            right,
            bottom,
            contentLeft,
            contentTop,
            contentRight,
            contentBottom,
            showConstructionSection,
            contentTop - state.assetManagementScroll,
            contentTop + assetsHeaderBaseY - state.assetManagementScroll,
            contentTop + noAssetsMessageBaseY - state.assetManagementScroll,
            totalContentHeight,
            maxScroll,
            closeButton,
            createStationButton,
            createAutomatedStationButton,
            createOutpostButton,
            siteRows,
            assetRows);
    }

    void draw(AssetManagementLayout layout, OrbitalAssetUiState state, int areaWidth, int areaHeight, int mouseX, int mouseY) {
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        String hoveredTooltip = null;

        drawPanelFrame(areaWidth, areaHeight, layout);

        int titleX = layout.left + 12;
        int titleY = layout.top + 10;
        String title = "Manage Assets";
        mc.fontRenderer.drawStringWithShadow(title, titleX, titleY, 0xFFFFFFFF);

        int titleRight = titleX + mc.fontRenderer.getStringWidth(title);
        int assetNameMaxWidth = Math.max(0, layout.closeButton.left - 12 - (titleRight + 12));
        if (assetNameMaxWidth > 0) {
            String assetName = mc.fontRenderer.trimStringToWidth(state.assetManagementBody.displayName(), assetNameMaxWidth);
            int assetNameWidth = mc.fontRenderer.getStringWidth(assetName);
            int assetNameX = Math.max(titleRight + 12, layout.closeButton.left - 12 - assetNameWidth);
            mc.fontRenderer.drawStringWithShadow(assetName, assetNameX, titleY, 0xFFD9E0FF);
        }

        hoveredTooltip = drawIconButton(
            layout.closeButton,
            null,
            AssetManagerButtonGlyph.CLOSE,
            "Close",
            true,
            mouseX,
            mouseY,
            hoveredTooltip);
        hoveredTooltip = drawIconButton(
            layout.createStationButton,
            CelestialAssetKind.STATION,
            AssetManagerButtonGlyph.NONE,
            "Create Station",
            callbacks.canCreateBaseStation(state.assetManagementBody),
            mouseX,
            mouseY,
            hoveredTooltip);
        if (callbacks.isGT5AutomationAvailable()) {
            hoveredTooltip = drawIconButton(
                layout.createAutomatedStationButton,
                CelestialAssetKind.AUTOMATED_STATION,
                AssetManagerButtonGlyph.NONE,
                "Create Automated Station",
                callbacks.canCreateAutomatedStation(state.assetManagementBody),
                mouseX,
                mouseY,
                hoveredTooltip);
            hoveredTooltip = drawIconButton(
                layout.createOutpostButton,
                CelestialAssetKind.AUTOMATED_OUTPOST,
                AssetManagerButtonGlyph.NONE,
                "Create Automated Outpost",
                callbacks.canCreateAutomatedOutpost(state.assetManagementBody),
                mouseX,
                mouseY,
                hoveredTooltip);
        } else {
            mc.fontRenderer.drawStringWithShadow(
                "GT5U required for automated assets",
                layout.left + 104,
                layout.top + 36,
                0xFF9AA7B8);
        }

        Gui.drawRect(layout.contentLeft, layout.contentTop, layout.contentRight, layout.contentBottom, 0x3318273A);
        if (layout.showConstructionSection) {
            if (layout.constructionHeaderY >= layout.contentTop && layout.constructionHeaderY <= layout.contentBottom - 10) {
                mc.fontRenderer.drawStringWithShadow("Construction", layout.left + 14, layout.constructionHeaderY, 0xFF5A63FF);
            }
            for (ConstructionSiteRow row : layout.siteRows) {
                CelestialManagedAsset site = row.asset;
                Gui.drawRect(row.left, row.top, row.right, row.bottom, 0x55213144);
                callbacks.drawAssetIcon(site.kind(), row.left + 10, row.top + 9, 16, 1.0f);
                mc.fontRenderer.drawStringWithShadow(
                    callbacks.formatAssetDisplayName(site),
                    row.left + 32,
                    row.top + 6,
                    row.nameClickArea.contains(mouseX, mouseY) ? 0xFF8CE4FF : 0xFFFFFFFF);
                mc.fontRenderer.drawStringWithShadow(
                    (site.status() == CelestialAssetStatus.DECONSTRUCTION ? "Stored: " : "Inventory: ")
                        + callbacks.buildConstructionInventorySummary(site),
                    row.left + 32,
                    row.top + 18,
                    0xFFD9E0FF);
                hoveredTooltip = drawIconButton(
                    row.actionButton,
                    null,
                    row.actionType == ConstructionRowActionType.SEND_RESOURCES
                        ? AssetManagerButtonGlyph.SEND
                        : AssetManagerButtonGlyph.CANCEL,
                    row.actionType.buttonLabel,
                    true,
                    mouseX,
                    mouseY,
                    hoveredTooltip);
            }
        }

        if (layout.assetsHeaderY >= layout.contentTop && layout.assetsHeaderY <= layout.contentBottom - 10) {
            mc.fontRenderer.drawStringWithShadow("Assets", layout.left + 14, layout.assetsHeaderY, 0xFF5A63FF);
        }
        if (layout.assetRows.isEmpty() && layout.noAssetsMessageY >= layout.contentTop
            && layout.noAssetsMessageY <= layout.contentBottom - 10) {
            mc.fontRenderer.drawStringWithShadow("No deployed assets", layout.left + 18, layout.noAssetsMessageY, 0xFF9AA7B8);
        } else {
            for (AssetRow row : layout.assetRows) {
                CelestialManagedAsset asset = row.asset;
                Gui.drawRect(row.left, row.top, row.right, row.bottom, 0x55213144);
                callbacks.drawAssetIcon(asset.kind(), row.left + 10, row.top + 9, 16, 1.0f);
                mc.fontRenderer.drawStringWithShadow(
                    callbacks.formatAssetDisplayName(asset),
                    row.left + 32,
                    row.top + 6,
                    row.nameClickArea.contains(mouseX, mouseY) ? 0xFF8CE4FF : 0xFFFFFFFF);
                mc.fontRenderer.drawStringWithShadow(
                    callbacks.formatAssetKind(asset.kind()) + " | " + callbacks.formatAssetLocation(asset.location()),
                    row.left + 32,
                    row.top + 16,
                    0xFFD9E0FF);
                if (row.manageButton != null) {
                    hoveredTooltip = drawIconButton(
                        row.manageButton,
                        null,
                        AssetManagerButtonGlyph.MANAGE,
                        "Manage",
                        true,
                        mouseX,
                        mouseY,
                        hoveredTooltip);
                }
                hoveredTooltip = drawIconButton(
                    row.destroyButton,
                    null,
                    AssetManagerButtonGlyph.DESTROY,
                    "Destroy",
                    true,
                    mouseX,
                    mouseY,
                    hoveredTooltip);
            }
        }

        drawScrollbar(layout, state.assetManagementScroll);

        if (hoveredTooltip != null && !state.hasBlockingModal()) {
            callbacks.drawTooltip(hoveredTooltip, mouseX, mouseY);
        }
    }

    boolean handleClick(AssetManagementLayout layout, OrbitalAssetUiState state, int localMouseX, int localMouseY) {
        if (layout == null) {
            return false;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            callbacks.closeAssetManagement();
            return true;
        }

        if (layout.closeButton.contains(localMouseX, localMouseY)) {
            callbacks.closeAssetManagement();
            return true;
        }
        if (layout.createStationButton.contains(localMouseX, localMouseY) && callbacks.canCreateBaseStation(state.assetManagementBody)) {
            callbacks.createBaseStation(state.assetManagementBody);
            return true;
        }
        if (layout.createAutomatedStationButton.contains(localMouseX, localMouseY)
            && callbacks.canCreateAutomatedStation(state.assetManagementBody)) {
            callbacks.triggerAssetCreation(state.assetManagementBody, CelestialAssetKind.AUTOMATED_STATION, false);
            return true;
        }
        if (layout.createOutpostButton.contains(localMouseX, localMouseY)
            && callbacks.canCreateAutomatedOutpost(state.assetManagementBody)) {
            callbacks.triggerAssetCreation(state.assetManagementBody, CelestialAssetKind.AUTOMATED_OUTPOST, false);
            return true;
        }

        for (ConstructionSiteRow row : layout.siteRows) {
            if (row.nameClickArea.contains(localMouseX, localMouseY)) {
                callbacks.openPendingAssetRename(row.asset);
                return true;
            }
            if (row.actionButton.contains(localMouseX, localMouseY)) {
                if (row.actionType == ConstructionRowActionType.CANCEL_BUILD) {
                    if (callbacks.isCreativeBuildModeEnabled()) {
                        CelestialAssetStore.cancelConstruction(row.asset.assetId());
                        callbacks.showActionStatus("Construction canceled");
                    } else if (callbacks.hasStoredConstructionResources(row.asset)) {
                        callbacks.openPendingConstructionCancellation(row.asset);
                    } else {
                        CelestialAssetStore.cancelConstruction(row.asset.assetId());
                        callbacks.showActionStatus("Construction canceled");
                    }
                } else if (row.actionType == ConstructionRowActionType.SEND_RESOURCES) {
                    callbacks.openPendingResourceTransfer(row.asset);
                }
                return true;
            }
        }

        for (AssetRow row : layout.assetRows) {
            if (row.nameClickArea.contains(localMouseX, localMouseY)) {
                callbacks.openPendingAssetRename(row.asset);
                return true;
            }
            if (row.manageButton != null && row.manageButton.contains(localMouseX, localMouseY)) {
                callbacks.openPendingAssetManagement(row.asset);
                return true;
            }
            if (row.destroyButton.contains(localMouseX, localMouseY)) {
                callbacks.openPendingAssetDestruction(row.asset);
                return true;
            }
        }

        return true;
    }

    private void drawPanelFrame(int areaWidth, int areaHeight, AssetManagementLayout layout) {
        Gui.drawRect(0, 0, areaWidth, areaHeight, 0xAA09111B);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF121B28);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 3, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.bottom - 3, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.left + 3, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.right - 3, layout.top, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 28, 0xFF22324A);
    }

    private void drawScrollbar(AssetManagementLayout layout, int scroll) {
        if (layout.maxScroll <= 0) {
            return;
        }

        int trackLeft = layout.contentRight + 4;
        int trackRight = trackLeft + 6;
        Gui.drawRect(trackLeft, layout.contentTop, trackRight, layout.contentBottom, 0x55304157);

        int viewportHeight = Math.max(1, layout.contentBottom - layout.contentTop);
        int thumbHeight = Math.max(24, Math.round((viewportHeight / (float) layout.totalContentHeight) * viewportHeight));
        int travel = Math.max(0, viewportHeight - thumbHeight);
        int thumbTop = layout.contentTop + Math.round((scroll / (float) layout.maxScroll) * travel);
        Gui.drawRect(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0xFF59BFD9);
    }

    private String drawIconButton(ButtonRect rect, CelestialAssetKind iconKind, AssetManagerButtonGlyph glyph, String tooltip,
        boolean enabled, int mouseX, int mouseY, String currentTooltip) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int bg = !enabled ? 0xFF243041 : hovered ? 0xFF3A5678 : 0xFF2D435D;
        int border = enabled ? 0xFF7FB6FF : 0xFF556577;
        Gui.drawRect(rect.left, rect.top, rect.right, rect.bottom, bg);
        Gui.drawRect(rect.left, rect.top, rect.right, rect.top + 1, border);
        Gui.drawRect(rect.left, rect.bottom - 1, rect.right, rect.bottom, border);
        Gui.drawRect(rect.left, rect.top, rect.left + 1, rect.bottom, border);
        Gui.drawRect(rect.right - 1, rect.top, rect.right, rect.bottom, border);

        if (iconKind != null) {
            callbacks.drawAssetIcon(iconKind, rect.left + (rect.right - rect.left - 14) / 2, rect.top + (rect.bottom - rect.top - 14) / 2, 14,
                enabled ? 1.0f : 0.45f);
        } else if (glyph != AssetManagerButtonGlyph.NONE) {
            drawGlyph(rect, glyph, enabled ? 0xFFFFFFFF : 0xFF94A0AF);
        }

        if (hovered && currentTooltip == null) {
            return tooltip;
        }
        return currentTooltip;
    }

    private void drawGlyph(ButtonRect rect, AssetManagerButtonGlyph glyph, int color) {
        int centerX = (rect.left + rect.right) / 2;
        int centerY = (rect.top + rect.bottom) / 2;
        switch (glyph) {
            case CLOSE, CANCEL, DESTROY -> drawGlyphX(centerX, centerY, 5, color);
            case SEND -> drawGlyphSend(centerX, centerY, color);
            case MANAGE -> drawGlyphManage(centerX, centerY, color);
            case NONE -> {}
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

    private List<CelestialManagedAsset> getAssetsWithStatus(List<CelestialManagedAsset> assets, CelestialAssetStatus status) {
        List<CelestialManagedAsset> matching = new ArrayList<>();
        for (CelestialManagedAsset asset : assets) {
            if (asset.status() == status) {
                matching.add(asset);
            }
        }
        return matching;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
