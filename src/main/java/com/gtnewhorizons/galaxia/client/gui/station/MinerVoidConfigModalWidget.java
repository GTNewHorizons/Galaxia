package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;

final class MinerVoidConfigModalWidget extends ParentWidget<MinerVoidConfigModalWidget> {

    static final int WIDTH = 340;
    static final int HEIGHT = 252;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int ROW_TOP_OFFSET = 34;
    private static final int ROW_Y = BODY_TOP + ROW_TOP_OFFSET;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_ROWS = 8;
    private static final int PAGE_BUTTON_WIDTH = 48;
    private static final int PAGE_BUTTON_HEIGHT = 14;
    private static final int PAGE_PREV_BUTTON_X = WIDTH - 116;
    private static final int PAGE_NEXT_BUTTON_X = WIDTH - 62;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int ROW_BUTTON_Y_OFFSET = 4;
    private static final int ROW_ZERO_BUTTON_X = 150;
    private static final int ROW_ZERO_BUTTON_WIDTH = 18;
    private static final int ROW_MINUS_BUTTON_X = 172;
    private static final int ROW_MINUS_BUTTON_WIDTH = 22;
    private static final int ROW_FIELD_X = 198;
    private static final int ROW_FIELD_WIDTH = 30;
    private static final int ROW_PLUS_BUTTON_X = 232;
    private static final int ROW_PLUS_BUTTON_WIDTH = 22;
    private static final int ROW_ALL_BUTTON_X = 258;
    private static final int ROW_ALL_BUTTON_WIDTH = 28;
    private static final int ROW_BUTTON_HEIGHT = 10;
    private static final int ROW_NAME_WIDTH = 136;
    private static final int PAGE_LABEL_Y = HEIGHT - 24;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    MinerVoidConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(() -> canChangePage(-1), "Prev", () -> changePage(-1))
                .pos(PAGE_PREV_BUTTON_X, BODY_TOP)
                .size(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(() -> canChangePage(1), "Next", () -> changePage(1))
                .pos(PAGE_NEXT_BUTTON_X, BODY_TOP)
                .size(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT));
        for (int i = 0; i < MAX_ROWS; i++) {
            int rowIndex = i;
            int rowY = ROW_Y + rowIndex * ROW_HEIGHT;
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "0", () -> setPercent(rowIndex, 0))
                    .pos(ROW_ZERO_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_ZERO_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "-1", () -> addPercent(rowIndex, -1))
                    .pos(ROW_MINUS_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_MINUS_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
            child(
                createPercentField(rowIndex).pos(ROW_FIELD_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_FIELD_WIDTH, ROW_BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "+1", () -> addPercent(rowIndex, 1))
                    .pos(ROW_PLUS_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_PLUS_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "All", () -> setPercent(rowIndex, 100))
                    .pos(ROW_ALL_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_ALL_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
        }
        child(
            ModuleConfigModalSupport.button(() -> controller.isMinerVoidOpen(), "Close", controller::close)
                .pos(PAGE_NEXT_BUTTON_X, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isMinerVoidOpen()) return;
        ModuleConfigModalSupport.drawFrame("Miner void configuration", WIDTH, HEIGHT);
        ModuleConfigModalSupport.drawLine(
            "Set percent of mined ore to void.",
            ModuleConfigModalSupport.PANEL_PADDING,
            BODY_TOP,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        if (facility == null || module == null || !(module.component() instanceof ModuleMiner)) {
            ModuleConfigModalSupport.drawLine(
                "No miner selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP + 18,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        List<MinerVoidOptions.Entry> options = MinerVoidOptions.forFacility(facility);
        if (options.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No ores available on this body",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP + 18,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        controller.setMinerVoidPage(Math.clamp(controller.minerVoidPage(), 0, maxPage(options.size())));
        int offset = controller.minerVoidPage() * MAX_ROWS;
        int rows = Math.min(options.size() - offset, MAX_ROWS);
        for (int i = 0; i < rows; i++) {
            MinerVoidOptions.Entry option = options.get(offset + i);
            int rowY = ROW_Y + i * ROW_HEIGHT;
            String name = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(option.displayName(), 136);
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                name,
                ModuleConfigModalSupport.PANEL_PADDING,
                rowY + 5,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
        ModuleConfigModalSupport.drawLine(
            "Page " + (controller.minerVoidPage() + 1) + "/" + (maxPage(options.size()) + 1),
            ModuleConfigModalSupport.PANEL_PADDING,
            HEIGHT - 24,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private TextFieldWidget createPercentField(int rowIndex) {
        return new TextFieldWidget().setMaxLength(3)
            .setPattern(Pattern.compile("[0-9]*"))
            .setDefaultNumber(0)
            .setNumbers(0, 100)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (canUseRow(rowIndex)) {
                    BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                }
            }))
            .value(new StringValue.Dynamic(() -> {
                AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
                MinerVoidOptions.Entry option = optionAt(rowIndex);
                return facility == null || option == null ? ""
                    : String.valueOf(facility.minerVoidChancePercent(option.key()));
            }, text -> {
                MinerVoidOptions.Entry option = optionAt(rowIndex);
                if (option == null) return;
                int parsed = 0;
                if (text != null && !text.isEmpty()) {
                    try {
                        parsed = Integer.parseInt(text);
                    } catch (NumberFormatException ignored) {
                        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
                        parsed = facility == null ? 0 : facility.minerVoidChancePercent(option.key());
                    }
                }
                setPercent(option.key(), parsed);
            }))
            .setFocusOnGuiOpen(false);
    }

    private void addPercent(int rowIndex, int delta) {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        MinerVoidOptions.Entry option = optionAt(rowIndex);
        if (facility == null || option == null) return;
        setPercent(option.key(), facility.minerVoidChancePercent(option.key()) + delta);
    }

    private void setPercent(int rowIndex, int percent) {
        MinerVoidOptions.Entry option = optionAt(rowIndex);
        if (option == null) return;
        setPercent(option.key(), percent);
    }

    private void setPercent(String oreKey, int percent) {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleMiner)) return;
        CelestialClient.updateMinerVoidPercent(
            assetId,
            controller.moduleIndex(),
            oreKey,
            AutomatedFacility.clampMinerVoidChancePercent(percent));
    }

    private boolean canUseRow(int rowIndex) {
        return controller.isMinerVoidOpen() && selectedModule() != null && optionAt(rowIndex) != null;
    }

    private boolean canChangePage(int delta) {
        if (!controller.isMinerVoidOpen()) return false;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return false;
        int nextPage = controller.minerVoidPage() + delta;
        return nextPage >= 0 && nextPage <= maxPage(
            MinerVoidOptions.forFacility(facility)
                .size());
    }

    private void changePage(int delta) {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return;
        controller.setMinerVoidPage(
            Math.clamp(
                controller.minerVoidPage() + delta,
                0,
                maxPage(
                    MinerVoidOptions.forFacility(facility)
                        .size())));
    }

    private MinerVoidOptions.Entry optionAt(int rowIndex) {
        if (!controller.isMinerVoidOpen()) return null;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return null;
        List<MinerVoidOptions.Entry> options = MinerVoidOptions.forFacility(facility);
        int index = controller.minerVoidPage() * MAX_ROWS + rowIndex;
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleIndex());
    }

    private static int maxPage(int optionCount) {
        return optionCount <= 0 ? 0 : (optionCount - 1) / MAX_ROWS;
    }
}
