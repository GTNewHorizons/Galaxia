package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import sun.misc.Unsafe;

/** Native integration contracts. World storage and entity lookup are the external harness boundary. */
final class RuntimeLandingSiteTest {

    private static final CelestialObjectKey BODY = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
    private static final Rocket.Footprint FOOTPRINT = new Rocket.Footprint(3, 10, 3);

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void nativeTopBlockHeightIsAlreadyTheLandingBase() throws Exception {
        var search = search(world());
        search.advance();
        assertTrue(search.done());
        assertNull(search.problemKey());
        assertNotNull(search.result());
        assertNull(
            search.result()
                .target());
        assertEquals(
            VehicleSchedule.Context.SURFACE,
            search.result()
                .context());
        assertEquals(
            new VehicleSchedule.SurfaceSite(0, 0, 65, 0, FOOTPRINT),
            search.result()
                .surfaceSite());
    }

    @Test
    void unloadedTerrainCanStillSupplyTheNearestLandingSite() throws Exception {
        var world = world();
        world.loadedTerrain = new HashSet<>();
        world.theChunkProviderServer = new ChunkProviderServer(world, null, null) {

            @Override
            public Chunk loadChunk(int x, int z) {
                world.loadedTerrain.add(new Column(x, z));
                return null;
            }
        };
        assertSite(search(world), 0, 0);
    }

    @Test
    void everyGroundCellMustSupportTheWholeFootprint() throws Exception {
        var world = world();
        world.blocks.put(new BlockPos(1, 64, 1), Blocks.air);
        assertSite(search(world), -1, 0);
    }

    @Test
    void liquidSupportCannotBecomeALandingSite() throws Exception {
        var world = world();
        world.blocks.put(new BlockPos(1, 64, 1), Blocks.lava);
        assertSite(search(world), -1, 0);
    }

    @Test
    void actualBlockCollisionRejectsTheWholeVehicleVolume() throws Exception {
        var world = world();
        world.blocks.put(new BlockPos(0, 66, 0), Blocks.stone);
        assertSite(search(world), -2, 0);
    }

    @Test
    void nonCollidingFluidStillBlocksTheVehicleVolume() throws Exception {
        var world = world();
        world.blocks.put(new BlockPos(0, 65, 0), Blocks.water);
        assertSite(search(world), -2, 0);
    }

    @Test
    void liveEntitiesRejectAnOtherwiseEmptyFootprint() throws Exception {
        var world = world();
        var entity = new Entity(world) {

            @Override
            protected void entityInit() {}

            @Override
            protected void readEntityFromNBT(NBTTagCompound tag) {}

            @Override
            protected void writeEntityToNBT(NBTTagCompound tag) {}
        };
        entity.setPosition(.5, 65, .5);
        world.entities.add(entity);
        assertSite(search(world), -2, 0);
    }

    @Test
    void nearestEuclideanSiteUsesStableCoordinatesForEqualDistances() throws Exception {
        var world = world();
        world.surfaceColumns = Set.of(new Column(-2, 0), new Column(-1, -1), new Column(-1, 1), new Column(1, -1));
        var footprint = new Rocket.Footprint(1, 1, 1);
        var search = new CelestialServerRuntime.LandingSearch(
            CelestialServerRuntime.create(),
            world,
            UUID.randomUUID(),
            BODY,
            footprint,
            0,
            0);
        assertSite(search, -1, -1, footprint);
    }

    @Test
    void searchingAStoredDistantSiteAdvancesWithoutOneBlockingScan() throws Exception {
        var world = world();
        world.surfaceColumns = Set.of(
            new Column(19, -1),
            new Column(19, 0),
            new Column(19, 1),
            new Column(20, -1),
            new Column(20, 0),
            new Column(20, 1),
            new Column(21, -1),
            new Column(21, 0),
            new Column(21, 1));
        var search = search(world);
        search.advance();
        assertFalse(search.done());
        assertNull(search.result());
        assertSite(search, 20, 0);
    }

    private static CelestialServerRuntime.LandingSearch search(TerrainWorld world) {
        return new CelestialServerRuntime.LandingSearch(
            CelestialServerRuntime.create(),
            world,
            UUID.randomUUID(),
            BODY,
            FOOTPRINT,
            0,
            0);
    }

    private static void assertSite(CelestialServerRuntime.LandingSearch search, int x, int z) {
        assertSite(search, x, z, FOOTPRINT);
    }

    private static void assertSite(CelestialServerRuntime.LandingSearch search, int x, int z,
        Rocket.Footprint footprint) {
        for (int i = 0; i < 1000 && !search.done(); i++) search.advance();
        assertTrue(search.done());
        assertNotNull(search.result(), search.problemKey());
        assertEquals(
            new VehicleSchedule.SurfaceSite(0, x, 65, z, footprint),
            search.result()
                .surfaceSite());
    }

    /** Harness exception: use real Minecraft block collision/material logic without chunk or server startup. */
    private static TerrainWorld world() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var world = (TerrainWorld) ((Unsafe) unsafeField.get(null)).allocateInstance(TerrainWorld.class);
        world.blocks = new HashMap<>();
        world.entities = new ArrayList<>();
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        Field collisions = World.class.getDeclaredField("collidingBoundingBoxes");
        collisions.setAccessible(true);
        collisions.set(world, new ArrayList<AxisAlignedBB>());
        return world;
    }

    private record Column(int x, int z) {}

    private static final class TerrainWorld extends WorldServer {

        private Map<BlockPos, Block> blocks;
        private List<Entity> entities;
        private Set<Column> surfaceColumns;
        private Set<Column> loadedTerrain;

        private TerrainWorld() {
            super(null, null, "landing_terrain_test", 0, null, null);
        }

        @Override
        public int getTopSolidOrLiquidBlock(int x, int z) {
            return blockExists(x, 64, z) && (surfaceColumns == null || surfaceColumns.contains(new Column(x, z))) ? 65
                : -1;
        }

        @Override
        public int getActualHeight() {
            return 256;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            boolean floor = y == 64 && (surfaceColumns == null || surfaceColumns.contains(new Column(x, z)));
            return blocks.getOrDefault(new BlockPos(x, y, z), floor ? Blocks.stone : Blocks.air);
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return y >= 0 && y < 256 && (loadedTerrain == null || loadedTerrain.contains(new Column(x >> 4, z >> 4)));
        }

        @Override
        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return true;
        }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side) {
            return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
        }

        @Override
        public List<Entity> getEntitiesWithinAABBExcludingEntity(Entity excluded, AxisAlignedBB bounds) {
            return entities.stream()
                .filter(entity -> entity != excluded && entity.boundingBox.intersectsWith(bounds))
                .toList();
        }
    }
}
