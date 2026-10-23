package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.CHASSIS;
import static com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.MANUFACTURING_RECIPES;
import static com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.REPAIR_RECIPES;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.Chassis;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.ruling_0.materiallib.api.MaterialLibAPI;

import gregtech.api.enums.Circuits;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.material.MaterialUtils;

/** Staged GT content for manufacturing and maintaining android bodies and their physical stations. */
public final class AndroidRecipes {

    private AndroidRecipes() {}

    public static void init() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(Materials.Titanium, Shapes.plate, 32),
                ItemList.Robot_Arm_EV.get(4),
                ItemList.Electric_Motor_EV.get(8),
                new Object[] { Circuits.EV.getIngredient(), 8 },
                ItemList.Sensor_EV.get(4),
                ItemList.BatteryHull_EV_Full.get(1))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 1152))
            .metadata(CHASSIS, Chassis.EV)
            .duration(120 * SECONDS)
            .eut(1920)
            .addTo(MANUFACTURING_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(Materials.NaquadahAlloy, Shapes.plate, 48),
                ItemList.Robot_Arm_LuV.get(4),
                ItemList.Electric_Motor_LuV.get(8),
                new Object[] { Circuits.LuV.getIngredient(), 8 },
                ItemList.Sensor_LuV.get(4),
                ItemList.BatteryHull_LuV_Full.get(1))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 4608))
            .metadata(CHASSIS, Chassis.LUV)
            .duration(180 * SECONDS)
            .eut(30720)
            .addTo(MANUFACTURING_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(
                MaterialLibAPI.getStack(Materials.Neutronium, Shapes.plate, 64),
                ItemList.Robot_Arm_UV.get(8),
                ItemList.Electric_Motor_UV.get(16),
                new Object[] { Circuits.UV.getIngredient(), 16 },
                ItemList.Sensor_UV.get(8),
                ItemList.BatteryHull_UV_Full.get(1))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 9216))
            .metadata(CHASSIS, Chassis.UV)
            .duration(240 * SECONDS)
            .eut(491520)
            .addTo(MANUFACTURING_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.Titanium, Shapes.plate, 1))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 72))
            .metadata(CHASSIS, Chassis.EV)
            .duration(100)
            .eut(240)
            .addTo(REPAIR_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.NaquadahAlloy, Shapes.plate, 1))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 72))
            .metadata(CHASSIS, Chassis.LUV)
            .duration(100)
            .eut(3840)
            .addTo(REPAIR_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(MaterialLibAPI.getStack(Materials.Neutronium, Shapes.plate, 1))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 72))
            .metadata(CHASSIS, Chassis.UV)
            .duration(100)
            .eut(61440)
            .addTo(REPAIR_RECIPES);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                ItemList.Robot_Arm_EV.get(2),
                new Object[] { Circuits.EV.getIngredient(), 2 },
                ItemList.Sensor_EV.get(1),
                MaterialLibAPI.getStack(Materials.Titanium, Shapes.gearGt, 2),
                MaterialLibAPI.getStack(Materials.Titanium, Shapes.plate, 4))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 576))
            .itemOutputs(new ItemStack(GalaxiaBlocksEnum.ANDROID_DOCK.get(), 1))
            .duration(1200)
            .eut(1920)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                new Object[] { Circuits.EV.getIngredient(), 2 },
                ItemList.Electric_Pump_EV.get(2),
                ItemList.Sensor_EV.get(1),
                MaterialLibAPI.getStack(Materials.Titanium, Shapes.plate, 4),
                new ItemStack(GalaxiaBlocksEnum.SPACE_STATION_GLASS.get(), 2))
            .fluidInputs(MaterialUtils.molten(Materials.SolderingAlloy, 576))
            .itemOutputs(new ItemStack(GalaxiaBlocksEnum.CONTROL_CAPSULE.get(), 1))
            .duration(1200)
            .eut(1920)
            .addTo(assemblerRecipes);
    }
}
