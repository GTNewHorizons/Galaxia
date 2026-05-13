package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class MinerBlacklistConfigModalWidget extends ParentWidget<MinerBlacklistConfigModalWidget> {

    static final int WIDTH = 360;
    static final int HEIGHT = 278;

    private static final int BODY_TOP_OFFSET = 10;
    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + BODY_TOP_OFFSET;
    private static final int GROUP_LABEL_Y = BODY_TOP + 17;
    private static final int GROUP_BUTTON_X = 104;
    private static final int GROUP_BUTTON_Y = BODY_TOP + 14;
    private static final int GROUP_BUTTON_WIDTH = 170;
    private static final int GROUP_BUTTON_HEIGHT = 14;
    private static final int GROUP_OPTION_Y = GROUP_BUTTON_Y + GROUP_BUTTON_HEIGHT + 2;
    private static final int GROUP_OPTION_HEIGHT = 12;
    private static final int GROUP_ICON_BUTTON_SIZE = GROUP_OPTION_HEIGHT;
    private static final int GROUP_OPTION_SELECT_WIDTH = GROUP_BUTTON_WIDTH - GROUP_ICON_BUTTON_SIZE * 2;
    private static final int GROUP_RENAME_BUTTON_X = GROUP_BUTTON_X + GROUP_OPTION_SELECT_WIDTH;
    private static final int GROUP_MEMBERS_BUTTON_X = GROUP_RENAME_BUTTON_X + GROUP_ICON_BUTTON_SIZE;
    private static final int MAX_GROUP_OPTIONS = 10;
    private static final int GROUP_MODAL_WIDTH = 260;
    private static final int GROUP_MODAL_HEIGHT = 102;
    private static final int GROUP_MODAL_X = (WIDTH - GROUP_MODAL_WIDTH) / 2;
    private static final int GROUP_MODAL_Y = 86;
    private static final int GROUP_MODAL_PAD = 10;
    private static final int GROUP_NAME_FIELD_X = GROUP_MODAL_X + GROUP_MODAL_PAD;
    private static final int GROUP_NAME_FIELD_Y = GROUP_MODAL_Y + 38;
    private static final int GROUP_NAME_FIELD_WIDTH = 140;
    private static final int GROUP_NAME_FIELD_HEIGHT = 18;
    private static final int GROUP_SAVE_BUTTON_X = GROUP_NAME_FIELD_X + GROUP_NAME_FIELD_WIDTH + 6;
    private static final int GROUP_EDITOR_BUTTON_WIDTH = 42;
    private static final int GROUP_CANCEL_BUTTON_X = GROUP_SAVE_BUTTON_X + GROUP_EDITOR_BUTTON_WIDTH + 4;
    private static final int GROUP_CANCEL_BUTTON_WIDTH = 50;
    private static final int GROUP_EDITOR_BUTTON_Y = GROUP_NAME_FIELD_Y;
    private static final int GROUP_MEMBER_ROW_Y = GROUP_MODAL_Y + 30;
    private static final int GROUP_MEMBER_ROW_HEIGHT = 13;
    private static final int GROUP_MAX_MEMBER_ROWS = 5;
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
    private GroupNameAction groupNameAction = GroupNameAction.NONE;
    private short editingGroupId;
    private String groupNameInput = "";
    private TextFieldWidget groupNameField;
    private final ItemStack renameIcon = new ItemStack(Items.feather);
    private final ItemStack membersIcon = new ItemStack(Items.paper);

    MinerBlacklistConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        StationTilePickerController tilePickerController) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
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
                        () -> canUseFullGroupOption(optionIndex),
                        () -> groupOptionLabel(optionIndex),
                        () -> selectGroupOption(optionIndex))
                    .pos(GROUP_BUTTON_X, GROUP_OPTION_Y + optionIndex * GROUP_OPTION_HEIGHT)
                    .size(GROUP_BUTTON_WIDTH, GROUP_OPTION_HEIGHT));
            child(
                ModuleConfigModalSupport
                    .button(
                        () -> canUseCompactGroupOption(optionIndex),
                        () -> groupOptionLabel(optionIndex),
                        () -> selectGroupOption(optionIndex))
                    .pos(GROUP_BUTTON_X, GROUP_OPTION_Y + optionIndex * GROUP_OPTION_HEIGHT)
                    .size(GROUP_OPTION_SELECT_WIDTH, GROUP_OPTION_HEIGHT));
            child(
                iconButton(
                    () -> canRenameGroupOption(optionIndex),
                    renameIcon,
                    "Rename group",
                    () -> beginRenameGroup(optionIndex))
                        .pos(GROUP_RENAME_BUTTON_X, GROUP_OPTION_Y + optionIndex * GROUP_OPTION_HEIGHT)
                        .size(GROUP_ICON_BUTTON_SIZE, GROUP_ICON_BUTTON_SIZE));
            child(
                iconButton(
                    () -> canShowGroupMembersOption(optionIndex),
                    membersIcon,
                    "Show group miners",
                    () -> beginShowGroupMembers(optionIndex))
                        .pos(GROUP_MEMBERS_BUTTON_X, GROUP_OPTION_Y + optionIndex * GROUP_OPTION_HEIGHT)
                        .size(GROUP_ICON_BUTTON_SIZE, GROUP_ICON_BUTTON_SIZE));
        }
        groupNameField = createGroupNameField();
        child(
            groupNameField.pos(GROUP_NAME_FIELD_X, GROUP_NAME_FIELD_Y)
                .size(GROUP_NAME_FIELD_WIDTH, GROUP_NAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canSaveGroupName, "Save", this::saveGroupName)
                .pos(GROUP_SAVE_BUTTON_X, GROUP_EDITOR_BUTTON_Y)
                .size(GROUP_EDITOR_BUTTON_WIDTH, GROUP_NAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(this::isGroupOverlayOpen, this::groupCancelButtonLabel, this::cancelGroupNameEdit)
                .pos(GROUP_CANCEL_BUTTON_X, GROUP_EDITOR_BUTTON_Y)
                .size(GROUP_CANCEL_BUTTON_WIDTH, GROUP_NAME_FIELD_HEIGHT));
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
        ModuleConfigModalSupport.drawLine(
            "Settings group:",
            ModuleConfigModalSupport.PANEL_PADDING,
            GROUP_LABEL_Y,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());

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
        if (controller.isMinerSettingsGroupMenuOpen()) {
            drawGroupOptionHint();
        } else {
            cancelGroupNameEdit();
        }
        ModuleConfigModalSupport.drawLine(
            "Page " + (controller.minerBlacklistPage() + 1) + "/" + (maxPage(options.size()) + 1),
            ModuleConfigModalSupport.PANEL_PADDING,
            HEIGHT - 24,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        drawGroupOverlay();
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
        controller.closeMinerSettingsGroupMenu();
        CelestialClient.updateMinerOreBlacklisted(assetId, controller.moduleIndex(), oreKey, blacklisted);
    }

    private void toggleFocusOre(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        ModuleInstance module = selectedModule();
        if (option == null || module == null || !(module.component() instanceof ModuleMiner)) return;
        controller.closeMinerSettingsGroupMenu();
        String targetOreKey = MinerFocusUiModel.oreTargetForClick(module, option.key());
        CelestialClient.setMinerFocusOre(assetId, controller.moduleIndex(), targetOreKey);
    }

    private void selectGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        ModuleInstance module = selectedModule();
        if (option == null || module == null) return;
        if (option.action() == GroupOptionAction.CREATE) {
            beginCreateGroup();
            return;
        }
        if (module.groupId() != option.groupId()) {
            controller.closeMinerSettingsGroupMenu();
            CelestialClient.updateMinerSettingsGroup(assetId, controller.moduleIndex(), option.groupId());
        }
    }

    private boolean canCopySettings() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        return tilePickerController != null && controller.isMinerBlacklistOpen()
            && !controller.isMinerSettingsGroupMenuOpen()
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
        return controller.isMinerBlacklistOpen() && !controller.isMinerSettingsGroupMenuOpen()
            && selectedModule() != null
            && optionAt(rowIndex) != null;
    }

    private boolean canUseFocusRow(int rowIndex) {
        MinerBlacklistOptions.Entry option = optionAt(rowIndex);
        return controller.isMinerBlacklistOpen() && !controller.isMinerSettingsGroupMenuOpen()
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
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Miner Configuration" : ModuleConfigModalSupport.moduleTitle(module, "Configuration");
    }

    private boolean hasMinerSelected() {
        ModuleInstance module = selectedModule();
        return controller.isMinerBlacklistOpen() && module != null && module.component() instanceof ModuleMiner;
    }

    private boolean canUseFullGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && !hasInlineGroupButtons(option);
    }

    private boolean canUseCompactGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && hasInlineGroupButtons(option);
    }

    private boolean canRenameGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && hasInlineGroupButtons(option);
    }

    private boolean canShowGroupMembersOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && hasInlineGroupButtons(option);
    }

    private boolean hasInlineGroupButtons(GroupOption option) {
        return option.action() == GroupOptionAction.SELECT && option.groupId() != 0;
    }

    private String currentGroupButtonLabel() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        if (facility == null || module == null) return "No Group";
        return currentGroupLabel(facility, module);
    }

    private String currentGroupLabel(AutomatedFacility facility, ModuleInstance module) {
        if (module.groupId() == 0) return "No Group";
        SettingsGroup group = facility.settingsGroups()
            .get(module.groupId());
        if (group == null || !isVisibleJoinableGroup(group)) return "No Group";
        return group.displayName();
    }

    private String groupOptionLabel(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return option == null ? "" : option.label();
    }

    private GroupOption groupOptionAt(int optionIndex) {
        List<GroupOption> options = groupOptions();
        return optionIndex >= 0 && optionIndex < options.size() ? options.get(optionIndex) : null;
    }

    private void beginCreateGroup() {
        groupNameAction = GroupNameAction.CREATE;
        editingGroupId = 0;
        groupNameInput = defaultNewGroupName();
        syncGroupNameField();
    }

    private void beginRenameGroup(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        if (option == null || option.groupId() == 0) return;
        groupNameAction = GroupNameAction.RENAME;
        editingGroupId = option.groupId();
        groupNameInput = option.label();
        syncGroupNameField();
    }

    private void beginShowGroupMembers(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        if (option == null || option.groupId() == 0) return;
        groupNameAction = GroupNameAction.MEMBERS;
        editingGroupId = option.groupId();
        groupNameInput = "";
        syncGroupNameField();
    }

    private void saveGroupName() {
        ModuleInstance module = selectedModule();
        String displayName = currentGroupNameInput().trim();
        if (module == null || displayName.isEmpty()) return;
        if (groupNameAction == GroupNameAction.CREATE) {
            CelestialClient.createMinerSettingsGroup(assetId, controller.moduleIndex(), displayName);
        } else if (groupNameAction == GroupNameAction.RENAME) {
            CelestialClient.renameMinerSettingsGroup(assetId, controller.moduleIndex(), editingGroupId, displayName);
        }
        cancelGroupNameEdit();
        controller.closeMinerSettingsGroupMenu();
    }

    private void cancelGroupNameEdit() {
        groupNameAction = GroupNameAction.NONE;
        editingGroupId = 0;
        groupNameInput = "";
        syncGroupNameField();
    }

    private boolean isEditingGroupName() {
        return groupNameAction == GroupNameAction.CREATE || groupNameAction == GroupNameAction.RENAME;
    }

    private boolean isGroupOverlayOpen() {
        return groupNameAction != GroupNameAction.NONE;
    }

    private boolean canSaveGroupName() {
        return controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
            && isEditingGroupName()
            && selectedModule() != null
            && !currentGroupNameInput().trim()
                .isEmpty();
    }

    private String groupCancelButtonLabel() {
        return groupNameAction == GroupNameAction.MEMBERS ? "Close" : "Cancel";
    }

    private String currentGroupNameInput() {
        return groupNameField != null ? groupNameField.getText() : groupNameInput;
    }

    private void syncGroupNameField() {
        if (groupNameField != null) groupNameField.setText(groupNameInput);
    }

    private String defaultNewGroupName() {
        ModuleInstance module = selectedModule();
        if (module == null) return "New Group";
        return module.kind()
            .name() + " Group";
    }

    private TextFieldWidget createGroupNameField() {
        return new TextFieldWidget().setMaxLength(32)
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
            .value(new StringValue.Dynamic(() -> groupNameInput, text -> groupNameInput = text == null ? "" : text))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(
                w -> controller.isMinerBlacklistOpen() && controller.isMinerSettingsGroupMenuOpen()
                    && isEditingGroupName());
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
            .filter(group -> group.kind() == FacilityModuleKind.MINER && isVisibleJoinableGroup(group))
            .sorted(Comparator.comparing(SettingsGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .forEach(group -> options.add(new GroupOption(group.displayName(), group.id(), GroupOptionAction.SELECT)));
        return options;
    }

    private static boolean isVisibleJoinableGroup(SettingsGroup group) {
        return group.isJoinable() && !(group.hasDefaultPrivateDisplayName() && group.members()
            .size() == 1);
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

    private void drawGroupOverlay() {
        if (!isGroupOverlayOpen()) return;
        if (groupNameAction == GroupNameAction.MEMBERS) {
            drawGroupMembersOverlay();
            return;
        }
        String title = groupNameAction == GroupNameAction.CREATE ? "Create Settings Group" : "Rename Settings Group";
        ModuleConfigModalSupport
            .drawFrameAt(title, GROUP_MODAL_X, GROUP_MODAL_Y, GROUP_MODAL_WIDTH, GROUP_MODAL_HEIGHT);
        ModuleConfigModalSupport.drawLine(
            "Group name",
            GROUP_NAME_FIELD_X,
            GROUP_NAME_FIELD_Y - 13,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void drawGroupMembersOverlay() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        SettingsGroup group = facility == null ? null
            : facility.settingsGroups()
                .get(editingGroupId);
        String title = group == null ? "Group Miners"
            : Minecraft.getMinecraft().fontRenderer.trimStringToWidth(group.displayName(), GROUP_MODAL_WIDTH - 28);
        ModuleConfigModalSupport
            .drawFrameAt(title, GROUP_MODAL_X, GROUP_MODAL_Y, GROUP_MODAL_WIDTH, GROUP_MODAL_HEIGHT);
        if (facility == null || group == null) {
            ModuleConfigModalSupport.drawLine(
                "Group no longer exists",
                GROUP_MODAL_X + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        List<ModuleInstance> members = group.members()
            .stream()
            .map(
                coord -> facility.stationLayout() == null ? null
                    : facility.stationLayout()
                        .moduleAt(coord))
            .filter(module -> module != null && module.component() instanceof ModuleMiner)
            .sorted(
                Comparator.comparingInt(
                    (ModuleInstance module) -> module.anchor()
                        .dx())
                    .thenComparingInt(
                        module -> module.anchor()
                            .dy()))
            .toList();
        if (members.isEmpty()) {
            ModuleConfigModalSupport.drawLine(
                "No miners in this group",
                GROUP_MODAL_X + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        int rows = Math.min(members.size(), GROUP_MAX_MEMBER_ROWS);
        for (int i = 0; i < rows; i++) {
            ModuleInstance member = members.get(i);
            String selected = member.id.equals(controller.moduleId()) ? "* " : "";
            String text = selected + "Miner ("
                + (int) member.anchor()
                    .dx()
                + ","
                + (int) member.anchor()
                    .dy()
                + ")";
            ModuleConfigModalSupport.drawLine(
                text,
                GROUP_MODAL_X + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y + i * GROUP_MEMBER_ROW_HEIGHT,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
        if (members.size() > rows) {
            ModuleConfigModalSupport.drawLine(
                "+" + (members.size() - rows) + " more",
                GROUP_MODAL_X + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y + rows * GROUP_MEMBER_ROW_HEIGHT,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
    }

    private static ButtonWidget<?> iconButton(BooleanSupplier enabledSupplier, ItemStack icon, String tooltip,
        Runnable onClick) {
        return new ButtonWidget<>()
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> drawIconButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), false)))
            .hoverBackground(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> drawIconButtonBackground(x, y, w, h, enabledSupplier.getAsBoolean(), true)))
            .overlay(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (!enabledSupplier.getAsBoolean()) return;
                renderItemIconScaled(icon, x + 2, y + 2, 0.5f);
            }))
            .tooltipDynamic(t -> { if (enabledSupplier.getAsBoolean()) t.addLine(tooltip); })
            .onUpdateListener(ButtonWidget::markTooltipDirty, true)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !enabledSupplier.getAsBoolean()) return false;
                onClick.run();
                return true;
            })
            .setEnabledIf(w -> enabledSupplier.getAsBoolean());
    }

    private static void drawIconButtonBackground(int x, int y, int w, int h, boolean enabled, boolean hovered) {
        if (!enabled) return;
        BorderedRect.draw(
            x,
            y,
            w,
            h,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
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

    private static void renderItemIconScaled(ItemStack stack, int x, int y, float scale) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glTranslatef(x, y, 0);
        org.lwjgl.opengl.GL11.glScalef(scale, scale, 1f);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 260f;
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        org.lwjgl.opengl.GL11.glPopMatrix();
    }

    private enum GroupOptionAction {
        SELECT,
        CREATE
    }

    private enum GroupNameAction {
        NONE,
        CREATE,
        RENAME,
        MEMBERS
    }

    private record GroupOption(String label, short groupId, GroupOptionAction action) {}
}
