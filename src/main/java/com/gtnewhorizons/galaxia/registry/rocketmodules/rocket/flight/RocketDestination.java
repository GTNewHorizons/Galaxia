package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.api.BlockPos;

/**
 * Where a rocket flies to: a specific silo, or just a dimension when no silo is involved
 *
 * @param dimension Target dimension id
 * @param silo      Controller position of the target silo, null to land anywhere in the dimension
 */
public record RocketDestination(int dimension, BlockPos silo) {

    public static final RocketDestination NONE = new RocketDestination(Integer.MIN_VALUE, null);

    public static RocketDestination ofSilo(int dimension, BlockPos silo) {
        return new RocketDestination(dimension, silo);
    }

    public static RocketDestination ofDimension(int dimension) {
        return new RocketDestination(dimension, null);
    }

    public boolean isSet() {
        return dimension != Integer.MIN_VALUE;
    }

    public boolean isSilo() {
        return isSet() && silo != null;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (!isSet()) return tag;
        tag.setInteger("dim", dimension);
        if (silo != null) tag.setTag("silo", silo.toNBT());
        return tag;
    }

    public static RocketDestination deserializeNBT(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("dim")) return NONE;
        BlockPos silo = tag.hasKey("silo") ? BlockPos.fromNBT(tag.getCompoundTag("silo")) : null;
        return new RocketDestination(tag.getInteger("dim"), silo);
    }
}
