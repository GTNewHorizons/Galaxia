package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

/**
 * The players around an oxygen pylon, seen from above: the one looking in one colour, everyone else in another
 */
public class PlayerRadarWidget extends Widget<PlayerRadarWidget> {

    private static final int MARK = 5;
    private static final int MARK_EDGE = 0xFF1B1D2C;

    private final TileEntityOxygenPylon tile;

    public PlayerRadarWidget(TileEntityOxygenPylon tile) {
        this.tile = tile;
        size(RadarGrid.SIZE, RadarGrid.SIZE);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);
        RadarGrid.draw(context, widgetTheme, EnumTextures.ICON_OXYGEN_PYLON.getImage());
        if (tile.getWorldObj() == null) return;

        List<EntityPlayer> players = tile.getWorldObj()
            .getEntitiesWithinAABB(EntityPlayer.class, tile.getRangeAABB());
        int half = RadarGrid.SIZE / 2;
        for (EntityPlayer p : players) {
            double relX = (p.posX - (tile.xCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;
            double relZ = (p.posZ - (tile.zCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;
            int x = (int) (half + relX * half) - MARK / 2;
            int y = (int) (half + relZ * half) - MARK / 2;
            boolean self = p.getUniqueID()
                .equals(Minecraft.getMinecraft().thePlayer.getUniqueID());
            GuiDraw.drawRect(x, y, MARK, MARK, MARK_EDGE);
            GuiDraw.drawRect(x + 1, y + 1, MARK - 2, MARK - 2, self ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.WARN);
        }
    }
}
