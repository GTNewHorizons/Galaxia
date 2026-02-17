package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.GalaxiaBlockBase.reg;

import com.gtnewhorizons.galaxia.dimension.PlanetEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;

public class GalaxiaBlocks {

    public static void registerPlanetBlocks() {
        reg(PlanetEnum.CALX, GalaxiaItemList.DUST_CALX, BlockVariant.REGOLITH);
        reg(PlanetEnum.DUNIA, BlockVariant.REGOLITH, BlockVariant.ANDESITE);
    }
}
