package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

public final class CelestialObjectKeyCodec {

    private CelestialObjectKeyCodec() {}

    public static NBTTagCompound write(CelestialObjectKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        NBTTagCompound tag = new NBTTagCompound();
        if (key.isRegistered()) {
            tag.setString("kind", "registered");
            tag.setString(
                "id",
                key.registeredBodyId()
                    .name());
            return tag;
        }
        tag.setString("kind", "minor");
        tag.setString(
            "parentBeltId",
            key.minorBodyId()
                .parentBeltId()
                .name());
        tag.setInteger(
            "index",
            key.minorBodyId()
                .index());
        return tag;
    }

    public static CelestialObjectKey read(NBTTagCompound tag) {
        Objects.requireNonNull(tag, "tag cannot be null");
        String kind = tag.getString("kind");
        if ("registered".equals(kind)) {
            return CelestialObjectKey.registered(parseRegisteredId(tag.getString("id"), "id"));
        }
        if ("minor".equals(kind)) {
            return CelestialObjectKey.minorBody(
                new MinorCelestialBodyId(
                    parseRegisteredId(tag.getString("parentBeltId"), "parentBeltId"),
                    tag.getInteger("index")));
        }
        throw new IllegalArgumentException("Unknown celestial object key kind: " + kind);
    }

    private static CelestialObjectId parseRegisteredId(String name, String fieldName) {
        try {
            return CelestialObjectId.valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + fieldName + " celestial object id: " + name, ex);
        }
    }
}
