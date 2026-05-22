package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.client.gui.mui.SafePhantomItemSlot;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

record VisibleEntry(CelestialObject body, int depth, boolean hasChildren) {}

record RowLayout(VisibleEntry entry, int left, int right, int top, int bottom) {}

public class CelestialSidebarWidget extends ParentWidget<CelestialSidebarWidget> {

    private final CelestialObject root;
    private final OrbitalView.OrbitalMapWidget map;
    private CelestialObject currentSystem;
    private CelestialObject activeLayer;
    private String searchQuery = "";
    private final Set<CelestialObject> expanded = new HashSet<>();
    private double scrollOffset = 0;
    private TextFieldWidget searchField;
    // Supply Debug panel state
    private boolean supplyDebugPanelOpen = false;
    private CelestialAsset.ID supplyDebugTargetAssetId = null;
    private TextFieldWidget supplyDebugAmountField;
    private ItemSlot supplyDebugGhostSlot;
    private ItemStackHandler supplyDebugGhostHandler;
    private final List<VisibleEntry> cachedVisibleEntries = new ArrayList<>();
    private final List<RowLayout> cachedRowLayouts = new ArrayList<>();
    private boolean visibleEntriesDirty = true;
    private boolean rowLayoutsDirty = true;
    private double cachedRowLayoutScrollOffset = Double.NaN;
    private int cachedRowLayoutHeight = -1;
    private int cachedRowLayoutWidth = -1;
    private int cachedSearchOffset = Integer.MIN_VALUE;
    private long lastSupplyDebugClickMs = 0L;

    private static final int LAYER_BUTTON_TOP = 14;
    private static final int LAYER_BUTTON_HEIGHT = 18;
    private static final int LAYER_BUTTON_GAP = 8;
    private static final int CREATIVE_BUTTON_TOP = 42;
    private static final int TRANSFER_SIMULATOR_BUTTON_TOP = 68;
    private static final int SUPPLY_DEBUG_BUTTON_TOP = 94;
    private static final int SEARCH_LABEL_TOP = 42;
    private static final int SEARCH_FIELD_TOP = 54;
    private static final int LIST_TOP = 82;
    private static final int LINE_HEIGHT = 26;
    private static final int ARROW_ZONE = 42;

    // Supply Debug panel constants
    private static final int DEBUG_PANEL_TOP = 120;
    private static final int DEBUG_PANEL_PADDING = 10;
    private static final int DEBUG_FIELD_HEIGHT = 14;
    private static final int DEBUG_BUTTON_HEIGHT = 14;
    private static final int DEBUG_GHOST_SLOT_LEFT = DEBUG_PANEL_PADDING + 110;
    private static final int DEBUG_PICK_BUTTON_LEFT = DEBUG_PANEL_PADDING + 134;
    private static final int DEBUG_PICK_BUTTON_WIDTH = 68;

    public CelestialSidebarWidget(CelestialObject root, CelestialObject currentSystem,
        OrbitalView.OrbitalMapWidget map) {
        this.root = root;
        this.map = map;
        this.currentSystem = currentSystem;
        this.activeLayer = currentSystem == null ? root : currentSystem;
        expanded.add(root);
        if (this.currentSystem != null) expanded.add(this.currentSystem);
    }

    @Override
    public void onInit() {
        super.onInit();
        searchField = new TextFieldWidget().left(14)
            .top(SEARCH_FIELD_TOP)
            .right(8)
            .height(16)
            .setMaxLength(64)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText(localizeOrFallback("galaxia.gui.orbital.search.placeholder", "Enter name..."))
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        child(searchField);

        supplyDebugAmountField = new TextFieldWidget().left(DEBUG_PANEL_PADDING)
            .top(-1000)
            .right(DEBUG_PANEL_PADDING)
            .height(DEBUG_FIELD_HEIGHT)
            .setMaxLength(12)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText("amount (max int)")
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        supplyDebugAmountField.setEnabled(false);
        child(supplyDebugAmountField);

        supplyDebugGhostHandler = new ItemStackHandler(1);
        ModularSlot supplyDebugModularSlot = new ModularSlot(supplyDebugGhostHandler, 0);
        supplyDebugGhostSlot = SafePhantomItemSlot.create()
            .slot(supplyDebugModularSlot)
            .left(DEBUG_GHOST_SLOT_LEFT)
            .top(-1000)
            .size(18, 18);
        child(supplyDebugGhostSlot);

        map.setBodySelectionListener(this::handleMapSelection);
        listenGuiAction((IGuiAction.MouseScroll) (dir, amt) -> {
            scrollOffset += dir.isUp() ? -35 : 35;
            scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
            rowLayoutsDirty = true;
            return true;
        });
        listenGuiAction((IGuiAction.MousePressed) button -> {
            int mouseX = getContext().getMouseX();
            int mouseY = getContext().getMouseY();
            return handleClick(mouseX, mouseY, button);
        });
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (ItemPickerScreen.hasPendingPickForSidebarDebug()) {
            ItemStack pickedStack = ItemPickerScreen.pollPendingPickForSidebarDebug();
            if (pickedStack != null && supplyDebugGhostHandler != null) {
                ItemStack displayStack = pickedStack.copy();
                displayStack.stackSize = 1;
                supplyDebugGhostHandler.setStackInSlot(0, displayStack);
            }
        }
    }

    private boolean handleClick(int mx, int my, int button) {
        if (button != 0) return false;
        int localX = mx - getArea().rx;
        int localYAbsolute = my - getArea().ry;
        if (localX < 0 || localYAbsolute < 0 || localX >= getArea().width || localYAbsolute >= getArea().height)
            return false;
        if (handleLayerButtonClick(localX, localYAbsolute)) return true;
        if (handleCreativeButtonClick(localX, localYAbsolute)) return true;
        if (handleTransferSimulatorButtonClick(localX, localYAbsolute)) return true;
        if (handleSupplyDebugButtonClick(localX, localYAbsolute)) return true;
        if (supplyDebugPanelOpen && handleSupplyDebugPanelClick(localX, localYAbsolute)) return true;
        if (activeLayer == root) return false;
        VisibleEntry entry = findVisibleRowAt(localX, localYAbsolute);
        if (entry == null) return false;
        if (entry.hasChildren() && localX < ARROW_ZONE + entry.depth() * 24) {
            if (expanded.contains(entry.body())) expanded.remove(entry.body());
            else expanded.add(entry.body());
            markEntriesDirty();
            return true;
        }
        map.focusOn(entry.body());
        handleMapSelection(entry.body());
        return true;
    }

    private void markEntriesDirty() {
        visibleEntriesDirty = true;
        rowLayoutsDirty = true;
    }

    private void ensureVisibleEntries() {
        if (!visibleEntriesDirty) return;
        cachedVisibleEntries.clear();
        if (activeLayer != root) {
            for (CelestialObject child : GalaxiaCelestialAPI.getChildren(activeLayer))
                collect(child, 0, cachedVisibleEntries);
        }
        visibleEntriesDirty = false;
        rowLayoutsDirty = true;
    }

    private void collect(CelestialObject body, int depth, List<VisibleEntry> list) {
        boolean matches = searchQuery.isEmpty() || body.displayName()
            .toLowerCase()
            .contains(searchQuery);

        List<CelestialObject> childs = GalaxiaCelestialAPI.getChildren(body);
        if (matches || searchQuery.isEmpty()) {
            list.add(new VisibleEntry(body, depth, !childs.isEmpty()));
        }
        if (expanded.contains(body) || !searchQuery.isEmpty()) {
            for (CelestialObject child : childs) collect(child, depth + 1, list);
        }
    }

    private double getMaxScroll() {
        ensureVisibleEntries();
        return Math.max(0, cachedVisibleEntries.size() * LINE_HEIGHT - getArea().height + getListTop() + 20);
    }

    private void ensureRowLayouts() {
        ensureVisibleEntries();
        if (!rowLayoutsDirty && Double.compare(cachedRowLayoutScrollOffset, scrollOffset) == 0
            && cachedRowLayoutHeight == getArea().height
            && cachedRowLayoutWidth == getArea().width) {
            return;
        }
        cachedRowLayouts.clear();
        int y = getListTop() - (int) scrollOffset;
        for (int i = 0; i < cachedVisibleEntries.size(); i++) {
            int sy = y + i * LINE_HEIGHT;
            if (sy < 50 || sy > getArea().height - 10) continue;
            VisibleEntry entry = cachedVisibleEntries.get(i);
            int iconX = 10 + entry.depth() * 24;
            int textX = 22 + entry.depth() * 24;
            int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(
                entry.body()
                    .displayName());
            int rowLeft = Math.max(0, iconX - 4);
            int rowRight = Math.min(getArea().width - 10, Math.max(textX + textWidth + 4, rowLeft + 16));
            cachedRowLayouts.add(new RowLayout(entry, rowLeft, rowRight, sy, sy + LINE_HEIGHT));
        }
        cachedRowLayoutScrollOffset = scrollOffset;
        cachedRowLayoutHeight = getArea().height;
        cachedRowLayoutWidth = getArea().width;
        rowLayoutsDirty = false;
    }

    private VisibleEntry findVisibleRowAt(int localX, int localYAbsolute) {
        ensureRowLayouts();
        for (RowLayout row : cachedRowLayouts) {
            if (localX < row.left() || localX > row.right()) continue;
            if (localYAbsolute >= row.top() && localYAbsolute < row.bottom()) return row.entry();
        }
        return null;
    }

    private String getCurrentSystemLabel() {
        return currentSystem == null ? "System" : currentSystem.displayName();
    }

    private boolean shouldShowCreativeButton() {
        return map.isCreativeModeAvailable();
    }

    private boolean shouldShowTransferSimulatorButton() {
        return map.isCreativeBuildModeEnabled();
    }

    private int getSearchOffset() {
        int offset = shouldShowCreativeButton() ? 28 : 0;
        if (shouldShowTransferSimulatorButton()) offset += 26;
        if (shouldShowSupplyDebugButton()) offset += 26;
        if (supplyDebugPanelOpen) offset += 128; // Add panel height + padding to clear it
        return offset;
    }

    private int getSearchLabelTop() {
        return SEARCH_LABEL_TOP + getSearchOffset();
    }

    private int getSearchFieldTop() {
        return SEARCH_FIELD_TOP + getSearchOffset();
    }

    private int getListTop() {
        return LIST_TOP + getSearchOffset();
    }

    private int getCreativeButtonWidth() {
        return Math.max(112, Minecraft.getMinecraft().fontRenderer.getStringWidth("Creative Mode") + 18);
    }

    private boolean handleLayerButtonClick(int localX, int localY) {
        int galaxyButtonWidth = 70;
        int starButtonX = 18 + galaxyButtonWidth + LAYER_BUTTON_GAP;
        int starButtonWidth = Math
            .max(80, Minecraft.getMinecraft().fontRenderer.getStringWidth(getCurrentSystemLabel()) + 18);
        if (localY >= LAYER_BUTTON_TOP && localY <= LAYER_BUTTON_TOP + LAYER_BUTTON_HEIGHT) {
            if (localX >= 18 && localX <= 18 + galaxyButtonWidth) {
                selectLayer(root);
                return true;
            }
            if (currentSystem != null && localX >= starButtonX && localX <= starButtonX + starButtonWidth) {
                selectLayer(currentSystem);
                return true;
            }
        }
        return false;
    }

    private boolean handleCreativeButtonClick(int localX, int localY) {
        if (!shouldShowCreativeButton()) return false;
        int width = getCreativeButtonWidth();
        if (localY >= CREATIVE_BUTTON_TOP && localY <= CREATIVE_BUTTON_TOP + LAYER_BUTTON_HEIGHT
            && localX >= 18
            && localX <= 18 + width) {
            map.toggleCreativeBuildMode();
            rowLayoutsDirty = true;
            return true;
        }
        return false;
    }

    private boolean handleTransferSimulatorButtonClick(int localX, int localY) {
        if (!shouldShowTransferSimulatorButton()) return false;
        int width = Math.max(132, Minecraft.getMinecraft().fontRenderer.getStringWidth("Transfer Simulator") + 18);
        if (localY >= TRANSFER_SIMULATOR_BUTTON_TOP && localY <= TRANSFER_SIMULATOR_BUTTON_TOP + LAYER_BUTTON_HEIGHT
            && localX >= 18
            && localX <= 18 + width) {
            map.toggleTransferSimulator();
            return true;
        }
        return false;
    }

    private void selectLayer(CelestialObject layerRoot) {
        activeLayer = layerRoot == null ? root : layerRoot;
        if (activeLayer != null) expanded.add(activeLayer);
        scrollOffset = 0;
        markEntriesDirty();
        map.showLayer(activeLayer);
    }

    private void handleMapSelection(CelestialObject body) {
        if (body.objectClass() == CelestialObject.Class.STAR) {
            currentSystem = body;
            if (activeLayer == root) {
                activeLayer = body;
                scrollOffset = 0;
                expanded.add(body);
                markEntriesDirty();
                map.showLayer(body);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Supply Debug button and panel
    // -------------------------------------------------------------------------

    private boolean shouldShowSupplyDebugButton() {
        return map.isCreativeBuildModeEnabled();
    }

    private boolean handleSupplyDebugButtonClick(int localX, int localY) {
        if (!shouldShowSupplyDebugButton()) return false;
        int width = Math.max(112, Minecraft.getMinecraft().fontRenderer.getStringWidth("Supply Debug") + 18);
        if (localY >= SUPPLY_DEBUG_BUTTON_TOP && localY <= SUPPLY_DEBUG_BUTTON_TOP + LAYER_BUTTON_HEIGHT
            && localX >= 18
            && localX <= 18 + width) {
            supplyDebugPanelOpen = !supplyDebugPanelOpen;
            if (supplyDebugPanelOpen) {
                supplyDebugTargetAssetId = resolveSupplyDebugAssetId();
                supplyDebugAmountField.setText("64");
                if (supplyDebugGhostHandler != null) {
                    supplyDebugGhostHandler.setStackInSlot(0, null);
                }
            } else {
                supplyDebugTargetAssetId = null;
            }
            return true;
        }
        return false;
    }

    /**
     * Handles clicks within the Supply Debug inline panel (Confirm and Cancel buttons).
     * Returns true if the click was consumed.
     */
    private boolean handleSupplyDebugPanelClick(int localX, int localY) {
        int panelWidth = getArea().width - DEBUG_PANEL_PADDING * 2;
        int pickTop = DEBUG_PANEL_TOP + 28;
        int confirmTop = DEBUG_PANEL_TOP + 80;
        int cancelTop = confirmTop + DEBUG_BUTTON_HEIGHT + 4;
        if (localY >= pickTop && localY <= pickTop + 18
            && localX >= DEBUG_PICK_BUTTON_LEFT
            && localX <= DEBUG_PICK_BUTTON_LEFT + DEBUG_PICK_BUTTON_WIDTH) {
            ItemPickerScreen.setPendingForSidebarDebug();
            ItemPickerScreen.FACTORY.openClient();
            return true;
        }
        // Confirm button
        if (localY >= confirmTop && localY <= confirmTop + DEBUG_BUTTON_HEIGHT
            && localX >= DEBUG_PANEL_PADDING
            && localX <= DEBUG_PANEL_PADDING + panelWidth) {
            confirmSupplyDebug();
            return true;
        }
        // Cancel button
        if (localY >= cancelTop && localY <= cancelTop + DEBUG_BUTTON_HEIGHT
            && localX >= DEBUG_PANEL_PADDING
            && localX <= DEBUG_PANEL_PADDING + panelWidth) {
            supplyDebugPanelOpen = false;
            supplyDebugTargetAssetId = null;
            return true;
        }
        return false;
    }

    /** Positions the Supply Debug text fields when the panel is open, hides them otherwise. */
    private void updateSupplyDebugFieldPositions() {
        if (!supplyDebugPanelOpen || !shouldShowSupplyDebugButton()) {
            if (supplyDebugAmountField != null && supplyDebugAmountField.isEnabled()) {
                supplyDebugAmountField.top(-1000);
                supplyDebugAmountField.setEnabled(false);
            }
            if (supplyDebugGhostSlot != null && supplyDebugGhostSlot.isEnabled()) {
                supplyDebugGhostSlot.top(-1000);
                supplyDebugGhostSlot.setEnabled(false);
            }
            return;
        }
        int amountFieldTop = DEBUG_PANEL_TOP + 60;
        int ghostSlotTop = DEBUG_PANEL_TOP + 28;
        if (supplyDebugAmountField != null) {
            supplyDebugAmountField.top(amountFieldTop);
            supplyDebugAmountField.setEnabled(true);
        }
        if (supplyDebugGhostSlot != null) {
            supplyDebugGhostSlot.top(ghostSlotTop);
            supplyDebugGhostSlot.setEnabled(true);
        }
    }

    private void drawSupplyDebugPanel(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        int panelLeft = DEBUG_PANEL_PADDING;
        int panelRight = getArea().width - DEBUG_PANEL_PADDING;
        int panelWidth = panelRight - panelLeft;

        // Panel background
        Gui.drawRect(
            panelLeft,
            DEBUG_PANEL_TOP,
            panelRight,
            DEBUG_PANEL_TOP + 126,
            EnumColors.MAP_COLOR_SIDEBAR_DEBUG_PANEL_BG.getColor());
        Gui.drawRect(
            panelLeft,
            DEBUG_PANEL_TOP,
            panelRight,
            DEBUG_PANEL_TOP + 1,
            EnumColors.MapSidebarListHovered.getColor());

        // Resolve target asset
        String targetLabel = resolveSupplyDebugTargetLabel();

        // Title
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Supply Debug",
            panelLeft + 4,
            DEBUG_PANEL_TOP + 5,
            EnumColors.MAP_COLOR_SIDEBAR_DEBUG_TITLE.getColor());
        // Target
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            targetLabel,
            panelLeft + 4,
            DEBUG_PANEL_TOP + 17,
            EnumColors.MapSidebarListNormal.getColor());

        // Item picker row
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Item:",
            panelLeft + 4,
            DEBUG_PANEL_TOP + 28,
            EnumColors.MapSidebaSearchLabel.getColor());
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Amount:",
            panelLeft + 4,
            DEBUG_PANEL_TOP + 46,
            EnumColors.MapSidebaSearchLabel.getColor());

        // Ghost slot background
        Gui.drawRect(
            DEBUG_GHOST_SLOT_LEFT,
            DEBUG_PANEL_TOP + 28,
            DEBUG_GHOST_SLOT_LEFT + 18,
            DEBUG_PANEL_TOP + 46,
            EnumColors.MAP_COLOR_SIDEBAR_GHOST_SLOT_BG.getColor());
        Gui.drawRect(
            DEBUG_GHOST_SLOT_LEFT + 1,
            DEBUG_PANEL_TOP + 29,
            DEBUG_GHOST_SLOT_LEFT + 17,
            DEBUG_PANEL_TOP + 45,
            EnumColors.MAP_COLOR_SIDEBAR_GHOST_SLOT_INNER.getColor());
        drawInlineButton(DEBUG_PICK_BUTTON_LEFT, DEBUG_PANEL_TOP + 28, DEBUG_PICK_BUTTON_WIDTH, 18, "Select", true);

        // Confirm button
        int confirmTop = DEBUG_PANEL_TOP + 86;
        boolean canConfirm = resolveSupplyDebugAsset() != null && supplyDebugGhostHandler != null
            && supplyDebugGhostHandler.getStackInSlot(0) != null;
        int confirmBg = canConfirm ? EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_BG_ENABLED.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_BG_DISABLED.getColor();
        Gui.drawRect(panelLeft, confirmTop, panelRight, confirmTop + DEBUG_BUTTON_HEIGHT, confirmBg);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Add to Inventory",
            panelLeft + 4,
            confirmTop + 3,
            canConfirm ? EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor()
                : EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_DISABLED.getColor());

        // Cancel button
        int cancelTop = confirmTop + DEBUG_BUTTON_HEIGHT + 4;
        Gui.drawRect(
            panelLeft,
            cancelTop,
            panelRight,
            cancelTop + DEBUG_BUTTON_HEIGHT,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_BG_DISABLED.getColor());
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Close",
            panelLeft + 4,
            cancelTop + 3,
            EnumColors.MAP_COLOR_SIDEBAR_CANCEL_TEXT.getColor());
    }

    private String resolveSupplyDebugTargetLabel() {
        CelestialAsset asset = resolveSupplyDebugAsset();
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
    private CelestialAsset resolveSupplyDebugAsset() {
        if (supplyDebugTargetAssetId != null) {
            CelestialAsset pinned = CelestialClient.getByAssetId(supplyDebugTargetAssetId);
            if (pinned != null && pinned.status() == CelestialAsset.Status.OPERATIONAL
                && (pinned.kind == CelestialAsset.Kind.AUTOMATED_OUTPOST
                    || pinned.kind == CelestialAsset.Kind.AUTOMATED_STATION)) {
                return pinned;
            }
            supplyDebugTargetAssetId = null;
        }
        CelestialAsset.ID currentAssetId = resolveSupplyDebugAssetId();
        if (currentAssetId != null) {
            supplyDebugTargetAssetId = currentAssetId;
            return CelestialClient.getByAssetId(currentAssetId);
        }
        return null;
    }

    private CelestialAsset.ID resolveSupplyDebugAssetId() {
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

    private void confirmSupplyDebug() {
        long now = System.currentTimeMillis();
        if (now - lastSupplyDebugClickMs < 200L) return;
        lastSupplyDebugClickMs = now;
        CelestialAsset asset = resolveSupplyDebugAsset();
        if (asset == null) return;
        String amountText = supplyDebugAmountField == null ? "64"
            : supplyDebugAmountField.getText()
                .trim();
        ItemStack selectedStack = supplyDebugGhostHandler == null ? null : supplyDebugGhostHandler.getStackInSlot(0);
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

    private void drawInlineButton(int x, int y, int width, int height, String label, boolean enabled) {
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

    private void drawLayerButton(int x, int y, int width, String label, boolean selected) {
        int bg = selected ? EnumColors.MAP_COLOR_SIDEBAR_LAYER_BTN_BG_SELECTED.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_LAYER_BTN_BG_NORMAL.getColor();
        int border = selected ? EnumColors.MapSidebarListHovered.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_INLINE_BTN_BORDER_DISABLED.getColor();
        Gui.drawRect(x, y, x + width, y + LAYER_BUTTON_HEIGHT, bg);
        Gui.drawRect(x, y, x + width, y + 1, border);
        Gui.drawRect(x, y + LAYER_BUTTON_HEIGHT - 1, x + width, y + LAYER_BUTTON_HEIGHT, border);
        Gui.drawRect(x, y, x + 1, y + LAYER_BUTTON_HEIGHT, border);
        Gui.drawRect(x + width - 1, y, x + width, y + LAYER_BUTTON_HEIGHT, border);
        Minecraft.getMinecraft().fontRenderer
            .drawStringWithShadow(label, x + 8, y + 5, EnumColors.MapSidebarListNormal.getColor());
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        super.drawBackground(context, widgetTheme);
        int currentSearchOffset = getSearchOffset();
        if (currentSearchOffset != cachedSearchOffset) {
            cachedSearchOffset = currentSearchOffset;
            rowLayoutsDirty = true;
        }
        String newQuery = searchField == null ? ""
            : searchField.getText()
                .toLowerCase();
        if (!newQuery.equals(searchQuery)) {
            searchQuery = newQuery;
            scrollOffset = 0;
            markEntriesDirty();
        }
        if (searchField != null) {
            if (activeLayer == root || supplyDebugPanelOpen) {
                searchField.top(-1000);
                if (searchField.isEnabled()) searchField.setEnabled(false);
            } else {
                searchField.top(getSearchFieldTop());
                if (!searchField.isEnabled()) searchField.setEnabled(true);
            }
        }
        Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapSidebarBackground.getColor());
        drawLayerButton(18, LAYER_BUTTON_TOP, 70, "Galaxy", activeLayer == root);
        drawLayerButton(
            18 + 70 + LAYER_BUTTON_GAP,
            LAYER_BUTTON_TOP,
            Math.max(80, Minecraft.getMinecraft().fontRenderer.getStringWidth(getCurrentSystemLabel()) + 18),
            getCurrentSystemLabel(),
            activeLayer == currentSystem);
        if (shouldShowCreativeButton()) drawLayerButton(
            18,
            CREATIVE_BUTTON_TOP,
            getCreativeButtonWidth(),
            "Creative Mode",
            map.isCreativeBuildModeEnabled());
        if (shouldShowTransferSimulatorButton()) drawLayerButton(
            18,
            TRANSFER_SIMULATOR_BUTTON_TOP,
            Math.max(132, Minecraft.getMinecraft().fontRenderer.getStringWidth("Transfer Simulator") + 18),
            "Transfer Simulator",
            map.isTransferSimulatorOpen());
        if (!shouldShowSupplyDebugButton() && supplyDebugPanelOpen) supplyDebugPanelOpen = false;
        if (shouldShowSupplyDebugButton()) drawLayerButton(
            18,
            SUPPLY_DEBUG_BUTTON_TOP,
            Math.max(112, Minecraft.getMinecraft().fontRenderer.getStringWidth("Supply Debug") + 18),
            "Supply Debug",
            supplyDebugPanelOpen);
        updateSupplyDebugFieldPositions();
        if (supplyDebugPanelOpen) {
            drawSupplyDebugPanel(context, widgetTheme);
            return;
        }
        if (activeLayer == root) return;
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            localizeOrFallback("galaxia.gui.orbital.search", "Search"),
            18,
            getSearchLabelTop(),
            EnumColors.MapSidebaSearchLabel.getColor());
        ensureRowLayouts();
        int mouseLocalX = getContext().getMouseX() - getArea().rx;
        int mouseLocalY = getContext().getMouseY() - getArea().ry;
        VisibleEntry hoveredEntry = findVisibleRowAt(mouseLocalX, mouseLocalY);
        CelestialObject hoveredBody = hoveredEntry == null ? null : hoveredEntry.body();
        for (RowLayout row : cachedRowLayouts) {
            VisibleEntry e = row.entry();
            int sy = row.top();
            boolean hovered = hoveredBody != null && e.body() == hoveredBody;
            int iconX = 10 + e.depth() * 24;
            int textX = 22 + e.depth() * 24;
            String text = e.body()
                .displayName();
            int color = hovered ? EnumColors.MAP_COLOR_SIDEBAR_ENTRY_HOVER.getColor()
                : EnumColors.MapSidebarListNormal.getColor();
            if (e.hasChildren()) {
                IDrawable play = IDrawable.of(GuiTextures.PLAY);
                if (expanded.contains(e.body())) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(iconX + 4f, sy + 10f, 0f);
                    GL11.glRotatef(90f, 0f, 0f, 1f);
                    GL11.glTranslatef(-4f, -4f, 0f);
                    play.draw(context, 0, 0, 8, 8, widgetTheme.getTheme());
                    GL11.glPopMatrix();
                } else {
                    play.draw(context, iconX, sy + 6, 8, 8, widgetTheme.getTheme());
                }
            }
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, textX, sy + 6, color);
        }
    }

    private String localizeOrFallback(String key, String fallback) {
        String translated = StatCollector.translateToLocal(key);
        return key.equals(translated) ? fallback : translated;
    }
}
