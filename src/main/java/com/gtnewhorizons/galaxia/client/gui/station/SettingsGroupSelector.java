package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.ObjectValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

/**
 * Row of a module window choosing the settings group the module shares its settings with, with dialogs to create,
 * rename and list the members of a group
 */
final class SettingsGroupSelector {

    static final String DIALOG_ID = "station.config.group";

    private static final int SELECT_WIDTH = 96;
    private static final int MENU_WIDTH = 170;
    private static final int NAME_WIDTH = 140;
    private static final int MEMBERS_HEIGHT = 80;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final Supplier<FacilityModuleKind> kindSupplier;
    private final Runnable ownerChangeSubmitted;
    private final DropdownWidget<GroupOption, ?> dropdown;
    private List<GroupOption> dropdownOptions;
    private SettingsGroup.ID editingGroupId;
    private String groupName = "";
    private TextFieldWidget nameField;

    SettingsGroupSelector(CelestialAsset.ID assetId, ModuleConfigModalController controller, FacilityModuleKind kind) {
        this(assetId, controller, () -> kind, () -> {});
    }

    SettingsGroupSelector(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        Supplier<FacilityModuleKind> kindSupplier, Runnable ownerChangeSubmitted) {
        this.assetId = assetId;
        this.controller = controller;
        this.kindSupplier = kindSupplier;
        this.ownerChangeSubmitted = ownerChangeSubmitted;
        dropdownOptions = groupOptions();
        dropdown = new GroupDropdown();
        dropdown.value(new ObjectValue.Dynamic<>(GroupOption.class, this::selectedOption, this::select))
            .options(dropdownOptions)
            .maxVerticalMenuSize(112)
            .optionToWidget(
                (option,
                    selected) -> new TextWidget<>(
                        selected ? IKey.dynamic(this::currentGroupName) : IKey.dynamic(() -> groupOptionLabel(option)))
                            .color(GalaxiaGuiStyle.TEXT)
                            .name(
                                selected ? "settings.group.selected" : option.create() ? "settings.group.create" : null)
                            .alignment(com.cleanroommc.modularui.utils.Alignment.Center)
                            .widthRel(1f)
                            .height(GalaxiaUi.BUTTON_H))
            .background(EnumTextures.GUI_BUTTON.getImage())
            .hoverBackground(EnumTextures.GUI_BUTTON_HOVERED.getImage())
            .name("settings.group.selector")
            .size(SELECT_WIDTH, GalaxiaUi.BUTTON_H);
    }

    /** The row to put in a module window; it hides itself for modules that have no groups */
    Flow row() {
        Flow row = GalaxiaUi.row()
            .child(GalaxiaUi.dim(tr("galaxia.gui.station.settings_group.label")))
            .child(dropdown)
            .child(
                GalaxiaUi.button(
                    tr("galaxia.gui.station.settings_group.rename"),
                    () -> currentGroup() != null,
                    this::editName))
            .child(
                GalaxiaUi.button(
                    tr("galaxia.gui.station.settings_group.members"),
                    () -> currentGroup() != null,
                    this::showMembers));
        row.setEnabledIf(w -> available());
        return row;
    }

    /** Follows groups made or removed elsewhere; called every tick by the owning window */
    void update() {
        List<GroupOption> currentOptions = groupOptions();
        if (currentOptions.equals(dropdownOptions)) return;
        dropdownOptions = currentOptions;
        dropdown.closeMenu(false);
        dropdown.clearOptions()
            .options(currentOptions);
        dropdown.deleteMenu();
    }

    /** While the group menu is open the module's own controls wait */
    boolean isBlockingModuleControls() {
        return dropdown.isOpen();
    }

    void closeMenu() {
        dropdown.closeMenu(false);
        closeDialog();
    }

    private void select(GroupOption option) {
        ModuleInstance module = module();
        if (module == null) return;
        if (option.create()) {
            editingGroupId = null;
            groupName = StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.settings_group.default_name",
                kind() != null ? kind().name() : "Module");
            openNameDialog("galaxia.gui.station.settings_group.create_title");
            return;
        }
        if (Objects.equals(currentGroupId(module), option.groupId())) return;
        CelestialClient.updateModuleSettingsGroup(assetId, controller.moduleId(), option.groupId());
        ownerChangeSubmitted.run();
    }

    private void editName() {
        SettingsGroup group = currentGroup();
        if (group == null) return;
        editingGroupId = group.id();
        groupName = group.displayName();
        openNameDialog("galaxia.gui.station.settings_group.rename_title");
    }

    private void openNameDialog(String titleKey) {
        WindowLayer layer = WindowLayer.of(dropdown);
        if (layer == null) return;
        layer.close(DIALOG_ID);
        layer.open(
            WindowSpec.of(DIALOG_ID, titleKey)
                .owner(ModuleConfigModalController.WINDOW_ID)
                .modal()
                .centered()
                .body(this::fillNameDialog));
    }

    private void fillNameDialog(GalaxiaUi.Column column) {
        nameField = GalaxiaUi.textField(null, NAME_WIDTH)
            .setMaxLength(32)
            .autoUpdateOnChange(false)
            .value(new StringValue.Dynamic(() -> groupName, value -> groupName = value == null ? "" : value))
            .setFocusOnGuiOpen(false);
        nameField.name("settings.group.name");
        column.dim(tr("galaxia.gui.station.settings_group.name"))
            .add(nameField);
        Flow footer = GalaxiaUi.footer(
            tr("galaxia.gui.common.cancel"),
            this::closeDialog,
            tr("galaxia.gui.station.settings_group.save"),
            this::canSave,
            this::saveName);
        ((Widget<?>) footer.getChildren()
            .get(0)).name("settings.group.cancel");
        ((Widget<?>) footer.getChildren()
            .get(1)).name("settings.group.save");
        column.add(footer);
    }

    private void showMembers() {
        SettingsGroup group = currentGroup();
        WindowLayer layer = WindowLayer.of(dropdown);
        if (group == null || layer == null) return;
        editingGroupId = group.id();
        layer.close(DIALOG_ID);
        layer.open(
            WindowSpec.of(DIALOG_ID, IKey.str(group.displayName()))
                .owner(ModuleConfigModalController.WINDOW_ID)
                .modal()
                .centered()
                .onClose(() -> editingGroupId = null)
                .body(column -> {
                    ListWidget<IWidget, ?> members = GalaxiaUi.scroll(MEMBERS_HEIGHT);
                    Flow list = GalaxiaUi.column();
                    for (String label : memberLabels()) list.child(GalaxiaUi.text(label));
                    members.child(list);
                    column.add(members);
                }));
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
        boolean ownerChanging = editingGroupId == null;
        if (ownerChanging) CelestialClient.createModuleSettingsGroup(assetId, controller.moduleId(), displayName);
        else CelestialClient.renameModuleSettingsGroup(assetId, editingGroupId, displayName);
        closeDialog();
        if (ownerChanging) ownerChangeSubmitted.run();
        else dropdown.deleteMenu();
    }

    private void closeDialog() {
        editingGroupId = null;
        groupName = "";
        nameField = null;
        WindowLayer layer = WindowLayer.of(dropdown);
        if (layer != null) layer.close(DIALOG_ID);
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
        if (facility == null || editingGroupId == null) return List.of(tr("galaxia.gui.station.settings_group.gone"));
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
                module -> StatCollector.translateToLocalFormatted(
                    module.id.equals(controller.moduleId()) ? "galaxia.gui.station.settings_group.member_this"
                        : "galaxia.gui.station.settings_group.member",
                    module.kind()
                        .getDisplayName(),
                    module.anchor()
                        .dx(),
                    module.anchor()
                        .dy()))
            .toList();
        return labels.isEmpty() ? List.of(tr("galaxia.gui.station.settings_group.empty")) : labels;
    }

    private GroupOption selectedOption() {
        ModuleInstance module = module();
        return new GroupOption(module == null ? null : currentGroupId(module), false);
    }

    private static SettingsGroup.ID currentGroupId(ModuleInstance module) {
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
        return group == null ? tr("galaxia.gui.station.settings_group.none") : group.displayName();
    }

    private String groupOptionLabel(GroupOption option) {
        if (option.create()) return tr("galaxia.gui.station.settings_group.create");
        AutomatedFacility facility = facility();
        SettingsGroup group = option.groupId() == null || facility == null ? null
            : facility.settingsGroup(option.groupId());
        return group == null ? tr("galaxia.gui.station.settings_group.none") : group.displayName();
    }

    private boolean available() {
        FacilityModuleKind kind = kind();
        return module() != null && kind != null
            && FacilityModuleRegistry.get(kind)
                .settingsGroups();
    }

    private FacilityModuleKind kind() {
        return kindSupplier.get();
    }

    private ModuleInstance module() {
        ModuleInstance module = StationUiData.module(assetId, controller.moduleId());
        return module != null && module.kind() == kind() ? module : null;
    }

    private AutomatedFacility facility() {
        return StationUiData.facility(assetId);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static final class GroupDropdown extends DropdownWidget<GroupOption, GroupDropdown> {

        GroupDropdown() {
            // Panel handlers outlive replaced configuration widgets within the station screen.
            super("module_settings_group_" + UUID.randomUUID(), GroupOption.class);
        }

        @Override
        protected Menu<?> createMenu() {
            return super.createMenu().width(MENU_WIDTH)
                .background(EnumTextures.GUI_PANEL.getImage());
        }
    }

    private record GroupOption(SettingsGroup.ID groupId, boolean create) {}
}
