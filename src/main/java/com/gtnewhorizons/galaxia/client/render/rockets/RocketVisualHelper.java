package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketTrophy;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * Central rendering engine for all rockets and rocket-like structures.
 * Single source of truth for visual representation of RocketBlueprint.
 */
public final class RocketVisualHelper {

    private RocketVisualHelper() {}

    public static void renderTrophy(TileEntityRocketTrophy trophy, double x, double y, double z, float partialTicks) {
        if (trophy == null) return;
        RocketBlueprint blueprint = trophy.getBlueprint();
        if (blueprint == null || blueprint.isEmpty()) return;

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();

        double dx = x + 0.5 + trophy.getOffsetX();
        double dy = y + 0.5 + trophy.getOffsetY();
        double dz = z + 0.5 + trophy.getOffsetZ();

        GL11.glTranslated(dx, dy, dz);

        GL11.glRotatef(trophy.getYaw(), 0f, 1f, 0f);
        GL11.glRotatef(trophy.getPitch(), 1f, 0f, 0f);
        GL11.glScalef(trophy.getScale(), trophy.getScale(), trophy.getScale());

        renderBlueprint(blueprint, 0, 0, 0, 0f, partialTicks, false);

        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    public static void renderSilo(TileEntitySilo silo, double x, double y, double z, float partialTicks) {
        if (silo == null || !silo.shouldRender) return;

        RocketBlueprint blueprint = silo.getBuiltBlueprint();
        if (blueprint == null || blueprint.isEmpty()) return;

        final int[] offset = TileEntitySilo.getRotatedOffset(
            TileEntitySilo.SILO_DEFAULT_X_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Y_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Z_OFFSET,
            silo.currentFacing);

        int px = silo.xCoord + offset[0];
        int py = silo.yCoord + offset[1];
        int pz = silo.zCoord + offset[2];

        int brightness = silo.getWorldObj()
            .getLightBrightnessForSkyBlocks(px, py, pz, 0);
        int blockLight = brightness % 65536;
        int skyLight = brightness / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);

        renderBlueprint(blueprint, x + offset[0], y + offset[1], z + offset[2], 0.0f, partialTicks, true);
    }

    public static void renderBlueprint(RocketBlueprint blueprint, double x, double y, double z, float yaw,
        float partialTicks, boolean isInSilo) {

        if (blueprint == null || blueprint.isEmpty()) return;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;

        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (RocketPartInstance part : blueprint.getParts()) {
            IRocketPartDef def = part.def();

            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());

            maxX = Math.max(maxX, part.x() + def.width());
            maxY = Math.max(maxY, part.y() + def.height());
            maxZ = Math.max(maxZ, part.z() + 1);
        }

        double centerX = (minX + maxX) * 0.5D;
        double totalHeight = maxY;
        double centerZ = (minZ + maxZ) * 0.5D;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        if (!isInSilo) {
            GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);
        }

        GL11.glTranslatef(0.5f, 0.0f, 0.5f);

        for (RocketPartInstance part : blueprint.getParts()) {
            renderPart(part, centerX, totalHeight, centerZ);
        }

        GL11.glPopMatrix();
    }

    private static void renderPart(RocketPartInstance part, double centerX, double totalHeight, double centerZ) {
        GL11.glPushMatrix();

        GL11.glTranslated(
            -(part.x() - centerX + 1.5),
            -(part.y() - totalHeight
                + (double) part.def()
                    .height() / 2),
            part.z() - centerZ + 0.5);

        IRocketPartDef def = part.def();

        if (def.modelLocation() != null) {
            IModelCustom model = ModelCache.get(def.modelLocation());
            if (model != null) {
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(def.textureLocation());

                model.renderAll();

                GL11.glPopAttrib();
                GL11.glPopMatrix();
                return;
            }
        }

        GL11.glPopMatrix();
    }
}
