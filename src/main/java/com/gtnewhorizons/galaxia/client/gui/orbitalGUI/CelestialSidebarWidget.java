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
    private final SupplyDebugPanel supplyDebugPanel;
    private final List<VisibleEntry> cachedVisibleEntries = new ArrayList<>();
    private final List<RowLayout> cachedRowLayouts = new ArrayList<>();
    private boolean visibleEntriesDirty = true;
    private boolean rowLayoutsDirty = true;
    private double cachedRowLayoutScrollOffset = Double.NaN;
    private int cachedRowLayoutHeight = -1;
    private int cachedRowLayoutWidth = -1;
    private int cachedSearchOffset = Integer.MIN_VALUE;

    public CelestialSidebarWidget(CelestialObject root, CelestialObject currentSystem,
        OrbitalView.OrbitalMapWidget map) {
        this.root = root;
        this.map = map;
        this.currentSystem = currentSystem;
        this.activeLayer = currentSystem == null ? root : currentSystem;
        this.supplyDebugPanel = new SupplyDebugPanel(map, this);
        expanded.add(root);
        if (this.currentSystem != null) expanded.add(this.currentSystem);
    }

    @Override
    public void onInit() {
        super.onInit();
        searchField = new TextFieldWidget().left(14)
            .top(CelestialSidebarUIConstants.SEARCH_FIELD_TOP)
            .right(8)
            .height(16)
            .setMaxLength(64)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText(localizeOrFallback("galaxia.gui.orbital.search.placeholder", "Enter name..."))
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        child(searchField);

        supplyDebugPanel.init();

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
            if (pickedStack != null) {
                ItemStack displayStack = pickedStack.copy();
                displayStack.stackSize = 1;
                supplyDebugPanel.onItemPicked(displayStack);
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
        if (supplyDebugPanel.handleButtonClick(localX, localYAbsolute)) return true;
        if (supplyDebugPanel.isPanelOpen() && supplyDebugPanel.handlePanelClick(localX, localYAbsolute, getArea().width)) return true;
        if (activeLayer == root) return false;
        VisibleEntry entry = findVisibleRowAt(localX, localYAbsolute);
        if (entry == null) return false;
        if (entry.hasChildren() && localX < CelestialSidebarUIConstants.ARROW_ZONE + entry.depth() * 24) {
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
        return Math.max(0, cachedVisibleEntries.size() * CelestialSidebarUIConstants.LINE_HEIGHT - getArea().height + getListTop() + 20);
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
            int sy = y + i * CelestialSidebarUIConstants.LINE_HEIGHT;
            if (sy < 50 || sy > getArea().height - 10) continue;
            VisibleEntry entry = cachedVisibleEntries.get(i);
            int iconX = 10 + entry.depth() * 24;
            int textX = 22 + entry.depth() * 24;
            int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(
                entry.body()
                    .displayName());
            int rowLeft = Math.max(0, iconX - 4);
            int rowRight = Math.min(getArea().width - 10, Math.max(textX + textWidth + 4, rowLeft + 16));
            cachedRowLayouts.add(new RowLayout(entry, rowLeft, rowRight, sy, sy + CelestialSidebarUIConstants.LINE_HEIGHT));
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
        if (supplyDebugPanel.shouldShowButton()) offset += 26;
        if (supplyDebugPanel.isPanelOpen()) offset += 128; // Add panel height + padding to clear it
        return offset;
    }

    private int getSearchFieldTop() {
        return CelestialSidebarUIConstants.SEARCH_FIELD_TOP + getSearchOffset();
    }

    private int getSearchLabelTop() {
        return CelestialSidebarUIConstants.SEARCH_LABEL_TOP + getSearchOffset();
    }

    private int getListTop() {
        return CelestialSidebarUIConstants.LIST_TOP + getSearchOffset();
    }

    private int getCreativeButtonWidth() {
        return Math.max(112, Minecraft.getMinecraft().fontRenderer.getStringWidth("Creative Mode") + 18);
    }

    private boolean handleLayerButtonClick(int localX, int localY) {
        int galaxyButtonWidth = 70;
        int starButtonX = 18 + galaxyButtonWidth + CelestialSidebarUIConstants.LAYER_BUTTON_GAP;
        int starButtonWidth = Math
            .max(80, Minecraft.getMinecraft().fontRenderer.getStringWidth(getCurrentSystemLabel()) + 18);
        if (localY >= CelestialSidebarUIConstants.LAYER_BUTTON_TOP && localY <= CelestialSidebarUIConstants.LAYER_BUTTON_TOP + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT) {
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
        if (localY >= CelestialSidebarUIConstants.CREATIVE_BUTTON_TOP && localY <= CelestialSidebarUIConstants.CREATIVE_BUTTON_TOP + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT
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
        if (localY >= CelestialSidebarUIConstants.TRANSFER_SIMULATOR_BUTTON_TOP && localY <= CelestialSidebarUIConstants.TRANSFER_SIMULATOR_BUTTON_TOP + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT
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

    private void drawLayerButton(int x, int y, int width, String label, boolean selected) {
        int bg = selected ? EnumColors.MAP_COLOR_SIDEBAR_LAYER_BTN_BG_SELECTED.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_LAYER_BTN_BG_NORMAL.getColor();
        int border = selected ? EnumColors.MapSidebarListHovered.getColor()
            : EnumColors.MAP_COLOR_SIDEBAR_INLINE_BTN_BORDER_DISABLED.getColor();
        Gui.drawRect(x, y, x + width, y + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT, bg);
        Gui.drawRect(x, y, x + width, y + 1, border);
        Gui.drawRect(x, y + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT - 1, x + width, y + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT, border);
        Gui.drawRect(x, y, x + 1, y + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT, border);
        Gui.drawRect(x + width - 1, y, x + width, y + CelestialSidebarUIConstants.LAYER_BUTTON_HEIGHT, border);
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
            if (activeLayer == root || supplyDebugPanel.isPanelOpen()) {
                searchField.top(-1000);
                if (searchField.isEnabled()) searchField.setEnabled(false);
            } else {
                searchField.top(getSearchFieldTop());
                if (!searchField.isEnabled()) searchField.setEnabled(true);
            }
        }
        Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapSidebarBackground.getColor());
        drawLayerButton(18, CelestialSidebarUIConstants.LAYER_BUTTON_TOP, 70, "Galaxy", activeLayer == root);
        drawLayerButton(
            18 + 70 + CelestialSidebarUIConstants.LAYER_BUTTON_GAP,
            CelestialSidebarUIConstants.LAYER_BUTTON_TOP,
            Math.max(80, Minecraft.getMinecraft().fontRenderer.getStringWidth(getCurrentSystemLabel()) + 18),
            getCurrentSystemLabel(),
            activeLayer == currentSystem);
        if (shouldShowCreativeButton()) drawLayerButton(
            18,
            CelestialSidebarUIConstants.CREATIVE_BUTTON_TOP,
            getCreativeButtonWidth(),
            "Creative Mode",
            map.isCreativeBuildModeEnabled());
        if (shouldShowTransferSimulatorButton()) drawLayerButton(
            18,
            CelestialSidebarUIConstants.TRANSFER_SIMULATOR_BUTTON_TOP,
            Math.max(132, Minecraft.getMinecraft().fontRenderer.getStringWidth("Transfer Simulator") + 18),
            "Transfer Simulator",
            map.isTransferSimulatorOpen());
        if (supplyDebugPanel.shouldShowButton()) drawLayerButton(
            18,
            CelestialSidebarUIConstants.SUPPLY_DEBUG_BUTTON_TOP,
            Math.max(112, Minecraft.getMinecraft().fontRenderer.getStringWidth("Supply Debug") + 18),
            "Supply Debug",
            supplyDebugPanel.isPanelOpen());
        supplyDebugPanel.updateFieldPositions();
        if (supplyDebugPanel.isPanelOpen()) {
            supplyDebugPanel.draw(context, widgetTheme, getArea().width);
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
