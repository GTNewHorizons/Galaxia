package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationManagementScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_station_management",
        StationManagementScreen::new);

    private static final int LEFT_PANEL_WIDTH = 216;
    private static final int PADDING = 12;
    private static final int PANEL_BG = 0xD80B1320;
    private static final int PANEL_BORDER = 0xFF334C68;

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
        StationMapWidget map = new StationMapWidget(assetId);

        return panel.child(
            new StationScreenBackground().left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f))
            .child(
                new StationDetailsPanel(assetId, map).left(PADDING)
                    .top(PADDING)
                    .width(LEFT_PANEL_WIDTH - PADDING)
                    .heightRelOffset(1f, -PADDING * 2))
            .child(
                map.left(LEFT_PANEL_WIDTH + PADDING)
                    .top(PADDING)
                    .widthRelOffset(1f, -(LEFT_PANEL_WIDTH + PADDING * 2))
                    .heightRelOffset(1f, -PADDING * 2));
    }

    private static @Nullable AutomatedFacility resolveFacility(@Nullable CelestialAsset.ID assetId) {
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
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

    private static final class StationDetailsPanel extends ParentWidget<StationDetailsPanel> {

        private final @Nullable CelestialAsset.ID assetId;
        private final StationMapWidget map;

        private StationDetailsPanel(@Nullable CelestialAsset.ID assetId, StationMapWidget map) {
            this.assetId = assetId;
            this.map = map;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            int x = getArea().x;
            int y = getArea().y;
            int width = getArea().width;
            int height = getArea().height;
            BorderedRect.draw(x, y, width, height, PANEL_BG, PANEL_BORDER);

            AutomatedFacility facility = resolveFacility(assetId);
            int lineY = y + 10;
            lineY = drawLine(
                facility == null ? "Station Management" : facility.displayName(),
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
            lineY += 6;

            if (facility == null) {
                drawLine("No station selected", x + 10, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                return;
            }

            lineY = drawLine(
                facility.kind.getDisplayName(),
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
            lineY = drawLine(
                "Modules: " + facility.modules()
                    .size(),
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            StationLayout layout = facility.stationLayout();
            lineY = drawLine(
                "Tiles: " + (layout == null ? 0 : layout.size()),
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            lineY = drawLine(
                "Energy: " + facility.getEnergyStored() + "/" + AutomatedFacility.MAX_ENERGY,
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            lineY += 8;

            StationTileCoord selected = map.selection();
            if (selected == null) {
                drawLine("No tile selected", x + 10, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                return;
            }

            lineY = drawLine(
                "Selected " + selected.dx() + ", " + selected.dy(),
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
            PlacedTile tile = layout == null ? null : layout.get(selected);
            if (tile == null) {
                drawLine("Expansion slot", x + 10, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
                return;
            }

            ModuleInstance module = tile.module();
            String moduleName = module == null ? "Station Core"
                : module.kind()
                    .getDisplayName();
            lineY = drawLine(moduleName, x + 10, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            drawLine(
                tile.state()
                    .name(),
                x + 10,
                lineY,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }

        private static int drawLine(String text, int x, int y, int color) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawStringWithShadow(text, x, y, color);
            return y + fr.FONT_HEIGHT + 3;
        }
    }
}
