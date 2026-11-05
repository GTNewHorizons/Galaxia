package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation;
import com.gtnewhorizons.galaxia.registry.block.PlacementHelper;
import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockBodyStation extends BlockUpdatable implements ITileEntityProvider {

    private final TileEntityBodyStation.Kind kind;

    @SideOnly(Side.CLIENT)
    private IIcon casingIcon;

    public BlockBodyStation(TileEntityBodyStation.Kind kind) {
        super(Material.iron);
        this.kind = kind;
        setBlockName(kind.blockName);
        setHardness(3);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return kind == TileEntityBodyStation.Kind.DOCK ? new TileEntityBodyStation.Dock()
            : new TileEntityBodyStation.Capsule();
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon("galaxia:machine/airlock_controller");
        casingIcon = register.registerIcon("galaxia:space_station/space_station_panel");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == ForgeDirection.NORTH.ordinal() ? blockIcon : casingIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (world.getTileEntity(x, y, z) instanceof TileEntityBodyStation station) {
            ForgeDirection inward = station.getCurrentFacing()
                .getRelativeBackInWorld()
                .getOpposite();
            return side == inward.ordinal() ? blockIcon : casingIcon;
        }
        return getIcon(side, world.getBlockMetadata(x, y, z));
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (!(world.getTileEntity(x, y, z) instanceof TileEntityBodyStation station)) return;
        ForgeDirection facing = PlacementHelper.placeOnlyCardinal(placer);
        station.setPlacedFacing(facing);
        station.setFacing(facing);
        if (!world.isRemote && placer instanceof EntityPlayer player) station.onPlaced(player);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (!(world.getTileEntity(x, y, z) instanceof TileEntityBodyStation station) || !station.canOperate(player)) {
            return false;
        }
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileEntityBodyStation station) {
            for (ItemStack stack : station.removeInputs()) {
                world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
        world.removeTileEntity(x, y, z);
    }
}
