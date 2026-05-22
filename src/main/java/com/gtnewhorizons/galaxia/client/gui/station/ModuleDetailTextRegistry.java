package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleDetailTextRegistry {

    private static final int SECTION_GAP = 4;
    private static final int LINE_GAP = 3;
    private static final String FEATURE_LINE_PREFIX = "  ";
    private static final String FEATURE_EFFECT_PREFIX = "    ";
    private static final List<Provider> PROVIDERS = new ArrayList<>();

    static {
        register(ModuleDetailTextRegistry::appendBaseModuleText);
        register(ModuleDetailTextRegistry::appendCapacityText);
        register(ModuleDetailTextRegistry::appendMaintenanceText);
        register(ModuleDetailTextRegistry::appendPlanetaryFeatureText);
        register(ModuleDetailTextRegistry::appendRecipeText);
    }

    private ModuleDetailTextRegistry() {}

    static void register(Provider provider) {
        if (provider == null) throw new IllegalArgumentException("Module detail text provider must not be null");
        PROVIDERS.add(provider);
    }

    static Lines collect(Context context) {
        Lines lines = new Lines();
        for (Provider provider : PROVIDERS) {
            provider.append(context, lines);
        }
        return lines;
    }

    static List<Provider> providers() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    private static void appendBaseModuleText(Context context, Lines lines) {
        lines.line(
            "Module: " + context.module()
                .kind()
                .name(),
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private static void appendCapacityText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (!module.kind()
            .isCapacityModule()) return;
        long baseCapacity = module.baseCapacity();
        int neighborCount = StationLayout.countOrthogonalNeighbors(context.layout(), module.anchor(), module.kind());
        long effectiveCapacity = Math.round(baseCapacity * (1.0 + 0.5 * neighborCount));
        lines.sectionLine("Base: " + baseCapacity, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lines.line("Capacity: " + effectiveCapacity, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
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
            lines.sectionLine("Maintenance Bay: -20% upkeep", EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
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

        lines.sectionLine("Features:", EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
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

            lines.line(
                FEATURE_LINE_PREFIX + featureDisplayName(
                    key) + " " + covered + "/" + total + (hasEffect ? "" : " (no current effect)"),
                hasEffect ? EnumColors.MAP_COLOR_TEXT_SECTION.getColor() : EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
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
        if (!(context.module()
            .component() instanceof IRecipeModule recipeModule)) return;
        RecipeConfig cfg = recipeModule.getRecipeConfig();
        int slots = cfg == null ? 0
            : cfg.savedRecipes()
                .toList()
                .size();
        lines.sectionLine("Recipes: " + slots, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
    }

    interface Provider {

        void append(Context context, Lines lines);
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

        int draw(int x, int y) {
            int lineY = y;
            for (Entry entry : entries) {
                if (entry == SectionGap.INSTANCE) {
                    lineY += SECTION_GAP;
                    continue;
                }
                TextEntry text = (TextEntry) entry;
                lineY = drawLine(text.text(), x, lineY, text.color());
            }
            return lineY;
        }

        int size() {
            int size = 0;
            for (Entry entry : entries) {
                if (entry instanceof TextEntry) size++;
            }
            return size;
        }

        List<String> texts() {
            List<String> texts = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry instanceof TextEntry text) {
                    texts.add(text.text());
                }
            }
            return texts;
        }

        private void section() {
            if (entries.isEmpty() || entries.get(entries.size() - 1) == SectionGap.INSTANCE) return;
            entries.add(SectionGap.INSTANCE);
        }

        private static int drawLine(String text, int x, int y, int color) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawStringWithShadow(text, x, y, color);
            return y + fr.FONT_HEIGHT + LINE_GAP;
        }
    }

    private interface Entry {
    }

    private record TextEntry(String text, int color) implements Entry {}

    private enum SectionGap implements Entry {
        INSTANCE
    }
}
