package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry.getModule;

import java.util.List;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleRegistry.ModuleInfo;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

public class RocketRenderer extends Render {

    public RocketRenderer() {
        this.shadowSize = 0.5F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        EntityRocket rocket = (EntityRocket) entity;
        if (!rocket.shouldRender()) return;

        List<Integer> types = rocket.getModuleTypes();
        if (types.isEmpty()) return;

        double yOff = 0.75;
        GL11.glDisable(GL11.GL_CULL_FACE);
        for (int type : types) {
            ModuleInfo info = getModule(type);
            if (info == null) {
                yOff += 2.0;
                continue;
            }

            bindTexture(info.texture());

            GL11.glPushMatrix();
            GL11.glTranslated(x, y + yOff, z);
            info.model()
                .renderAll();
            GL11.glPopMatrix();

            yOff += info.height();
        }
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
