package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class ModuleSettingsGroupSelectorWidget extends ParentWidget<ModuleSettingsGroupSelectorWidget> {

    static final int GROUP_LABEL_Y = 50;
    static final int GROUP_BUTTON_X = 104;
    static final int GROUP_BUTTON_Y = 47;
    static final int GROUP_BUTTON_WIDTH = 170;
    static final int GROUP_BUTTON_HEIGHT = 14;

    private static final int ACTION_WIDTH = 44;
    private static final int SELECT_WIDTH = GROUP_BUTTON_WIDTH - ACTION_WIDTH * 2;
    private static final int MODAL_WIDTH = 260;
    private static final int MODAL_Y = 86;
    private static final int MODAL_PAD = 10;
    private static final int FIELD_HEIGHT = 18;
    private static final int NAME_WIDTH = 140;
    private static final int MEMBER_ROW_HEIGHT = 13;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final Supplier<FacilityModuleKind> kindSupplier;
    private final BooleanSupplier openSupplier;
    private final Runnable ownerChangeSubmitted;
    private final DropdownWidget<GroupOption, ?> dropdown;
    private final ParentWidget<?> overlay = new ParentWidget<>();
    private List<GroupOption> dropdownOptions;
    private GroupNameAction action = GroupNameAction.NONE;
    private SettingsGroup.ID editingGroupId;
    private String groupName = "";
    private TextFieldWidget nameField;

    ModuleSettingsGroupSelectorWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        FacilityModuleKind kind, BooleanSupplier openSupplier, int modalWidth) {
        this(assetId, controller, () -> kind, openSupplier, modalWidth, () -> {});
    }

    ModuleSettingsGroupSelectorWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        Supplier<FacilityModuleKind> kindSupplier, BooleanSupplier openSupplier, int modalWidth,
        Runnable ownerChangeSubmitted) {
        this.assetId = assetId;
        this.controller = controller;
        this.kindSupplier = kindSupplier;
        this.openSupplier = openSupplier;
        this.ownerChangeSubmitted = ownerChangeSubmitted;

        child(
            text("Settings group:", EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .pos(ModuleConfigModalSupport.PANEL_PADDING, GROUP_LABEL_Y)
                .size(GROUP_BUTTON_X - ModuleConfigModalSupport.PANEL_PADDING, GROUP_BUTTON_HEIGHT)
                .setEnabledIf(w -> available()));

        dropdownOptions = groupOptions();
        dropdown = new DropdownWidget<>("module_settings_group", GroupOption.class);
        dropdown.value(new ObjectValue.Dynamic<>(GroupOption.class, this::selectedOption, this::select))
            .options(dropdownOptions)
            .maxVerticalMenuSize(112)
            .optionToWidget(
                (option,
                    selected) -> new TextWidget<>(
                        selected ? IKey.dynamic(this::currentGroupName) : IKey.dynamic(() -> groupOptionLabel(option)))
                            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .widthRel(1f)
                            .heightRel(1f))
            .setEnabledIf(w -> available() && action == GroupNameAction.NONE)
            .pos(GROUP_BUTTON_X, GROUP_BUTTON_Y)
            .size(SELECT_WIDTH, GROUP_BUTTON_HEIGHT);
        child(dropdown);
        child(
            currentGroupButton("Rename", this::editName).pos(GROUP_BUTTON_X + SELECT_WIDTH, GROUP_BUTTON_Y)
                .size(ACTION_WIDTH, GROUP_BUTTON_HEIGHT));
        child(
            currentGroupButton("Members", this::showMembers)
                .pos(GROUP_BUTTON_X + SELECT_WIDTH + ACTION_WIDTH, GROUP_BUTTON_Y)
                .size(ACTION_WIDTH, GROUP_BUTTON_HEIGHT));

        child(
            new ButtonWidget<>().onMousePressed(button -> button == 0)
                .setEnabledIf(w -> action != GroupNameAction.NONE)
                .pos(0, 0)
                .size(modalWidth, MODAL_Y + 132));
        child(
            overlay.pos((modalWidth - MODAL_WIDTH) / 2, MODAL_Y)
                .size(MODAL_WIDTH, 132)
                .setEnabledIf(w -> open() && action != GroupNameAction.NONE));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        List<GroupOption> currentOptions = groupOptions();
        if (currentOptions.equals(dropdownOptions)) return;
        dropdownOptions = currentOptions;
        dropdown.closeMenu(false);
        dropdown.clearOptions()
            .options(currentOptions);
        dropdown.deleteMenu();
    }

    boolean isBlockingModuleControls() {
        return dropdown.isOpen() || action != GroupNameAction.NONE;
    }

    void closeMenu() {
        dropdown.closeMenu(false);
        closeOverlay();
    }

    private ButtonWidget<?> currentGroupButton(String label, Runnable click) {
        return ModuleConfigModalSupport
            .button(() -> open() && currentGroup() != null && action == GroupNameAction.NONE, label, click);
    }

    private void select(GroupOption option) {
        ModuleInstance module = module();
        if (module == null) return;
        if (option.create()) {
            action = GroupNameAction.CREATE;
            groupName = (kind() != null ? kind().name() : "Module") + " Group";
            buildNameOverlay("Create Settings Group");
            return;
        }
        SettingsGroup.ID current = currentGroupId(module);
        if (java.util.Objects.equals(current, option.groupId())) return;
        CelestialClient.updateModuleSettingsGroup(assetId, controller.moduleId(), option.groupId());
        ownerChangeSubmitted.run();
    }

    private void editName() {
        SettingsGroup group = currentGroup();
        if (group == null) return;
        action = GroupNameAction.RENAME;
        editingGroupId = group.id();
        groupName = group.displayName();
        buildNameOverlay("Rename Settings Group");
    }

    private void showMembers() {
        SettingsGroup group = currentGroup();
        if (group == null) return;
        action = GroupNameAction.MEMBERS;
        editingGroupId = group.id();
        overlay.removeAll();
        overlay.background(background(EnumColors.MAP_COLOR_MODAL_BG.getColor()));
        overlay.child(
            text(group.displayName(), EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(MODAL_PAD, 4)
                .size(MODAL_WIDTH - MODAL_PAD * 2, 16));

        List<String> labels = memberLabels();
        ParentWidget<?> rows = new ParentWidget<>().widthRel(1f);
        for (int i = 0; i < labels.size(); i++) {
            rows.child(
                text(labels.get(i), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(0, i * MEMBER_ROW_HEIGHT)
                    .size(MODAL_WIDTH - MODAL_PAD * 2, MEMBER_ROW_HEIGHT));
        }
        int height = Math.max(76, labels.size() * MEMBER_ROW_HEIGHT);
        rows.height(height);
        VerticalScrollData scrollData = new VerticalScrollData();
        scrollData.setScrollSize(height);
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(MODAL_PAD, 25)
            .size(MODAL_WIDTH - MODAL_PAD * 2, 76)
            .background(background(EnumColors.MAP_COLOR_SCROLL_BG.getColor()));
        scroll.child(rows);
        overlay.child(scroll);
        overlay.child(
            ModuleConfigModalSupport.button(() -> action == GroupNameAction.MEMBERS, "Close", this::closeOverlay)
                .pos(MODAL_WIDTH - MODAL_PAD - 50, 104)
                .size(50, FIELD_HEIGHT));
        overlay.scheduleResize();
    }

    private void buildNameOverlay(String title) {
        overlay.removeAll();
        overlay.background(background(EnumColors.MAP_COLOR_MODAL_BG.getColor()));
        overlay.child(
            text(title, EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(MODAL_PAD, 4)
                .size(MODAL_WIDTH - MODAL_PAD * 2, 16));
        overlay.child(
            text("Group name", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(MODAL_PAD, 25)
                .size(NAME_WIDTH, 12));
        nameField = new TextFieldWidget().setMaxLength(32)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .background(background(EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor()))
            .value(new StringValue.Dynamic(() -> groupName, value -> groupName = value == null ? "" : value))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> action == GroupNameAction.CREATE || action == GroupNameAction.RENAME);
        overlay.child(
            nameField.pos(MODAL_PAD, 38)
                .size(NAME_WIDTH, FIELD_HEIGHT));
        int saveX = MODAL_PAD + NAME_WIDTH + 6;
        overlay.child(
            ModuleConfigModalSupport.button(this::canSave, "Save", this::saveName)
                .pos(saveX, 38)
                .size(42, FIELD_HEIGHT));
        overlay.child(
            ModuleConfigModalSupport.button(() -> action != GroupNameAction.NONE, "Cancel", this::closeOverlay)
                .pos(saveX + 46, 38)
                .size(50, FIELD_HEIGHT));
        overlay.scheduleResize();
    }

    private boolean canSave() {
        return module() != null && nameField != null
            && !nameField.getText()
                .trim()
                .isEmpty();
    }

    private void saveName() {
        if (!canSave()) return;
        String displayName = nameField.getText()
            .trim();
        boolean ownerChanging = action == GroupNameAction.CREATE;
        if (ownerChanging) CelestialClient.createModuleSettingsGroup(assetId, controller.moduleId(), displayName);
        else CelestialClient.renameModuleSettingsGroup(assetId, editingGroupId, displayName);
        closeOverlay();
        if (ownerChanging) ownerChangeSubmitted.run();
        else dropdown.deleteMenu();
    }

    private void closeOverlay() {
        action = GroupNameAction.NONE;
        editingGroupId = null;
        groupName = "";
        nameField = null;
        overlay.removeAll();
    }

    private List<GroupOption> groupOptions() {
        AutomatedFacility facility = facility();
        FacilityModuleKind kind = kind();
        if (facility == null || kind == null) return List.of();
        List<GroupOption> options = new ArrayList<>();
        options.add(new GroupOption(null, false));
        options.add(new GroupOption(null, true));
        facility.settingsGroups()
            .stream()
            .filter(group -> group.kind() == kind)
            .sorted(Comparator.comparing(SettingsGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .forEach(group -> options.add(new GroupOption(group.id(), false)));
        return options;
    }

    private List<String> memberLabels() {
        AutomatedFacility facility = facility();
        if (facility == null || editingGroupId == null) return List.of("Group no longer exists");
        List<String> labels = facility.settingsGroupMembers(editingGroupId)
            .stream()
            .sorted(
                Comparator.comparingInt(
                    (ModuleInstance module) -> module.anchor()
                        .dx())
                    .thenComparingInt(
                        module -> module.anchor()
                            .dy()))
            .map(
                module -> (module.id.equals(controller.moduleId()) ? "* " : "") + module.kind()
                    .getDisplayName()
                    + " ("
                    + module.anchor()
                        .dx()
                    + ","
                    + module.anchor()
                        .dy()
                    + ")")
            .toList();
        return labels.isEmpty() ? List.of("No modules in this group") : labels;
    }

    private GroupOption selectedOption() {
        ModuleInstance module = module();
        return new GroupOption(module == null ? null : currentGroupId(module), false);
    }

    private SettingsGroup.ID currentGroupId(ModuleInstance module) {
        return module.settingsBinding() instanceof ModuleInstance.SettingsBinding.Shared shared ? shared.groupId()
            : null;
    }

    private SettingsGroup currentGroup() {
        AutomatedFacility facility = facility();
        ModuleInstance module = module();
        SettingsGroup.ID id = module == null ? null : currentGroupId(module);
        return facility == null || id == null ? null : facility.settingsGroup(id);
    }

    private String currentGroupName() {
        SettingsGroup group = currentGroup();
        return group == null ? "No Group" : group.displayName();
    }

    private String groupOptionLabel(GroupOption option) {
        if (option.create()) return "Create New Group";
        AutomatedFacility facility = facility();
        SettingsGroup group = option.groupId() == null || facility == null ? null
            : facility.settingsGroup(option.groupId());
        return group == null ? "No Group" : group.displayName();
    }

    private boolean available() {
        FacilityModuleKind kind = kind();
        return open() && module() != null
            && kind != null
            && FacilityModuleRegistry.get(kind)
                .settingsGroups();
    }

    private boolean open() {
        return openSupplier.getAsBoolean();
    }

    private FacilityModuleKind kind() {
        return kindSupplier.get();
    }

    private ModuleInstance module() {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, controller.moduleId());
        return module != null && module.kind() == kind() ? module : null;
    }

    private AutomatedFacility facility() {
        return ModuleConfigModalSupport.facility(assetId);
    }

    private static TextWidget<?> text(String value, int color) {
        return new TextWidget<>(IKey.str(value)).color(color);
    }

    private static com.cleanroommc.modularui.api.drawable.IDrawable background(int color) {
        return (ctx, x, y, w, h, ignoredTheme) -> BorderedRect
            .draw(x, y, w, h, color, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
    }

    private record GroupOption(SettingsGroup.ID groupId, boolean create) {}

    private enum GroupNameAction {
        NONE,
        CREATE,
        RENAME,
        MEMBERS
    }
}
