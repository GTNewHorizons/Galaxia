package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.PlacementHelper;
import com.gtnewhorizons.galaxia.registry.block.base.BlockOpenable;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockAirlockController extends BlockOpenable implements ITileEntityProvider {

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;

    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;

    @SideOnly(Side.CLIENT)
    private IIcon closedOverlay;

    public BlockAirlockController() {
        super(Material.rock);
        this.setHardness(1.5F);
        this.setBlockName("airlock_controller");
        this.setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.frontIcon = reg.registerIcon("galaxia:machine/airlock_controller");
        this.sideIcon = reg.registerIcon("galaxia:machine/airlock_casing");
        this.closedOverlay = reg.registerIcon("galaxia:machine/airlock_controller_closed");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 2 || side == 3 ? frontIcon : sideIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);

        if (te instanceof TileEntityAirlock airlock) {
            ForgeDirection facing = airlock.getPlacedFacing();

            if (side == facing.ordinal() || side == facing.getOpposite()
                .ordinal()) {
                return airlock.isOpen() ? closedOverlay : frontIcon;
            }
        }

        return sideIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAirlock();
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);

        if (!(te instanceof TileEntityAirlock airlock)) return;
        ForgeDirection facing = PlacementHelper.placeInEveryDirection(placer);

        airlock.setPlacedFacing(facing);
        airlock.setFacing(facing);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        return toggleDoor(world, x, y, z);
    }

    public boolean toggleDoor(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAirlock) {
            ((TileEntityAirlock) te).toggleState();
            return true;
        }

        return false;
    }
}
