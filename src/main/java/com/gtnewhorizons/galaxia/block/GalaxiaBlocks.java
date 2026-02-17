package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.GalaxiaBlockBase.reg;

import com.gtnewhorizons.galaxia.dimension.PlanetEnum;

public class GalaxiaBlocks {

    public static void registerPlanetBlocks() {
        reg(PlanetEnum.CALX, BlockVariant.ROCK);
        reg(PlanetEnum.DUNIA, BlockVariant.ROCK, BlockVariant.SAND);
    }
}
