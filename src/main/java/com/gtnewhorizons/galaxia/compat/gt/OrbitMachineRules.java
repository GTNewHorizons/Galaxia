package com.gtnewhorizons.galaxia.compat.gt;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

/**
 * What GT machines may do in orbit. Kept out of the mixin so the rules stay readable and testable
 */
public final class OrbitMachineRules {

    /** Shown in the multiblock's GUI, the text is GT5U.gui.text.recipe_result.galaxia_no_exhaust_in_orbit */
    public static final CheckRecipeResult NO_EXHAUST_IN_ORBIT = SimpleCheckRecipeResult
        .ofFailure("galaxia_no_exhaust_in_orbit");

    private OrbitMachineRules() {}

    /**
     * Blocks polluting multiblocks in orbit unless an EBF can vent into vacuum.
     * The restriction applies independently of GT's pollution configuration.
     */
    public static boolean isExhaustBlocked(MTEMultiBlockBase machine) {
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return false;
        if (!OrbitDimensions.isOrbit(base.getWorld().provider.dimensionId)) return false;
        if (ventsWithoutPollution(machine)) return false;
        ItemStack controllerSlot = machine.mInventory != null && machine.mInventory.length > 1 ? machine.mInventory[1]
            : null;
        return machine.getPollutionPerSecond(controllerSlot) > 0;
    }

    /** Whether the EBF's current environment disperses its exhaust without pollution. */
    public static boolean ventsWithoutPollution(MetaTileEntity machine) {
        return machine instanceof MTEElectricBlastFurnace && RecipeEnvironments.NO_ATMOSPHERE.isSatisfied(machine);
    }
}
