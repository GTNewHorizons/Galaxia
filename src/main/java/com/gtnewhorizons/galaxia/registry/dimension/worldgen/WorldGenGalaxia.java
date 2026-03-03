package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.feature.WorldGenerator;

import com.gtnewhorizons.galaxia.utility.BlockMeta;

public abstract class WorldGenGalaxia extends WorldGenerator {

    private final int rarity;
    private final BlockMeta[] surfaceRequirements;

    public WorldGenGalaxia(int rarity, BlockMeta[] surfaceRequirements) {
        super();
        this.rarity = rarity;
        this.surfaceRequirements = surfaceRequirements;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return false;
        }
        Block surfaceBlock = world.getBlock(x, y - 1, z);
        int surfaceMeta = world.getBlockMetadata(x, y - 1, z);
        for (BlockMeta surfaceRequirement : surfaceRequirements) {
            if (surfaceBlock == surfaceRequirement.block() && surfaceMeta == surfaceRequirement.meta()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets block in the world at set coordinates
     *
     * @param world The world to place the block in
     * @param x     Target x coordinate
     * @param y     Target y coordinate
     * @param z     Target z coordinate
     * @param block The block to place
     * @param meta  Metadata of the block to place
     */
    protected void setBlockFast(World world, int x, int y, int z, Block block, int meta) {
        if (y < 0 || y > 255) return;

        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int sectionY = y >> 4;

        ExtendedBlockStorage currentBlockStorage = storage[sectionY];
        if (currentBlockStorage == null) {
            currentBlockStorage = storage[sectionY] = new ExtendedBlockStorage(sectionY << 4, !world.provider.hasNoSky);
        }

        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        currentBlockStorage.func_150818_a(lx, ly, lz, block);
        currentBlockStorage.setExtBlockMetadata(lx, ly, lz, meta);
        chunk.isModified = true;
    }
}
