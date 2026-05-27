package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

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
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;

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
    private static final int AMOUNT_X = 136;
    private static final int ITEM_INTERACTION_BUTTON_SIZE = 18;
    private static final int ITEM_INTERACTION_BUTTON_X = AMOUNT_X - ITEM_INTERACTION_BUTTON_SIZE - 4;
    private static final int NAME_WIDTH = ITEM_INTERACTION_BUTTON_X - NAME_X - 4;
    private static final int ROW_RIGHT_PADDING = 8;
    private static final int CONTROL_GAP = 4;
    private static final int BOUNDS_WIDTH = 54;
    private static final int AMOUNT_INPUT_WIDTH = 44;
    private static final int MODE_BUTTON_WIDTH = 52;
    private static final int VOID_WIDTH = 42;
    private static final int VOID_X = SCROLL_WIDTH - ROW_RIGHT_PADDING - VOID_WIDTH;
    private static final int MODE_BUTTON_X = VOID_X - CONTROL_GAP - MODE_BUTTON_WIDTH;
    private static final int UPKEEP_AUTO_ORDER_WIDTH = 18;
    private static final int AMOUNT_INPUT_X = MODE_BUTTON_X - CONTROL_GAP - AMOUNT_INPUT_WIDTH;
    private static final int BOUNDS_X = AMOUNT_INPUT_X - CONTROL_GAP - BOUNDS_WIDTH;
    private static final int UPKEEP_USE_X = AMOUNT_X;
    private static final int UPKEEP_STOCK_X = 212;
    private static final int UPKEEP_RESERVE_INPUT_X = 250;
    private static final int UPKEEP_AUTO_ORDER_X = UPKEEP_RESERVE_INPUT_X + AMOUNT_INPUT_WIDTH + 8;
    private static final int BOUND_MARKER_SIZE = 4;
    private static final int BOUND_MARKER_WARNING = EnumColors.MAP_COLOR_RECIPE_BOUND_MARKER_WARNING.getColor();
    private static final int BOUND_MARKER_BLOCKING = EnumColors.MAP_COLOR_INVENTORY_BOUND_MARKER_BLOCKING.getColor();
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
    private final ParentWidget<?> itemInteractionRoot = new ParentWidget<>();
    private ResourceMode resourceMode = ResourceMode.ITEMS;
    private final TextWidget<?> emptyInventoryText = new TextWidget<>(
        IKey.dynamic(() -> resourceMode == ResourceMode.UPKEEP ? "No upkeep demand." : "Inventory is empty."))
            .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .shadow(true)
            .pos(8, 48);
    private final Map<String, Boolean> amountModes = new LinkedHashMap<>();
    private final Map<String, String> amountInputs = new LinkedHashMap<>();
    private final Map<String, String> upkeepReserveInputs = new LinkedHashMap<>();
    private @Nullable ItemStackWrapper selectedBoundItem;
    private @Nullable FluidKey selectedBoundFluid;
    private String inputBoundAmount = "";
    private String outputBoundAmount = "";
    private @Nullable TextFieldWidget inputBoundField;
    private @Nullable TextFieldWidget outputBoundField;
    private final StationOverlayCoordinator overlayCoordinator;
    private final @Nullable ModuleConfigModalController configController;
    private final BooleanSupplier visibleSupplier;
    private boolean open;
    private String rowStructureSignature = "";
    private Map<ItemStackWrapper, Long> cachedItemAmounts = Map.of();
    private Map<FluidKey, Long> cachedFluidAmounts = Map.of();
    private @Nullable ItemStackWrapper selectedInteractionItem;

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId) {
        this(assetId, new StationOverlayCoordinator());
    }

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId, StationOverlayCoordinator overlayCoordinator) {
        this(assetId, overlayCoordinator, null);
    }

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId, StationOverlayCoordinator overlayCoordinator,
        @Nullable ModuleConfigModalController configController) {
        this(assetId, overlayCoordinator, configController, () -> true);
    }

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId, StationOverlayCoordinator overlayCoordinator,
        @Nullable ModuleConfigModalController configController, BooleanSupplier visibleSupplier) {
        this.assetId = assetId;
        this.overlayCoordinator = overlayCoordinator;
        this.configController = configController;
        this.visibleSupplier = visibleSupplier;
        overlayCoordinator.register(this);
        size(PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        child(
            ModuleConfigModalSupport.button(() -> isVisible() && assetId != null, this::toggleLabel, this::toggleOpen)
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
                    () -> open && af() != null,
                    () -> resourceMode == ResourceMode.FLUIDS ? "* Fluids" : "Fluids",
                    () -> setResourceMode(ResourceMode.FLUIDS))
                .pos(BUTTON_WIDTH + 80, 0)
                .size(74, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(
                    () -> open && af() != null,
                    () -> resourceMode == ResourceMode.UPKEEP ? "* Upkeep" : "Upkeep",
                    () -> setResourceMode(ResourceMode.UPKEEP))
                .pos(BUTTON_WIDTH + 158, 0)
                .size(82, BUTTON_HEIGHT));
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
            ModuleConfigModalSupport.button(this::canApplyBound, "Set", () -> applyBound(true))
                .pos(BOUND_SET_X, BOUND_EDITOR_Y + 34)
                .size(40, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::isBoundEditorOpen, "Clear", () -> clearBound(true))
                .pos(BOUND_CLEAR_X, BOUND_EDITOR_Y + 34)
                .size(50, 18));
        boundEditorRoot.child(
            ModuleConfigModalSupport.button(this::canApplyBound, "Set", () -> applyBound(false))
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
        itemInteractionRoot.pos(0, 0)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .setEnabled(false);
        panelRoot.child(itemInteractionRoot);
        child(panelRoot);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!isVisible()) {
            close();
            disablePanelContent();
            return;
        }
        if (!open) {
            disablePanelContent();
            return;
        }
        IDistributedInventory distributed = distributed();
        if (distributed == null) {
            open = false;
            return;
        }
        cachedItemAmounts = distributed.aggregatedItems();
        cachedFluidAmounts = distributed.aggregatedFluids();
        List<Map.Entry<ItemStackWrapper, Long>> itemRows = rows(distributed);
        List<StationInventoryPanelModel.FluidRow> fluidRows = fluidRows(distributed);
        AutomatedFacility af = af();
        List<StationInventoryPanelModel.UpkeepItemRow> upkeepRows = af == null ? List.of()
            : StationInventoryPanelModel.upkeepItemRows(af);
        refreshAmountInputs(itemRows, upkeepRows);
        String nextSignature = rowStructureSignature(itemRows, fluidRows, upkeepRows);
        if (!panelRoot.isEnabled() || !nextSignature.equals(rowStructureSignature)) {
            rebuildPanel(itemRows, fluidRows, upkeepRows);
            rowStructureSignature = nextSignature;
        }
        boundEditorRoot.setEnabled(isBoundEditorOpen());
        itemInteractionRoot.setEnabled(isItemInteractionOpen());
    }

    @Override
    public boolean canHoverThrough() {
        return !isVisible() || !open;
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
        closeItemInteractions();
    }

    @Override
    public boolean containsMouse(int mouseX, int mouseY) {
        if (!isVisible() || !open) return false;
        int left = getArea().rx;
        int top = getArea().ry;
        return mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= top && mouseY < top + PANEL_Y + PANEL_HEIGHT;
    }

    private boolean isVisible() {
        return visibleSupplier.getAsBoolean();
    }

    private void disablePanelContent() {
        if (!panelRoot.isEnabled()) return;
        panelRoot.setEnabled(false);
        boundEditorRoot.setEnabled(false);
        itemInteractionRoot.setEnabled(false);
        selectedInteractionItem = null;
        rowStructureSignature = "";
        cachedItemAmounts = Map.of();
        cachedFluidAmounts = Map.of();
    }

    @Override
    public boolean blocksInput() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawBackground(context, widgetTheme);
        if (!open) return;
        String frameTitle = resourceMode == ResourceMode.UPKEEP ? "Station Upkeep" : "Station Inventory";
        ModuleConfigModalSupport.drawFrameAt(frameTitle, 0, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        ModuleConfigModalSupport.drawLine(
            resourceMode == ResourceMode.FLUIDS ? "Fluid" : "Item",
            NAME_X + SCROLL_X,
            PANEL_Y + 32,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawLine(
            resourceMode == ResourceMode.UPKEEP ? "Use/min" : "Amount",
            (resourceMode == ResourceMode.UPKEEP ? UPKEEP_USE_X : AMOUNT_X) + SCROLL_X,
            PANEL_Y + 32,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        if (resourceMode == ResourceMode.UPKEEP) {
            ModuleConfigModalSupport
                .drawLine("Stock", UPKEEP_STOCK_X + SCROLL_X, PANEL_Y + 32, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            ModuleConfigModalSupport.drawLine(
                "Reserve",
                UPKEEP_RESERVE_INPUT_X + SCROLL_X,
                PANEL_Y + 32,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
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
        List<StationInventoryPanelModel.FluidRow> fluidRows,
        List<StationInventoryPanelModel.UpkeepItemRow> upkeepRows) {
        panelRoot.setEnabled(true);
        scrollContent.removeAll();
        boolean empty = switch (resourceMode) {
            case ITEMS -> itemRows.isEmpty();
            case FLUIDS -> fluidRows.isEmpty();
            case UPKEEP -> upkeepRows.isEmpty();
        };
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
        } else if (resourceMode == ResourceMode.FLUIDS) {
            for (StationInventoryPanelModel.FluidRow row : fluidRows) {
                scrollContent.child(buildFluidRow(row).pos(0, y));
                y += ROW_HEIGHT + ROW_GAP;
            }
        } else {
            for (StationInventoryPanelModel.UpkeepItemRow row : upkeepRows) {
                scrollContent.child(buildUpkeepRow(row).pos(0, y));
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
            ModuleConfigModalSupport.renderItemIcon(displayStack, x, y + 4);
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
            ModuleConfigModalSupport
                .textureIconButton(
                    () -> af() != null,
                    EnumTextures.ICON_STATION_ITEM_INTERACTIONS.get(),
                    "Interactions",
                    () -> openItemInteractions(wrapper))
                .pos(ITEM_INTERACTION_BUTTON_X, 3)
                .size(ITEM_INTERACTION_BUTTON_SIZE, 18));
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
            amountField(rowKey, wrapper).pos(AMOUNT_INPUT_X, 3)
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

    private ParentWidget<?> buildUpkeepRow(StationInventoryPanelModel.UpkeepItemRow row) {
        ItemStackWrapper wrapper = row.item();
        ItemStack displayStack = wrapper.toStack(1);
        String rowKey = wrapper.toKey();
        ParentWidget<?> rowWidget = new ParentWidget<>().widthRel(1f)
            .height(ROW_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
        rowWidget.child(
            drawable((ctx, x, y, w, h) -> ModuleConfigModalSupport.renderItemIcon(displayStack, x, y + 4)).asWidget()
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
            new TextWidget<>(
                IKey.dynamic(
                    () -> row.perMinute()
                        .toDisplayString() + "/min")).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                            .shadow(true)
                            .pos(UPKEEP_USE_X, 8));
        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> formatAmount(currentAmount(wrapper))))
                .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .shadow(true)
                .pos(UPKEEP_STOCK_X, 8));
        rowWidget.child(
            upkeepReserveField(wrapper, rowKey).pos(UPKEEP_RESERVE_INPUT_X, 3)
                .size(AMOUNT_INPUT_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport
                .checkbox(
                    () -> isUpkeepItem(wrapper),
                    () -> isUpkeepAutoOrderEnabled(wrapper),
                    "Auto-order upkeep",
                    () -> toggleUpkeepAutoOrder(wrapper))
                .pos(UPKEEP_AUTO_ORDER_X, 3)
                .size(UPKEEP_AUTO_ORDER_WIDTH, 18));
        return rowWidget;
    }

    private ParentWidget<?> buildFluidRow(StationInventoryPanelModel.FluidRow row) {
        String fluidName = row.fluidName();
        FluidKey fluidKey = row.fluidKey();
        ParentWidget<?> rowWidget = new ParentWidget<>().widthRel(1f)
            .height(ROW_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
        rowWidget.child(drawable((ctx, x, y, w, h) -> {
            renderFluidIcon(fluidName, x, y + 4);
            renderFluidBoundMarkers(fluidKey, x, y + 4);
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
            new TextWidget<>(IKey.dynamic(() -> formatAmount(currentFluidAmount(fluidKey))))
                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(AMOUNT_X, 8));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> canEditBounds(fluidKey), "Bounds", () -> openBoundEditor(fluidKey))
                .pos(BOUNDS_X, 3)
                .size(BOUNDS_WIDTH, 18));
        return rowWidget;
    }

    private TextFieldWidget amountField(String rowKey, ItemStackWrapper wrapper) {
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

    private TextFieldWidget upkeepReserveField(ItemStackWrapper wrapper, String rowKey) {
        return new TextFieldWidget().setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(true)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (!isUpkeepItem(wrapper)) return;
                BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    upkeepReserveBorderColor(wrapper));
            }))
            .value(
                new StringValue.Dynamic(
                    () -> upkeepReserveInputs.getOrDefault(rowKey, Long.toString(currentUpkeepReserve(wrapper))),
                    text -> updateUpkeepReserveInput(wrapper, rowKey, text)))
            .tooltipDynamic(t -> {
                StationInventoryPanelModel.UpkeepReserveStatus status = upkeepReserveStatus(wrapper);
                if (status.level() != StationInventoryPanelModel.UpkeepReserveLevel.NONE) {
                    t.addLine(status.tooltip());
                }
            })
            .onUpdateListener(TextFieldWidget::markTooltipDirty, true)
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> isUpkeepItem(wrapper));
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
                        boundFieldBorderColor())))
            .tooltipDynamic(t -> { if (!boundsInputValid()) t.addLine("Lower bound cannot exceed upper bound."); })
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
            closeItemInteractions();
        }
    }

    private void setResourceMode(ResourceMode mode) {
        if (resourceMode == mode) return;
        resourceMode = mode;
        closeBoundEditor();
        closeItemInteractions();
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
        return af() != null && wrapper != null;
    }

    private boolean canEditBounds(FluidKey fluid) {
        return af() != null && fluid != null;
    }

    private boolean isBoundEditorOpen() {
        return open && (selectedBoundItem != null || selectedBoundFluid != null);
    }

    private boolean canApplyBound() {
        return isBoundEditorOpen() && boundsInputValid();
    }

    private boolean boundsInputValid() {
        AutomatedFacility af = af();
        InventoryKey resource = selectedBoundItem != null ? selectedBoundItem : selectedBoundFluid;
        if (af == null || resource == null) return false;
        boolean hasLower = af.hasLowerBound(resource);
        boolean hasUpper = af.hasUpperBound(resource);
        return StationInventoryPanelModel.boundsInputValid(
            inputBoundAmount,
            hasLower,
            hasLower ? af.getBound(resource)
                .low() : 0L,
            outputBoundAmount,
            hasUpper,
            hasUpper ? af.getBound(resource)
                .upper() : 0L);
    }

    private int boundFieldBorderColor() {
        return boundsInputValid() ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
            : EnumColors.MAP_COLOR_BTN_DANGER_BORDER.getColor();
    }

    private void openBoundEditor(ItemStackWrapper wrapper) {
        selectedBoundItem = wrapper;
        selectedBoundFluid = null;
        AutomatedFacility af = af();
        inputBoundAmount = af != null && af.hasLowerBound(wrapper) ? Long.toString(
            af.getBound(wrapper)
                .lowOrDefault())
            : "";
        outputBoundAmount = af != null && af.hasUpperBound(wrapper) ? Long.toString(
            af.getBound(wrapper)
                .upperOrDefault())
            : "";
        if (inputBoundField != null) inputBoundField.setText(inputBoundAmount);
        if (outputBoundField != null) outputBoundField.setText(outputBoundAmount);
    }

    private void openBoundEditor(FluidKey fluid) {
        selectedBoundItem = null;
        selectedBoundFluid = fluid;
        AutomatedFacility af = af();
        inputBoundAmount = af != null && af.hasLowerBound(fluid) ? Long.toString(
            af.getBound(fluid)
                .lowOrDefault())
            : "";
        outputBoundAmount = af != null && af.hasUpperBound(fluid) ? Long.toString(
            af.getBound(fluid)
                .upperOrDefault())
            : "";
        if (inputBoundField != null) inputBoundField.setText(inputBoundAmount);
        if (outputBoundField != null) outputBoundField.setText(outputBoundAmount);
    }

    private void closeBoundEditor() {
        selectedBoundItem = null;
        selectedBoundFluid = null;
    }

    private boolean isItemInteractionOpen() {
        return open && selectedInteractionItem != null;
    }

    private void openItemInteractions(ItemStackWrapper wrapper) {
        AutomatedFacility af = af();
        if (af == null) return;
        closeBoundEditor();
        selectedInteractionItem = wrapper;
        itemInteractionRoot.removeAll();
        itemInteractionRoot.child(
            new StationItemInteractionModalWidget(assetId, configController, af, wrapper, this::closeItemInteractions)
                .pos(48, 24)
                .size(StationItemInteractionModalWidget.WIDTH, StationItemInteractionModalWidget.HEIGHT));
        itemInteractionRoot.setEnabled(true);
    }

    private void closeItemInteractions() {
        selectedInteractionItem = null;
        itemInteractionRoot.setEnabled(false);
        itemInteractionRoot.removeAll();
    }

    private void applyBound(boolean low) {
        if (assetId == null || !isBoundEditorOpen()) return;
        String text = low ? inputBoundAmount : outputBoundAmount;
        long amount = parseAmount(text);
        BoundKind kind = selectedBoundItem != null ? (low ? BoundKind.ITEM_LOWER : BoundKind.ITEM_UPPER)
            : (low ? BoundKind.FLUID_LOWER : BoundKind.FLUID_UPPER);
        InventoryKey resource = selectedBoundItem != null ? selectedBoundItem : selectedBoundFluid;
        AutomatedFacility af = af();
        if (af != null) {
            if (!af.trySetBound(resource, amount, low)) return;
        }
        CelestialClient.updateInventoryBound(
            assetId,
            AssetModuleUpdatePacket.ConfigAction.SET_INVENTORY_BOUND,
            kind,
            resource,
            amount);
    }

    private void clearBound(boolean low) {
        if (assetId == null || !isBoundEditorOpen()) return;
        BoundKind kind = selectedBoundItem != null ? (low ? BoundKind.ITEM_LOWER : BoundKind.ITEM_UPPER)
            : (low ? BoundKind.FLUID_LOWER : BoundKind.FLUID_UPPER);
        InventoryKey resource = selectedBoundItem != null ? selectedBoundItem : selectedBoundFluid;
        AutomatedFacility af = af();
        if (af != null) {
            af.clearBound(resource, low);
        }
        CelestialClient.updateInventoryBound(
            assetId,
            AssetModuleUpdatePacket.ConfigAction.CLEAR_INVENTORY_BOUND,
            kind,
            resource,
            0L);
        if (low) {
            inputBoundAmount = "";
            if (inputBoundField != null) inputBoundField.setText("");
        } else {
            outputBoundAmount = "";
            if (outputBoundField != null) outputBoundField.setText("");
        }
        AutomatedFacility af2 = af();
        if (af2 != null && selectedBoundItem != null
            && currentAmount(selectedBoundItem) <= 0L
            && !af2.hasLowerBound(selectedBoundItem)
            && !af2.hasUpperBound(selectedBoundItem)) {
            closeBoundEditor();
        } else if (af2 != null && selectedBoundFluid != null
            && currentFluidAmount(selectedBoundFluid) <= 0L
            && !af2.hasLowerBound(selectedBoundFluid)
            && !af2.hasUpperBound(selectedBoundFluid)) {
                closeBoundEditor();
            }
    }

    private String selectedBoundName() {
        if (selectedBoundItem != null) return selectedBoundItem.toStack(1)
            .getDisplayName();
        return selectedBoundFluid == null ? ""
            : selectedBoundFluid.fluid()
                .getName();
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
        return cachedItemAmounts.getOrDefault(wrapper, 0L);
    }

    private long currentUpkeepReserve(ItemStackWrapper wrapper) {
        AutomatedFacility af = af();
        return af == null ? 0L : af.upkeepReserve(wrapper);
    }

    private long currentFluidAmount(FluidKey fluid) {
        return cachedFluidAmounts.getOrDefault(fluid, 0L);
    }

    private @Nullable AutomatedFacility af() {
        return assetId != null && CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility af ? af : null;
    }

    private @Nullable IDistributedInventory distributed() {
        CelestialAsset asset = assetId != null ? CelestialClient.getByAssetId(assetId) : null;
        return asset instanceof IDistributedInventory d ? d : null;
    }

    private List<Map.Entry<ItemStackWrapper, Long>> rows(IDistributedInventory distributed) {
        return StationInventoryPanelModel.inventoryRows(distributed);
    }

    private List<StationInventoryPanelModel.FluidRow> fluidRows(IDistributedInventory distributed) {
        return StationInventoryPanelModel.fluidRows(distributed);
    }

    private void refreshAmountInputs(List<Map.Entry<ItemStackWrapper, Long>> rows,
        List<StationInventoryPanelModel.UpkeepItemRow> upkeepRows) {
        for (Map.Entry<ItemStackWrapper, Long> row : rows) {
            String rowKey = row.getKey()
                .toKey();
            if (!isAmountMode(rowKey)) {
                amountInputs.put(rowKey, Long.toString(row.getValue()));
            }
        }
        for (StationInventoryPanelModel.UpkeepItemRow row : upkeepRows) {
            upkeepReserveInputs.putIfAbsent(
                row.item()
                    .toKey(),
                Long.toString(row.reserve()));
        }
    }

    private boolean isUpkeepItem(ItemStackWrapper wrapper) {
        AutomatedFacility af = af();
        return af != null && af.upkeepSummary()
            .itemsPerMinute()
            .containsKey(wrapper);
    }

    private boolean isUpkeepAutoOrderEnabled(ItemStackWrapper wrapper) {
        AutomatedFacility af = af();
        return af != null && af.isUpkeepAutoOrderEnabled(wrapper);
    }

    private void toggleUpkeepAutoOrder(ItemStackWrapper wrapper) {
        AutomatedFacility af = af();
        if (af == null) return;
        boolean enabled = !af.isUpkeepAutoOrderEnabled(wrapper);
        af.setUpkeepAutoOrder(wrapper, enabled);
        if (assetId != null) {
            CelestialClient.updateLogisticsConfig(
                assetId,
                wrapper,
                af.logisticsConfig.get(wrapper),
                LogisticsConfigAccessMode.IMPORT_ONLY);
        }
    }

    private void updateUpkeepReserveInput(ItemStackWrapper wrapper, String rowKey, String text) {
        String value = text == null ? "" : text;
        upkeepReserveInputs.put(rowKey, value);
        AutomatedFacility af = af();
        if (af != null) {
            long amount = parseAmount(value);
            af.setUpkeepReserve(wrapper, amount);
            if (assetId != null) {
                CelestialClient.updateLogisticsConfig(
                    assetId,
                    wrapper,
                    af.logisticsConfig.get(wrapper),
                    LogisticsConfigAccessMode.IMPORT_ONLY);
            }
        }
    }

    private StationInventoryPanelModel.UpkeepReserveStatus upkeepReserveStatus(ItemStackWrapper wrapper) {
        AutomatedFacility af = af();
        return af == null
            ? new StationInventoryPanelModel.UpkeepReserveStatus(
                0L,
                0.0D,
                StationInventoryPanelModel.UpkeepReserveLevel.NONE,
                "")
            : StationInventoryPanelModel.upkeepReserveStatus(af, wrapper);
    }

    private int upkeepReserveBorderColor(ItemStackWrapper wrapper) {
        return switch (upkeepReserveStatus(wrapper).level()) {
            case CRITICAL -> BOUND_MARKER_BLOCKING;
            case WARNING -> BOUND_MARKER_WARNING;
            case NONE, NORMAL -> EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor();
        };
    }

    private String rowStructureSignature(List<Map.Entry<ItemStackWrapper, Long>> itemRows,
        List<StationInventoryPanelModel.FluidRow> fluidRows,
        List<StationInventoryPanelModel.UpkeepItemRow> upkeepRows) {
        StringBuilder signature = new StringBuilder((itemRows.size() + fluidRows.size() + upkeepRows.size()) * 24);
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
        signature.append('|');
        for (StationInventoryPanelModel.UpkeepItemRow row : upkeepRows) {
            signature.append(
                row.item()
                    .toKey())
                .append(';');
        }
        return signature.toString();
    }

    private void renderBoundMarkers(ItemStackWrapper wrapper, int x, int y) {
        AutomatedFacility af = af();
        if (af == null) return;
        long amount = currentAmount(wrapper);
        if (af.hasLowerBound(wrapper)) {
            int color = amount < af.getBound(wrapper)
                .lowOrDefault() ? BOUND_MARKER_BLOCKING : BOUND_MARKER_WARNING;
            Gui.drawRect(x, y, x + BOUND_MARKER_SIZE, y + BOUND_MARKER_SIZE, color);
        }
        if (af.hasUpperBound(wrapper)) {
            int color = amount >= af.getBound(wrapper)
                .upperOrDefault() ? BOUND_MARKER_BLOCKING : BOUND_MARKER_WARNING;
            Gui.drawRect(x + 16 - BOUND_MARKER_SIZE, y, x + 16, y + BOUND_MARKER_SIZE, color);
        }
    }

    private void renderFluidIcon(String fluidName, int x, int y) {
        Gui.drawRect(x, y, x + 16, y + 16, EnumColors.MAP_COLOR_SIDEBAR_GHOST_SLOT_BG.getColor());
        Gui.drawRect(x + 2, y + 2, x + 14, y + 14, EnumColors.MAP_COLOR_CONNECTOR_TANK.getColor());
    }

    private void renderFluidBoundMarkers(FluidKey fluid, int x, int y) {
        AutomatedFacility af = af();
        if (af == null) return;
        long amount = currentFluidAmount(fluid);
        if (af.hasLowerBound(fluid)) {
            int color = amount < af.getBound(fluid)
                .lowOrDefault() ? BOUND_MARKER_BLOCKING : BOUND_MARKER_WARNING;
            Gui.drawRect(x, y, x + BOUND_MARKER_SIZE, y + BOUND_MARKER_SIZE, color);
        }
        if (af.hasUpperBound(fluid)) {
            int color = amount >= af.getBound(fluid)
                .upperOrDefault() ? BOUND_MARKER_BLOCKING : BOUND_MARKER_WARNING;
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
        FLUIDS,
        UPKEEP
    }
}
