package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;

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

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
