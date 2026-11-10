package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Full block where a rail enters a machine. The rail itself is not drawn here, faces with a rail in front of them
 * show the recess the rail plugs into instead
 */
public class BlockGantryTerminal extends BlockGantry {

    @SideOnly(Side.CLIENT)
    private IIcon slotIcon;
    @SideOnly(Side.CLIENT)
    private IIcon socketIcon;

    public BlockGantryTerminal() {
        super();
        this.setBlockTextureName("galaxia:machine/launchpad_sheeting");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantryTerminal();
    }

    @Override
    public int getRenderType() {
        return 0;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list,
        Entity entity) {
        setBlockBounds(0, 0, 0, 1, 1, 1);
        AxisAlignedBB box = getCollisionBoundingBoxFromPool(world, x, y, z);
        if (box != null && mask.intersectsWith(box)) list.add(box);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        slotIcon = register.registerIcon("galaxia:machine/gantry_terminal_slot");
        socketIcon = register.registerIcon("galaxia:machine/gantry_terminal_socket");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        TileEntity neighbour = world.getTileEntity(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
        // Terminals next to each other connect without a visible rail between them
        if (neighbour instanceof TileEntityGantry && !(neighbour instanceof TileEntityGantryTerminal)) {
            return dir.offsetY == 0 ? slotIcon : socketIcon;
        }
        return blockIcon;
    }

    /**
     * Right click reports the terminal and its network, sneak right click with an empty hand cycles the mode
     *
     * @return Boolean : True => handled
     */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (player.getHeldItem() != null) return false;
        if (world.isRemote) return true;
        if (!(world.getTileEntity(x, y, z) instanceof TileEntityGantryTerminal terminal)) return false;

        if (player.isSneaking()) terminal.cycleMode();
        for (IChatComponent line : describe(terminal)) {
            player.addChatComponentMessage(line);
        }
        return true;
    }

    private static IChatComponent[] describe(TileEntityGantryTerminal terminal) {
        TerminalMode mode = terminal.getMode();
        IChatComponent modeName = new ChatComponentTranslation(mode.langKey());
        modeName.getChatStyle()
            .setColor(mode.color);

        String machineKey = terminal.getSilo() != null ? "galaxia.gantry.machine.silo"
            : terminal.getAssembler() != null ? "galaxia.gantry.machine.assembler" : "galaxia.gantry.machine.none";

        GantryNetwork network = terminal.getNetwork();
        return new IChatComponent[] {
            new ChatComponentTranslation(
                "galaxia.gantry.terminal.status",
                modeName,
                new ChatComponentTranslation(machineKey)),
            new ChatComponentTranslation(
                "galaxia.gantry.network.status",
                network.size(),
                network.getProviders()
                    .size(),
                network.getReceivers()
                    .size()),
            new ChatComponentTranslation("galaxia.gantry.terminal.hint") };
    }
}
