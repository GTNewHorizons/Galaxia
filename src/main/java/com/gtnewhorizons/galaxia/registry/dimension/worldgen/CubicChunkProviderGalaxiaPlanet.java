package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.HashMap3D;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeBlockPalette;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.biome.DefaultBlockPalette;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShape;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.SurfaceFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.UndergroundFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NoiseSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NormalizedSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.OctavesSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.ScaledSampler;

import lombok.Getter;

@ParametersAreNonnullByDefault
public class CubicChunkProviderGalaxiaPlanet implements IWorldGenerator, GalaxiaPlanetGenerator {

    private static final int CHUNK_AREA = 256;
    private static final int CHUNK_WIDTH = 16;

    private static final DefaultBlockPalette DEFAULT_PALETTE = new DefaultBlockPalette();

    private static final ImmutableBlockMeta AIR = new BlockMeta(Blocks.air);

    @Getter
    private final DimensionEnum dimension;
    private final World worldObj;
    private final Random rand;
    private final NoiseSampler crackNoise1, crackNoise2;
    @Getter
    private final HeightOracle heightOracle;

    private final HashMap3D<ArrayList<DeferredWrite>> deferredWrites = new HashMap3D<>();

    public record DeferredWrite(int x, int y, int z, Block block, int meta) {}

    public CubicChunkProviderGalaxiaPlanet(World world, DimensionEnum dimension) {
        this.dimension = dimension;
        this.worldObj = world;
        this.heightOracle = new HeightOracle(world, dimension, false);

        this.rand = new StdLCG(world.getSeed());
        this.crackNoise1 = new NormalizedSampler(new ScaledSampler(new OctavesSampler(rand, 2), 0.05));
        this.crackNoise2 = new NormalizedSampler(new ScaledSampler(new OctavesSampler(rand, 2), 0.05));
    }

    @Override
    public void setBlockSafe(int x, int y, int z, Block block, int meta) {
        worldObj.setBlock(x, y, z, block, meta, 2);
    }

    @Override
    public void queueDeferredWrite(int x, int y, int z, Block block, int meta) {
        deferredWrites.computeIfAbsent(x >> 4, y >> 4, z >> 4, ($x, $y, $z) -> new ArrayList<>())
            .add(new DeferredWrite(x & 0xF, y & 0xF, z & 0xF, block, meta));
    }

    private void drainDeferredWrites(int cx, int cy, int cz, Cube cube) {
        var writes = deferredWrites.remove(cx, cy, cz);

        if (writes == null) return;

        var ebs = cube.getOrCreateStorage();

        for (DeferredWrite w : writes) {
            ebs.func_150818_a(w.x, w.y, w.z, w.block);
            ebs.setExtBlockMetadata(w.x, w.y, w.z, w.meta);
        }
    }

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        boolean chunkGenerated = false;

        if (chunk == null) {
            chunk = provideColumn(worldObj, cubeX, cubeZ).object;
            chunkGenerated = true;
        }

        Cube cube = new Cube(chunk, cubeY);

        var data = heightOracle.getOrCompute(cubeX, cubeZ);

        CaveShape caveShape = null;

        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                BiomeGenBase localBiome = data.biomes[localX + localZ * CHUNK_WIDTH];

                BiomeBlockPalette palette = DEFAULT_PALETTE;

                if (localBiome instanceof BiomeGenSpace spaceBiome) {
                    palette = spaceBiome;

                    if (caveShape == null || !caveShape.equals(spaceBiome.getCaveShape())) {
                        caveShape = spaceBiome.getCaveShape();
                    }
                }

                int terrainHeight = Math.max(1, (int) data.heightmap[localX + (localZ << 4)]);

                if (caveShape != null) {
                    if (!caveShape.preparedCaveShape()) {
                        caveShape.prepareCaveShape(rand);
                    }
                    if (!caveShape.preparedCaveCache(cubeX, cubeZ)) {
                        caveShape.prepareCaveCache(cubeX, cubeZ);
                    }
                }

                int genHeight = Math.max(palette.getOceanHeight(), terrainHeight);

                int minY = cubeY << 4;
                int maxY = Math.min(minY + 16, genHeight);

                for (int y = minY; y < maxY; y++) {
                    // True when voxel is terrain and can be carved (e.g. by caves)
                    boolean isTerrain = true;

                    ImmutableBlockMeta block;

                    if (y >= terrainHeight - palette.getSurfaceThickness()) {
                        ImmutableBlockMeta replacementBlock = data.surfaceBlocks[localX + (localZ << 4)];

                        if (replacementBlock != null) {
                            block = replacementBlock;
                        } else {
                            block = y >= palette.getSnowHeight() ? palette.getSnowBlock() : palette.getTopBlock();
                        }
                    } else {
                        block = palette.getFillerBlocks()
                            .getStrataBlock(y);
                    }

                    int oceanHeight = palette.getOceanHeight();

                    if (y <= oceanHeight) {
                        if (y == terrainHeight - 1) {
                            if (y > palette.getSeabedHeight()) {
                                block = palette.getOceanSurface();
                                isTerrain = false;
                            } else {
                                block = palette.getSeabed();
                            }
                        } else if (y > terrainHeight - 1) {
                            isTerrain = false;

                            int oceanDepth = oceanHeight - terrainHeight;

                            boolean topTwoLayers = y == oceanHeight - 1 || y == oceanHeight - 2;

                            boolean isCrack = oceanDepth >= 2 && palette.hasCracks()
                                && topTwoLayers
                                && isCrackBlock(
                                    palette.getOceanCrackThickness(),
                                    cubeX * CHUNK_WIDTH + localX,
                                    cubeZ * CHUNK_WIDTH + localZ);

                            if (isCrack) {
                                // The top layer should always be air and the lower blocks should be the crack blocks
                                block = y == oceanHeight - 1 ? AIR : palette.getOceanCrackBlock();
                            } else {
                                if (y == oceanHeight) {
                                    block = palette.getOceanSurface();
                                } else {
                                    block = palette.getOceanFiller();
                                }
                            }
                        }
                    }

                    if (caveShape != null && isTerrain && caveShape.generateCave(localX, y, localZ, terrainHeight)) {
                        block = AIR;
                    }

                    if (block != null) {
                        if (block.getBlock() != Blocks.air) {
                            ebs.func_150818_a(localX, y & 15, localZ, block.getBlock());
                        }
                        if (block.getBlockMeta() != 0) {
                            ebs.setExtBlockMetadata(localX, y & 15, localZ, block.getBlockMeta());
                        }
                    }
                }
            }
        }

        drainDeferredWrites(cubeX, cubeY, cubeZ, cube);

        if (chunkGenerated) {
            chunk.generateSkylightMap();

            return new GenerationResult<>(cube, List.of(chunk), List.of());
        } else {
            return new GenerationResult<>(cube);
        }
    }

    private boolean isCrackBlock(float crackThickness, int x, int z) {
        double a = crackNoise1.sample(x, z);
        double b = crackNoise2.sample(x, z);

        return a * a + b * b < crackThickness;
    }

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        Chunk chunk = new Chunk(world, columnX, columnZ);

        var data = heightOracle.getOrCompute(columnX, columnZ);

        WorldgenUtils.setBiomes(chunk, data.biomes);

        return new GenerationResult<>(chunk);
    }

    private ICubeLoader getCubeLoader() {
        return getCubeProviderServer().getCubeLoader();
    }

    private CubeProviderServer getCubeProviderServer() {
        return ((ICubicWorldInternal.Server) worldObj).getCubeCache();
    }

    @Override
    public void populate(Cube cube) {
        var data = heightOracle.getOrCompute(cube.getX(), cube.getZ());

        int surfaceMinY = (int) (data.heightMin / 16d);
        int surfaceMaxY = MathHelper.ceiling_double_int(data.heightMax / 16d);

        BiomeGenSpace biome = data.biomes[0] instanceof BiomeGenSpace space ? space : null;

        int cx = cube.getX();
        int cy = cube.getY();
        int cz = cube.getZ();

        cube.getColumn().isTerrainPopulated = true;

        boolean isSurface = cy >= surfaceMinY && cy <= surfaceMaxY;

        ICubeLoader loader = getCubeLoader();

        if (isSurface) {
            loader.cacheCubes(new Box(cx - 1, surfaceMinY, cz - 1, cx + 1, surfaceMaxY, cz + 1));

            if (biome != null) {
                for (SurfaceFeature feature : biome.getSurfaceFeatures()) {
                    feature.generateSurfaceFeature(worldObj, this, cx, cz);
                }
            }

            populateCubes(loader, biome, new Box(cx, surfaceMinY, cz, cx, surfaceMaxY, cz), surfaceMaxY);

            loader.uncacheCubes();
        } else {
            loader.cacheCubes(new Box(cx - 1, cy - 1, cz - 1, cx + 1, cy + 1, cz + 1));

            populateCubes(loader, biome, new Box(cx, cy, cz, cx, cy, cz), surfaceMaxY);

            loader.uncacheCubes();
        }
    }

    private void populateCubes(ICubeLoader loader, @Nullable BiomeGenSpace biome, Box cubesToPopulate,
        int surfaceMaxY) {
        for (var pos : cubesToPopulate) {
            if (pos.y() <= surfaceMaxY && biome != null) {
                // Generate underground features
                for (UndergroundFeature feature : biome.getUndergroundFeatures()) {
                    feature.generateUndergroundFeature(worldObj, this, pos.x(), pos.y(), pos.z());
                }
            }

            loader.getCube(pos.x(), pos.y(), pos.z(), Requirement.GENERATE)
                .markPopulated(Cube.POP_ALL);
        }
    }

    @Override
    public void recreateStructures(ICube cube) {

    }

    @Override
    public void recreateStructures(Chunk column) {

    }

    @Override
    public List<SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return List.of();
    }

    @Override
    public @Nullable ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return null;
    }
}
