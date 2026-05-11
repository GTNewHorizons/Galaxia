package com.gtnewhorizons.galaxia.registry.block.machine;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenFiller;

public class BlockOxygenFiller extends BlockGalaxiaMachine {

    public BlockOxygenFiller() {
        super("galaxia:machine/oxygen_filler_on", "galaxia:machine/oxygen_filler_off");
        setBlockName("oxygen_collector");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityOxygenFiller();
    }
}
