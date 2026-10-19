package com.gtnewhorizons.galaxia.client.gui.station;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.compat.recipe.NativeRecipeWidget;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;

/**
 * Recipes a processing module runs, edited as a draft that Save sends to the station, and for one recipe the native
 * NEI view where the inventory bounds of its inputs and outputs are set
 */
final class RecipeConfigWindow implements ModuleConfigWindow {

    static final String RENAME_ID = "station.config.rename";

    private static final int INDEX_WIDTH = 14;
    private static final int TOGGLE_WIDTH = 30;
    private static final int NAME_WIDTH = 148;
    private static final int DETAILS_WIDTH = 46;
    private static final int FIELD_WIDTH = 40;
    private static final int REMOVE_WIDTH = 14;
    private static final int LIST_HEIGHT = 6 * (GalaxiaUi.BUTTON_H + GalaxiaUi.GAP);
    private static final int RECIPE_VIEW_WIDTH = 300;
    private static final int RECIPE_VIEW_HEIGHT = 140;
    private static final int RENAME_WIDTH = 150;
    private static final int BOUND_WIDTH = 100;
    private static final int MAX_BOUND_DIGITS = 19;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final @Nullable StationTilePickerController tilePickerController;
    private final SettingsGroupSelector settingsGroupSelector;
    private final RecipeBookEditorModel editor;
    private int builtSize = -1;
    private @Nullable SavedRecipe detailsRecipe;
    private @Nullable Resource selectedResource;
    private boolean selectedInput;
    private String boundInput = "";
    private @Nullable TextFieldWidget boundField;
    private int renameIndex = -1;
    private String recipeNameInput = "";
    private @Nullable TextFieldWidget recipeNameField;

    RecipeConfigWindow(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        @Nullable StationTilePickerController tilePickerController, RecipeBookEditorModel editor) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
        this.editor = editor;
        // Finish native dropdown input before disposing its configuration owner.
        this.settingsGroupSelector = new SettingsGroupSelector(assetId, controller, () -> {
            ModuleInstance module = selectedModule();
            return module != null ? module.kind() : null;
        }, () -> controller.requestRetargetTo(null));
    }

    @Override
    public String title() {
        ModuleInstance module = selectedModule();
        return module != null ? StationUiData.moduleTitle(module, "galaxia.gui.station.config.purpose.recipes")
            : tr("galaxia.gui.station.config.purpose.recipes");
    }

    @Override
    public void update() {
        settingsGroupSelector.update();
        // Added and removed recipes change the rows; values follow on their own
        if (detailsRecipe == null && editor.size() != builtSize) controller.rebuild();
    }

    @Override
    public void build(GalaxiaUi.Column column) {
        if (detailsRecipe != null) buildDetails(column, detailsRecipe);
        else buildList(column);
    }

    // region Recipe list

    private void buildList(GalaxiaUi.Column column) {
        builtSize = editor.size();
        column.add(settingsGroupSelector.row());
        column.row(
            GalaxiaUi.colored(
                () -> StatCollector.translateToLocalFormatted("galaxia.gui.station.recipe.slots", editor.size()),
                GalaxiaGuiStyle.TEXT_DIM),
            GalaxiaUi.button(this::modeLabel, 96, this::canConfigureRecipes, this::cycleMode),
            GalaxiaUi.button(this::notDoablePolicyLabel, 140, this::canConfigureRecipes, this::cycleNotDoablePolicy));
        if (editor.size() == 0) {
            column.dim(tr("galaxia.gui.station.recipe.empty"));
        } else {
            column.add(
                GalaxiaUi.row()
                    .child(header("#", INDEX_WIDTH))
                    .child(header(tr("galaxia.gui.station.recipe.column.on"), TOGGLE_WIDTH))
                    .child(header(tr("galaxia.gui.station.recipe.column.flow"), NAME_WIDTH))
                    .child(header("", DETAILS_WIDTH))
                    .child(header(tr("galaxia.gui.station.recipe.column.priority"), FIELD_WIDTH))
                    .child(header(tr("galaxia.gui.station.recipe.column.request"), FIELD_WIDTH)));
            ListWidget<IWidget, ?> list = GalaxiaUi.scroll(LIST_HEIGHT);
            Flow rows = GalaxiaUi.column();
            for (int slot = 0; slot < editor.size(); slot++) rows.child(slotRow(slot));
            list.child(rows);
            column.add(list);
        }
        column.row(
            GalaxiaUi.button(tr("galaxia.gui.station.recipe.add"), this::canAddRecipe, this::addRecipe)
                .name("recipe.add"),
            GalaxiaUi
                .button(tr("galaxia.gui.station.copy_settings"), this::canCopySettings, this::startCopySettingsPicker),
            GalaxiaUi.button(tr("galaxia.gui.station.recipe.save"), this::canConfigureRecipes, this::save)
                .name("recipe.save"));
    }

    private TextWidget<?> header(String text, int width) {
        return GalaxiaUi.dim(text)
            .width(width)
            .alignment(Alignment.Center);
    }

    private Flow slotRow(int slot) {
        return GalaxiaUi.row()
            .child(
                GalaxiaUi.dim(Integer.toString(slot + 1))
                    .width(INDEX_WIDTH)
                    .alignment(Alignment.Center))
            .child(
                GalaxiaUi
                    .button(
                        () -> tr(
                            recipe(slot) != null && recipe(slot).enabled() ? "galaxia.gui.common.on"
                                : "galaxia.gui.common.off"),
                        TOGGLE_WIDTH,
                        () -> canUseSlot(slot),
                        () -> toggleEnabled(slot))
                    .name("recipe.toggle." + slot))
            .child(nameButton(slot))
            .child(
                GalaxiaUi
                    .button(
                        () -> tr("galaxia.gui.station.recipe.details.open"),
                        DETAILS_WIDTH,
                        () -> canUseSlot(slot),
                        () -> openDetails(slot))
                    .name("recipe.details." + slot))
            .child(numberField(slot, Field.PRIORITY))
            .child(numberField(slot, Field.REQUEST_AMOUNT))
            .child(
                GalaxiaUi.button(() -> "X", REMOVE_WIDTH, () -> canUseSlot(slot), () -> removeSlot(slot))
                    .tooltip(t -> t.addLine(tr("galaxia.gui.station.recipe.remove"))));
    }

    /** The recipe's name, clicked to rename it */
    private ButtonWidget<?> nameButton(int slot) {
        IDrawable on = IKey.dynamic(
            () -> recipe(slot) == null ? "" : GalaxiaUi.clip(RecipeSlotUiModel.slotTitle(recipe(slot)), NAME_WIDTH - 4))
            .color(GalaxiaGuiStyle.TEXT)
            .alignment(Alignment.CenterLeft);
        IDrawable off = IKey.dynamic(
            () -> recipe(slot) == null ? "" : GalaxiaUi.clip(RecipeSlotUiModel.slotTitle(recipe(slot)), NAME_WIDTH - 4))
            .color(GalaxiaGuiStyle.TEXT_DIM)
            .alignment(Alignment.CenterLeft);
        return GalaxiaUi
            .flatButton(NAME_WIDTH, GalaxiaUi.BUTTON_H, () -> { if (canUseSlot(slot)) beginRecipeRename(slot); })
            .overlay(
                new com.cleanroommc.modularui.drawable.DynamicDrawable(
                    () -> recipe(slot) != null && recipe(slot).enabled() ? on : off))
            .tooltip(t -> t.addLine(tr("galaxia.gui.station.recipe.rename_hint")));
    }

    private TextFieldWidget numberField(int slot, Field field) {
        TextFieldWidget widget = GalaxiaUi.textField(null, FIELD_WIDTH);
        widget.setMaxLength(6)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(field.defaultValue)
            .setNumbers(field.min, field.max)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .value(new StringValue.Dynamic(() -> fieldText(slot, field), text -> setField(slot, field, text)))
            .setFocusOnGuiOpen(false);
        return widget;
    }

    private String fieldText(int slot, Field field) {
        SavedRecipe recipe = recipe(slot);
        return recipe == null ? "" : Integer.toString(field.value(recipe));
    }

    private void setField(int slot, Field field, String text) {
        SavedRecipe recipe = recipe(slot);
        if (recipe == null || !canUseSlot(slot)) return;
        int value = RecipeSlotUiModel.parseIntOrCurrent(text, field.value(recipe), field.min, field.max);
        editor.update(slot, field.updated(recipe, value));
    }

    private void toggleEnabled(int slot) {
        SavedRecipe recipe = recipe(slot);
        if (recipe == null) return;
        editor.update(
            slot,
            new SavedRecipe(
                recipe.recipe(),
                !recipe.enabled(),
                recipe.requestAmount(),
                recipe.priority(),
                recipe.orderSize(),
                recipe.displayName()));
    }

    private void removeSlot(int slot) {
        if (recipe(slot) == null) return;
        settingsGroupSelector.closeMenu();
        if (renameIndex == slot) closeRecipeRename();
        editor.remove(slot);
    }

    private boolean canUseSlot(int slot) {
        return canConfigureRecipes() && recipe(slot) != null;
    }

    private boolean canConfigureRecipes() {
        return detailsRecipe == null && renameIndex < 0 && selectedRecipe() != null;
    }

    private boolean canAddRecipe() {
        return canConfigureRecipes() && editor.canAdd();
    }

    private boolean canCopySettings() {
        AutomatedFacility facility = StationUiData.facility(assetId);
        ModuleInstance module = selectedModule();
        return tilePickerController != null && canConfigureRecipes()
            && !settingsGroupSelector.isBlockingModuleControls()
            && facility != null
            && facility.stationLayout() != null
            && module != null
            && FacilityModuleRegistry.get(module.kind())
                .settingsGroups();
    }

    private void cycleMode() {
        if (selectedRecipe() == null) return;
        settingsGroupSelector.closeMenu();
        editor.cycleMode();
    }

    private void cycleNotDoablePolicy() {
        if (selectedRecipe() == null) return;
        settingsGroupSelector.closeMenu();
        editor.cycleNotDoablePolicy();
    }

    private void addRecipe() {
        ModuleInstance module = selectedModule();
        if (module == null) return;
        settingsGroupSelector.closeMenu();
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;
        GTRecipeInputScreen.open(module, editor::add);
    }

    private void save() {
        CelestialClient.replaceRecipeBook(assetId, editor.moduleId(), editor.replacement());
        controller.close();
    }

    private void startCopySettingsPicker() {
        AutomatedFacility facility = StationUiData.facility(assetId);
        ModuleInstance source = selectedModule();
        if (facility == null || source == null || tilePickerController == null) return;
        settingsGroupSelector.closeMenu();
        CelestialClient.replaceRecipeBook(assetId, editor.moduleId(), editor.replacement());
        controller.close();
        tilePickerController
            .start(tr("galaxia.gui.station.recipe.copy_title"), tr("galaxia.gui.station.copy"), coord -> {
                ModuleInstance target = facility.stationLayout()
                    .moduleAt(coord);
                return target != null && facility.canCopyModuleRuntimeSettings(source, target);
            },
                coord -> StationTilePickerController.normalizeModuleTarget(facility, coord),
                targets -> CelestialClient
                    .copyModuleSettings(assetId, source.id, StationTilePickerController.moduleIds(facility, targets)));
    }

    private String modeLabel() {
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.station.recipe.mode",
            editor.mode()
                .name());
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

    // endregion

    // region Rename

    private void beginRecipeRename(int slot) {
        SavedRecipe recipe = recipe(slot);
        WindowLayer layer = WindowLayer.current();
        if (recipe == null || layer == null) return;
        settingsGroupSelector.closeMenu();
        if (!editor.select(slot)) return;
        renameIndex = slot;
        recipeNameInput = recipe.displayName()
            .isBlank() ? RecipeSlotUiModel.slotTitle(recipe) : recipe.displayName();
        layer.open(
            WindowSpec.of(RENAME_ID, "galaxia.gui.station.recipe.rename_title")
                .owner(ModuleConfigModalController.WINDOW_ID)
                .modal()
                .centered()
                .onClose(this::closeRecipeRename)
                .body(this::fillRename));
    }

    private void fillRename(GalaxiaUi.Column column) {
        recipeNameField = GalaxiaUi.textField(null, RENAME_WIDTH)
            .setMaxLength(64)
            .autoUpdateOnChange(false)
            .value(
                new StringValue.Dynamic(() -> recipeNameInput, input -> recipeNameInput = input == null ? "" : input))
            .setFocusOnGuiOpen(false);
        column.dim(tr("galaxia.gui.station.recipe.name"))
            .add(recipeNameField)
            .row(
                GalaxiaUi.button(tr("galaxia.gui.station.recipe.details.clear"), this::clearRecipeName),
                GalaxiaUi.button(
                    tr("galaxia.gui.station.settings_group.save"),
                    this::canSaveRecipeName,
                    this::saveRecipeName));
    }

    private boolean canSaveRecipeName() {
        return renameIndex >= 0 && !currentRecipeNameInput().trim()
            .isEmpty();
    }

    private void saveRecipeName() {
        if (!canSaveRecipeName()) return;
        editor.rename(renameIndex, currentRecipeNameInput());
        closeRenameWindow();
    }

    private void clearRecipeName() {
        if (renameIndex < 0) return;
        editor.rename(renameIndex, "");
        closeRenameWindow();
    }

    private void closeRenameWindow() {
        WindowLayer layer = WindowLayer.current();
        if (layer != null) layer.close(RENAME_ID);
        closeRecipeRename();
    }

    private void closeRecipeRename() {
        renameIndex = -1;
        recipeNameInput = "";
        recipeNameField = null;
    }

    private String currentRecipeNameInput() {
        return recipeNameField != null ? recipeNameField.getText() : recipeNameInput;
    }

    // endregion

    // region Details

    private void openDetails(int slot) {
        SavedRecipe recipe = recipe(slot);
        if (recipe == null) return;
        settingsGroupSelector.closeMenu();
        detailsRecipe = recipe;
        selectedResource = null;
        boundInput = "";
        controller.rebuild();
    }

    private void closeDetails() {
        detailsRecipe = null;
        selectedResource = null;
        boundField = null;
        controller.rebuild();
    }

    private void buildDetails(GalaxiaUi.Column column, SavedRecipe saved) {
        RecipeHandlerRef nativeRecipe = GTRecipeMapId.nativeRecipe(saved.recipe());
        String title = saved.displayName()
            .isBlank() ? (nativeRecipe == null ? detailText("open") : nativeRecipe.handler.getRecipeName())
                : saved.displayName();
        column.add(GalaxiaUi.clipped(title, RECIPE_VIEW_WIDTH, GalaxiaGuiStyle.TEXT_TITLE));
        if (nativeRecipe == null) {
            column.dim(detailText("unavailable"));
        } else {
            NativeRecipeWidget nativeView = new NativeRecipeWidget(
                nativeRecipe,
                stack -> selectResource(
                    saved,
                    stack.item,
                    nativeRecipe.handler.getIngredientStacks(nativeRecipe.recipeIndex)
                        .contains(stack)));
            ListWidget<IWidget, ?> view = GalaxiaUi.scroll(RECIPE_VIEW_HEIGHT);
            view.child(nativeView);
            column.add(
                GalaxiaUi.well()
                    .child(view));
            column.add(
                GalaxiaUi
                    .colored(
                        () -> selectedResource == null ? detailText("select_resource") : "",
                        GalaxiaGuiStyle.TEXT_DIM)
                    .setEnabledIf(w -> selectedResource == null));
            column.add(
                GalaxiaUi
                    .text(
                        () -> selectedResource == null ? ""
                            : GalaxiaUi.clip(resourceName(selectedResource), RECIPE_VIEW_WIDTH))
                    .setEnabledIf(w -> selectedResource != null));
            column.add(
                GalaxiaUi
                    .colored(
                        () -> selectedResource == null ? ""
                            : StatCollector.translateToLocalFormatted(
                                "galaxia.gui.station.recipe.details.bound",
                                detailText(selectedInput ? "lower" : "upper"),
                                currentBoundText()),
                        GalaxiaGuiStyle.TEXT_DIM)
                    .setEnabledIf(w -> selectedResource != null));
            boundField = GalaxiaUi.textField(null, BOUND_WIDTH)
                .setMaxLength(MAX_BOUND_DIGITS)
                .setPattern(INTEGER_PATTERN)
                .autoUpdateOnChange(true)
                .value(new StringValue.Dynamic(() -> boundInput, value -> boundInput = value))
                .setFocusOnGuiOpen(false);
            Flow bounds = GalaxiaUi.row()
                .child(boundField)
                .child(GalaxiaUi.button(detailText("set"), this::canApplyBound, this::applyBound))
                .child(GalaxiaUi.button(detailText("clear"), this::hasBound, this::clearBound));
            bounds.setEnabledIf(w -> selectedResource != null);
            column.add(bounds);
        }
        column.add(
            GalaxiaUi.button(detailText("back"), this::closeDetails)
                .name("recipe.details.back"));
    }

    private void selectResource(SavedRecipe saved, ItemStack stack, boolean isInput) {
        FluidStack fluid = StackInfo.getFluid(stack);
        InventoryKey key = fluid == null ? ItemStackWrapper.of(stack) : FluidKey.of(fluid);
        List<Resource> resources = fluid == null ? (isInput ? saved.recipe()
            .itemInputs()
            : saved.recipe()
                .itemOutputs())
            : (isInput ? saved.recipe()
                .fluidInputs()
                : saved.recipe()
                    .fluidOutputs());
        for (Resource resource : resources) {
            if (!resource.key()
                .equals(key)) continue;
            selectedResource = resource;
            selectedInput = isInput;
            boundInput = hasBound() ? currentBoundText() : "";
            if (boundField != null) boundField.setText(boundInput);
            return;
        }
    }

    private static String resourceName(Resource resource) {
        return resource.key() instanceof ItemStackWrapper item ? item.toStack(1)
            .getDisplayName()
            : ((FluidKey) resource.key()).fluid()
                .getLocalizedName(resource.fluidStack());
    }

    private @Nullable InventoryBounds bounds() {
        AutomatedFacility facility = StationUiData.facility(assetId);
        return facility == null || selectedResource == null ? null : facility.getBound(selectedResource.key());
    }

    private boolean hasBound() {
        InventoryBounds bounds = bounds();
        return bounds != null && (selectedInput ? bounds.hasLow() : bounds.hasUpper());
    }

    private String currentBoundText() {
        InventoryBounds bounds = bounds();
        return bounds == null || !(selectedInput ? bounds.hasLow() : bounds.hasUpper()) ? detailText("unset")
            : Long.toString(selectedInput ? bounds.low() : bounds.upper());
    }

    private long parsedAmount() {
        try {
            return Long.parseLong(boundField == null ? boundInput : boundField.getText());
        } catch (NumberFormatException invalid) {
            return -1L;
        }
    }

    private boolean canApplyBound() {
        InventoryBounds bounds = bounds();
        long amount = parsedAmount();
        return bounds != null && amount >= 0L
            && (selectedInput ? amount <= bounds.upperOrDefault() : amount >= bounds.lowOrDefault());
    }

    private BoundKind boundKind(Resource resource) {
        boolean item = resource.key() instanceof ItemStackWrapper;
        return selectedInput ? (item ? BoundKind.ITEM_LOWER : BoundKind.FLUID_LOWER)
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
        if (boundField != null) boundField.setText(boundInput);
    }

    // endregion

    private @Nullable SavedRecipe recipe(int slot) {
        return editor.recipeAt(slot);
    }

    private @Nullable FacilityModuleRegistry.Definition.Recipe selectedRecipe() {
        ModuleInstance module = selectedModule();
        return module == null ? null : module.recipe();
    }

    private @Nullable ModuleInstance selectedModule() {
        return StationUiData.module(assetId, controller.moduleId());
    }

    private static String detailText(String key) {
        return tr("galaxia.gui.station.recipe.details." + key);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
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
