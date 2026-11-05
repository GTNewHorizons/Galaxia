package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaWindow;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Management of an automated station or outpost, opened as a window over the galactic map: the module layout with its
 * side panel, the inventory and the module settings
 */
public final class StationManagementScreen {

    /** Space left around the station window, which otherwise takes the whole map like the workspace it is */
    private static final int MARGIN = 20;
    private static final int LEFT_PANEL_WIDTH = 216;
    private static final int PADDING = 12;
    private static final int PICKER_WIDTH = 190;
    private static final int PICKER_CONFIRM_WIDTH = 76;

    private static volatile @Nullable BuildPickerRequest pendingBuildPickerRequest;

    private StationManagementScreen() {}

    /** Id of the window of a station, for finding or closing it */
    public static String windowId(CelestialAsset.ID assetId) {
        return "station." + assetId;
    }

    public static void open(CelestialAsset.ID assetId) {
        open(assetId, false);
    }

    public static void open(CelestialAsset.ID assetId, boolean creativeBuildMode) {
        pendingBuildPickerRequest = null;
        openScreen(assetId, creativeBuildMode);
    }

    private static void openScreen(CelestialAsset.ID assetId, boolean creativeBuildMode) {
        CelestialAsset asset = CelestialClient.getByAssetId(assetId);
        if (asset == null || asset.kind == CelestialAsset.Kind.SATELLITE) return;
        WindowLayer layer = WindowLayer.current();
        if (layer == null) return;
        String id = windowId(assetId);
        // Opened again so a pending build picker starts on a fresh view
        layer.close(id);
        int width = layer.getArea().width - MARGIN * 2;
        int height = layer.getArea().height - MARGIN * 2 - GalaxiaUi.ICON - GalaxiaUi.PAD * 2 - GalaxiaUi.GAP;
        layer.open(
            WindowSpec.of(id, IKey.str(asset.displayName()))
                .centered()
                .maxHeight(height)
                .body(column -> column.add(build(assetId, creativeBuildMode, width, height))));
    }

    static void openBuildPicker(CelestialAsset.ID assetId, FacilityModuleKind kind, boolean creativeBuildMode) {
        pendingBuildPickerRequest = BuildPickerRequest.create(
            assetId,
            kind,
            kind.defaultShape(),
            kind.defaultTier(),
            null,
            MinerFocusTier.NONE,
            null,
            creativeBuildMode);
        openScreen(assetId, creativeBuildMode);
    }

    static void openBuildPicker(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape, ModuleTier tier,
        HammerVariant hammerVariant, MinerFocusTier minerFocusTier, @Nullable SettingsGroup.ID settingsGroupId,
        boolean creativeBuildMode) {
        pendingBuildPickerRequest = BuildPickerRequest
            .create(assetId, kind, shape, tier, hammerVariant, minerFocusTier, settingsGroupId, creativeBuildMode);
        openScreen(assetId, creativeBuildMode);
    }

    static void openCopyBuildPicker(CelestialAsset.ID assetId, ModuleInstance.ID sourceModuleId,
        boolean creativeBuildMode) {
        pendingBuildPickerRequest = BuildPickerRequest.copy(assetId, sourceModuleId, creativeBuildMode);
        openScreen(assetId, creativeBuildMode);
    }

    /**
     * The content of a station window
     *
     * @param assetId           The station
     * @param creativeBuildMode Whether modules are built without materials
     * @param width             Width of the content
     * @param height            Height of the content
     */
    public static ParentWidget<?> build(CelestialAsset.ID assetId, boolean creativeBuildMode, int width, int height) {
        ParentWidget<?> panel = new ParentWidget<>().name("station.content")
            .size(width, height)
            .background(EnumTextures.GUI_WELL.getImage());
        int overlayX = LEFT_PANEL_WIDTH + PADDING * 2;
        int overlayY = PADDING + GalaxiaUi.BUTTON_H + GalaxiaUi.GAP;
        int columnWidth = LEFT_PANEL_WIDTH - PADDING;

        if (CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility) {
            StationTilePickerController tilePickerController = new StationTilePickerController();
            ModuleConfigModalController configController = new ModuleConfigModalController(
                panel,
                assetId,
                overlayX,
                overlayY,
                tilePickerController);
            panel.onUpdateListener(w -> configController.processDeferredActions(), true);
            StationMapWidget map = new StationMapWidget(
                assetId,
                coord -> ModulePickerScreen.open(assetId, coord, creativeBuildMode),
                tile -> configController.requestRetargetTo(tile.isCore() ? null : tile.module()),
                LEFT_PANEL_WIDTH + PADDING,
                PADDING,
                PADDING,
                StationVisionLayer.BASE,
                (x, y) -> coveredByWindow(panel, assetId, x, y),
                tilePickerController);
            StationSidePanel side = new StationSidePanel(
                assetId,
                map,
                tilePickerController,
                configController,
                columnWidth);
            panel.child(
                map.left(0)
                    .top(0)
                    .widthRel(1f)
                    .heightRel(1f));
            StationInventoryWindow inventory = new StationInventoryWindow(
                assetId,
                configController,
                () -> !tilePickerController.isActive());
            panel.child(
                sideColumn(
                    height,
                    side.overview(
                        inventory.toggleButton(),
                        moduleId -> openCopyBuildPicker(assetId, moduleId, creativeBuildMode)),
                    side.details()).pos(PADDING, PADDING));
            panel.child(pickerControls(tilePickerController).pos(overlayX, PADDING * 2));
            startPendingBuildPicker(assetId, tilePickerController);
        } else {
            ModuleConfigModalController configController = new ModuleConfigModalController(
                panel,
                assetId,
                overlayX,
                overlayY,
                null);
            panel.onUpdateListener(w -> configController.processDeferredActions(), true);
            StationSidePanel side = new StationSidePanel(assetId, null, null, null, columnWidth);
            Flow overview = side
                .overview(new StationInventoryWindow(assetId, configController, () -> true).toggleButton(), null);
            overview.child(
                GalaxiaUi.button(
                    StatCollector.translateToLocal("galaxia.gui.station.items"),
                    configController::openStationLogistics));
            panel.child(sideColumn(height, overview).pos(PADDING, PADDING));
        }
        return panel;
    }

    /** The side panels stacked on the left, scrolling when they do not fit the window */
    private static ListWidget<IWidget, ?> sideColumn(int height, Flow... panels) {
        Flow column = GalaxiaUi.column()
            .childPadding(PADDING / 2);
        for (Flow part : panels) column.child(part);
        ListWidget<IWidget, ?> scroll = GalaxiaUi.scroll(height - PADDING * 2);
        scroll.child(column);
        return scroll;
    }

    /** Title, count and buttons of the tile picker, shown while tiles are being chosen */
    private static Flow pickerControls(StationTilePickerController controller) {
        Flow panel = GalaxiaUi.column()
            .padding(GalaxiaUi.PAD)
            .background(EnumTextures.GUI_PANEL.getImage());
        panel
            .child(
                GalaxiaUi.colored(() -> GalaxiaUi.clip(controller.title(), PICKER_WIDTH), GalaxiaGuiStyle.TEXT_TITLE))
            .child(
                GalaxiaUi.row()
                    .child(
                        GalaxiaUi.text(
                            () -> StatCollector.translateToLocalFormatted(
                                "galaxia.gui.station.picker_controls.selected",
                                controller.selectedCount())))
                    .child(
                        GalaxiaUi
                            .colored(
                                () -> StatCollector.translateToLocalFormatted(
                                    "galaxia.gui.station.picker_controls.anchor",
                                    controller.footprintRotation() + 1),
                                GalaxiaGuiStyle.TEXT_DIM)
                            .setEnabledIf(w -> controller.rotatesFootprint())))
            .child(
                GalaxiaUi.row()
                    .child(
                        GalaxiaUi.button(
                            StatCollector.translateToLocal("galaxia.gui.common.cancel"),
                            controller::isActive,
                            controller::cancel))
                    .child(
                        GalaxiaUi.button(
                            controller::confirmLabel,
                            PICKER_CONFIRM_WIDTH,
                            controller::canConfirm,
                            controller::confirm)));
        panel.setEnabledIf(w -> controller.isActive());
        return panel;
    }

    /** Whether a window other than the station's own lies over this point of the screen */
    private static boolean coveredByWindow(ParentWidget<?> content, CelestialAsset.ID assetId, int screenX,
        int screenY) {
        WindowLayer layer = WindowLayer.of(content);
        if (layer == null) return false;
        int[] local = GalaxiaUi.toLocal(layer, screenX, screenY);
        GalaxiaWindow top = layer.windowAt(local[0], local[1]);
        return top != null && !top.id()
            .equals(windowId(assetId));
    }

    private static void startPendingBuildPicker(CelestialAsset.ID assetId, StationTilePickerController controller) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        BuildPickerRequest request = pendingBuildPickerRequest;
        if (request == null || assetId == null || !assetId.equals(request.assetId())) return;
        pendingBuildPickerRequest = null;
        if (!(com.gtnewhorizons.galaxia.client.CelestialClient
            .getByAssetId(assetId) instanceof AutomatedFacility facility)) {
            return;
        }
        ModuleInstance copySource = request.copySource(facility);
        FacilityModuleKind kind = copySource == null ? request.kind() : copySource.kind();
        ModuleShape shape = copySource == null ? request.shape() : copySource.shape();
        ModuleTier tier = copySource == null ? request.tier() : copySource.tier();
        if (kind == null || shape == null || tier == null) return;
        controller
            .startWithPlacements(
                StatCollector.translateToLocalFormatted(
                    copySource == null ? "galaxia.gui.station.picker.build_title"
                        : "galaxia.gui.station.picker.copy_title",
                    kind.getDisplayName()),
                StatCollector.translateToLocal("galaxia.gui.common.confirm"),
                (coord, selected) -> {
                    List<ModulePlacement> proposed = new ArrayList<>(selected.size() + 1);
                    for (StationTileCoord target : selected) {
                        proposed.add(new ModulePlacement(target, controller.selectedTargetRotation(target)));
                    }
                    proposed.add(new ModulePlacement(coord, controller.footprintRotation()));
                    return facility.buildablePlacements(kind, shape, tier, proposed)
                        .equals(proposed);
                },
                coord -> coord,
                placements -> {
                    boolean sent;
                    if (copySource != null) {
                        sent = com.gtnewhorizons.galaxia.client.CelestialClient
                            .copyModule(assetId, request.copySourceModuleId(), request.creativeBuildMode(), placements);
                    } else {
                        sent = com.gtnewhorizons.galaxia.client.CelestialClient.createModules(
                            assetId,
                            kind,
                            shape,
                            tier,
                            request.hammerVariant(),
                            request.minerFocusTier(),
                            request.settingsGroupId(),
                            request.creativeBuildMode(),
                            placements);
                    }
                    if (!sent) StationNotificationHelper
                        .showFailure(StatCollector.translateToLocal("galaxia.gui.station.picker.build_failed"));
                },
                targets -> {
                    List<ModulePlacement> proposed = new ArrayList<>(targets.size());
                    for (StationTileCoord target : targets) {
                        proposed.add(new ModulePlacement(target, controller.selectedTargetRotation(target)));
                    }
                    return facility.connectedBuildablePlacements(kind, shape, tier, proposed)
                        .stream()
                        .map(ModulePlacement::anchor)
                        .toList();
                });
        controller.setSelectionFootprint(shape, shape != ModuleShape.SINGLE);
        controller.setPreviewModuleKind(kind);
    }

    private record BuildPickerRequest(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier,
        @Nullable SettingsGroup.ID settingsGroupId, ModuleInstance.ID copySourceModuleId, boolean creativeBuildMode) {

        static BuildPickerRequest create(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
            ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier,
            @Nullable SettingsGroup.ID settingsGroupId, boolean creativeBuildMode) {
            return new BuildPickerRequest(
                assetId,
                kind,
                shape,
                tier,
                hammerVariant,
                minerFocusTier == null ? MinerFocusTier.NONE : minerFocusTier,
                settingsGroupId,
                null,
                creativeBuildMode);
        }

        static BuildPickerRequest copy(CelestialAsset.ID assetId, ModuleInstance.ID sourceModuleId,
            boolean creativeBuildMode) {
            return new BuildPickerRequest(
                assetId,
                null,
                null,
                null,
                null,
                MinerFocusTier.NONE,
                null,
                sourceModuleId,
                creativeBuildMode);
        }

        @Nullable
        ModuleInstance copySource(AutomatedFacility facility) {
            return facility == null ? null : facility.moduleById(copySourceModuleId);
        }
    }
}
