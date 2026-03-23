package com.gtnewhorizons.galaxia.registry.block.planet;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import net.minecraft.block.Block;

import java.util.HashMap;

public class PlanetBlockMap {
    private final HashMap<DimensionEnum, HashMap<PlanetBlockType, Block>> planetMap = new HashMap<>();

    public void addBlock(DimensionEnum dimension, PlanetBlockType planetBlockType, Block block) {
        HashMap<PlanetBlockType, Block> blockMap = planetMap.get(dimension);
        if (blockMap == null) {
            blockMap = new HashMap<>();
            blockMap.put(planetBlockType, block);
            planetMap.put(dimension, blockMap);
        } else {
            blockMap.put(planetBlockType, block);
            planetMap.replace(dimension, blockMap);
        }
    }

    public Block getBlock(DimensionEnum dimension, PlanetBlockType planetBlockType) {
        if (planetBlockType == null) {
            return null;
        }
        if (dimension == null) {
            return null;
        }
        HashMap<PlanetBlockType, Block> blockMap = planetMap.get(dimension);
        if (blockMap == null) {
            return null;
        }
        return blockMap.get(planetBlockType);
    }
}
