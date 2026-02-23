package com.gtnewhorizons.galaxia.modules.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.block.tileentities.TileEntityModuleController;
import com.gtnewhorizons.galaxia.items.special.ItemModuleMover;
import com.gtnewhorizons.galaxia.modules.ModuleType;

public class ModuleTESR extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileEntityModuleController ctrl)) return;

        ModuleType type = ctrl.getType();
        if (type == null) return;

        if (isSelected(ctrl)) {
            GL11.glColor4f(1.0F, 0.3F, 0.3F, 1.0F); // красный tint
        }

        IModelCustom model = type.getModel();
        if (model != null) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5 + type.getOffsetX(), y + 0.5 + type.getOffsetY(), z + 0.5 + type.getOffsetZ());
            GL11.glScalef(type.getScale(), type.getScale(), type.getScale());

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(type.getTextureLocation());
            model.renderAll();

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);

            if (isSelected(ctrl)) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }

            GL11.glPopMatrix();
        }
    }

    private boolean isSelected(TileEntityModuleController ctrl) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return false;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemModuleMover)) return false;
        int[] sel = ItemModuleMover.getSelectedPos(held);
        if (sel == null) return false;
        return sel[0] == ctrl.xCoord && sel[1] == ctrl.yCoord
            && sel[2] == ctrl.zCoord
            && ctrl.getWorldObj() == player.worldObj;
    }
}
