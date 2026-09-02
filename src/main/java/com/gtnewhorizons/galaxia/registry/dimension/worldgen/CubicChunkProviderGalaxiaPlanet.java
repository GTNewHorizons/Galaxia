package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

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
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeBlockPalette;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.biome.DefaultBlockPalette;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShape;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.details.Terrain3D;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.SurfaceFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.UndergroundFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle.MantleCache;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle.MantleCacheData;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle.MantleRules;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NoiseSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NormalizedSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.OctavesSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.ScaledSampler;
import lombok.Getter;
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

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Terrain generator for Galaxia planets with Cubic Chunks support
 * <p>
 * Generates a crust layer from 0 to whatever the maximum height is for the planet's terrain
 * <p>
 * Generates an upper mantle layer from -1 to -128 and a lower mantle layer from -129 to -256
 */
@ParametersAreNonnullByDefault
public class CubicChunkProviderGalaxiaPlanet implements IWorldGenerator, GalaxiaPlanetGenerator {

    private static final int CHUNK_WIDTH = 16;
    private static final int UPPER_MANTLE_CEILING = 0;
    private static final int UPPER_MANTLE_FLOOR = -128;
    private static final int LOWER_MANTLE_CEILING = -128;
    private static final int LOWER_MANTLE_FLOOR = -256;
    private static final int UPPER_INTERMEDIARY_CAVES_TOP = 64;
    private static final int UPPER_INTERMEDIARY_CAVES_BOTTOM = -64;
    private static final int LOWER_INTERMEDIARY_CAVES_TOP = -64;
    private static final int LOWER_INTERMEDIARY_CAVES_BOTTOM = -192;
    private final MantleCache upperMantleCache;
    private final MantleCache lowerMantleCache;

    private static final DefaultBlockPalette DEFAULT_PALETTE = new DefaultBlockPalette();

    private static final ImmutableBlockMeta AIR = new BlockMeta(Blocks.air);

    @Getter
    private final DimensionEnum dimension;
    private final World worldObj;
    private final Random rand;
    private final NoiseSampler crackNoise1, crackNoise2;
    @Getter
    private final HeightOracle heightOracle;
    private final ModifierHandler modifierHandler;

    private final HashMap3D<ArrayList<DeferredWrite>> deferredWrites = new HashMap3D<>();

    public record DeferredWrite(int x, int y, int z, Block block, int meta) {}

    public CubicChunkProviderGalaxiaPlanet(World world, DimensionEnum dimension) {
        this.dimension = dimension;
        this.worldObj = world;
        this.modifierHandler = new ModifierHandler(world);
        this.heightOracle = new HeightOracle(world, dimension, false, modifierHandler);

        this.rand = new StdLCG(world.getSeed());
        this.crackNoise1 = new NormalizedSampler(new ScaledSampler(new OctavesSampler(rand, 2), 0.05));
        this.crackNoise2 = new NormalizedSampler(new ScaledSampler(new OctavesSampler(rand, 2), 0.05));
        this.upperMantleCache = new MantleCache(world, rand, dimension, modifierHandler);
        this.lowerMantleCache = new MantleCache(world, rand, dimension, modifierHandler);
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
        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        if (cubeY >= 0) {
            generateCrust(cubeX, cubeY, cubeZ, data, ebs);
        } else if (cubeY >= -8) {
            generateMantle(cubeX, cubeY, cubeZ, ebs, UPPER_MANTLE_CEILING, UPPER_MANTLE_FLOOR, true);
        } else {
            generateMantle(cubeX, cubeY, cubeZ, ebs, LOWER_MANTLE_CEILING, LOWER_MANTLE_FLOOR, false);
        }

        drainDeferredWrites(cubeX, cubeY, cubeZ, cube);

        if (chunkGenerated) {
            chunk.generateSkylightMap();

            return new GenerationResult<>(cube, List.of(chunk), List.of());
        } else {
            return new GenerationResult<>(cube);
        }
    }

    /**
     * Generates a mantle layer with floor and ceiling
     * @param cubeX x coordinate of the currently generating cube
     * @param cubeY y coordinate of the currently generating cube
     * @param cubeZ z coordinate of the currently generating cube
     * @param ebs Block storage for efficient block placement
     * @param ceilingOffset Upper limit height of the ceiling
     * @param floorOffset Lower limit height of the floor
     * @param upperMantle Whether it is the upper mantle layer
     */
    private void generateMantle(int cubeX, int cubeY, int cubeZ, ExtendedBlockStorage ebs, int ceilingOffset,
        int floorOffset, boolean upperMantle) {
        ImmutableBlockMeta block;
        TerrainConfiguration ceiling;
        TerrainConfiguration floor;

        // Fetch mantle rules
        MantleRules mantleRules;
        if (upperMantle) {
            mantleRules = dimension.getUpperMantleRules();
        } else {
            mantleRules = dimension.getLowerMantleRules();
        }
        if (mantleRules == null) {
            return;
        }

        // Set important values for mantle generation
        block = mantleRules.fillerBlock;
        ceiling = mantleRules.getCeiling();
        floor = mantleRules.getFloor();
        double[] ceilingHeightmap;
        double[] floorHeightmap;
        MantleCacheData mantleCacheData;
        if (upperMantle) {
            mantleCacheData = upperMantleCache.getLocalData(cubeX, cubeZ, ceiling, floor);
        } else {
            mantleCacheData = lowerMantleCache.getLocalData(cubeX, cubeZ, ceiling, floor);
        }
        ceilingHeightmap = mantleCacheData.ceilingHeightmap();
        floorHeightmap = mantleCacheData.floorHeightmap();

        // Assign floor and ceiling caves for connecting different layers
        CaveShape ceilingCaves;
        CaveShape floorCaves;
        if (upperMantle) {
            ceilingCaves = dimension.getUpperIntermediaryCaves();
            floorCaves = dimension.getLowerIntermediaryCaves();
        } else {
            ceilingCaves = dimension.getLowerIntermediaryCaves();
            floorCaves = null;
        }

        // Prepare ceiling caves
        if (ceilingCaves != null) {
            if (!ceilingCaves.preparedCaveShape()) {
                ceilingCaves.prepareCaveShape(rand);
            }
            if (!ceilingCaves.preparedCaveCache(cubeX, cubeZ)) {
                ceilingCaves.prepareCaveCache(cubeX, cubeZ);
            }
        }

        // Prepare floor caves
        if (floorCaves != null) {
            if (!floorCaves.preparedCaveShape()) {
                floorCaves.prepareCaveShape(rand);
            }
            if (!floorCaves.preparedCaveCache(cubeX, cubeZ)) {
                floorCaves.prepareCaveCache(cubeX, cubeZ);
            }
        }

        // Set cave height coordinates
        int ceilingCaveTop = upperMantle ? UPPER_INTERMEDIARY_CAVES_TOP : LOWER_INTERMEDIARY_CAVES_TOP;
        int ceilingCaveBottom = upperMantle ? UPPER_INTERMEDIARY_CAVES_BOTTOM : LOWER_INTERMEDIARY_CAVES_BOTTOM;
        int floorCaveTop = upperMantle ? LOWER_INTERMEDIARY_CAVES_TOP : Integer.MAX_VALUE;
        int floorCaveBottom = upperMantle ? LOWER_INTERMEDIARY_CAVES_BOTTOM : Integer.MIN_VALUE;

        // Process cave data and generate
        boolean isCave = false;
        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                double ceilingHeight = -ceilingHeightmap[localX + (localZ << 4)] + ceilingOffset;
                double floorHeight = floorHeightmap[localX + (localZ << 4)] + floorOffset;
                int minY = cubeY << 4;
                int maxY = minY + 16;
                for (int y = minY; y < maxY; y++) {

                    // Check if y overlaps with floor or ceiling terrain
                    if ((y < floorHeight || y > ceilingHeight)) {

                        // Handle ceiling caves
                        if (ceilingCaves != null && y > ceilingCaveBottom) {
                            isCave = ceilingCaves
                                .isInCave(localX, y - ceilingCaveBottom, localZ, ceilingCaveTop - ceilingCaveBottom);
                        }

                        // Handle floor caves
                        if (!isCave && floorCaves != null && y < floorCaveTop) {
                            isCave = floorCaves
                                .isInCave(localX, y - floorCaveBottom, localZ, floorCaveTop - floorCaveBottom);
                        }

                        // Place block or reset cave status if current block is in a cave
                        if (!isCave) {
                            placeBlock(ebs, block, localX, y, localZ);
                        } else {
                            isCave = false;
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles terrain generation for y values greater than 0
     * @param cubeX x coordinate of the currently generating cube
     * @param cubeY y coordinate of the currently generating cube
     * @param cubeZ z coordinate of the currently generating cube
     * @param data Data of the current chunk
     * @param ebs Block storage for efficient block placement
     */
    private void generateCrust(int cubeX, int cubeY, int cubeZ, HeightOracle.ChunkData data, ExtendedBlockStorage ebs) {
        CaveShape crustCaves = null;
        Terrain3D terrain3d = null;
        CaveShape intermediateCaves = dimension.getUpperIntermediaryCaves();
        if (intermediateCaves != null) {
            if (!intermediateCaves.preparedCaveShape()) {
                intermediateCaves.prepareCaveShape(rand);
            }
            if (!intermediateCaves.preparedCaveCache(cubeX, cubeZ)) {
                intermediateCaves.prepareCaveCache(cubeX, cubeZ);
            }
        }

        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                BiomeGenBase localBiome = data.biomes[localX + localZ * CHUNK_WIDTH];

                BiomeBlockPalette palette = DEFAULT_PALETTE;

                // Check if the biome is a space biome and specify additional biome content
                if (localBiome instanceof BiomeGenSpace spaceBiome) {
                    palette = spaceBiome;
                    Terrain3D biome3d = spaceBiome.getTerrain3d();

                    if (crustCaves == null || !crustCaves.equals(spaceBiome.getCaveShape())) {
                        crustCaves = spaceBiome.getCaveShape();
                    }
                    if (terrain3d == null && biome3d != null || biome3d == null && terrain3d != null
                        || biome3d != null && !biome3d.equals(terrain3d)) {
                        terrain3d = biome3d;
                    }
                }

                int terrainHeight = Math.max(1, (int) data.heightmap[localX + (localZ << 4)]);

                // Prepare cave generation if present
                if (crustCaves != null) {
                    if (!crustCaves.preparedCaveShape()) {
                        crustCaves.prepareCaveShape(rand);
                    }
                    if (!crustCaves.preparedCaveCache(cubeX, cubeZ)) {
                        crustCaves.prepareCaveCache(cubeX, cubeZ);
                    }
                }

                // Prepare 3D terrain if present
                int terrain3dHeight = 0;
                if (terrain3d != null) {
                    if (!terrain3d.preparedFunctions()) {
                        terrain3d.prepareFunctions(rand, worldObj.getSeed());
                    }
                    if (!terrain3d.preparedTerrainCache(cubeX, cubeZ)) {
                        terrain3d.prepareTerrainCache(cubeX, cubeZ, modifierHandler);
                    }
                    terrain3dHeight = terrain3d.getHeight(localX, localZ);
                }

                int genHeight = Math.max(palette.getOceanHeight(), terrainHeight + terrain3dHeight);

                int minY = cubeY << 4;
                int maxY = Math.min(minY + 16, genHeight);

                for (int y = minY; y < maxY; y++) {
                    // True when voxel is terrain and can be carved (e.g. by caves)
                    boolean isTerrain = true;

                    ImmutableBlockMeta block;

                    // Handle 3D terrain block checks
                    if (y >= terrainHeight && y - terrainHeight < terrain3dHeight) {
                        if (!terrain3d.isSolid(localX, y - terrainHeight, localZ)) {
                            isTerrain = false;
                        }
                    }

                    // Set block according to height
                    if (isTerrain && y >= terrainHeight + terrain3dHeight - palette.getSurfaceThickness()) {
                        block = getSurfaceBlock(data, localX, localZ, y, palette);
                    } else if (isTerrain) {
                        block = palette.getFillerBlocks()
                            .getStrataBlock(y);
                    } else {
                        block = AIR;
                    }

                    int oceanHeight = palette.getOceanHeight();

                    // Handle ocean logic if necessary
                    if (y <= oceanHeight) {
                        if (y == terrainHeight - 1) { // Handle seabed
                            if (y > palette.getSeabedHeight()) {
                                block = palette.getOceanSurface();
                                isTerrain = false;
                            } else {
                                block = palette.getSeabed();
                            }
                        } else if (y > terrainHeight - 1) { // Handle ocean itself
                            isTerrain = false;

                            int oceanDepth = oceanHeight - terrainHeight;

                            boolean topTwoLayers = y == oceanHeight - 1 || y == oceanHeight - 2;

                            // Check if a cracked surface should generate
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
                                // Fill out ocean with surface and filler blocks
                                if (y == oceanHeight) {
                                    block = palette.getOceanSurface();
                                } else {
                                    block = palette.getOceanFiller();
                                }
                            }
                        }
                    }

                    // Handle cave generation
                    // Check for crust caves
                    boolean isCave = crustCaves != null && isTerrain && crustCaves.isInCave(localX, y, localZ, terrainHeight);
                    // Check for caves connecting to the upper mantle
                    if (!isCave && intermediateCaves != null && y < UPPER_INTERMEDIARY_CAVES_TOP) {
                        isCave = intermediateCaves.isInCave(
                            localX,
                            y - UPPER_INTERMEDIARY_CAVES_BOTTOM,
                            localZ,
                            UPPER_INTERMEDIARY_CAVES_TOP - UPPER_INTERMEDIARY_CAVES_BOTTOM);
                    }
                    if (isCave) {
                        block = AIR;
                    }

                    placeBlock(ebs, block, localX, y, localZ);
                }
            }
        }
    }

    /**
     * Picks the correct surface block
     * @param data Data of the current chunk
     * @param localX x coordinate within the chunk
     * @param localZ z coordinate within the chunk
     * @param y Absolute height coordinate
     * @param palette Block palette of the dominant biome
     * @return Surface block
     */
    private static ImmutableBlockMeta getSurfaceBlock(HeightOracle.ChunkData data, int localX, int localZ, int y, BiomeBlockPalette palette) {
        ImmutableBlockMeta block;
        ImmutableBlockMeta replacementBlock = data.surfaceBlocks[localX + (localZ << 4)];

        if (replacementBlock != null) {
            block = replacementBlock;
        } else {
            // Handle snow layer generation
            block = y >= palette.getSnowHeight() ? palette.getSnowBlock() : palette.getTopBlock();
        }
        return block;
    }

    /**
     * Efficiently places a block into the world
     * @param ebs Block storage for efficient placement
     * @param block Block to be placed
     * @param localX x coordinate within the chunk
     * @param y Absolute height
     * @param localZ z coordinate within the chunk
     */
    private static void placeBlock(ExtendedBlockStorage ebs, ImmutableBlockMeta block, int localX, int y, int localZ) {
        if (block.getBlock() != Blocks.air) {
            ebs.func_150818_a(localX, y & 15, localZ, block.getBlock());
        }
        if (block.getBlockMeta() != 0) {
            ebs.setExtBlockMetadata(localX, y & 15, localZ, block.getBlockMeta());
        }
    }

    /**
     * Checks if a crack should generate at the given coordinates
     * @param crackThickness Thickness of the filled out part of the cracks
     * @param x x coordinate of the current block
     * @param z z coordinate of the current block
     * @return Whether the block should generate a crack
     */
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

    private Random withSeed(int chunkX, int chunkZ, int index, int nonce) {
        long seed = Fnv1a64.initialState();
        seed = Fnv1a64.hashStep(seed, worldObj.getSeed());
        seed = Fnv1a64.hashStep(seed, chunkX);
        seed = Fnv1a64.hashStep(seed, chunkZ);
        seed = Fnv1a64.hashStep(seed, index);
        seed = Fnv1a64.hashStep(seed, nonce);

        rand.setSeed(seed);
        return rand;
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
