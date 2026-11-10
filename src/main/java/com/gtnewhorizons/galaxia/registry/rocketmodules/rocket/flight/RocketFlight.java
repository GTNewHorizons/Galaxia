package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;

import lombok.Getter;

/**
 * A rocket between two silos. It only holds what survived the launch: discarded stages and the cargo in them are
 * already gone
 */
@Getter
public final class RocketFlight {

    private final UUID id;
    private final RocketBlueprint blueprint;
    private final RocketLoadout loadout;
    private final int sourceDimension;
    private final BlockPos sourceSilo;
    private final RocketDestination destination;
    private long arrivalTime;
    private boolean holding;

    public RocketFlight(UUID id, RocketBlueprint blueprint, RocketLoadout loadout, int sourceDimension,
        BlockPos sourceSilo, RocketDestination destination, long arrivalTime) {
        this.id = id;
        this.blueprint = blueprint;
        this.loadout = loadout;
        this.sourceDimension = sourceDimension;
        this.sourceSilo = sourceSilo;
        this.destination = destination;
        this.arrivalTime = arrivalTime;
    }

    /**
     * Keeps the rocket in orbit for a while when the destination cannot take it yet
     *
     * @param retryAt World time of the next landing attempt
     */
    void hold(long retryAt) {
        this.arrivalTime = retryAt;
        this.holding = true;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", id.toString());
        tag.setTag("blueprint", blueprint.serializeNBT());
        tag.setTag("loadout", loadout.serializeNBT());
        tag.setInteger("sourceDim", sourceDimension);
        if (sourceSilo != null) tag.setTag("sourceSilo", sourceSilo.toNBT());
        tag.setTag("destination", destination.serializeNBT());
        tag.setLong("arrival", arrivalTime);
        tag.setBoolean("holding", holding);
        return tag;
    }

    public static RocketFlight deserializeNBT(NBTTagCompound tag) {
        RocketFlight flight = new RocketFlight(
            UUID.fromString(tag.getString("id")),
            RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance()),
            RocketLoadout.deserializeNBT(tag.getCompoundTag("loadout")),
            tag.getInteger("sourceDim"),
            tag.hasKey("sourceSilo") ? BlockPos.fromNBT(tag.getCompoundTag("sourceSilo")) : null,
            RocketDestination.deserializeNBT(tag.getCompoundTag("destination")),
            tag.getLong("arrival"));
        flight.holding = tag.getBoolean("holding");
        return flight;
    }
}
