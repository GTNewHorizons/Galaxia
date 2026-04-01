package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

final class OrbitalAssetPendingModalPanel {

    interface Callbacks {

        void drawAssetIcon(CelestialAssetKind kind, int x, int y, int size, float alpha);

        String formatAssetKind(CelestialAssetKind kind);

        String formatAssetDisplayName(CelestialManagedAsset asset);

        void confirmPendingAssetCreation();

        void closePendingAssetRename();

        void confirmPendingAssetRename();

        void showActionStatus(String message);
    }

    private final Callbacks callbacks;

    OrbitalAssetPendingModalPanel(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    void draw(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        drawPendingAssetCreationModal(state, areaWidth, areaHeight);
        drawPendingAssetDestructionModal(state, areaWidth, areaHeight);
        drawPendingConstructionCancellationModal(state, areaWidth, areaHeight);
        drawPendingResourceTransferModal(state, areaWidth, areaHeight);
        drawPendingAssetManagementModal(state, areaWidth, areaHeight);
        drawPendingAssetRenameModal(state, areaWidth, areaHeight);
    }

    boolean handleClick(OrbitalAssetUiState state, int areaWidth, int areaHeight, int localMouseX, int localMouseY) {
        if (state.pendingAssetRename != null) {
            return handlePendingAssetRenameClick(state, areaWidth, areaHeight, localMouseX, localMouseY);
        }
        if (state.pendingResourceTransfer != null) {
            return handlePendingResourceTransferClick(state, areaWidth, areaHeight, localMouseX, localMouseY);
        }
        if (state.pendingAssetManagement != null) {
            return handlePendingAssetManagementClick(state, areaWidth, areaHeight, localMouseX, localMouseY);
        }
        if (state.pendingConstructionCancellation != null) {
            return handlePendingConstructionCancellationClick(state, areaWidth, areaHeight, localMouseX, localMouseY);
        }
        if (state.pendingAssetDestruction != null) {
            return handlePendingAssetDestructionClick(state, areaWidth, areaHeight, localMouseX, localMouseY);
        }
        if (state.pendingAssetCreation != null) {
            return handlePendingAssetCreationClick(state, areaWidth, areaHeight, localMouseX, localMouseY);
        }
        return false;
    }

    PendingAssetRenameLayout getPendingAssetRenameLayout(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        if (state.pendingAssetRename == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(areaWidth, areaHeight, 340, 126);
        return new PendingAssetRenameLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new ButtonRect(bounds.left + 14, bounds.top + 54, bounds.right - 14, bounds.top + 76),
            createLeftFooterButton(bounds, 110),
            createRightFooterButton(bounds, 110));
    }

    private boolean handlePendingAssetCreationClick(OrbitalAssetUiState state, int areaWidth, int areaHeight, int localMouseX,
        int localMouseY) {
        PendingAssetCreationLayout layout = getPendingAssetCreationLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            state.pendingAssetCreation = null;
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            state.pendingAssetCreation = null;
            return true;
        }
        if (layout.confirmButton.contains(localMouseX, localMouseY)) {
            callbacks.confirmPendingAssetCreation();
            return true;
        }
        return true;
    }

    private boolean handlePendingAssetRenameClick(OrbitalAssetUiState state, int areaWidth, int areaHeight, int localMouseX,
        int localMouseY) {
        PendingAssetRenameLayout layout = getPendingAssetRenameLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            callbacks.closePendingAssetRename();
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            callbacks.closePendingAssetRename();
            return true;
        }
        if (layout.confirmButton.contains(localMouseX, localMouseY)) {
            callbacks.confirmPendingAssetRename();
            return true;
        }
        return true;
    }

    private boolean handlePendingAssetDestructionClick(OrbitalAssetUiState state, int areaWidth, int areaHeight, int localMouseX,
        int localMouseY) {
        PendingAssetDestructionLayout layout = getPendingAssetDestructionLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            state.pendingAssetDestruction = null;
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            state.pendingAssetDestruction = null;
            return true;
        }
        if (layout.destroyButton.contains(localMouseX, localMouseY)) {
            if (!state.pendingAssetDestruction.armed) {
                state.pendingAssetDestruction = new PendingAssetDestruction(state.pendingAssetDestruction.asset, true);
            } else {
                CelestialAssetStore.destroyAsset(state.pendingAssetDestruction.asset.assetId());
                callbacks.showActionStatus("Asset destroyed");
                state.pendingAssetDestruction = null;
            }
            return true;
        }
        return true;
    }

    private boolean handlePendingConstructionCancellationClick(OrbitalAssetUiState state, int areaWidth, int areaHeight,
        int localMouseX, int localMouseY) {
        PendingConstructionCancellationLayout layout = getPendingConstructionCancellationLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            state.pendingConstructionCancellation = null;
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            state.pendingConstructionCancellation = null;
            return true;
        }
        if (layout.confirmButton.contains(localMouseX, localMouseY)) {
            CelestialAssetStore.startDeconstruction(state.pendingConstructionCancellation.asset.assetId());
            callbacks.showActionStatus("Construction site converted to deconstruction");
            state.pendingConstructionCancellation = null;
            return true;
        }
        return true;
    }

    private boolean handlePendingResourceTransferClick(OrbitalAssetUiState state, int areaWidth, int areaHeight, int localMouseX,
        int localMouseY) {
        PendingResourceTransferLayout layout = getPendingResourceTransferLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            state.pendingResourceTransfer = null;
            return true;
        }
        if (layout.closeButton.contains(localMouseX, localMouseY)) {
            state.pendingResourceTransfer = null;
            return true;
        }
        for (TransferTargetRow row : layout.rows) {
            if (row.sendButton.contains(localMouseX, localMouseY)) {
                callbacks.showActionStatus("Resource transfer planning is not implemented yet");
                state.pendingResourceTransfer = null;
                return true;
            }
        }
        return true;
    }

    private boolean handlePendingAssetManagementClick(OrbitalAssetUiState state, int areaWidth, int areaHeight, int localMouseX,
        int localMouseY) {
        PendingAssetManagementLayout layout = getPendingAssetManagementLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            state.pendingAssetManagement = null;
            return true;
        }
        if (layout.closeButton.contains(localMouseX, localMouseY)) {
            state.pendingAssetManagement = null;
            return true;
        }
        return true;
    }

    private void drawPendingAssetCreationModal(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        PendingAssetCreationLayout layout = getPendingAssetCreationLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        drawFramedModal(areaWidth, areaHeight, layout.left, layout.top, layout.right, layout.bottom, 0x88000000, 0xFF121B28,
            0xFF59BFD9);

        callbacks.drawAssetIcon(state.pendingAssetCreation.kind, layout.left + 12, layout.top + 10, 18, 1.0f);
        mc.fontRenderer.drawStringWithShadow(
            "Confirm " + callbacks.formatAssetKind(state.pendingAssetCreation.kind),
            layout.left + 36,
            layout.top + 10,
            0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            state.pendingAssetCreation.displayName,
            layout.left + 36,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow("Required resources", layout.left + 12, layout.top + 52, 0xFF5A63FF);

        int resourceY = layout.top + 68;
        for (CelestialAssetRequirement requirement : state.pendingAssetCreation.requiredResources) {
            mc.fontRenderer.drawStringWithShadow(
                "- " + requirement.amount() + " " + requirement.displayName(),
                layout.left + 16,
                resourceY,
                0xFFD9E0FF);
            resourceY += 12;
        }

        drawModalButton(layout.cancelButton, "Cancel", true);
        drawModalButton(layout.confirmButton, "Confirm", true);
    }

    private void drawPendingAssetRenameModal(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        PendingAssetRenameLayout layout = getPendingAssetRenameLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        drawFramedModal(areaWidth, areaHeight, layout.left, layout.top, layout.right, layout.bottom, 0x88000000, 0xFF121B28,
            0xFF59BFD9);

        mc.fontRenderer.drawStringWithShadow("Rename Asset", layout.left + 12, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            callbacks.formatAssetDisplayName(state.pendingAssetRename.asset),
            layout.left + 12,
            layout.top + 28,
            0xFFD9E0FF);

        drawModalButton(layout.cancelButton, "Cancel", true);
        drawModalButton(layout.confirmButton, "Confirm", true);
    }

    private void drawPendingAssetDestructionModal(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        PendingAssetDestructionLayout layout = getPendingAssetDestructionLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        drawFramedModal(areaWidth, areaHeight, layout.left, layout.top, layout.right, layout.bottom, 0xAA000000, 0xFF1A1012,
            0xFFD14A4A);

        drawCenteredLargeString("THIS IS IRREVERSIBLE", (layout.left + layout.right) / 2f, layout.top + 16, 1.45f, 0xFFFF5A5A);
        mc.fontRenderer.drawStringWithShadow("You are about to destroy:", layout.left + 18, layout.top + 52, 0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow(
            callbacks.formatAssetDisplayName(state.pendingAssetDestruction.asset),
            layout.left + 18,
            layout.top + 68,
            0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            state.pendingAssetDestruction.armed ? "Click Destroy again to confirm." : "Press Destroy to arm confirmation.",
            layout.left + 18,
            layout.top + 92,
            0xFFFFB3B3);

        drawModalButton(layout.cancelButton, "Cancel", true);
        drawDangerButton(layout.destroyButton, "Destroy");
    }

    private void drawPendingConstructionCancellationModal(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        PendingConstructionCancellationLayout layout = getPendingConstructionCancellationLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        drawFramedModal(areaWidth, areaHeight, layout.left, layout.top, layout.right, layout.bottom, 0x88000000, 0xFF121B28,
            0xFFE6B35A);

        mc.fontRenderer.drawStringWithShadow("Cancel Construction?", layout.left + 12, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            callbacks.formatAssetDisplayName(state.pendingConstructionCancellation.asset),
            layout.left + 12,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow(
            "Stored resources will be moved into deconstruction recovery.",
            layout.left + 12,
            layout.top + 54,
            0xFFFFD59A);

        drawModalButton(layout.cancelButton, "Cancel", true);
        drawModalButton(layout.confirmButton, "Confirm", true);
    }

    private void drawPendingResourceTransferModal(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        PendingResourceTransferLayout layout = getPendingResourceTransferLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        drawFramedModal(areaWidth, areaHeight, layout.left, layout.top, layout.right, layout.bottom, 0x88000000, 0xFF121B28,
            0xFF59BFD9);

        mc.fontRenderer.drawStringWithShadow("Send Resources To", layout.left + 12, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            callbacks.formatAssetDisplayName(state.pendingResourceTransfer.asset),
            layout.left + 12,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow(
            "Requires an orbital rocket with enough capacity.",
            layout.left + 12,
            layout.top + 46,
            0xFF9AA7B8);

        if (layout.rows.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow("No stations available in this system", layout.left + 16, layout.top + 74, 0xFF9AA7B8);
        } else {
            for (TransferTargetRow row : layout.rows) {
                Gui.drawRect(row.left, row.top, row.right, row.bottom, 0x55213144);
                callbacks.drawAssetIcon(CelestialAssetKind.STATION, row.left + 10, row.top + 9, 16, 1.0f);
                mc.fontRenderer.drawStringWithShadow(row.target.displayName, row.left + 32, row.top + 6, 0xFFFFFFFF);
                mc.fontRenderer.drawStringWithShadow(row.target.hostBodyName, row.left + 32, row.top + 18, 0xFFD9E0FF);
                drawModalButton(row.sendButton, "Send", true);
            }
        }

        drawModalButton(layout.closeButton, "Close", true);
    }

    private void drawPendingAssetManagementModal(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        PendingAssetManagementLayout layout = getPendingAssetManagementLayout(state, areaWidth, areaHeight);
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        drawFramedModal(areaWidth, areaHeight, layout.left, layout.top, layout.right, layout.bottom, 0x88000000, 0xFF121B28,
            0xFF59BFD9);

        callbacks.drawAssetIcon(state.pendingAssetManagement.asset.kind(), layout.left + 12, layout.top + 10, 18, 1.0f);
        mc.fontRenderer.drawStringWithShadow("Manage Station", layout.left + 36, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            callbacks.formatAssetDisplayName(state.pendingAssetManagement.asset),
            layout.left + 36,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow("This panel is not implemented yet.", layout.left + 14, layout.top + 62, 0xFF9AA7B8);

        drawModalButton(layout.closeButton, "Close", true);
    }

    private PendingAssetCreationLayout getPendingAssetCreationLayout(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        if (state.pendingAssetCreation == null) {
            return null;
        }

        int width = 320;
        int height = 150 + Math.max(0, state.pendingAssetCreation.requiredResources.size() - 2) * 12;
        ModalBounds bounds = createCenteredModalBounds(areaWidth, areaHeight, width, height);

        return new PendingAssetCreationLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            createLeftFooterButton(bounds, 110),
            createRightFooterButton(bounds, 110));
    }

    private PendingAssetDestructionLayout getPendingAssetDestructionLayout(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        if (state.pendingAssetDestruction == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(areaWidth, areaHeight, 360, 150);
        ButtonRect leftButton = createLeftFooterButton(bounds, 130);
        ButtonRect rightButton = createRightFooterButton(bounds, 130);

        return state.pendingAssetDestruction.armed
            ? new PendingAssetDestructionLayout(bounds.left, bounds.top, bounds.right, bounds.bottom, rightButton, leftButton)
            : new PendingAssetDestructionLayout(bounds.left, bounds.top, bounds.right, bounds.bottom, leftButton, rightButton);
    }

    private PendingAssetManagementLayout getPendingAssetManagementLayout(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        if (state.pendingAssetManagement == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(areaWidth, areaHeight, 360, 150);
        return new PendingAssetManagementLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            createRightFooterButton(bounds, 110));
    }

    private PendingConstructionCancellationLayout getPendingConstructionCancellationLayout(OrbitalAssetUiState state, int areaWidth,
        int areaHeight) {
        if (state.pendingConstructionCancellation == null) {
            return null;
        }

        ModalBounds bounds = createCenteredModalBounds(areaWidth, areaHeight, 360, 124);
        return new PendingConstructionCancellationLayout(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            createLeftFooterButton(bounds, 130),
            createRightFooterButton(bounds, 130));
    }

    private PendingResourceTransferLayout getPendingResourceTransferLayout(OrbitalAssetUiState state, int areaWidth, int areaHeight) {
        if (state.pendingResourceTransfer == null) {
            return null;
        }

        int width = 420;
        int height = Math.min(280, 120 + state.pendingResourceTransfer.targets.size() * 42);
        int left = (areaWidth - width) / 2;
        int top = (areaHeight - height) / 2;
        int right = left + width;
        int bottom = top + height;

        List<TransferTargetRow> rows = new ArrayList<>();
        int rowTop = top + 66;
        for (int i = 0; i < state.pendingResourceTransfer.targets.size(); i++) {
            StationTransferTarget target = state.pendingResourceTransfer.targets.get(i);
            int currentTop = rowTop + i * 42;
            int currentBottom = currentTop + 36;
            rows.add(new TransferTargetRow(
                target,
                left + 14,
                currentTop,
                right - 14,
                currentBottom,
                new ButtonRect(right - 92, currentTop + 8, right - 20, currentTop + 26)));
        }

        return new PendingResourceTransferLayout(
            left,
            top,
            right,
            bottom,
            new ButtonRect(right - 96, top + 8, right - 18, top + 26),
            rows);
    }

    private void drawFramedModal(int areaWidth, int areaHeight, int left, int top, int right, int bottom, int overlayColor,
        int backgroundColor, int accentColor) {
        Gui.drawRect(0, 0, areaWidth, areaHeight, overlayColor);
        Gui.drawRect(left, top, right, bottom, backgroundColor);
        Gui.drawRect(left, top, right, top + 3, accentColor);
        Gui.drawRect(left, bottom - 3, right, bottom, accentColor);
        Gui.drawRect(left, top, left + 3, bottom, accentColor);
        Gui.drawRect(right - 3, top, right, bottom, accentColor);
    }

    private ModalBounds createCenteredModalBounds(int areaWidth, int areaHeight, int width, int height) {
        int left = (areaWidth - width) / 2;
        int top = (areaHeight - height) / 2;
        return new ModalBounds(left, top, left + width, top + height);
    }

    private ButtonRect createLeftFooterButton(ModalBounds bounds, int width) {
        return new ButtonRect(bounds.left + 18, bounds.bottom - 34, bounds.left + 18 + width, bounds.bottom - 14);
    }

    private ButtonRect createRightFooterButton(ModalBounds bounds, int width) {
        return new ButtonRect(bounds.right - 18 - width, bounds.bottom - 34, bounds.right - 18, bounds.bottom - 14);
    }

    private void drawModalButton(ButtonRect rect, String label, boolean enabled) {
        int bg = enabled ? 0xFF2D435D : 0xFF243041;
        int border = enabled ? 0xFF7FB6FF : 0xFF556577;
        Gui.drawRect(rect.left, rect.top, rect.right, rect.bottom, bg);
        Gui.drawRect(rect.left, rect.top, rect.right, rect.top + 1, border);
        Gui.drawRect(rect.left, rect.bottom - 1, rect.right, rect.bottom, border);
        Gui.drawRect(rect.left, rect.top, rect.left + 1, rect.bottom, border);
        Gui.drawRect(rect.right - 1, rect.top, rect.right, rect.bottom, border);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(label, rect.left + 8, rect.top + 5, enabled ? 0xFFFFFFFF : 0xFF94A0AF);
    }

    private void drawDangerButton(ButtonRect rect, String label) {
        Gui.drawRect(rect.left, rect.top, rect.right, rect.bottom, 0xFF5A1E24);
        Gui.drawRect(rect.left, rect.top, rect.right, rect.top + 1, 0xFFFF5A5A);
        Gui.drawRect(rect.left, rect.bottom - 1, rect.right, rect.bottom, 0xFFFF5A5A);
        Gui.drawRect(rect.left, rect.top, rect.left + 1, rect.bottom, 0xFFFF5A5A);
        Gui.drawRect(rect.right - 1, rect.top, rect.right, rect.bottom, 0xFFFF5A5A);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(label, rect.left + 8, rect.top + 5, 0xFFFFFFFF);
    }

    private void drawCenteredLargeString(String text, float x, float y, float scale, int colour) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1f);
        float w = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, Math.round(-w / 2f), 0, colour);
        GlStateManager.popMatrix();
    }
}
