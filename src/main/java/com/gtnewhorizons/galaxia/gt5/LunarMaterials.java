package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.ruling_0.materiallib.api.Material;
import com.ruling_0.materiallib.api.MaterialLibAPI;
import com.ruling_0.materiallib.api.MaterialRegistrationEvent;
import com.ruling_0.materiallib.api.StandardProperties;
import com.ruling_0.materiallib.api.TextureSet;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.enums.FluidState;
import gregtech.api.enums.materials.Families;
import gregtech.api.enums.materials.FluidShapes;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.enums.materials.TEBlockShapes;
import gregtech.api.fluid.GTFluidFactory;
import gregtech.api.material.GTMaterialFlag;
import gregtech.api.material.GTMaterialProperties;
import gregtech.api.recipe.RecipeEnvironmentRequirements;

/** GT-bound lunar intermediates. Extraction mixtures have no chemical formula. */
public final class LunarMaterials {

    public static Material scandiumConcentrate;
    public static Material scandiumOxide;
    public static Material spaceGradeAlloy;
    public static Fluid spentCarrier;
    public static Fluid scandiumSolution;
    public static Fluid spentLeachSolution;
    private static final Map<CraterProfile, Fluid> ENRICHED = new EnumMap<>(CraterProfile.class);

    private LunarMaterials() {}

    public static void registerEventHandler() {
        MinecraftForge.EVENT_BUS.register(new LunarMaterials());
    }

    public static void preInit() {
        for (CraterProfile profile : CraterProfile.values()) {
            ENRICHED.put(profile, fluid("enriched_" + profile.id, "Enriched " + profile.id.replace('_', ' ')));
        }
        spentCarrier = fluid("spent_crater_carrier", "Spent Crater Carrier");
        scandiumSolution = fluid("scandium_solution", "Scandium Solution");
        spentLeachSolution = fluid("spent_scandium_leach_solution", "Spent Scandium Leach Solution");
    }

    public static Fluid enriched(CraterProfile profile) {
        return ENRICHED.get(profile);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void registerMaterials(MaterialRegistrationEvent event) {
        // GT declares the shared shapes at NORMAL priority before these materials use them.
        scandiumConcentrate = MaterialLibAPI
            .newMaterial("galaxia", "ScandiumConcentrate", TextureSet.of("gregtech", "DULL"))
            .addToFamily(Families.all)
            .generateShapes(Shapes.dust)
            .setProperty(StandardProperties.DISPLAY_NAME_KEY, "galaxia.material.scandium_concentrate.name")
            .setProperty(GTMaterialProperties.FLAGS, EnumSet.of(GTMaterialFlag.NO_RECIPES))
            .build();
        // Keep oxide decomposition in the explicitly authored refinery recipes.
        scandiumOxide = MaterialLibAPI.newMaterial("galaxia", "ScandiumOxide", TextureSet.of("gregtech", "DULL"))
            .addToFamily(Families.all)
            .generateShapes(Shapes.dust)
            .setProperty(StandardProperties.DISPLAY_NAME_KEY, "galaxia.material.scandium_oxide.name")
            .setProperty(GTMaterialProperties.FORMULA, "Sc2O3")
            .setProperty(GTMaterialProperties.FLAGS, EnumSet.of(GTMaterialFlag.NO_RECIPES))
            .build();
        spaceGradeAlloy = MaterialLibAPI
            .newMaterial("galaxia", "SpaceGradeAlloy", TextureSet.of("gregtech", "METALLIC"))
            .addToFamily(Families.all)
            .generateShapes(
                Shapes.ingot,
                Shapes.plate,
                Shapes.stick,
                Shapes.foil,
                TEBlockShapes.frameGt,
                FluidShapes.fluidMolten)
            .setTint(0xffb8cad8)
            .setProperty(StandardProperties.DISPLAY_NAME_KEY, "galaxia.material.space_grade_alloy.name")
            .setProperty(GTMaterialProperties.FLAGS, EnumSet.of(GTMaterialFlag.NO_RECIPES, GTMaterialFlag.NO_RECYCLING))
            .setProperty(GTMaterialProperties.AUTO_RECYCLE_RECIPES, false)
            .setProperty(
                GTMaterialProperties.MOLTEN_PROCESSING_ENVIRONMENTS,
                RecipeEnvironmentRequirements.of(NO_ATMOSPHERE))
            .build();
    }

    private static Fluid fluid(String id, String name) {
        String registeredName = "galaxia." + id;
        if (FluidRegistry.getFluid(registeredName) != null) {
            throw new IllegalStateException("Lunar fluid already registered: " + registeredName);
        }
        return GTFluidFactory.of(registeredName, name, FluidState.LIQUID, 295);
    }
}
