package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAnalyzer;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAnalyzer.Analysis;

import lombok.Getter;

public class RocketBlueprint {

    private final List<RocketPartInstance> parts = new ArrayList<>();
    @Getter
    private String name = "";

    public RocketBlueprint() {}

    public RocketBlueprint copy() {
        RocketBlueprint copy = new RocketBlueprint();
        copy.name = this.name;
        copy.parts.addAll(parts);
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
        return parts.stream()
            .noneMatch(existing -> existing.overlaps(candidate))
            && (parts.isEmpty() || parts.stream()
                .anyMatch(candidate::isAdjacentTo));
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

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public int getWidth() {
        return bounds().width();
    }

    public int getHeight() {
        return bounds().height();
    }

    /** Authored module extents shared by rendering and physical landing clearance. */
    public Bounds bounds() {
        if (parts.isEmpty()) return new Bounds(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var part : parts) {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(
                maxX,
                part.x() + part.def()
                    .width());
            maxY = Math.max(
                maxY,
                part.y() + part.def()
                    .height());
            maxZ = Math.max(maxZ, part.z() + 1);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        public int width() {
            return maxX - minX;
        }

        public int height() {
            return maxY - minY;
        }

        public int depth() {
            return maxZ - minZ;
        }

        public double centerX() {
            return (minX + maxX) * .5;
        }

        public double centerZ() {
            return (minZ + maxZ) * .5;
        }
    }

    public Analysis analyze() {
        return RocketAnalyzer.analyze(this);
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }
}
