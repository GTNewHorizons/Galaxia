package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;

public record RocketPartInstance(IRocketPartDef def, int x, int y, int z, boolean isRadial) {

    public RocketPartInstance copy() {
        return new RocketPartInstance(def, x, y, z, isRadial);
    }

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
        IRocketPartDef def = registry.get(id);
        if (def == null) return null;
        return new RocketPartInstance(
            def,
            tag.getInteger("x"),
            tag.getInteger("y"),
            tag.getInteger("z"),
            tag.getBoolean("radial"));
    }

    public boolean overlaps(RocketPartInstance other) {
        if (this == other) return true;
        if (z != other.z || isRadial != other.isRadial) return false;

        int left1 = x, right1 = x + def.width();
        int left2 = other.x, right2 = other.x + other.def.width();
        int top1 = y, bottom1 = y + def.height();
        int top2 = other.y, bottom2 = other.y + other.def.height();

        return !(right1 <= left2 || right2 <= left1 || bottom1 <= top2 || bottom2 <= top1);
    }

    public boolean isAdjacentTo(RocketPartInstance other) {
        if (z != other.z || isRadial != other.isRadial) return false;
        boolean xOverlap = x < other.x + other.def()
            .width() && x + def().width() > other.x;
        boolean yOverlap = y < other.y + other.def()
            .height() && y + def().height() > other.y;

        boolean touchTop = (y + def().height() == other.y) && xOverlap;
        boolean touchBottom = (other.y + other.def()
            .height() == y) && xOverlap;
        boolean touchRight = (x + def().width() == other.x) && yOverlap;
        boolean touchLeft = (other.x + other.def()
            .width() == x) && yOverlap;

        return touchTop || touchBottom || touchRight || touchLeft;
    }
}
