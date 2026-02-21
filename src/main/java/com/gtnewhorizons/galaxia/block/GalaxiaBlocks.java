package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.GalaxiaBlockBase.reg;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.modules.BlockModuleController;
import com.gtnewhorizons.galaxia.modules.BlockModuleShell;
import com.gtnewhorizons.galaxia.modules.TileEntityModuleController;

import cpw.mods.fml.common.registry.GameRegistry;

public class GalaxiaBlocks {

    public static Block moduleController;
    public static Block moduleShell;

    public static void registerBlocks() {
        moduleController = new BlockModuleController();
        GameRegistry.registerBlock(moduleController, "module_controller");
        moduleShell = new BlockModuleShell();
        GameRegistry.registerBlock(moduleShell, "module_shell");

        GameRegistry.registerTileEntity(TileEntityModuleController.class, "galaxia_module_controller");
    }

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
