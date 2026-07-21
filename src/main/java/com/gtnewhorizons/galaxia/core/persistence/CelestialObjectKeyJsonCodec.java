package com.gtnewhorizons.galaxia.core.persistence;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

/**
 * Shared JSON {@code kind/bodyId/index} codec for {@link CelestialObjectKey}.
 * <p>
 * TLDR: one Key encoding used by both scan-discovery and team-knowledge
 * persistence so the two adapters do not reinvent a second on-disk Key layout.
 */
final class CelestialObjectKeyJsonCodec {

    private CelestialObjectKeyJsonCodec() {}

    static CelestialObjectKeyJson encode(CelestialObjectKey key) {
        CelestialObjectKeyJson json = new CelestialObjectKeyJson();
        json.kind = key.isRegistered() ? "registered" : "minor";
        json.bodyId = key.isRegistered() ? key.registeredBodyId()
            .name()
            : key.minorBodyId()
                .parentBodyId()
                .name();
        if (key.isMinorBody()) json.index = key.minorBodyId()
            .index();
        return json;
    }

    static CelestialObjectKey decode(CelestialObjectKeyJson json) {
        if (json == null || json.kind == null || json.bodyId == null) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed celestial object key");
        }
        CelestialObjectId bodyId = requireEnum(
            CelestialObjectId.class,
            json.bodyId,
            "[PERSIST] LOAD FAILED: unknown celestial object id " + json.bodyId);
        if ("registered".equals(json.kind)) return CelestialObjectKey.registered(bodyId);
        if ("minor".equals(json.kind))
            return CelestialObjectKey.minorBody(new MinorCelestialBodyId(bodyId, json.index));
        throw new IllegalStateException("[PERSIST] LOAD FAILED: unknown celestial object key kind " + json.kind);
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
        String bodyId;
        int index;
    }
}
