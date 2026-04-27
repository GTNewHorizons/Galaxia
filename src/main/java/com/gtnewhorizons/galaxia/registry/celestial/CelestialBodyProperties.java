package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.compat.GTUtility;

public record CelestialBodyProperties(boolean visitable, boolean canCreateStation, boolean canCreateOutpost,
    double standardGravitationalParameter, double sphereOfInfluenceRadius, double parkingOrbitRadius, String oreProfile,
    List<ItemStack> ores, List<String> gtOreVeinOres, double radiation, double temperature,
    Map<String, String> metadata) {

    public CelestialBodyProperties {
        if (oreProfile == null) oreProfile = "";
        if (metadata == null) metadata = Collections.emptyMap();
        else metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (ores == null) ores = List.of();
        else ores = copyOres(ores);
        if (gtOreVeinOres == null) gtOreVeinOres = List.of();
        else gtOreVeinOres = Collections.unmodifiableList(new ArrayList<>(gtOreVeinOres));
    }

    private static List<ItemStack> copyOres(List<ItemStack> ores) {
        if (ores.isEmpty()) return List.of();
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack ore : ores) {
            if (ore == null) continue;
            ItemStack copy = ore.copy();
            copy.stackSize = 1;
            copies.add(copy);
        }
        return Collections.unmodifiableList(copies);
    }

    public boolean hasGtOreVeinOres() {
        return !gtOreVeinOres.isEmpty();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean visitable;
        private boolean canCreateStation;
        private boolean canCreateOutpost;
        private double standardGravitationalParameter;
        private double sphereOfInfluenceRadius;
        private double parkingOrbitRadius;
        private String oreProfile = "";
        private final List<ItemStack> resolvedOres = new ArrayList<>();
        private final List<String> resolvedGtOreVeinOres = new ArrayList<>();
        private double radiation;
        private double temperature;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder() {}

        public Builder(CelestialBodyProperties source) {
            if (source == null) return;
            this.visitable = source.visitable;
            this.canCreateStation = source.canCreateStation;
            this.canCreateOutpost = source.canCreateOutpost;
            this.standardGravitationalParameter = source.standardGravitationalParameter;
            this.sphereOfInfluenceRadius = source.sphereOfInfluenceRadius;
            this.parkingOrbitRadius = source.parkingOrbitRadius;
            this.oreProfile = source.oreProfile;
            this.resolvedOres.addAll(source.ores);
            this.resolvedGtOreVeinOres.addAll(source.gtOreVeinOres);
            this.radiation = source.radiation;
            this.temperature = source.temperature;
            this.metadata.putAll(source.metadata);
        }

        public Builder visitable(boolean value) {
            this.visitable = value;
            return this;
        }

        public Builder canCreateStation(boolean value) {
            this.canCreateStation = value;
            return this;
        }

        public Builder canCreateOutpost(boolean value) {
            this.canCreateOutpost = value;
            return this;
        }

        public Builder standardGravitationalParameter(double value) {
            this.standardGravitationalParameter = Math.max(0.0, value);
            return this;
        }

        public Builder sphereOfInfluenceRadius(double value) {
            this.sphereOfInfluenceRadius = Math.max(0.0, value);
            return this;
        }

        public Builder parkingOrbitRadius(double value) {
            this.parkingOrbitRadius = Math.max(0.0, value);
            return this;
        }

        public Builder oreProfile(String value) {
            this.oreProfile = value == null ? "" : value;
            return this;
        }

        public Builder ore(@Nonnull ItemStack value) {
            ItemStack copy = value.copy();
            copy.stackSize = 1;
            resolvedOres.add(copy);

            return this;
        }

        public Builder ores(@Nonnull ItemStack... values) {
            for (ItemStack value : values) ore(value);
            return this;
        }

        public Builder ores(@Nonnull Block... ores) {
            for (Block ore : ores) ore(new ItemStack(ore));
            return this;
        }

        public Builder gtOreVeinIds(@Nonnull String... veinIds) {
            ores(
                GTUtility.getRawOres(veinIds)
                    .toArray(new ItemStack[0]));
            return this;
        }

        public Builder radiation(double value) {
            this.radiation = value;
            return this;
        }

        public Builder temperature(double value) {
            this.temperature = value;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder clearMetadata() {
            this.metadata.clear();
            return this;
        }

        public Builder withGravity(double standardGravitationalParameter, double sphereOfInfluenceRadius) {
            return standardGravitationalParameter(standardGravitationalParameter)
                .sphereOfInfluenceRadius(sphereOfInfluenceRadius);
        }

        public CelestialBodyProperties build() {
            return new CelestialBodyProperties(
                visitable,
                canCreateStation,
                canCreateOutpost,
                standardGravitationalParameter,
                sphereOfInfluenceRadius,
                parkingOrbitRadius,
                oreProfile,
                resolvedOres,
                resolvedGtOreVeinOres,
                radiation,
                temperature,
                metadata);
        }
    }
}
