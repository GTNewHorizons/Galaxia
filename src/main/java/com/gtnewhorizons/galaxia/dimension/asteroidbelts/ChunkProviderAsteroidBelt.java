package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;

import com.gtnewhorizons.galaxia.worldgen.Asteroid;

public class ChunkProviderAsteroidBelt implements IChunkProvider {

    private final Random rand;
    private final World worldObj;
    private final MapGenScatteredFeature scatteredFeatureGenerator = new MapGenScatteredFeature();
    private final Asteroid[] asteroids;

    public ChunkProviderAsteroidBelt(World world, long seed, Asteroid[] asteroids) {
        this.worldObj = world;
        this.rand = new Random(seed);
        this.asteroids = asteroids;
    }

    @Override
    public boolean chunkExists(int p_73149_1_, int p_73149_2_) {
        return true;
    }

    public Chunk provideChunk(int p_73154_1_, int p_73154_2_) {
        this.rand.setSeed((long) p_73154_1_ * 341873128712L + (long) p_73154_2_ * 132897987541L);
        Block[] ablock = new Block[65536];
        byte[] abyte = new byte[65536];

        Chunk chunk = new Chunk(this.worldObj, ablock, abyte, p_73154_1_, p_73154_2_);

        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk loadChunk(int p_73158_1_, int p_73158_2_) {
        return this.provideChunk(p_73158_1_, p_73158_2_);
    }

    @Override
    public void populate(IChunkProvider iChunkProvider, int chunkX, int chunkZ) {
        int x = chunkX * 16;
        int z = chunkZ * 16;

        for (Asteroid asteroid : asteroids) {
            int localX = x + this.rand.nextInt(16) + 8;
            int localY = this.rand.nextInt(176) + 16;
            int localZ = z + this.rand.nextInt(16) + 8;
            asteroid.generate(worldObj, rand, localX, localY, localZ);
        }
    }

    @Override
    public boolean saveChunks(boolean p_73151_1_, IProgressUpdate p_73151_2_) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "RandomAsteroidSource";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType p_73155_1_, int p_73155_2_,
        int p_73155_3_, int p_73155_4_) {
        BiomeGenBase biomegenbase = this.worldObj.getBiomeGenForCoords(p_73155_2_, p_73155_4_);
        return p_73155_1_ == EnumCreatureType.monster
            && this.scatteredFeatureGenerator.func_143030_a(p_73155_2_, p_73155_3_, p_73155_4_)
                ? this.scatteredFeatureGenerator.getScatteredFeatureSpawnList()
                : biomegenbase.getSpawnableList(p_73155_1_);
    }

    @Override
    public ChunkPosition func_147416_a(World p_147416_1_, String p_147416_2_, int p_147416_3_, int p_147416_4_,
        int p_147416_5_) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int p_82695_1_, int p_82695_2_) {

    }

    @Override
    public void saveExtraData() {

    }
}
