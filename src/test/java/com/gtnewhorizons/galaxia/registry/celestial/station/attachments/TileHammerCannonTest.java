package com.gtnewhorizons.galaxia.registry.celestial.station.attachments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class TileHammerCannonTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        TileEntity.addMapping(TileHammerCannon.class, "galaxia:test_hammer_cannon");
    }

    @Test
    void packageExtractionMatchesNbtAndUsesOnlyThisCannonBuffer() {
        ItemStack requested = stack(4, "requested");
        TileHammerCannon chosen = cannonWith(requested, stack(8, "other"));
        TileHammerCannon otherCannon = cannonWith(new ItemStack(Items.iron_ingot, 8, 0));
        IInventory chosenInventory = chosen.getChestInventories()
            .get(0);

        assertTrue(chosen.tryExtractPackage(ItemStackWrapper.of(requested), 4L));

        assertNull(chosenInventory.getStackInSlot(0));
        assertEquals(8, chosenInventory.getStackInSlot(1).stackSize);
        assertEquals(8, stackAt(otherCannon, 0).stackSize);
    }

    @Test
    void packageSnapshotUsesTheSameItemsAsPackageExtraction() {
        ItemStack requested = stack(4, "requested");
        TileHammerCannon cannon = cannonWith(requested, stack(8, "other"));

        assertEquals(4L, cannon.getPackageAmount(ItemStackWrapper.of(requested)));
        assertEquals(
            Map.of(ItemStackWrapper.of(requested), 4L, ItemStackWrapper.of(stack(8, "other")), 8L),
            cannon.getPackageItems());
    }

    @Test
    void packageExtractionIsAllOrNothingWhenChosenBufferIsShort() {
        TileHammerCannon cannon = cannonWith(new ItemStack(Items.iron_ingot, 3, 0));
        ItemStackWrapper resource = ItemStackWrapper.of(stackAt(cannon, 0));

        assertFalse(cannon.tryExtractPackage(resource, 4L));

        assertEquals(3, stackAt(cannon, 0).stackSize);
    }

    @Test
    void routeAndShotCooldownsUseDistinctNbtKeys() {
        TileHammerCannon cannon = new TileHammerCannon();
        cannon.getHammer()
            .setDispatchCooldowns(3, 7);
        NBTTagCompound encoded = new NBTTagCompound();

        cannon.writeToNBT(encoded);

        assertEquals(3, encoded.getInteger("hammerCooldownShot"));
        assertEquals(7, encoded.getInteger("hammerCooldownRoute"));
    }

    private static TileHammerCannon cannonWith(ItemStack... stacks) {
        TileHammerCannon cannon = new TileHammerCannon();
        InventoryBasic inventory = new InventoryBasic("test", false, stacks.length);
        for (int i = 0; i < stacks.length; i++) inventory.setInventorySlotContents(i, stacks[i]);
        cannon.getChestInventories()
            .add(inventory);
        return cannon;
    }

    private static ItemStack stack(int amount, String grade) {
        ItemStack stack = new ItemStack(Items.iron_ingot, amount, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("grade", grade);
        stack.setTagCompound(tag);
        return stack;
    }

    private static ItemStack stackAt(TileHammerCannon cannon, int slot) {
        return cannon.getChestInventories()
            .get(0)
            .getStackInSlot(slot);
    }
}
