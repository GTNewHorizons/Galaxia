package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

import net.minecraft.client.Minecraft;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class MinerBlacklistConfigModalWidget extends ParentWidget<MinerBlacklistConfigModalWidget> {

    static final int WIDTH = 360;
    static final int HEIGHT = 278;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int ROW_TOP_OFFSET = 58;
    private static final int ROW_Y = BODY_TOP + ROW_TOP_OFFSET;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_ROWS = 6;
    private static final int PAGE_BUTTON_WIDTH = 48;
    private static final int PAGE_BUTTON_HEIGHT = 14;
    private static final int PAGE_PREV_BUTTON_X = WIDTH - 116;
    private static final int PAGE_NEXT_BUTTON_X = WIDTH - 62;
    private static final int PAGE_BUTTON_Y = ROW_Y - 20;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int COPY_SETTINGS_BUTTON_WIDTH = 104;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int ROW_ICON_X = ModuleConfigModalSupport.PANEL_PADDING;
    private static final int ROW_ICON_Y_OFFSET = 1;
    private static final int ROW_NAME_X = ROW_ICON_X + 22;
    private static final int ROW_NAME_WIDTH = 170;
    private static final int ROW_FOCUS_BUTTON_X = 210;
    private static final int ROW_FOCUS_BUTTON_WIDTH = 54;
    private static final int ROW_FOCUS_BUTTON_HEIGHT = 14;
    private static final int ROW_FOCUS_BUTTON_Y_OFFSET = 2;
    private static final int ROW_CHECKBOX_X = WIDTH - 34;
    private static final int ROW_CHECKBOX_SIZE = 14;
    private static final int ROW_CHECKBOX_Y_OFFSET = 2;
    private static final int PAGE_LABEL_Y = HEIGHT - 24;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final StationTilePickerController tilePickerController;
    private final ModuleSettingsGroupSelectorWidget settingsGroupSelector;

    MinerBlacklistConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        StationTilePickerController tilePickerController) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
        this.settingsGroupSelector = new ModuleSettingsGroupSelectorWidget(
            assetId,
            controller,
            FacilityModuleKind.MINER,
            controller::isMinerBlacklistOpen,
            WIDTH);
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
            int rowY = ROW_Y + rowIndex * ROW_HEIGHT;
            child(
                ModuleConfigModalSupport
                    .button(
                        () -> canUseFocusRow(rowIndex),
                        () -> focusButtonLabel(rowIndex),
                        () -> toggleFocusOre(rowIndex))
                    .pos(ROW_FOCUS_BUTTON_X, rowY + ROW_FOCUS_BUTTON_Y_OFFSET)
                    .size(ROW_FOCUS_BUTTON_WIDTH, ROW_FOCUS_BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport
                    .checkbox(
                        () -> canUseRow(rowIndex),
                        () -> isBlacklisted(rowIndex),
                        "Void this ore after mining",
                        () -> toggleBlacklisted(rowIndex))
                    .pos(ROW_CHECKBOX_X, rowY + ROW_CHECKBOX_Y_OFFSET)
                    .size(ROW_CHECKBOX_SIZE, ROW_CHECKBOX_SIZE));
        }
        child(
            ModuleConfigModalSupport.button(this::canCopySettings, "Copy Settings...", this::startCopySettingsPicker)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(COPY_SETTINGS_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(() -> controller.isMinerBlacklistOpen(), "Close", controller::close)
                .pos(PAGE_NEXT_BUTTON_X, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
        child(
            settingsGroupSelector.pos(0, 0)
                .size(WIDTH, HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isMinerBlacklistOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        ModuleConfigModalSupport.drawLine(
            "Runtime miner settings.",
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

        List<MinerBlacklistOptions.Entry> options = MinerBlacklistOptions.forFacility(facility);
        if (options.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No ores available on this body",
                ModuleConfigModalSupport.PANEL_PADDING,
                ROW_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        controller.setMinerBlacklistPage(Math.clamp(controller.minerBlacklistPage(), 0, maxPage(options.size())));
        ModuleConfigModalSupport.drawLine(
            "Planetary ores",
            ModuleConfigModalSupport.PANEL_PADDING,
            ROW_Y - 14,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        if (MinerFocusUiModel.canShowOreFocus(module)) {
            ModuleConfigModalSupport.drawCenteredLine(
                "Focus",
                ROW_FOCUS_BUTTON_X + ROW_FOCUS_BUTTON_WIDTH / 2,
                ROW_Y - 14,
                58,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
        ModuleConfigModalSupport.drawCenteredLine(
            "Blacklist",
            ROW_CHECKBOX_X + ROW_CHECKBOX_SIZE / 2,
            ROW_Y - 14,
            58,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        int offset = controller.minerBlacklistPage() * MAX_ROWS;
        int rows = Math.min(options.size() - offset, MAX_ROWS);
        for (int i = 0; i < rows; i++) {
            MinerBlacklistOptions.Entry option = options.get(offset + i);
            int rowY = ROW_Y + i * ROW_HEIGHT;
            renderItemIcon(option.displayStack(), ROW_ICON_X, rowY + ROW_ICON_Y_OFFSET);
            String name = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(option.displayName(), ROW_NAME_WIDTH);
            int color = MinerFocusUiModel.isFocusedOre(module, option.key())
                ? EnumColors.MAP_COLOR_TEXT_WARNING.getColor()
                : EnumColors.MAP_COLOR_TEXT_BODY.getColor();
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(name, ROW_NAME_X, rowY + 5, color);
        }
        ModuleConfigModalSupport.drawLine(
            "Page " + (controller.minerBlacklistPage() + 1) + "/" + (maxPage(options.size()) + 1),
            ModuleConfigModalSupport.PANEL_PADDING,
            HEIGHT - 24,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private boolean isBlacklisted(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        ModuleInstance module = selectedModule();
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        return option != null && module != null
            && facility != null
            && facility.isMinerOreBlacklisted(module, option.key());
    }

    private void toggleBlacklisted(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        if (option == null) return;
        setBlacklisted(option.key(), !isBlacklisted(rowIndex));
    }

    private void setBlacklisted(String oreKey, boolean blacklisted) {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleMiner)) return;
        settingsGroupSelector.closeMenu();
        CelestialClient.updateMinerOreBlacklisted(assetId, controller.moduleIndex(), oreKey, blacklisted);
    }

    private void toggleFocusOre(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        ModuleInstance module = selectedModule();
        if (option == null || module == null || !(module.component() instanceof ModuleMiner)) return;
        settingsGroupSelector.closeMenu();
        String targetOreKey = MinerFocusUiModel.oreTargetForClick(module, option.key());
        CelestialClient.setMinerFocusOre(assetId, controller.moduleIndex(), targetOreKey);
    }

    private boolean canCopySettings() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        return tilePickerController != null && controller.isMinerBlacklistOpen()
            && !settingsGroupSelector.isBlockingModuleControls()
            && facility != null
            && facility.stationLayout() != null
            && module != null
            && module.component() instanceof ModuleMiner;
    }

    private void startCopySettingsPicker() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance source = selectedModule();
        int sourceModuleIndex = controller.moduleIndex();
        if (facility == null || source == null || tilePickerController == null || sourceModuleIndex < 0) return;
        controller.close();
        tilePickerController.start(
            "Copy miner settings",
            "Copy",
            coord -> MinerSettingsCopyPickerModel.isCompatibleTarget(facility, source, coord),
            coord -> MinerSettingsCopyPickerModel.normalizeTarget(facility, coord),
            targets -> CelestialClient.copyMinerSettings(assetId, sourceModuleIndex, targets));
    }

    private boolean canUseRow(int rowIndex) {
        return controller.isMinerBlacklistOpen() && !settingsGroupSelector.isBlockingModuleControls()
            && selectedModule() != null
            && optionAt(rowIndex) != null;
    }

    private boolean canUseFocusRow(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        return controller.isMinerBlacklistOpen() && !settingsGroupSelector.isBlockingModuleControls()
            && option != null
            && MinerFocusUiModel.canSetOre(selectedModule(), option.key());
    }

    private String focusButtonLabel(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        if (option == null) return "";
        return MinerFocusUiModel.isFocusedOre(selectedModule(), option.key()) ? "Clear" : "Set";
    }

    private boolean canChangePage(int delta) {
        if (!controller.isMinerBlacklistOpen()) return false;
        if (settingsGroupSelector.isBlockingModuleControls()) return false;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return false;
        int nextPage = controller.minerBlacklistPage() + delta;
        return nextPage >= 0 && nextPage <= maxPage(
            MinerBlacklistOptions.forFacility(facility)
                .size());
    }

    private void changePage(int delta) {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return;
        settingsGroupSelector.closeMenu();
        controller.setMinerBlacklistPage(
            Math.clamp(
                controller.minerBlacklistPage() + delta,
                0,
                maxPage(
                    MinerBlacklistOptions.forFacility(facility)
                        .size())));
    }

    private MinerBlacklistOptions.Entry optionAt(int rowIndex) {
        if (!controller.isMinerBlacklistOpen()) return null;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return null;
        List<MinerBlacklistOptions.Entry> options = MinerBlacklistOptions.forFacility(facility);
        int index = controller.minerBlacklistPage() * MAX_ROWS + rowIndex;
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Miner Configuration" : ModuleConfigModalSupport.moduleTitle(module, "Configuration");
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
