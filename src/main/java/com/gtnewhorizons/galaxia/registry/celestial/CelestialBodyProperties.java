package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialBodyProperties(CelestialConstructionProfile construction, CelestialGravityProfile gravity,
    String oreProfile, CelestialResourceSet resources, CelestialEnvironmentProfile environment,
    Map<String, String> metadata) {

    public CelestialBodyProperties {
        construction = construction == null ? CelestialConstructionProfile.NONE : construction;
        gravity = gravity == null ? CelestialGravityProfile.NONE : gravity;
        oreProfile = oreProfile == null ? "" : oreProfile;
        resources = resources == null ? CelestialResourceSet.builder()
            .build() : resources;
        environment = environment == null ? CelestialEnvironmentProfile.NONE : environment;
        metadata = metadata == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public boolean visitable() {
        return construction.visitable();
    }

    public boolean canCreateStation() {
        return construction.canCreateStation();
    }

    public boolean canCreateOutpost() {
        return construction.canCreateOutpost();
    }

    public double standardGravitationalParameter() {
        return gravity.standardGravitationalParameter();
    }

    public double sphereOfInfluenceRadius() {
        return gravity.sphereOfInfluenceRadius();
    }

    public double parkingOrbitRadius() {
        return gravity.parkingOrbitRadius();
    }

    public double radiation() {
        return environment.radiation();
    }

    public double temperature() {
        return environment.temperature();
    }

    public List<ItemStack> ores() {
        return resources.vanillaOres();
    }

    public List<String> gtOreVeinIds() {
        return resources.gtOreVeinIds();
    }

    public List<GtOreVeinDefinition> gtOreVeins() {
        return resources.activeGtOreVeins();
    }

    public boolean usesGtOreVeins() {
        return resources.usesGtOreVeins();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private CelestialConstructionProfile.Builder construction = CelestialConstructionProfile.builder();
        private CelestialGravityProfile.Builder gravity = CelestialGravityProfile.builder();
        private String oreProfile = "";
        private CelestialResourceSet.Builder resources = CelestialResourceSet.builder();
        private CelestialEnvironmentProfile.Builder environment = CelestialEnvironmentProfile.builder();
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder() {}

        public Builder(CelestialBodyProperties source) {
            if (source == null) return;
            this.construction = source.construction()
                .toBuilder();
            this.gravity = source.gravity()
                .toBuilder();
            this.oreProfile = source.oreProfile();
            this.resources = source.resources()
                .toBuilder();
            this.environment = source.environment()
                .toBuilder();
            this.metadata.putAll(source.metadata());
        }

        public Builder construction(CelestialConstructionProfile value) {
            this.construction = value == null ? CelestialConstructionProfile.builder() : value.toBuilder();
            return this;
        }

        public Builder construction(Consumer<CelestialConstructionProfile.Builder> mutator) {
            mutator.accept(this.construction);
            return this;
        }

        public Builder visitable(boolean value) {
            this.construction.visitable(value);
            return this;
        }

        public Builder canCreateStation(boolean value) {
            this.construction.canCreateStation(value);
            return this;
        }

        public Builder canCreateOutpost(boolean value) {
            this.construction.canCreateOutpost(value);
            return this;
        }

        public Builder gravity(CelestialGravityProfile value) {
            this.gravity = value == null ? CelestialGravityProfile.builder() : value.toBuilder();
            return this;
        }

        public Builder gravity(Consumer<CelestialGravityProfile.Builder> mutator) {
            mutator.accept(this.gravity);
            return this;
        }

        public Builder standardGravitationalParameter(double value) {
            this.gravity.standardGravitationalParameter(value);
            return this;
        }

        public Builder sphereOfInfluenceRadius(double value) {
            this.gravity.sphereOfInfluenceRadius(value);
            return this;
        }

        public Builder parkingOrbitRadius(double value) {
            this.gravity.parkingOrbitRadius(value);
            return this;
        }

        public Builder oreProfile(String value) {
            this.oreProfile = value == null ? "" : value;
            return this;
        }

        public Builder resources(CelestialResourceSet value) {
            this.resources = value == null ? CelestialResourceSet.builder() : value.toBuilder();
            return this;
        }

        public Builder resources(Consumer<CelestialResourceSet.Builder> mutator) {
            mutator.accept(this.resources);
            return this;
        }

        public Builder ore(ItemStack value) {
            this.resources.vanillaOre(value);
            return this;
        }

        public Builder ores(ItemStack... values) {
            this.resources.vanillaOres(values);
            return this;
        }

        public Builder gtOreVeinId(String veinId) {
            this.resources.gtOreVeinId(veinId);
            return this;
        }

        public Builder gtOreVeinIds(String... veinIds) {
            this.resources.gtOreVeinIds(veinIds);
            return this;
        }

        public Builder gtOreVein(GtOreVeinDefinition vein) {
            this.resources.gtOreVein(vein);
            return this;
        }

        public Builder gtOreVeins(GtOreVeinDefinition... veins) {
            this.resources.gtOreVeins(veins);
            return this;
        }

        public Builder environment(CelestialEnvironmentProfile value) {
            this.environment = value == null ? CelestialEnvironmentProfile.builder() : value.toBuilder();
            return this;
        }

        public Builder environment(Consumer<CelestialEnvironmentProfile.Builder> mutator) {
            mutator.accept(this.environment);
            return this;
        }

        public Builder radiation(double value) {
            this.environment.radiation(value);
            return this;
        }

        public Builder temperature(double value) {
            this.environment.temperature(value);
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

        public CelestialBodyProperties build() {
            return new CelestialBodyProperties(
                construction.build(),
                gravity.build(),
                oreProfile,
                resources.build(),
                environment.build(),
                metadata);
        }
    }
}

@Desugar
record CelestialGravityProfile(double standardGravitationalParameter, double sphereOfInfluenceRadius,
    double parkingOrbitRadius) {

    static final CelestialGravityProfile NONE = new CelestialGravityProfile(0.0, 0.0, 0.0);

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private double standardGravitationalParameter;
        private double sphereOfInfluenceRadius;
        private double parkingOrbitRadius;

        Builder() {}

        Builder(CelestialGravityProfile source) {
            if (source == null) return;
            this.standardGravitationalParameter = source.standardGravitationalParameter();
            this.sphereOfInfluenceRadius = source.sphereOfInfluenceRadius();
            this.parkingOrbitRadius = source.parkingOrbitRadius();
        }

        Builder standardGravitationalParameter(double value) {
            this.standardGravitationalParameter = Math.max(0.0, value);
            return this;
        }

        Builder sphereOfInfluenceRadius(double value) {
            this.sphereOfInfluenceRadius = Math.max(0.0, value);
            return this;
        }

        Builder parkingOrbitRadius(double value) {
            this.parkingOrbitRadius = Math.max(0.0, value);
            return this;
        }

        CelestialGravityProfile build() {
            return new CelestialGravityProfile(
                standardGravitationalParameter,
                sphereOfInfluenceRadius,
                parkingOrbitRadius);
        }
    }
}

@Desugar
record CelestialConstructionProfile(boolean visitable, boolean canCreateStation, boolean canCreateOutpost) {

    static final CelestialConstructionProfile NONE = new CelestialConstructionProfile(false, false, false);

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private boolean visitable;
        private boolean canCreateStation;
        private boolean canCreateOutpost;

        Builder() {}

        Builder(CelestialConstructionProfile source) {
            if (source == null) return;
            this.visitable = source.visitable();
            this.canCreateStation = source.canCreateStation();
            this.canCreateOutpost = source.canCreateOutpost();
        }

        Builder visitable(boolean value) {
            this.visitable = value;
            return this;
        }

        Builder canCreateStation(boolean value) {
            this.canCreateStation = value;
            return this;
        }

        Builder canCreateOutpost(boolean value) {
            this.canCreateOutpost = value;
            return this;
        }

        CelestialConstructionProfile build() {
            return new CelestialConstructionProfile(visitable, canCreateStation, canCreateOutpost);
        }
    }
}

@Desugar
record CelestialEnvironmentProfile(double radiation, double temperature) {

    static final CelestialEnvironmentProfile NONE = new CelestialEnvironmentProfile(0.0, 0.0);

    Builder toBuilder() {
        return new Builder(this);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private double radiation;
        private double temperature;

        Builder() {}

        Builder(CelestialEnvironmentProfile source) {
            if (source == null) return;
            this.radiation = source.radiation();
            this.temperature = source.temperature();
        }

        Builder radiation(double value) {
            this.radiation = value;
            return this;
        }

        Builder temperature(double value) {
            this.temperature = value;
            return this;
        }

        CelestialEnvironmentProfile build() {
            return new CelestialEnvironmentProfile(radiation, temperature);
        }
    }
}

@Desugar
record CelestialResourceSet(List<ItemStack> vanillaOres, List<String> gtOreVeinIds) {

    CelestialResourceSet {
        vanillaOres = copyVanillaOres(vanillaOres);
        gtOreVeinIds = gtOreVeinIds == null || gtOreVeinIds.isEmpty() ? Collections.emptyList()
            : Collections.unmodifiableList(new java.util.ArrayList<>(gtOreVeinIds));
    }

    List<GtOreVeinDefinition> activeGtOreVeins() {
        if (!com.gtnewhorizons.galaxia.core.Galaxia.hasGT5U()) return Collections.emptyList();
        return GtOreVeinCatalog.resolveAll(gtOreVeinIds);
    }

    boolean usesGtOreVeins() {
        return !activeGtOreVeins().isEmpty();
    }

    static Builder builder() {
        return new Builder();
    }

    Builder toBuilder() {
        return new Builder(this);
    }

    private static List<ItemStack> copyVanillaOres(List<ItemStack> ores) {
        if (ores == null || ores.isEmpty()) return Collections.emptyList();
        List<ItemStack> copies = new java.util.ArrayList<>();
        for (ItemStack ore : ores) {
            if (ore == null) continue;
            ItemStack copy = ore.copy();
            copy.stackSize = 1;
            copies.add(copy);
        }
        return Collections.unmodifiableList(copies);
    }

    static final class Builder {

        private final List<ItemStack> vanillaOres = new java.util.ArrayList<>();
        private final List<String> gtOreVeinIds = new java.util.ArrayList<>();

        Builder() {}

        Builder(CelestialResourceSet source) {
            if (source == null) return;
            this.vanillaOres.addAll(copyVanillaOres(source.vanillaOres()));
            this.gtOreVeinIds.addAll(source.gtOreVeinIds());
        }

        Builder vanillaOre(ItemStack value) {
            if (value != null) {
                ItemStack copy = value.copy();
                copy.stackSize = 1;
                vanillaOres.add(copy);
            }
            return this;
        }

        Builder vanillaOres(ItemStack... values) {
            if (values == null) return this;
            for (ItemStack value : values) vanillaOre(value);
            return this;
        }

        Builder gtOreVeinId(String veinId) {
            if (veinId != null && !veinId.isEmpty()) gtOreVeinIds.add(veinId);
            return this;
        }

        Builder gtOreVeinIds(String... veinIds) {
            if (veinIds == null) return this;
            for (String veinId : veinIds) gtOreVeinId(veinId);
            return this;
        }

        Builder gtOreVein(GtOreVeinDefinition vein) {
            if (vein != null) gtOreVeinId(vein.id());
            return this;
        }

        Builder gtOreVeins(GtOreVeinDefinition... veins) {
            if (veins == null) return this;
            for (GtOreVeinDefinition vein : veins) gtOreVein(vein);
            return this;
        }

        CelestialResourceSet build() {
            return new CelestialResourceSet(vanillaOres, gtOreVeinIds);
        }
    }
}
