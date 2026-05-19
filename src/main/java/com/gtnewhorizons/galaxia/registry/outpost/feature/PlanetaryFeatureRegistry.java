package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;

public final class PlanetaryFeatureRegistry {

    public static final PlanetaryFeatureDefinition REGOLITH_FLATS = feature("regolith_flats")
        .displayName("Regolith Flats")
        .description("Flat construction terrain")
        .layer(PlanetaryFeatureLayer.TERRAIN)
        .placement(PlanetaryFeaturePlacement.patch(42.0, 14.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_REGOLITH_FLATS.getColor())
        .build();

    public static final PlanetaryFeatureDefinition STABLE_BEDROCK = feature("stable_bedrock")
        .displayName("Stable Bedrock")
        .description("Structurally stable terrain")
        .layer(PlanetaryFeatureLayer.TERRAIN)
        .placement(PlanetaryFeaturePlacement.patch(30.0, 10.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_STABLE_BEDROCK.getColor())
        .build();

    public static final PlanetaryFeatureDefinition MINERAL_VEIN = feature("mineral_vein").displayName("Mineral Vein")
        .description("Ore-rich tile")
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.patch(14.0, 6.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_MINERAL_VEIN.getColor())
        .build();

    public static final PlanetaryFeatureDefinition SUBSURFACE_ICE_POCKET = feature("subsurface_ice_pocket")
        .displayName("Subsurface Ice Pocket")
        .description("Buried ice deposit")
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.patch(5.0, 2.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_SUBSURFACE_ICE_POCKET.getColor())
        .build();

    public static final PlanetaryFeatureDefinition GEOTHERMAL_VENT = feature("geothermal_vent")
        .displayName("Geothermal Vent")
        .description("Natural heat source")
        .layer(PlanetaryFeatureLayer.ENVIRONMENT)
        .placement(PlanetaryFeaturePlacement.isolated())
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_GEOTHERMAL_VENT.getColor())
        .build();

    public static final PlanetaryFeatureDefinition VOLATILE_DEPOSIT = feature("volatile_deposit")
        .displayName("Volatile Deposit")
        .description("Chemical volatile pocket")
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.clusteredPatch(8.0, 4.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_VOLATILE_DEPOSIT.getColor())
        .build();

    public static final PlanetaryFeatureDefinition RARE_CRYSTAL_FORMATION = feature("rare_crystal_formation")
        .displayName("Rare Crystal Formation")
        .description("Rare crystal growth")
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.patch(3.0, 1.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_RARE_CRYSTAL_FORMATION.getColor())
        .build();

    public static final PlanetaryFeatureDefinition THERMAL_SINK_ZONE = feature("thermal_sink_zone")
        .displayName("Thermal Sink Zone")
        .description("Naturally heat-absorbing terrain")
        .layer(PlanetaryFeatureLayer.ENVIRONMENT)
        .placement(PlanetaryFeaturePlacement.patch(12.0, 5.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_THERMAL_SINK_ZONE.getColor())
        .build();

    private static final Map<PlanetaryFeatureKey, PlanetaryFeatureDefinition> FEATURES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private PlanetaryFeatureRegistry() {}

    public static synchronized void registerDefaults() {
        if (bootstrapped) return;
        bootstrapped = true;
        register(REGOLITH_FLATS);
        register(STABLE_BEDROCK);
        register(MINERAL_VEIN);
        register(SUBSURFACE_ICE_POCKET);
        register(GEOTHERMAL_VENT);
        register(VOLATILE_DEPOSIT);
        register(RARE_CRYSTAL_FORMATION);
        register(THERMAL_SINK_ZONE);
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

    public static Collection<PlanetaryFeatureDefinition> all() {
        registerDefaults();
        return Collections.unmodifiableCollection(FEATURES.values());
    }

    private static PlanetaryFeatureDefinition.Builder feature(String path) {
        return PlanetaryFeatureDefinition.builder(path)
            .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/" + path + ".png"));
    }
}
