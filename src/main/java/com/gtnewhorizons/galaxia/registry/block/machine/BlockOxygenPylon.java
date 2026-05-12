package com.gtnewhorizons.galaxia.registry.block.machine;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;

public class BlockOxygenPylon extends BlockGalaxiaMachine {

    public BlockOxygenPylon() {
        super("galaxia:machine/oxygen_pylon_on", "galaxia:machine/oxygen_pylon_off");
        setBlockName("oxygen_pylon");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityOxygenPylon();
    }
}
