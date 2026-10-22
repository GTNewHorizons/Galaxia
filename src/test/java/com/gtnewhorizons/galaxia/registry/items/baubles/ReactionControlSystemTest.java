package com.gtnewhorizons.galaxia.registry.items.baubles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.mojang.authlib.GameProfile;

import baubles.common.container.InventoryBaubles;
import baubles.common.lib.PlayerHandler;

final class ReactionControlSystemTest {

    private final int[] originalSlots = Galaxia.rcsSlot;

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @AfterEach
    void restoreSlots() {
        Galaxia.rcsSlot = originalSlots;
        PlayerHandler.clearServerPlayerBaubles();
    }

    /** Bug regression: switching one equipped RCS cannot switch another player's equipment. */
    @Test
    void togglingOneEquippedStackDoesNotDisableAnotherPlayersRcs() {
        Galaxia.rcsSlot = new int[] { 0 };
        ItemReactionControlSystem item = new ItemReactionControlSystem();
        EntityPlayer first = player("rcs-first", new ItemStack(item));
        EntityPlayer second = player("rcs-second", new ItemStack(item));

        assertTrue(GalaxiaAPI.hasZeroGMovementCapability(first));
        assertTrue(GalaxiaAPI.hasZeroGMovementCapability(second));

        GalaxiaAPI.setZeroGMovement(first, false);

        assertFalse(GalaxiaAPI.hasZeroGMovementCapability(first));
        assertTrue(GalaxiaAPI.hasZeroGMovementCapability(second), "Another equipped stack must remain enabled");

        GalaxiaAPI.setZeroGMovement(second, true);
        assertFalse(GalaxiaAPI.hasZeroGMovementCapability(first), "The first stack must remain disabled");
    }

    private static EntityPlayer player(String name, ItemStack equipment) {
        EntityPlayer player = new TestPlayer(name);
        InventoryBaubles baubles = new InventoryBaubles(player);
        baubles.stackList = new ItemStack[] { equipment };
        PlayerHandler.setPlayerBaubles(player, baubles);
        return player;
    }

    private static final class TestPlayer extends EntityPlayer {

        private TestPlayer(String name) {
            super(
                new TestWorld(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 64, 0);
        }

        @Override
        public void addChatMessage(IChatComponent message) {}

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return false;
        }
    }

    private static final class TestWorld extends World {

        private TestWorld() {
            super(
                null,
                "rcs",
                new WorldProviderSurface(),
                new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                new Profiler());
        }

        @Override
        public ChunkCoordinates getSpawnPoint() {
            return new ChunkCoordinates(0, 64, 0);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }
}
