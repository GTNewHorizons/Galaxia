package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import net.minecraft.nbt.NBTTagCompound;

public record RocketPartInstance(RocketPartDef def, int x, int y, int z, boolean isRadial) {

    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", def.id());
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setBoolean("radial", isRadial);
        return tag;
    }

    public static RocketPartInstance deserialize(NBTTagCompound tag, RocketPartRegistry registry) {
        int id = tag.getInteger("id");
        RocketPartDef def = registry.get(id);
        if (def == null) return null;
        return new RocketPartInstance(
            def,
            tag.getInteger("x"),
            tag.getInteger("y"),
            tag.getInteger("z"),
            tag.getBoolean("radial")
        );
    }

    public boolean overlaps(RocketPartInstance other) {
        if (this == other) return true;
        int right1 = x + def.getWidthCells();
        int right2 = other.x + other.def.getWidthCells();
        int top1 = y + def.getHeightCells();
        int top2 = other.y + other.def.getHeightCells();
        boolean xOverlap = x < right2 && right1 > other.x;
        boolean yOverlap = y < top2 && top1 > other.y;
        return xOverlap && yOverlap && z == other.z && isRadial == other.isRadial;
    }
}
