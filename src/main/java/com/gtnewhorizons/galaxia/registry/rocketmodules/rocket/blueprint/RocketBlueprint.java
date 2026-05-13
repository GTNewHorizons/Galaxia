package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RocketBlueprint {

    private final List<RocketPartInstance> parts = new ArrayList<>();
    private String name = "";
    private int width = 7;
    private int height = 12;

    public RocketBlueprint() {}

    public RocketBlueprint(int width, int height) {
        this.width = Math.max(3, width);
        this.height = Math.max(5, height);
    }

    public RocketBlueprint copy() {
        RocketBlueprint copy = new RocketBlueprint(width, height);
        copy.name = this.name;
        for (RocketPartInstance part : parts) {
            copy.parts.add(new RocketPartInstance(part.def(), part.x(), part.y(), part.z(), part.isRadial()));
        }
        return copy;
    }

    public void replaceWith(RocketBlueprint other) {
        this.parts.clear();
        this.parts.addAll(other.parts);
        this.name = other.name;
        this.width = other.width;
        this.height = other.height;
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
        if (candidate.x() < 0 || candidate.y() < 0 ||
            candidate.x() + candidate.def().getWidthCells() > width ||
            candidate.y() + candidate.def().getHeightCells() > height) {
            return false;
        }

        for (RocketPartInstance existing : parts) {
            if (existing.overlaps(candidate)) {
                return false;
            }
        }
        return true;
    }

    public RocketPartInstance partAt(int x, int y, int z) {
        for (RocketPartInstance part : parts) {
            if (part.x() == x && part.y() == y && part.z() == z) {
                return part;
            }
        }
        return null;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);
        tag.setInteger("width", width);
        tag.setInteger("height", height);

        NBTTagList partList = new NBTTagList();
        for (RocketPartInstance part : parts) {
            partList.appendTag(part.serialize());
        }
        tag.setTag("parts", partList);
        return tag;
    }

    public static RocketBlueprint deserializeNBT(NBTTagCompound tag, RocketPartRegistry registry) {
        if (tag == null) return new RocketBlueprint();

        RocketBlueprint bp = new RocketBlueprint(
            tag.getInteger("width"),
            tag.getInteger("height")
        );
        bp.name = tag.getString("name");

        NBTTagList list = tag.getTagList("parts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            RocketPartInstance part = RocketPartInstance.deserialize(list.getCompoundTagAt(i), registry);
            if (part != null) {
                bp.parts.add(part);
            }
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
        this.name = name == null ? "" : name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public RocketAssembly analyze() {
        return RocketAssembly.fromBlueprint(this);
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }
}
