package com.gtnewhorizons.galaxia.items;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.client.gui.GuiPlanetTeleporter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemTeleporter extends Item {

    public ItemTeleporter() {
        super();
        this.setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiPlanetTeleporter());
        }
        player.swingItem();
        return stack;
    }
}
