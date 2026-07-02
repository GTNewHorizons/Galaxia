package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/**
 * Stable identity for anything that can own assets or appear as a starmap body.
 *
 * Registered bodies keep using the enum-backed id. Minor bodies are derived from
 * a registered parent container plus a stable slot index, so generated asteroids
 * can be addressed without adding infinite enum values.
 */
public record CelestialObjectKey(CelestialObjectId registeredBodyId, MinorCelestialBodyId minorBodyId) {

    public CelestialObjectKey {
        if ((registeredBodyId == null) == (minorBodyId == null)) {
            throw new IllegalStateException("Celestial object key must target exactly one body");
        }
    }

    public static CelestialObjectKey registered(CelestialObjectId id) {
        return new CelestialObjectKey(Objects.requireNonNull(id, "id cannot be null"), null);
    }

    public static CelestialObjectKey minorBody(MinorCelestialBodyId id) {
        return new CelestialObjectKey(null, Objects.requireNonNull(id, "id cannot be null"));
    }

    public boolean isRegistered() {
        return registeredBodyId != null;
    }

    public boolean isMinorBody() {
        return minorBodyId != null;
    }

    public CelestialObjectId requireRegisteredBodyId() {
        if (registeredBodyId == null) {
            throw new IllegalStateException("Expected registered celestial object key");
        }
        return registeredBodyId;
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        // The explicit discriminator keeps registered enum bodies and generated
        // minor bodies from sharing an ambiguous "id" field.
        if (isRegistered()) {
            tag.setString("kind", "registered");
            tag.setString("id", registeredBodyId.name());
            return tag;
        }
        tag.setString("kind", "minor");
        tag.setString(
            "parentBeltId",
            minorBodyId.parentBeltId()
                .name());
        tag.setInteger("index", minorBodyId.index());
        return tag;
    }

    public static CelestialObjectKey fromNbt(NBTTagCompound tag) {
        Objects.requireNonNull(tag, "tag cannot be null");
        String kind = tag.getString("kind");
        // Invalid data is intentionally loud: a bad body key can attach assets to
        // the wrong place, which is worse than failing the load or packet decode.
        if ("registered".equals(kind)) {
            return registered(parseRegisteredId(tag.getString("id"), "id"));
        }
        if ("minor".equals(kind)) {
            return minorBody(
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
