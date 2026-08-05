package com.gtnewhorizons.galaxia.compat;

import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.structure.adders.ITileAdder;
import com.gtnewhorizons.galaxia.compat.structure.IExtendedStructureElement;

public class GalaxiaStructureUtility {

    public static <T> IExtendedStructureElement<T> ofTileAdderCheckHints(ITileAdder<T> iTileAdder, Block hintBlock,
        int hintMeta) {
        if (iTileAdder == null || hintBlock == null) {
            throw new IllegalArgumentException();
        }
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return hintBlock;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                // This used to check if it's a GT tile. Since this is now an standalone mod we no longer do this
                return iTileAdder.apply(t, tileEntity);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block worldBlock = world.getBlock(x, y, z);
                return hintBlock == worldBlock && hintMeta == worldBlock.getDamageValue(world, x, y, z);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return false;
            }
        };
    }

    public static <T> IExtendedStructureElement<T> ofTileAdderCheckHintsAnyMeta(ITileAdder<T> iTileAdder,
        Block hintBlock, int hintMeta) {
        if (iTileAdder == null || hintBlock == null) {
            throw new IllegalArgumentException();
        }
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return hintBlock;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                // This used to check if it's a GT tile. Since this is now an standalone mod we no longer do this
                return couldBeValid(t, world, x, y, z, null) && iTileAdder.apply(t, tileEntity);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block worldBlock = world.getBlock(x, y, z);
                return hintBlock == worldBlock;
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return false;
            }
        };
    }

    public static <T> IExtendedStructureElement<T> ofBlock(Block block, int meta) {
        return IExtendedStructureElement.extend(block, StructureUtility.ofBlock(block, meta));
    }

    public static <T> IExtendedStructureElement<T> ofBlockAnyMeta(Block block) {
        return IExtendedStructureElement.extend(block, StructureUtility.ofBlockAnyMeta(block));
    }

    /**
     * Structure element for a block whose meta must both validate against the tile entity and be recomputed for
     * placement (hints and construction). {@code validMeta} decides whether an already-placed meta is acceptable;
     * {@code placeMeta} computes the meta to place when (re)building the structure.
     */
    public static <T> IExtendedStructureElement<T> ofBlockWithMeta(Block block, BiPredicate<T, Integer> validMeta,
        ToIntFunction<T> placeMeta) {
        if (block == null || validMeta == null || placeMeta == null) {
            throw new IllegalArgumentException();
        }
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return block;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                return block == world.getBlock(x, y, z) && validMeta.test(t, world.getBlockMetadata(x, y, z));
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                return block == world.getBlock(x, y, z);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, block, placeMeta.applyAsInt(t));
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return world.setBlock(x, y, z, block, placeMeta.applyAsInt(t), 2);
            }

            @Override
            public IStructureElement.PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z,
                ItemStack trigger, AutoPlaceEnvironment env) {
                return StructureUtility.survivalPlaceBlock(
                    block,
                    placeMeta.applyAsInt(t),
                    world,
                    x,
                    y,
                    z,
                    env.getSource(),
                    env.getActor(),
                    env.getChatter());
            }
        };
    }

    @FunctionalInterface
    public interface BlockPosConsumer<T> {

        void accept(T t, int x, int y, int z);
    }

    public static <T> IExtendedStructureElement<T> ofBlockPosAdderNoMetaForceCheck(BlockPosConsumer<T> consumer,
        Block block, int hintMeta) {
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return block;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                if (block == world.getBlock(x, y, z)) {
                    consumer.accept(t, x, y, z);
                    return true;
                }
                return false;
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                return check(t, world, x, y, z);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, block, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return false;
            }
        };
    }

}
