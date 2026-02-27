package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleType;
import com.gtnewhorizons.galaxia.rocketmodules.entities.EntityRocket;

public class RocketRenderer extends Render {

    public RocketRenderer() {
        this.shadowSize = 0.5F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        EntityRocket rocket = (EntityRocket) entity;
        if (!rocket.shouldRender()) return;

        RocketVisualHelper.render(rocket.getAssembly(), x, y, z, false);
    }

    private void renderModule(ModuleType info, double x, double y, double z, double offsetX, double offsetZ) {
        bindTexture(info.getTexture());
        GL11.glPushMatrix();
        GL11.glTranslated(x + offsetX, y, z + offsetZ);
        info.getModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
