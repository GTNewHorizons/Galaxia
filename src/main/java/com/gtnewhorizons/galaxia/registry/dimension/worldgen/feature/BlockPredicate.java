package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

@FunctionalInterface
public interface BlockPredicate {

    boolean test(Block block, int meta);

    default boolean test(ImmutableBlockMeta blockMeta) {
        return test(blockMeta.getBlock(), blockMeta.getBlockMeta());
    }

    default boolean test(World world, int x, int y, int z) {
        return test(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
    }

    static BlockPredicate matches(Block block) {
        return (b, m) -> b == block;
    }
}
