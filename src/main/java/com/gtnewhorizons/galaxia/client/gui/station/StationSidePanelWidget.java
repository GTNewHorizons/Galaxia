package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationSidePanelWidget extends ParentWidget<StationSidePanelWidget> {

    private static final int CONTENT_PADDING = 10;
    private static final int TITLE_SECTION_GAP = 6;
    private static final int SECTION_GAP = 8;
    private static final int DESTROY_BUTTON_X = 10;
    private static final int DESTROY_BUTTON_Y = 205;
    private static final int ACTION_BUTTON_WIDTH = 88;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int ACTION_BUTTON_COLUMN_GAP = 8;
    private static final int ACTION_BUTTON_ROW_GAP = 6;
    private static final int ACTION_GRID_WIDTH = ACTION_BUTTON_WIDTH * ModulePanelActionLayout.COLUMNS
        + ACTION_BUTTON_COLUMN_GAP;
    private static final int DESTROY_MULTIPLE_TOGGLE_HEIGHT = 14;
    private static final int DESTROY_MULTIPLE_TOGGLE_Y = DESTROY_BUTTON_Y - DESTROY_MULTIPLE_TOGGLE_HEIGHT - 4;
    private static final int DESTROY_MULTIPLE_CHECKBOX_SIZE = 10;
    private static final int DESTROY_BUTTON_WIDTH = ACTION_GRID_WIDTH;
    private static final int DESTROY_BUTTON_HEIGHT = 20;
    private static final int ACTION_BUTTON_START_Y = DESTROY_BUTTON_Y + DESTROY_BUTTON_HEIGHT + 8;
    private static final int BUTTON_TEXT_BASELINE_OFFSET = 1;
    private static final int HAMMER_ENERGY_BAR_HEIGHT = 8;
    private static final int HAMMER_ENERGY_BAR_TOP_GAP = 4;

    private final @Nullable CelestialAsset.ID assetId;
    private final StationMapWidget map;
    private final @Nullable StationTilePickerController tilePickerController;
    private final @Nullable ModuleConfigModalController configController;
    private @Nullable StationTileCoord armedDestroySelection;
    private @Nullable HammerDispatchStatus.Status hammerDispatchStatus;
    private boolean destroyMultipleMode;

    public StationSidePanelWidget(@Nullable CelestialAsset.ID assetId, StationMapWidget map) {
        this(assetId, map, null, null);
    }

    public StationSidePanelWidget(@Nullable CelestialAsset.ID assetId, StationMapWidget map,
        @Nullable StationTilePickerController tilePickerController) {
        this(assetId, map, tilePickerController, null);
    }

    public StationSidePanelWidget(@Nullable CelestialAsset.ID assetId, StationMapWidget map,
        @Nullable StationTilePickerController tilePickerController,
        @Nullable ModuleConfigModalController configController) {
        this.assetId = assetId;
        this.map = map;
        this.tilePickerController = tilePickerController;
        this.configController = configController;
        child(
            createDestroyMultipleToggle().pos(DESTROY_BUTTON_X, DESTROY_MULTIPLE_TOGGLE_Y)
                .size(DESTROY_BUTTON_WIDTH, DESTROY_MULTIPLE_TOGGLE_HEIGHT));
        child(
            createDestroyButton().pos(DESTROY_BUTTON_X, DESTROY_BUTTON_Y)
                .size(DESTROY_BUTTON_WIDTH, DESTROY_BUTTON_HEIGHT));
        for (int slot = 0; slot < ModulePanelAction.values().length; slot++) {
            ModulePanelActionLayout.Cell cell = ModulePanelActionLayout.cellForIndex(slot);
            int actionX = DESTROY_BUTTON_X + cell.column() * (ACTION_BUTTON_WIDTH + ACTION_BUTTON_COLUMN_GAP);
            int actionY = ACTION_BUTTON_START_Y + cell.row() * (ACTION_BUTTON_HEIGHT + ACTION_BUTTON_ROW_GAP);
            int actionSlot = slot;
            child(
                createModuleActionButton(actionSlot).pos(actionX, actionY)
                    .size(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT));
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (map == null) return;
        StationTileCoord selected = map.selection();
        if (armedDestroySelection != null && !armedDestroySelection.equals(selected)) {
            armedDestroySelection = null;
            destroyMultipleMode = false;
        }
        refreshHammerDispatchStatus();
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (isPickerActive()) return;
        int x = 0;
        int y = 0;
        int width = getArea().width;
        int height = getArea().height;
        BorderedRect.draw(
            x,
            y,
            width,
            height,
            EnumColors.MAP_COLOR_STATION_PANEL_BG.getColor(),
            EnumColors.MAP_COLOR_STATION_PANEL_BORDER.getColor());

        AutomatedFacility facility = resolveFacility(assetId);
        int lineY = y + CONTENT_PADDING;
        lineY = drawLine(
            facility == null ? "Station Management" : facility.displayName(),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
        lineY += TITLE_SECTION_GAP;

        if (facility == null) {
            drawLine("No station selected", x + CONTENT_PADDING, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        lineY = drawLine(
            facility.kind.getDisplayName(),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY = drawLine(
            "Layer: " + map.visionLayer()
                .getDisplayName(),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = drawLine(
            "Modules: " + facility.modules()
                .size(),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        StationLayout layout = facility.stationLayout();
        lineY = drawLine(
            "Tiles: " + (layout == null ? 0 : layout.size()),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = drawLine(
            "Energy: " + facility.getEnergyStored() + "/" + facility.energyCapacity(),
            x + CONTENT_PADDING,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY += SECTION_GAP;

        StationTileCoord selected = map.selection();
        if (selected == null) {
            drawLine("No tile selected", x + CONTENT_PADDING, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }

        PlacedTile tile = layout == null ? null : layout.get(selected);
        ModuleInstance module = tile == null ? null : tile.module();
        lineY = ModuleStatusTextRegistry
            .collect(new ModuleStatusTextRegistry.Context(facility, selected, tile, module, hammerDispatchStatus))
            .draw(x + CONTENT_PADDING, lineY);
        if (module != null && module.component() instanceof ModuleHammer hammer) {
            drawHammerEnergyBar(
                hammer,
                x + CONTENT_PADDING,
                lineY + HAMMER_ENERGY_BAR_TOP_GAP,
                width - CONTENT_PADDING * 2);
        }
    }

    private static @Nullable AutomatedFacility resolveFacility(@Nullable CelestialAsset.ID assetId) {
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
    }

    private void refreshHammerDispatchStatus() {
        ModuleInstance module = selectedModule();
        hammerDispatchStatus = module != null && module.component() instanceof ModuleHammer
            ? CelestialClient.hammerDispatchStatus(assetId, module.id)
            : null;
    }

    private ButtonWidget<?> createDestroyButton() {
        return new ButtonWidget<>().background((ctx, x, y, w, h, ignoredTheme) -> {
            if (isPickerActive()) return;
            BorderedRect.draw(
                x,
                y,
                w,
                h,
                canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_DEFAULT.getColor()
                    : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor()
                    : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor());
        })
            .hoverBackground((ctx, x, y, w, h, ignoredTheme) -> {
                if (isPickerActive()) return;
                BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_HOVERED.getColor()
                        : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                    canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor()
                        : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor());
            })
            .overlay((ctx, x, y, w, h, ignoredTheme) -> {
                if (isPickerActive()) return;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String label = armedDestroySelection != null && armedDestroySelection.equals(map.selection())
                    ? "Confirm"
                    : "Destroy";
                int color = canDestroySelected() ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                    : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor();
                int textWidth = fr.getStringWidth(label);
                fr.drawStringWithShadow(
                    label,
                    x + (w - textWidth) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + BUTTON_TEXT_BASELINE_OFFSET,
                    color);
            })
            .onMousePressed(mouseButton -> {
                if (isPickerActive()) return false;
                if (mouseButton != 0 || !canDestroySelected()) return true;
                StationTileCoord selected = map.selection();
                if (!selected.equals(armedDestroySelection)) {
                    armedDestroySelection = selected;
                    destroyMultipleMode = false;
                    return true;
                }
                if (destroyMultipleMode && startDestroyPicker()) {
                    armedDestroySelection = null;
                    destroyMultipleMode = false;
                    return true;
                }
                destroySelected();
                armedDestroySelection = null;
                destroyMultipleMode = false;
                return true;
            });
    }

    private ButtonWidget<?> createDestroyMultipleToggle() {
        return new ButtonWidget<>()
            .background((ctx, x, y, w, h, ignoredTheme) -> drawDestroyMultipleToggle(x, y, w, h, false))
            .hoverBackground((ctx, x, y, w, h, ignoredTheme) -> drawDestroyMultipleToggle(x, y, w, h, true))
            .onMousePressed(mouseButton -> {
                if (isPickerActive() || armedDestroySelection == null || mouseButton != 0) return false;
                destroyMultipleMode = !destroyMultipleMode;
                return true;
            });
    }

    private void drawDestroyMultipleToggle(int x, int y, int width, int height, boolean hovered) {
        if (isPickerActive() || armedDestroySelection == null) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int boxY = y + (height - DESTROY_MULTIPLE_CHECKBOX_SIZE) / 2;
        BorderedRect.draw(
            x,
            boxY,
            DESTROY_MULTIPLE_CHECKBOX_SIZE,
            DESTROY_MULTIPLE_CHECKBOX_SIZE,
            hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
        if (destroyMultipleMode) {
            fr.drawStringWithShadow("X", x + 2, boxY + 1, EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
        }
        String label = fr.trimStringToWidth("Multiple", width - DESTROY_MULTIPLE_CHECKBOX_SIZE - 4);
        fr.drawStringWithShadow(
            label,
            x + DESTROY_MULTIPLE_CHECKBOX_SIZE + 4,
            y + (height - fr.FONT_HEIGHT) / 2 + BUTTON_TEXT_BASELINE_OFFSET,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private ButtonWidget<?> createModuleActionButton(int slot) {
        return new ButtonWidget<>().background((ctx, x, y, w, h, ignoredTheme) -> {
            if (isPickerActive()) return;
            ModulePanelAction action = actionAtSlot(slot);
            if (action == null) return;
            drawActionButtonBackground(x, y, w, h, true, false);
        })
            .hoverBackground((ctx, x, y, w, h, ignoredTheme) -> {
                if (isPickerActive()) return;
                ModulePanelAction action = actionAtSlot(slot);
                if (action == null) return;
                drawActionButtonBackground(x, y, w, h, true, true);
            })
            .overlay((ctx, x, y, w, h, ignoredTheme) -> {
                if (isPickerActive()) return;
                ModulePanelAction action = actionAtSlot(slot);
                if (action == null) return;
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String label = fr.trimStringToWidth(action.label(), w - 4);
                int textWidth = fr.getStringWidth(label);
                fr.drawStringWithShadow(
                    label,
                    x + (w - textWidth) / 2,
                    y + (h - fr.FONT_HEIGHT) / 2 + BUTTON_TEXT_BASELINE_OFFSET,
                    EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor());
            })
            .onMousePressed(mouseButton -> {
                if (isPickerActive() || mouseButton != 0) return false;
                ModulePanelAction action = actionAtSlot(slot);
                if (action == null) return true;
                runModuleAction(action);
                return true;
            })
            .setEnabledIf(w -> !isPickerActive() && actionAtSlot(slot) != null);
    }

    private void drawActionButtonBackground(int x, int y, int w, int h, boolean enabled, boolean hovered) {
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

    private @Nullable ModulePanelAction actionAtSlot(int slot) {
        if (slot < 0) return null;
        if (isCoreSelected()) return slot == 0 ? ModulePanelAction.CONFIG : null;
        ModuleInstance module = selectedModule();
        if (module == null) return null;
        FacilityModuleRegistry.Definition definition = FacilityModuleRegistry.get(module.kind());
        if (definition == null) return null;
        if (slot >= definition.panelActions()
            .size()) return null;
        return definition.panelActions()
            .get(slot);
    }

    private void runModuleAction(ModulePanelAction action) {
        if (configController == null) return;
        if (isCoreSelected()) {
            if (action == ModulePanelAction.CONFIG) configController.openCoreLogistics();
            return;
        }
        ModuleInstance module = selectedModule();
        if (module == null) return;
        switch (action) {
            case CONFIG -> openModuleConfig(module);
            case UPGRADE -> openModuleUpgrade(module);
        }
    }

    private void openModuleConfig(ModuleInstance module) {
        if (module.component() instanceof ModuleMiner) {
            configController.openMinerBlacklist(module.id);
        } else if (module.component() instanceof ModuleHammer) {
            configController.openHammer(module.id);
        } else if (module.recipe() != null) {
            configController.openRecipeConfig(module.id);
        } else if (module.component() instanceof ModuleDebugDataGenerator) {
            configController.openDebugDataGenerator(module.id);
        }
    }

    private void openModuleUpgrade(ModuleInstance module) {
        if (ModuleUpgradeUiModel.supports(module)) configController.openUpgrade(module.id);
    }

    private boolean canDestroySelected() {
        return !isPickerActive() && selectedModule() != null;
    }

    private @Nullable ModuleInstance selectedModule() {
        AutomatedFacility facility = resolveFacility(assetId);
        if (facility == null || map == null) return null;
        StationLayout layout = facility.stationLayout();
        StationTileCoord selected = map.selection();
        if (layout == null || selected == null) return null;
        ModuleInstance module = layout.moduleAt(selected);
        return module == null ? null : facility.moduleById(module.id);
    }

    private boolean isCoreSelected() {
        AutomatedFacility facility = resolveFacility(assetId);
        if (facility == null || map == null) return false;
        StationLayout layout = facility.stationLayout();
        StationTileCoord selected = map.selection();
        if (layout == null || selected == null) return false;
        PlacedTile tile = layout.get(selected);
        return tile != null && tile.isCore();
    }

    private void destroySelected() {
        ModuleInstance module = selectedModule();
        if (assetId == null || module == null) return;
        CelestialClient.requestModuleDeconstruction(assetId, module.id);
    }

    private boolean startDestroyPicker() {
        if (assetId == null || tilePickerController == null) return false;
        AutomatedFacility facility = resolveFacility(assetId);
        if (facility == null || facility.stationLayout() == null) return false;
        tilePickerController.start(
            "Destroy modules",
            "Destroy",
            coord -> canDestroyTarget(facility, coord),
            coord -> canonicalDestroyTarget(facility, coord),
            this::destroyTargets);
        tilePickerController.setVisualStyle(StationTilePickerController.VisualStyle.DECONSTRUCT);
        return true;
    }

    private void destroyTargets(List<StationTileCoord> targets) {
        if (assetId == null || targets == null || targets.isEmpty()) return;
        AutomatedFacility facility = resolveFacility(assetId);
        if (facility == null) return;
        Set<ModuleInstance.ID> moduleIds = new HashSet<>();
        for (StationTileCoord target : targets) {
            ModuleInstance module = moduleAt(facility, target);
            if (module != null) moduleIds.add(module.id);
        }
        List<ModuleInstance> modules = facility.modules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            ModuleInstance module = modules.get(i);
            if (moduleIds.contains(module.id)) CelestialClient.requestModuleDeconstruction(assetId, module.id);
        }
    }

    private static boolean canDestroyTarget(AutomatedFacility facility, StationTileCoord coord) {
        return moduleAt(facility, coord) != null;
    }

    private static @Nullable ModuleInstance moduleAt(AutomatedFacility facility, StationTileCoord coord) {
        if (facility == null || coord == null) return null;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return null;
        PlacedTile tile = layout.get(coord);
        if (tile == null || tile.isCore() || tile.module() == null) return null;
        return facility.moduleById(tile.module().id);
    }

    private static @Nullable StationTileCoord canonicalDestroyTarget(AutomatedFacility facility,
        StationTileCoord coord) {
        if (facility == null || coord == null) return null;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return null;
        PlacedTile tile = layout.get(coord);
        if (tile == null || tile.isCore() || tile.module() == null) return null;
        ModuleInstance.ID moduleId = tile.module().id;
        StationTileCoord canonical = null;
        for (StationTileCoord candidate : layout.snapshot()
            .keySet()) {
            PlacedTile candidateTile = layout.get(candidate);
            if (candidateTile == null || candidateTile.module() == null || !candidateTile.module().id.equals(moduleId))
                continue;
            if (canonical == null || candidate.dx() < canonical.dx()
                || candidate.dx() == canonical.dx() && candidate.dy() < canonical.dy()) {
                canonical = candidate;
            }
        }
        return canonical;
    }

    private static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    private static void drawHammerEnergyBar(ModuleHammer hammer, int x, int y, int width) {
        long capacity = Math.max(1L, hammer.energyCapacity());
        int fillW = (int) (width * hammer.energyStored() / capacity);
        Gui.drawRect(x, y, x + width, y + HAMMER_ENERGY_BAR_HEIGHT, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(
            x,
            y,
            x + fillW,
            y + HAMMER_ENERGY_BAR_HEIGHT,
            EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
    }

    private boolean isPickerActive() {
        return tilePickerController != null && tilePickerController.isActive();
    }
}
