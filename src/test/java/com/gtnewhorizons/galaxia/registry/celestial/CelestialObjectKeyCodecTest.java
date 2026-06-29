package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialObjectKeyCodecTest {

    @Test
    void roundTripsRegisteredBodyKey() {
        CelestialObjectKey key = CelestialObjectKey.registered(CelestialObjectId.MARS);

        NBTTagCompound tag = CelestialObjectKeyCodec.write(key);

        assertEquals("registered", tag.getString("kind"));
        assertEquals("MARS", tag.getString("id"));
        assertEquals(key, CelestialObjectKeyCodec.read(tag));
    }

    @Test
    void roundTripsMinorBodyKey() {
        CelestialObjectKey key = CelestialObjectKey.minorBody(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 4));

        NBTTagCompound tag = CelestialObjectKeyCodec.write(key);

        assertEquals("minor", tag.getString("kind"));
        assertEquals("FROZEN_BELT", tag.getString("parentBeltId"));
        assertEquals(4, tag.getInteger("index"));
        assertEquals(key, CelestialObjectKeyCodec.read(tag));
    }

    @Test
    void rejectsUnknownKeyKind() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("kind", "unknown");

        assertThrows(IllegalArgumentException.class, () -> CelestialObjectKeyCodec.read(tag));
    }
}
