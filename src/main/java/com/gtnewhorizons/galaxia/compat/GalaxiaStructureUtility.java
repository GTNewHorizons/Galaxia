package com.gtnewhorizons.galaxia.compat;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
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
