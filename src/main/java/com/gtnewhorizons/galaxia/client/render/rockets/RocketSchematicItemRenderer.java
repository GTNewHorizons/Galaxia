package com.gtnewhorizons.galaxia.client.render.rockets;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.drawable.GuiDraw;
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
        // Held in third person it shows its icon, like a map
        return switch (type) {
            case FIRST_PERSON_MAP -> true;
            case ENTITY -> RenderItem.renderInFrame;
            default -> false;
        };
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        switch (type) {
            // The game already holds a 128 by 128 sheet in both hands, as for a map
            case FIRST_PERSON_MAP -> renderSchematic(stack, Minecraft.getMinecraft().renderEngine);
            case ENTITY -> {
                GL11.glPushMatrix();
                renderSchematicInFrame(stack, RenderManager.instance);
                GL11.glPopMatrix();
            }
            default -> {}
        }
    }

    private void renderSchematic(ItemStack stack, TextureManager textureManager) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // The blank sheet, also all an empty blueprint shows
        drawQuad(textureManager, SCHEMATIC_BASE, -7f, 128f + 14f, -0.01f);

        RocketBlueprint blueprint = ItemRocketSchematic.getBlueprint(stack);
        if (blueprint == null || blueprint.isEmpty()) {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            return;
        }

        RocketAssembly assembly = RocketAnalyzer.analyze(blueprint);
        List<RocketPartInstance> parts = blueprint.getParts();

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
            float x = PADDING + (usable - totalWidth * pixelsPerCell) / 2f + (part.x() - minX) * pixelsPerCell;
            float y = PADDING + (usable - totalHeight * pixelsPerCell) / 2f + (part.y() - minY) * pixelsPerCell;
            float w = part.def()
                .width() * pixelsPerCell;
            float h = part.def()
                .height() * pixelsPerCell;

            if (part.def()
                .modelLocation() != null) {
                // GUI depth points at the viewer, on the sheet it points away, and the drawing lies flat on the paper
                GL11.glPushMatrix();
                GL11.glScalef(1f, 1f, -0.01f);
                ModuleModelRenderer.drawInGui(part.def(), x, y, w, h, 1f);
                GL11.glPopMatrix();
            } else {
                drawQuad(textureManager, MISSING_TEXTURE, x, y, w, h, -0.02f);
            }
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
        drawQuad(tm, tex, offset, offset, size, size, z);
    }

    private static void drawQuad(TextureManager tm, ResourceLocation tex, float x, float y, float w, float h, float z) {
        tm.bindTexture(tex);
        GuiDraw.drawTexture(x, y, x + w, y + h, 0f, 0f, 1f, 1f, z);
    }

    private static void drawTexts(RocketAssembly assembly, RocketBlueprint blueprint) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 0.5f);
        GL11.glTranslatef(8f, PADDING, -5f);

        String[] lines = {
            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.name", blueprint.getName()),

            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.stages", assembly.getStageCount()),

            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.height", blueprint.getHeight()),

            StatCollector.translateToLocalFormatted("galaxia.item.schematic.label.width", blueprint.getWidth()),

            StatCollector
                .translateToLocalFormatted("galaxia.item.schematic.label.weight", (int) assembly.getTotalMass()),

            StatCollector
                .translateToLocalFormatted("galaxia.item.schematic.label.thrust", (int) assembly.getTotalThrust()) };

        // Text and its shadow lie in one plane, written to depth they would fight over every pixel
        GL11.glDepthMask(false);
        float offsetY = 16f;
        for (String line : lines) {
            GL11.glTranslatef(0f, offsetY, 0f);
            fr.drawStringWithShadow(line, 0, 0, 0xFFFFFFFF);
        }
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }
}
