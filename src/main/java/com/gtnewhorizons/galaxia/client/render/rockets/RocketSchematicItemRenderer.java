package com.gtnewhorizons.galaxia.client.render.rockets;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;

public class RocketSchematicItemRenderer implements IItemRenderer {

    private static final ResourceLocation SCHEMATIC_BASE = LocationGalaxia("textures/items/tool/schematic_base.png");

    private static final int SIZE = 128;
    private static final int PADDING = 8;

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        return type == ItemRenderType.FIRST_PERSON_MAP || (type == ItemRenderType.ENTITY && RenderItem.renderInFrame);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (type == ItemRenderType.FIRST_PERSON_MAP) {
            TextureManager renderEngine = (TextureManager) data[1];
            renderSchematic(stack, renderEngine);
        } else if (type == ItemRenderType.ENTITY && RenderItem.renderInFrame) {
            renderSchematicInFrame(stack, RenderManager.instance);
        }
    }

    /**
     * Render method for item held in a player hand
     *
     * @param stack        The itemstack that needs rendering
     * @param renderEngine The TextureManager of the minecraft client
     */
    private void renderSchematic(ItemStack stack, TextureManager renderEngine) {

        List<Integer> moduleIds = ItemRocketSchematic.readModules(stack);
        if (moduleIds == null || moduleIds.isEmpty()) return;

        RocketAssembly assembly = new RocketAssembly(moduleIds);
        List<ModulePlacement> placements = assembly.getPlacements();

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        drawQuad(renderEngine, SCHEMATIC_BASE, -7f, 128f + 14f, -0.01f);
        GL11.glTranslatef(64f, 64f, 0f);
        GL11.glRotatef(45f, 0f, 0f, 1f);
        GL11.glTranslatef(-64f, -64f, 0f);
        if (!placements.isEmpty()) {
            placements.sort(Comparator.comparingInt(p -> (int) p.y()));

            int baseY = (int) placements.get(0)
                .y();
            int topY = placements.stream()
                .mapToInt(
                    p -> (int) (p.y() + p.type()
                        .getHeight()))
                .max()
                .orElse(1);
            int totalHeight = topY - baseY;
            int maxWidth = placements.stream()
                .mapToInt(
                    p -> (int) p.type()
                        .getWidth())
                .max()
                .orElse(1);

            int usable = SIZE - PADDING * 2;

            int pixelsPerBlock = usable / Math.max(totalHeight, maxWidth);

            for (ModulePlacement p : placements) {
                RocketModule module = p.type();
                if (module == null) continue;

                ResourceLocation sprite = module.getSchematicSprite();
                if (sprite == null) continue;

                float moduleW = (float) module.getWidth() * pixelsPerBlock;
                float moduleH = (float) module.getHeight() * pixelsPerBlock;

                float px = (float) (PADDING + (usable - maxWidth * pixelsPerBlock) / 2f + p.x() * pixelsPerBlock);
                float py = (float) (SIZE - PADDING - ((p.y() - baseY) + module.getHeight()) * pixelsPerBlock);

                // -0.02 to avoid z-fighting with map texture
                drawQuad(renderEngine, sprite, px, py, moduleW, moduleH, -0.02f);
            }
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glPopAttrib();
        GL11.glPopMatrix();

        drawTexts(assembly, stack);
    }

    /**
     * Render method for when placed in an item frame
     *
     * @param stack         The item stack being rendered from the frame
     * @param renderManager the RenderManager being used in this client instance
     */
    private void renderSchematicInFrame(ItemStack stack, RenderManager renderManager) {
        GL11.glRotatef(180f, 0f, 1f, 0f);
        GL11.glRotatef(180f, 0f, 0f, 1f);
        GL11.glScalef(0.00881250f, 0.00881250f, 0.00881250f);
        GL11.glTranslatef(-64f, -104f, -3f);
        GL11.glNormal3f(0f, 0f, -1f);

        renderManager.renderEngine.bindTexture(LocationGalaxia("textures/items/tool/schematic_base.png"));
        Tessellator t = Tessellator.instance;
        byte b0 = 7;
        t.startDrawingQuads();
        t.addVertexWithUV(0 - b0, 128 + b0, 0, 0, 1);
        t.addVertexWithUV(128 + b0, 128 + b0, 0, 1, 1);
        t.addVertexWithUV(128 + b0, 0 - b0, 0, 1, 0);
        t.addVertexWithUV(0 - b0, 0 - b0, 0, 0, 0);
        t.draw();

        GL11.glTranslatef(0f, 0f, -1f);

        renderSchematic(stack, renderManager.renderEngine);
    }

    private static void drawQuad(TextureManager tm, ResourceLocation tex, float offset, float size, float z) {
        if (tex != null) tm.bindTexture(tex);
        float end = offset + size;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(offset, end, z, 0, 1);
        t.addVertexWithUV(end, end, z, 1, 1);
        t.addVertexWithUV(end, offset, z, 1, 0);
        t.addVertexWithUV(offset, offset, z, 0, 0);
        t.draw();
    }

    private static void drawQuad(TextureManager tm, ResourceLocation tex, float x, float y, float w, float h, float z) {
        tm.bindTexture(tex);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, z, 0, 1);
        t.addVertexWithUV(x + w, y + h, z, 1, 1);
        t.addVertexWithUV(x + w, y, z, 1, 0);
        t.addVertexWithUV(x, y, z, 0, 0);
        t.draw();
    }

    private static void drawTexts(RocketAssembly assembly, ItemStack stack) {
        float offsetY = 16f;
        GL11.glPushMatrix();
        String[] lines = {
            StatCollector
                .translateToLocalFormatted("galaxia.item.schematic.label.name", ItemRocketSchematic.readName(stack)),
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.tier", assembly.getTier()),
            StatCollector.translateToLocalFormatted(
                "galaxia.item.schematic.label.modules",
                assembly.getModules()
                    .stream()
                    .count()),
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.height", assembly.getTotalHeight()),
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.width", assembly.getTotalWidth()),
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.weight", assembly.getTotalWeight()),
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.thrust", assembly.getTotalThrust()) };

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        GL11.glScalef(0.5f, 0.5f, 0.5f);

        GL11.glTranslatef(8f, PADDING, -5f);
        for (String line : lines) {

            GL11.glTranslatef(0, offsetY, 0);
            fr.drawStringWithShadow(line, 0, 0, 0xFFFFFFFF);
        }

        GL11.glPopMatrix();
    }

}
