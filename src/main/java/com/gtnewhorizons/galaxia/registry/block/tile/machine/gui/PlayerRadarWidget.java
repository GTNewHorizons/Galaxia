package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

public class PlayerRadarWidget extends Widget<PlayerRadarWidget> {

    private final TileEntityOxygenPylon tile;
    private final int size = 81;

    public PlayerRadarWidget(TileEntityOxygenPylon tile) {
        this.tile = tile;
        size(size, size);
        background(EnumTextures.SELECTION_FRAME.getImage());
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        EnumTextures.MARS.getImage()
            .draw(context, 0, 0, size, size, widgetTheme.getTheme());

        List<EntityPlayer> players = tile.getWorldObj()
            .getEntitiesWithinAABB(EntityPlayer.class, tile.getRangeAABB());

        for (EntityPlayer p : players) {
            double relX = (p.posX - (tile.xCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;
            double relZ = (p.posZ - (tile.zCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;

            int vx = (int) (40 + (relX * 40));
            int vy = (int) (40 + (relZ * 40));

            int playerColor = p.getUniqueID()
                .equals(Minecraft.getMinecraft().thePlayer.getUniqueID()) ? EnumColors.MAP_PLAYER_SELF.getColor()
                    : EnumColors.MAP_PLAYER_OTHER.getColor();

            EnumTextures.OVERWORLD.getImage()
                .withColorOverride(playerColor)
                .draw(context, vx - 2, vy - 2, 5, 5, widgetTheme.getTheme());
        }
    }
}
