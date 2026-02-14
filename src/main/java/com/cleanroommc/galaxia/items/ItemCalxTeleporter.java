package com.cleanroommc.galaxia.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;

import static com.cleanroommc.galaxia.dimension.SolarSystemRegistry.CALX;

public class ItemCalxTeleporter extends Item {
    public ItemCalxTeleporter() {
        super();
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP playerMP) {
            int dimId = CALX.id;
            playerMP.mcServer.getConfigurationManager().transferPlayerToDimension(
                playerMP,
                dimId,
                new Teleporter(playerMP.mcServer.worldServerForDimension(dimId)) {
                    @Override
                    public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
                        entity.setLocationAndAngles(0, 80, 0, yaw, 0);
                    }

                    @Override
                    public boolean makePortal(Entity entity) {
                        return true;
                    }
                }
            );
        }
        player.swingItem();
        return stack;
    }
}
