package com.gtnewhorizons.galaxia.client.render.rockets;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws rocket module models, both in the world and flat in GUIs. Models may have a group named {@value #CORE_GROUP}
 * that gets tinted with the module's fuel colour, so one frame model serves every fuel
 */
@SideOnly(Side.CLIENT)
public final class ModuleModelRenderer {

    public static final String CORE_GROUP = "core";

    /** Depth the GUI models are pushed forward by, keeps them in front of the panel background */
    private static final float GUI_DEPTH = 150f;
    private static final float MAX_DEPTH_SCALE = 40f;
    /** Light levels for GUI models, vanilla item lighting uses 0.4 ambient and 0.6 diffuse */
    private static final float GUI_AMBIENT = 0.7f;
    private static final float GUI_DIFFUSE = 0.35f;
    private static final FloatBuffer LIGHT_BUFFER = BufferUtils.createFloatBuffer(4);

    private ModuleModelRenderer() {}

    /**
     * Renders a module centred on the origin, one unit per block, with the core tinted
     *
     * @param def   The module to render
     * @param alpha Opacity, 1 for solid
     *
     * @return Boolean : True => a model was rendered
     */
    public static boolean renderModel(IRocketPartDef def, float alpha) {
        if (def.modelLocation() == null) return false;
        IModelCustom model = ModelCache.get(def.modelLocation());
        if (model == null) return false;

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(def.textureLocation());

        int color = def.coreColor();
        GL11.glColor4f(1f, 1f, 1f, alpha);
        if (color < 0) {
            model.renderAll();
            return true;
        }

        model.renderAllExcept(CORE_GROUP);
        GL11.glColor4f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, alpha);
        model.renderOnly(CORE_GROUP);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        return true;
    }

    /**
     * The item lighting leaves faces turned away from the lights almost black, which reads too harsh on large
     * models. More ambient and less directional light keeps the shape readable without the hard shadows
     */
    private static void softenLighting() {
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, colour(GUI_AMBIENT));
        FloatBuffer diffuse = colour(GUI_DIFFUSE);
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, diffuse);
        GL11.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, diffuse);
    }

    private static FloatBuffer colour(float brightness) {
        LIGHT_BUFFER.clear();
        LIGHT_BUFFER.put(brightness)
            .put(brightness)
            .put(brightness)
            .put(1f)
            .flip();
        return LIGHT_BUFFER;
    }

    /**
     * Draws a module front-on into a GUI rectangle. The model keeps its proportions and is centred, one block is as
     * wide as {@code width / def.width()} so neighbouring parts on a grid line up
     *
     * @param def    The module to draw
     * @param x      Left edge
     * @param y      Top edge
     * @param width  Rectangle width
     * @param height Rectangle height
     * @param alpha  Opacity, 1 for solid
     */
    public static void drawInGui(IRocketPartDef def, float x, float y, float width, float height, float alpha) {
        if (def.modelLocation() == null) return;
        float scale = Math.min(width / def.width(), height / def.height());

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glTranslatef(x + width / 2f, y + height / 2f, GUI_DEPTH);
        // Depth is flattened so zoomed-in models stay inside the GUI depth range
        GL11.glScalef(scale, scale, Math.min(scale, MAX_DEPTH_SCALE));
        // GUI y points down while models are built y up, a half turn keeps the lighting and winding intact
        GL11.glRotatef(180f, 0f, 0f, 1f);

        // Widget backgrounds may come after a flat rectangle that switched texturing off
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_NORMALIZE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderHelper.enableGUIStandardItemLighting();
        softenLighting();

        renderModel(def, alpha);

        RenderHelper.disableStandardItemLighting();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
