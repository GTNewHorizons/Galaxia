package com.gtnewhorizons.galaxia.compat.structure;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementNoPlacement;

public interface IExtendedStructureElement<T> extends IStructureElement<T> {

    /**
     * @return a single Block this element represents (never null).
     *         For multi-block elements, returns an arbitrary block from the set.
     */
    Block getValidBlock();

    /**
     * @return all Blocks this element could match. Used by
     *         {@link ArbitraryShapeDefinition.Builder} to register this element
     *         under every matching block key. The default returns
     *         {@code singleton(getValidBlock())}.
     */
    default Collection<Block> getValidBlocks() {
        return Collections.singleton(getValidBlock());
    }

    // ── Static factories ────────────────────────────────────────────────────

    /**
     * Wrap a single-block {@link IStructureElement} with a known valid block.
     */
    static <T> IExtendedStructureElement<T> extend(Block validBlock, IStructureElement<T> inner) {
        return extend(Collections.singleton(validBlock), inner);
    }

    /**
     * Wrap a multi-block {@link IStructureElement} — the element will be
     * registered under every block in {@code validBlocks}.
     */
    static <T> IExtendedStructureElement<T> extend(Collection<Block> validBlocks, IStructureElement<T> inner) {
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return validBlocks.iterator()
                    .next();
            }

            @Override
            public Collection<Block> getValidBlocks() {
                return validBlocks;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                return inner.check(t, world, x, y, z);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                return inner.couldBeValid(t, world, x, y, z, trigger);
            }

            @Override
            public List<String> getDescription(T context) {
                return inner.getDescription(context);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                return inner.spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return inner.placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger,
                IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return inner.survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return inner.getBlocksToPlace(t, world, x, y, z, trigger, env);
            }

            @Override
            public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return inner.survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }

            @Override
            public IStructureElementNoPlacement<T> noPlacement() {
                return inner.noPlacement();
            }

            @Override
            public int getStepA() {
                return inner.getStepA();
            }

            @Override
            public int getStepB() {
                return inner.getStepB();
            }

            @Override
            public int getStepC() {
                return inner.getStepC();
            }

            @Override
            public boolean resetA() {
                return inner.resetA();
            }

            @Override
            public boolean resetB() {
                return inner.resetB();
            }

            @Override
            public boolean resetC() {
                return inner.resetC();
            }

            @Override
            public boolean isNavigating() {
                return inner.isNavigating();
            }
        };
    }

}
