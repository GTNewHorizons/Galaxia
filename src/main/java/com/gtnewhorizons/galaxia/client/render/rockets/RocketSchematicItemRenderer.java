package com.gtnewhorizons.galaxia.client.render.rockets;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

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
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAnalyzer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;

public class RocketSchematicItemRenderer implements IItemRenderer {

    private static final ResourceLocation SCHEMATIC_BASE = LocationGalaxia("textures/items/tool/schematic_base.png");
    private static final ResourceLocation MISSING_TEXTURE = LocationGalaxia("textures/gui/rocket/placeholder_part.png");

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
            renderSchematic(stack, Minecraft.getMinecraft().renderEngine);
        } else if (type == ItemRenderType.ENTITY && RenderItem.renderInFrame) {
            renderSchematicInFrame(stack, RenderManager.instance);
        }
    }

    private void renderSchematic(ItemStack stack, TextureManager textureManager) {
        RocketBlueprint blueprint = ItemRocketSchematic.getBlueprint(stack);
        if (blueprint == null || blueprint.isEmpty()) {
            return;
        }

        RocketAssembly assembly = RocketAnalyzer.analyze(blueprint);
        List<RocketPartInstance> parts = blueprint.getParts();

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // Draw background
        drawQuad(textureManager, SCHEMATIC_BASE, -7f, 128f + 14f, -0.01f);

        // Center and rotate
        GL11.glTranslatef(64f, 64f, 0f);
        GL11.glRotatef(45f, 0f, 0f, 1f);
        GL11.glTranslatef(-64f, -64f, 0f);

        int minX = parts.stream()
            .mapToInt(RocketPartInstance::x)
            .min()
            .orElse(0);
        int minY = parts.stream()
            .mapToInt(RocketPartInstance::y)
            .min()
            .orElse(0);
        int maxX = parts.stream()
            .mapToInt(
                p -> p.x() + p.def()
                    .width())
            .max()
            .orElse(1);
        int maxY = parts.stream()
            .mapToInt(
                p -> p.y() + p.def()
                    .height())
            .max()
            .orElse(1);

        int totalWidth = maxX - minX;
        int totalHeight = maxY - minY;
        int usable = SIZE - PADDING * 2;
        int pixelsPerCell = usable / Math.max(totalWidth, totalHeight);
        if (pixelsPerCell < 1) pixelsPerCell = 1;

        for (RocketPartInstance part : parts) {
            ResourceLocation tex = part.def()
                .textureLocation();
            if (tex == null) tex = MISSING_TEXTURE;

            float x = PADDING + (usable - totalWidth * pixelsPerCell) / 2f + (part.x() - minX) * pixelsPerCell;
            float y = SIZE - PADDING
                - ((part.y() - minY) + part.def()
                    .height()) * pixelsPerCell;
            float w = part.def()
                .width() * pixelsPerCell;
            float h = part.def()
                .height() * pixelsPerCell;

            drawQuad(textureManager, tex, x, y, w, h, -0.02f);
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();

        drawTexts(assembly, blueprint);
    }

    private void renderSchematicInFrame(ItemStack stack, RenderManager renderManager) {
        GL11.glRotatef(180f, 0f, 1f, 0f);
        GL11.glRotatef(180f, 0f, 0f, 1f);
        GL11.glScalef(0.0088125f, 0.0088125f, 0.0088125f);
        GL11.glTranslatef(-64f, -104f, -3f);
        renderSchematic(stack, renderManager.renderEngine);
    }

    private static void drawQuad(TextureManager tm, ResourceLocation tex, float offset, float size, float z) {
        tm.bindTexture(tex);
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

    private static void drawTexts(RocketAssembly assembly, RocketBlueprint blueprint) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 0.5f);
        GL11.glTranslatef(8f, PADDING, -5f);

        String[] lines = {
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.name", blueprint.getName()),
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.stages", assembly.getStageCount()),
            StatCollector.translateToLocalFormatted(
                "galaxia.item.schematic.label.parts",
                blueprint.getParts()
                    .size()),
            StatCollector
                .translateToLocalFormatted("galaxia.item.schematic.label.delta_v", (int) assembly.getTotalDeltaV()),
            StatCollector
                .translateToLocalFormatted("galaxia.item.schematic.label.viable", assembly.viable() ? "YES" : "NO") };

        float offsetY = 16f;
        for (String line : lines) {
            GL11.glTranslatef(0f, offsetY, 0f);
            fr.drawStringWithShadow(line, 0, 0, 0xFFFFFFFF);
        }
        GL11.glPopMatrix();
    }
}
