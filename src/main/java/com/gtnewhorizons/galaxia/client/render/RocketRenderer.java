package com.gtnewhorizons.galaxia.client.render;

import com.gtnewhorizons.galaxia.registry.entity.rocket.EntityRocket;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

public class RocketRenderer extends Render {
    private static final ResourceLocation MODEL = LocationGalaxia("textures/model/modules/hub_3x3/model.obj");
    private static final ResourceLocation TEXTURE = LocationGalaxia("textures/model/modules/hub_3x3/texture.png");

    private IModelCustom model;

    public RocketRenderer() {
        this.model = AdvancedModelLoader.loadModel(MODEL);
        this.shadowSize = 0.5F;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {

        EntityRocket rocket = (EntityRocket) entity;

        if (!rocket.shouldRender()) {
            return;
        }

        bindEntityTexture(entity);

        int mods = rocket.getMods();

        if (mods > 0) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 1, y - 1, z + 1);
            model.renderAll();
            GL11.glPopMatrix();
        }

        if (mods > 1) {
            GL11.glPushMatrix();
            GL11.glTranslated(x - 1, y - 1, z + 1);
            model.renderAll();
            GL11.glPopMatrix();
        }

        if (mods > 2) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 1, y - 1, z - 1);
            model.renderAll();
            GL11.glPopMatrix();
        }

        if (mods > 3) {
            GL11.glPushMatrix();
            GL11.glTranslated(x - 1, y - 1, z - 1);
            model.renderAll();
            GL11.glPopMatrix();
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TEXTURE;
    }
}
