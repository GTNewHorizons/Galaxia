package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.feature.types.MagmaPoolFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.MineralVeinFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.RareCrystalFormationFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.RegolithFlatsFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.StableBedrockFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.SubsurfaceIcePocketFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.ThermalSinkZoneFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.types.VolatileDepositFeature;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class PlanetaryFeatureRegistry {

    private static final PlanetaryFeature REGOLITH_FLATS_FEATURE = new RegolithFlatsFeature();
    private static final PlanetaryFeature STABLE_BEDROCK_FEATURE = new StableBedrockFeature();
    private static final PlanetaryFeature MINERAL_VEIN_FEATURE = new MineralVeinFeature();
    private static final PlanetaryFeature SUBSURFACE_ICE_POCKET_FEATURE = new SubsurfaceIcePocketFeature();
    private static final PlanetaryFeature MAGMA_POOL_FEATURE = new MagmaPoolFeature();
    private static final PlanetaryFeature VOLATILE_DEPOSIT_FEATURE = new VolatileDepositFeature();
    private static final PlanetaryFeature RARE_CRYSTAL_FORMATION_FEATURE = new RareCrystalFormationFeature();
    private static final PlanetaryFeature THERMAL_SINK_ZONE_FEATURE = new ThermalSinkZoneFeature();

    public static final PlanetaryFeatureDefinition REGOLITH_FLATS = REGOLITH_FLATS_FEATURE.definition();
    public static final PlanetaryFeatureDefinition STABLE_BEDROCK = STABLE_BEDROCK_FEATURE.definition();
    public static final PlanetaryFeatureDefinition MINERAL_VEIN = MINERAL_VEIN_FEATURE.definition();
    public static final PlanetaryFeatureDefinition SUBSURFACE_ICE_POCKET = SUBSURFACE_ICE_POCKET_FEATURE.definition();
    public static final PlanetaryFeatureDefinition MAGMA_POOL = MAGMA_POOL_FEATURE.definition();
    public static final PlanetaryFeatureDefinition VOLATILE_DEPOSIT = VOLATILE_DEPOSIT_FEATURE.definition();
    public static final PlanetaryFeatureDefinition RARE_CRYSTAL_FORMATION = RARE_CRYSTAL_FORMATION_FEATURE.definition();
    public static final PlanetaryFeatureDefinition THERMAL_SINK_ZONE = THERMAL_SINK_ZONE_FEATURE.definition();

    private static final Map<PlanetaryFeatureKey, PlanetaryFeatureDefinition> FEATURES = new LinkedHashMap<>();
    private static final Map<PlanetaryFeatureKey, PlanetaryFeature> BEHAVIORS = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private PlanetaryFeatureRegistry() {}

    public static synchronized void registerDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;
        register(REGOLITH_FLATS_FEATURE);
        register(STABLE_BEDROCK_FEATURE);
        register(MINERAL_VEIN_FEATURE);
        register(SUBSURFACE_ICE_POCKET_FEATURE);
        register(MAGMA_POOL_FEATURE);
        register(VOLATILE_DEPOSIT_FEATURE);
        register(RARE_CRYSTAL_FORMATION_FEATURE);
        register(THERMAL_SINK_ZONE_FEATURE);
    }

    public static synchronized PlanetaryFeature register(PlanetaryFeature feature) {
        PlanetaryFeatureDefinition definition = feature.definition();
        PlanetaryFeatureDefinition previous = FEATURES.putIfAbsent(definition.key(), definition);
        PlanetaryFeature previousBehavior = BEHAVIORS.putIfAbsent(definition.key(), feature);
        if (previous != null || previousBehavior != null) {
            throw new IllegalStateException("Duplicate planetary feature registration: " + definition.key());
        }
        return feature;
    }

    public static synchronized PlanetaryFeatureDefinition register(PlanetaryFeatureDefinition definition) {
        PlanetaryFeatureDefinition previous = FEATURES.putIfAbsent(definition.key(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate planetary feature registration: " + definition.key());
        }
        return definition;
    }

    public static PlanetaryFeatureDefinition get(PlanetaryFeatureKey key) {
        registerDefaults();
        return FEATURES.get(key);
    }

    public static PlanetaryFeature feature(PlanetaryFeatureKey key) {
        registerDefaults();
        return BEHAVIORS.get(key);
    }

    public static Collection<PlanetaryFeatureDefinition> all() {
        registerDefaults();
        return Collections.unmodifiableCollection(FEATURES.values());
    }

    public static Collection<PlanetaryFeature> allFeatures() {
        registerDefaults();
        return Collections.unmodifiableCollection(BEHAVIORS.values());
    }

    public static PlanetaryFeatureKey requiredAnchorFeature(FacilityModuleKind kind) {
        registerDefaults();
        for (PlanetaryFeature feature : BEHAVIORS.values()) {
            if (feature.isRequiredAnchorFeatureFor(kind)) return feature.key();
        }
        return null;
    }
}
