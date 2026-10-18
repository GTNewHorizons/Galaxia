package com.gtnewhorizons.galaxia.core.state;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/** Shared NBT identity for registered and generated celestial bodies. */
public final class CelestialObjectKeyState {

    private CelestialObjectKeyState() {}

    @Nonnull
    public static NBTTagCompound encode(@Nonnull CelestialObjectKey key) {
        NBTTagCompound out = new NBTTagCompound();
        if (key.isRegistered()) {
            out.setString("type", "registered");
            out.setString(
                "body",
                key.registeredBodyId()
                    .name());
        } else {
            out.setString("type", "minor");
            out.setString(
                "parent",
                key.minorBodyId()
                    .parentBodyId()
                    .name());
            out.setInteger(
                "index",
                key.minorBodyId()
                    .index());
        }
        return out;
    }

    @Nonnull
    public static CelestialObjectKey decode(@Nonnull NBTTagCompound encoded) {
        return decode(new NbtReader(encoded, "celestialObjectKey"));
    }

    @Nonnull
    public static CelestialObjectKey decode(@Nonnull NbtReader in) {
        String type = in.string("type");
        if ("registered".equals(type)) {
            return CelestialObjectKey.registered(in.enumValue(CelestialObjectId.class, "body"));
        }
        if ("minor".equals(type)) {
            CelestialObjectId parent = in.enumValue(CelestialObjectId.class, "parent");
            int index = in.integer("index");
            try {
                return CelestialObjectKey.minorBody(new MinorCelestialBodyId(parent, index));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("[PERSIST] " + in.path() + ": invalid minor celestial body key", ex);
            }
        }
        throw new IllegalStateException("[PERSIST] " + in.path() + ".type: unknown celestial object key type " + type);
    }
}
