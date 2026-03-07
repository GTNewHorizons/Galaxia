package com.gtnewhorizons.galaxia.registry.items.special;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.link.ILinkable;
import com.gtnewhorizons.galaxia.rocketmodules.link.LinkRegistry;
import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemLinkingTool extends Item {

    private static final String NBT_MASTER_X = "masterX";
    private static final String NBT_MASTER_Y = "masterY";
    private static final String NBT_MASTER_Z = "masterZ";
    private static final String NBT_MASTER_NAME = "masterName";

    private static boolean hasBound(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(NBT_MASTER_NAME);
    }

    private static ChunkCoordinates getBoundPos(ItemStack stack) {
        if (!hasBound(stack)) return null;
        NBTTagCompound nbt = stack.getTagCompound();
        return new ChunkCoordinates(
            nbt.getInteger(NBT_MASTER_X),
            nbt.getInteger(NBT_MASTER_Y),
            nbt.getInteger(NBT_MASTER_Z));
    }

    private static String getBoundName(ItemStack stack) {
        return hasBound(stack) ? stack.getTagCompound()
            .getString(NBT_MASTER_NAME) : null;
    }

    private static void setBound(ItemStack stack, int x, int y, int z, String name) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound nbt = stack.getTagCompound();
        nbt.setInteger(NBT_MASTER_X, x);
        nbt.setInteger(NBT_MASTER_Y, y);
        nbt.setInteger(NBT_MASTER_Z, z);
        nbt.setString(NBT_MASTER_NAME, name);
    }

    private static void clearBound(ItemStack stack) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound nbt = stack.getTagCompound();
        nbt.removeTag(NBT_MASTER_X);
        nbt.removeTag(NBT_MASTER_Y);
        nbt.removeTag(NBT_MASTER_Z);
        nbt.removeTag(NBT_MASTER_NAME);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking() && hasBound(stack)) {
            clearBound(stack);
            player.addChatMessage(new ChatComponentText(GalaxiaAPI.translate("galaxia.linking_tool.cleared")));
        }
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(x, y, z);

        // Shift+RMB — bind master
        if (player.isSneaking()) {
            if (!(te instanceof ILinkable linkable)) {
                player.addChatMessage(
                    new ChatComponentText(GalaxiaAPI.translate("galaxia.linking_tool.not_linkable_master")));
                return true;
            }
            if (!linkable.canBeMaster()) {
                player.addChatMessage(
                    new ChatComponentText(
                        GalaxiaAPI.format("galaxia.linking_tool.not_master", linkable.getLinkableName())));
                return true;
            }
            setBound(stack, x, y, z, linkable.getLinkableName());
            player.addChatMessage(
                new ChatComponentText(
                    GalaxiaAPI.format("galaxia.linking_tool.bound", linkable.getLinkableName(), x, y, z)));
            return true;
        }

        // RMB — link slave
        if (!hasBound(stack)) {
            player.addChatMessage(new ChatComponentText(GalaxiaAPI.translate("galaxia.linking_tool.no_master")));
            return true;
        }

        if (!(te instanceof ILinkable slave)) {
            player
                .addChatMessage(new ChatComponentText(GalaxiaAPI.translate("galaxia.linking_tool.not_linkable_slave")));
            return true;
        }

        if (!slave.canBeSlave()) {
            player.addChatMessage(
                new ChatComponentText(GalaxiaAPI.format("galaxia.linking_tool.not_slave", slave.getLinkableName())));
            return true;
        }

        ChunkCoordinates masterPos = getBoundPos(stack);
        String masterName = getBoundName(stack);

        TileEntity masterTE = world.getTileEntity(masterPos.posX, masterPos.posY, masterPos.posZ);
        if (masterTE == null) {
            player.addChatMessage(new ChatComponentText(GalaxiaAPI.translate("galaxia.linking_tool.master_gone")));
            clearBound(stack);
            return true;
        }

        if (!LinkRegistry.areCompatible(masterTE, te)) {
            player.addChatMessage(
                new ChatComponentText(
                    GalaxiaAPI.format("galaxia.linking_tool.incompatible", slave.getLinkableName(), masterName)));
            return true;
        }

        slave.setMasterPos(masterPos);
        ((ILinkable) masterTE).onSlaveLinked(te, player);

        player.addChatMessage(
            new ChatComponentText(
                GalaxiaAPI.format(
                    "galaxia.linking_tool.linked",
                    slave.getLinkableName(),
                    x,
                    y,
                    z,
                    masterName,
                    masterPos.posX,
                    masterPos.posY,
                    masterPos.posZ)));

        return true;
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(GalaxiaAPI.translate("item.galaxia.linking_tool.desc.usage"));
        tooltip.add(GalaxiaAPI.translate("item.galaxia.linking_tool.desc.shift_rmb_air"));
        tooltip.add(GalaxiaAPI.translate("item.galaxia.linking_tool.desc.shift_rmb"));
        tooltip.add(GalaxiaAPI.translate("item.galaxia.linking_tool.desc.rmb"));

        ChunkCoordinates pos = getBoundPos(stack);
        String name = getBoundName(stack);
        tooltip.add(
            pos != null
                ? GalaxiaAPI.format("item.galaxia.linking_tool.desc.bound_to", name, pos.posX, pos.posY, pos.posZ)
                : GalaxiaAPI.translate("item.galaxia.linking_tool.desc.not_bound"));
    }

    public static ChunkCoordinates getBoundMasterPos(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemLinkingTool)) return null;
        return getBoundPos(held);
    }
}
