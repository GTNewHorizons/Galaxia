package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartType;

public final class RocketVisualHelper {

    private RocketVisualHelper() {}

    public static void render(RocketBlueprint blueprint, double x, double y, double z,
                              float yaw, float partialTicks, boolean isInSilo) {

        if (blueprint == null || blueprint.isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        if (!isInSilo) {
            GL11.glRotatef(yaw, 0, 1, 0);
        }

        GL11.glTranslatef(0.5f, 0.0f, 0.5f);

        for (RocketPartInstance part : blueprint.getParts()) {
            renderSinglePart(part, isInSilo);
        }

        GL11.glPopMatrix();
    }

    private static void renderSinglePart(RocketPartInstance part, boolean isInSilo) {
        GL11.glPushMatrix();

        double px = part.x() * 1.0;
        double py = part.y() * 1.0;
        double pz = part.z() * 1.0;

        GL11.glTranslated(px, py, pz);


        if (part.def().type() == RocketPartType.CAPSULE) {
            renderColoredCube(0.0f, 0.8f, 0.0f);
        } else if (part.def().type() == RocketPartType.FUEL_TANK) {
            renderColoredCube(0.2f, 0.4f, 0.8f);
        } else if (part.def().type() == RocketPartType.ENGINE) {
            renderColoredCube(0.8f, 0.2f, 0.0f);
        } else if (part.def().type() == RocketPartType.DECOUPLER) {
            renderColoredCube(0.6f, 0.6f, 0.6f);
        } else {
            renderColoredCube(0.5f, 0.5f, 0.5f);
        }

        GL11.glPopMatrix();
    }

    private static void renderColoredCube(float r, float g, float b) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(r, g, b);

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);

        GL11.glVertex3f(0, 0, 1);
        GL11.glVertex3f(0, 1, 1);
        GL11.glVertex3f(1, 1, 1);
        GL11.glVertex3f(1, 0, 1);

        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(1, 1, 1);
    }

    private static void renderModel(RocketPartInstance part) {
        IModelCustom model = ModelCache.get(part.def().modelLocation());
        if (model != null) model.renderAll();
    }
}
