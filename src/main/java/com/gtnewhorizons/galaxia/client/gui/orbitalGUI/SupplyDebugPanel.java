package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.client.gui.mui.SafePhantomItemSlot;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/**
 * Manages the Supply Debug panel UI, state, and interactions.
 * Extracted from CelestialSidebarWidget for better separation of concerns.
 */
public class SupplyDebugPanel {

    // UI Constants
    public static final int PANEL_TOP = 120;
    public static final int PANEL_PADDING = 10;
    public static final int FIELD_HEIGHT = 14;
    public static final int BUTTON_HEIGHT = 14;
    public static final int GHOST_SLOT_LEFT = PANEL_PADDING + 110;
    public static final int PICK_BUTTON_LEFT = PANEL_PADDING + 134;
    public static final int PICK_BUTTON_WIDTH = 68;
    private static final int BUTTON_TOP = 94;

    // State
    private boolean panelOpen = false;
    private CelestialAsset.ID targetAssetId = null;
    private TextFieldWidget amountField;
    private ItemSlot ghostSlot;
    private ItemStackHandler ghostHandler;
    private long lastClickMs = 0L;

    // Parent widget reference for initialization
    private final OrbitalView.OrbitalMapWidget map;
    private final ParentWidget<?> parentWidget;

    public SupplyDebugPanel(OrbitalView.OrbitalMapWidget map, ParentWidget<?> parentWidget) {
        this.map = map;
        this.parentWidget = parentWidget;
    }

    /**
     * Initializes the supply debug UI components.
     * Must be called from the parent widget's onInit() method.
     */
    public void init() {
        amountField = new TextFieldWidget().left(PANEL_PADDING)
            .top(-1000)
            .right(PANEL_PADDING)
            .height(FIELD_HEIGHT)
            .setMaxLength(12)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText("amount (max int)")
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        amountField.setEnabled(false);
        parentWidget.child(amountField);

        ghostHandler = new ItemStackHandler(1);
        ModularSlot modularSlot = new ModularSlot(ghostHandler, 0);
        ghostSlot = SafePhantomItemSlot.create()
            .slot(modularSlot)
            .left(GHOST_SLOT_LEFT)
            .top(-1000)
            .size(18, 18);
        parentWidget.child(ghostSlot);
    }

    /**
     * Checks if the button should be visible (only in creative build mode).
     */
    public boolean shouldShowButton() {
        return map.isCreativeBuildModeEnabled();
    }

    /**
     * Handles clicks on the Supply Debug button.
     * Returns true if the click was consumed.
     */
    public boolean handleButtonClick(int localX, int localY) {
        if (!shouldShowButton()) return false;
        int width = Math.max(112, Minecraft.getMinecraft().fontRenderer.getStringWidth("Supply Debug") + 18);
        if (localY >= BUTTON_TOP && localY <= BUTTON_TOP + 18 && localX >= 18 && localX <= 18 + width) {
            panelOpen = !panelOpen;
            if (panelOpen) {
                targetAssetId = resolveAssetId();
                amountField.setText("64");
                if (ghostHandler != null) {
                    ghostHandler.setStackInSlot(0, null);
                }
            } else {
                targetAssetId = null;
            }
            return true;
        }
        return false;
    }

    /**
     * Handles clicks within the Supply Debug panel (buttons and controls).
     * Returns true if the click was consumed.
     */
    public boolean handlePanelClick(int localX, int localY, int panelWidth) {
        if (!panelOpen) return false;
        
        int pickTop = PANEL_TOP + 28;
        int confirmTop = PANEL_TOP + 80;
        int cancelTop = confirmTop + BUTTON_HEIGHT + 4;

        // Item picker button
        if (localY >= pickTop && localY <= pickTop + 18
            && localX >= PICK_BUTTON_LEFT
            && localX <= PICK_BUTTON_LEFT + PICK_BUTTON_WIDTH) {
            ItemPickerScreen.setPendingForSidebarDebug();
            ItemPickerScreen.FACTORY.openClient();
            return true;
        }

        // Confirm button
        if (localY >= confirmTop && localY <= confirmTop + BUTTON_HEIGHT
            && localX >= PANEL_PADDING
            && localX <= PANEL_PADDING + panelWidth) {
            confirmAction();
            return true;
        }

        // Cancel button
        if (localY >= cancelTop && localY <= cancelTop + BUTTON_HEIGHT
            && localX >= PANEL_PADDING
            && localX <= PANEL_PADDING + panelWidth) {
            panelOpen = false;
            targetAssetId = null;
            return true;
        }

        return false;
    }

    /**
     * Updates field positions based on panel state.
     */
    public void updateFieldPositions() {
        if (!panelOpen || !shouldShowButton()) {
            if (amountField != null && amountField.isEnabled()) {
                amountField.top(-1000);
                amountField.setEnabled(false);
            }
            if (ghostSlot != null && ghostSlot.isEnabled()) {
                ghostSlot.top(-1000);
                ghostSlot.setEnabled(false);
            }
            return;
        }

        int amountFieldTop = PANEL_TOP + 60;
        int ghostSlotTop = PANEL_TOP + 28;
        if (amountField != null) {
            amountField.top(amountFieldTop);
            amountField.setEnabled(true);
        }
        if (ghostSlot != null) {
            ghostSlot.top(ghostSlotTop);
            ghostSlot.setEnabled(true);
        }
    }

    /**
     * Renders the Supply Debug panel and all its components.
     */
    public void draw(ModularGuiContext context, WidgetThemeEntry widgetTheme, int widgetWidth) {
        if (!panelOpen) return;

        int panelLeft = PANEL_PADDING;
        int panelRight = widgetWidth - PANEL_PADDING;

        // Panel background
        Gui.drawRect(
            panelLeft,
            PANEL_TOP,
            panelRight,
            PANEL_TOP + 126,
            EnumColors.MAP_COLOR_SIDEBAR_DEBUG_PANEL_BG.getColor());
        Gui.drawRect(
            panelLeft,
            PANEL_TOP,
            panelRight,
            PANEL_TOP + 1,
            EnumColors.MapSidebarListHovered.getColor());

        String targetLabel = resolveTargetLabel();

        // Title
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Supply Debug",
            panelLeft + 4,
            PANEL_TOP + 5,
            EnumColors.MAP_COLOR_SIDEBAR_DEBUG_TITLE.getColor());

        // Target label
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            targetLabel,
            panelLeft + 4,
            PANEL_TOP + 17,
            EnumColors.MapSidebarListNormal.getColor());

        // Item and amount labels
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Item:",
            panelLeft + 4,
            PANEL_TOP + 28,
            EnumColors.MapSidebaSearchLabel.getColor());
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Amount:",
            panelLeft + 4,
            PANEL_TOP + 46,
            EnumColors.MapSidebaSearchLabel.getColor());

        // Ghost slot background
        Gui.drawRect(
            GHOST_SLOT_LEFT,
            PANEL_TOP + 28,
            GHOST_SLOT_LEFT + 18,
            PANEL_TOP + 46,
            EnumColors.MAP_COLOR_SIDEBAR_GHOST_SLOT_BG.getColor());
        Gui.drawRect(
            GHOST_SLOT_LEFT + 1,
            PANEL_TOP + 29,
            GHOST_SLOT_LEFT + 17,
            PANEL_TOP + 45,
            EnumColors.MAP_COLOR_SIDEBAR_GHOST_SLOT_INNER.getColor());

        drawButton(PICK_BUTTON_LEFT, PANEL_TOP + 28, PICK_BUTTON_WIDTH, 18, "Select", true);

        // Confirm button
        int confirmTop = PANEL_TOP + 86;
        boolean canConfirm = resolveAsset() != null && ghostHandler != null
            && ghostHandler.getStackInSlot(0) != null;
        int confirmBg = canConfirm ? EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_BG_ENABLED.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_BG_DISABLED.getColor();
        
        int panelWidth = panelRight - panelLeft;
        Gui.drawRect(panelLeft, confirmTop, panelRight, confirmTop + BUTTON_HEIGHT, confirmBg);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Add to Inventory",
            panelLeft + 4,
            confirmTop + 3,
            canConfirm ? EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor()
                : EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_DISABLED.getColor());

        // Cancel button
        int cancelTop = confirmTop + BUTTON_HEIGHT + 4;
        Gui.drawRect(
            panelLeft,
            cancelTop,
            panelRight,
            cancelTop + BUTTON_HEIGHT,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_BG_DISABLED.getColor());
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Close",
            panelLeft + 4,
            cancelTop + 3,
            EnumColors.MAP_COLOR_SIDEBAR_CANCEL_TEXT.getColor());
    }

    /**
     * Helper to draw a styled button.
     */
    private void drawButton(int x, int y, int width, int height, String label, boolean enabled) {
        int bg = enabled ? EnumColors.MAP_COLOR_SIDEBAR_INLINE_BTN_BG_ENABLED.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_INLINE_BTN_BG_DISABLED.getColor();
        int border = enabled ? EnumColors.MapSidebarListHovered.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_INLINE_BTN_BORDER_DISABLED.getColor();
        
        Gui.drawRect(x, y, x + width, y + height, bg);
        Gui.drawRect(x, y, x + width, y + 1, border);
        Gui.drawRect(x, y + height - 1, x + width, y + height, border);
        Gui.drawRect(x, y, x + 1, y + height, border);
        Gui.drawRect(x + width - 1, y, x + width, y + height, border);
        
        int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(label);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            label,
            x + (width - textWidth) / 2,
            y + (height - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) / 2 + 1,
            enabled ? EnumColors.MapSidebarListNormal.getColor()
                : EnumColors.MAP_COLOR_SIDEBAR_INLINE_BTN_TEXT_DISABLED.getColor());
    }

    /**
     * Resolves the target asset label for display.
     */
    private String resolveTargetLabel() {
        CelestialAsset asset = resolveAsset();
        if (asset == null) {
            CelestialObject focused = map.getFocusedBody();
            if (focused == null) return "No body selected";
            return "No outpost on " + focused.displayName();
        }
        return asset.displayName();
    }

    /**
     * Finds the first operational AUTOMATED_OUTPOST or AUTOMATED_STATION asset
     * on the currently focused body.
     */
    private CelestialAsset resolveAsset() {
        if (targetAssetId != null) {
            CelestialAsset pinned = CelestialClient.getByAssetId(targetAssetId);
            if (pinned != null && pinned.status() == CelestialAsset.Status.OPERATIONAL
                && (pinned.kind == CelestialAsset.Kind.AUTOMATED_OUTPOST
                    || pinned.kind == CelestialAsset.Kind.AUTOMATED_STATION)) {
                return pinned;
            }
            targetAssetId = null;
        }

        CelestialAsset.ID currentAssetId = resolveAssetId();
        if (currentAssetId != null) {
            targetAssetId = currentAssetId;
            return CelestialClient.getByAssetId(currentAssetId);
        }
        return null;
    }

    /**
     * Resolves the asset ID from the focused body.
     */
    private CelestialAsset.ID resolveAssetId() {
        CelestialObject focused = map.getFocusedBody();
        if (focused == null) return null;
        List<CelestialAsset> state = CelestialClient.getState(focused.id());
        for (CelestialAsset asset : state) {
            if (asset.status() != CelestialAsset.Status.OPERATIONAL) continue;
            if (asset.kind == CelestialAsset.Kind.AUTOMATED_OUTPOST
                || asset.kind == CelestialAsset.Kind.AUTOMATED_STATION) {
                return asset.assetId;
            }
        }
        return null;
    }

    /**
     * Confirms and executes the supply debug action with click rate limiting.
     */
    private void confirmAction() {
        long now = System.currentTimeMillis();
        if (now - lastClickMs < 200L) return;
        lastClickMs = now;

        CelestialAsset asset = resolveAsset();
        if (asset == null) return;

        String amountText = amountField == null ? "64" : amountField.getText().trim();
        ItemStack selectedStack = ghostHandler == null ? null : ghostHandler.getStackInSlot(0);
        
        if (selectedStack == null) return;

        long amount;
        try {
            amount = Long.parseLong(amountText);
        } catch (NumberFormatException e) {
            return;
        }

        if (amount <= 0) return;
        amount = Math.min(amount, Integer.MAX_VALUE);

        ItemStackWrapper resource = ItemStackWrapper.of(selectedStack);
        if (resource == null) return;

        Galaxia.LOG.info("[Supply Debug] Adding {} x {} to {}", amount, resource, asset.assetId);
        CelestialClient.addInventory(asset.assetId, resource, amount);
    }

    /**
     * Called when an item is picked from the item picker screen.
     */
    public void onItemPicked(ItemStack stack) {
        if (stack != null && ghostHandler != null) {
            ghostHandler.setStackInSlot(0, stack);
        }
    }

    public boolean isPanelOpen() {
        return panelOpen;
    }

    public TextFieldWidget getAmountField() {
        return amountField;
    }

    public ItemSlot getGhostSlot() {
        return ghostSlot;
    }

    public ItemStackHandler getGhostHandler() {
        return ghostHandler;
    }
}
