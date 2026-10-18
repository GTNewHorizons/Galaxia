package com.gtnewhorizons.galaxia.gt5;

import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Engine;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Propellant;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Storage;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.WorkBill;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gtPlusPlus.core.fluids.GTPPFluids;

/** GT-bound content staged here until its recipes and items move to their pack owner. */
public final class RocketContent {

    public static final Item SATELLITE_FRAME = new Item();
    public static final Item PRIMITIVE_TELESCOPE = new Item();

    private RocketContent() {}

    public static void registerItems() {
        register(SATELLITE_FRAME, "satellite_frame", "parts/heavy_duty_plate_base");
        register(PRIMITIVE_TELESCOPE, "primitive_telescope_satellite", "parts/satellite_dish");
    }

    private static void register(Item item, String name, String texture) {
        item.setUnlocalizedName("galaxia." + name);
        item.setTextureName(Galaxia.TEXTURE_PREFIX + texture);
        item.setCreativeTab(Galaxia.creativeTab);
        GameRegistry.registerItem(item, name);
    }

    public static void registerRecipesAndDefinitions() {
        registerPayloadRecipes();
        registerTransportInfrastructure();
        var config = ConfigRocket.basic;
        var registry = RocketPartRegistry.instance();
        var fuel = new FluidKey(GTPPFluids.Kerosene, null);
        var oxidizer = FluidKey.of(Materials.LiquidOxygen.getGas(1));
        var tin = FluidKey.of(Materials.Tin.getMolten(1));

        registry.replace(
            RocketPartRegistry.CAPSULE_T1.withManufactureBill(
                new WorkBill(
                    Map.of(
                        ore(OrePrefixes.plate, Materials.Aluminium),
                        16L,
                        ItemStackWrapper.of(new ItemStack(Blocks.glass)),
                        4L,
                        ore(OrePrefixes.circuit, Materials.HV),
                        2L,
                        item(ItemList.Emitter_MV),
                        1L,
                        item(ItemList.Sensor_MV),
                        1L,
                        ore(OrePrefixes.wireFine, Materials.Copper),
                        8L,
                        tin,
                        288L),
                    config.manufactureEuPerTick,
                    config.capsuleManufactureTicks)));
        registry.replace(
            RocketPartRegistry.FUEL_TANK_T1.withStorage(
                new Storage(0, 0, Map.of(fuel, (long) config.fuelCapacity, oxidizer, (long) config.oxidizerCapacity)))
                .withManufactureBill(
                    new WorkBill(
                        Map.of(
                            ore(OrePrefixes.plate, Materials.Aluminium),
                            24L,
                            ore(OrePrefixes.ring, Materials.Aluminium),
                            4L,
                            ore(OrePrefixes.pipeSmall, Materials.StainlessSteel),
                            4L,
                            item(ItemList.Electric_Pump_MV),
                            2L,
                            tin,
                            288L),
                        config.manufactureEuPerTick,
                        config.tankManufactureTicks)));
        registry.replace(
            RocketPartRegistry.ENGINE_T1
                .withEngine(
                    new Engine(
                        config.thrustNewtons,
                        config.exhaustVelocityMps,
                        Map.of(
                            fuel,
                            new Propellant(config.fuelVolumeParts, config.fuelKilogramsPerUnit),
                            oxidizer,
                            new Propellant(config.oxidizerVolumeParts, config.oxidizerKilogramsPerUnit))))
                .withManufactureBill(
                    new WorkBill(
                        Map.of(
                            ore(OrePrefixes.plate, Materials.StainlessSteel),
                            8L,
                            ore(OrePrefixes.pipeMedium, Materials.Copper),
                            4L,
                            item(ItemList.Electric_Pump_HV),
                            2L,
                            item(ItemList.Electric_Motor_HV),
                            1L,
                            ore(OrePrefixes.stick, Materials.Steel),
                            4L,
                            tin,
                            288L),
                        config.manufactureEuPerTick,
                        config.engineManufactureTicks)));
        registry.replace(
            RocketPartRegistry.STORAGE_T1.withManufactureBill(
                new WorkBill(
                    Map.of(
                        ore(OrePrefixes.plate, Materials.Aluminium),
                        12L,
                        ore(OrePrefixes.stick, Materials.Steel),
                        4L,
                        item(ItemList.Conveyor_Module_MV),
                        1L,
                        item(ItemList.Electric_Piston_MV),
                        1L,
                        tin,
                        144L),
                    config.manufactureEuPerTick,
                    config.cargoManufactureTicks)));
        registry.replace(
            RocketPartRegistry.FLUID_STORAGE_T1.withManufactureBill(
                new WorkBill(
                    Map.of(
                        ore(OrePrefixes.plate, Materials.Aluminium),
                        24L,
                        ore(OrePrefixes.ring, Materials.Aluminium),
                        4L,
                        ore(OrePrefixes.pipeSmall, Materials.StainlessSteel),
                        4L,
                        item(ItemList.Electric_Pump_MV),
                        2L,
                        tin,
                        288L),
                    config.manufactureEuPerTick,
                    config.tankManufactureTicks)));
        var aluminiumRepair = new WorkBill(
            Map.of(ore(OrePrefixes.plate, Materials.Aluminium), (long) config.repairMaterialAmount),
            config.repairEuPerTick,
            config.repairTicks);
        registry.replace(RocketPartRegistry.CAPSULE_T1.withRepairBill(aluminiumRepair));
        registry.replace(RocketPartRegistry.FUEL_TANK_T1.withRepairBill(aluminiumRepair));
        registry.replace(RocketPartRegistry.STORAGE_T1.withRepairBill(aluminiumRepair));
        registry.replace(RocketPartRegistry.FLUID_STORAGE_T1.withRepairBill(aluminiumRepair));
        registry.replace(
            RocketPartRegistry.ENGINE_T1.withRepairBill(
                new WorkBill(
                    Map.of(ore(OrePrefixes.plate, Materials.StainlessSteel), (long) config.repairMaterialAmount),
                    config.repairEuPerTick,
                    config.repairTicks)));
    }

    private static InventoryKey item(ItemList component) {
        return required(component.get(1));
    }

    private static void registerTransportInfrastructure() {
        FacilityModuleRegistry.registerLandingBay(
            Map.of(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 1),
                32L,
                ItemList.Conveyor_Module_HV.get(1),
                4L,
                ItemList.Electric_Pump_HV.get(1),
                4L,
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 1),
                4L,
                ItemList.Robot_Arm_HV.get(1),
                2L));
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 8),
                ItemList.Conveyor_Module_HV.get(1),
                ItemList.Electric_Pump_HV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2))
            .itemOutputs(new ItemStack(GalaxiaBlocksEnum.ROCKET_LOADING_PORT.get()))
            .duration(600)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
        infrastructureRecipe(
            GalaxiaBlocksEnum.SILO_CONTROLLER,
            1,
            ItemList.Hull_HV.get(1),
            GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2),
            ItemList.Robot_Arm_HV.get(2),
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4));
        infrastructureRecipe(
            GalaxiaBlocksEnum.LANDING_PAD_CONTROLLER,
            1,
            ItemList.Hull_HV.get(1),
            GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2),
            ItemList.Conveyor_Module_HV.get(2),
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 4));
        infrastructureRecipe(
            GalaxiaBlocksEnum.DOCK_CONTROLLER,
            1,
            ItemList.Hull_HV.get(1),
            GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2),
            ItemList.Electric_Pump_HV.get(2),
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4));
        infrastructureRecipe(
            GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER,
            1,
            ItemList.Hull_HV.get(1),
            GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 4),
            ItemList.Robot_Arm_HV.get(2),
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.StainlessSteel, 8));
        infrastructureRecipe(
            GalaxiaBlocksEnum.GANTRY,
            8,
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 8),
            ItemList.Electric_Motor_HV.get(1),
            ItemList.Conveyor_Module_HV.get(1));
        infrastructureRecipe(
            GalaxiaBlocksEnum.GANTRY_TERMINAL,
            1,
            ItemList.Hull_HV.get(1),
            GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2),
            ItemList.Sensor_MV.get(1));
        infrastructureRecipe(
            GalaxiaBlocksEnum.LAUNCHPAD_FRAMEBOX,
            4,
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 4),
            GTOreDictUnificator.get(OrePrefixes.stick, Materials.Steel, 4));
        infrastructureRecipe(
            GalaxiaBlocksEnum.LAUNCHPAD_REINFORCEMENT,
            4,
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 8),
            GTOreDictUnificator.get(OrePrefixes.ring, Materials.Steel, 4));
        infrastructureRecipe(
            GalaxiaBlocksEnum.LAUNCHPAD_ASSEMBLING_CASING,
            4,
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 4),
            ItemList.Conveyor_Module_HV.get(1),
            ItemList.Electric_Motor_HV.get(1));
        infrastructureRecipe(
            GalaxiaBlocksEnum.LAUNCHPAD_SHEETING,
            4,
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 2));
        infrastructureRecipe(
            GalaxiaBlocksEnum.LAUNCHPAD_CASING,
            4,
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 4));
    }

    private static void infrastructureRecipe(GalaxiaBlocksEnum block, int amount, ItemStack... inputs) {
        GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(new ItemStack(block.get(), amount))
            .duration(600)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    private static InventoryKey ore(OrePrefixes prefix, Materials material) {
        return required(GTOreDictUnificator.get(prefix, material, 1));
    }

    private static ItemStackWrapper required(ItemStack stack) {
        if (stack == null || stack.getItem() == null)
            throw new IllegalStateException("Missing GT Rocket recipe component");
        return ItemStackWrapper.of(stack);
    }

    private static void registerPayloadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4),
                GTOreDictUnificator.get(OrePrefixes.stick, Materials.Steel, 2),
                GTOreDictUnificator.get(OrePrefixes.ring, Materials.Aluminium, 2))
            .itemOutputs(new ItemStack(SATELLITE_FRAME))
            .duration(600)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4),
                GTOreDictUnificator.get(OrePrefixes.wireFine, Materials.Copper, 4),
                ItemList.Emitter_MV.get(1))
            .fluidInputs(Materials.Tin.getMolten(144))
            .itemOutputs(new ItemStack(GalaxiaItemList.SATELLITE_DISH.getItem()))
            .duration(400)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(SATELLITE_FRAME),
                new ItemStack(GalaxiaItemList.SATELLITE_DISH.getItem()),
                GTOreDictUnificator.get(OrePrefixes.lens, Materials.Glass, 4),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 1),
                ItemList.Sensor_MV.get(1),
                ItemList.Battery_RE_HV_Lithium.get(1),
                ItemList.Cover_SolarPanel.get(1))
            .fluidInputs(Materials.Tin.getMolten(144))
            .itemOutputs(new ItemStack(PRIMITIVE_TELESCOPE))
            .duration(900)
            .eut(480)
            .addTo(RecipeMaps.assemblerRecipes);
    }
}
