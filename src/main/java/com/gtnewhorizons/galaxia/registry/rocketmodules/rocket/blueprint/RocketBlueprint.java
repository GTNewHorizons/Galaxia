package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class RocketBlueprint {
    private final List<RocketPartInstance> parts = new ArrayList<>();
    private String name = "";
    private int width = 3;
    private int height = 20;

    public RocketBlueprint() {}

    public void addPart(RocketPartInstance part) {
        parts.add(part);
    }

    public void removePart(RocketPartInstance part) {
        parts.remove(part);
    }

    public void removePartAt(int x, int y) {

        parts.removeIf(part -> {

            int px = part.x();
            int py = part.y();

            int pw = part.def().getWidthCells();
            int ph = part.def().getHeightCells();

            return x >= px &&
                x < px + pw &&
                y >= py &&
                y < py + ph;
        });
    }

    public boolean canPlacePart(RocketPartInstance instance) {

        int width = instance.def().getWidthCells();
        int height = instance.def().getHeightCells();

        if (instance.x() < 0 || instance.y() < 0) {
            return false;
        }

        if (instance.x() + width > this.width) {
            return false;
        }

        if (instance.y() + height > this.height) {
            return false;
        }

        for (RocketPartInstance other : parts) {

            int ox = other.x();
            int oy = other.y();

            int ow = other.def().getWidthCells();
            int oh = other.def().getHeightCells();

            boolean overlap =
                instance.x() < ox + ow &&
                    instance.x() + width > ox &&
                    instance.y() < oy + oh &&
                    instance.y() + height > oy;

            if (overlap) {
                return false;
            }
        }

        return true;
    }

    public RocketPartInstance partAt(int x, int y) {
        for (RocketPartInstance part : parts) {
            int px = part.x();
            int py = part.y();
            int pw = Math.max(1, part.def().getWidthCells());
            int ph = Math.max(1, part.def().getHeightCells());
            boolean inside = x >= px && x < px + pw && y >= py && y < py + ph;
            if (inside) {
                return part;
            }
        }
        return null;
    }

    public boolean isOccupied(int x, int y, int partWidth, int partHeight) {
        return partAt(x, y) != null || overlapsAny(x, y, partWidth, partHeight);
    }

    private boolean overlapsAny(int x, int y, int partWidth, int partHeight) {
        int x2 = x + partWidth;
        int y2 = y + partHeight;

        for (RocketPartInstance existing : parts) {
            int ex = existing.x();
            int ey = existing.y();
            int ew = Math.max(1, existing.def().getWidthCells());
            int eh = Math.max(1, existing.def().getHeightCells());

            int ex2 = ex + ew;
            int ey2 = ey + eh;

            boolean overlap = x < ex2 && x2 > ex && y < ey2 && y2 > ey;
            if (overlap) {
                return true;
            }
        }
        return false;
    }

    public List<RocketPartInstance> getParts() {
        return parts;
    }

    public void clear() {
        parts.clear();
    }

    public RocketBlueprint copy() {
        RocketBlueprint bp = new RocketBlueprint();
        bp.name = name;
        bp.width = width;
        bp.height = height;
        bp.parts.addAll(parts);
        return bp;
    }

    public void replaceWith(RocketBlueprint other) {
        parts.clear();
        parts.addAll(other.parts);
        name = other.name;
        width = other.width;
        height = other.height;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getWidth() {
        return width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);
        tag.setInteger("width", width);
        tag.setInteger("height", height);
        NBTTagList list = new NBTTagList();
        for (RocketPartInstance part : parts) {
            list.appendTag(part.serialize());
        }
        tag.setTag("parts", list);
        return tag;
    }

    public static RocketBlueprint deserialize(NBTTagCompound tag, RocketPartRegistry registry) {
        RocketBlueprint bp = new RocketBlueprint();
        bp.name = tag.getString("name");
        bp.width = tag.getInteger("width");
        bp.height = tag.getInteger("height");
        NBTTagList list = tag.getTagList("parts", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            RocketPartInstance part = RocketPartInstance.deserialize(list.getCompoundTagAt(i), registry);
            if (part != null) {
                bp.parts.add(part);
            }
        }
        return bp;
    }
}
