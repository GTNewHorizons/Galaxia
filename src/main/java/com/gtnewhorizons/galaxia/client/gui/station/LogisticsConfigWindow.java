package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

/** Items a station keeps track of: how many it keeps, in what packets it sends them and whether it trades them */
final class LogisticsConfigWindow implements ModuleConfigWindow {

    private static final int NAME_WIDTH = 140;
    private static final int STOCK_WIDTH = 44;
    private static final int STEP_WIDTH = 14;
    private static final int VALUE_WIDTH = 42;
    private static final int AMOUNT_WIDTH = STEP_WIDTH * 2 + VALUE_WIDTH + GalaxiaUi.GAP * 2;
    private static final int TOGGLE_WIDTH = 36;
    private static final int LIST_HEIGHT = 8 * (GalaxiaUi.ICON + GalaxiaUi.GAP);
    private static final int MAX_LOGISTICS_AMOUNT = 999_999;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private String rowSignature = "";

    LogisticsConfigWindow(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
    }

    @Override
    public String title() {
        ModuleInstance.ID modId = controller.moduleId();
        ModuleInstance module = modId != null ? StationUiData.module(assetId, modId) : null;
        if (module != null) return StationUiData.moduleTitle(module, "galaxia.gui.station.config.purpose.logistics");
        return tr(
            logisticsAccessMode() == LogisticsConfigAccessMode.IMPORT_ONLY ? "galaxia.gui.station.logistics.core_title"
                : "galaxia.gui.station.logistics.title");
    }

    @Override
    public void update() {
        consumePickedItem();
        CelestialAsset asset = asset();
        String signature = asset == null ? "" : rowSignature(rows(asset));
        if (signature.equals(rowSignature)) return;
        rowSignature = signature;
        controller.rebuild();
    }

    @Override
    public void build(GalaxiaUi.Column column) {
        CelestialAsset asset = asset();
        if (asset == null) {
            column.dim(tr("galaxia.gui.station.logistics.none"));
            return;
        }
        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = rows(asset);
        rowSignature = rowSignature(rows);
        column.row(
            GalaxiaUi.button(tr("galaxia.gui.station.logistics.back"), this::back)
                .setEnabledIf(w -> selectedHammer() != null),
            GalaxiaUi.button(tr("galaxia.gui.station.logistics.add"), this::openItemPicker));
        if (rows.isEmpty()) {
            column.dim(tr("galaxia.gui.station.logistics.empty"));
            return;
        }
        boolean supply = logisticsAccessMode().canEditSupply();
        Flow header = GalaxiaUi.row()
            .child(header("item", GalaxiaUi.ICON + GalaxiaUi.GAP + NAME_WIDTH))
            .child(header("inventory", STOCK_WIDTH))
            .child(header("reserve", AMOUNT_WIDTH))
            .child(header("packet", AMOUNT_WIDTH))
            .child(header("import", TOGGLE_WIDTH));
        if (supply) header.child(header("export", TOGGLE_WIDTH));
        column.add(header);

        ListWidget<IWidget, ?> list = GalaxiaUi.scroll(LIST_HEIGHT);
        Flow lines = GalaxiaUi.column();
        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row : rows)
            lines.child(row(asset, row.getKey(), supply));
        list.child(lines);
        column.add(list);
    }

    private TextWidget<?> header(String column, int width) {
        return GalaxiaUi.dim(tr("galaxia.gui.station.logistics.column." + column))
            .width(width)
            .alignment(Alignment.Center)
            .tooltip(t -> t.addLine(tr("galaxia.gui.station.logistics.column." + column + ".tooltip")));
    }

    private Flow row(CelestialAsset asset, ItemStackWrapper item, boolean supply) {
        ItemStack stack = item.toStack(1);
        Flow row = GalaxiaUi.row()
            .child(GalaxiaUi.item(stack))
            .child(
                GalaxiaUi.clipped(stack.getDisplayName(), NAME_WIDTH, GalaxiaGuiStyle.TEXT)
                    .width(NAME_WIDTH))
            .child(
                GalaxiaUi.colored(() -> formatAmount(stockAmount(asset(), item)), GalaxiaGuiStyle.TEXT_TITLE)
                    .width(STOCK_WIDTH)
                    .alignment(Alignment.Center))
            .child(amountControl(item, true))
            .child(amountControl(item, false))
            .child(
                GalaxiaUi.toggle(
                    () -> onOff(config(item) != null && config(item).isImportEnabled()),
                    TOGGLE_WIDTH,
                    () -> config(item) != null && config(item).isImportEnabled(),
                    () -> toggleImport(item)));
        if (supply) {
            row.child(
                GalaxiaUi.toggle(
                    () -> onOff(config(item) != null && config(item).isSupplyEnabled()),
                    TOGGLE_WIDTH,
                    () -> config(item) != null && config(item).isSupplyEnabled(),
                    () -> toggleExport(item)));
        }
        return row.child(
            GalaxiaUi.button(() -> "X", STEP_WIDTH, () -> config(item) != null, () -> removeEntry(item))
                .tooltip(t -> t.addLine(tr("galaxia.gui.station.logistics.remove"))));
    }

    private Flow amountControl(ItemStackWrapper item, boolean reserve) {
        return GalaxiaUi.row()
            .child(
                GalaxiaUi.button(() -> "-", STEP_WIDTH, () -> canEdit(item, reserve), () -> shift(item, reserve, -1)))
            .child(amountField(item, reserve))
            .child(
                GalaxiaUi.button(() -> "+", STEP_WIDTH, () -> canEdit(item, reserve), () -> shift(item, reserve, 1)));
    }

    private TextFieldWidget amountField(ItemStackWrapper item, boolean reserve) {
        TextFieldWidget field = GalaxiaUi.textField(null, VALUE_WIDTH);
        field.setMaxLength(6)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(reserve ? 0 : 1)
            .setNumbers(reserve ? 0 : 1, MAX_LOGISTICS_AMOUNT)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .value(new StringValue.Dynamic(() -> amountText(item, reserve), text -> setAmount(item, reserve, text)))
            .setFocusOnGuiOpen(false);
        return field;
    }

    private static long stockAmount(CelestialAsset asset, ItemStackWrapper wrapper) {
        if (asset instanceof AutomatedFacility af) {
            return af.itemAmount(wrapper);
        }
        return asset instanceof IDistributedInventory physicalInventory ? physicalInventory.aggregatedItems()
            .getOrDefault(wrapper, 0L) : 0L;
    }

    private String amountText(ItemStackWrapper item, boolean reserve) {
        LogisticsResourceConfig config = config(item);
        if (config == null) return "";
        return Integer.toString(reserve ? config.minReserve() : config.orderSize());
    }

    private void setAmount(ItemStackWrapper item, boolean reserve, String text) {
        LogisticsResourceConfig current = config(item);
        CelestialAsset asset = asset();
        if (current == null || asset == null || !canEdit(item, reserve)) return;
        int min = reserve ? 0 : 1;
        int parsed = min;
        if (text != null && !text.isEmpty()) {
            try {
                parsed = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                parsed = min;
            }
        }
        int clamped = Math.max(min, Math.min(MAX_LOGISTICS_AMOUNT, parsed));
        update(asset, item, reserve ? current.withMinReserve(clamped) : current.withOrderSize(clamped));
    }

    private void shift(ItemStackWrapper item, boolean reserve, int delta) {
        LogisticsResourceConfig cfg = config(item);
        CelestialAsset asset = asset();
        if (cfg == null || asset == null || !canEdit(item, reserve)) return;
        update(
            asset,
            item,
            reserve ? cfg.withMinReserve(Math.max(0, cfg.minReserve() + delta))
                : cfg.withOrderSize(Math.max(1, cfg.orderSize() + delta)));
    }

    /** The reserve can always be set, the packet only where the station may send the item */
    private boolean canEdit(ItemStackWrapper item, boolean reserve) {
        return config(item) != null && (reserve || logisticsAccessMode().canEditSupply());
    }

    private void openItemPicker() {
        if (assetId == null) return;
        ItemPickerScreen.setPendingForOutpost(assetId);
        ItemPickerScreen.FACTORY.openClient();
    }

    private void consumePickedItem() {
        if (!ItemPickerScreen.hasPendingPickForOutpost() || assetId == null
            || !assetId.equals(ItemPickerScreen.getPendingForOutpostId())) {
            return;
        }
        ItemStack stack = ItemPickerScreen.pollPendingPickForOutpost();
        CelestialAsset asset = asset();
        if (stack == null || asset == null) return;
        ItemStackWrapper wrapper = ItemStackWrapper.of(stack);
        if (wrapper == null) return;
        LogisticsResourceConfig existing = asset.logisticsConfig.get(wrapper);
        LogisticsResourceConfig config = existing == LogisticsResourceConfig.DEFAULT
            ? defaultConfigForAccessMode(asset, wrapper)
            : logisticsAccessMode().sanitize(existing);
        CelestialClient.updateLogisticsConfig(asset.assetId, wrapper, config, logisticsAccessMode());
    }

    private void toggleImport(ItemStackWrapper item) {
        LogisticsResourceConfig cfg = config(item);
        CelestialAsset asset = asset();
        if (cfg == null || asset == null) return;
        boolean enabled = !cfg.isImportEnabled();
        if (enabled && cfg.minReserve() == 0 && asset instanceof AutomatedFacility facility) {
            cfg = cfg.withMinReserve((int) Math.min(Integer.MAX_VALUE, facility.upkeepReserve(item)));
        }
        update(asset, item, cfg.withImportEnabled(enabled));
    }

    private void toggleExport(ItemStackWrapper item) {
        if (!logisticsAccessMode().canEditSupply()) return;
        LogisticsResourceConfig cfg = config(item);
        CelestialAsset asset = asset();
        if (cfg == null || asset == null) return;
        update(asset, item, cfg.withSupplyEnabled(!cfg.isSupplyEnabled()));
    }

    private void removeEntry(ItemStackWrapper item) {
        CelestialAsset asset = asset();
        if (config(item) == null || asset == null) return;
        CelestialClient.removeLogisticsConfig(asset.assetId, item);
    }

    private void update(CelestialAsset asset, ItemStackWrapper wrapper, LogisticsResourceConfig config) {
        config = logisticsAccessMode().sanitize(config);
        CelestialClient.updateLogisticsConfig(asset.assetId, wrapper, config, logisticsAccessMode());
    }

    /** The item's settings as they stand now, or null once it is no longer tracked */
    private @Nullable LogisticsResourceConfig config(ItemStackWrapper item) {
        CelestialAsset asset = asset();
        if (asset == null) return null;
        for (Map.Entry<InventoryKey, LogisticsResourceConfig> e : asset.logisticsConfig.snapshot()
            .entrySet()) {
            if (item.equals(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows(CelestialAsset asset) {
        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = new ArrayList<>();
        for (Map.Entry<InventoryKey, LogisticsResourceConfig> e : asset.logisticsConfig.snapshot()
            .entrySet()) {
            if (e.getKey() instanceof ItemStackWrapper item) {
                rows.add(new java.util.AbstractMap.SimpleEntry<>(item, e.getValue()));
            }
        }
        rows.sort(
            Comparator.comparing(
                entry -> entry.getKey()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static String rowSignature(List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows) {
        StringBuilder signature = new StringBuilder();
        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row : rows) {
            signature.append(
                row.getKey()
                    .toKey())
                .append(';');
        }
        return signature.toString();
    }

    private @Nullable ModuleHammer selectedHammer() {
        ModuleInstance.ID modId = controller.moduleId();
        ModuleInstance module = modId == null ? null : StationUiData.module(assetId, modId);
        return module != null && module.component() instanceof ModuleHammer hammer ? hammer : null;
    }

    /** Back to the hammer this list was opened from */
    private void back() {
        if (selectedHammer() != null) controller.openHammer(controller.moduleId());
    }

    private CelestialAsset asset() {
        return StationUiData.asset(assetId);
    }

    private LogisticsConfigAccessMode logisticsAccessMode() {
        return controller.logisticsAccessMode();
    }

    private LogisticsResourceConfig defaultConfigForAccessMode(CelestialAsset asset, ItemStackWrapper item) {
        int reserve = asset instanceof AutomatedFacility facility
            ? (int) Math.min(Integer.MAX_VALUE, facility.upkeepReserve(item))
            : 0;
        return logisticsAccessMode() == LogisticsConfigAccessMode.IMPORT_ONLY
            ? new LogisticsResourceConfig(reserve, 64, true, false)
            : new LogisticsResourceConfig(reserve, 64, false, false);
    }

    private static String onOff(boolean on) {
        return tr(on ? "galaxia.gui.common.on" : "galaxia.gui.common.off");
    }

    private static String formatAmount(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
