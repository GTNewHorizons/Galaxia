package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

/**
 * Draws the Gantry Shaft item as a straight rail. Forge's block helpers place it like any other block item, the beam
 * is only scaled up so it fills a slot the way a full block does
 */
public class GantryItemRenderer implements IItemRenderer {

    /** A beam is half as thick as a block, scaled up it covers a slot like a cube */
    private static final float INVENTORY_SCALE = 1.45f;

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return switch (type) {
            case INVENTORY -> helper == ItemRendererHelper.INVENTORY_BLOCK;
            case ENTITY -> helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING
                || helper == ItemRendererHelper.BLOCK_3D;
            case EQUIPPED, EQUIPPED_FIRST_PERSON -> helper == ItemRendererHelper.EQUIPPED_BLOCK;
            default -> false;
        };
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        switch (type) {
            case INVENTORY -> GL11.glScalef(INVENTORY_SCALE, INVENTORY_SCALE, INVENTORY_SCALE);
            // The equipped helper moves the origin to the corner of the block
            case EQUIPPED, EQUIPPED_FIRST_PERSON -> GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            default -> {}
        }
        Minecraft.getMinecraft().renderEngine.bindTexture(GantryRailModels.TEXTURE);
        ModelCache.get(GantryRailModels.STRAIGHT)
            .renderAll();
        GL11.glPopMatrix();
    }
}
