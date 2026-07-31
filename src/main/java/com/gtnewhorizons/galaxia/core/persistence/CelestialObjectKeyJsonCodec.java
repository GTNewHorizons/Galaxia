package com.gtnewhorizons.galaxia.core.persistence;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/**
 * The on-disk shape of a {@link CelestialObjectKey}, shared by every persistence adapter.
 * <p>
 * Registered and minor bodies keep separate id fields so a generated minor body never has to be packed into one string
 * that later code would have to parse heuristically.
 */
final class CelestialObjectKeyJsonCodec {

    private CelestialObjectKeyJsonCodec() {}

    static CelestialObjectKeyJson encode(CelestialObjectKey key) {
        if (key == null) return null;
        CelestialObjectKeyJson json = new CelestialObjectKeyJson();
        if (key.isRegistered()) {
            json.kind = "registered";
            json.registeredBodyId = key.registeredBodyId()
                .name();
            return json;
        }
        MinorCelestialBodyId minorId = key.minorBodyId();
        json.kind = "minor";
        json.parentBodyId = minorId.parentBodyId()
            .name();
        json.index = minorId.index();
        return json;
    }

    static CelestialObjectKey decode(CelestialObjectKeyJson json) {
        if (json == null) throw invalidKey("celestialObjectKey", null, "key is required");
        if (json.kind == null || json.kind.isBlank()) {
            throw invalidKey("celestialObjectKey.kind", String.valueOf(json.kind), "kind is required");
        }
        if ("registered".equals(json.kind)) {
            CelestialObjectId registeredId = CelestialObjectId.fromString(json.registeredBodyId);
            if (registeredId == null) {
                throw invalidKey(
                    "celestialObjectKey.registeredBodyId",
                    String.valueOf(json.registeredBodyId),
                    "invalid registeredBodyId");
            }
            return CelestialObjectKey.registered(registeredId);
        }
        if ("minor".equals(json.kind)) {
            CelestialObjectId parentBodyId = CelestialObjectId.fromString(json.parentBodyId);
            if (parentBodyId == null) {
                throw invalidKey(
                    "celestialObjectKey.parentBodyId",
                    String.valueOf(json.parentBodyId),
                    "invalid parentBodyId");
            }
            if (json.index == null) {
                throw invalidKey("celestialObjectKey.index", "null", "index is required");
            }
            try {
                return CelestialObjectKey.minorBody(new MinorCelestialBodyId(parentBodyId, json.index));
            } catch (IllegalArgumentException ex) {
                throw invalidKey("celestialObjectKey.index", String.valueOf(json.index), ex.getMessage());
            }
        }
        throw invalidKey("celestialObjectKey.kind", json.kind, "unknown key kind");
    }

    static IllegalArgumentException invalidKey(String fieldName, String value, String reason) {
        return new IllegalArgumentException(
            "[PERSIST] Invalid persisted celestial key field " + fieldName + "='" + value + "': " + reason);
    }

    static <T extends Enum<T>> T requireEnum(Class<T> cls, String name, String message) {
        try {
            return Enum.valueOf(cls, name);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException(message, ex);
        }
    }

    static final class CelestialObjectKeyJson {

        String kind;
        String registeredBodyId;
        String parentBodyId;
        Integer index;
    }
}
