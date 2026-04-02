package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.orbitalGUI.GalaxiaRegistry;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class GalacticChartGui {

    private static final int LEFT_PANEL_WIDTH = 216;

    public ModularPanel build(PanelSyncManager syncManager) {
        ModularPanel panel = ModularPanel.defaultPanel("galactic_orbital_map")
            .fullScreenInvisible();
        OrbitalCelestialBody galaxyRoot = GalaxiaRegistry.root();
        int currentDimension = Minecraft.getMinecraft().thePlayer == null ? 0
            : Minecraft.getMinecraft().thePlayer.dimension;
        OrbitalCelestialBody currentStar = GalaxiaRegistry.findCurrentStar(currentDimension)
            .orElseGet(
                () -> GalaxiaRegistry.getPrimaryStar()
                    .orElse(galaxyRoot));
        TextFieldWidget renameField = new TextFieldWidget().left(LEFT_PANEL_WIDTH)
            .top(-1000)
            .width(180)
            .height(22)
            .setMaxLength(48)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText("Asset name")
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        OrbitalView.OrbitalMapWidget map = new OrbitalView.OrbitalMapWidget(galaxyRoot).withInitialLayer(currentStar)
            .attachRenameField(renameField);
        OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget pinnedInfoOverlay = map.createPinnedInfoWidget();
        OrbitalContextMenuWidget contextMenuOverlay = map.createContextMenuWidget();
        AssetManagementSystem.OrbitalAssetManagementWidget assetManagementOverlay = map.createAssetManagementWidget();
        CelestialSidebarWidget sidebar = new CelestialSidebarWidget(galaxyRoot, currentStar, map);
        return panel.child(
            (IWidget) sidebar.left(0)
                .top(0)
                .width(LEFT_PANEL_WIDTH)
                .heightRel(1f))
            .child(
                (IWidget) map.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .widthRelOffset(1f, -LEFT_PANEL_WIDTH)
                    .heightRel(1f))
            .child(
                (IWidget) pinnedInfoOverlay.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .widthRelOffset(1f, -LEFT_PANEL_WIDTH)
                    .heightRel(1f))
            .child(
                (IWidget) contextMenuOverlay.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .widthRelOffset(1f, -LEFT_PANEL_WIDTH)
                    .heightRel(1f))
            .child(
                (IWidget) assetManagementOverlay.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .widthRelOffset(1f, -LEFT_PANEL_WIDTH)
                    .heightRel(1f))
            .child((IWidget) renameField);
    }
}
