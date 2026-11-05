package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleDetailTextRegistry {

    private static final String FEATURE_LINE_PREFIX = "  ";
    private static final String FEATURE_EFFECT_PREFIX = "    ";

    private ModuleDetailTextRegistry() {}

    static Lines collect(Context context) {
        Lines lines = new Lines();
        appendCapacityText(context, lines);
        appendMaintenanceText(context, lines);
        appendPlanetaryFeatureText(context, lines);
        appendRecipeText(context, lines);
        return lines;
    }

    private static void appendCapacityText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (!module.kind()
            .isCapacityModule()) return;
        long baseCapacity = module.baseCapacity();
        int neighborCount = StationLayout.countOrthogonalNeighbors(context.layout(), module.anchor(), module.kind());
        long effectiveCapacity = Math.round(baseCapacity * (1.0 + 0.5 * neighborCount));
        lines.sectionLine(
            effectiveCapacity == baseCapacity
                ? net.minecraft.util.StatCollector
                    .translateToLocalFormatted("galaxia.gui.station.detail.capacity", effectiveCapacity)
                : net.minecraft.util.StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.detail.capacity_bonus",
                    effectiveCapacity,
                    baseCapacity),
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private static void appendMaintenanceText(Context context, Lines lines) {
        if (context.facilityId() == null) return;
        Set<StationTileCoord> coverage = GalaxiaAPI.getMaintenanceCoverage(context.facilityId());
        for (StationTileCoord coord : context.module()
            .shape()
            .tiles(
                context.module()
                    .anchor())) {
            if (!coverage.contains(coord)) continue;
            lines.sectionLine(
                StatCollector.translateToLocal("galaxia.gui.station.detail.maintenance"),
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            return;
        }
    }

    private static void appendPlanetaryFeatureText(Context context, Lines lines) {
        Map<PlanetaryFeatureKey, Integer> coveredFeatures = new LinkedHashMap<>();
        int totalTiles = 0;
        for (StationTileCoord coord : context.module()
            .shape()
            .tiles(
                context.module()
                    .anchor())) {
            totalTiles++;
            for (PlanetaryFeatureKey key : context.facility()
                .planetaryFeaturesAt(coord)) {
                coveredFeatures.merge(key, 1, Integer::sum);
            }
        }
        appendFeatureLines(
            lines,
            coveredFeatures,
            totalTiles,
            context.facility()
                .featureContributions(context.module()));
    }

    static void appendFeatureLines(Lines lines, Map<PlanetaryFeatureKey, Integer> coveredFeatures, int totalTiles,
        List<FeatureContribution> contributions) {
        Map<PlanetaryFeatureKey, List<FeatureContribution>> contributionsByFeature = new LinkedHashMap<>();
        for (FeatureContribution contribution : contributions) {
            contributionsByFeature.computeIfAbsent(contribution.key(), ignored -> new ArrayList<>())
                .add(contribution);
        }

        LinkedHashSet<PlanetaryFeatureKey> keys = new LinkedHashSet<>(coveredFeatures.keySet());
        keys.addAll(contributionsByFeature.keySet());
        if (keys.isEmpty()) return;

        lines.sectionLine(
            StatCollector.translateToLocal("galaxia.gui.station.detail.features"),
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        for (PlanetaryFeatureKey key : keys) {
            List<FeatureContribution> featureContributions = contributionsByFeature.getOrDefault(key, List.of());
            int covered = coveredFeatures.getOrDefault(
                key,
                featureContributions.isEmpty() ? 0
                    : featureContributions.get(0)
                        .coveredTiles() & 0xFF);
            int total = totalTiles > 0 ? totalTiles
                : featureContributions.isEmpty() ? covered
                    : featureContributions.get(0)
                        .totalTiles() & 0xFF;
            boolean hasEffect = featureContributions.stream()
                .anyMatch(
                    contribution -> !contribution.effectLine()
                        .isBlank());

            lines
                .line(
                    FEATURE_LINE_PREFIX + StatCollector.translateToLocalFormatted(
                        hasEffect ? "galaxia.gui.station.detail.feature"
                            : "galaxia.gui.station.detail.feature_no_effect",
                        featureDisplayName(key),
                        covered,
                        total),
                    hasEffect ? EnumColors.MAP_COLOR_TEXT_SECTION.getColor()
                        : EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            for (FeatureContribution contribution : featureContributions) {
                if (contribution.effectLine()
                    .isBlank()) continue;
                lines.line(
                    FEATURE_EFFECT_PREFIX + contribution.effectLine(),
                    EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            }
        }
    }

    private static String featureDisplayName(PlanetaryFeatureKey key) {
        PlanetaryFeatureDefinition definition = PlanetaryFeatureRegistry.get(key);
        return definition != null ? definition.displayName() : key.toString();
    }

    private static void appendRecipeText(Context context, Lines lines) {
        if (context.module()
            .recipe() == null) return;
        int slots = context.facility()
            .recipeBook(context.module())
            .recipes()
            .size();
        lines.sectionLine(
            StatCollector.translateToLocalFormatted("galaxia.gui.station.detail.recipes", slots),
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
    }

    record Context(AutomatedFacility facility, StationLayout layout, ModuleInstance module,
        @Nullable CelestialAsset.ID facilityId) {}

    static final class Lines {

        private final List<Entry> entries = new ArrayList<>();

        void line(String text, int color) {
            if (text == null || text.isBlank()) return;
            entries.add(new TextEntry(text, color));
        }

        void sectionLine(String text, int color) {
            section();
            line(text, color);
        }

        /** The lines in order, a section gap as a blank line */
        List<GalaxiaUi.Line> lines() {
            List<GalaxiaUi.Line> lines = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                lines.add(
                    entry instanceof TextEntry text ? new GalaxiaUi.Line(text.text(), text.color())
                        : new GalaxiaUi.Line("", 0));
            }
            return lines;
        }

        private void section() {
            if (entries.isEmpty() || entries.get(entries.size() - 1) == SectionGap.INSTANCE) return;
            entries.add(SectionGap.INSTANCE);
        }
    }

    private interface Entry {
    }

    private record TextEntry(String text, int color) implements Entry {}

    private enum SectionGap implements Entry {
        INSTANCE
    }
}
