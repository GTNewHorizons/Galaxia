package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAnalyzer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;

public class RocketBlueprint {

    private final List<RocketPartInstance> parts = new ArrayList<>();
    private String name = "";

    public RocketBlueprint() {}

    public RocketBlueprint copy() {
        RocketBlueprint copy = new RocketBlueprint();
        copy.name = this.name;
        for (RocketPartInstance part : parts) {
            copy.parts.add(part.copy());
        }
        return copy;
    }

    public void clear() {
        parts.clear();
        name = "";
    }

    public boolean addPart(RocketPartInstance part) {
        if (!canPlacePart(part)) return false;
        parts.add(part);
        return true;
    }

    public void removePartAt(int x, int y, int z) {
        parts.removeIf(p -> p.x() == x && p.y() == y && p.z() == z);
    }

    public boolean canPlacePart(RocketPartInstance candidate) {
        for (RocketPartInstance existing : parts) {
            if (existing.overlaps(candidate)) {
                return false;
            }
        }

        if (parts.isEmpty()) {
            return true;
        }

        for (RocketPartInstance existing : parts) {
            if (candidate.isAdjacentTo(existing)) {
                return true;
            }
        }

        return false;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);

        NBTTagList partList = new NBTTagList();
        for (RocketPartInstance part : parts) {
            partList.appendTag(part.serialize());
        }
        tag.setTag("parts", partList);
        return tag;
    }

    public static RocketBlueprint deserializeNBT(NBTTagCompound tag, RocketPartRegistry registry) {
        if (tag == null) return new RocketBlueprint();

        RocketBlueprint bp = new RocketBlueprint();
        bp.name = tag.getString("name");

        NBTTagList list = tag.getTagList("parts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            RocketPartInstance part = RocketPartInstance.deserialize(list.getCompoundTagAt(i), registry);
            if (part != null) bp.parts.add(part);
        }
        return bp;
    }

    public List<RocketPartInstance> getParts() {
        return Collections.unmodifiableList(parts);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public int getWidth() {
        int max = 0;
        for (RocketPartInstance part : parts) {
            max = Math.max(
                max,
                part.x() + part.def()
                    .width());
        }
        return max;
    }

    public int getHeight() {
        int max = 0;
        for (RocketPartInstance part : parts) {
            max = Math.max(
                max,
                part.y() + part.def()
                    .height());
        }
        return max;
    }

    public RocketAssembly analyze() {
        return RocketAnalyzer.analyze(this);
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }
}
