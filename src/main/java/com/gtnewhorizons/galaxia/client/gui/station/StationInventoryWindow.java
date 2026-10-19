package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.FluidDrawable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaWindow;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;

/**
 * Window with everything a station holds: its items, fluids and upkeep, with voiding, inventory bounds and the list of
 * what uses an item
 */
final class StationInventoryWindow {

    static final String WINDOW_ID = "station.inventory";
    static final String BOUNDS_ID = "station.inventory.bounds";
    static final String ITEM_ID = "station.inventory.item";

    private static final int NAME_WIDTH = 100;
    private static final int NUMBER_WIDTH = 36;
    private static final int FIELD_WIDTH = 40;
    private static final int LIST_HEIGHT = 9 * (GalaxiaUi.ICON + GalaxiaUi.GAP);
    private static final int INTERACTIONS_HEIGHT = 8 * (GalaxiaUi.ICON + GalaxiaUi.GAP);
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final @Nullable CelestialAsset.ID assetId;
    private final @Nullable ModuleConfigModalController configController;
    private final BooleanSupplier visibleSupplier;
    private final Map<ItemStackWrapper, Boolean> amountModes = new LinkedHashMap<>();
    private final Map<ItemStackWrapper, String> amountInputs = new LinkedHashMap<>();
    private final Map<ItemStackWrapper, String> upkeepReserveInputs = new LinkedHashMap<>();
    private ResourceMode resourceMode = ResourceMode.ITEMS;
    private @Nullable GalaxiaWindow window;
    private @Nullable ButtonWidget<?> toggle;
    private @Nullable RowStructure rowStructure;
    private Map<ItemStackWrapper, Long> cachedItemAmounts = Map.of();
    private Map<FluidKey, Long> cachedFluidAmounts = Map.of();
    private List<StationInventoryPanelModel.InventoryItemRow> itemRows = List.of();
    private List<StationInventoryPanelModel.FluidRow> fluidRows = List.of();
    private List<StationInventoryPanelModel.UpkeepItemRow> upkeepRows = List.of();
    private @Nullable InventoryKey boundResource;
    private String inputBoundAmount = "";
    private String outputBoundAmount = "";

    StationInventoryWindow(@Nullable CelestialAsset.ID assetId, @Nullable ModuleConfigModalController configController,
        BooleanSupplier visibleSupplier) {
        this.assetId = assetId;
        this.configController = configController;
        this.visibleSupplier = visibleSupplier;
    }

    /** The button on the station window that opens and closes the inventory */
    ButtonWidget<?> toggleButton() {
        String label = tr("galaxia.gui.station.inventory.button");
        toggle = GalaxiaUi.toggle(() -> label, GalaxiaUi.width(label) + 10, () -> window != null, this::toggleOpen);
        toggle.name("station.inventory.toggle")
            .setEnabledIf(w -> visibleSupplier.getAsBoolean() && inventoryOwner() != null);
        return toggle;
    }

    private void toggleOpen() {
        if (window != null) {
            window.close();
            return;
        }
        WindowLayer layer = toggle == null ? null : WindowLayer.of(toggle);
        if (layer == null || assetId == null) return;
        refresh();
        window = layer.open(
            WindowSpec
                .of(
                    WINDOW_ID,
                    com.cleanroommc.modularui.api.drawable.IKey.dynamic(
                        () -> tr(
                            resourceMode == ResourceMode.UPKEEP ? "galaxia.gui.station.inventory.upkeep_title"
                                : "galaxia.gui.station.inventory.title")))
                .owner(StationManagementScreen.windowId(assetId))
                .anchor((layerWidth, layerHeight, width, height) -> {
                    // Right of the panel the button sits in, level with the button
                    IWidget beside = toggle.getParent() == null ? toggle : toggle.getParent();
                    int[] panel = GalaxiaUi.positionIn(beside, layer);
                    int[] at = GalaxiaUi.positionIn(toggle, layer);
                    return new int[] { Math
                        .max(0, Math.min(panel[0] + beside.getArea().width + GalaxiaUi.GAP * 2, layerWidth - width)),
                        Math.max(0, Math.min(at[1], layerHeight - height)) };
                })
                .onUpdate(this::update)
                .onClose(() -> window = null)
                .body(this::fill));
    }

    private void update() {
        if (!visibleSupplier.getAsBoolean() || inventoryOwner() == null) {
            if (window != null) window.close();
            return;
        }
        refresh();
        RowStructure structure = rowStructure();
        if (structure.equals(rowStructure)) return;
        rowStructure = structure;
        if (window != null) window.rebuildLater();
    }

    /** Reads the amounts once a tick, every row of the window reads them from here */
    private void refresh() {
        CelestialAsset owner = inventoryOwner();
        if (owner == null) return;
        AutomatedFacility facility = owner instanceof AutomatedFacility af ? af : null;
        if (facility != null) {
            cachedItemAmounts = facility.itemSnapshot();
            cachedFluidAmounts = facility.fluidAmounts();
        } else {
            IDistributedInventory physicalInventory = (IDistributedInventory) owner;
            cachedItemAmounts = physicalInventory.aggregatedItems();
            cachedFluidAmounts = physicalInventory.aggregatedFluids();
        }
        itemRows = StationInventoryPanelModel.inventoryRows(cachedItemAmounts, facility);
        fluidRows = StationInventoryPanelModel.fluidRows(cachedFluidAmounts);
        upkeepRows = facility == null ? List.of() : StationInventoryPanelModel.upkeepItemRows(facility);
        for (StationInventoryPanelModel.InventoryItemRow row : itemRows) {
            if (!isAmountMode(row.item())) amountInputs.put(row.item(), Long.toString(row.amount()));
        }
        for (StationInventoryPanelModel.UpkeepItemRow row : upkeepRows) {
            upkeepReserveInputs.putIfAbsent(row.item(), Long.toString(row.reserve()));
        }
    }

    private void fill(GalaxiaUi.Column column) {
        rowStructure = rowStructure();
        boolean facility = af() != null;
        column.row(
            modeTab(ResourceMode.ITEMS, "galaxia.gui.station.inventory.tab.items", true),
            modeTab(ResourceMode.FLUIDS, "galaxia.gui.station.inventory.tab.fluids", facility),
            modeTab(ResourceMode.UPKEEP, "galaxia.gui.station.inventory.tab.upkeep", facility));
        boolean empty = switch (resourceMode) {
            case ITEMS -> itemRows.isEmpty();
            case FLUIDS -> fluidRows.isEmpty();
            case UPKEEP -> upkeepRows.isEmpty();
        };
        if (empty) {
            column.dim(
                tr(
                    resourceMode == ResourceMode.UPKEEP ? "galaxia.gui.station.inventory.no_upkeep"
                        : "galaxia.gui.station.inventory.empty"));
            return;
        }
        column.add(header());
        ListWidget<IWidget, ?> list = GalaxiaUi.scroll(LIST_HEIGHT);
        Flow rows = GalaxiaUi.column();
        switch (resourceMode) {
            case ITEMS -> {
                for (int index = 0; index < itemRows.size(); index++) {
                    ItemStackWrapper item = itemRows.get(index)
                        .item();
                    amountModes.putIfAbsent(item, false);
                    amountInputs.putIfAbsent(
                        item,
                        Long.toString(
                            itemRows.get(index)
                                .amount()));
                    rows.child(itemRow(index, item));
                }
            }
            case FLUIDS -> {
                for (StationInventoryPanelModel.FluidRow row : fluidRows) rows.child(fluidRow(row));
            }
            case UPKEEP -> {
                for (StationInventoryPanelModel.UpkeepItemRow row : upkeepRows) rows.child(upkeepRow(row));
            }
        }
        list.child(rows);
        column.add(list);
    }

    private ButtonWidget<?> modeTab(ResourceMode mode, String key, boolean available) {
        String label = tr(key);
        return GalaxiaUi
            .choice(() -> label, GalaxiaUi.width(label) + 10, () -> resourceMode == mode, () -> available, () -> {
                if (resourceMode == mode) return;
                resourceMode = mode;
                closeChildWindows();
                rowStructure = null;
            })
            .name("station.inventory.tab." + mode.name());
    }

    private Flow header() {
        Flow header = GalaxiaUi.row()
            .child(
                headerText(
                    resourceMode == ResourceMode.FLUIDS ? "galaxia.gui.station.inventory.column.fluid"
                        : "galaxia.gui.station.inventory.column.item",
                    GalaxiaUi.ICON + GalaxiaUi.GAP + NAME_WIDTH));
        switch (resourceMode) {
            case ITEMS -> header.child(new com.cleanroommc.modularui.widget.Widget<>().size(GalaxiaUi.ICON, 1))
                .child(headerText("galaxia.gui.station.inventory.column.upkeep", NUMBER_WIDTH))
                .child(headerText("galaxia.gui.station.inventory.column.amount", NUMBER_WIDTH));
            case FLUIDS -> header.child(headerText("galaxia.gui.station.inventory.column.amount", NUMBER_WIDTH));
            case UPKEEP -> header.child(headerText("galaxia.gui.station.inventory.column.use", NUMBER_WIDTH + 10))
                .child(headerText("galaxia.gui.station.inventory.column.stock", NUMBER_WIDTH))
                .child(headerText("galaxia.gui.station.inventory.column.reserve", FIELD_WIDTH));
        }
        return header;
    }

    private static TextWidget<?> headerText(String key, int width) {
        return GalaxiaUi.dim(tr(key))
            .width(width)
            .alignment(Alignment.Center);
    }

    // region Rows

    private Flow itemRow(int index, ItemStackWrapper item) {
        ItemStack stack = item.toStack(1);
        return GalaxiaUi.row()
            .child(GalaxiaUi.item(stack))
            .child(
                GalaxiaUi.clipped(stack.getDisplayName(), NAME_WIDTH, GalaxiaGuiStyle.TEXT)
                    .width(NAME_WIDTH))
            .child(
                GalaxiaUi
                    .iconButton(
                        EnumTextures.ICON_STATION_ITEM_INTERACTIONS.getImage(),
                        () -> tr("galaxia.gui.station.inventory.interactions"),
                        () -> af() != null,
                        () -> openItemInteractions(item))
                    .name("station.inventory.interactions." + index))
            .child(
                GalaxiaUi.text(() -> formatUpkeepReserve(currentUpkeepReserve(item)))
                    .width(NUMBER_WIDTH)
                    .alignment(Alignment.Center)
                    .tooltipDynamic(t -> {
                        StationInventoryPanelModel.UpkeepReserveStatus status = upkeepReserveStatus(item);
                        if (status.level() != StationInventoryPanelModel.UpkeepReserveLevel.NONE) {
                            t.addLine(status.tooltip());
                        }
                    }))
            .child(
                GalaxiaUi.text(() -> formatAmount(currentAmount(item)), () -> boundColor(item, currentAmount(item)))
                    .width(NUMBER_WIDTH)
                    .alignment(Alignment.Center)
                    .tooltipDynamic(t -> boundTooltip(t, item)))
            .child(
                GalaxiaUi
                    .iconButton(
                        EnumTextures.ICON_STATION_INVENTORY_BOUNDS.getImage(),
                        () -> tr("galaxia.gui.station.inventory.bounds"),
                        () -> af() != null,
                        () -> openBoundEditor(item))
                    .name("station.inventory.bounds." + index))
            .child(amountField(item))
            .child(
                GalaxiaUi.iconButton(
                    new DynamicDrawable(
                        () -> (isAmountMode(item) ? EnumTextures.ICON_STATION_INVENTORY_AMOUNT
                            : EnumTextures.ICON_STATION_INVENTORY_ALL).getImage()),
                    () -> tr(
                        isAmountMode(item) ? "galaxia.gui.station.inventory.mode.amount"
                            : "galaxia.gui.station.inventory.mode.all"),
                    () -> true,
                    () -> amountModes.put(item, !isAmountMode(item))))
            .child(
                GalaxiaUi.iconButton(
                    EnumTextures.ICON_STATION_INVENTORY_VOID.getImage(),
                    () -> tr("galaxia.gui.station.inventory.void"),
                    () -> currentAmount(item) > 0L,
                    () -> voidRow(item)));
    }

    private Flow fluidRow(StationInventoryPanelModel.FluidRow row) {
        FluidKey fluid = row.fluidKey();
        return GalaxiaUi.row()
            .child(
                new FluidDrawable(new FluidStack(fluid.fluid(), 1000)).asWidget()
                    .size(GalaxiaUi.ICON)
                    .tooltip(t -> t.addLine(row.fluidName())))
            .child(
                GalaxiaUi.clipped(row.fluidName(), NAME_WIDTH, GalaxiaGuiStyle.TEXT)
                    .width(NAME_WIDTH))
            .child(
                GalaxiaUi
                    .text(
                        () -> formatAmount(currentFluidAmount(fluid)),
                        () -> boundColor(fluid, currentFluidAmount(fluid)))
                    .width(NUMBER_WIDTH)
                    .alignment(Alignment.Center)
                    .tooltipDynamic(t -> boundTooltip(t, fluid)))
            .child(
                GalaxiaUi.iconButton(
                    EnumTextures.ICON_STATION_INVENTORY_BOUNDS.getImage(),
                    () -> tr("galaxia.gui.station.inventory.fluid_bounds"),
                    () -> af() != null,
                    () -> openBoundEditor(fluid)));
    }

    private Flow upkeepRow(StationInventoryPanelModel.UpkeepItemRow row) {
        ItemStackWrapper item = row.item();
        ItemStack stack = item.toStack(1);
        return GalaxiaUi.row()
            .child(GalaxiaUi.item(stack))
            .child(
                GalaxiaUi.clipped(stack.getDisplayName(), NAME_WIDTH, GalaxiaGuiStyle.TEXT)
                    .width(NAME_WIDTH))
            .child(
                GalaxiaUi
                    .colored(
                        () -> StatCollector.translateToLocalFormatted(
                            "galaxia.gui.station.inventory.per_minute",
                            row.perMinute()
                                .toDisplayString()),
                        GalaxiaGuiStyle.TEXT_TITLE)
                    .width(NUMBER_WIDTH + 10)
                    .alignment(Alignment.Center))
            .child(
                GalaxiaUi.text(() -> formatAmount(currentAmount(item)))
                    .width(NUMBER_WIDTH)
                    .alignment(Alignment.Center))
            .child(upkeepReserveField(item))
            .child(
                GalaxiaUi
                    .toggle(
                        () -> tr("galaxia.gui.station.inventory.auto_order"),
                        GalaxiaUi.width(tr("galaxia.gui.station.inventory.auto_order")) + 10,
                        () -> isUpkeepAutoOrderEnabled(item),
                        () -> toggleUpkeepAutoOrder(item))
                    .tooltip(t -> t.addLine(tr("galaxia.gui.station.inventory.auto_order.tooltip"))));
    }

    private TextFieldWidget amountField(ItemStackWrapper item) {
        TextFieldWidget field = numberField();
        field.autoUpdateOnChange(false)
            .value(
                new StringValue.Dynamic(
                    () -> amountInputs.getOrDefault(item, "0"),
                    text -> amountInputs.put(item, text == null ? "" : text)))
            .tooltip(t -> t.addLine(tr("galaxia.gui.station.inventory.amount_field")))
            .setEnabledIf(w -> isAmountMode(item));
        return field;
    }

    private TextFieldWidget upkeepReserveField(ItemStackWrapper item) {
        TextFieldWidget field = numberField();
        field.autoUpdateOnChange(true)
            .value(
                new StringValue.Dynamic(
                    () -> upkeepReserveInputs.getOrDefault(item, Long.toString(currentUpkeepReserve(item))),
                    text -> updateUpkeepReserveInput(item, text)))
            .tooltipDynamic(t -> {
                StationInventoryPanelModel.UpkeepReserveStatus status = upkeepReserveStatus(item);
                if (status.level() != StationInventoryPanelModel.UpkeepReserveLevel.NONE) t.addLine(status.tooltip());
            })
            .onUpdateListener(TextFieldWidget::markTooltipDirty, true)
            .setEnabledIf(w -> isUpkeepItem(item));
        return field;
    }

    private static TextFieldWidget numberField() {
        TextFieldWidget field = GalaxiaUi.textField(null, FIELD_WIDTH);
        field.setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .setFocusOnGuiOpen(false);
        return field;
    }

    // endregion

    // region Voiding and upkeep

    private boolean isAmountMode(ItemStackWrapper item) {
        return amountModes.getOrDefault(item, false);
    }

    private void voidRow(ItemStackWrapper item) {
        if (assetId == null) return;
        long amount = StationInventoryPanelModel
            .voidAmount(isAmountMode(item), currentAmount(item), amountInputs.getOrDefault(item, ""));
        if (amount <= 0L) return;
        if (amount >= currentAmount(item)) {
            CelestialClient.removeInventory(assetId, item);
        } else {
            CelestialClient.removeInventoryAmount(assetId, item, amount);
        }
    }

    private boolean isUpkeepItem(ItemStackWrapper item) {
        AutomatedFacility af = af();
        return af != null && af.upkeepSummary()
            .itemsPerMinute()
            .containsKey(item);
    }

    private boolean isUpkeepAutoOrderEnabled(ItemStackWrapper item) {
        AutomatedFacility af = af();
        return af != null && af.isUpkeepAutoOrderEnabled(item);
    }

    private void toggleUpkeepAutoOrder(ItemStackWrapper item) {
        AutomatedFacility af = af();
        if (af == null || !isUpkeepItem(item)) return;
        boolean enabled = !af.isUpkeepAutoOrderEnabled(item);
        LogisticsResourceConfig current = af.logisticsConfig.get(item);
        LogisticsResourceConfig updated;
        if (enabled) {
            int reserve = (int) Math.min(Integer.MAX_VALUE, af.upkeepReserve(item));
            int orderSize = current == LogisticsResourceConfig.DEFAULT ? 64 : current.orderSize();
            updated = new LogisticsResourceConfig(reserve, orderSize, true, false);
        } else {
            updated = current.withImportEnabled(false)
                .withSupplyEnabled(false);
        }
        CelestialClient.updateLogisticsConfig(assetId, item, updated, LogisticsConfigAccessMode.IMPORT_ONLY);
    }

    private void updateUpkeepReserveInput(ItemStackWrapper item, String text) {
        String value = text == null ? "" : text;
        upkeepReserveInputs.put(item, value);
        AutomatedFacility af = af();
        if (af != null) {
            CelestialClient.updateLogisticsConfig(
                assetId,
                item,
                af.logisticsConfig.get(item)
                    .withMinReserve((int) Math.min(Integer.MAX_VALUE, parseAmount(value))),
                LogisticsConfigAccessMode.IMPORT_ONLY);
        }
    }

    private StationInventoryPanelModel.UpkeepReserveStatus upkeepReserveStatus(ItemStackWrapper item) {
        AutomatedFacility af = af();
        return af == null
            ? new StationInventoryPanelModel.UpkeepReserveStatus(
                0L,
                0.0D,
                StationInventoryPanelModel.UpkeepReserveLevel.NONE,
                "")
            : StationInventoryPanelModel.upkeepReserveStatus(af, item);
    }

    // endregion

    // region Bounds

    /** Amount coloured by its bounds: a bound that stops the station shows red, one that is set shows yellow */
    private int boundColor(InventoryKey resource, long amount) {
        AutomatedFacility af = af();
        if (af == null) return GalaxiaGuiStyle.TEXT_TITLE;
        boolean low = af.hasLowerBound(resource);
        boolean high = af.hasUpperBound(resource);
        if (low && amount < af.getBound(resource)
            .lowOrDefault()) return GalaxiaGuiStyle.BAD;
        if (high && amount >= af.getBound(resource)
            .upperOrDefault()) return GalaxiaGuiStyle.BAD;
        return low || high ? GalaxiaGuiStyle.WARN : GalaxiaGuiStyle.TEXT_TITLE;
    }

    private void boundTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip, InventoryKey resource) {
        AutomatedFacility af = af();
        if (af == null) return;
        if (af.hasLowerBound(resource)) {
            tooltip.addLine(
                StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.inventory.lower_bound",
                    af.getBound(resource)
                        .lowOrDefault()));
        }
        if (af.hasUpperBound(resource)) {
            tooltip.addLine(
                StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.inventory.upper_bound",
                    af.getBound(resource)
                        .upperOrDefault()));
        }
    }

    private void openBoundEditor(InventoryKey resource) {
        AutomatedFacility af = af();
        WindowLayer layer = toggle == null ? null : WindowLayer.of(toggle);
        if (af == null || layer == null) return;
        boundResource = resource;
        inputBoundAmount = af.hasLowerBound(resource) ? Long.toString(
            af.getBound(resource)
                .lowOrDefault())
            : "";
        outputBoundAmount = af.hasUpperBound(resource) ? Long.toString(
            af.getBound(resource)
                .upperOrDefault())
            : "";
        layer.close(BOUNDS_ID);
        layer.open(
            WindowSpec.of(BOUNDS_ID, "galaxia.gui.station.inventory.bounds_title")
                .owner(WINDOW_ID)
                .modal()
                .centered()
                .onClose(() -> boundResource = null)
                .body(this::fillBoundEditor));
    }

    private void fillBoundEditor(GalaxiaUi.Column column) {
        column.add(GalaxiaUi.clipped(boundName(), 220, GalaxiaGuiStyle.TEXT));
        column.add(boundRow(true));
        column.add(boundRow(false));
    }

    private Flow boundRow(boolean low) {
        TextFieldWidget field = numberField();
        field.autoUpdateOnChange(false)
            .value(new StringValue.Dynamic(() -> low ? inputBoundAmount : outputBoundAmount, text -> {
                if (low) inputBoundAmount = text == null ? "" : text;
                else outputBoundAmount = text == null ? "" : text;
            }))
            .tooltipDynamic(
                t -> { if (!boundsInputValid()) t.addLine(tr("galaxia.gui.station.inventory.bounds_invalid")); })
            .onUpdateListener(TextFieldWidget::markTooltipDirty, true);
        return GalaxiaUi.row()
            .child(
                GalaxiaUi
                    .dim(
                        tr(
                            low ? "galaxia.gui.station.inventory.input_lower"
                                : "galaxia.gui.station.inventory.output_upper"))
                    .width(70))
            .child(field)
            .child(
                GalaxiaUi.button(
                    tr("galaxia.gui.station.recipe.details.set"),
                    this::boundsInputValid,
                    () -> applyBound(low)))
            .child(GalaxiaUi.button(tr("galaxia.gui.station.recipe.details.clear"), () -> clearBound(low)));
    }

    private boolean boundsInputValid() {
        AutomatedFacility af = af();
        InventoryKey resource = boundResource;
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

    private BoundKind boundKind(boolean low) {
        return boundResource instanceof ItemStackWrapper ? (low ? BoundKind.ITEM_LOWER : BoundKind.ITEM_UPPER)
            : (low ? BoundKind.FLUID_LOWER : BoundKind.FLUID_UPPER);
    }

    private void applyBound(boolean low) {
        if (assetId == null || boundResource == null) return;
        CelestialClient.setInventoryBound(
            assetId,
            boundKind(low),
            boundResource,
            parseAmount(low ? inputBoundAmount : outputBoundAmount));
    }

    private void clearBound(boolean low) {
        if (assetId == null || boundResource == null) return;
        CelestialClient.clearInventoryBound(assetId, boundKind(low), boundResource);
        if (low) inputBoundAmount = "";
        else outputBoundAmount = "";
    }

    private String boundName() {
        if (boundResource instanceof ItemStackWrapper item) return item.toStack(1)
            .getDisplayName();
        return boundResource instanceof FluidKey fluid ? fluid.fluid()
            .getName() : "";
    }

    // endregion

    // region Item interactions

    private void openItemInteractions(ItemStackWrapper item) {
        AutomatedFacility af = af();
        WindowLayer layer = toggle == null ? null : WindowLayer.of(toggle);
        if (af == null || layer == null) return;
        ItemStack stack = item.toStack(1);
        List<StationItemInteractionModel.Entry> entries = StationItemInteractionModel.forItem(af, item);
        layer.close(ITEM_ID);
        layer.open(
            WindowSpec.of(ITEM_ID, com.cleanroommc.modularui.api.drawable.IKey.str(stack.getDisplayName()))
                .owner(WINDOW_ID)
                .centered()
                .body(column -> fillItemInteractions(column, entries)));
    }

    private void fillItemInteractions(GalaxiaUi.Column column, List<StationItemInteractionModel.Entry> entries) {
        if (entries.isEmpty()) {
            column.dim(tr("galaxia.gui.station.interactions.none"));
            return;
        }
        ListWidget<IWidget, ?> list = GalaxiaUi.scroll(INTERACTIONS_HEIGHT);
        Flow lines = GalaxiaUi.column();
        StationItemInteractionModel.Section section = null;
        for (StationItemInteractionModel.Entry entry : entries) {
            if (entry.section() != section) {
                section = entry.section();
                lines.child(GalaxiaUi.section(sectionTitle(section)));
            }
            Flow line = GalaxiaUi.row()
                .child(GalaxiaUi.item(StationItemInteractionText.iconFor(entry)))
                .child(
                    GalaxiaUi.clipped(StationItemInteractionText.textFor(entry), 220, GalaxiaGuiStyle.TEXT)
                        .width(220));
            if (canOpen(entry)) {
                line.child(GalaxiaUi.button(tr("galaxia.gui.station.interactions.open"), () -> openEntry(entry)));
            }
            lines.child(line);
        }
        list.child(lines);
        column.add(list);
    }

    private static String sectionTitle(StationItemInteractionModel.Section section) {
        return tr(switch (section) {
            case LOGISTICS -> "galaxia.gui.station.interactions.logistics";
            case MACHINES -> "galaxia.gui.station.interactions.machines";
            case UPKEEP -> "galaxia.gui.station.interactions.upkeep";
        });
    }

    private boolean canOpen(StationItemInteractionModel.Entry entry) {
        return configController != null
            && (entry.role() == StationItemInteractionModel.Role.CORE_IMPORT || entry.targetModuleId() != null);
    }

    private void openEntry(StationItemInteractionModel.Entry entry) {
        if (configController == null) return;
        closeChildWindows();
        if (entry.role() == StationItemInteractionModel.Role.CORE_IMPORT) {
            configController.openCoreLogistics();
            return;
        }
        com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance module = StationUiData
            .module(assetId, entry.targetModuleId());
        if (module == null) return;
        if (module.component() instanceof com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer) {
            configController.openHammer(module.id);
        } else if (module.recipe() != null) {
            configController.openRecipeConfig(module.id);
        } else if (module.component() instanceof com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner) {
            configController.openMinerBlacklist(module.id);
        } else if (module
            .component() instanceof com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator) {
                configController.openDebugDataGenerator(module.id);
            } else {
                configController.openUpgrade(module.id);
            }
    }

    // endregion

    private void closeChildWindows() {
        WindowLayer layer = toggle == null ? null : WindowLayer.of(toggle);
        if (layer == null) return;
        layer.close(BOUNDS_ID);
        layer.close(ITEM_ID);
    }

    private RowStructure rowStructure() {
        List<ItemStackWrapper> items = new ArrayList<>(itemRows.size());
        for (StationInventoryPanelModel.InventoryItemRow row : itemRows) items.add(row.item());
        List<String> fluids = new ArrayList<>(fluidRows.size());
        for (StationInventoryPanelModel.FluidRow row : fluidRows) fluids.add(row.fluidName());
        List<ItemStackWrapper> upkeepItems = new ArrayList<>(upkeepRows.size());
        for (StationInventoryPanelModel.UpkeepItemRow row : upkeepRows) upkeepItems.add(row.item());
        return new RowStructure(resourceMode, List.copyOf(items), List.copyOf(fluids), List.copyOf(upkeepItems));
    }

    private long currentAmount(ItemStackWrapper item) {
        return cachedItemAmounts.getOrDefault(item, 0L);
    }

    private long currentFluidAmount(FluidKey fluid) {
        return cachedFluidAmounts.getOrDefault(fluid, 0L);
    }

    private long currentUpkeepReserve(ItemStackWrapper item) {
        AutomatedFacility af = af();
        return af == null ? 0L : af.upkeepReserve(item);
    }

    private @Nullable AutomatedFacility af() {
        return StationUiData.facility(assetId);
    }

    private @Nullable CelestialAsset inventoryOwner() {
        CelestialAsset asset = StationUiData.asset(assetId);
        return asset instanceof AutomatedFacility || asset instanceof IDistributedInventory ? asset : null;
    }

    private static long parseAmount(String text) {
        if (text == null || text.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String formatUpkeepReserve(long reserve) {
        return reserve <= 0L ? "" : formatAmount(reserve);
    }

    private static String formatAmount(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private enum ResourceMode {
        ITEMS,
        FLUIDS,
        UPKEEP
    }

    private record RowStructure(ResourceMode resourceMode, List<ItemStackWrapper> items, List<String> fluids,
        List<ItemStackWrapper> upkeepItems) {}
}
