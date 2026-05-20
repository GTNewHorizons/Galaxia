package com.gtnewhorizons.galaxia.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.network.BeamEffectPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

final class NetworkManagerTest {

    @Test
    void registerPacketsKeepsDiscriminatorsStableAcrossPhysicalSides() {
        List<Registration> registrations = new ArrayList<>();

        NetworkManager.registerPackets(new NetworkManager.PacketRegistrar() {

            @Override
            public <REQ extends IMessage, REPLY extends IMessage> void registerMessage(
                Class<? extends IMessageHandler<REQ, REPLY>> handler, Class<REQ> packet, int discriminator, Side side) {
                registrations.add(new Registration(handler, packet, discriminator, side));
            }
        });

        assertFalse(registrations.isEmpty());
        assertTrue(
            registrations.stream()
                .anyMatch(registration -> registration.side == Side.SERVER));
        assertTrue(
            registrations.stream()
                .anyMatch(registration -> registration.side == Side.CLIENT));

        Set<Integer> seenDiscriminators = new HashSet<>();

        for (int i = 0; i < registrations.size(); i++) {
            Registration registration = registrations.get(i);
            assertEquals(i, registration.discriminator);
            assertTrue(seenDiscriminators.add(registration.discriminator));
        }

        int firstClientPacket = firstIndexOfSide(registrations, Side.CLIENT);
        int lastServerPacket = lastIndexOfSide(registrations, Side.SERVER);
        assertEquals(lastServerPacket + 1, firstClientPacket);

        for (Registration registration : registrations) {
            assertFalse(
                registration.handler.isAnnotationPresent(SideOnly.class),
                () -> registration.handler.getName() + " must load on both physical sides for network registration");
        }
    }

    @Test
    void beamEffectHandlerKeepsClientOnlyHelpersOutOfDedicatedServerClassShape() throws ReflectiveOperationException {
        SideOnly sideOnly = BeamEffectPacket.Handler.class
            .getDeclaredMethod("spawnBeamParticles", BeamEffectPacket.class)
            .getAnnotation(SideOnly.class);

        assertEquals(Side.CLIENT, sideOnly.value());
    }

    @Test
    void chunkApiBulkChunkMixinKeepsReadAndWriteProtocolTogether() throws ReflectiveOperationException {
        Class<?> bulkChunkMixin = Class
            .forName("com.falsepattern.chunk.internal.mixin.mixins.common.vanilla.S26PacketMapChunkBulkMixin");

        assertEquals(
            bulkChunkMixin,
            bulkChunkMixin.getDeclaredMethod("readPacketData", PacketBuffer.class)
                .getDeclaringClass());
        assertEquals(
            bulkChunkMixin,
            bulkChunkMixin.getDeclaredMethod("writePacketData", PacketBuffer.class)
                .getDeclaringClass());
    }

    private static int firstIndexOfSide(List<Registration> registrations, Side side) {
        for (int i = 0; i < registrations.size(); i++) {
            if (registrations.get(i).side == side) return i;
        }
        throw new AssertionError("No registration for " + side);
    }

    private static int lastIndexOfSide(List<Registration> registrations, Side side) {
        for (int i = registrations.size() - 1; i >= 0; i--) {
            if (registrations.get(i).side == side) return i;
        }
        throw new AssertionError("No registration for " + side);
    }

    private record Registration(Class<? extends IMessageHandler<? extends IMessage, ? extends IMessage>> handler,
        Class<? extends IMessage> packet, int discriminator, Side side) {}
}
