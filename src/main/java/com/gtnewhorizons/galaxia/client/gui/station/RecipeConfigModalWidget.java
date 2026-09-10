package com.gtnewhorizons.galaxia.client.gui.station;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

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
    private static final int PRIORITY_X = 304;
    private static final int ORDER_X = 350;
    private static final int REMOVE_X = 394;
    private static final int ENABLE_WIDTH = 40;
    private static final int RECIPE_WIDTH = 148;
    private static final int DETAILS_X = 232;
    private static final int DETAILS_WIDTH = 64;
    private static final int SMALL_FIELD_WIDTH = 46;
    private static final int REMOVE_WIDTH = 38;
    private static final int PAGE_BUTTON_WIDTH = 28;
    private static final int MODE_BUTTON_WIDTH = 96;
    private static final int POLICY_BUTTON_X = 280;
    private static final int POLICY_BUTTON_Y = 47;
    private static final int POLICY_BUTTON_WIDTH = 152;
    private static final int POLICY_BUTTON_HEIGHT = 14;
    private static final int ADD_BUTTON_WIDTH = 52;
    private static final int COPY_SETTINGS_BUTTON_X = 236;
    private static final int COPY_SETTINGS_BUTTON_WIDTH = 116;
    private static final int SAVE_BUTTON_X = 356;
    private static final int SAVE_BUTTON_WIDTH = 36;
    private static final int CLOSE_BUTTON_X = 396;
    private static final int CLOSE_BUTTON_WIDTH = 36;
    private static final int RENAME_MODAL_WIDTH = 260;
    private static final int RENAME_MODAL_HEIGHT = 104;
    private static final int RENAME_MODAL_Y = 110;
    private static final int RENAME_FIELD_WIDTH = 150;
    private static final int RENAME_BUTTON_WIDTH = 44;
    private static final int RENAME_CLEAR_BUTTON_WIDTH = 48;
    private static final int RENAME_CANCEL_BUTTON_WIDTH = 54;
    private static final int RENAME_FIELD_HEIGHT = 18;
    private static final int RENAME_FIELD_Y = RENAME_MODAL_Y + 40;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final @Nullable StationTilePickerController tilePickerController;
    private final ModuleSettingsGroupSelectorWidget settingsGroupSelector;
    private final RecipeBookEditorModel editor;
    private int page;
    private boolean renameOpen;
    private String recipeNameInput = "";
    private @Nullable TextFieldWidget recipeNameField;
    private @Nullable RecipeDetails details;

    RecipeConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        @Nullable StationTilePickerController tilePickerController, RecipeBookEditorModel editor) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
        this.editor = editor;
        this.settingsGroupSelector = new ModuleSettingsGroupSelectorWidget(assetId, controller, () -> {
            ModuleInstance module = selectedModule();
            return module != null ? module.kind() : null;
        }, this::isRecipeListOpen, WIDTH, controller::close);

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int rowY = ROW_TOP + row * ROW_HEIGHT;
            int rowIndex = row;
            child(
                ModuleConfigModalSupport
                    .button(() -> canUseRow(rowIndex), () -> enabledLabel(rowIndex), () -> toggleEnabled(rowIndex))
                    .pos(ENABLE_X, rowY)
                    .size(ENABLE_WIDTH, BUTTON_HEIGHT));
            child(
                new RecipeNameClickWidget(rowIndex).pos(RECIPE_X, rowY)
                    .size(RECIPE_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport
                    .button(() -> canUseRow(rowIndex), () -> detailText("open"), () -> openDetails(rowIndex))
                    .pos(DETAILS_X, rowY)
                    .size(DETAILS_WIDTH, BUTTON_HEIGHT));
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
            ModuleConfigModalSupport
                .button(this::canConfigureRecipes, this::notDoablePolicyLabel, this::cycleNotDoablePolicy)
                .pos(POLICY_BUTTON_X, POLICY_BUTTON_Y)
                .size(POLICY_BUTTON_WIDTH, POLICY_BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canAddRecipe, "Add", this::addRecipe)
                .pos(178, FOOTER_Y)
                .size(ADD_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canCopySettings, "Copy Settings...", this::startCopySettingsPicker)
                .pos(COPY_SETTINGS_BUTTON_X, FOOTER_Y)
                .size(COPY_SETTINGS_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(
                    this::canConfigureRecipes,
                    () -> StatCollector.translateToLocal("galaxia.gui.station.recipe.save"),
                    this::save)
                .pos(SAVE_BUTTON_X, FOOTER_Y)
                .size(SAVE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isRecipeConfigOpen, "Close", controller::close)
                .pos(CLOSE_BUTTON_X, FOOTER_Y)
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
        if (details != null) return;

        int slotCount = editor.size();
        int color = canConfigureRecipes() ? EnumColors.MAP_COLOR_TEXT_BODY.getColor()
            : EnumColors.MAP_COLOR_TEXT_MUTED.getColor();
        ModuleConfigModalSupport
            .drawLine("Slots: " + slotCount, ModuleConfigModalSupport.PANEL_PADDING, BODY_TOP, color);
        ModuleConfigModalSupport.drawLine(modeLabel(), 116, BODY_TOP, color);
        drawHeader(color);

        if (slotCount == 0) {
            ModuleConfigModalSupport
                .drawLine("No recipes configured", ModuleConfigModalSupport.PANEL_PADDING, ROW_TOP, color);
            return;
        }

        int first = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int slotIndex = first + row;
            if (slotIndex >= slotCount) break;
            drawSlotRow(row, slotIndex, editor.recipeAt(slotIndex), color);
        }

        drawRecipeRenameOverlay();
    }

    private void drawHeader(int color) {
        int y = ROW_TOP - 12;
        ModuleConfigModalSupport.drawLine("#", SLOT_X, y, color);
        ModuleConfigModalSupport.drawLine("On", ENABLE_X + 9, y, color);
        ModuleConfigModalSupport.drawLine("Flow", RECIPE_X, y, color);
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
            .background((ctx, x, y, w, h, ignoredTheme) -> {
                if (!canUseRow(rowIndex)) return;
                com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            })
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
        return isRecipeListOpen() && !isRecipeRenameOpen() && selectedRecipe() != null;
    }

    private boolean canAddRecipe() {
        return canConfigureRecipes() && editor.canAdd();
    }

    private boolean canCopySettings() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance module = selectedModule();
        return tilePickerController != null && canConfigureRecipes()
            && !settingsGroupSelector.isBlockingModuleControls()
            && facility != null
            && facility.stationLayout() != null
            && module != null
            && FacilityModuleRegistry.get(module.kind())
                .settingsGroups();
    }

    private boolean isRecipeListOpen() {
        return controller.isRecipeConfigOpen() && details == null;
    }

    private void openDetails(int rowIndex) {
        SavedRecipe recipe = slotAtRow(rowIndex);
        if (recipe == null) return;
        settingsGroupSelector.closeMenu();
        details = new RecipeDetails(recipe);
        child(
            details.pos(0, 0)
                .size(WIDTH, HEIGHT));
        scheduleResize();
    }

    private void closeDetails() {
        if (details == null) return;
        remove(details);
        details = null;
    }

    private static String detailText(String key) {
        return StatCollector.translateToLocal("galaxia.gui.station.recipe.details." + key);
    }

    private final class RecipeDetails extends ParentWidget<RecipeDetails> {

        private static final int CONTENT_X = 8;
        private static final int CONTENT_Y = 58;
        private static final int CONTENT_WIDTH = WIDTH - CONTENT_X * 2;
        private static final int CONTENT_HEIGHT = 156;
        private static final int RESOURCE_ROW_HEIGHT = 30;
        private static final int BOUND_LABEL_Y = 222;
        private static final int BOUND_CONTROL_Y = 254;
        private static final int AMOUNT_WIDTH = 100;
        private static final int BOUND_BUTTON_WIDTH = 54;
        private static final int CONTROL_GAP = 6;
        private static final int BOUND_LABEL_SPACING = 14;
        private static final int RESOURCE_TEXT_PADDING = 4;
        private static final int RESOURCE_NAME_Y = 3;
        private static final int RESOURCE_AMOUNT_Y = 16;
        private static final int SCROLLBAR_GAP = 6;
        private static final int MAX_BOUND_DIGITS = 19;

        private @Nullable Resource selectedResource;
        private boolean input;
        private String boundInput = "";
        private final TextFieldWidget amountField;

        private RecipeDetails(SavedRecipe saved) {
            overlay((ctx, x, y, w, h, theme) -> {
                ModuleConfigModalSupport.drawTrimmedLine(
                    RecipeSlotUiModel.slotTitle(saved),
                    x + CONTENT_X,
                    y + BODY_TOP,
                    CONTENT_WIDTH,
                    EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
                if (selectedResource != null) {
                    ModuleConfigModalSupport.drawTrimmedLine(
                        resourceName(selectedResource),
                        x + CONTENT_X,
                        y + BOUND_LABEL_Y,
                        CONTENT_WIDTH,
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor());
                    ModuleConfigModalSupport.drawLine(
                        detailText(input ? "lower" : "upper") + ": " + currentBoundText(),
                        x + CONTENT_X,
                        y + BOUND_LABEL_Y + BOUND_LABEL_SPACING,
                        EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
                }
            });
            ParentWidget<?> rows = new ParentWidget<>().widthRel(1f);
            int row = 0;
            row = appendResources(
                rows,
                saved.recipe()
                    .itemInputs(),
                true,
                row);
            row = appendResources(
                rows,
                saved.recipe()
                    .fluidInputs(),
                true,
                row);
            row = appendResources(
                rows,
                saved.recipe()
                    .itemOutputs(),
                false,
                row);
            row = appendResources(
                rows,
                saved.recipe()
                    .fluidOutputs(),
                false,
                row);
            int contentHeight = row * RESOURCE_ROW_HEIGHT;
            rows.height(contentHeight);
            VerticalScrollData scrollData = new VerticalScrollData();
            scrollData.setScrollSize(contentHeight);
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(CONTENT_X, CONTENT_Y)
                .size(CONTENT_WIDTH, CONTENT_HEIGHT);
            scroll.child(rows);
            child(scroll);
            amountField = new TextFieldWidget().setMaxLength(MAX_BOUND_DIGITS)
                .setPattern(INTEGER_PATTERN)
                .autoUpdateOnChange(true)
                .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .background(
                    (ctx, x, y, w, h, theme) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()))
                .value(new StringValue.Dynamic(() -> boundInput, value -> boundInput = value))
                .setFocusOnGuiOpen(false)
                .setEnabledIf(w -> selectedResource != null);
            child(
                amountField.pos(CONTENT_X, BOUND_CONTROL_Y)
                    .size(AMOUNT_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(this::canApplyBound, () -> detailText("set"), this::applyBound)
                    .pos(CONTENT_X + AMOUNT_WIDTH + CONTROL_GAP, BOUND_CONTROL_Y)
                    .size(BOUND_BUTTON_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(this::hasBound, () -> detailText("clear"), this::clearBound)
                    .pos(CONTENT_X + AMOUNT_WIDTH + BOUND_BUTTON_WIDTH + CONTROL_GAP * 2, BOUND_CONTROL_Y)
                    .size(BOUND_BUTTON_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport
                    .button(() -> true, () -> detailText("back"), RecipeConfigModalWidget.this::closeDetails)
                    .pos(CONTENT_X, FOOTER_Y)
                    .size(BOUND_BUTTON_WIDTH, BUTTON_HEIGHT));
        }

        private int appendResources(ParentWidget<?> rows, List<Resource> resources, boolean isInput, int firstRow) {
            int row = firstRow;
            for (Resource resource : resources) {
                String name = resourceName(resource);
                String chance = BigDecimal.valueOf(resource.effectiveChance(), 2)
                    .stripTrailingZeros()
                    .toPlainString();
                String amount = StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.recipe.details." + (isInput ? "input_amount" : "output_amount"),
                    resource.amount(),
                    resource.key() instanceof FluidKey ? "L" : "",
                    chance);
                rows.child(new com.cleanroommc.modularui.widgets.ButtonWidget<>().overlay((ctx, x, y, w, h, theme) -> {
                    int border = selectedResource == resource && input == isInput
                        ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
                        : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor();
                    BorderedRect.draw(x, y, w, h, EnumColors.MAP_COLOR_ROW_BG.getColor(), border);
                    ModuleConfigModalSupport.drawTrimmedLine(
                        name,
                        x + RESOURCE_TEXT_PADDING,
                        y + RESOURCE_NAME_Y,
                        w - RESOURCE_TEXT_PADDING * 2,
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor());
                    ModuleConfigModalSupport.drawTrimmedLine(
                        amount,
                        x + RESOURCE_TEXT_PADDING,
                        y + RESOURCE_AMOUNT_Y,
                        w - RESOURCE_TEXT_PADDING * 2,
                        EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                })
                    .tooltip(t -> {
                        t.addLine(name);
                        t.addLine(amount);
                    })
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        selectedResource = resource;
                        input = isInput;
                        boundInput = hasBound() ? currentBoundText() : "";
                        amountField.setText(boundInput);
                        return true;
                    })
                    .pos(0, row++ * RESOURCE_ROW_HEIGHT)
                    .widthRelOffset(1f, -SCROLLBAR_GAP)
                    .height(RESOURCE_ROW_HEIGHT));
            }
            return row;
        }

        private String resourceName(Resource resource) {
            return resource.key() instanceof ItemStackWrapper item ? item.toStack(1)
                .getDisplayName()
                : ((FluidKey) resource.key()).fluid()
                    .getLocalizedName(resource.fluidStack());
        }

        private @Nullable InventoryBounds bounds() {
            AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
            return facility == null || selectedResource == null ? null : facility.getBound(selectedResource.key());
        }

        private boolean hasBound() {
            InventoryBounds bounds = bounds();
            return bounds != null && (input ? bounds.hasLow() : bounds.hasUpper());
        }

        private String currentBoundText() {
            InventoryBounds bounds = bounds();
            return bounds == null || !(input ? bounds.hasLow() : bounds.hasUpper()) ? detailText("unset")
                : Long.toString(input ? bounds.low() : bounds.upper());
        }

        private long parsedAmount() {
            try {
                return Long.parseLong(amountField.getText());
            } catch (NumberFormatException invalid) {
                return -1L;
            }
        }

        private boolean canApplyBound() {
            InventoryBounds bounds = bounds();
            long amount = parsedAmount();
            return bounds != null && amount >= 0L
                && (input ? amount <= bounds.upperOrDefault() : amount >= bounds.lowOrDefault());
        }

        private BoundKind boundKind(Resource resource) {
            boolean item = resource.key() instanceof ItemStackWrapper;
            return input ? (item ? BoundKind.ITEM_LOWER : BoundKind.FLUID_LOWER)
                : (item ? BoundKind.ITEM_UPPER : BoundKind.FLUID_UPPER);
        }

        private void applyBound() {
            Resource resource = selectedResource;
            if (resource == null || !canApplyBound()) return;
            CelestialClient.setInventoryBound(assetId, boundKind(resource), resource.key(), parsedAmount());
        }

        private void clearBound() {
            Resource resource = selectedResource;
            if (resource == null || !hasBound()) return;
            CelestialClient.clearInventoryBound(assetId, boundKind(resource), resource.key());
            boundInput = "";
            amountField.setText(boundInput);
        }
    }

    private boolean canUseRow(int rowIndex) {
        return canConfigureRecipes() && slotAtRow(rowIndex) != null;
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

    private void removeSlot(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0 || slotAtRow(rowIndex) == null) return;
        settingsGroupSelector.closeMenu();
        boolean selectedOverlay = editor.selectedIndex() == slotIndex && isRecipeRenameOpen();
        if (!editor.remove(slotIndex)) return;
        if (selectedOverlay) {
            closeRecipeRename();
        }
        page = Math.min(page, maxPage());
    }

    private void updateSlot(int rowIndex, SavedRecipe slot) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0) return;
        updateSlotIndex(slotIndex, slot);
    }

    private void updateSlotIndex(int slotIndex, SavedRecipe slot) {
        if (slotIndex < 0) return;
        editor.update(slotIndex, slot);
    }

    private void cycleMode() {
        if (selectedRecipe() == null) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        editor.cycleMode();
    }

    private void cycleNotDoablePolicy() {
        if (selectedRecipe() == null) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        editor.cycleNotDoablePolicy();
    }

    private void addRecipe() {
        ModuleInstance module = selectedModule();
        if (module == null) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;
        GTRecipeInputScreen.open(module, editor::add);
    }

    private void save() {
        CelestialClient.replaceRecipeBook(assetId, editor.moduleId(), editor.replacement());
        controller.close();
    }

    private void startCopySettingsPicker() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance source = selectedModule();
        if (facility == null || source == null || tilePickerController == null) return;
        settingsGroupSelector.closeMenu();
        closeRecipeRename();
        CelestialClient.replaceRecipeBook(assetId, editor.moduleId(), editor.replacement());
        controller.close();
        tilePickerController.start("Copy module settings", "Copy", coord -> {
            ModuleInstance target = facility.stationLayout()
                .moduleAt(coord);
            return target != null && facility.canCopyModuleRuntimeSettings(source, target);
        },
            coord -> StationTilePickerController.normalizeModuleTarget(facility, coord),
            targets -> CelestialClient
                .copyModuleSettings(assetId, source.id, StationTilePickerController.moduleIds(facility, targets)));
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
        return canConfigureRecipes() && (page + 1) * ROWS_PER_PAGE < editor.size();
    }

    private int maxPage() {
        int size = editor.size();
        return size == 0 ? 0 : (size - 1) / ROWS_PER_PAGE;
    }

    private void beginRecipeRename(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        SavedRecipe slot = slotAtRow(rowIndex);
        if (slotIndex < 0 || slot == null) return;
        settingsGroupSelector.closeMenu();
        if (!editor.select(slotIndex)) return;
        renameOpen = true;
        recipeNameInput = slot.displayName()
            .isBlank() ? RecipeSlotUiModel.slotTitle(slot) : slot.displayName();
        syncRecipeNameFieldText();
        focusRecipeNameField();
    }

    private boolean isRecipeRenameOpen() {
        return renameOpen && editor.selectedRecipe() != null;
    }

    private boolean canSaveRecipeName() {
        return isRecipeRenameOpen() && !currentRecipeNameInput().trim()
            .isEmpty();
    }

    private void saveRecipeName() {
        if (!isRecipeRenameOpen()) return;
        editor.rename(editor.selectedIndex(), currentRecipeNameInput());
        closeRecipeRename();
    }

    private void clearRecipeName() {
        if (!isRecipeRenameOpen()) return;
        editor.rename(editor.selectedIndex(), "");
        closeRecipeRename();
    }

    private void closeRecipeRename() {
        renameOpen = false;
        recipeNameInput = "";
        syncRecipeNameFieldText();
    }

    private TextFieldWidget createRecipeNameField() {
        return new TextFieldWidget().setMaxLength(64)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background((ctx, x, y, w, h, ignoredTheme) -> {
                if (!isRecipeRenameOpen()) return;
                BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            })
            .value(
                new StringValue.Dynamic(() -> recipeNameInput, input -> recipeNameInput = input == null ? "" : input))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> isRecipeRenameOpen());
    }

    private String currentRecipeNameInput() {
        return recipeNameField != null ? recipeNameField.getText() : recipeNameInput;
    }

    private void syncRecipeNameFieldText() {
        if (recipeNameField != null) recipeNameField.setText(recipeNameInput);
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

    private String modeLabel() {
        return RecipeSlotUiModel.modeLabel(editor.mode());
    }

    private String notDoablePolicyLabel() {
        String valueKey = switch (editor.notDoablePolicy()) {
            case SKIP -> "galaxia.gui.station.recipe.not_doable_policy.skip";
            case BACK_TO_BEGINNING -> "galaxia.gui.station.recipe.not_doable_policy.back_to_beginning";
        };
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.station.recipe.not_doable_policy.label",
            StatCollector.translateToLocal(valueKey));
    }

    private @Nullable SavedRecipe slotAtRow(int rowIndex) {
        return editor.recipeAt(slotIndexForRow(rowIndex));
    }

    private int slotIndexForRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= ROWS_PER_PAGE) return -1;
        return page * ROWS_PER_PAGE + rowIndex;
    }

    private @Nullable FacilityModuleRegistry.Definition.Recipe selectedRecipe() {
        ModuleInstance module = selectedModule();
        return module == null ? null : module.recipe();
    }

    private @Nullable ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
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
