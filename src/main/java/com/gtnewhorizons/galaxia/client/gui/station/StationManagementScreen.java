package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;

public final class StationManagementScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_station_management",
        StationManagementScreen::new);

    private static final int LEFT_PANEL_WIDTH = 216;
    private static final int PADDING = 12;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;

    public static void open(CelestialAsset.ID assetId) {
        pendingAssetId = assetId;
        FACTORY.openClient();
    }

    public static @Nullable CelestialAsset.ID pendingAssetId() {
        return pendingAssetId;
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_station_management")
            .fullScreenInvisible();
        CelestialAsset.ID assetId = pendingAssetId;
        StationMapWidget map = new StationMapWidget(assetId, coord -> ModulePickerScreen.open(assetId, coord, false));

        return panel.child(
            new StationScreenBackground().left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f))
            .child(
                new StationSidePanelWidget(assetId, map).left(PADDING)
                    .top(PADDING)
                    .width(LEFT_PANEL_WIDTH - PADDING)
                    .heightRelOffset(1f, -PADDING * 2))
            .child(
                map.left(LEFT_PANEL_WIDTH + PADDING)
                    .top(PADDING)
                    .widthRelOffset(1f, -(LEFT_PANEL_WIDTH + PADDING * 2))
                    .heightRelOffset(1f, -PADDING * 2));
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
            BorderedRect.draw(getArea().x, getArea().y, getArea().width, getArea().height, 0xFF08101B, 0xFF17283C);
        }
    }
}
