package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly.ModulePlacement;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityRocketTrophy;

public class RocketTrophyRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityRocketTrophy trophy)) return;
        if (trophy.getSchematic() == null) return;

        List<Integer> moduleIds = ItemRocketSchematic.readModules(trophy.getSchematic());
        if (moduleIds.isEmpty()) return;

        RocketAssembly assembly = new RocketAssembly(moduleIds);
        List<ModulePlacement> placements = assembly.getPlacements();
        if (placements.isEmpty()) return;

        // Translate to given offsets

        // Apply yaw then pitch

        GL11.glDisable(GL11.GL_CULL_FACE);
        for (ModulePlacement placement : placements) {
            RocketModule module = placement.type();

            GL11.glPushMatrix();
            double dx = x + 0.5;
            double dy = y + module.getHeight() / 2.0 * trophy.getScale(); // + H/2 as models' origin are centered now
            double dz = z + 0.5;

            GL11.glTranslated(dx + trophy.getOffsetX(), dy + trophy.getOffsetY(), dz + trophy.getOffsetZ());

            GL11.glRotatef(trophy.getYaw(), 0f, 1f, 0f);
            GL11.glRotatef(trophy.getPitch(), 1f, 0f, 0f);

            GL11.glScalef(trophy.getScale(), trophy.getScale(), trophy.getScale());

            GL11.glTranslated(placement.x(), placement.y(), placement.z());
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(module.getTexture());
            module.getModel()
                .renderAll();

            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_CULL_FACE);

    }
}
