package com.gtnewhorizons.galaxia.registry.block.machine;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;

public class BlockOxygenCollector extends BlockGalaxiaMachine {

    public BlockOxygenCollector() {
        super("galaxia:machine/oxygen_collector_on", "galaxia:machine/oxygen_collector_off");
        setBlockName("oxygen_collector");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityOxygenCollector();
    }
}
