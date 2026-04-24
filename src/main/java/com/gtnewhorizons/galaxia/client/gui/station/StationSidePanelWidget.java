package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class StationSidePanelWidget extends ParentWidget<StationSidePanelWidget> {

    private static final int PANEL_BG = 0xD80B1320;
    private static final int PANEL_BORDER = 0xFF334C68;

    private final @Nullable CelestialAsset.ID assetId;
    private final StationMapWidget map;
    private @Nullable StationTileCoord armedDestroySelection;

    public StationSidePanelWidget(@Nullable CelestialAsset.ID assetId, StationMapWidget map) {
        this.assetId = assetId;
        this.map = map;
        child(
            createDestroyButton().pos(10, 150)
                .size(92, 20));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        StationTileCoord selected = map.selection();
        if (armedDestroySelection != null && !armedDestroySelection.equals(selected)) {
            armedDestroySelection = null;
        }
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int x = 0;
        int y = 0;
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

        lineY = drawLine(facility.kind.getDisplayName(), x + 10, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
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

    private static @Nullable AutomatedFacility resolveFacility(@Nullable CelestialAsset.ID assetId) {
        if (assetId == null) return null;
        return CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility : null;
    }

    private ButtonWidget<?> createDestroyButton() {
        return new ButtonWidget<>()
            .background(
                drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_DEFAULT.getColor()
                            : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                        canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor()
                            : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor())))
            .hoverBackground(
                drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_HOVERED.getColor()
                            : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                        canDestroySelected() ? EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor()
                            : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor())))
            .overlay(drawable((ctx, x, y, w, h) -> {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                String label = armedDestroySelection != null && armedDestroySelection.equals(map.selection())
                    ? "Confirm"
                    : "Destroy";
                int color = canDestroySelected() ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                    : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor();
                int textWidth = fr.getStringWidth(label);
                fr.drawStringWithShadow(label, x + (w - textWidth) / 2, y + (h - fr.FONT_HEIGHT) / 2 + 1, color);
            }))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !canDestroySelected()) return true;
                StationTileCoord selected = map.selection();
                if (!selected.equals(armedDestroySelection)) {
                    armedDestroySelection = selected;
                    return true;
                }
                destroySelected();
                armedDestroySelection = null;
                return true;
            });
    }

    private boolean canDestroySelected() {
        return selectedModuleIndex() >= 0;
    }

    private int selectedModuleIndex() {
        AutomatedFacility facility = resolveFacility(assetId);
        if (facility == null) return -1;
        StationLayout layout = facility.stationLayout();
        StationTileCoord selected = map.selection();
        if (layout == null || selected == null) return -1;
        PlacedTile tile = layout.get(selected);
        if (tile == null || tile.isCore() || tile.module() == null) return -1;
        for (int i = 0; i < facility.modules()
            .size(); i++) {
            if (facility.modules()
                .get(i).id.equals(tile.module().id)) return i;
        }
        return -1;
    }

    private void destroySelected() {
        int moduleIndex = selectedModuleIndex();
        if (assetId == null || moduleIndex < 0) return;
        CelestialClient.updateModuleAction(assetId, moduleIndex, AssetModuleUpdatePacket.Action.DESTROY);
    }

    private static int drawLine(String text, int x, int y, int color) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        fr.drawStringWithShadow(text, x, y, color);
        return y + fr.FONT_HEIGHT + 3;
    }

    private com.cleanroommc.modularui.api.drawable.IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }
}
