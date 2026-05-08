package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class MinerFocusConfigModalWidget extends ParentWidget<MinerFocusConfigModalWidget> {

    static final int WIDTH = 340;
    static final int HEIGHT = 252;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int BAR_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;
    private static final int BAR_HEIGHT = 8;
    private static final int TIER_BUTTON_X = 58;
    private static final int TIER_BUTTON_Y = BODY_TOP + 52;
    private static final int TIER_BUTTON_WIDTH = 42;
    private static final int TIER_BUTTON_HEIGHT = 14;
    private static final int TIER_BUTTON_GAP = 6;
    private static final int ROW_Y = BODY_TOP + 92;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_ROWS = 5;
    private static final int ROW_ICON_X = ModuleConfigModalSupport.PANEL_PADDING;
    private static final int ROW_ICON_Y_OFFSET = 1;
    private static final int ROW_NAME_X = ROW_ICON_X + 22;
    private static final int ROW_NAME_WIDTH = 210;
    private static final int ROW_FOCUS_BUTTON_X = 258;
    private static final int ROW_FOCUS_BUTTON_WIDTH = 54;
    private static final int PAGE_BUTTON_WIDTH = 48;
    private static final int PAGE_BUTTON_HEIGHT = 14;
    private static final int PAGE_PREV_BUTTON_X = WIDTH - 116;
    private static final int PAGE_NEXT_BUTTON_X = WIDTH - 62;
    private static final int PAGE_BUTTON_Y = ROW_Y - 20;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int CANCEL_BUTTON_WIDTH = 70;
    private static final int CLOSE_BUTTON_X = WIDTH - 62;
    private static final int CLOSE_BUTTON_WIDTH = 54;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    MinerFocusConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        MinerFocusTier[] tiers = MinerFocusTier.values();
        for (int i = 0; i < tiers.length; i++) {
            MinerFocusTier tier = tiers[i];
            child(
                ModuleConfigModalSupport.button(() -> MinerFocusUiModel.canPlanTier(selectedModule(), tier),
                    () -> tierLabel(tier), () -> planTier(tier))
                    .pos(TIER_BUTTON_X + i * (TIER_BUTTON_WIDTH + TIER_BUTTON_GAP), TIER_BUTTON_Y)
                    .size(TIER_BUTTON_WIDTH, TIER_BUTTON_HEIGHT));
        }
        child(
            ModuleConfigModalSupport.button(() -> canChangePage(-1), "Prev", () -> changePage(-1))
                .pos(PAGE_PREV_BUTTON_X, PAGE_BUTTON_Y)
                .size(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(() -> canChangePage(1), "Next", () -> changePage(1))
                .pos(PAGE_NEXT_BUTTON_X, PAGE_BUTTON_Y)
                .size(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT));
        for (int i = 0; i < MAX_ROWS; i++) {
            int rowIndex = i;
            child(
                ModuleConfigModalSupport
                    .button(
                        () -> canUseRow(rowIndex),
                        () -> focusButtonLabel(rowIndex),
                        () -> toggleFocusOre(rowIndex))
                    .pos(ROW_FOCUS_BUTTON_X, ROW_Y + rowIndex * ROW_HEIGHT + 2)
                    .size(ROW_FOCUS_BUTTON_WIDTH, 14));
        }
        child(
            ModuleConfigModalSupport
                .button(this::hasCancellableOperation, this::cancelButtonLabel, this::cancelOperation)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(CANCEL_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isMinerFocusOpen, "Close", controller::close)
                .pos(CLOSE_BUTTON_X, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isMinerFocusOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        if (facility == null || module == null || !(module.component() instanceof ModuleMiner miner)) {
            ModuleConfigModalSupport.drawLine(
                "No miner selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        List<MinerBlacklistOptions.Entry> options = MinerBlacklistOptions.forFacility(facility);
        drawSummary(miner, options);
        controller.setMinerFocusPage(Math.clamp(controller.minerFocusPage(), 0, maxPage(options.size())));
        ModuleConfigModalSupport.drawLine(
            "Focus target",
            ModuleConfigModalSupport.PANEL_PADDING,
            ROW_Y - 20,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        if (options.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No ores available on this body",
                ModuleConfigModalSupport.PANEL_PADDING,
                ROW_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        int offset = controller.minerFocusPage() * MAX_ROWS;
        int rows = Math.min(options.size() - offset, MAX_ROWS);
        for (int i = 0; i < rows; i++) {
            MinerBlacklistOptions.Entry option = options.get(offset + i);
            int rowY = ROW_Y + i * ROW_HEIGHT;
            renderItemIcon(option.displayStack(), ROW_ICON_X, rowY + ROW_ICON_Y_OFFSET);
            String name = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(option.displayName(), ROW_NAME_WIDTH);
            int color = MinerFocusUiModel.isFocusedOre(module, option.key()) ? EnumColors.MAP_COLOR_TEXT_WARNING
                .getColor() : EnumColors.MAP_COLOR_TEXT_BODY.getColor();
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(name, ROW_NAME_X, rowY + 5, color);
        }
        drawFooterStatus(module, options.size());
    }

    private void drawSummary(ModuleMiner miner, List<MinerBlacklistOptions.Entry> options) {
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        int y = BODY_TOP;
        ModuleConfigModalSupport.drawLine(
            "Tier: " + tierLabel(miner.focusTier()) + "  Ore: " + oreLabel(miner.focusOreKeyOrNull(), options),
            x,
            y,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawLine(
            "Alignment: " + MinerFocusUiModel.alignmentPercent(miner) + "%",
            x,
            y + 13,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        int barY = y + 29;
        int fillW = BAR_WIDTH * MinerFocusUiModel.alignmentPercent(miner) / 100;
        Gui.drawRect(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(
            x,
            barY,
            x + fillW,
            barY + BAR_HEIGHT,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
        ModuleConfigModalSupport.drawLine(
            "Focus tier:",
            x,
            TIER_BUTTON_Y + 3,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void drawFooterStatus(ModuleInstance module, int optionCount) {
        String label = MinerFocusUiModel.hasActiveOperation(module)
            ? controller.isModuleOperationCancelArmed() ? "Click Confirm to cancel operation" : "Operation active"
            : "Page " + (controller.minerFocusPage() + 1) + "/" + (maxPage(optionCount) + 1);
        int color = MinerFocusUiModel.hasActiveOperation(module) ? EnumColors.MAP_COLOR_TEXT_WARNING.getColor()
            : EnumColors.MAP_COLOR_TEXT_MUTED.getColor();
        ModuleConfigModalSupport.drawTrimmedLine(label, 86, FOOTER_Y + 6, 180, color);
    }

    private boolean canUseRow(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        return option != null && MinerFocusUiModel.canSetOre(selectedModule(), option.key());
    }

    private String focusButtonLabel(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        if (option == null) return "";
        return MinerFocusUiModel.isFocusedOre(selectedModule(), option.key()) ? "Clear" : "Set";
    }

    private void toggleFocusOre(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        ModuleInstance module = selectedModule();
        if (option == null || module == null) return;
        String targetOreKey = MinerFocusUiModel.oreTargetForClick(module, option.key());
        CelestialClient.setMinerFocusOre(assetId, controller.moduleIndex(), targetOreKey);
    }

    private void planTier(MinerFocusTier tier) {
        if (!MinerFocusUiModel.canPlanTier(selectedModule(), tier)) return;
        CelestialClient.planMinerFocusTier(assetId, controller.moduleIndex(), tier);
    }

    private boolean canChangePage(int delta) {
        if (!controller.isMinerFocusOpen()) return false;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return false;
        int nextPage = controller.minerFocusPage() + delta;
        return nextPage >= 0 && nextPage <= maxPage(
            MinerBlacklistOptions.forFacility(facility)
                .size());
    }

    private void changePage(int delta) {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return;
        controller.setMinerFocusPage(
            Math.clamp(
                controller.minerFocusPage() + delta,
                0,
                maxPage(
                    MinerBlacklistOptions.forFacility(facility)
                        .size())));
    }

    private boolean hasCancellableOperation() {
        return controller.isMinerFocusOpen() && MinerFocusUiModel.hasActiveOperation(selectedModule());
    }

    private String cancelButtonLabel() {
        return controller.isModuleOperationCancelArmed() ? "Confirm" : "Cancel";
    }

    private void cancelOperation() {
        if (!hasCancellableOperation()) return;
        if (!controller.isModuleOperationCancelArmed()) {
            controller.armModuleOperationCancel();
            return;
        }
        CelestialClient.cancelModuleOperation(assetId, controller.moduleIndex());
        controller.clearModuleOperationCancel();
    }

    private MinerBlacklistOptions.Entry optionAt(int rowIndex) {
        if (!controller.isMinerFocusOpen()) return null;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return null;
        List<MinerBlacklistOptions.Entry> options = MinerBlacklistOptions.forFacility(facility);
        int index = controller.minerFocusPage() * MAX_ROWS + rowIndex;
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Miner Focus Configuration"
            : ModuleConfigModalSupport.moduleTitle(module, "Focus Configuration");
    }

    private static String tierLabel(MinerFocusTier tier) {
        return tier == MinerFocusTier.NONE ? "None" : tier.name();
    }

    private static String oreLabel(String oreKey, List<MinerBlacklistOptions.Entry> options) {
        if (oreKey == null) return "None";
        for (MinerBlacklistOptions.Entry option : options) {
            if (oreKey.equals(option.key())) return option.displayName();
        }
        return oreKey;
    }

    private static int maxPage(int optionCount) {
        return optionCount <= 0 ? 0 : (optionCount - 1) / MAX_ROWS;
    }

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(x, y, 0);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 200f;
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        org.lwjgl.opengl.GL11.glPopMatrix();
    }
}
