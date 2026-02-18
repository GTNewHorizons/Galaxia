package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.GalaxiaBlockBase.reg;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;

public class GalaxiaBlocks {

    // spotless:off
    public static void registerPlanetBlocks() {
        reg(
            DimensionEnum.CALX,
            GalaxiaItemList.DUST_CALX,
            BlockVariant.REGOLITH,
            BlockVariant.TEKTITE,
            BlockVariant.MAGMA,
            BlockVariant.GABBRO,
            BlockVariant.BRECCIA,
            BlockVariant.BASALT,
            BlockVariant.ANORTHOSITE,
            BlockVariant.ANDESITE);
        reg(DimensionEnum.DUNIA,
            BlockVariant.REGOLITH,
            BlockVariant.ANDESITE);
    }
    //spotless:on
}
