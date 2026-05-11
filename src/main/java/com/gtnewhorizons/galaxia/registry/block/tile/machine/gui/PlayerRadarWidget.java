package com.gtnewhorizons.galaxia.registry.block.tile.machine.gui;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

public class PlayerRadarWidget extends Widget<PlayerRadarWidget> {

    private static final int RADAR_RADIUS_PIXELS = 36;

    private final TileEntityOxygenPylon tile;

    public PlayerRadarWidget(TileEntityOxygenPylon tile) {
        this.tile = tile;

        background(EnumTextures.SELECTION_FRAME.getImage());

        tooltip(t -> t.addLine(IKey.lang("galaxia.gui.oxygen_pylon.radar_tooltip")));
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        if (tile.getWorldObj() == null) {
            return;
        }

        AxisAlignedBB area = tile.getRangeAABB();

        List<EntityPlayer> players = tile.getWorldObj()
            .getEntitiesWithinAABB(EntityPlayer.class, area);

        int centerX = getArea().width / 2;
        int centerY = getArea().height / 2;

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glColor4f(0.1f, 0.4f, 0.35f, 0.25f);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);

        for (int i = 0; i <= 32; i++) {
            double angle = Math.PI * 2.0 * i / 32.0;

            double x = centerX + Math.cos(angle) * RADAR_RADIUS_PIXELS;
            double y = centerY + Math.sin(angle) * RADAR_RADIUS_PIXELS;

            GL11.glVertex2d(x, y);
        }

        GL11.glEnd();

        for (EntityPlayer player : players) {

            double dx = (player.posX - (tile.xCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;

            double dz = (player.posZ - (tile.zCoord + 0.5)) / TileEntityOxygenPylon.PYLON_RADIUS;

            dx *= RADAR_RADIUS_PIXELS;
            dz *= RADAR_RADIUS_PIXELS;

            int px = (int) (centerX + dx);
            int py = (int) (centerY + dz);

            GL11.glColor4f(0.2f, 1.0f, 0.8f, 1.0f);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(px - 2, py - 2);
            GL11.glVertex2f(px + 2, py - 2);
            GL11.glVertex2f(px + 2, py + 2);
            GL11.glVertex2f(px - 2, py + 2);
            GL11.glEnd();
        }

        GL11.glColor4f(0.3f, 0.8f, 1.0f, 1.0f);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(centerX - 3, centerY - 3);
        GL11.glVertex2f(centerX + 3, centerY - 3);
        GL11.glVertex2f(centerX + 3, centerY + 3);
        GL11.glVertex2f(centerX - 3, centerY + 3);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
