package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;

/**
 * Central rendering engine for all rockets and rocket-like structures.
 * Single source of truth for visual representation of RocketBlueprint.
 */
public final class RocketVisualHelper {

    private RocketVisualHelper() {}

    public static void renderBlueprint(RocketBlueprint blueprint, double x, double y, double z, float yaw,
        float partialTicks, boolean isInSilo) {

        if (blueprint == null || blueprint.isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        if (!isInSilo) {
            GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);
        }

        // Center the blueprint in the block
        GL11.glTranslatef(0.5f, 0.0f, 0.5f);

        for (RocketPartInstance part : blueprint.getParts()) {
            renderPart(part);
        }

        GL11.glPopMatrix();
    }

    private static void renderPart(RocketPartInstance part) {
        GL11.glPushMatrix();

        GL11.glTranslated(part.x(), part.y(), part.z());

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
