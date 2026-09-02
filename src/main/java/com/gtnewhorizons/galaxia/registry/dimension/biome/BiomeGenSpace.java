package com.gtnewhorizons.galaxia.registry.dimension.biome;

import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShape;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationFunction;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.details.Terrain3D;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.SurfaceFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.UndergroundFeature;

import lombok.Getter;

/**
 * The class holding all generation fields for Biome generation
 */
@Getter
public class BiomeGenSpace extends BiomeGenBase implements BiomeBlockPalette {

    private final ImmutableBlockMeta topBlock;
    private final TerrainConfiguration terrain;
    private final int snowHeight;
    private final ImmutableBlockMeta snowBlock;
    private final int oceanHeight;
    private final int seabedHeight;
    private final ImmutableBlockMeta oceanFiller;
    private final ImmutableBlockMeta oceanSurface;
    private final ImmutableBlockMeta seabed;
    private final List<SurfaceFeature> surfaceFeatures;
    private final List<UndergroundFeature> undergroundFeatures;
    private final int surfaceThickness;
    private final ImmutableBlockMeta oceanCrackBlock;
    private final float oceanCrackThickness;
    private final int oceanCrackComplexity;
    private final StratificationFunction fillerBlocks;
    private final CaveShape caveShape;
    private final Terrain3D terrain3d;

    /**
     * Creates a biome generator and configures it based on the provided builder
     *
     * @param id The biome ID
     * @param b  The configured (hopefully) biome builder
     */
    public BiomeGenSpace(int id, BiomeGenBuilder b) {
        super(id);

        // Configure the class based on builder fields
        this.setBiomeName(b.name);
        this.setHeight(b.height);
        this.setTemperatureRainfall(b.temperature, b.rainfall);
        this.enableRain = b.enableRain;

        this.fillerBlocks = b.fillerBlocks;
        this.topBlock = b.topBlock;
        this.snowBlock = b.snowBlock;
        this.snowHeight = b.snowHeight;
        this.oceanHeight = b.oceanHeight;
        this.seabedHeight = b.seabedHeight;
        this.oceanFiller = b.oceanFiller;
        this.oceanSurface = b.oceanSurface;
        this.seabed = b.seabed;
        this.caveShape = b.caveShape;
        this.terrain3d = b.terrain3d;

        this.spawnableCaveCreatureList = b.mobsCave;
        this.spawnableCreatureList = b.mobsGeneral;
        this.spawnableMonsterList = b.mobsMonster;
        this.spawnableWaterCreatureList = b.mobsWater;
        this.flowers = b.flowers;
        this.surfaceFeatures = b.surfaceFeatures;
        this.undergroundFeatures = b.undergroundFeatures;
        this.surfaceThickness = b.surfaceThickness;
        this.oceanCrackThickness = b.oceanCrackThickness;
        this.oceanCrackBlock = b.oceanCrackBlock;
        this.oceanCrackComplexity = b.oceanCrackComplexity;

        // Set terrain if there is one, if not build a default
        this.terrain = b.terrain != null ? b.terrain
            : TerrainConfiguration.builder()
                .build();
    }
}
