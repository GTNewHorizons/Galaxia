package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;

/**
 * Central rendering engine for all rockets and rocket-like structures.
 * Single source of truth for visual representation of RocketBlueprint.
 */
public final class RocketVisualHelper {

    private RocketVisualHelper() {}

    public static void renderBlueprint(RocketBlueprint blueprint, double x, double y, double z,
                                       float yaw, float partialTicks, boolean isInSilo) {

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

        RocketPartDef def = part.def();

        if (def.modelLocation() != null) {
            IModelCustom model = ModelCache.get(def.modelLocation());
            if (model != null) {
                bindTexture(def.textureLocation());
                model.renderAll();
                GL11.glPopMatrix();
                return;
            }
        }

        renderFallbackPart(def);

        GL11.glPopMatrix();
    }

    private static void renderFallbackPart(RocketPartDef def) {
        float r, g, b;
        switch (def.type()) {
            case CAPSULE -> { r = 0.0f; g = 0.8f; b = 0.0f; }
            case LANDER -> { r = 0.0f; g = 0.9f; b = 0.4f; }
            case FUEL_TANK -> { r = 0.2f; g = 0.4f; b = 0.9f; }
            case ENGINE -> { r = 0.9f; g = 0.3f; b = 0.0f; }
            case DECOUPLER -> { r = 0.6f; g = 0.6f; b = 0.6f; }
            default -> { r = 0.5f; g = 0.5f; b = 0.5f; }
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
        GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0); GL11.glVertex3f(0, 1, 0);
        // Back
        GL11.glVertex3f(0, 0, 1); GL11.glVertex3f(0, 1, 1);
        GL11.glVertex3f(1, 1, 1); GL11.glVertex3f(1, 0, 1);
        // Top / Bottom / Sides omitted for brevity — in production use full cube or model
        // (This is temporary visual placeholder)
    }

    private static void bindTexture(ResourceLocation texture) {
        if (texture != null) {
            // Use Minecraft's texture manager in real implementation
            // net.minecraft.client.renderer.texture.TextureManager
        }
    }
}
