package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly.ModulePlacement;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public class RocketSchematicItemRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.FIRST_PERSON_MAP
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        List<Integer> moduleIds = ItemRocketSchematic.readModules(item);
        if (moduleIds.isEmpty()) return;

        RocketAssembly assembly = new RocketAssembly(moduleIds);
        List<RocketModule> modules = assembly.getModules();
        if (modules.isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_CULL_FACE);

        try {
            applyTypeTransform(type, assembly);
            setupHoloGLState();
            renderHologram(modules, assembly);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private void applyTypeTransform(ItemRenderType type, RocketAssembly assembly) {
        double totalHeight = assembly.getTotalHeight();
        double totalWidth = assembly.getTotalWidth();
        float scale = (float) (1.0 / (Math.max(totalHeight, 1.0) * 1.1));

        switch (type) {

            case INVENTORY:
                GL11.glTranslatef(10.0f, 8.0f, 0.0f);
                GL11.glRotatef(30.0f, 1.0f, 0.0f, 0.0f);
                GL11.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
                GL11.glScalef(16.0f * scale, -16.0f * scale, 16.0f * scale);
                GL11.glTranslatef((float) (-totalWidth / 2.0), (float) (-totalHeight / 2.0), 0.0f);
                break;

            case EQUIPPED_FIRST_PERSON:
                GL11.glTranslatef(0.5f, 1.1f, 0.5f);
                GL11.glRotatef(-60.0f, 0.0f, 0.0f, 1.0f);
                GL11.glRotatef(20.0f, 0.0f, 1.0f, 0.0f);
                GL11.glScalef(scale, scale, scale);
                GL11.glTranslatef((float) (-totalWidth / 2.0), 0.0f, 0.0f);
                break;

            case EQUIPPED:
                GL11.glTranslatef(0.5f, 0.2f, 0.0f);
                GL11.glScalef(scale, scale, scale);
                GL11.glTranslatef((float) (-totalWidth / 2.0), 0.0f, 0.0f);
                break;

            case ENTITY:
                GL11.glTranslatef(0.0f, 0.25f, 0.0f);
                GL11.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
                GL11.glScalef(scale * 0.5f, scale * 0.5f, scale * 0.5f);
                GL11.glTranslatef((float) (-totalWidth / 2.0), (float) (-totalHeight / 2.0), 0.0f);
                break;

            case FIRST_PERSON_MAP:
                GL11.glTranslatef(0.5f, 0.5f, 0.0f);
                GL11.glScalef(scale * 0.4f, scale * 0.4f, scale * 0.4f);
                GL11.glTranslatef((float) (-totalWidth / 2.0), (float) (-totalHeight / 2.0), 0.0f);
                break;

            default:
                break;
        }

    }

    private void renderHologram(List<RocketModule> modules, RocketAssembly assembly) {
        List<ModulePlacement> placements = assembly.getPlacements();

        double totalWidth = assembly.getTotalWidth();
        GL11.glTranslatef((float) (-totalWidth / 2.0), 0f, 0f);

        for (ModulePlacement placement : placements) {
            RocketModule module = placement.type();
            GL11.glPushMatrix();
            GL11.glTranslated(placement.x(), placement.y() + module.getHeight() / 2.0, placement.z());
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(module.getHoloTexture());
            module.getModel()
                .renderAll();

            GL11.glPopMatrix();
        }
    }

    private void setupHoloGLState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive blending for glow
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        // Slight overall blue-white tint on top of the texture's own holo colours
        GL11.glColor4f(0.8f, 0.95f, 1.0f, 0.85f);
    }
}
