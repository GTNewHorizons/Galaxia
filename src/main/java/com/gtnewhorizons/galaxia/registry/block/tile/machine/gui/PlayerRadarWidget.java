package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import java.util.List;

import com.cleanroommc.modularui.theme.WidgetTheme;
import com.gtnewhorizons.galaxia.client.EnumColors;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

public class PlayerRadarWidget extends Widget<PlayerRadarWidget> {
    private final TileEntityOxygenPylon tile;

    public PlayerRadarWidget(TileEntityOxygenPylon tile) {
        this.tile = tile;
        size(81, 81);
        background(EnumTextures.SELECTION_FRAME.getImage());
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);
        EnumTextures.MARS.getImage().draw(context, getArea(), WidgetTheme.getDefault().getTheme());

        List<EntityPlayer> players = tile.getWorldObj().getEntitiesWithinAABB(
            EntityPlayer.class, tile.getRangeAABB());

        for (EntityPlayer p : players) {
            double relX = (p.posX - (tile.xCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;
            double relZ = (p.posZ - (tile.zCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;

            int vx = (int) (40 + (relX * 40));
            int vy = (int) (40 + (relZ * 40));

            EnumTextures.OVERWORLD.getImage()
                .withColorOverride(EnumColors.MAP_PLAYER_SELF.getColor())
                .draw(context, vx - 2, vy - 2, 5, 5, widgetTheme.getTheme());
        }
    }
}
