package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;

/**
 * Everything a built rocket carries on top of its modules: fuel in each tank and items in each cargo hold. Blueprint
 * parts are immutable records, so per-module state lives here keyed by the module's position
 */
public class RocketLoadout {

    private final Map<String, Integer> fuel = new HashMap<>();
    private final Map<String, ItemStack[]> cargo = new HashMap<>();

    public static String slotKey(RocketPartInstance part) {
        return part.x() + ":" + part.y() + ":" + part.z();
    }

    /** Fuel in a tank in mB */
    public int getFuel(RocketPartInstance tank) {
        return fuel.getOrDefault(slotKey(tank), 0);
    }

    public void setFuel(RocketPartInstance tank, int amount) {
        if (amount <= 0) fuel.remove(slotKey(tank));
        else fuel.put(slotKey(tank), amount);
    }

    /**
     * Gets the slots of a cargo hold, creating them on first access
     *
     * @param hold A part whose module is a {@link StoragePartDef}
     *
     * @return The live slot array, or an empty array for other modules
     */
    public ItemStack[] getCargo(RocketPartInstance hold) {
        if (!(hold.def() instanceof StoragePartDef storage)) return new ItemStack[0];
        ItemStack[] slots = cargo.get(slotKey(hold));
        if (slots == null || slots.length != storage.slots()) {
            ItemStack[] resized = new ItemStack[storage.slots()];
            if (slots != null) System.arraycopy(slots, 0, resized, 0, Math.min(slots.length, resized.length));
            slots = resized;
            cargo.put(slotKey(hold), slots);
        }
        return slots;
    }

    /** Total fuel of every tank in a blueprint, in mB */
    public long getTotalFuel(RocketBlueprint blueprint) {
        long total = 0;
        for (RocketPartInstance part : blueprint.getParts()) {
            if (part.def() instanceof FuelTankPartDef) total += getFuel(part);
        }
        return total;
    }

    /** Fuel every tank of a blueprint can hold together, in mB */
    public static long getFuelCapacity(RocketBlueprint blueprint) {
        long total = 0;
        for (RocketPartInstance part : blueprint.getParts()) {
            if (part.def() instanceof FuelTankPartDef tank) total += tank.fuelCapacity();
        }
        return total;
    }

    /** Mass of the fuel in one tank, in kg */
    public double getFuelMass(RocketPartInstance tank) {
        return tank.def() instanceof FuelTankPartDef def ? def.fuel()
            .massOf(getFuel(tank)) : 0;
    }

    /** Mass of the items in one hold, in kg */
    public double getCargoMass(RocketPartInstance hold) {
        double mass = 0;
        for (ItemStack stack : getCargo(hold)) {
            if (stack != null) mass += stack.stackSize * RocketFlightPlanner.CARGO_MASS_PER_ITEM;
        }
        return mass;
    }

    public boolean hasCargo() {
        for (ItemStack[] slots : cargo.values()) {
            for (ItemStack stack : slots) {
                if (stack != null) return true;
            }
        }
        return false;
    }

    /**
     * Drops the state of every module not in the given list, used when stages are discarded
     *
     * @param kept Modules that are still attached
     */
    public void retainOnly(Collection<RocketPartInstance> kept) {
        Set<String> keys = new HashSet<>();
        for (RocketPartInstance part : kept) keys.add(slotKey(part));
        fuel.keySet()
            .retainAll(keys);
        cargo.keySet()
            .retainAll(keys);
    }

    public void clear() {
        fuel.clear();
        cargo.clear();
    }

    public RocketLoadout copy() {
        RocketLoadout copy = new RocketLoadout();
        copy.fuel.putAll(fuel);
        for (Map.Entry<String, ItemStack[]> entry : cargo.entrySet()) {
            ItemStack[] slots = entry.getValue();
            ItemStack[] copied = new ItemStack[slots.length];
            for (int i = 0; i < slots.length; i++) {
                copied[i] = slots[i] == null ? null : slots[i].copy();
            }
            copy.cargo.put(entry.getKey(), copied);
        }
        return copy;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound fuelTag = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : fuel.entrySet()) {
            fuelTag.setInteger(entry.getKey(), entry.getValue());
        }
        tag.setTag("fuel", fuelTag);

        NBTTagCompound cargoTag = new NBTTagCompound();
        for (Map.Entry<String, ItemStack[]> entry : cargo.entrySet()) {
            NBTTagList slots = new NBTTagList();
            ItemStack[] stacks = entry.getValue();
            for (int i = 0; i < stacks.length; i++) {
                if (stacks[i] == null) continue;
                NBTTagCompound slot = stacks[i].writeToNBT(new NBTTagCompound());
                slot.setShort("Slot", (short) i);
                slots.appendTag(slot);
            }
            NBTTagCompound hold = new NBTTagCompound();
            hold.setInteger("size", stacks.length);
            hold.setTag("items", slots);
            cargoTag.setTag(entry.getKey(), hold);
        }
        tag.setTag("cargo", cargoTag);
        return tag;
    }

    public static RocketLoadout deserializeNBT(NBTTagCompound tag) {
        RocketLoadout loadout = new RocketLoadout();
        if (tag == null) return loadout;

        NBTTagCompound fuelTag = tag.getCompoundTag("fuel");
        for (Object key : fuelTag.func_150296_c()) {
            loadout.fuel.put((String) key, fuelTag.getInteger((String) key));
        }

        NBTTagCompound cargoTag = tag.getCompoundTag("cargo");
        for (Object key : cargoTag.func_150296_c()) {
            NBTTagCompound hold = cargoTag.getCompoundTag((String) key);
            ItemStack[] stacks = new ItemStack[hold.getInteger("size")];
            NBTTagList slots = hold.getTagList("items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < slots.tagCount(); i++) {
                NBTTagCompound slot = slots.getCompoundTagAt(i);
                int index = slot.getShort("Slot");
                if (index >= 0 && index < stacks.length) stacks[index] = ItemStack.loadItemStackFromNBT(slot);
            }
            loadout.cargo.put((String) key, stacks);
        }
        return loadout;
    }
}
