package com.gtnewhorizons.galaxia.registry.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.DamageSource;
import net.minecraft.util.StringTranslate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class EntityBodyTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
        StringTranslate.inject(EntityBodyTest.class.getResourceAsStream("/assets/galaxia/lang/en_US.lang"));
    }

    /** Death messages identify the chassis, retain Minecraft causes and never impersonate its operator. */
    @Test
    void androidDeathMessagesRetainNamesAndCauses() {
        UUID id = UUID.fromString("12345678-0000-0000-0000-000000000001");
        AndroidBodies.Anchor dock = new AndroidBodies.Anchor(UUID.randomUUID(), 0, 0, 64, 0);
        BodyState state = BodyState.androidAt(0, .5, 64, .5, 20);
        AndroidBodies.AndroidBody named = new AndroidBodies.AndroidBody(
            id,
            UUID.randomUUID(),
            dock,
            state,
            1000,
            AndroidBodies.Chassis.EV,
            -1,
            false,
            "Scout");
        EntityLiving attacker = new EntityLiving(null) {};
        attacker.setCustomNameTag("Zombie");
        assertEquals(
            "Android Scout died",
            AndroidControl.deathMessage(named, DamageSource.generic, null)
                .getUnformattedText());
        assertEquals(
            "Android Scout was slain by Zombie",
            AndroidControl.deathMessage(named, DamageSource.causeMobDamage(attacker), null)
                .getUnformattedText());
        assertEquals(
            "Android Scout self-destructed",
            AndroidControl.deathMessage(named, new DamageSource("galaxia.android.selfDestruct"), null)
                .getUnformattedText());
        AndroidBodies.AndroidBody unnamed = new AndroidBodies.AndroidBody(
            id,
            named.teamId(),
            dock,
            state,
            1000,
            AndroidBodies.Chassis.EV,
            -1,
            false,
            "");
        assertEquals(
            "Android EV 12345678 fell out of the world",
            AndroidControl.deathMessage(unnamed, DamageSource.outOfWorld, null)
                .getUnformattedText());
    }

    /** Cleanup cannot turn a live projection into a dead authoritative body by writing its health. */
    @Test
    void administrativeHealthResetPreservesTheLastLivingHealth() {
        EntityBody body = new EntityBody(null, UUID.randomUUID(), null);
        body.setHealth(7);
        body.setHealth(0);
        assertEquals(7, body.getHealth());
        body.setHealth(-1);
        assertEquals(7, body.getHealth());
        body.setHealth(9);
        assertEquals(9, body.getHealth());
    }

    /** Direct death callbacks remove only a projection and require no logical body destruction. */
    @Test
    void administrativeDeathDiscardsOnlyTheProjection() {
        EntityBody body = new EntityBody(null, UUID.randomUUID(), null);
        body.onDeath(DamageSource.generic);
        assertTrue(body.isDead);
        assertEquals(body.getMaxHealth(), body.getHealth());
    }

    /** Product contract: the biological body identifies its owner using the requested possessive name. */
    @Test
    void biologicalBodyUsesItsOperatorsName() {
        GameProfile operator = new GameProfile(UUID.randomUUID(), "Pxx500");
        EntityBody body = new EntityBody(null, UUID.randomUUID(), operator);
        assertEquals("Pxx500's Body", body.getCommandSenderName());
    }

    /** Integration contract: a remote biological projection retains the owner's identity and signed skin properties. */
    @Test
    void spawnRetainsTheBiologicalOperatorsSkinProfile() {
        GameProfile operator = new GameProfile(UUID.randomUUID(), "Pxx500");
        operator.getProperties()
            .put("textures", new Property("textures", "signed-skin-payload", "skin-signature"));
        UUID bodyId = UUID.randomUUID();
        EntityBody source = new EntityBody(null, bodyId, operator);
        EntityBody remote = new EntityBody(null);
        ByteBuf payload = Unpooled.buffer();
        try {
            source.writeSpawnData(payload);
            remote.readSpawnData(payload);
            assertEquals(bodyId, remote.bodyId());
            assertEquals(operator.getId(), remote.operatorId());
            assertEquals("Pxx500's Body", remote.getCommandSenderName());
            Property skin = remote.operatorProfile()
                .getProperties()
                .get("textures")
                .iterator()
                .next();
            assertEquals("signed-skin-payload", skin.getValue());
            assertEquals("skin-signature", skin.getSignature());
            assertFalse(payload.isReadable());
        } finally {
            payload.release();
        }
    }

    /** Integration contract: manufactured projections have their own identity without a biological owner. */
    @Test
    void spawnRetainsAnAndroidWithoutAnOperatorProfile() {
        UUID bodyId = UUID.randomUUID();
        EntityBody source = new EntityBody(null, bodyId, null);
        EntityBody remote = new EntityBody(null);
        ByteBuf payload = Unpooled.buffer();
        try {
            source.writeSpawnData(payload);
            remote.readSpawnData(payload);
            assertEquals(bodyId, remote.bodyId());
            assertNull(remote.operatorId());
            assertNull(remote.operatorProfile());
            assertFalse(remote.isBiological());
            assertFalse(payload.isReadable());
        } finally {
            payload.release();
        }
    }

}
