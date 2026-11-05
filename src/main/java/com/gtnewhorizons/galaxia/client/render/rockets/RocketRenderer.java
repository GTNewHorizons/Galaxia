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
        if (!(entity instanceof EntityRocket rocket)) return;
        if (!rocket.shouldRender()) return;
        RocketVisualHelper.renderBlueprint(rocket.getBlueprint(), x - 0.5, y, z - 0.5, yaw, partialTicks, false);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
