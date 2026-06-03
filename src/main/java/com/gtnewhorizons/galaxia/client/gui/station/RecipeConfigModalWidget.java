package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeInputScreen;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeChance;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeMapLayout;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

import gregtech.api.modularui2.GTGuiTextures;

final class RecipeConfigModalWidget extends ParentWidget<RecipeConfigModalWidget> {

    static final int WIDTH = 440;
    static final int HEIGHT = 320;

    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + 10;
    private static final int ROW_TOP = BODY_TOP + 56;
    private static final int ROW_HEIGHT = 25;
    private static final int ROWS_PER_PAGE = 5;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SLOT_X = 8;
    private static final int ENABLE_X = 30;
    private static final int RECIPE_X = 76;
    private static final int CONFIG_X = 232;
    private static final int PRIORITY_X = 304;
    private static final int ORDER_X = 350;
    private static final int REMOVE_X = 394;
    private static final int ENABLE_WIDTH = 40;
    private static final int RECIPE_WIDTH = 148;
    private static final int CONFIG_WIDTH = 64;
    private static final int SMALL_FIELD_WIDTH = 46;
    private static final int REMOVE_WIDTH = 38;
    private static final int PAGE_BUTTON_WIDTH = 28;
    private static final int MODE_BUTTON_WIDTH = 96;
    private static final int ADD_BUTTON_WIDTH = 52;
    private static final int COPY_SETTINGS_BUTTON_X = 236;
    private static final int COPY_SETTINGS_BUTTON_WIDTH = 116;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int RENAME_MODAL_WIDTH = 260;
    private static final int RENAME_MODAL_HEIGHT = 104;
    private static final int RENAME_MODAL_Y = 110;
    private static final int RENAME_FIELD_WIDTH = 150;
    private static final int RENAME_BUTTON_WIDTH = 44;
    private static final int RENAME_CLEAR_BUTTON_WIDTH = 48;
    private static final int RENAME_CANCEL_BUTTON_WIDTH = 54;
    private static final int RENAME_FIELD_HEIGHT = 18;
    private static final int RENAME_FIELD_Y = RENAME_MODAL_Y + 40;
    private static final int DETAIL_TITLE_Y = BODY_TOP + 4;
    private static final int DETAIL_RECIPE_WIDGET_X = ModuleConfigModalSupport.PANEL_PADDING;
    private static final int DETAIL_RECIPE_WIDGET_Y = BODY_TOP + 22;
    private static final int DETAIL_RECIPE_WIDGET_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;
    private static final int DETAIL_RECIPE_WIDGET_HEIGHT = 180;
    private static final int DETAIL_RECIPE_SCALE = 1;
    private static final int DETAIL_CONTROL_Y = DETAIL_RECIPE_WIDGET_Y + DETAIL_RECIPE_WIDGET_HEIGHT + 24;
    private static final int DETAIL_AMOUNT_WIDTH = 72;
    private static final int DETAIL_ACTIONS_WIDTH = DETAIL_AMOUNT_WIDTH + 6 + 48 + 4 + 54;
    private static final int DETAIL_AMOUNT_X = (WIDTH - DETAIL_ACTIONS_WIDTH) / 2;
    private static final int DETAIL_SLOT_SIZE = 18;
    private static final int BOUND_MARKER_SIZE = 5;
    private static final int BOUND_MARKER_INSET = 1;
    private static final int BOUND_MARKER_WARNING = EnumColors.MAP_COLOR_RECIPE_BOUND_MARKER_WARNING.getColor();
    private static final int BOUND_MARKER_BLOCKING = EnumColors.MAP_COLOR_RECIPE_BOUND_MARKER_BLOCKING.getColor();
    private static final int DETAIL_ITEM_SLOT_COUNT = 9;
    private static final int DETAIL_FLUID_SLOT_COUNT = 4;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final @Nullable StationEditModeController editModeController;
    private final ModuleSettingsGroupSelectorWidget settingsGroupSelector;
    private int page;
    private int boundsSlotIndex = -1;
    private @Nullable BoundTarget selectedBoundTarget;
    private String boundAmountInput = "";
    private @Nullable TextFieldWidget boundAmountField;
    private int renameSlotIndex = -1;
    private final RecipeRenameFormModel recipeRenameForm = new RecipeRenameFormModel();
    private @Nullable TextFieldWidget recipeNameField;

    RecipeConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        @Nullable StationEditModeController editModeController) {
        this.assetId = assetId;
        this.controller = controller;
        this.editModeController = editModeController;
        this.settingsGroupSelector = new ModuleSettingsGroupSelectorWidget(assetId, controller, () -> {
            ModuleInstance module = selectedModule();
            return module != null ? module.kind() : null;
        }, this::isRecipeListOpen, WIDTH);

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int rowY = ROW_TOP + row * ROW_HEIGHT;
            int rowIndex = row;
            child(
                ModuleConfigModalSupport
                    .button(() -> canUseRow(rowIndex), () -> enabledLabel(rowIndex), () -> toggleEnabled(rowIndex))
                    .pos(ENABLE_X, rowY)
                    .size(ENABLE_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "Config", () -> openBounds(rowIndex))
                    .pos(CONFIG_X, rowY)
                    .size(CONFIG_WIDTH, BUTTON_HEIGHT));
            child(
                new RecipeNameClickWidget(rowIndex).pos(RECIPE_X, rowY)
                    .size(RECIPE_WIDTH, BUTTON_HEIGHT));
            child(
                numberField(rowIndex, Field.PRIORITY).pos(PRIORITY_X, rowY)
                    .size(SMALL_FIELD_WIDTH, BUTTON_HEIGHT));
            child(
                numberField(rowIndex, Field.REQUEST_AMOUNT).pos(ORDER_X, rowY)
                    .size(SMALL_FIELD_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "X", () -> removeSlot(rowIndex))
                    .pos(REMOVE_X, rowY)
                    .size(REMOVE_WIDTH, BUTTON_HEIGHT));
        }

        child(
            new RecipeBoundsViewWidget().pos(DETAIL_RECIPE_WIDGET_X, DETAIL_RECIPE_WIDGET_Y)
                .size(DETAIL_RECIPE_WIDGET_WIDTH, DETAIL_RECIPE_WIDGET_HEIGHT));
        child(
            createBoundAmountField().pos(DETAIL_AMOUNT_X, DETAIL_CONTROL_Y)
                .size(DETAIL_AMOUNT_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canApplySelectedBound, "Set", this::applySelectedBound)
                .pos(DETAIL_AMOUNT_X + DETAIL_AMOUNT_WIDTH + 6, DETAIL_CONTROL_Y)
                .size(48, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canClearSelectedBound, "Clear", this::clearSelectedBound)
                .pos(DETAIL_AMOUNT_X + DETAIL_AMOUNT_WIDTH + 58, DETAIL_CONTROL_Y)
                .size(54, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::isBoundsOpen, "Back", this::closeBounds)
                .pos(WIDTH - CLOSE_BUTTON_WIDTH - ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, BUTTON_HEIGHT));
        recipeNameField = createRecipeNameField();
        child(
            recipeNameField.pos(renameFieldX(), RENAME_FIELD_Y)
                .size(RENAME_FIELD_WIDTH, RENAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canSaveRecipeName, "Save", this::saveRecipeName)
                .pos(renameSaveButtonX(), RENAME_FIELD_Y)
                .size(RENAME_BUTTON_WIDTH, RENAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::isRecipeRenameOpen, "Clear", this::clearRecipeName)
                .pos(renameClearButtonX(), RENAME_FIELD_Y)
                .size(RENAME_CLEAR_BUTTON_WIDTH, RENAME_FIELD_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::isRecipeRenameOpen, "Cancel", this::closeRecipeRename)
                .pos(renameCancelButtonX(), RENAME_FIELD_Y)
                .size(RENAME_CANCEL_BUTTON_WIDTH, RENAME_FIELD_HEIGHT));

        child(
            ModuleConfigModalSupport.button(this::hasPreviousPage, "<", this::previousPage)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(PAGE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::hasNextPage, ">", this::nextPage)
                .pos(ModuleConfigModalSupport.PANEL_PADDING + PAGE_BUTTON_WIDTH + 4, FOOTER_Y)
                .size(PAGE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfigureRecipes, this::modeLabel, this::cycleMode)
                .pos(76, FOOTER_Y)
                .size(MODE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfigureRecipes, "Add", this::addRecipe)
                .pos(178, FOOTER_Y)
                .size(ADD_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canCopySettings, "Copy Settings...", this::startCopySettingsPicker)
                .pos(COPY_SETTINGS_BUTTON_X, FOOTER_Y)
                .size(COPY_SETTINGS_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(() -> controller.isRecipeConfigOpen() && !isBoundsOpen(), "Close", controller::close)
                .pos(WIDTH - CLOSE_BUTTON_WIDTH - ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            settingsGroupSelector.pos(0, 0)
                .size(WIDTH, HEIGHT));
        setEnabledIf(w -> controller.isRecipeConfigOpen());
    }

    @Override
    public boolean canHoverThrough() {
        return !controller.isRecipeConfigOpen();
    }

    @Override
    public boolean canClickThrough() {
        return !controller.isRecipeConfigOpen();
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isRecipeConfigOpen()) return;
        ModuleInstance module = selectedModule();
        String title = module != null ? ModuleConfigModalSupport.moduleTitle(module, "Recipes") : "Recipes";
        ModuleConfigModalSupport.drawFrame(title, WIDTH, HEIGHT);

        if (isBoundsOpen()) {
            drawBoundsView();
            return;
        }

        RecipeConfig config = selectedConfig();
        int slotCount = slots().size();
        int color = canConfigureRecipes() ? EnumColors.MAP_COLOR_TEXT_BODY.getColor()
            : EnumColors.MAP_COLOR_TEXT_MUTED.getColor();
        ModuleConfigModalSupport
            .drawLine("Slots: " + slotCount, ModuleConfigModalSupport.PANEL_PADDING, BODY_TOP, color);
        ModuleConfigModalSupport.drawLine(modeLabel(), 116, BODY_TOP, color);
        drawHeader(color);

        List<SavedRecipe> slots = slots();
        if (slots.isEmpty()) {
            ModuleConfigModalSupport
                .drawLine("No recipes configured", ModuleConfigModalSupport.PANEL_PADDING, ROW_TOP, color);
            return;
        }

        int first = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int slotIndex = first + row;
            if (slotIndex >= slots.size()) break;
            drawSlotRow(row, slotIndex, slots.get(slotIndex), color);
        }

        if (config == null && module != null) {
            ModuleConfigModalSupport.drawLine(
                "Config will be created when adding a recipe",
                ModuleConfigModalSupport.PANEL_PADDING,
                ROW_TOP + ROWS_PER_PAGE * ROW_HEIGHT + 2,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
        drawRecipeRenameOverlay();
    }

    private void drawHeader(int color) {
        int y = ROW_TOP - 12;
        ModuleConfigModalSupport.drawLine("#", SLOT_X, y, color);
        ModuleConfigModalSupport.drawLine("On", ENABLE_X + 9, y, color);
        ModuleConfigModalSupport.drawLine("Flow", RECIPE_X, y, color);
        ModuleConfigModalSupport.drawLine("Config", CONFIG_X + 8, y, color);
        ModuleConfigModalSupport.drawLine("Pri", PRIORITY_X + 11, y, color);
        ModuleConfigModalSupport.drawLine("Req", ORDER_X + 9, y, color);
    }

    private void drawSlotRow(int row, int slotIndex, SavedRecipe slot, int color) {
        int y = ROW_TOP + row * ROW_HEIGHT + 6;
        ModuleConfigModalSupport.drawLine(Integer.toString(slotIndex + 1), SLOT_X, y, color);
        ModuleConfigModalSupport.drawTrimmedLine(
            RecipeSlotUiModel.slotTitle(slot),
            RECIPE_X,
            y,
            RECIPE_WIDTH,
            slot.enabled() ? EnumColors.MAP_COLOR_TEXT_BODY.getColor() : EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private TextFieldWidget numberField(int rowIndex, Field field) {
        return new TextFieldWidget().setMaxLength(6)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(field.defaultValue)
            .setNumbers(field.min, field.max)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (!canUseRow(rowIndex)) return;
                com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .value(new StringValue.Dynamic(() -> fieldText(rowIndex, field), text -> setField(rowIndex, field, text)))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> canUseRow(rowIndex));
    }

    private String fieldText(int rowIndex, Field field) {
        SavedRecipe slot = slotAtRow(rowIndex);
        if (slot == null) return "";
        return Integer.toString(field.value(slot));
    }

    private void setField(int rowIndex, Field field, String text) {
        SavedRecipe slot = slotAtRow(rowIndex);
        if (slot == null) return;
        int value = RecipeSlotUiModel.parseIntOrCurrent(text, field.value(slot), field.min, field.max);
        updateSlot(rowIndex, field.updated(slot, value));
    }

    private boolean canConfigureRecipes() {
        return isRecipeListOpen() && !isRecipeRenameOpen() && selectedRecipeModule() != null;
    }

    private boolean canCopySettings() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        return editModeController != null && canConfigureRecipes()
            && !settingsGroupSelector.isBlockingModuleControls()
            && facility != null
            && facility.stationLayout() != null
            && module != null
            && FacilityModuleRegistry.get(module.kind())
                .settingsGroups();
    }

    private boolean isRecipeListOpen() {
        return controller.isRecipeConfigOpen() && !isBoundsOpen();
    }

    private boolean canUseRow(int rowIndex) {
        return canConfigureRecipes() && !isBoundsOpen() && slotAtRow(rowIndex) != null;
    }

    private String enabledLabel(int rowIndex) {
        SavedRecipe slot = slotAtRow(rowIndex);
        return slot != null && slot.enabled() ? "On" : "Off";
    }

    private void toggleEnabled(int rowIndex) {
        SavedRecipe slot = slotAtRow(rowIndex);
        if (slot == null) return;
        updateSlot(
            rowIndex,
            new SavedRecipe(
                slot.recipe(),
                !slot.enabled(),
                slot.requestAmount(),
                slot.priority(),
                slot.orderSize(),
                slot.displayName()));
    }

    private void openBounds(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0 || slotIndex >= slots().size()) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        boundsSlotIndex = slotIndex;
        selectFirstBoundTarget();
    }

    private void closeBounds() {
        boundsSlotIndex = -1;
        selectedBoundTarget = null;
        boundAmountInput = "";
    }

    private void removeSlot(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0 || slotAtRow(rowIndex) == null) return;
        settingsGroupSelector.closeMenu();
        updateRenameAfterSlotRemoval(slotIndex);
        updateBoundsAfterSlotRemoval(slotIndex);
        CelestialClient.updateModuleRecipeSlot(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT,
            (byte) slotIndex,
            null);
        page = Math.min(page, maxPageAfterRemoval());
    }

    private void updateRenameAfterSlotRemoval(int removedSlotIndex) {
        if (!isRecipeRenameOpen()) return;
        if (renameSlotIndex == removedSlotIndex) {
            closeRecipeRename();
            return;
        }
        if (renameSlotIndex > removedSlotIndex) renameSlotIndex--;
    }

    private void updateBoundsAfterSlotRemoval(int removedSlotIndex) {
        if (!isBoundsOpen()) return;
        if (boundsSlotIndex == removedSlotIndex) {
            closeBounds();
            return;
        }
        if (boundsSlotIndex > removedSlotIndex) boundsSlotIndex--;
    }

    private void updateSlot(int rowIndex, SavedRecipe slot) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0) return;
        updateSlotIndex(slotIndex, slot);
    }

    private void updateSlotIndex(int slotIndex, SavedRecipe slot) {
        if (slotIndex < 0) return;
        updateLocalSlot(slotIndex, slot);
        CelestialClient.updateModuleRecipeSlot(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT,
            (byte) slotIndex,
            slot);
    }

    private void updateLocalSlot(int slotIndex, SavedRecipe slot) {
        RecipeConfig config = selectedConfig();
        if (config == null || slotIndex >= config.savedRecipes()
            .size()) return;
        config.savedRecipes()
            .set(slotIndex, slot);
    }

    private void cycleMode() {
        IRecipeModule recipeModule = selectedRecipeModule();
        if (recipeModule == null) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_RECIPE_SCHEDULER_MODE,
            RecipeSlotUiModel.nextMode(recipeModule.getRecipeConfig()));
    }

    private void addRecipe() {
        ModuleInstance module = selectedModule();
        if (module == null) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        RecipeInputScreen.open(assetId, controller.moduleIndex(), module);
    }

    private void startCopySettingsPicker() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance source = selectedModule();
        int sourceModuleIndex = controller.moduleIndex();
        if (facility == null || source == null || editModeController == null || sourceModuleIndex < 0) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        controller.close();
        editModeController.startTileMode(
            StationEditModeController.Mode.COPY_MODULE,
            "Copy module settings",
            "Copy",
            coord -> ModuleSettingsCopyPickerModel.isCompatibleTarget(facility, source, coord),
            coord -> ModuleSettingsCopyPickerModel.normalizeTarget(facility, coord),
            targets -> CelestialClient.copyModuleSettings(assetId, sourceModuleIndex, targets));
    }

    private void previousPage() {
        if (hasPreviousPage()) page--;
    }

    private void nextPage() {
        if (hasNextPage()) page++;
    }

    private boolean hasPreviousPage() {
        return canConfigureRecipes() && page > 0;
    }

    private boolean hasNextPage() {
        return canConfigureRecipes() && (page + 1) * ROWS_PER_PAGE < slots().size();
    }

    private int maxPageAfterRemoval() {
        int remaining = Math.max(0, slots().size() - 1);
        return remaining == 0 ? 0 : (remaining - 1) / ROWS_PER_PAGE;
    }

    private boolean isBoundsOpen() {
        return boundsSlotIndex >= 0 && boundsSlotIndex < slots().size();
    }

    private @Nullable SavedRecipe boundsSlot() {
        List<SavedRecipe> slots = slots();
        return boundsSlotIndex >= 0 && boundsSlotIndex < slots.size() ? slots.get(boundsSlotIndex) : null;
    }

    private TextFieldWidget createBoundAmountField() {
        boundAmountField = new TextFieldWidget().setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(true)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (selectedBoundTarget == null || !isBoundsOpen()) return;
                BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .value(new StringValue.Dynamic(() -> boundAmountInput, text -> boundAmountInput = text == null ? "" : text))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> selectedBoundTarget != null && isBoundsOpen());
        return boundAmountField;
    }

    private void drawBoundsView() {
        SavedRecipe slot = boundsSlot();
        if (slot == null) return;
        int color = EnumColors.MAP_COLOR_TEXT_BODY.getColor();
        ModuleConfigModalSupport.drawTrimmedLine(
            RecipeSlotUiModel.slotTitle(slot),
            ModuleConfigModalSupport.PANEL_PADDING,
            DETAIL_TITLE_Y,
            WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2,
            color);

        String selected = selectedBoundTarget == null ? "Select a slot"
            : boundDescription(selectedBoundTarget) + ": " + currentBoundText(selectedBoundTarget);
        ModuleConfigModalSupport.drawTrimmedLine(
            selected,
            ModuleConfigModalSupport.PANEL_PADDING,
            DETAIL_CONTROL_Y - 18,
            WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
    }

    private void drawBoundMarker(BoundTarget target, int x, int y) {
        if (!hasBound(target)) return;
        int color = isBoundBlocking(target) ? BOUND_MARKER_BLOCKING : BOUND_MARKER_WARNING;
        net.minecraft.client.gui.Gui.drawRect(x, y, x + BOUND_MARKER_SIZE, y + BOUND_MARKER_SIZE, color);
    }

    private boolean canUseBoundTarget(BoundTarget target) {
        if (!isBoundsOpen()) return false;
        return target.resource() == BoundResource.ITEM ? itemStack(target) != null : fluidStack(target) != null;
    }

    private void selectFirstBoundTarget() {
        selectedBoundTarget = null;
        GTRecipeMapLayout layout = detailLayout();
        BoundTarget candidate = firstVisibleBoundTarget(layout.itemInputs(), BoundSide.INPUT, BoundResource.ITEM);
        if (candidate == null)
            candidate = firstVisibleBoundTarget(layout.itemOutputs(), BoundSide.OUTPUT, BoundResource.ITEM);
        if (candidate == null)
            candidate = firstVisibleBoundTarget(layout.fluidInputs(), BoundSide.INPUT, BoundResource.FLUID);
        if (candidate == null)
            candidate = firstVisibleBoundTarget(layout.fluidOutputs(), BoundSide.OUTPUT, BoundResource.FLUID);
        if (candidate != null) {
            selectedBoundTarget = candidate;
            boundAmountInput = currentBoundText(candidate);
            syncBoundAmountFieldText();
            focusBoundAmountField();
            return;
        }
        boundAmountInput = "";
        syncBoundAmountFieldText();
    }

    private @Nullable BoundTarget firstVisibleBoundTarget(List<GTRecipeMapLayout.Slot> slots, BoundSide side,
        BoundResource resource) {
        for (GTRecipeMapLayout.Slot slot : slots) {
            BoundTarget candidate = new BoundTarget(side, resource, slot.index());
            if (canUseBoundTarget(candidate)) return candidate;
        }
        return null;
    }

    private boolean canApplySelectedBound() {
        return selectedBoundTarget != null && canUseBoundTarget(selectedBoundTarget);
    }

    private boolean canClearSelectedBound() {
        return canApplySelectedBound() && hasBound(selectedBoundTarget);
    }

    private void applySelectedBound() {
        if (!canApplySelectedBound()) return;
        long amount;
        try {
            amount = Long.parseLong(currentBoundAmountInput());
        } catch (NumberFormatException ignored) {
            return;
        }
        amount = Math.max(0L, amount);
        SavedRecipe slot = boundsSlot();
        if (slot == null) return;
        updateBound(selectedBoundTarget, amount);
        boundAmountInput = Long.toString(amount);
        syncBoundAmountFieldText();
    }

    private void clearSelectedBound() {
        if (!canClearSelectedBound()) return;
        SavedRecipe slot = boundsSlot();
        if (slot == null) return;
        clearBound(selectedBoundTarget);
        boundAmountInput = "";
        syncBoundAmountFieldText();
    }

    private String currentBoundAmountInput() {
        return boundAmountField != null ? boundAmountField.getText() : boundAmountInput;
    }

    private void syncBoundAmountFieldText() {
        if (boundAmountField != null) boundAmountField.setText(boundAmountInput);
    }

    private void focusBoundAmountField() {
        if (boundAmountField != null && getContext() != null) getContext().focus(boundAmountField);
    }

    private void beginRecipeRename(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        SavedRecipe slot = slotAtRow(rowIndex);
        if (slotIndex < 0 || slot == null) return;
        settingsGroupSelector.closeMenu();
        renameSlotIndex = slotIndex;
        recipeRenameForm.open(slot);
        syncRecipeNameFieldText();
        focusRecipeNameField();
    }

    private boolean isRecipeRenameOpen() {
        return renameSlotIndex >= 0 && renameSlotIndex < slots().size();
    }

    private boolean canSaveRecipeName() {
        recipeRenameForm.setInput(currentRecipeNameInput());
        return isRecipeRenameOpen() && recipeRenameForm.canSave();
    }

    private void saveRecipeName() {
        SavedRecipe slot = renameSlot();
        if (slot == null) return;
        recipeRenameForm.setInput(currentRecipeNameInput());
        updateSlotIndex(renameSlotIndex, recipeRenameForm.save(slot));
        closeRecipeRename();
    }

    private void clearRecipeName() {
        SavedRecipe slot = renameSlot();
        if (slot == null) return;
        updateSlotIndex(renameSlotIndex, recipeRenameForm.clear(slot));
        closeRecipeRename();
    }

    private void closeRecipeRename() {
        renameSlotIndex = -1;
        recipeRenameForm.close();
        syncRecipeNameFieldText();
    }

    private @Nullable SavedRecipe renameSlot() {
        return isRecipeRenameOpen() ? slots().get(renameSlotIndex) : null;
    }

    private TextFieldWidget createRecipeNameField() {
        return new TextFieldWidget().setMaxLength(64)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (!isRecipeRenameOpen()) return;
                BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .value(new StringValue.Dynamic(recipeRenameForm::input, recipeRenameForm::setInput))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> isRecipeRenameOpen());
    }

    private String currentRecipeNameInput() {
        return recipeNameField != null ? recipeNameField.getText() : recipeRenameForm.input();
    }

    private void syncRecipeNameFieldText() {
        if (recipeNameField != null) recipeNameField.setText(recipeRenameForm.input());
    }

    private void focusRecipeNameField() {
        if (recipeNameField != null && getContext() != null) getContext().focus(recipeNameField);
    }

    private void drawRecipeRenameOverlay() {
        if (!isRecipeRenameOpen()) return;
        int x = renameModalX();
        ModuleConfigModalSupport
            .drawFrameAt("Rename Recipe", x, RENAME_MODAL_Y, RENAME_MODAL_WIDTH, RENAME_MODAL_HEIGHT);
        ModuleConfigModalSupport
            .drawLine("Recipe name", renameFieldX(), RENAME_FIELD_Y - 13, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private int renameModalX() {
        return (WIDTH - RENAME_MODAL_WIDTH) / 2;
    }

    private int renameFieldX() {
        return renameModalX() + ModuleConfigModalSupport.PANEL_PADDING;
    }

    private int renameSaveButtonX() {
        return renameFieldX() + RENAME_FIELD_WIDTH + 6;
    }

    private int renameClearButtonX() {
        return renameSaveButtonX() + RENAME_BUTTON_WIDTH + 4;
    }

    private int renameCancelButtonX() {
        return renameClearButtonX() + RENAME_CLEAR_BUTTON_WIDTH + 4;
    }

    private String currentBoundText(BoundTarget target) {
        SavedRecipe slot = boundsSlot();
        if (slot == null) return "";
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return "";
        return hasBound(target) ? Long.toString(boundAmount(facility, target)) : "";
    }

    private boolean hasBound(BoundTarget target) {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return false;
        ItemStackWrapper item = itemKey(target);
        FluidStack stack = fluidStack(target);
        if (stack == null) return false;
        FluidKey fluid = FluidKey.of(stack);
        return switch (boundKind(target)) {
            case ITEM_LOWER -> item != null && facility.hasLowerBound(item);
            case ITEM_UPPER -> item != null && facility.hasUpperBound(item);
            case FLUID_LOWER -> facility.hasLowerBound(fluid);
            case FLUID_UPPER -> facility.hasUpperBound(fluid);
        };
    }

    private boolean isBoundBlocking(BoundTarget target) {
        SavedRecipe slot = boundsSlot();
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (slot == null || facility == null || !hasBound(target)) return false;
        long bound = boundAmount(facility, target);
        if (target.resource() == BoundResource.ITEM) {
            ItemStackWrapper item = itemKey(target);
            if (item == null) return false;
            long current = facility.getItemAmount(item);
            long recipeAmount = target.side() == BoundSide.INPUT ? itemInputAmount(slot.recipe(), item)
                : itemOutputAmount(slot.recipe(), item);
            return target.side() == BoundSide.INPUT ? current - recipeAmount < bound : current >= bound;
        }
        FluidStack fluid = fluidStack(target);
        if (fluid == null) return false;
        FluidKey key = FluidKey.of(fluid);
        long current = facility.getFluidAmount(key);
        long recipeAmount = target.side() == BoundSide.INPUT ? fluidInputAmount(slot.recipe(), key)
            : fluidOutputAmount(slot.recipe(), key);
        return target.side() == BoundSide.INPUT ? current - recipeAmount < bound : current >= bound;
    }

    private void updateBound(BoundTarget target, long amount) {
        if (!canUseBoundTarget(target)) return;
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return;
        BoundKind kind = boundKind(target);
        boolean isLow = kind == BoundKind.ITEM_LOWER || kind == BoundKind.FLUID_LOWER;
        InventoryKey key = inventoryKey(target);
        if (key == null) return;
        facility.setBound(key, amount, isLow);
        CelestialClient.updateInventoryBound(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_INVENTORY_BOUND,
            kind,
            key,
            amount);
    }

    private void clearBound(BoundTarget target) {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (facility == null) return;
        BoundKind kind = boundKind(target);
        boolean isLow = kind == BoundKind.ITEM_LOWER || kind == BoundKind.FLUID_LOWER;
        InventoryKey key = inventoryKey(target);
        if (key == null) return;
        facility.clearBound(key, isLow);
        CelestialClient.updateInventoryBound(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.CLEAR_INVENTORY_BOUND,
            kind,
            key,
            0L);
    }

    private @Nullable InventoryKey inventoryKey(BoundTarget target) {
        if (target.resource() == BoundResource.ITEM) {
            return itemKey(target);
        }
        FluidStack stack = fluidStack(target);
        return stack != null ? FluidKey.of(stack) : null;
    }

    private static BoundKind boundKind(BoundTarget target) {
        return switch (target.resource()) {
            case ITEM -> target.side() == BoundSide.INPUT ? BoundKind.ITEM_LOWER : BoundKind.ITEM_UPPER;
            case FLUID -> target.side() == BoundSide.INPUT ? BoundKind.FLUID_LOWER : BoundKind.FLUID_UPPER;
        };
    }

    private long boundAmount(AutomatedFacility facility, BoundTarget target) {
        ItemStackWrapper item = itemKey(target);
        FluidStack fluid = fluidStack(target);
        return switch (boundKind(target)) {
            case ITEM_LOWER -> facility.getBound(item)
                .lowOrDefault();
            case ITEM_UPPER -> facility.getBound(item)
                .upperOrDefault();
            case FLUID_LOWER -> facility.getBound(FluidKey.of(fluid))
                .lowOrDefault();
            case FLUID_UPPER -> facility.getBound(FluidKey.of(fluid))
                .upperOrDefault();
        };
    }

    private String boundDescription(BoundTarget target) {
        String side = target.side() == BoundSide.INPUT ? "Input lower" : "Output upper";
        return side + " " + resourceName(target);
    }

    private String resourceName(BoundTarget target) {
        if (target.resource() == BoundResource.ITEM) {
            ItemStack stack = itemStack(target);
            return stack != null ? stack.getDisplayName() : "?";
        }
        FluidStack stack = fluidStack(target);
        return stack != null ? fluidLabel(stack) : "?";
    }

    private @Nullable ItemStack itemStack(BoundTarget target) {
        SavedRecipe slot = boundsSlot();
        return slot != null ? itemStack(slot, target) : null;
    }

    private @Nullable ItemStack itemStack(SavedRecipe slot, BoundTarget target) {
        if (slot == null || target.resource() != BoundResource.ITEM) return null;
        ItemStack[] stacks = target.side() == BoundSide.INPUT ? slot.recipe()
            .inputs()
            : slot.recipe()
                .outputs();
        return target.index() >= 0 && stacks != null && target.index() < stacks.length ? stacks[target.index()] : null;
    }

    private @Nullable ItemStackWrapper itemKey(BoundTarget target) {
        return ItemStackWrapper.of(itemStack(target));
    }

    private @Nullable FluidStack fluidStack(BoundTarget target) {
        SavedRecipe slot = boundsSlot();
        return slot != null ? fluidStack(slot, target) : null;
    }

    private @Nullable FluidStack fluidStack(SavedRecipe slot, BoundTarget target) {
        if (slot == null || target.resource() != BoundResource.FLUID) return null;
        FluidStack[] stacks = target.side() == BoundSide.INPUT ? slot.recipe()
            .fluidInputs()
            : slot.recipe()
                .fluidOutputs();
        return target.index() >= 0 && stacks != null && target.index() < stacks.length ? stacks[target.index()] : null;
    }

    private GTRecipeMapLayout detailLayout() {
        IRecipeModule recipeModule = selectedRecipeModule();
        GTRecipeMapId mapId = recipeModule != null ? GTRecipeMapId.fromRecipeMapName(recipeModule.getRecipeMapName())
            : null;
        return GTRecipeMapLayout.fromRecipeMap(GTRecipeMapId.findRecipeMap(mapId));
    }

    private @Nullable String fluidName(BoundTarget target) {
        FluidStack stack = fluidStack(target);
        if (stack == null) return null;
        try {
            Fluid fluid = stack.getFluid();
            return fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String fluidLabel(FluidStack stack) {
        String name = null;
        try {
            Fluid fluid = stack.getFluid();
            name = fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {}
        return (name != null ? name : "Fluid") + " " + stack.amount + "L";
    }

    private long itemInputAmount(RecipeSnapshot recipe, ItemStackWrapper item) {
        return itemAmount(recipe.inputs(), item);
    }

    private long itemOutputAmount(RecipeSnapshot recipe, ItemStackWrapper item) {
        return itemAmount(recipe.outputs(), item);
    }

    private long itemAmount(@Nullable ItemStack[] stacks, ItemStackWrapper item) {
        if (stacks == null) return 0L;
        long total = 0L;
        for (ItemStack stack : stacks) {
            ItemStackWrapper key = ItemStackWrapper.of(stack);
            if (item.equals(key)) total += stack.stackSize;
        }
        return total;
    }

    private long fluidInputAmount(RecipeSnapshot recipe, FluidKey fluid) {
        return fluidAmount(recipe.fluidInputs(), fluid);
    }

    private long fluidOutputAmount(RecipeSnapshot recipe, FluidKey fluid) {
        return fluidAmount(recipe.fluidOutputs(), fluid);
    }

    private long fluidAmount(@Nullable FluidStack[] stacks, FluidKey fluid) {
        if (stacks == null) return 0L;
        long total = 0L;
        for (FluidStack stack : stacks) {
            if (stack == null) continue;
            if (FluidKey.of(stack)
                .equals(fluid)) total += stack.amount;
        }
        return total;
    }

    private String modeLabel() {
        return RecipeSlotUiModel.modeLabel(selectedConfig());
    }

    private @Nullable SavedRecipe slotAtRow(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        List<SavedRecipe> slots = slots();
        return slotIndex >= 0 && slotIndex < slots.size() ? slots.get(slotIndex) : null;
    }

    private int slotIndexForRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= ROWS_PER_PAGE) return -1;
        return page * ROWS_PER_PAGE + rowIndex;
    }

    private List<SavedRecipe> slots() {
        RecipeConfig config = selectedConfig();
        return config == null ? List.of()
            : config.savedRecipes()
                .toList();
    }

    private @Nullable RecipeConfig selectedConfig() {
        IRecipeModule recipeModule = selectedRecipeModule();
        return recipeModule != null ? recipeModule.getRecipeConfig() : null;
    }

    private @Nullable IRecipeModule selectedRecipeModule() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof IRecipeModule recipeModule ? recipeModule : null;
    }

    private @Nullable ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 200f;
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        RenderHelper.disableStandardItemLighting();
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void renderItemIconScaled(ItemStack stack, int x, int y, int scale) {
        if (stack == null) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        renderItemIcon(stack, 0, 0);
        GL11.glPopMatrix();
    }

    private final class RecipeNameClickWidget extends ParentWidget<RecipeNameClickWidget> implements Interactable {

        private final int rowIndex;

        RecipeNameClickWidget(int rowIndex) {
            this.rowIndex = rowIndex;
        }

        @Override
        public boolean canHover() {
            return canUseRow(rowIndex);
        }

        @Override
        public boolean canHoverThrough() {
            return !canUseRow(rowIndex);
        }

        @Override
        public boolean canClickThrough() {
            return !canUseRow(rowIndex);
        }

        @Override
        public Interactable.Result onMousePressed(int mouseButton) {
            if (mouseButton != 0 || !canUseRow(rowIndex)) return Interactable.Result.IGNORE;
            beginRecipeRename(rowIndex);
            Interactable.playButtonClickSound();
            return Interactable.Result.SUCCESS;
        }
    }

    private final class RecipeBoundsViewWidget extends ParentWidget<RecipeBoundsViewWidget> {

        RecipeBoundsViewWidget() {
            child(
                new RecipeProgressWidget().left(this::progressX, Unit.Measure.PIXEL)
                    .top(this::progressY, Unit.Measure.PIXEL)
                    .width(this::progressWidth, Unit.Measure.PIXEL)
                    .height(this::progressHeight, Unit.Measure.PIXEL)
                    .setEnabledIf(
                        w -> isBoundsOpen() && detailLayout().progress()
                            .enabled()));
            addSlotWidgets(BoundSide.INPUT, BoundResource.ITEM, DETAIL_ITEM_SLOT_COUNT);
            addSlotWidgets(BoundSide.OUTPUT, BoundResource.ITEM, DETAIL_ITEM_SLOT_COUNT);
            addSlotWidgets(BoundSide.INPUT, BoundResource.FLUID, DETAIL_FLUID_SLOT_COUNT);
            addSlotWidgets(BoundSide.OUTPUT, BoundResource.FLUID, DETAIL_FLUID_SLOT_COUNT);
        }

        private void addSlotWidgets(BoundSide side, BoundResource resource, int count) {
            for (int i = 0; i < count; i++) {
                BoundTarget target = new BoundTarget(side, resource, i);
                child(
                    new RecipeBoundSlotWidget(target).left(() -> slotX(target), Unit.Measure.PIXEL)
                        .top(() -> slotY(target), Unit.Measure.PIXEL)
                        .width(this::slotSize, Unit.Measure.PIXEL)
                        .height(this::slotSize, Unit.Measure.PIXEL)
                        .setEnabledIf(w -> isVisibleBoundSlot(target)));
            }
        }

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public boolean canClickThrough() {
            return true;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            scheduleResize();
            for (com.cleanroommc.modularui.api.widget.IWidget child : getChildren()) {
                child.scheduleResize();
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            if (boundsSlot() == null || !isBoundsOpen()) return;
            GTRecipeMapLayout layout = detailLayout();
            GTGuiTextures.BACKGROUND_STANDARD.draw(
                context,
                cardX(layout),
                cardY(layout),
                cardWidth(layout) * viewScale(layout),
                cardHeight(layout) * viewScale(layout),
                widgetTheme.getTheme());
        }

        private boolean isVisibleBoundSlot(BoundTarget target) {
            return boundsSlot() != null && layoutSlot(target) != null;
        }

        private int slotX(BoundTarget target) {
            GTRecipeMapLayout layout = detailLayout();
            GTRecipeMapLayout.Slot slot = layoutSlot(target);
            return slot == null ? -1000 : cardX(layout) + slot.x() * viewScale(layout);
        }

        private int slotY(BoundTarget target) {
            GTRecipeMapLayout layout = detailLayout();
            GTRecipeMapLayout.Slot slot = layoutSlot(target);
            return slot == null ? -1000 : cardY(layout) + slot.y() * viewScale(layout);
        }

        private int slotSize() {
            return DETAIL_SLOT_SIZE * viewScale(detailLayout());
        }

        private int progressX() {
            GTRecipeMapLayout layout = detailLayout();
            return cardX(layout) + layout.progress()
                .x() * viewScale(layout);
        }

        private int progressY() {
            GTRecipeMapLayout layout = detailLayout();
            return cardY(layout) + layout.progress()
                .y() * viewScale(layout);
        }

        private int progressWidth() {
            GTRecipeMapLayout layout = detailLayout();
            return layout.progress()
                .width() * viewScale(layout);
        }

        private int progressHeight() {
            GTRecipeMapLayout layout = detailLayout();
            return layout.progress()
                .height() * viewScale(layout);
        }

        private int cardX(GTRecipeMapLayout layout) {
            return (getArea().width - cardWidth(layout) * viewScale(layout)) / 2;
        }

        private int cardY(GTRecipeMapLayout layout) {
            return (getArea().height - cardHeight(layout) * viewScale(layout)) / 2;
        }

        private int viewScale(GTRecipeMapLayout layout) {
            return cardWidth(layout) * DETAIL_RECIPE_SCALE <= getArea().width
                && cardHeight(layout) * DETAIL_RECIPE_SCALE <= getArea().height ? DETAIL_RECIPE_SCALE : 1;
        }

        private int cardWidth(GTRecipeMapLayout layout) {
            return Math.max(layout.width(), maxSlotRight(layout));
        }

        private int cardHeight(GTRecipeMapLayout layout) {
            return Math.max(layout.height(), maxSlotBottom(layout));
        }

        private int maxSlotRight(GTRecipeMapLayout layout) {
            int right = 0;
            right = Math.max(right, maxSlotRight(layout.itemInputs()));
            right = Math.max(right, maxSlotRight(layout.itemOutputs()));
            right = Math.max(right, maxSlotRight(layout.fluidInputs()));
            right = Math.max(right, maxSlotRight(layout.fluidOutputs()));
            return right + 6;
        }

        private int maxSlotRight(List<GTRecipeMapLayout.Slot> slots) {
            int right = 0;
            for (GTRecipeMapLayout.Slot slot : slots) {
                right = Math.max(right, slot.x() + DETAIL_SLOT_SIZE);
            }
            return right;
        }

        private int maxSlotBottom(GTRecipeMapLayout layout) {
            int bottom = 0;
            bottom = Math.max(bottom, maxSlotBottom(layout.itemInputs()));
            bottom = Math.max(bottom, maxSlotBottom(layout.itemOutputs()));
            bottom = Math.max(bottom, maxSlotBottom(layout.fluidInputs()));
            bottom = Math.max(bottom, maxSlotBottom(layout.fluidOutputs()));
            return bottom + 6;
        }

        private int maxSlotBottom(List<GTRecipeMapLayout.Slot> slots) {
            int bottom = 0;
            for (GTRecipeMapLayout.Slot slot : slots) {
                bottom = Math.max(bottom, slot.y() + DETAIL_SLOT_SIZE);
            }
            return bottom;
        }

        private @Nullable GTRecipeMapLayout.Slot layoutSlot(BoundTarget target) {
            List<GTRecipeMapLayout.Slot> slots = switch (target.resource()) {
                case ITEM -> target.side() == BoundSide.INPUT ? detailLayout().itemInputs()
                    : detailLayout().itemOutputs();
                case FLUID -> target.side() == BoundSide.INPUT ? detailLayout().fluidInputs()
                    : detailLayout().fluidOutputs();
            };
            for (GTRecipeMapLayout.Slot slot : slots) {
                if (slot.index() == target.index()) return slot;
            }
            return null;
        }
    }

    private final class RecipeProgressWidget extends ParentWidget<RecipeProgressWidget> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            GTRecipeMapLayout.Progress progress = detailLayout().progress();
            if (!progress.enabled()) return;
            UITexture texture = progress.texture();
            if (texture == null) {
                BorderedRect.draw(
                    0,
                    0,
                    getArea().width,
                    getArea().height,
                    EnumColors.MAP_COLOR_RECIPE_PROGRESS_FALLBACK_BG.getColor(),
                    EnumColors.MAP_COLOR_RECIPE_PROGRESS_FALLBACK_BORDER.getColor());
                Minecraft.getMinecraft().fontRenderer.drawString(
                    ">",
                    getArea().width / 2 - 2,
                    getArea().height / 2 - 4,
                    EnumColors.MAP_COLOR_RECIPE_PROGRESS_FALLBACK_TEXT.getColor());
                return;
            }
            texture.getSubArea(0, 0, 1, 0.5f)
                .draw(context, 0, 0, getArea().width, getArea().height, widgetTheme.getTheme());
            drawProgressFill(context, widgetTheme, texture, progress);
        }

        private void drawProgressFill(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme, UITexture texture,
            GTRecipeMapLayout.Progress progress) {
            float amount = (System.currentTimeMillis() % 1000L) / 1000.0f;
            if (amount <= 0) return;
            UITexture full = texture.getSubArea(0, 0.5f, 1, 1);
            float u0 = 0f;
            float v0 = 0f;
            float u1 = 1f;
            float v1 = 1f;
            float x = 0f;
            float y = 0f;
            float width = getArea().width;
            float height = getArea().height;
            switch (progress.direction()) {
                case RIGHT -> {
                    u1 = amount;
                    width *= amount;
                }
                case LEFT -> {
                    u0 = 1f - amount;
                    width *= amount;
                    x = getArea().width - width;
                }
                case DOWN -> {
                    v1 = amount;
                    height *= amount;
                }
                case UP -> {
                    v0 = 1f - amount;
                    height *= amount;
                    y = getArea().height - height;
                }
                default -> {}
            }
            full.drawSubArea(x, y, width, height, u0, v0, u1, v1, widgetTheme.getTheme());
        }
    }

    private final class RecipeBoundSlotWidget extends ParentWidget<RecipeBoundSlotWidget> implements Interactable {

        private final BoundTarget target;

        RecipeBoundSlotWidget(BoundTarget target) {
            this.target = target;
        }

        @Override
        public boolean canHover() {
            return isVisible();
        }

        @Override
        public boolean canHoverThrough() {
            return !isVisible();
        }

        @Override
        public boolean canClickThrough() {
            return !isVisible();
        }

        @Override
        public Interactable.Result onMousePressed(int mouseButton) {
            if (mouseButton != 0 || !canUseBoundTarget(target)) return Interactable.Result.IGNORE;
            selectedBoundTarget = target;
            boundAmountInput = currentBoundText(target);
            syncBoundAmountFieldText();
            focusBoundAmountField();
            Interactable.playButtonClickSound();
            return Interactable.Result.SUCCESS;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            if (!isVisible()) return;
            int size = getArea().width;
            IDrawable overlay = slotOverlay();
            if (target.resource() == BoundResource.ITEM) {
                GTGuiTextures.SLOT_ITEM_STANDARD.draw(context, 0, 0, size, size, widgetTheme.getTheme());
            } else {
                GTGuiTextures.SLOT_FLUID_STANDARD.draw(context, 0, 0, size, size, widgetTheme.getTheme());
            }
            if (overlay != null) overlay.draw(context, 0, 0, size, size, widgetTheme.getTheme());
            drawSlotContent(context);
            drawChanceText(size);
            drawSlotHighlight(size);
            drawBoundMarker(target, boundMarkerX(size), boundMarkerY(size));
        }

        private int boundMarkerX(int size) {
            return target.resource() == BoundResource.ITEM ? BOUND_MARKER_INSET
                : size - BOUND_MARKER_SIZE - BOUND_MARKER_INSET;
        }

        private int boundMarkerY(int size) {
            return size - BOUND_MARKER_SIZE - BOUND_MARKER_INSET;
        }

        private void drawSlotContent(ModularGuiContext context) {
            int scale = Math.max(1, getArea().width / DETAIL_SLOT_SIZE);
            if (target.resource() == BoundResource.ITEM) {
                ItemStack stack = itemStack(target);
                if (stack != null) renderItemIconScaled(stack, scale, scale, scale);
                return;
            }
            FluidStack stack = fluidStack(target);
            if (stack == null) return;
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            try {
                com.cleanroommc.modularui.drawable.GuiDraw
                    .drawFluidTexture(stack, scale, scale, 16 * scale, 16 * scale, context.getCurrentDrawingZ());
                drawFluidAmountText(stack, getArea().width, scale);
            } finally {
                GL11.glPopAttrib();
                GL11.glColor4f(1f, 1f, 1f, 1f);
            }
        }

        private void drawFluidAmountText(FluidStack stack, int size, int scale) {
            String text = RecipeSlotUiModel.fluidSlotAmountText(stack);
            if (text == null) return;
            float textScale = 0.5F * scale;
            GL11.glPushMatrix();
            GL11.glScalef(textScale, textScale, 1.0F);
            int scaledHeight = Math.max(1, Math.round(size / textScale));
            int y = Math.max(0, scaledHeight - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT);
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, 1, y, 0xFFFFFF);
            GL11.glPopMatrix();
        }

        private void drawSlotHighlight(int size) {
            if (!canUseBoundTarget(target)) return;
            boolean selected = target.equals(selectedBoundTarget);
            if (!selected && !isHovering()) return;
            int color = selected ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
                : EnumColors.MAP_COLOR_RECIPE_BOUND_SLOT_INACTIVE_BORDER.getColor();
            BorderedRect.draw(-1, -1, size + 2, size + 2, EnumColors.MAP_COLOR_TRANSPARENT.getColor(), color);
        }

        private void drawChanceText(int size) {
            String text = chanceText();
            if (text == null) return;
            GL11.glPushMatrix();
            GL11.glScalef(0.5F, 0.5F, 1.0F);
            int scaledX = Math.max(0, size * 2 - Minecraft.getMinecraft().fontRenderer.getStringWidth(text) - 1);
            Minecraft.getMinecraft().fontRenderer
                .drawStringWithShadow(text, scaledX, 1, EnumColors.MAP_COLOR_RECIPE_BOUND_CHANCE_TEXT.getColor());
            GL11.glPopMatrix();
        }

        private @Nullable String chanceText() {
            if (target.side() != BoundSide.OUTPUT) return null;
            SavedRecipe slot = boundsSlot();
            if (slot == null) return null;
            int[] chances = target.resource() == BoundResource.ITEM ? slot.recipe()
                .outputChances()
                : slot.recipe()
                    .fluidOutputChances();
            return GTRecipeChance.optionalOutputLabel(chances, target.index());
        }

        private @Nullable IDrawable slotOverlay() {
            GTRecipeMapLayout.Slot slot = layoutSlot(target);
            return slot != null ? slot.overlay() : null;
        }

        private boolean isVisible() {
            return isBoundsOpen() && layoutSlot(target) != null;
        }

        private @Nullable GTRecipeMapLayout.Slot layoutSlot(BoundTarget target) {
            List<GTRecipeMapLayout.Slot> slots = switch (target.resource()) {
                case ITEM -> target.side() == BoundSide.INPUT ? detailLayout().itemInputs()
                    : detailLayout().itemOutputs();
                case FLUID -> target.side() == BoundSide.INPUT ? detailLayout().fluidInputs()
                    : detailLayout().fluidOutputs();
            };
            for (GTRecipeMapLayout.Slot slot : slots) {
                if (slot.index() == target.index()) return slot;
            }
            return null;
        }
    }

    private record BoundTarget(BoundSide side, BoundResource resource, int index) {}

    private enum BoundSide {
        INPUT,
        OUTPUT
    }

    private enum BoundResource {
        ITEM,
        FLUID
    }

    private enum Field {

        PRIORITY(0, RecipeSlotUiModel.MAX_BYTE_SETTING, 1) {

            @Override
            int value(SavedRecipe slot) {
                return slot.priority();
            }

            @Override
            SavedRecipe updated(SavedRecipe slot, int value) {
                return new SavedRecipe(
                    slot.recipe(),
                    slot.enabled(),
                    slot.requestAmount(),
                    (byte) value,
                    slot.orderSize(),
                    slot.displayName());
            }
        },
        REQUEST_AMOUNT(0, Integer.MAX_VALUE, 0) {

            @Override
            int value(SavedRecipe slot) {
                return (int) Math.min(Integer.MAX_VALUE, slot.requestAmount());
            }

            @Override
            SavedRecipe updated(SavedRecipe slot, int value) {
                return new SavedRecipe(
                    slot.recipe(),
                    slot.enabled(),
                    value,
                    slot.priority(),
                    slot.orderSize(),
                    slot.displayName());
            }
        };

        final int min;
        final int max;
        final int defaultValue;

        Field(int min, int max, int defaultValue) {
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }

        abstract int value(SavedRecipe slot);

        abstract SavedRecipe updated(SavedRecipe slot, int value);
    }
}
