package com.gtnewhorizons.galaxia.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * Mixins into other mods, only queued when the mod they change is loaded
 */
@LateMixin
public class GalaxiaLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.galaxia.late.json";
    }

    @Override
    public @Nonnull List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("gregtech")) {
            mixins.add("gregtech.CraterProspectorMixin");
            mixins.add("gregtech.ElectricCraterProspectorMixin");
            mixins.add("gregtech.MTEMultiBlockBaseOrbitMixin");
            mixins.add("gregtech.MTEHatchEnergyStationMixin");
            mixins.add("gregtech.MTEHatchDynamoStationMixin");
        }
        if (loadedMods.contains("appliedenergistics2")) {
            mixins.add("ae2.PlayerDataAccessorMixin");
            mixins.add("ae2.QuantumClusterMixin");
        }
        return mixins;
    }
}
