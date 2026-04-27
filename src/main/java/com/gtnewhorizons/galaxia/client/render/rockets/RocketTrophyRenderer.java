package com.gtnewhorizons.galaxia.client.render.rockets;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketTrophy;

public class RocketTrophyRenderer extends TileEntitySpecialRenderer {

    // TODO: fix normal shadows
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

        GL11.glPushMatrix();

        double dx = x + 0.5 + trophy.getOffsetX();
        double dy = y + 0.5 + trophy.getOffsetY();
        double dz = z + 0.5 + trophy.getOffsetZ();

        GL11.glTranslated(dx, dy, dz);

        GL11.glRotatef(trophy.getYaw(), 0f, 1f, 0f);
        GL11.glRotatef(trophy.getPitch(), 1f, 0f, 0f);

        GL11.glScalef(trophy.getScale(), trophy.getScale(), trophy.getScale());

        for (ModulePlacement placement : placements) {
            RocketModule module = placement.type();
            GL11.glPushMatrix();
            GL11.glTranslated(placement.x(), placement.y() + module.getHeight() / 2.0, placement.z());
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(module.getTexture());
            module.getModel()
                .renderAll();

            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_CULL_FACE);

    }
}
