package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

final class LogisticsConfigModalWidget extends ParentWidget<LogisticsConfigModalWidget> {

    static final int WIDTH = 430;
    static final int HEIGHT = 260;

    private static final int BODY_TOP_OFFSET = 34;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_COUNT = 5;
    private static final int BUTTON_HEIGHT = 18;
    private static final int FOOTER_TOP = HEIGHT - 30;
    private static final int ADD_BUTTON_X = 8;
    private static final int BACK_BUTTON_WIDTH = 54;
    private static final int NAME_X = 8;
    private static final int STOCK_X = 150;
    private static final int RESERVE_X = 196;
    private static final int PACKAGE_X = 270;
    private static final int IMPORT_X = 342;
    private static final int EXPORT_X = 378;
    private static final int REMOVE_X = 406;
    private static final int SMALL_BUTTON_WIDTH = 18;
    private static final int VALUE_WIDTH = 34;
    private static final int TOGGLE_WIDTH = 30;
    private static final int MAX_LOGISTICS_AMOUNT = 999_999;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private int page;

    LogisticsConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(controller::isLogisticsOpen, "Add Item", this::openItemPicker)
                .pos(ADD_BUTTON_X, ModuleConfigModalSupport.HEADER_HEIGHT + 8)
                .size(72, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::hasPreviousPage, "Prev", () -> page--)
                .pos(92, ModuleConfigModalSupport.HEADER_HEIGHT + 8)
                .size(44, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::hasNextPage, "Next", () -> page++)
                .pos(142, ModuleConfigModalSupport.HEADER_HEIGHT + 8)
                .size(44, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isLogisticsOpen, "Back", this::back)
                .pos(WIDTH - ModuleConfigModalSupport.PANEL_PADDING - BACK_BUTTON_WIDTH, FOOTER_TOP)
                .size(BACK_BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        consumePickedItem();
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isLogisticsOpen()) return;
        ModuleConfigModalSupport.drawFrame("Logistics items", WIDTH, HEIGHT);
        AutomatedFacility facility = facility();
        if (facility == null) {
            ModuleConfigModalSupport.drawLine(
                "No station selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        int headerY = BODY_TOP - 14;
        ModuleConfigModalSupport.drawLine("Item", NAME_X, headerY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawLine("Stock", STOCK_X, headerY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawLine("Reserve", RESERVE_X, headerY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawLine("Packet", PACKAGE_X, headerY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawLine("I", IMPORT_X + 8, headerY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawLine("E", EXPORT_X + 8, headerY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());

        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = rows(facility);
        clampPage(rows.size());
        if (rows.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No tracked items",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        int start = page * ROW_COUNT;
        int end = Math.min(rows.size(), start + ROW_COUNT);
        int y = BODY_TOP;
        for (int i = start; i < end; i++) {
            drawRow(facility, rows.get(i), y);
            y += ROW_HEIGHT;
        }
    }

    private void drawRow(AutomatedFacility facility, Map.Entry<ItemStackWrapper, LogisticsResourceConfig> entry,
        int y) {
        ItemStackWrapper wrapper = entry.getKey();
        LogisticsResourceConfig cfg = entry.getValue();
        ItemStack stack = wrapper.toStack(1);
        ModuleConfigModalSupport.drawTrimmedLine(
            stack.getDisplayName(),
            NAME_X,
            y + 5,
            STOCK_X - NAME_X - 6,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawLine(
            formatAmount(facility.inventory.getAmount(wrapper)),
            STOCK_X,
            y + 5,
            EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    @Override
    public void onInit() {
        super.onInit();
        for (int row = 0; row < ROW_COUNT; row++) {
            int rowIndex = row;
            int y = BODY_TOP + row * ROW_HEIGHT + 2;
            child(
                ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "-", () -> shiftReserve(rowIndex, -1))
                    .pos(RESERVE_X, y)
                    .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
            child(
                amountField(rowIndex, true).pos(RESERVE_X + SMALL_BUTTON_WIDTH + 2, y)
                    .size(VALUE_WIDTH - 2, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "+", () -> shiftReserve(rowIndex, 1))
                    .pos(RESERVE_X + SMALL_BUTTON_WIDTH + VALUE_WIDTH, y)
                    .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "-", () -> shiftPackage(rowIndex, -1))
                    .pos(PACKAGE_X, y)
                    .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
            child(
                amountField(rowIndex, false).pos(PACKAGE_X + SMALL_BUTTON_WIDTH + 2, y)
                    .size(VALUE_WIDTH - 2, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "+", () -> shiftPackage(rowIndex, 1))
                    .pos(PACKAGE_X + SMALL_BUTTON_WIDTH + VALUE_WIDTH, y)
                    .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport
                    .button(() -> rowEntry(rowIndex) != null, () -> importLabel(rowIndex), () -> toggleImport(rowIndex))
                    .pos(IMPORT_X, y)
                    .size(TOGGLE_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport
                    .button(() -> rowEntry(rowIndex) != null, () -> exportLabel(rowIndex), () -> toggleExport(rowIndex))
                    .pos(EXPORT_X, y)
                    .size(TOGGLE_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "X", () -> removeEntry(rowIndex))
                    .pos(REMOVE_X, y)
                    .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
        }
    }

    private TextFieldWidget amountField(int rowIndex, boolean reserve) {
        return new TextFieldWidget().setMaxLength(6)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(reserve ? 0 : 1)
            .setNumbers(reserve ? 0 : 1, MAX_LOGISTICS_AMOUNT)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .value(
                new StringValue.Dynamic(
                    () -> amountText(rowIndex, reserve),
                    text -> setAmount(rowIndex, reserve, text)))
            .setFocusOnGuiOpen(false);
    }

    private String amountText(int rowIndex, boolean reserve) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        if (row == null) return "";
        int amount = reserve ? row.getValue()
            .minReserve()
            : row.getValue()
                .orderSize();
        return Integer.toString(amount);
    }

    private void setAmount(int rowIndex, boolean reserve, String text) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        AutomatedFacility facility = facility();
        if (row == null || facility == null) return;
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
        LogisticsResourceConfig current = row.getValue();
        LogisticsResourceConfig updated = reserve ? current.withMinReserve(clamped) : current.withOrderSize(clamped);
        update(facility, row.getKey(), updated);
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
        AutomatedFacility facility = facility();
        if (stack == null || facility == null) return;
        ItemStackWrapper wrapper = ItemStackWrapper.of(stack);
        if (wrapper == null) return;
        LogisticsResourceConfig existing = facility.logisticsConfig.get(wrapper);
        LogisticsResourceConfig config = existing == LogisticsResourceConfig.DEFAULT
            ? new LogisticsResourceConfig(0, 64, false, false)
            : existing;
        facility.logisticsConfig.set(wrapper, config);
        CelestialClient.updateLogisticsConfig(facility.assetId, wrapper, config);
    }

    private void shiftReserve(int rowIndex, int delta) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        AutomatedFacility facility = facility();
        if (row == null || facility == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(facility, row.getKey(), cfg.withMinReserve(Math.max(0, cfg.minReserve() + delta)));
    }

    private void shiftPackage(int rowIndex, int delta) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        AutomatedFacility facility = facility();
        if (row == null || facility == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(facility, row.getKey(), cfg.withOrderSize(Math.max(1, cfg.orderSize() + delta)));
    }

    private void toggleImport(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        AutomatedFacility facility = facility();
        if (row == null || facility == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(facility, row.getKey(), cfg.withImportEnabled(!cfg.isImportEnabled()));
    }

    private void toggleExport(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        AutomatedFacility facility = facility();
        if (row == null || facility == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(facility, row.getKey(), cfg.withSupplyEnabled(!cfg.isSupplyEnabled()));
    }

    private void removeEntry(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        AutomatedFacility facility = facility();
        if (row == null || facility == null) return;
        facility.logisticsConfig.reset(row.getKey());
        CelestialClient.removeLogisticsConfig(facility.assetId, row.getKey());
    }

    private void update(AutomatedFacility facility, ItemStackWrapper wrapper, LogisticsResourceConfig config) {
        facility.logisticsConfig.set(wrapper, config);
        CelestialClient.updateLogisticsConfig(facility.assetId, wrapper, config);
    }

    private String importLabel(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        return row != null && row.getValue()
            .isImportEnabled() ? "On" : "Off";
    }

    private String exportLabel(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        return row != null && row.getValue()
            .isSupplyEnabled() ? "On" : "Off";
    }

    private Map.Entry<ItemStackWrapper, LogisticsResourceConfig> rowEntry(int rowIndex) {
        AutomatedFacility facility = facility();
        if (facility == null) return null;
        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = rows(facility);
        clampPage(rows.size());
        int index = page * ROW_COUNT + rowIndex;
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private boolean hasPreviousPage() {
        return page > 0;
    }

    private boolean hasNextPage() {
        AutomatedFacility facility = facility();
        return facility != null && (page + 1) * ROW_COUNT < rows(facility).size();
    }

    private void clampPage(int size) {
        int maxPage = Math.max(0, (size - 1) / ROW_COUNT);
        if (page > maxPage) page = maxPage;
    }

    private List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows(AutomatedFacility facility) {
        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = new ArrayList<>(
            facility.logisticsConfig.snapshot()
                .entrySet());
        rows.sort(
            Comparator.comparing(
                entry -> entry.getKey()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private void back() {
        int moduleIndex = controller.moduleIndex();
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleIndex);
        if (module != null
            && module.component() instanceof com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer) {
            controller.openHammer(moduleIndex);
            return;
        }
        controller.close();
    }

    private AutomatedFacility facility() {
        return ModuleConfigModalSupport.facility(assetId);
    }

    private static String formatAmount(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }
}
