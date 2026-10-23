package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;

import java.util.EnumSet;

import net.minecraftforge.common.MinecraftForge;

import com.ruling_0.materiallib.api.Material;
import com.ruling_0.materiallib.api.MaterialLibAPI;
import com.ruling_0.materiallib.api.MaterialRegistrationEvent;
import com.ruling_0.materiallib.api.StandardProperties;
import com.ruling_0.materiallib.api.TextureSet;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.enums.materials.Families;
import gregtech.api.enums.materials.FluidShapes;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.enums.materials.TEBlockShapes;
import gregtech.api.material.GTMaterialFlag;
import gregtech.api.material.GTMaterialProperties;
import gregtech.api.recipe.RecipeEnvironmentRequirements;

/** Canonical Space Grade Alloy forms shared by lunar and orbital manufacturing. */
public final class LunarMaterials {

    public static Material spaceGradeAlloy;

    private LunarMaterials() {}

    public static void registerEventHandler() {
        MinecraftForge.EVENT_BUS.register(new LunarMaterials());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void registerMaterials(MaterialRegistrationEvent event) {
        // GT declares the shared shapes at NORMAL priority before this material uses them.
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

}
