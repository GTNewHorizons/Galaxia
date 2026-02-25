package com.gtnewhorizons.galaxia.client.render;

import com.gtnewhorizons.galaxia.registry.block.tileentities.TileEntitySilo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

public class SiloRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation MODEL = LocationGalaxia("textures/model/modules/hub_3x3/model.obj");
    private static final ResourceLocation TEXTURE = LocationGalaxia("textures/model/modules/hub_3x3/texture.png");

    private final IModelCustom model;

    public SiloRenderer() {
        model = AdvancedModelLoader.loadModel(MODEL);
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {

        if (!(te instanceof TileEntitySilo silo)) return;

        if (!silo.shouldRender) return;

        bindTexture(TEXTURE);

        if (silo.mods > 0) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 1.5, y, z + 1.5);
            model.renderAll();
            GL11.glPopMatrix();
        }

        if (silo.mods > 1) {
            GL11.glPushMatrix();
            GL11.glTranslated(x - .5, y, z + 1.5);
            model.renderAll();
            GL11.glPopMatrix();
        }

        if (silo.mods > 2) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 1.5, y, z - .5);
            model.renderAll();
            GL11.glPopMatrix();
        }

        if (silo.mods > 3) {
            GL11.glPushMatrix();
            GL11.glTranslated(x - .5, y, z - .5);
            model.renderAll();
            GL11.glPopMatrix();
        }
    }
}
