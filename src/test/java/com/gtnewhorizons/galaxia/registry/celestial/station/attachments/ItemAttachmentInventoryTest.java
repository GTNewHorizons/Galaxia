package com.gtnewhorizons.galaxia.registry.celestial.station.attachments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IInventoryStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ItemAttachmentInventoryTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void rejectedItemIsHiddenFromRoutingButRemainsVisibleInStorage() {
        InventoryBasic storage = new InventoryBasic("test", false, 1);
        storage.setInventorySlotContents(0, new ItemStack(Items.stick, 4));

        ResourceFilter<ItemStackWrapper> filter = ResourceFilter.forItems();
        filter.add(ItemStackWrapper.of(new ItemStack(Items.diamond)));
        ItemAttachmentInventory<Object> inventory = new ItemAttachmentInventory<>(
            new TestItemStorageHandler(storage, filter),
            new Object());
        ItemStackWrapper rejectedItem = ItemStackWrapper.of(new ItemStack(Items.stick));

        assertEquals(
            4L,
            inventory.aggregatedItems()
                .get(rejectedItem));
        assertEquals(0L, inventory.getFreeItemSpace(rejectedItem));
        assertEquals(0L, inventory.updateItems(rejectedItem, 2L));
        assertEquals(0L, inventory.updateItems(rejectedItem, -2L));
        assertEquals(
            4L,
            inventory.aggregatedItems()
                .get(rejectedItem));
    }

    @Test
    void acceptedItemContinuesToRouteThroughAttachmentStorage() {
        InventoryBasic storage = new InventoryBasic("test", false, 1);
        ResourceFilter<ItemStackWrapper> filter = ResourceFilter.forItems();
        ItemStackWrapper acceptedItem = ItemStackWrapper.of(new ItemStack(Items.diamond));
        filter.add(acceptedItem);
        ItemAttachmentInventory<Object> inventory = new ItemAttachmentInventory<>(
            new TestItemStorageHandler(storage, filter),
            new Object());

        assertEquals(64L, inventory.getFreeItemSpace(acceptedItem));
        assertEquals(3L, inventory.updateItems(acceptedItem, 3L));
        assertEquals(
            3L,
            inventory.aggregatedItems()
                .get(acceptedItem));
        assertEquals(2L, inventory.updateItems(acceptedItem, -2L));
        assertEquals(
            1L,
            inventory.aggregatedItems()
                .get(acceptedItem));
    }

    @Test
    void inventoryRootDoesNotTreatChildCapacityAsItsOwnStorage() {
        InventoryBasic storage = new InventoryBasic("test", false, 1);
        ResourceFilter<ItemStackWrapper> filter = ResourceFilter.forItems();
        filter.add(ItemStackWrapper.of(new ItemStack(Items.stick)));
        ItemAttachmentInventory<Object> child = new ItemAttachmentInventory<>(
            new TestItemStorageHandler(storage, filter),
            new Object());
        IDistributedInventory root = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            Buildable.Status.OPERATIONAL) {

            @Override
            public List<IDistributedInventory> getChildren() {
                return List.of(child);
            }
        };

        assertEquals(0L, root.updateItems(ItemStackWrapper.of(new ItemStack(Items.diamond)), 1L));
        assertEquals(0L, root.totalItemsStored());
    }

    private record TestItemStorageHandler(IInventory storage, ResourceFilter<ItemStackWrapper> filter)
        implements IInventoryStorageHandler<Object> {

        @Override
        public ResourceFilter<ItemStackWrapper> getItemFilter(Object attachment) {
            return filter;
        }

        @Override
        public List<IInventory> getInventories(Object attachment) {
            return List.of(storage);
        }

        @Override
        public BlockPos getPosition(Object attachment) {
            return new BlockPos(0, 0, 0);
        }

        @Override
        public void tick(Object attachment) {}

        @Override
        public boolean isReady(Object attachment) {
            return true;
        }

        @Override
        public void onAttached(Object attachment, StationGraph graph) {}

        @Override
        public void onDetached(Object attachment, StationGraph graph) {}

        @Override
        public void markDirty(Object attachment) {}
    }
}
