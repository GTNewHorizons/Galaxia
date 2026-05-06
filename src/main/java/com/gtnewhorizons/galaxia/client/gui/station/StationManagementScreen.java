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
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class StationManagementScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_station_management",
        StationManagementScreen::new);

    private static final int LEFT_PANEL_WIDTH = 216;
    private static final int PADDING = 12;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile boolean pendingCreativeBuildMode;
    private static volatile StationVisionLayer pendingVisionLayer = StationVisionLayer.BASE;

    public static void open(CelestialAsset.ID assetId) {
        open(assetId, false);
    }

    public static void open(CelestialAsset.ID assetId, boolean creativeBuildMode) {
        pendingAssetId = assetId;
        pendingCreativeBuildMode = creativeBuildMode;
        FACTORY.openClient();
    }

    public static @Nullable CelestialAsset.ID pendingAssetId() {
        return pendingAssetId;
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
        StationMapWidget map = new StationMapWidget(
            assetId,
            coord -> ModulePickerScreen.open(assetId, coord, creativeBuildMode),
            LEFT_PANEL_WIDTH + PADDING,
            PADDING,
            PADDING,
            visionLayer);
        ModuleConfigModalController configController = new ModuleConfigModalController(
            panel,
            assetId,
            LEFT_PANEL_WIDTH + PADDING * 2,
            PADDING * 2);

        panel.child(
            new StationScreenBackground().left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f));
        panel.child(
            map.left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f));
        panel.child(
            new StationSidePanelWidget(assetId, map).left(PADDING)
                .top(PADDING)
                .width(LEFT_PANEL_WIDTH - PADDING)
                .heightRelOffset(0.55f, -PADDING * 2));
        panel.child(
            new ModuleDetailPanel(map, configController).left(PADDING)
                .width(LEFT_PANEL_WIDTH - PADDING)
                .heightRelOffset(0.45f, -PADDING)
                .bottom(PADDING));
        return panel;
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
            BorderedRect.draw(0, 0, getArea().width, getArea().height, 0xFF08101B, 0xFF17283C);
        }
    }
}
