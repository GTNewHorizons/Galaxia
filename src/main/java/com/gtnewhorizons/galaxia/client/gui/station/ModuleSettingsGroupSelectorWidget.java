package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.Interactable;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class ModuleSettingsGroupSelectorWidget extends ParentWidget<ModuleSettingsGroupSelectorWidget>
    implements Interactable {

    static final int GROUP_LABEL_Y = 50;
    static final int GROUP_BUTTON_X = 104;
    static final int GROUP_BUTTON_Y = 47;
    static final int GROUP_BUTTON_WIDTH = 170;
    static final int GROUP_BUTTON_HEIGHT = 14;

    private static final int GROUP_OPTION_Y = GROUP_BUTTON_Y + GROUP_BUTTON_HEIGHT;
    private static final int GROUP_OPTION_HEIGHT = 12;
    private static final int GROUP_ICON_BUTTON_SIZE = GROUP_OPTION_HEIGHT;
    private static final int GROUP_OPTION_SELECT_WIDTH = GROUP_BUTTON_WIDTH - GROUP_ICON_BUTTON_SIZE * 2;
    private static final int GROUP_RENAME_BUTTON_X = GROUP_BUTTON_X + GROUP_OPTION_SELECT_WIDTH;
    private static final int GROUP_MEMBERS_BUTTON_X = GROUP_RENAME_BUTTON_X + GROUP_ICON_BUTTON_SIZE;
    private static final int MAX_GROUP_OPTIONS = 10;
    private static final int GROUP_MODAL_WIDTH = 260;
    private static final int GROUP_MODAL_HEIGHT = 102;
    private static final int GROUP_MODAL_Y = 86;
    private static final int GROUP_MODAL_PAD = 10;
    private static final int GROUP_NAME_FIELD_Y = GROUP_MODAL_Y + 38;
    private static final int GROUP_NAME_FIELD_WIDTH = 140;
    private static final int GROUP_NAME_FIELD_HEIGHT = 18;
    private static final int GROUP_EDITOR_BUTTON_WIDTH = 42;
    private static final int GROUP_CANCEL_BUTTON_WIDTH = 50;
    private static final int GROUP_EDITOR_BUTTON_Y = GROUP_NAME_FIELD_Y;
    private static final int GROUP_MEMBER_ROW_Y = GROUP_MODAL_Y + 30;
    private static final int GROUP_MEMBER_ROW_HEIGHT = 13;
    private static final int GROUP_MAX_MEMBER_ROWS = 5;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final Supplier<FacilityModuleKind> kindSupplier;
    private final BooleanSupplier openSupplier;
    private final int modalWidth;
    private GroupNameAction groupNameAction = GroupNameAction.NONE;
    private short editingGroupId;
    private String groupNameInput = "";
    private TextFieldWidget groupNameField;
    private final ItemStack renameIcon = new ItemStack(Items.feather);
    private final ItemStack membersIcon = new ItemStack(Items.paper);

    ModuleSettingsGroupSelectorWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        FacilityModuleKind kind, BooleanSupplier openSupplier, int modalWidth) {
        this(assetId, controller, () -> kind, openSupplier, modalWidth);
    }

    ModuleSettingsGroupSelectorWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        Supplier<FacilityModuleKind> kindSupplier, BooleanSupplier openSupplier, int modalWidth) {
        this.assetId = assetId;
        this.controller = controller;
        this.kindSupplier = kindSupplier;
        this.openSupplier = openSupplier;
        this.modalWidth = modalWidth;

        child(
            ModuleConfigModalSupport
                .button(this::hasModuleSelected, this::currentGroupButtonLabel, controller::toggleSettingsGroupMenu)
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
                    "Show group modules",
                    () -> beginShowGroupMembers(optionIndex))
                        .pos(GROUP_MEMBERS_BUTTON_X, GROUP_OPTION_Y + optionIndex * GROUP_OPTION_HEIGHT)
                        .size(GROUP_ICON_BUTTON_SIZE, GROUP_ICON_BUTTON_SIZE));
        }
        groupNameField = createGroupNameField();
        child(
            groupNameField.pos(groupNameFieldX(), GROUP_NAME_FIELD_Y)
                .size(GROUP_NAME_FIELD_WIDTH, GROUP_NAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canSaveGroupName, "Save", this::saveGroupName)
                .pos(groupSaveButtonX(), GROUP_EDITOR_BUTTON_Y)
                .size(GROUP_EDITOR_BUTTON_WIDTH, GROUP_NAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(this::isGroupOverlayOpen, this::groupCancelButtonLabel, this::cancelGroupNameEdit)
                .pos(groupCancelButtonX(), GROUP_EDITOR_BUTTON_Y)
                .size(GROUP_CANCEL_BUTTON_WIDTH, GROUP_NAME_FIELD_HEIGHT));
    }

    @Override
    public boolean canHover() {
        return isBlockingModuleControls();
    }

    @Override
    public boolean canHoverThrough() {
        return !isBlockingModuleControls();
    }

    @Override
    public boolean canClickThrough() {
        return !isBlockingModuleControls();
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton != 0 || !controller.isSettingsGroupMenuOpen() || isGroupOverlayOpen()) {
            return Interactable.Result.IGNORE;
        }
        if (isMouseInGroupButtonOrMenu()) return Interactable.Result.IGNORE;
        controller.closeSettingsGroupMenu();
        return Interactable.Result.SUCCESS;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!isOpen() || selectedModule() == null) return;
        if (!controller.isSettingsGroupMenuOpen() && isGroupOverlayOpen()) {
            cancelGroupNameEdit();
        }
        ModuleConfigModalSupport.drawLine(
            "Settings group:",
            ModuleConfigModalSupport.PANEL_PADDING,
            GROUP_LABEL_Y,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        if (controller.isSettingsGroupMenuOpen()) {
            drawGroupOptionHint();
        }
        drawGroupOverlay();
    }

    boolean isBlockingModuleControls() {
        return controller.isSettingsGroupMenuOpen() || isGroupOverlayOpen();
    }

    void closeMenu() {
        controller.closeSettingsGroupMenu();
        cancelGroupNameEdit();
    }

    private boolean isOpen() {
        return openSupplier.getAsBoolean();
    }

    private ModuleInstance selectedModule() {
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, controller.moduleId());
        FacilityModuleKind kind = kind();
        return module != null && module.kind() == kind ? module : null;
    }

    private boolean hasModuleSelected() {
        FacilityModuleKind kind = kind();
        if (kind == null) return false;
        return isOpen() && selectedModule() != null
            && FacilityModuleRegistry.get(kind)
                .settingsGroups();
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
            controller.closeSettingsGroupMenu();
            CelestialClient.updateModuleSettingsGroup(assetId, controller.moduleIndex(), option.groupId());
        }
    }

    private boolean canUseFullGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return isOpen() && controller.isSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && !hasInlineGroupButtons(option);
    }

    private boolean canUseCompactGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return isOpen() && controller.isSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && hasInlineGroupButtons(option);
    }

    private boolean canRenameGroupOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return isOpen() && controller.isSettingsGroupMenuOpen()
            && !isGroupOverlayOpen()
            && option != null
            && hasInlineGroupButtons(option);
    }

    private boolean canShowGroupMembersOption(int optionIndex) {
        GroupOption option = groupOptionAt(optionIndex);
        return isOpen() && controller.isSettingsGroupMenuOpen()
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

    private boolean isMouseInGroupButtonOrMenu() {
        int localX = getContext().getMouseX() - getArea().rx;
        int localY = getContext().getMouseY() - getArea().ry;
        if (contains(localX, localY, GROUP_BUTTON_X, GROUP_BUTTON_Y, GROUP_BUTTON_WIDTH, GROUP_BUTTON_HEIGHT)) {
            return true;
        }
        int optionCount = Math.min(groupOptions().size(), MAX_GROUP_OPTIONS);
        int optionHeight = optionCount * GROUP_OPTION_HEIGHT;
        return optionCount > 0
            && contains(localX, localY, GROUP_BUTTON_X, GROUP_OPTION_Y, GROUP_BUTTON_WIDTH, optionHeight);
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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
            CelestialClient.createModuleSettingsGroup(assetId, controller.moduleIndex(), displayName);
        } else if (groupNameAction == GroupNameAction.RENAME) {
            CelestialClient.renameModuleSettingsGroup(assetId, controller.moduleIndex(), editingGroupId, displayName);
        }
        cancelGroupNameEdit();
        controller.closeSettingsGroupMenu();
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
        return isOpen() && controller.isSettingsGroupMenuOpen()
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
        FacilityModuleKind kind = kind();
        return (kind != null ? kind.name() : "Module") + " Group";
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
            .setEnabledIf(w -> isOpen() && controller.isSettingsGroupMenuOpen() && isEditingGroupName());
    }

    private List<GroupOption> groupOptions() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        FacilityModuleKind kind = kind();
        if (facility == null || module == null
            || kind == null
            || !FacilityModuleRegistry.get(kind)
                .settingsGroups())
            return List.of();
        List<GroupOption> options = new ArrayList<>();
        options.add(new GroupOption("No Group", (short) 0, GroupOptionAction.SELECT));
        options.add(new GroupOption("Create New Group", (short) 0, GroupOptionAction.CREATE));
        facility.settingsGroups()
            .groups()
            .values()
            .stream()
            .filter(group -> group.kind() == kind && isVisibleJoinableGroup(group))
            .sorted(Comparator.comparing(SettingsGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .forEach(group -> options.add(new GroupOption(group.displayName(), group.id(), GroupOptionAction.SELECT)));
        return options;
    }

    private FacilityModuleKind kind() {
        return kindSupplier.get();
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
            .drawFrameAt(title, groupModalX(), GROUP_MODAL_Y, GROUP_MODAL_WIDTH, GROUP_MODAL_HEIGHT);
        ModuleConfigModalSupport.drawLine(
            "Group name",
            groupNameFieldX(),
            GROUP_NAME_FIELD_Y - 13,
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void drawGroupMembersOverlay() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        SettingsGroup group = facility == null ? null
            : facility.settingsGroups()
                .get(editingGroupId);
        String title = group == null ? "Group Modules"
            : Minecraft.getMinecraft().fontRenderer.trimStringToWidth(group.displayName(), GROUP_MODAL_WIDTH - 28);
        int modalX = groupModalX();
        ModuleConfigModalSupport.drawFrameAt(title, modalX, GROUP_MODAL_Y, GROUP_MODAL_WIDTH, GROUP_MODAL_HEIGHT);
        if (facility == null || group == null) {
            ModuleConfigModalSupport.drawLine(
                "Group no longer exists",
                modalX + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        FacilityModuleKind kind = kind();
        List<ModuleInstance> members = group.members()
            .stream()
            .map(
                coord -> facility.stationLayout() == null ? null
                    : facility.stationLayout()
                        .moduleAt(coord))
            .filter(module -> module != null && module.kind() == kind)
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
                "No modules in this group",
                modalX + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        int rows = Math.min(members.size(), GROUP_MAX_MEMBER_ROWS);
        for (int i = 0; i < rows; i++) {
            ModuleInstance member = members.get(i);
            String selected = member.id.equals(controller.moduleId()) ? "* " : "";
            String text = selected + member.kind()
                .getDisplayName()
                + " ("
                + member.anchor()
                    .dx()
                + ","
                + member.anchor()
                    .dy()
                + ")";
            ModuleConfigModalSupport.drawLine(
                text,
                modalX + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y + i * GROUP_MEMBER_ROW_HEIGHT,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
        if (members.size() > rows) {
            ModuleConfigModalSupport.drawLine(
                "+" + (members.size() - rows) + " more",
                modalX + GROUP_MODAL_PAD,
                GROUP_MEMBER_ROW_Y + rows * GROUP_MEMBER_ROW_HEIGHT,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
    }

    private int groupModalX() {
        return (modalWidth - GROUP_MODAL_WIDTH) / 2;
    }

    private int groupNameFieldX() {
        return groupModalX() + GROUP_MODAL_PAD;
    }

    private int groupSaveButtonX() {
        return groupNameFieldX() + GROUP_NAME_FIELD_WIDTH + 6;
    }

    private int groupCancelButtonX() {
        return groupSaveButtonX() + GROUP_EDITOR_BUTTON_WIDTH + 4;
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
