package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class StationInventoryPanelWidget extends ParentWidget<StationInventoryPanelWidget>
    implements StationOverlayCoordinator.Overlay {

    static final int BUTTON_WIDTH = 78;
    static final int BUTTON_HEIGHT = 20;
    static final int PANEL_WIDTH = 384;
    static final int PANEL_HEIGHT = 236;

    private static final int PANEL_Y = BUTTON_HEIGHT + 4;
    private static final int HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 2;
    private static final int SCROLL_X = 6;
    private static final int SCROLL_Y = 48;
    private static final int SCROLL_WIDTH = PANEL_WIDTH - 12;
    private static final int SCROLL_HEIGHT = PANEL_HEIGHT - SCROLL_Y - 8;
    private static final int ICON_X = 4;
    private static final int NAME_X = 24;
    private static final int NAME_WIDTH = 104;
    private static final int AMOUNT_X = 136;
    private static final int ROW_RIGHT_PADDING = 8;
    private static final int CONTROL_GAP = 4;
    private static final int BOUNDS_WIDTH = 54;
    private static final int AMOUNT_INPUT_WIDTH = 44;
    private static final int MODE_BUTTON_WIDTH = 52;
    private static final int VOID_WIDTH = 42;
    private static final int VOID_X = SCROLL_WIDTH - ROW_RIGHT_PADDING - VOID_WIDTH;
    private static final int MODE_BUTTON_X = VOID_X - CONTROL_GAP - MODE_BUTTON_WIDTH;
    private static final int AMOUNT_INPUT_X = MODE_BUTTON_X - CONTROL_GAP - AMOUNT_INPUT_WIDTH;
    private static final int BOUNDS_X = AMOUNT_INPUT_X - CONTROL_GAP - BOUNDS_WIDTH;
    private static final int BOUND_MARKER_SIZE = 4;
    private static final int BOUND_MARKER_WARNING = 0xFFFFFF00;
    private static final int BOUND_MARKER_BLOCKING = 0xFFFF0000;
    private static final int BOUND_EDITOR_X = 92;
    private static final int BOUND_EDITOR_Y = 58;
    private static final int BOUND_EDITOR_WIDTH = 276;
    private static final int BOUND_EDITOR_HEIGHT = 124;
    private static final int BOUND_FIELD_X = BOUND_EDITOR_X + 96;
    private static final int BOUND_SET_X = BOUND_FIELD_X + 74;
    private static final int BOUND_CLEAR_X = BOUND_SET_X + 44;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final @Nullable CelestialAsset.ID assetId;
    private final ParentWidget<?> panelRoot = new ParentWidget<>();
    private final VerticalScrollData scrollData = new VerticalScrollData();
    private final ParentWidget<?> scrollContent = new ParentWidget<>().widthRel(1f);
    private final ParentWidget<?> boundEditorRoot = new ParentWidget<>();
    private final TextWidget<?> emptyInventoryText = new TextWidget<>(IKey.str("Inventory is empty."))
        .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
        .shadow(true)
        .pos(8, 48);
    private final Map<String, Boolean> amountModes = new LinkedHashMap<>();
    private final Map<String, String> amountInputs = new LinkedHashMap<>();
    private ResourceMode resourceMode = ResourceMode.ITEMS;
    private @Nullable ItemStackWrapper selectedBoundItem;
    private @Nullable String selectedBoundFluid;
    private String inputBoundAmount = "";
    private String outputBoundAmount = "";
    private @Nullable TextFieldWidget inputBoundField;
    private @Nullable TextFieldWidget outputBoundField;
    private final StationOverlayCoordinator overlayCoordinator;
    private boolean open;
    private String rowStructureSignature = "";

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId) {
        this(assetId, new StationOverlayCoordinator());
    }

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId, StationOverlayCoordinator overlayCoordinator) {
        this.assetId = assetId;
        this.overlayCoordinator = overlayCoordinator;
        overlayCoordinator.register(this);
        size(PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        child(
            ModuleConfigModalSupport.button(() -> assetId != null, this::toggleLabel, this::toggleOpen)
                .pos(0, 0)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(
                    () -> open,
                    () -> resourceMode == ResourceMode.ITEMS ? "* Items" : "Items",
                    () -> setResourceMode(ResourceMode.ITEMS))
                .pos(BUTTON_WIDTH + 6, 0)
                .size(70, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(
                    () -> open,
                    () -> resourceMode == ResourceMode.FLUIDS ? "* Fluids" : "Fluids",
                    () -> setResourceMode(ResourceMode.FLUIDS))
                .pos(BUTTON_WIDTH + 80, 0)
                .size(74, BUTTON_HEIGHT));
        panelRoot.pos(0, PANEL_Y)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .setEnabled(false);
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(SCROLL_X, SCROLL_Y)
            .size(SCROLL_WIDTH, SCROLL_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
        scroll.child(scrollContent);
        panelRoot.child(scroll);
        emptyInventoryText.setEnabled(false);
        panelRoot.child(emptyInventoryText);
        boundEditorRoot.pos(0, 0)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .overlay(drawable((ctx, x, y, w, h) -> drawBoundEditorOverlay(x, y, w, h)))
            .setEnabled(false);
        boundEditorRoot.child(
            boundField(true).pos(BOUND_FIELD_X, BOUND_EDITOR_Y + 34)
                .size(70, 18));
        boundEditorRoot.child(
            boundField(false).pos(BOUND_FIELD_X, BOUND_EDITOR_Y + 58)
                .size(70, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::isBoundEditorOpen, "Set", () -> applyBound(true))
                .pos(BOUND_SET_X, BOUND_EDITOR_Y + 34)
                .size(40, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::isBoundEditorOpen, "Clear", () -> clearBound(true))
                .pos(BOUND_CLEAR_X, BOUND_EDITOR_Y + 34)
                .size(50, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::isBoundEditorOpen, "Set", () -> applyBound(false))
                .pos(BOUND_SET_X, BOUND_EDITOR_Y + 58)
                .size(40, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::isBoundEditorOpen, "Clear", () -> clearBound(false))
                .pos(BOUND_CLEAR_X, BOUND_EDITOR_Y + 58)
                .size(50, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::isBoundEditorOpen, "Close", this::closeBoundEditor)
                .pos(BOUND_EDITOR_X + BOUND_EDITOR_WIDTH - 62, BOUND_EDITOR_Y + BOUND_EDITOR_HEIGHT - 26)
                .size(54, 18));
        panelRoot.child(boundEditorRoot);
        child(panelRoot);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!open) {
            if (panelRoot.isEnabled()) {
                panelRoot.setEnabled(false);
                boundEditorRoot.setEnabled(false);
                rowStructureSignature = "";
            }
            return;
        }
        AutomatedFacility facility = facility();
        if (facility == null) {
            open = false;
            return;
        }
        List<Map.Entry<ItemStackWrapper, Long>> itemRows = rows(facility);
        List<StationInventoryPanelModel.FluidRow> fluidRows = fluidRows(facility);
        refreshAmountInputs(itemRows);
        String nextSignature = rowStructureSignature(itemRows, fluidRows);
        if (!panelRoot.isEnabled() || !nextSignature.equals(rowStructureSignature)) {
            rebuildPanel(itemRows, fluidRows);
            rowStructureSignature = nextSignature;
        }
        boundEditorRoot.setEnabled(isBoundEditorOpen());
    }

    @Override
    public boolean canHoverThrough() {
        return !open;
    }

    @Override
    public boolean canHover() {
        return open || super.canHover();
    }

    boolean isPointInPanel(int localX, int localY) {
        return open && localX >= 0 && localX <= PANEL_WIDTH && localY >= PANEL_Y && localY <= PANEL_Y + PANEL_HEIGHT;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        closeBoundEditor();
    }

    @Override
    public boolean containsMouse(int mouseX, int mouseY) {
        if (!open) return false;
        int left = getArea().rx;
        int top = getArea().ry;
        return mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= top && mouseY < top + PANEL_Y + PANEL_HEIGHT;
    }

    @Override
    public boolean blocksInput() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawBackground(context, widgetTheme);
        if (!open) return;
        ModuleConfigModalSupport.drawFrameAt("Station Inventory", 0, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        ModuleConfigModalSupport.drawLine(
            resourceMode == ResourceMode.ITEMS ? "Item" : "Fluid",
            NAME_X + SCROLL_X,
            PANEL_Y + 32,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport
            .drawLine("Amount", AMOUNT_X + SCROLL_X, PANEL_Y + 32, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void drawBoundEditorOverlay(int x, int y, int width, int height) {
        if (!isBoundEditorOpen()) return;
        Gui.drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_OVERLAY_BG.getColor());
        int editorX = x + BOUND_EDITOR_X;
        int editorY = y + BOUND_EDITOR_Y;
        ModuleConfigModalSupport
            .drawFrameAt("Inventory Bounds", editorX, editorY, BOUND_EDITOR_WIDTH, BOUND_EDITOR_HEIGHT);
        ModuleConfigModalSupport.drawTrimmedLine(
            selectedBoundName(),
            editorX + 10,
            editorY + 26,
            BOUND_EDITOR_WIDTH - 20,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport
            .drawLine("Input lower", editorX + 10, editorY + 39, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport
            .drawLine("Output upper", editorX + 10, editorY + 63, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void rebuildPanel(List<Map.Entry<ItemStackWrapper, Long>> itemRows,
        List<StationInventoryPanelModel.FluidRow> fluidRows) {
        panelRoot.setEnabled(true);
        scrollContent.removeAll();
        boolean empty = resourceMode == ResourceMode.ITEMS ? itemRows.isEmpty() : fluidRows.isEmpty();
        emptyInventoryText.setEnabled(empty);
        if (empty) {
            scrollContent.height(SCROLL_HEIGHT);
            scrollData.setScrollSize(SCROLL_HEIGHT);
            panelRoot.scheduleResize();
            return;
        }

        int y = 0;
        if (resourceMode == ResourceMode.ITEMS) {
            for (Map.Entry<ItemStackWrapper, Long> row : itemRows) {
                String rowKey = row.getKey()
                    .toKey();
                amountModes.putIfAbsent(rowKey, false);
                amountInputs.putIfAbsent(rowKey, Long.toString(row.getValue()));
                scrollContent.child(buildRow(row).pos(0, y));
                y += ROW_HEIGHT + ROW_GAP;
            }
        } else {
            for (StationInventoryPanelModel.FluidRow row : fluidRows) {
                scrollContent.child(buildFluidRow(row).pos(0, y));
                y += ROW_HEIGHT + ROW_GAP;
            }
        }
        int contentHeight = Math.max(SCROLL_HEIGHT, y);
        scrollContent.height(contentHeight);
        scrollData.setScrollSize(contentHeight);
        panelRoot.scheduleResize();
    }

    private ParentWidget<?> buildRow(Map.Entry<ItemStackWrapper, Long> row) {
        ItemStackWrapper wrapper = row.getKey();
        ItemStack displayStack = wrapper.toStack(1);
        String rowKey = wrapper.toKey();
        ParentWidget<?> rowWidget = new ParentWidget<>().widthRel(1f)
            .height(ROW_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
        rowWidget.child(drawable((ctx, x, y, w, h) -> {
            renderItemIcon(displayStack, x, y + 4);
            renderBoundMarkers(wrapper, x, y + 4);
        }).asWidget()
            .pos(ICON_X, 0)
            .size(16, ROW_HEIGHT)
            .tooltip(t -> t.addLine(displayStack.getDisplayName())));
        rowWidget.child(
            drawable(
                (ctx, x, y, w, h) -> ModuleConfigModalSupport.drawTrimmedLine(
                    displayStack.getDisplayName(),
                    x,
                    y + 8,
                    NAME_WIDTH,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor())).asWidget()
                        .pos(NAME_X, 0)
                        .size(NAME_WIDTH, ROW_HEIGHT));
        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> formatAmount(currentAmount(wrapper))))
                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(AMOUNT_X, 8));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> canEditBounds(wrapper), "Bounds", () -> openBoundEditor(wrapper))
                .pos(BOUNDS_X, 3)
                .size(BOUNDS_WIDTH, 18));
        rowWidget.child(
            amountField(rowKey).pos(AMOUNT_INPUT_X, 3)
                .size(AMOUNT_INPUT_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> isAmountMode(rowKey), "Amount", () -> setAmountMode(rowKey, false))
                .pos(MODE_BUTTON_X, 3)
                .size(MODE_BUTTON_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> !isAmountMode(rowKey), "ALL", () -> setAmountMode(rowKey, true))
                .pos(MODE_BUTTON_X, 3)
                .size(MODE_BUTTON_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> currentAmount(wrapper) > 0L, "Void", () -> voidRow(wrapper))
                .pos(VOID_X, 3)
                .size(VOID_WIDTH, 18));
        return rowWidget;
    }

    private ParentWidget<?> buildFluidRow(StationInventoryPanelModel.FluidRow row) {
        String fluidName = row.fluidName();
        ParentWidget<?> rowWidget = new ParentWidget<>().widthRel(1f)
            .height(ROW_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
        rowWidget.child(drawable((ctx, x, y, w, h) -> {
            renderFluidIcon(fluidName, x, y + 4);
            renderFluidBoundMarkers(fluidName, x, y + 4);
        }).asWidget()
            .pos(ICON_X, 0)
            .size(16, ROW_HEIGHT)
            .tooltip(t -> t.addLine(fluidName)));
        rowWidget.child(
            drawable(
                (ctx, x, y, w, h) -> ModuleConfigModalSupport
                    .drawTrimmedLine(fluidName, x, y + 8, NAME_WIDTH, EnumColors.MAP_COLOR_TEXT_BODY.getColor()))
                        .asWidget()
                        .pos(NAME_X, 0)
                        .size(NAME_WIDTH, ROW_HEIGHT));
        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> formatAmount(currentFluidAmount(fluidName))))
                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(AMOUNT_X, 8));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> canEditBounds(fluidName), "Bounds", () -> openBoundEditor(fluidName))
                .pos(BOUNDS_X, 3)
                .size(BOUNDS_WIDTH, 18));
        return rowWidget;
    }

    private TextFieldWidget amountField(String rowKey) {
        return new TextFieldWidget().setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .value(
                new StringValue.Dynamic(
                    () -> amountInputs.getOrDefault(rowKey, "0"),
                    text -> { amountInputs.put(rowKey, text == null ? "" : text); }))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> isAmountMode(rowKey));
    }

    private TextFieldWidget boundField(boolean input) {
        TextFieldWidget field = new TextFieldWidget().setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .value(new StringValue.Dynamic(() -> input ? inputBoundAmount : outputBoundAmount, text -> {
                if (input) {
                    inputBoundAmount = text == null ? "" : text;
                } else {
                    outputBoundAmount = text == null ? "" : text;
                }
            }))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> isBoundEditorOpen());
        if (input) {
            inputBoundField = field;
        } else {
            outputBoundField = field;
        }
        return field;
    }

    private void toggleOpen() {
        open = !open;
        if (open) {
            overlayCoordinator.closeOthers(this);
        } else {
            closeBoundEditor();
        }
    }

    private void setResourceMode(ResourceMode mode) {
        if (resourceMode == mode) return;
        resourceMode = mode;
        closeBoundEditor();
        rowStructureSignature = "";
    }

    private String toggleLabel() {
        return open ? "Close Inv" : "Inventory";
    }

    private void setAmountMode(String rowKey, boolean amountMode) {
        amountModes.put(rowKey, amountMode);
    }

    private boolean isAmountMode(String rowKey) {
        return amountModes.getOrDefault(rowKey, false);
    }

    private void voidRow(ItemStackWrapper wrapper) {
        if (assetId == null) return;
        String rowKey = wrapper.toKey();
        long amount = StationInventoryPanelModel
            .voidAmount(isAmountMode(rowKey), currentAmount(wrapper), amountInputs.getOrDefault(rowKey, ""));
        if (amount <= 0L) return;
        if (amount >= currentAmount(wrapper)) {
            CelestialClient.removeInventory(assetId, wrapper);
        } else {
            CelestialClient.removeInventoryAmount(assetId, wrapper, amount);
        }
    }

    private boolean canEditBounds(ItemStackWrapper wrapper) {
        return assetId != null && wrapper != null;
    }

    private boolean canEditBounds(String fluidName) {
        return assetId != null && fluidName != null && !fluidName.isEmpty();
    }

    private boolean isBoundEditorOpen() {
        return open && (selectedBoundItem != null || selectedBoundFluid != null);
    }

    private void openBoundEditor(ItemStackWrapper wrapper) {
        selectedBoundItem = wrapper;
        selectedBoundFluid = null;
        AutomatedFacility facility = facility();
        inputBoundAmount = facility != null && facility.inventory.hasItemLowerBound(wrapper)
            ? Long.toString(facility.inventory.itemLowerBoundOrDefault(wrapper))
            : "";
        outputBoundAmount = facility != null && facility.inventory.hasItemUpperBound(wrapper)
            ? Long.toString(facility.inventory.itemUpperBoundOrDefault(wrapper))
            : "";
        if (inputBoundField != null) inputBoundField.setText(inputBoundAmount);
        if (outputBoundField != null) outputBoundField.setText(outputBoundAmount);
    }

    private void openBoundEditor(String fluidName) {
        selectedBoundItem = null;
        selectedBoundFluid = fluidName;
        AutomatedFacility facility = facility();
        inputBoundAmount = facility != null && facility.inventory.hasFluidLowerBound(fluidName)
            ? Long.toString(facility.inventory.fluidLowerBoundOrDefault(fluidName))
            : "";
        outputBoundAmount = facility != null && facility.inventory.hasFluidUpperBound(fluidName)
            ? Long.toString(facility.inventory.fluidUpperBoundOrDefault(fluidName))
            : "";
        if (inputBoundField != null) inputBoundField.setText(inputBoundAmount);
        if (outputBoundField != null) outputBoundField.setText(outputBoundAmount);
    }

    private void closeBoundEditor() {
        selectedBoundItem = null;
        selectedBoundFluid = null;
    }

    private void applyBound(boolean input) {
        if (assetId == null || !isBoundEditorOpen()) return;
        String text = input ? inputBoundAmount : outputBoundAmount;
        long amount = parseAmount(text);
        BoundKind kind = selectedBoundItem != null ? (input ? BoundKind.ITEM_LOWER : BoundKind.ITEM_UPPER)
            : (input ? BoundKind.FLUID_LOWER : BoundKind.FLUID_UPPER);
        String resourceKey = selectedBoundItem != null ? selectedBoundItem.toKey() : selectedBoundFluid;
        AutomatedFacility facility = facility();
        if (facility != null && selectedBoundItem != null && input) {
            facility.inventory.setItemLowerBound(selectedBoundItem, amount);
        } else if (facility != null && selectedBoundItem != null) {
            facility.inventory.setItemUpperBound(selectedBoundItem, amount);
        } else if (facility != null) {
            facility.inventory.setBound(kind, resourceKey, amount);
        }
        CelestialClient.updateInventoryBound(
            assetId,
            AssetModuleUpdatePacket.ConfigAction.SET_INVENTORY_BOUND,
            kind,
            resourceKey,
            amount);
    }

    private void clearBound(boolean input) {
        if (assetId == null || !isBoundEditorOpen()) return;
        BoundKind kind = selectedBoundItem != null ? (input ? BoundKind.ITEM_LOWER : BoundKind.ITEM_UPPER)
            : (input ? BoundKind.FLUID_LOWER : BoundKind.FLUID_UPPER);
        String resourceKey = selectedBoundItem != null ? selectedBoundItem.toKey() : selectedBoundFluid;
        AutomatedFacility facility = facility();
        if (facility != null && selectedBoundItem != null && input) {
            facility.inventory.clearItemLowerBound(selectedBoundItem);
        } else if (facility != null && selectedBoundItem != null) {
            facility.inventory.clearItemUpperBound(selectedBoundItem);
        } else if (facility != null) {
            facility.inventory.clearBound(kind, resourceKey);
        }
        CelestialClient.updateInventoryBound(
            assetId,
            AssetModuleUpdatePacket.ConfigAction.CLEAR_INVENTORY_BOUND,
            kind,
            resourceKey,
            0L);
        if (input) {
            inputBoundAmount = "";
            if (inputBoundField != null) inputBoundField.setText("");
        } else {
            outputBoundAmount = "";
            if (outputBoundField != null) outputBoundField.setText("");
        }
        facility = facility();
        if (facility != null && selectedBoundItem != null
            && currentAmount(selectedBoundItem) <= 0L
            && !facility.inventory.hasItemLowerBound(selectedBoundItem)
            && !facility.inventory.hasItemUpperBound(selectedBoundItem)) {
            closeBoundEditor();
        } else if (facility != null && selectedBoundFluid != null
            && currentFluidAmount(selectedBoundFluid) <= 0L
            && !facility.inventory.hasFluidLowerBound(selectedBoundFluid)
            && !facility.inventory.hasFluidUpperBound(selectedBoundFluid)) {
                closeBoundEditor();
            }
    }

    private String selectedBoundName() {
        if (selectedBoundItem != null) return selectedBoundItem.toStack(1)
            .getDisplayName();
        return selectedBoundFluid == null ? "" : selectedBoundFluid;
    }

    private static long parseAmount(String text) {
        if (text == null || text.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long currentAmount(ItemStackWrapper wrapper) {
        AutomatedFacility facility = facility();
        return facility == null ? 0L : facility.inventory.getAmount(wrapper);
    }

    private long currentFluidAmount(String fluidName) {
        AutomatedFacility facility = facility();
        return facility == null ? 0L : facility.inventory.getFluidAmount(fluidName);
    }

    private @Nullable AutomatedFacility facility() {
        return assetId != null && CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility
            : null;
    }

    private List<Map.Entry<ItemStackWrapper, Long>> rows(AutomatedFacility facility) {
        return StationInventoryPanelModel.inventoryRows(facility.inventory);
    }

    private List<StationInventoryPanelModel.FluidRow> fluidRows(AutomatedFacility facility) {
        return StationInventoryPanelModel.fluidRows(facility.inventory);
    }

    private void refreshAmountInputs(List<Map.Entry<ItemStackWrapper, Long>> rows) {
        for (Map.Entry<ItemStackWrapper, Long> row : rows) {
            String rowKey = row.getKey()
                .toKey();
            if (!isAmountMode(rowKey)) {
                amountInputs.put(rowKey, Long.toString(row.getValue()));
            }
        }
    }

    private String rowStructureSignature(List<Map.Entry<ItemStackWrapper, Long>> itemRows,
        List<StationInventoryPanelModel.FluidRow> fluidRows) {
        StringBuilder signature = new StringBuilder((itemRows.size() + fluidRows.size()) * 24);
        signature.append(resourceMode)
            .append(':');
        for (Map.Entry<ItemStackWrapper, Long> row : itemRows) {
            signature.append(
                row.getKey()
                    .toKey())
                .append(';');
        }
        signature.append('|');
        for (StationInventoryPanelModel.FluidRow row : fluidRows) {
            signature.append(row.fluidName())
                .append(';');
        }
        return signature.toString();
    }

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || mc.getTextureManager() == null) return;
        com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
        com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 200f;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
    }

    private void renderBoundMarkers(ItemStackWrapper wrapper, int x, int y) {
        AutomatedFacility facility = facility();
        if (facility == null) return;
        if (facility.inventory.hasItemLowerBound(wrapper)) {
            int color = facility.inventory.getAmount(wrapper) < facility.inventory.itemLowerBoundOrDefault(wrapper)
                ? BOUND_MARKER_BLOCKING
                : BOUND_MARKER_WARNING;
            Gui.drawRect(x, y, x + BOUND_MARKER_SIZE, y + BOUND_MARKER_SIZE, color);
        }
        if (facility.inventory.hasItemUpperBound(wrapper)) {
            int color = facility.inventory.getAmount(wrapper) >= facility.inventory.itemUpperBoundOrDefault(wrapper)
                ? BOUND_MARKER_BLOCKING
                : BOUND_MARKER_WARNING;
            Gui.drawRect(x + 16 - BOUND_MARKER_SIZE, y, x + 16, y + BOUND_MARKER_SIZE, color);
        }
    }

    private void renderFluidIcon(String fluidName, int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, EnumColors.MAP_COLOR_SIDEBAR_GHOST_SLOT_BG.getColor());
        Gui.drawRect(x + 2, y + 2, x + 14, y + 14, EnumColors.MAP_COLOR_CONNECTOR_TANK.getColor());
    }

    private void renderFluidBoundMarkers(String fluidName, int x, int y) {
        AutomatedFacility facility = facility();
        if (facility == null) return;
        if (facility.inventory.hasFluidLowerBound(fluidName)) {
            int color = facility.inventory.getFluidAmount(fluidName)
                < facility.inventory.fluidLowerBoundOrDefault(fluidName) ? BOUND_MARKER_BLOCKING : BOUND_MARKER_WARNING;
            Gui.drawRect(x, y, x + BOUND_MARKER_SIZE, y + BOUND_MARKER_SIZE, color);
        }
        if (facility.inventory.hasFluidUpperBound(fluidName)) {
            int color = facility.inventory.getFluidAmount(fluidName)
                >= facility.inventory.fluidUpperBoundOrDefault(fluidName) ? BOUND_MARKER_BLOCKING
                    : BOUND_MARKER_WARNING;
            Gui.drawRect(x + 16 - BOUND_MARKER_SIZE, y, x + 16, y + BOUND_MARKER_SIZE, color);
        }
    }

    private static String formatAmount(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private com.cleanroommc.modularui.api.drawable.IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    private enum ResourceMode {
        ITEMS,
        FLUIDS
    }
}
