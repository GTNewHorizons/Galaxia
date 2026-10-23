package com.gtnewhorizons.galaxia.core;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** GT must be transformed after its mod classes become available. */
@LateMixin
public class RecipeEnvironmentMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.galaxia.environment.late.json";
    }

    @Override
    @Nonnull
    public List<String> getMixins(Set<String> loadedMods) {
        return loadedMods.contains("gregtech") ? List.of(
            "BasicMachineEnvironmentMixin",
            "MultiblockEnvironmentMixin",
            "ProcessingEnvironmentMixin",
            "RecipeCopyEnvironmentMixin") : List.of();
    }
}
