package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.core.Galaxia;

/**
 * Item store of a station, joins the station's shared storage when placed inside one of its rooms
 */
public class BlockStationItemVault extends Block implements ITileEntityProvider {

    public BlockStationItemVault() {
        super(Material.iron);
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setBlockName("station_item_vault");
        setBlockTextureName("galaxia:space_station/station_item_vault");
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileStationItemVault();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }
}
