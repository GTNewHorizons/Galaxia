package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.compat.structure.util.IntQueue;
import com.gtnewhorizons.galaxia.compat.structure.util.LocalCoord;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.PlacementHelper;
import com.gtnewhorizons.galaxia.registry.block.base.BlockOpenable;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class BlockAirlockDoor extends BlockOpenable {

    public static final int ORIENT_Y = 0;
    public static final int ORIENT_X = 1;
    public static final int ORIENT_Z = 2;

    private static final int ORIENTATION_SHIFT = 1;
    private static final int ORIENTATION_MASK = 0b110;

    public BlockAirlockDoor() {
        super(Material.iron);

        setBlockName("airlock_door");
        setBlockTextureName("galaxia:machine/airlock_door");

        setHardness(2.0F);
        setResistance(10.0F);
        this.setCreativeTab(Galaxia.creativeTab);
    }

    public static int encodeMeta(boolean open, int orientation) {
        return (open ? META_OPEN : META_CLOSED) | (orientation << ORIENTATION_SHIFT);
    }

    public static int getOrientation(int meta) {
        return (meta & ORIENTATION_MASK) >> ORIENTATION_SHIFT;
    }

    public static int orientationForAxis(ForgeDirection axis) {
        return switch (axis) {
            case EAST, WEST -> ORIENT_X;
            case NORTH, SOUTH -> ORIENT_Z;
            default -> ORIENT_Y;
        };
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int orientation;
        if (placer == null) {
            orientation = ORIENT_Y;
        } else {
            orientation = switch (PlacementHelper.placeInEveryDirection(placer)) {
                case UP, DOWN -> ORIENT_Y;
                case NORTH, SOUTH -> ORIENT_Z;
                default -> ORIENT_X;
            };
        }

        world.setBlockMetadataWithNotify(x, y, z, encodeMeta(false, orientation), 3);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        switch (getOrientation(world.getBlockMetadata(x, y, z))) {
            case ORIENT_X -> setBlockBounds(0.25F, 0.0F, 0.0F, 0.75F, 1.0F, 1.0F);
            case ORIENT_Z -> setBlockBounds(0.0F, 0.0F, 0.25F, 1.0F, 1.0F, 0.75F);
            default -> setBlockBounds(0.0F, 0.25F, 0.0F, 1.0F, 0.75F, 1.0F);
        }
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0.0F, 0.25F, 0.0F, 1.0F, 0.75F, 1.0F);
    }

    @Override
    public void setOpen(World world, int x, int y, int z, boolean open) {
        int meta = world.getBlockMetadata(x, y, z);
        world.setBlockMetadataWithNotify(x, y, z, encodeMeta(open, getOrientation(meta)), 3);
    }

    @Override
    public boolean isOpen(IBlockAccess world, int x, int y, int z) {
        return (world.getBlockMetadata(x, y, z) & META_OPEN) != 0;
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

                if (b == GalaxiaBlocksEnum.AIRLOCK_DOOR.get()) {
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
