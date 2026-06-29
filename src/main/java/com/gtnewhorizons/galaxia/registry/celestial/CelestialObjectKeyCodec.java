package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/**
 * NBT codec for celestial object keys used by network/persistence callers that
 * need a typed body reference instead of a lossy string.
 */
public final class CelestialObjectKeyCodec {

    private CelestialObjectKeyCodec() {}

    public static NBTTagCompound write(CelestialObjectKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        NBTTagCompound tag = new NBTTagCompound();
        // The explicit discriminator keeps registered enum bodies and generated
        // minor bodies from sharing an ambiguous "id" field.
        if (key.isRegistered()) {
            tag.setString("kind", "registered");
            tag.setString("id", key.registeredBodyId()
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
        // Invalid data is intentionally loud: a bad body key can attach assets to
        // the wrong place, which is worse than failing the load or packet decode.
        if ("registered".equals(kind)) {
            return CelestialObjectKey.registered(parseRegisteredId(tag.getString("id"), "id"));
        }
        if ("minor".equals(kind)) {
            return CelestialObjectKey.minorBody(
                new MinorCelestialBodyId(parseRegisteredId(tag.getString("parentBeltId"), "parentBeltId"), tag.getInteger("index")));
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
