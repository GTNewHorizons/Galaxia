package com.gtnewhorizons.galaxia.registry.items.special;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.link.ILinkable;
import com.gtnewhorizons.galaxia.rocketmodules.link.LinkRegistry;

/**
 * Usage:
 * 1. LMB on a master block - stores master position in item NBT, prints "[Name] @ X,Y,Z bound"
 * 2. RMB on a slave block - reads stored master, validates via LinkRegistry, performs link
 * <p>
 * Adding a new linkable pair:
 * - Implement {@link ILinkable} on both TileEntities
 * - Call {@code LinkRegistry.register(MasterClass.class, SlaveClass.class)} at init
 * (No changes needed to this item)
 */
public class ItemLinkingTool extends Item {

    private static final String NBT_MASTER_X = "masterX";
    private static final String NBT_MASTER_Y = "masterY";
    private static final String NBT_MASTER_Z = "masterZ";
    private static final String NBT_MASTER_NAME = "masterName";
    private static final String NBT_HAS_MASTER = "hasMaster";

    public ItemLinkingTool() {
        super();
        this.setMaxStackSize(1);
        this.setUnlocalizedName("galaxia.linking_tool");
    }

    /**
     * Called when the player left-clicks a block
     * Binds the clicked block as master if it implements ILinkable and canBeMaster()
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return false;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof ILinkable)) return false;

        ILinkable linkable = (ILinkable) te;
        if (!linkable.canBeMaster()) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentText("§c" + linkable.getLinkableName() + " cannot be a master."));
            return true;
        }

        NBTTagCompound nbt = checkNbt(stack);
        nbt.setInteger(NBT_MASTER_X, x);
        nbt.setInteger(NBT_MASTER_Y, y);
        nbt.setInteger(NBT_MASTER_Z, z);
        nbt.setString(NBT_MASTER_NAME, linkable.getLinkableName());
        nbt.setBoolean(NBT_HAS_MASTER, true);

        player.addChatMessage(
            new net.minecraft.util.ChatComponentText(
                "§a" + linkable.getLinkableName() + " §7@ " + x + "," + y + "," + z + " §abound."));
        return true;
    }

    /**
     * Called when the player right-clicks a block
     * Links the clicked block as slave to the previously bound master
     */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return false;

        NBTTagCompound nbt = checkNbt(stack);

        if (!nbt.getBoolean(NBT_HAS_MASTER)) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentText("§eNo master bound. Left-click a master block first."));
            return true;
        }

        TileEntity slave = world.getTileEntity(x, y, z);
        if (!(slave instanceof ILinkable)) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText("§cThis block cannot be linked."));
            return true;
        }

        ILinkable lSlave = (ILinkable) slave;
        if (!lSlave.canBeSlave()) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentText("§c" + lSlave.getLinkableName() + " cannot be a slave."));
            return true;
        }

        int mx = nbt.getInteger(NBT_MASTER_X);
        int my = nbt.getInteger(NBT_MASTER_Y);
        int mz = nbt.getInteger(NBT_MASTER_Z);
        String masterName = nbt.getString(NBT_MASTER_NAME);

        TileEntity master = world.getTileEntity(mx, my, mz);
        if (master == null) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText("§cBound master is gone. Please re-bind."));
            clearMaster(stack);
            return true;
        }

        if (!LinkRegistry.areCompatible(master, slave)) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentText(
                    "§c" + lSlave.getLinkableName() + " is not compatible with " + masterName + "."));
            return true;
        }

        ChunkCoordinates masterPos = new ChunkCoordinates(mx, my, mz);
        lSlave.setMasterPos(masterPos);
        ((ILinkable) master).onSlaveLinked(slave, player);

        player.addChatMessage(
            new net.minecraft.util.ChatComponentText(
                "§a" + lSlave.getLinkableName()
                    + " §7@ "
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + " §alinked to §7"
                    + masterName
                    + " §a@ "
                    + mx
                    + ","
                    + my
                    + ","
                    + mz
                    + "."));
        return true;
    }

    private NBTTagCompound checkNbt(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }

    private void clearMaster(ItemStack stack) {
        NBTTagCompound nbt = checkNbt(stack);
        nbt.setBoolean(NBT_HAS_MASTER, false);
    }

    /**
     * used for renderer overlays (highlight the bound master block in-world)
     */
    public ChunkCoordinates getBoundMasterPos(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        NBTTagCompound nbt = stack.getTagCompound();
        if (!nbt.getBoolean(NBT_HAS_MASTER)) return null;
        return new ChunkCoordinates(
            nbt.getInteger(NBT_MASTER_X),
            nbt.getInteger(NBT_MASTER_Y),
            nbt.getInteger(NBT_MASTER_Z));
    }
}
