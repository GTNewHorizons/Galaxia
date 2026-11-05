package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.CHASSIS;
import static com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.MANUFACTURING_RECIPES;
import static com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.REPAIR_RECIPES;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.Chassis;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/** Staged GT content for manufacturing and maintaining android bodies and their physical stations. */
public final class AndroidRecipes {

    private AndroidRecipes() {}

    public static void init() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 32),
                ItemList.Robot_Arm_EV.get(4),
                ItemList.Electric_Motor_EV.get(8),
                new Object[] { OrePrefixes.circuit.get(Materials.EV), 8 },
                ItemList.Sensor_EV.get(4),
                ItemList.BatteryHull_EV_Full.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1152))
            .metadata(CHASSIS, Chassis.EV)
            .duration(120 * SECONDS)
            .eut(1920)
            .addTo(MANUFACTURING_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.NaquadahAlloy, 48),
                ItemList.Robot_Arm_LuV.get(4),
                ItemList.Electric_Motor_LuV.get(8),
                new Object[] { OrePrefixes.circuit.get(Materials.LuV), 8 },
                ItemList.Sensor_LuV.get(4),
                ItemList.BatteryHull_LuV_Full.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(4608))
            .metadata(CHASSIS, Chassis.LUV)
            .duration(180 * SECONDS)
            .eut(30720)
            .addTo(MANUFACTURING_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Neutronium, 64),
                ItemList.Robot_Arm_UV.get(8),
                ItemList.Electric_Motor_UV.get(16),
                new Object[] { OrePrefixes.circuit.get(Materials.UV), 16 },
                ItemList.Sensor_UV.get(8),
                ItemList.BatteryHull_UV_Full.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(9216))
            .metadata(CHASSIS, Chassis.UV)
            .duration(240 * SECONDS)
            .eut(491520)
            .addTo(MANUFACTURING_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(72))
            .metadata(CHASSIS, Chassis.EV)
            .duration(100)
            .eut(240)
            .addTo(REPAIR_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(GTOreDictUnificator.get(OrePrefixes.plate, Materials.NaquadahAlloy, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(72))
            .metadata(CHASSIS, Chassis.LUV)
            .duration(100)
            .eut(3840)
            .addTo(REPAIR_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(GTOreDictUnificator.get(OrePrefixes.plate, Materials.Neutronium, 1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(72))
            .metadata(CHASSIS, Chassis.UV)
            .duration(100)
            .eut(61440)
            .addTo(REPAIR_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                ItemList.Robot_Arm_EV.get(2),
                new Object[] { OrePrefixes.circuit.get(Materials.EV), 2 },
                ItemList.Sensor_EV.get(1),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.Titanium, 2),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 4))
            .fluidInputs(Materials.SolderingAlloy.getMolten(576))
            .itemOutputs(new ItemStack(GalaxiaBlocksEnum.ANDROID_DOCK.get(), 1))
            .duration(1200)
            .eut(1920)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                new Object[] { OrePrefixes.circuit.get(Materials.EV), 2 },
                ItemList.Electric_Pump_EV.get(2),
                ItemList.Sensor_EV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 4),
                new ItemStack(GalaxiaBlocksEnum.SPACE_STATION_GLASS.get(), 2))
            .fluidInputs(Materials.SolderingAlloy.getMolten(576))
            .itemOutputs(new ItemStack(GalaxiaBlocksEnum.CONTROL_CAPSULE.get(), 1))
            .duration(1200)
            .eut(1920)
            .addTo(assemblerRecipes);
    }
}
