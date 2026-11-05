package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

/**
 * Record class to hold modules currently in transit in Gantry System
 *
 * @param module      The rocket module being moved
 * @param destination The terminal endpoint of the journey, null if it no longer exists
 * @param trail       The last moves of the module, newest first, so clients can draw hooks that trail behind it
 */

public record TransitModule(RocketPartInstance module, TileEntityGantryTerminal destination, byte[] trail) {

    /** Moves remembered in {@link #trail} */
    public static final int TRAIL_LENGTH = 8;

    public TransitModule(RocketPartInstance module, TileEntityGantryTerminal destination) {
        this(module, destination, new byte[0]);
    }

    /**
     * Same module heading somewhere else
     *
     * @param newDestination The new terminal endpoint
     *
     * @return A new transit record
     */
    public TransitModule redirect(TileEntityGantryTerminal newDestination) {
        return new TransitModule(module, newDestination, trail);
    }

    /**
     * Same module after one more step along the rails
     *
     * @param move The step just taken
     *
     * @return A new transit record
     */
    public TransitModule moved(ForgeDirection move) {
        byte[] next = new byte[Math.min(TRAIL_LENGTH, trail.length + 1)];
        next[0] = (byte) move.ordinal();
        System.arraycopy(trail, 0, next, 1, next.length - 1);
        return new TransitModule(module, destination, next);
    }

    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("module", module.serialize());
        if (destination != null) {
            tag.setInteger("destX", destination.xCoord);
            tag.setInteger("destY", destination.yCoord);
            tag.setInteger("destZ", destination.zCoord);
        }
        return tag;
    }

    /**
     * Restores a saved module, the destination is looked up in the given world
     *
     * @param world The world the gantry is in
     * @param tag   The saved record
     *
     * @return The record, or null if the module type no longer exists
     */
    public static TransitModule deserialize(World world, NBTTagCompound tag) {
        RocketPartInstance module = RocketPartInstance
            .deserialize(tag.getCompoundTag("module"), RocketPartRegistry.instance());
        if (module == null) return null;

        TileEntityGantryTerminal destination = null;
        if (tag.hasKey("destX")) {
            TileEntity te = world
                .getTileEntity(tag.getInteger("destX"), tag.getInteger("destY"), tag.getInteger("destZ"));
            if (te instanceof TileEntityGantryTerminal terminal) destination = terminal;
        }
        return new TransitModule(module, destination);
    }

    /**
     * Custom method to return a more useful string format
     */
    @Override
    public String toString() {
        return String.format("TransitModule: Module: {%s}, Destination: {%s}", module(), destination);
    }
}
