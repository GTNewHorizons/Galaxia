package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.dimension.sky.CelestialBody;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;

public record PlayableDimensionProfile(DimensionEnum dimension, Class<? extends WorldProvider> provider,
    boolean keepLoaded, double airResistance, boolean removeSpeedCancelation, List<CelestialBody> celestialBodies,
    EffectBuilder effects, EnumTiers tier, ResourceLocation[] skyboxTexture, List<Block> validSpaceStationBlocks,
    WorldGenerationAdapter worldGenerationAdapter) {

    public PlayableDimensionProfile {
        if (dimension == null) throw new IllegalStateException("Playable dimension requires a dimension enum");
        if (provider == null) throw new IllegalStateException("Playable dimension requires a world provider");
        if (effects == null) throw new IllegalStateException("Playable dimension requires effects");
        if (tier == null) throw new IllegalStateException("Playable dimension requires a tier");
        if (worldGenerationAdapter == null) throw new IllegalStateException("Playable dimension requires worldgen");
        celestialBodies = celestialBodies == null ? List.of() : Collections.unmodifiableList(celestialBodies);
        skyboxTexture = skyboxTexture == null ? null : skyboxTexture.clone();
        validSpaceStationBlocks = validSpaceStationBlocks == null ? List.of()
            : Collections.unmodifiableList(validSpaceStationBlocks);
    }

    public ResourceLocation[] skyboxTexture() {
        return skyboxTexture == null ? null : skyboxTexture.clone();
    }

    public static Builder builder(DimensionEnum dimension) {
        return new Builder(dimension);
    }

    public static final class Builder {

        private final DimensionEnum dimension;
        private Class<? extends WorldProvider> provider = WorldProviderSpace.class;
        private boolean keepLoaded = true;
        private double airResistance = 1.0;
        private boolean removeSpeedCancelation;
        private List<CelestialBody> celestialBodies = List.of();
        private EffectBuilder effects = EffectBuilder.builder()
            .build();
        private EnumTiers tier = EnumTiers.TIER_1;
        private ResourceLocation[] skyboxTexture;
        private final List<Block> validSpaceStationBlocks = new ArrayList<>();
        private WorldGenerationAdapter worldGenerationAdapter;

        private Builder(DimensionEnum dimension) {
            this.dimension = dimension;
        }

        public Builder provider(Class<? extends WorldProvider> value) {
            this.provider = value;
            return this;
        }

        public Builder keepLoaded(boolean value) {
            this.keepLoaded = value;
            return this;
        }

        public Builder airResistance(double value) {
            this.airResistance = value;
            return this;
        }

        public Builder removeSpeedCancelation() {
            this.removeSpeedCancelation = true;
            return this;
        }

        public Builder celestialBodies(List<CelestialBody> value) {
            this.celestialBodies = value == null ? List.of() : value;
            return this;
        }

        public Builder effects(EffectBuilder value) {
            this.effects = value;
            return this;
        }

        public Builder tier(EnumTiers value) {
            this.tier = value;
            return this;
        }

        public Builder skybox(ResourceLocation[] value) {
            this.skyboxTexture = value == null ? null : value.clone();
            return this;
        }

        public Builder addValidSpaceStationBlocks(Block... blocks) {
            if (blocks != null) Collections.addAll(validSpaceStationBlocks, blocks);
            return this;
        }

        public Builder worldGeneration(WorldGenerationAdapter value) {
            this.worldGenerationAdapter = value;
            return this;
        }

        public PlayableDimensionProfile build() {
            return new PlayableDimensionProfile(
                dimension,
                provider,
                keepLoaded,
                airResistance,
                removeSpeedCancelation,
                celestialBodies,
                effects,
                tier,
                skyboxTexture,
                validSpaceStationBlocks,
                worldGenerationAdapter);
        }
    }
}
