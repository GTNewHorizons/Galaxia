package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class StationManagementScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_station_management",
        StationManagementScreen::new);

    private static final int LEFT_PANEL_WIDTH = 216;
    private static final int PADDING = 12;
    private static final int MAP_ACTION_BUTTON_WIDTH = 128;
    private static final int MAP_ACTION_BUTTON_HEIGHT = 20;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile boolean pendingCreativeBuildMode;
    private static volatile StationVisionLayer pendingVisionLayer = StationVisionLayer.BASE;
    private static volatile @Nullable BuildPickerRequest pendingBuildPickerRequest;

    public static void open(CelestialAsset.ID assetId) {
        open(assetId, false);
    }

    public static void open(CelestialAsset.ID assetId, boolean creativeBuildMode) {
        pendingAssetId = assetId;
        pendingCreativeBuildMode = creativeBuildMode;
        FACTORY.openClient();
    }

    static void openBuildPicker(CelestialAsset.ID assetId, FacilityModuleKind kind, boolean creativeBuildMode) {
        pendingBuildPickerRequest = BuildPickerRequest.create(
            assetId,
            kind,
            kind.defaultShape(),
            kind.defaultTier(),
            null,
            MinerFocusTier.NONE,
            (short) 0,
            creativeBuildMode);
        open(assetId, creativeBuildMode);
    }

    static void openBuildPicker(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape, ModuleTier tier,
        HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId, boolean creativeBuildMode) {
        pendingBuildPickerRequest = BuildPickerRequest
            .create(assetId, kind, shape, tier, hammerVariant, minerFocusTier, settingsGroupId, creativeBuildMode);
        open(assetId, creativeBuildMode);
    }

    static void openCopyBuildPicker(CelestialAsset.ID assetId, int sourceModuleIndex, ModuleInstance.ID sourceModuleId,
        boolean creativeBuildMode) {
        pendingBuildPickerRequest = BuildPickerRequest
            .copy(assetId, sourceModuleIndex, sourceModuleId, creativeBuildMode);
        open(assetId, creativeBuildMode);
    }

    public static @Nullable CelestialAsset.ID pendingAssetId() {
        return pendingAssetId;
    }

    static boolean pendingCreativeBuildMode() {
        return pendingCreativeBuildMode;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_station_management")
            .fullScreenInvisible();
        CelestialAsset.ID assetId = pendingAssetId;
        boolean creativeBuildMode = pendingCreativeBuildMode;
        StationVisionLayer visionLayer = pendingVisionLayer;
        boolean isAutomatedFacility = CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility;
        StationOverlayCoordinator overlayCoordinator = new StationOverlayCoordinator();
        int overlayX = LEFT_PANEL_WIDTH + PADDING * 2;

        panel.child(
            new StationScreenBackground().left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f));

        if (isAutomatedFacility) {
            StationTilePickerController tilePickerController = new StationTilePickerController();
            StationEditModeController editModeController = new StationEditModeController(tilePickerController);
            int overlayY = PADDING + StationInventoryPanelWidget.BUTTON_HEIGHT + 4;
            ModuleConfigModalController configController = new ModuleConfigModalController(
                panel,
                assetId,
                overlayX,
                overlayY,
                tilePickerController,
                overlayCoordinator);
            StationInventoryPanelWidget inventoryPanel = new StationInventoryPanelWidget(
                assetId,
                overlayCoordinator,
                configController,
                () -> !editModeController.isActive());
            StationMapWidget map = new StationMapWidget(
                assetId,
                coord -> ModulePickerScreen.open(assetId, coord, creativeBuildMode),
                tile -> configController.requestRetargetTo(tile.isCore() ? null : tile.module()),
                LEFT_PANEL_WIDTH + PADDING,
                PADDING,
                PADDING,
                visionLayer,
                overlayCoordinator::containsMouse,
                tilePickerController);

            panel.child(
                map.left(0)
                    .top(0)
                    .widthRel(1f)
                    .heightRel(1f));
            panel.child(
                new StationSidePanelWidget(assetId, map, tilePickerController, editModeController, configController)
                    .left(PADDING)
                    .top(PADDING)
                    .width(LEFT_PANEL_WIDTH - PADDING)
                    .heightRelOffset(0.55f, -PADDING * 2));
            panel.child(
                new ModuleDetailPanel(map, tilePickerController).left(PADDING)
                    .width(LEFT_PANEL_WIDTH - PADDING)
                    .heightRelOffset(0.45f, -PADDING)
                    .bottom(PADDING));
            panel.child(
                new StationTilePickerControlsWidget(editModeController).left(LEFT_PANEL_WIDTH + PADDING * 2)
                    .top(PADDING * 2)
                    .width(StationTilePickerControlsWidget.WIDTH)
                    .height(StationTilePickerControlsWidget.HEIGHT));
            panel.child(
                inventoryPanel.left(overlayX)
                    .top(PADDING)
                    .width(StationInventoryPanelWidget.PANEL_WIDTH)
                    .height(StationInventoryPanelWidget.PANEL_HEIGHT + StationInventoryPanelWidget.BUTTON_HEIGHT + 4));
            panel.child(
                new StationCopyModuleMapButton(assetId, map, editModeController, creativeBuildMode)
                    .left(LEFT_PANEL_WIDTH + PADDING * 2)
                    .bottom(PADDING)
                    .size(MAP_ACTION_BUTTON_WIDTH, MAP_ACTION_BUTTON_HEIGHT));
            panel.child(
                new ModalInputBlocker(overlayCoordinator).left(0)
                    .top(0)
                    .widthRel(1f)
                    .heightRel(1f));
            startPendingBuildPicker(assetId, editModeController);
        } else {
            int overlayY = PADDING + StationInventoryPanelWidget.BUTTON_HEIGHT + 4;
            ModuleConfigModalController configController = new ModuleConfigModalController(
                panel,
                assetId,
                overlayX,
                overlayY,
                null,
                overlayCoordinator);
            StationInventoryPanelWidget inventoryPanel = new StationInventoryPanelWidget(
                assetId,
                overlayCoordinator,
                configController);
            panel.child(
                new StationSidePanelWidget(assetId, null).left(PADDING)
                    .top(PADDING)
                    .width(LEFT_PANEL_WIDTH - PADDING)
                    .heightRelOffset(1f, -PADDING * 2));
            panel.child(
                inventoryPanel.left(overlayX)
                    .top(PADDING)
                    .width(StationInventoryPanelWidget.PANEL_WIDTH)
                    .height(StationInventoryPanelWidget.PANEL_HEIGHT + StationInventoryPanelWidget.BUTTON_HEIGHT + 4));
            panel.child(
                ModuleConfigModalSupport.button("Items", () -> configController.openStationLogistics())
                    .pos(PADDING, PADDING + 200)
                    .size(88, 20));
            panel.child(
                new ModalInputBlocker(overlayCoordinator).left(0)
                    .top(0)
                    .widthRel(1f)
                    .heightRel(1f));
        }
        return panel;
    }

    private static void startPendingBuildPicker(CelestialAsset.ID assetId, StationEditModeController controller) {
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
        controller.startTileMode(
            copySource == null ? StationEditModeController.Mode.MASS_BUILD : StationEditModeController.Mode.COPY_MODULE,
            (copySource == null ? "Build " : "Copy ") + kind.getDisplayName(),
            "Confirm",
            (coord, selected) -> ModuleBuildPickerModel
                .isCompatibleTarget(facility, kind, shape, tier, coord, selected),
            coord -> coord,
            targets -> {
                boolean sent;
                if (copySource != null) {
                    sent = com.gtnewhorizons.galaxia.client.CelestialClient.copyModule(
                        assetId,
                        request.copySourceModuleIndex(),
                        request.copySourceModuleId(),
                        request.creativeBuildMode(),
                        targets);
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
                        targets);
                }
                if (!sent) StationNotificationHelper.showFailure("Module build request failed");
            },
            targets -> ModuleBuildPickerModel.connectedTargets(facility, targets, shape));
        controller.setSelectionFootprint(shape, shape == ModuleShape.QUAD_2x2);
        controller.setPreviewModuleKind(kind);
    }

    private static final class StationScreenBackground extends ParentWidget<StationScreenBackground> {

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
            BorderedRect.draw(
                0,
                0,
                getArea().width,
                getArea().height,
                EnumColors.MAP_COLOR_STATION_SCREEN_BG.getColor(),
                EnumColors.MAP_COLOR_STATION_SCREEN_BORDER.getColor());
        }
    }

    private static final class ModalInputBlocker extends ParentWidget<ModalInputBlocker> {

        private final StationOverlayCoordinator overlayCoordinator;
        private boolean listenersRegistered;

        private ModalInputBlocker(StationOverlayCoordinator overlayCoordinator) {
            this.overlayCoordinator = overlayCoordinator;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            overlayCoordinator.processDeferredActions();
        }

        @Override
        public void onInit() {
            super.onInit();
            if (listenersRegistered) return;
            listenersRegistered = true;
            listenGuiAction(
                (com.cleanroommc.modularui.api.widget.IGuiAction.MousePressed) button -> overlayCoordinator
                    .blocksInputAt(getContext().getMouseX(), getContext().getMouseY()));
            listenGuiAction(
                (com.cleanroommc.modularui.api.widget.IGuiAction.MouseReleased) button -> overlayCoordinator
                    .blocksInputAt(getContext().getMouseX(), getContext().getMouseY()));
            listenGuiAction(
                (com.cleanroommc.modularui.api.widget.IGuiAction.MouseDrag) (mouseButton, time) -> overlayCoordinator
                    .blocksInputAt(getContext().getMouseX(), getContext().getMouseY()));
        }

        @Override
        public boolean canHover() {
            return overlayCoordinator.blocksInputAt(getContext().getMouseX(), getContext().getMouseY());
        }

        @Override
        public boolean canHoverThrough() {
            return !overlayCoordinator.blocksInputAt(getContext().getMouseX(), getContext().getMouseY());
        }
    }

    private record BuildPickerRequest(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
        ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId,
        int copySourceModuleIndex, ModuleInstance.ID copySourceModuleId, boolean creativeBuildMode) {

        static BuildPickerRequest create(CelestialAsset.ID assetId, FacilityModuleKind kind, ModuleShape shape,
            ModuleTier tier, HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId,
            boolean creativeBuildMode) {
            return new BuildPickerRequest(
                assetId,
                kind,
                shape,
                tier,
                hammerVariant,
                minerFocusTier == null ? MinerFocusTier.NONE : minerFocusTier,
                settingsGroupId,
                -1,
                null,
                creativeBuildMode);
        }

        static BuildPickerRequest copy(CelestialAsset.ID assetId, int sourceModuleIndex,
            ModuleInstance.ID sourceModuleId, boolean creativeBuildMode) {
            return new BuildPickerRequest(
                assetId,
                null,
                null,
                null,
                null,
                MinerFocusTier.NONE,
                (short) 0,
                sourceModuleIndex,
                sourceModuleId,
                creativeBuildMode);
        }

        @Nullable
        ModuleInstance copySource(AutomatedFacility facility) {
            if (facility == null || copySourceModuleIndex < 0 || copySourceModuleId == null) return null;
            if (copySourceModuleIndex >= facility.modules()
                .size()) return null;
            ModuleInstance source = facility.modules()
                .get(copySourceModuleIndex);
            return copySourceModuleId.equals(source.id) ? source : null;
        }
    }
}
