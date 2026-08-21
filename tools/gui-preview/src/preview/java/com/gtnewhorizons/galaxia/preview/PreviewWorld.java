package com.gtnewhorizons.galaxia.preview;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import sun.misc.Unsafe;

final class PreviewWorld extends World {

    private Map<Long, TileEntity> tiles;

    private PreviewWorld() {
        super((ISaveHandler) null, "preview", (WorldProvider) null, (WorldSettings) null, (Profiler) null);
    }

    static PreviewWorld create() {
        return create(new TileEntity[0]);
    }

    static PreviewWorld create(TileEntity... tiles) {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            PreviewWorld world = (PreviewWorld) ((Unsafe) field.get(null)).allocateInstance(PreviewWorld.class);
            world.isRemote = true;
            world.tiles = new HashMap<>();
            for (TileEntity tile : tiles) {
                tile.setWorldObj(world);
                world.tiles.put(tileKey(tile.xCoord, tile.yCoord, tile.zCoord), tile);
            }
            return world;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the local preview world", exception);
        }
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return 0;
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return false;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return tiles.get(tileKey(x, y, z));
    }

    @Override
    public void markTileEntityChunkModified(int x, int y, int z, TileEntity tile) {}

    @Override
    public void func_147453_f(int x, int y, int z, Block block) {}

    @Override
    public void markBlockForUpdate(int x, int y, int z) {}

    @Override
    public <T> List<T> getEntitiesWithinAABB(Class<T> type, AxisAlignedBB bounds) {
        EntityPlayer player = PreviewSupport.player();
        return type.isInstance(player) ? List.of(type.cast(player)) : List.of();
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    protected int func_152379_p() {
        return 0;
    }

    @Override
    public Entity getEntityByID(int id) {
        return null;
    }

    private static long tileKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}
