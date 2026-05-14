package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;

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

        renderFallbackPart(def);

        GL11.glPopMatrix();
    }

    private static void renderFallbackPart(IRocketPartDef def) {
        float r, g, b;
        switch (def) {
            case CapsulePartDef ignored -> {
                r = 0.0f;
                g = 0.8f;
                b = 0.0f;
            }
            case LanderPartDef ignored -> {
                r = 0.0f;
                g = 0.9f;
                b = 0.4f;
            }
            case FuelTankPartDef ignored -> {
                r = 0.2f;
                g = 0.4f;
                b = 0.9f;
            }
            case EnginePartDef ignored -> {
                r = 0.9f;
                g = 0.3f;
                b = 0.0f;
            }
            case DecouplerPartDef ignored -> {
                r = 0.6f;
                g = 0.6f;
                b = 0.6f;
            }
            default -> {
                r = 0.5f;
                g = 0.5f;
                b = 0.5f;
            }
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(r, g, b);

        GL11.glBegin(GL11.GL_QUADS);
        drawCubeVertices();
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(1, 1, 1);
    }

    private static void drawCubeVertices() {
        // Front
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        // Back
        GL11.glVertex3f(0, 0, 1);
        GL11.glVertex3f(0, 1, 1);
        GL11.glVertex3f(1, 1, 1);
        GL11.glVertex3f(1, 0, 1);
        // Top / Bottom / Sides omitted for brevity — in production use full cube or model
        // (This is temporary visual placeholder)
    }
}
