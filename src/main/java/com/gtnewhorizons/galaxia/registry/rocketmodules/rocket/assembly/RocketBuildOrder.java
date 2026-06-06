package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;

/**
 * Immutable snapshot of a rocket build order created from a design blueprint.
 * Tracks delivery state per part. The parts list is sorted once at creation and
 * never re-ordered, so index-based BitSet delivery is stable across saves.
 */
public class RocketBuildOrder {

    private final List<RocketPartInstance> parts;
    private final BitSet delivered;

    /** Create a new order from a design blueprint (sorts parts deterministically). */
    public RocketBuildOrder(RocketBlueprint design) {
        this.parts = design.getParts()
            .stream()
            .map(RocketPartInstance::copy)
            .sorted(
                Comparator.comparingInt(RocketPartInstance::y)
                    .thenComparingInt(RocketPartInstance::x))
            .toList();
        this.delivered = new BitSet(parts.size());
    }

    /** Private constructor used for NBT deserialization — preserves exact order. */
    private RocketBuildOrder(List<RocketPartInstance> parts, BitSet delivered) {
        this.parts = List.copyOf(parts);
        this.delivered = delivered;
    }

    /**
     * Mark the matching part (by def id + x/y/z) as delivered.
     *
     * @return true if a matching undelivered part was found and marked.
     */
    public boolean markDelivered(RocketPartInstance part) {
        for (int i = 0; i < parts.size(); i++) {
            if (delivered.get(i)) continue;
            RocketPartInstance p = parts.get(i);
            if (p.def()
                .id()
                == part.def()
                    .id()
                && p.x() == part.x()
                && p.y() == part.y()
                && p.z() == part.z()) {
                delivered.set(i);
                return true;
            }
        }
        return false;
    }

    public boolean isComplete() {
        return delivered.cardinality() == parts.size();
    }

    /** Returns the next part that has not yet been delivered, or null if all done. */
    public RocketPartInstance getNextUndelivered() {
        int idx = delivered.nextClearBit(0);
        return idx < parts.size() ? parts.get(idx) : null;
    }

    public double getProgress() {
        return parts.isEmpty() ? 0.0 : (double) delivered.cardinality() / parts.size();
    }

    public int deliveredCount() {
        return delivered.cardinality();
    }

    public int totalCount() {
        return parts.size();
    }

    /** Build a blueprint from only the parts confirmed as delivered so far. */
    public RocketBlueprint createAssembledBlueprint() {
        RocketBlueprint bp = new RocketBlueprint();
        for (int i = 0; i < parts.size(); i++) {
            if (delivered.get(i)) {
                bp.addPart(
                    parts.get(i)
                        .copy());
            }
        }
        return bp;
    }

    public List<RocketPartInstance> getParts() {
        return parts;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList partList = new NBTTagList();
        for (RocketPartInstance p : parts) {
            partList.appendTag(p.serialize());
        }
        tag.setTag("parts", partList);

        // BitSet → compact byte array
        tag.setByteArray("delivered", delivered.toByteArray());
        return tag;
    }

    /** Returns null if tag is null or malformed. */
    public static RocketBuildOrder deserializeNBT(NBTTagCompound tag, RocketPartRegistry registry) {
        if (tag == null) return null;

        NBTTagList partList = tag.getTagList("parts", Constants.NBT.TAG_COMPOUND);
        List<RocketPartInstance> parts = new ArrayList<>(partList.tagCount());
        for (int i = 0; i < partList.tagCount(); i++) {
            RocketPartInstance part = RocketPartInstance.deserialize(partList.getCompoundTagAt(i), registry);
            if (part != null) parts.add(part);
        }

        byte[] deliveredBytes = tag.getByteArray("delivered");
        BitSet delivered = deliveredBytes.length > 0 ? BitSet.valueOf(deliveredBytes) : new BitSet(parts.size());

        return new RocketBuildOrder(parts, delivered);
    }
}
