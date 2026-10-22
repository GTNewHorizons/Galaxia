package com.gtnewhorizons.galaxia.gt5;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.core.Galaxia;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.FluidState;
import gregtech.api.fluid.GTFluidFactory;

/** GT-bound orbital feedstocks and components, staged here until their GT5 transfer. */
public enum OrbitalItems {

    SIGE_BLEND("sige_blend"),
    POLY_SIGE("poly_sige"),
    SIGE_SEED("sige_seed"),
    SIGE_MONOCRYSTAL("sige_monocrystal"),
    SIGE_WAFER("sige_wafer"),
    CLC_WAFER("clc_wafer"),
    ETCHED_CLC_WAFER("etched_clc_wafer"),
    CLC_CHIP("clc_chip"),
    CLC_MODULE("clc_module"),
    FLUORIDE_GLASS_MIX("fluoride_glass_mix"),
    FLUORIDE_GLASS_BLANK("fluoride_glass_blank"),
    FLUORIDE_GLASS_PREFORM("fluoride_glass_preform"),
    HEATED_FLUORIDE_GLASS_PREFORM("heated_fluoride_glass_preform"),
    FLUORIDE_OPTICAL_FIBER("fluoride_optical_fiber"),
    FLUORIDE_FIBER_SPOOL("fluoride_fiber_spool"),
    SOLAR_CELL("solar_cell"),
    SOLAR_GLASS_SHEET("solar_glass_sheet"),
    SPACE_GRADE_SOLAR_PANEL("space_grade_solar_panel"),
    SATELLITE_FRAME("satellite_frame"),
    PRIMITIVE_TELESCOPE_SATELLITE("primitive_telescope_satellite"),
    COMMUNICATION_SATELLITE("communication_satellite"),
    PROSPECTING_SATELLITE("prospecting_satellite"),
    ROCKET_AUTOMATION_CONTROLLER("rocket_automation_controller");

    private final String registryName;
    private Item item;

    OrbitalItems(String registryName) {
        this.registryName = registryName;
    }

    public String registryName() {
        return registryName;
    }

    public ItemStack get(int amount) {
        if (item == null) throw new IllegalStateException("Orbital items have not been registered");
        return new ItemStack(item, amount);
    }

    public static void register() {
        for (OrbitalItems entry : values()) {
            if (entry.item != null) throw new IllegalStateException("Orbital items already registered");
            entry.item = new Item().setUnlocalizedName(Galaxia.UNLOCALIZED_PREFIX + entry.registryName)
                .setTextureName("minecraft:missingno")
                .setCreativeTab(Galaxia.creativeTab);
            GameRegistry.registerItem(entry.item, entry.registryName);
        }
        for (Melt melt : Melt.values()) {
            melt.fluid = GTFluidFactory.builder(melt.name)
                .withStillIconResourceLocation(new ResourceLocation("minecraft", "missingno"))
                .withStateAndTemperature(FluidState.MOLTEN, melt.temperatureK)
                .buildAndRegister()
                .asFluid();
        }
    }

    /** Explicit melts avoid generating a blend-melting bypass around powder treatment. */
    public enum Melt {

        SIGE("molten.galaxia.sige", 1784),
        FLUORIDE_GLASS("molten.galaxia.fluoride_glass", 1800);

        private final String name;
        private final int temperatureK;
        private Fluid fluid;

        Melt(String name, int temperatureK) {
            this.name = name;
            this.temperatureK = temperatureK;
        }

        public FluidStack get(int amount) {
            if (fluid == null) throw new IllegalStateException("Orbital fluids have not been registered");
            return new FluidStack(fluid, amount);
        }
    }
}
