package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

public sealed interface RecipeBookOwner permits RecipeBookOwner.Private,RecipeBookOwner.Group {

    record Private(ModuleInstance.ID moduleId) implements RecipeBookOwner {

        public Private {
            Objects.requireNonNull(moduleId, "moduleId");
        }
    }

    record Group(SettingsGroup.ID groupId) implements RecipeBookOwner {

        public Group {
            Objects.requireNonNull(groupId, "groupId");
        }
    }
}
