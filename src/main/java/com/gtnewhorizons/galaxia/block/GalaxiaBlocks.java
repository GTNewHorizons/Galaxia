package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.GalaxiaBlockBase.reg;

import com.gtnewhorizons.galaxia.dimension.PlanetEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;

public class GalaxiaBlocks {

    // spotless:off
    public static void registerPlanetBlocks() {
        reg(
            PlanetEnum.CALX,
            GalaxiaItemList.DUST_CALX,
            BlockVariant.REGOLITH,
            BlockVariant.TEKTITE,
            BlockVariant.MAGMA,
            BlockVariant.GABBRO,
            BlockVariant.BRECCIA,
            BlockVariant.BASALT,
            BlockVariant.ANORTHOSITE,
            BlockVariant.ANDESITE);
        reg(PlanetEnum.DUNIA,
            BlockVariant.REGOLITH,
            BlockVariant.ANDESITE);
    }
    //spotless:on
}
