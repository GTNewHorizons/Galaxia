package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

final class LogisticsConfigModalWidget extends ParentWidget<LogisticsConfigModalWidget> {

    static final int WIDTH = 600;
    static final int HEIGHT = 310;

    private static final int TOP_BUTTON_Y = ModuleConfigModalSupport.HEADER_HEIGHT + 8;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BACK_BUTTON_X = 8;
    private static final int BACK_BUTTON_WIDTH = 54;
    private static final int ADD_BUTTON_X = 76;
    private static final int ADD_BUTTON_WIDTH = 72;
    private static final int HEADER_Y = ModuleConfigModalSupport.HEADER_HEIGHT + 38;
    private static final int SCROLL_X = 8;
    private static final int SCROLL_Y = ModuleConfigModalSupport.HEADER_HEIGHT + 52;
    private static final int SCROLL_WIDTH = WIDTH - SCROLL_X * 2;
    private static final int SCROLL_HEIGHT = HEIGHT - SCROLL_Y - 10;
    private static final int SCROLLBAR_GAP = 8;
    private static final int LIST_WIDTH = SCROLL_WIDTH - SCROLLBAR_GAP;
    private static final int ROW_HEIGHT = 31;
    private static final int ROW_GAP = 2;
    private static final int ICON_X = 8;
    private static final int ICON_Y = 7;
    private static final int NAME_X = 30;
    private static final int NAME_WIDTH = 184;
    private static final int SMALL_BUTTON_WIDTH = 18;
    private static final int VALUE_WIDTH = 42;
    private static final int CONTROL_GROUP_WIDTH = SMALL_BUTTON_WIDTH + VALUE_WIDTH + SMALL_BUTTON_WIDTH;
    private static final int TOGGLE_WIDTH = 48;
    private static final int REMOVE_BUTTON_WIDTH = 18;
    private static final int STOCK_X = columnX(0.39f);
    private static final int RESERVE_X = columnX(0.50f);
    private static final int PACKAGE_X = columnX(0.65f);
    private static final int IMPORT_X = columnX(0.79f);
    private static final int EXPORT_X = columnX(0.88f);
    private static final int REMOVE_X = LIST_WIDTH - REMOVE_BUTTON_WIDTH;
    private static final int MAX_LOGISTICS_AMOUNT = 999_999;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final VerticalScrollData scrollData = new VerticalScrollData();
    private final ParentWidget<?> scrollContent = new ParentWidget<>().widthRel(1f);

    private String rowSignature = "";

    LogisticsConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(controller::isLogisticsOpen, "Back", this::back)
                .pos(BACK_BUTTON_X, TOP_BUTTON_Y)
                .size(BACK_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isLogisticsOpen, "Add Item", this::openItemPicker)
                .pos(ADD_BUTTON_X, TOP_BUTTON_Y)
                .size(ADD_BUTTON_WIDTH, BUTTON_HEIGHT));
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(SCROLL_X, SCROLL_Y)
            .size(SCROLL_WIDTH, SCROLL_HEIGHT)
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
        scroll.child(scrollContent);
        child(scroll);
        addHeaderTooltip(NAME_X, NAME_WIDTH, "Tracked item");
        addHeaderTooltip(STOCK_X, RESERVE_X - STOCK_X, "Items stored in this station");
        addHeaderTooltip(RESERVE_X, CONTROL_GROUP_WIDTH, "Minimum stock kept in station inventory");
        addHeaderTooltip(PACKAGE_X, CONTROL_GROUP_WIDTH, "Export package size for supplied items");
        addHeaderTooltip(IMPORT_X, TOGGLE_WIDTH, "Request this item from other stations");
        if (logisticsAccessMode().canEditSupply()) {
            addHeaderTooltip(EXPORT_X, TOGGLE_WIDTH, "Send this item to other stations");
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        consumePickedItem();
        refreshRows();
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isLogisticsOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        CelestialAsset asset = asset();
        if (asset == null) {
            ModuleConfigModalSupport.drawLine(
                "No station selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                HEADER_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        drawHeaders();
        if (rows(asset).isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No tracked items",
                SCROLL_X + ModuleConfigModalSupport.PANEL_PADDING,
                SCROLL_Y + ModuleConfigModalSupport.PANEL_PADDING,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
    }

    private void refreshRows() {
        CelestialAsset asset = asset();
        if (asset == null || !controller.isLogisticsOpen()) {
            rowSignature = "";
            setRows(List.of(), asset);
            return;
        }
        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = rows(asset);
        String signature = rowSignature(rows);
        if (signature.equals(rowSignature)) return;
        rowSignature = signature;
        setRows(rows, asset);
    }

    private void setRows(List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows, CelestialAsset asset) {
        scrollContent.removeAll();
        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            scrollContent.child(rowWidget(i, rows.get(i), asset).pos(0, y));
            y += ROW_HEIGHT + ROW_GAP;
        }
        int contentHeight = Math.max(SCROLL_HEIGHT, y + ROW_GAP);
        scrollContent.height(contentHeight);
        scrollData.setScrollSize(contentHeight);
        scrollContent.scheduleResize();
        scheduleResize();
    }

    private ParentWidget<?> rowWidget(int rowIndex, Map.Entry<ItemStackWrapper, LogisticsResourceConfig> entry,
        CelestialAsset asset) {
        ParentWidget<?> row = new ParentWidget<>().widthRelOffset(1f, -SCROLLBAR_GAP)
            .height(ROW_HEIGHT)
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
        row.child(
            ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> drawRowText(asset, entry, x, y, w))
                .asWidget()
                .pos(0, 0)
                .widthRel(1f)
                .height(ROW_HEIGHT));
        row.child(
            ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "-", () -> shiftReserve(rowIndex, -1))
                .pos(RESERVE_X, 6)
                .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
        row.child(
            amountField(rowIndex, true).pos(RESERVE_X + SMALL_BUTTON_WIDTH, 6)
                .size(VALUE_WIDTH, BUTTON_HEIGHT));
        row.child(
            ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "+", () -> shiftReserve(rowIndex, 1))
                .pos(RESERVE_X + SMALL_BUTTON_WIDTH + VALUE_WIDTH, 6)
                .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
        row.child(
            ModuleConfigModalSupport.button(() -> canEditPackage(rowIndex), "-", () -> shiftPackage(rowIndex, -1))
                .pos(PACKAGE_X, 6)
                .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
        row.child(
            amountField(rowIndex, false).pos(PACKAGE_X + SMALL_BUTTON_WIDTH, 6)
                .size(VALUE_WIDTH, BUTTON_HEIGHT));
        row.child(
            ModuleConfigModalSupport.button(() -> canEditPackage(rowIndex), "+", () -> shiftPackage(rowIndex, 1))
                .pos(PACKAGE_X + SMALL_BUTTON_WIDTH + VALUE_WIDTH, 6)
                .size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT));
        row.child(
            ModuleConfigModalSupport
                .button(() -> rowEntry(rowIndex) != null, () -> importLabel(rowIndex), () -> toggleImport(rowIndex))
                .pos(IMPORT_X, 6)
                .size(TOGGLE_WIDTH, BUTTON_HEIGHT));
        row.child(
            ModuleConfigModalSupport
                .button(
                    () -> logisticsAccessMode().canEditSupply() && rowEntry(rowIndex) != null,
                    () -> exportLabel(rowIndex),
                    () -> toggleExport(rowIndex))
                .pos(EXPORT_X, 6)
                .size(TOGGLE_WIDTH, BUTTON_HEIGHT));
        row.child(
            ModuleConfigModalSupport.button(() -> rowEntry(rowIndex) != null, "X", () -> removeEntry(rowIndex))
                .pos(REMOVE_X, 6)
                .size(REMOVE_BUTTON_WIDTH, BUTTON_HEIGHT));
        return row;
    }

    private void drawHeaders() {
        ModuleConfigModalSupport.drawCenteredLine(
            "Item",
            SCROLL_X + NAME_X + NAME_WIDTH / 2,
            HEADER_Y,
            NAME_WIDTH,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawCenteredLine(
            "Inventory",
            SCROLL_X + STOCK_X + (RESERVE_X - STOCK_X) / 2,
            HEADER_Y,
            RESERVE_X - STOCK_X,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawCenteredLine(
            "Reserve",
            SCROLL_X + RESERVE_X + CONTROL_GROUP_WIDTH / 2,
            HEADER_Y,
            CONTROL_GROUP_WIDTH,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawCenteredLine(
            "Packet",
            SCROLL_X + PACKAGE_X + CONTROL_GROUP_WIDTH / 2,
            HEADER_Y,
            CONTROL_GROUP_WIDTH,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport.drawCenteredLine(
            "Import",
            SCROLL_X + IMPORT_X + TOGGLE_WIDTH / 2,
            HEADER_Y,
            TOGGLE_WIDTH,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        if (logisticsAccessMode().canEditSupply()) {
            ModuleConfigModalSupport.drawCenteredLine(
                "Export",
                SCROLL_X + EXPORT_X + TOGGLE_WIDTH / 2,
                HEADER_Y,
                TOGGLE_WIDTH,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
    }

    private void drawRowText(CelestialAsset asset, Map.Entry<ItemStackWrapper, LogisticsResourceConfig> entry, int x,
        int y, int width) {
        ItemStackWrapper wrapper = entry.getKey();
        ItemStack stack = wrapper.toStack(1);
        renderItemIcon(stack, x + ICON_X, y + ICON_Y);
        ModuleConfigModalSupport.drawTrimmedLine(
            stack.getDisplayName(),
            x + NAME_X,
            y + 11,
            Math.min(NAME_WIDTH, STOCK_X - NAME_X - 8),
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawTrimmedLine(
            formatAmount(stockAmount(asset, wrapper)),
            x + STOCK_X,
            y + 11,
            RESERVE_X - STOCK_X - 8,
            EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    private static long stockAmount(CelestialAsset asset, ItemStackWrapper wrapper) {
        if (asset instanceof AutomatedFacility af) {
            return af.getItemAmount(wrapper);
        }
        return asset.aggregatedItems()
            .getOrDefault(wrapper, 0L);
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
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> reserve ? rowEntry(rowIndex) != null : canEditPackage(rowIndex));
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
        CelestialAsset asset = asset();
        if (row == null || asset == null) return;
        if (!reserve && !canEditPackage(rowIndex)) return;
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
        update(asset, row.getKey(), updated);
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
        LogisticsResourceConfig config = existing == LogisticsResourceConfig.DEFAULT ? defaultConfigForAccessMode()
            : logisticsAccessMode().sanitize(existing);
        asset.logisticsConfig.set(wrapper, config);
        CelestialClient.updateLogisticsConfig(asset.assetId, wrapper, config, logisticsAccessMode());
    }

    private void shiftReserve(int rowIndex, int delta) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        CelestialAsset asset = asset();
        if (row == null || asset == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(asset, row.getKey(), cfg.withMinReserve(Math.max(0, cfg.minReserve() + delta)));
    }

    private void shiftPackage(int rowIndex, int delta) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        CelestialAsset asset = asset();
        if (row == null || asset == null || !canEditPackage(rowIndex)) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(asset, row.getKey(), cfg.withOrderSize(Math.max(1, cfg.orderSize() + delta)));
    }

    private boolean canEditPackage(int rowIndex) {
        return logisticsAccessMode().canEditSupply() && rowEntry(rowIndex) != null;
    }

    private void toggleImport(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        CelestialAsset asset = asset();
        if (row == null || asset == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(asset, row.getKey(), cfg.withImportEnabled(!cfg.isImportEnabled()));
    }

    private void toggleExport(int rowIndex) {
        if (!logisticsAccessMode().canEditSupply()) return;
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        CelestialAsset asset = asset();
        if (row == null || asset == null) return;
        LogisticsResourceConfig cfg = row.getValue();
        update(asset, row.getKey(), cfg.withSupplyEnabled(!cfg.isSupplyEnabled()));
    }

    private void removeEntry(int rowIndex) {
        Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row = rowEntry(rowIndex);
        CelestialAsset asset = asset();
        if (row == null || asset == null) return;
        asset.logisticsConfig.reset(row.getKey());
        CelestialClient.removeLogisticsConfig(asset.assetId, row.getKey());
        rowSignature = "";
        refreshRows();
    }

    private void update(CelestialAsset asset, ItemStackWrapper wrapper, LogisticsResourceConfig config) {
        config = logisticsAccessMode().sanitize(config);
        asset.logisticsConfig.set(wrapper, config);
        CelestialClient.updateLogisticsConfig(asset.assetId, wrapper, config, logisticsAccessMode());
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
        CelestialAsset asset = asset();
        if (asset == null) return null;
        List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows = rows(asset);
        return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex) : null;
    }

    @SuppressWarnings("Convert2Diamond")
    private List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows(CelestialAsset asset) {
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

    private String rowSignature(List<Map.Entry<ItemStackWrapper, LogisticsResourceConfig>> rows) {
        StringBuilder signature = new StringBuilder();
        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> row : rows) {
            signature.append(
                row.getKey()
                    .toKey())
                .append(';');
        }
        return signature.toString();
    }

    private void back() {
        ModuleInstance.ID modId = controller.moduleId();
        if (modId != null) {
            int moduleIndex = controller.moduleIndex();
            ModuleInstance module = ModuleConfigModalSupport.module(assetId, modId);
            if (module != null && module.component() instanceof ModuleHammer) {
                controller.openHammer(moduleIndex);
                return;
            }
        }
        controller.close();
    }

    private String title() {
        ModuleInstance.ID modId = controller.moduleId();
        ModuleInstance module = modId != null ? ModuleConfigModalSupport.module(assetId, modId) : null;
        if (module != null) return ModuleConfigModalSupport.moduleTitle(module, "Logistics");
        return logisticsAccessMode() == LogisticsConfigAccessMode.IMPORT_ONLY ? "Core Logistics" : "Logistics";
    }

    private CelestialAsset asset() {
        return ModuleConfigModalSupport.celestialAsset(assetId);
    }

    private LogisticsConfigAccessMode logisticsAccessMode() {
        return controller.logisticsAccessMode();
    }

    private LogisticsResourceConfig defaultConfigForAccessMode() {
        return logisticsAccessMode() == LogisticsConfigAccessMode.IMPORT_ONLY
            ? new LogisticsResourceConfig(0, 64, true, false)
            : new LogisticsResourceConfig(0, 64, false, false);
    }

    private void addHeaderTooltip(int x, int width, String text) {
        child(
            ModuleConfigModalSupport.drawable((ctx, drawX, drawY, drawW, drawH) -> {})
                .asWidget()
                .pos(SCROLL_X + x, HEADER_Y - 2)
                .size(width, 13)
                .tooltip(t -> t.addLine(text)));
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

    private static String formatAmount(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private static int columnX(float rel) {
        return Math.round(LIST_WIDTH * rel);
    }
}
