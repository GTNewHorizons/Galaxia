package com.gtnewhorizons.galaxia.api;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.MICROGRAVITY;
import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

class RecipeEnvironmentsTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    /** Product contract: each declared condition must be true, with vacuum independent of gravity. */
    @Test
    void independentRequirementsAreConjunctiveAndUnknownConditionsFailClosed() {
        var both = RecipeEnvironments.Requirement.of(MICROGRAVITY, NO_ATMOSPHERE);
        assertTrue(both.matches(new RecipeEnvironments.Conditions(0, 0)));
        assertFalse(both.matches(new RecipeEnvironments.Conditions(1, 0)));
        assertFalse(both.matches(new RecipeEnvironments.Conditions(0, 101325)));
        assertFalse(both.matches(null));
        assertTrue(
            RecipeEnvironments.Requirement.of(NO_ATMOSPHERE)
                .matches(new RecipeEnvironments.Conditions(1, 0)));
        assertTrue(new RecipeEnvironments.Requirement(List.of()).matches(null));
        assertFalse(
            new RecipeEnvironments.Requirement(List.of("unknown:condition"))
                .matches(new RecipeEnvironments.Conditions(0, 0)));
    }

    /** Product contract: mutation of authors' lists and a native save/load cannot drop an active restriction. */
    @Test
    void requirementsOwnTheirTagsAndSurviveNbtIncludingUnknownTags() {
        var tags = new ArrayList<>(List.of("galaxia:no_atmosphere", "missing:tag"));
        var requirement = new RecipeEnvironments.Requirement(tags);
        tags.clear();
        NBTTagCompound nbt = new NBTTagCompound();
        requirement.writeToNBT(nbt);
        var restored = RecipeEnvironments.Requirement.readFromNBT(nbt);
        assertEquals(requirement, restored);
        assertFalse(restored.matches(new RecipeEnvironments.Conditions(0, 0)));
        assertTrue(
            RecipeEnvironments.Requirement.readFromNBT(new NBTTagCompound())
                .matches(null));
    }
}
