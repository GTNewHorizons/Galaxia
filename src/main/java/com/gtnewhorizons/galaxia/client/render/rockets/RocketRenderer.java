package com.gtnewhorizons.galaxia.client.render.rockets;

import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.RocketPlumeHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;

public class RocketRenderer extends Render {
    public RocketRenderer() {
        this.shadowSize = 0.5F;
        RocketPlumeHelper.plumeInit();
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (!(entity instanceof EntityRocket rocket)) return;

        rocket.plumeHelper.render(x, y, z);

        RocketBlueprint blueprint = rocket.getBlueprint();
        if (blueprint == null || blueprint.isEmpty()) return;

        RocketVisualHelper.renderBlueprint(blueprint, x, y, z, yaw, partialTicks, false);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
