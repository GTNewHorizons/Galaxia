package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Block for the Rocket Silo Controller
 */
public class BlockSilo extends Block implements ITileEntityProvider {

    @SideOnly(Side.CLIENT)
    private IIcon frontIconOff;

    @SideOnly(Side.CLIENT)
    private IIcon frontIconOn;

    public BlockSilo() {
        super(Material.rock);
        this.setHardness(1.5F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.frontIconOff = iconRegister.registerIcon("galaxia:machine/silo_off");
        this.frontIconOn = iconRegister.registerIcon("galaxia:machine/silo_on");

    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (meta == 0) { // item in inventory - show the controller face
            if (side == 4) return frontIconOff;
            return GalaxiaBlocksEnum.RUSTY_PANEL.get()
                .getIcon(side, 0);
        }

        // Metavalues 2-5 for when the structure is unformed, 6-9 for when it is formed
        boolean formed = meta >= 6;
        int facing = formed ? meta - 4 : meta;

        if (side == facing) return formed ? frontIconOn : frontIconOff;
        return GalaxiaBlocksEnum.RUSTY_PANEL.get()
            .getIcon(side, 0);
    }

    /**
     * Makes sure the controller face always faces the player
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {

        int facing = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;

        int meta = switch (facing) {
            case 0 -> ForgeDirection.NORTH.ordinal(); // player faces south, front faces north
            case 1 -> ForgeDirection.EAST.ordinal();
            case 2 -> ForgeDirection.SOUTH.ordinal();
            case 3 -> ForgeDirection.WEST.ordinal();
            default -> ForgeDirection.NORTH.ordinal();
        };
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntitySilo();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo silo) silo.kill();

    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }
}
