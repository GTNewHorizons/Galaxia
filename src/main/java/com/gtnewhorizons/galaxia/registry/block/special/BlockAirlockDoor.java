package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.base.BlockOpenable;
import com.gtnewhorizons.galaxia.registry.block.tile.TileEntityAirlock;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class BlockAirlockDoor extends BlockOpenable {

    public BlockAirlockDoor() {
        super(Material.iron);

        setBlockName("airlock_door");
        setBlockTextureName("galaxia:machine/airlock_door");

        setHardness(2.0F);
        setResistance(10.0F);
        this.setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        searchAndOpenDoor(world, x, y, z);
        return false;
    }

    public static void searchAndOpenDoor(World world, int x, int y, int z) {
        final int searchRadius = TileEntityAirlock.MAXIMUM_RADIUS + 1;

        IntQueue floodBFS = new IntQueue();
        IntOpenHashSet visited = new IntOpenHashSet();

        int start = LocalCoord.pack(0, 0, 0, searchRadius);
        visited.add(start);
        floodBFS.enqueue(start);
        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx = LocalCoord.unpackX(cur, searchRadius);
            int ly = LocalCoord.unpackY(cur, searchRadius);
            int lz = LocalCoord.unpackZ(cur, searchRadius);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz, searchRadius)) continue;

                int np = LocalCoord.pack(nlx, nly, nlz, searchRadius);
                if (visited.contains(np)) continue;

                int wx = LocalCoord.worldX(nlx, x);
                int wy = LocalCoord.worldY(nly, y);
                int wz = LocalCoord.worldZ(nlz, z);

                Block b = world.getBlock(wx, wy, wz);

                if (b == GalaxiaBlocksEnum.AIRLOCK_DOOR.get() || b == GalaxiaBlocksEnum.AIRLOCK_CASING.get()) {
                    visited.add(np);
                    floodBFS.enqueue(np);
                } else if (b == GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get()) {
                    BlockAirlockController controller = (BlockAirlockController) b;
                    controller.toggleDoor(world, wx, wy, wz);
                    return;
                }
            }
        }
    }
}
