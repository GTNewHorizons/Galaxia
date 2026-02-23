package com.gtnewhorizons.galaxia.items.special;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.client.gui.GuiPlanetTeleporter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A class mainly used as a temporary debugging tool. Allows user to teleport to supplied coordinates on
 * a chosen Galaxia Dimension
 */
public class ItemTeleporter extends Item {

    public ItemTeleporter() {
        super();
        this.setMaxStackSize(1);
    }

    /**
     * Defines the right click behaviour to open the teleportation GUI
     *
     * @param stack  The item stack defining the held item
     * @param world  The current world of the using player
     * @param player The player using the item
     * @return The item stack initially used
     */
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
