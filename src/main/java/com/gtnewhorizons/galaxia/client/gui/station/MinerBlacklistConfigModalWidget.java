package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class MinerBlacklistConfigModalWidget extends ParentWidget<MinerBlacklistConfigModalWidget> {

    static final int WIDTH = 340;
    static final int HEIGHT = 252;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int GROUP_LABEL_Y = BODY_TOP + 17;
    private static final int GROUP_BUTTON_X = 104;
    private static final int GROUP_BUTTON_Y = BODY_TOP + 14;
    private static final int GROUP_BUTTON_WIDTH = 170;
    private static final int GROUP_BUTTON_HEIGHT = 14;
    private static final int GROUP_OPTION_Y = GROUP_BUTTON_Y + GROUP_BUTTON_HEIGHT + 2;
    private static final int GROUP_OPTION_HEIGHT = 12;
    private static final int MAX_GROUP_OPTIONS = 10;
    private static final int ROW_TOP_OFFSET = 62;
    private static final int ROW_Y = BODY_TOP + ROW_TOP_OFFSET;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_ROWS = 6;
    private static final int PAGE_BUTTON_WIDTH = 48;
    private static final int PAGE_BUTTON_HEIGHT = 14;
    private static final int PAGE_PREV_BUTTON_X = WIDTH - 116;
    private static final int PAGE_NEXT_BUTTON_X = WIDTH - 62;
    private static final int PAGE_BUTTON_Y = BODY_TOP + 38;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int ROW_BUTTON_Y_OFFSET = 4;
    private static final int ROW_STATE_X = 150;
    private static final int ROW_KEEP_BUTTON_X = 202;
    private static final int ROW_KEEP_BUTTON_WIDTH = 34;
    private static final int ROW_VOID_BUTTON_X = 244;
    private static final int ROW_VOID_BUTTON_WIDTH = 34;
    private static final int ROW_FOCUS_BUTTON_X = 286;
    private static final int ROW_FOCUS_BUTTON_WIDTH = 42;
    private static final int ROW_BUTTON_HEIGHT = 10;
    private static final int ROW_NAME_WIDTH = 136;
    private static final int PAGE_LABEL_Y = HEIGHT - 24;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    MinerBlacklistConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport
                .button(this::hasMinerSelected, this::currentGroupButtonLabel, controller::toggleMinerSettingsGroupMenu)
                .pos(GROUP_BUTTON_X, GROUP_BUTTON_Y)
                .size(GROUP_BUTTON_WIDTH, GROUP_BUTTON_HEIGHT));
        for (int i = 0; i < MAX_GROUP_OPTIONS; i++) {
            int optionIndex = i;
            child(
                ModuleConfigModalSupport
                    .button(
                        () -> canUseGroupOption(optionIndex),
                        () -> groupOptionLabel(optionIndex),
                        () -> selectGroupOption(optionIndex))
                    .pos(GROUP_BUTTON_X, GROUP_OPTION_Y + optionIndex * GROUP_OPTION_HEIGHT)
                    .size(GROUP_BUTTON_WIDTH, GROUP_OPTION_HEIGHT));
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
            int rowY = ROW_Y + rowIndex * ROW_HEIGHT;
            child(
                ModuleConfigModalSupport
                    .button(() -> canUseRow(rowIndex), "Keep", () -> setBlacklisted(rowIndex, false))
                    .pos(ROW_KEEP_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_KEEP_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "Void", () -> setBlacklisted(rowIndex, true))
                    .pos(ROW_VOID_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_VOID_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseFocusRow(rowIndex), "Focus", () -> planFocus(rowIndex))
                    .pos(ROW_FOCUS_BUTTON_X, rowY + ROW_BUTTON_Y_OFFSET)
                    .size(ROW_FOCUS_BUTTON_WIDTH, ROW_BUTTON_HEIGHT));
        }
        child(
            ModuleConfigModalSupport.button(() -> controller.isMinerBlacklistOpen(), "Close", controller::close)
                .pos(PAGE_NEXT_BUTTON_X, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isMinerBlacklistOpen()) return;
        ModuleConfigModalSupport.drawFrame("Miner blacklist configuration", WIDTH, HEIGHT);
        ModuleConfigModalSupport.drawLine(
            "Void selected ores after they are mined.",
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

        ModuleConfigModalSupport.drawLine(
            "Settings group:",
            ModuleConfigModalSupport.PANEL_PADDING,
            GROUP_LABEL_Y,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport.drawLine(
            focusLabel((ModuleMiner) module.component()),
            ModuleConfigModalSupport.PANEL_PADDING,
            BODY_TOP + 36,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());

        List<MinerBlacklistOptions.Entry> options = MinerBlacklistOptions.forFacility(facility);
        if (options.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No ores available on this body",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP + 18,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        controller.setMinerBlacklistPage(Math.clamp(controller.minerBlacklistPage(), 0, maxPage(options.size())));
        int offset = controller.minerBlacklistPage() * MAX_ROWS;
        int rows = Math.min(options.size() - offset, MAX_ROWS);
        for (int i = 0; i < rows; i++) {
            MinerBlacklistOptions.Entry option = options.get(offset + i);
            int rowY = ROW_Y + i * ROW_HEIGHT;
            String name = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(option.displayName(), 136);
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                name,
                ModuleConfigModalSupport.PANEL_PADDING,
                rowY + 5,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                facility.isMinerOreBlacklisted(module, option.key()) ? "VOID" : "KEEP",
                ROW_STATE_X,
                rowY + 5,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
        if (controller.isMinerSettingsGroupMenuOpen()) {
            drawGroupOptionHint();
        }
        ModuleConfigModalSupport.drawLine(
            "Page " + (controller.minerBlacklistPage() + 1) + "/" + (maxPage(options.size()) + 1),
            ModuleConfigModalSupport.PANEL_PADDING,
            HEIGHT - 24,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void setBlacklisted(int rowIndex, boolean blacklisted) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        if (option == null) return;
        setBlacklisted(option.key(), blacklisted);
    }

    private void setBlacklisted(String oreKey, boolean blacklisted) {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleMiner)) return;
        controller.closeMinerSettingsGroupMenu();
        CelestialClient.updateMinerOreBlacklisted(assetId, controller.moduleIndex(), oreKey, blacklisted);
    }

    private void planFocus(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        ModuleInstance module = selectedModule();
        if (option == null || module == null || !(module.component() instanceof ModuleMiner miner)) return;
        controller.closeMinerSettingsGroupMenu();
        MinerFocusTier targetTier = targetFocusTier(miner, option.key());
        CelestialClient.planMinerFocus(
            assetId,
            controller.moduleIndex(),
            targetTier,
            targetTier == MinerFocusTier.NONE ? null : option.key());
    }

    private void selectGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        ModuleInstance module = selectedModule();
        if (option == null || module == null) return;
        controller.closeMinerSettingsGroupMenu();
        if (option.action() == GroupOptionAction.CREATE) {
            CelestialClient.createMinerSettingsGroup(assetId, controller.moduleIndex());
            return;
        }
        if (module.groupId() != option.groupId()) {
            CelestialClient.updateMinerSettingsGroup(assetId, controller.moduleIndex(), option.groupId());
        }
    }

    private boolean canUseRow(int rowIndex) {
        return controller.isMinerBlacklistOpen() && !controller.isMinerSettingsGroupMenuOpen()
            && selectedModule() != null
            && optionAt(rowIndex) != null;
    }

    private boolean canUseFocusRow(int rowIndex) {
        ModuleInstance module = selectedModule();
        return canUseRow(rowIndex) && module != null && module.operationOrNull() == null;
    }

    private boolean canChangePage(int delta) {
        if (!controller.isMinerBlacklistOpen()) return false;
        if (controller.isMinerSettingsGroupMenuOpen()) return false;
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
        controller.closeMinerSettingsGroupMenu();
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
        return ModuleConfigModalSupport.module(assetId, controller.moduleIndex());
    }

    private boolean hasMinerSelected() {
        ModuleInstance module = selectedModule();
        return controller.isMinerBlacklistOpen() && module != null && module.component() instanceof ModuleMiner;
    }

    private boolean canUseGroupOption(int optionIndex) {
        return controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
            && groupOptionAt(optionIndex) != null;
    }

    private String currentGroupButtonLabel() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        if (facility == null || module == null) return "No Group";
        return currentGroupLabel(facility, module);
    }

    private String currentGroupLabel(AutomatedFacility facility, ModuleInstance module) {
        if (module.groupId() == 0) return "No Group";
        return facility.settingsGroups()
            .require(module.groupId(), module.kind())
            .displayName();
    }

    private String groupOptionLabel(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return option == null ? "" : option.label();
    }

    private GroupOption groupOptionAt(int optionIndex) {
        List<GroupOption> options = groupOptions();
        return optionIndex >= 0 && optionIndex < options.size() ? options.get(optionIndex) : null;
    }

    private List<GroupOption> groupOptions() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        if (facility == null || module == null || !(module.component() instanceof ModuleMiner)) return List.of();
        List<GroupOption> options = new ArrayList<>();
        options.add(new GroupOption("No Group", (short) 0, GroupOptionAction.SELECT));
        options.add(new GroupOption("Create New Group", (short) 0, GroupOptionAction.CREATE));
        facility.settingsGroups()
            .groups()
            .values()
            .stream()
            .filter(group -> group.kind() == FacilityModuleKind.MINER)
            .sorted(Comparator.comparing(SettingsGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .forEach(group -> options.add(new GroupOption(group.displayName(), group.id(), GroupOptionAction.SELECT)));
        return options;
    }

    private void drawGroupOptionHint() {
        int extraGroups = groupOptions().size() - MAX_GROUP_OPTIONS;
        if (extraGroups <= 0) return;
        ModuleConfigModalSupport.drawLine(
            "+" + extraGroups + " more groups",
            GROUP_BUTTON_X,
            GROUP_OPTION_Y + MAX_GROUP_OPTIONS * GROUP_OPTION_HEIGHT + 2,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private static int maxPage(int optionCount) {
        return optionCount <= 0 ? 0 : (optionCount - 1) / MAX_ROWS;
    }

    private static MinerFocusTier targetFocusTier(ModuleMiner miner, String oreKey) {
        if (miner.focusTier() == MinerFocusTier.NONE) return MinerFocusTier.I;
        if (!oreKey.equals(miner.focusOreKeyOrNull())) return miner.focusTier();
        return miner.focusTier()
            .next();
    }

    private static String focusLabel(ModuleMiner miner) {
        if (miner.focusTier() == MinerFocusTier.NONE) return "Focus: none";
        int progress = miner.focusAlignmentProgress() * 100 / MinerFocusTier.ALIGNMENT_REQUIRED_TICKS;
        return "Focus: " + miner.focusTier()
            .name() + " " + miner.focusOreKeyOrNull() + " " + progress + "%";
    }

    private enum GroupOptionAction {
        SELECT,
        CREATE
    }

    private record GroupOption(String label, short groupId, GroupOptionAction action) {}
}
