package com.gtnewhorizons.galaxia.registry.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.Chassis;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AndroidBodiesTest {

    private static final UUID TEAM = new UUID(10L, 11L);
    private static final UUID OPERATOR = new UUID(20L, 21L);

    @TempDir
    File directory;

    @BeforeAll
    static void initItems() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    /** Product contract: a team name belongs to the body and survives physical updates and a world reopen. */
    @Test
    void namedBodyRetainsItsNameAndRejectsAnotherTeamsRename() throws Exception {
        File checkpoint = new File(directory, "named-bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(checkpoint);
        AndroidBodies.Anchor dock = new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20);
        AndroidBodies.AndroidBody body = manufacture(
            bodies,
            TEAM,
            dock,
            BodyState.androidAt(7, 12.5, 64, 20.5, 20),
            Chassis.EV,
            100);
        assertThrows(IllegalStateException.class, () -> bodies.rename(UUID.randomUUID(), body.id(), "Stolen"));
        bodies.rename(TEAM, body.id(), "  Mars Scout  ");
        bodies.setBattery(body.id(), 75);
        bodies.observeBody(
            body.id(),
            body.state()
                .withHealth(17));
        bodies.checkpoint();
        assertEquals(
            "Mars Scout",
            AndroidBodies.open(checkpoint)
                .body(body.id())
                .orElseThrow()
                .name());
    }

    /** Product contract: a manufactured body's identity and dock survive reopening the world checkpoint. */
    @Test
    void manufacturedBodyRetainsItsIdentityAndDockAcrossReopening() throws Exception {
        File checkpoint = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(checkpoint);
        UUID team = UUID.randomUUID();
        AndroidBodies.Anchor dock = new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20);
        AndroidBodies.AndroidBody created = manufacture(
            bodies,
            team,
            dock,
            BodyState.androidAt(7, 12.5, 64, 20.5, 20),
            Chassis.EV,
            100);

        assertTrue(
            bodies.body(created.id())
                .isPresent(),
            "Manufacture must register one authoritative body");
        AndroidBodies restored = AndroidBodies.open(checkpoint);
        AndroidBodies.AndroidBody loaded = restored.body(created.id())
            .orElseThrow();
        assertEquals(created.id(), loaded.id());
        assertEquals(team, loaded.teamId());
        assertEquals(dock, loaded.dock());
        assertEquals(100, loaded.battery());
        assertEquals(
            1,
            restored.bodies(team)
                .size());
    }

    /** Bug regression: replaying a completed dock job cannot manufacture another body, even after destruction. */
    @Test
    void replayedManufactureCannotDuplicateOrRecreateItsBody() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        UUID operation = new UUID(70L, 71L);
        AndroidBodies.Anchor dock = new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20);
        BodyState initial = BodyState.androidAt(7, 12.5, 64, 20.5, 20);

        UUID first = bodies.manufacture(operation, TEAM, dock, initial, Chassis.EV, 100);
        UUID replay = bodies.manufacture(operation, TEAM, dock, initial, Chassis.EV, 100);

        assertEquals(
            1,
            bodies.allBodies()
                .size(),
            "A replayed manufacture job must not create another body");
        assertEquals(operation, first);
        assertEquals(first, replay);
        assertThrows(
            IllegalStateException.class,
            () -> bodies.manufacture(operation, UUID.randomUUID(), dock, initial, Chassis.EV, 100));
        assertThrows(
            IllegalStateException.class,
            () -> bodies.manufacture(operation, TEAM, dock, initial, Chassis.LUV, 100));
        bodies.destroy(first);
        AndroidBodies restored = AndroidBodies.open(file);
        assertEquals(operation, restored.manufacture(operation, TEAM, dock, initial, Chassis.EV, 100));
        assertTrue(
            restored.allBodies()
                .isEmpty());
        assertThrows(
            IllegalStateException.class,
            () -> restored.manufacture(
                operation,
                TEAM,
                new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20),
                initial,
                Chassis.EV,
                100));
        assertThrows(
            IllegalStateException.class,
            () -> restored.manufacture(operation, TEAM, dock, initial, Chassis.UV, 100));
    }

    /** Product contract: each chassis retains its funded charge and tier through body updates and world reopening. */
    @Test
    void chassisAndFundedChargeSurviveUpdatesAndCheckpointRoundTrips() throws Exception {
        for (Chassis chassis : Chassis.values()) {
            File file = new File(directory, chassis.name() + "-bodies.dat");
            AndroidBodies bodies = AndroidBodies.open(file);
            AndroidBodies.Anchor dock = new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20);
            long initialCharge = chassis.capacity() / 3;
            AndroidBodies.AndroidBody created = manufacture(
                bodies,
                TEAM,
                dock,
                BodyState.androidAt(7, 12.5, 64, 20.5, 20),
                chassis,
                initialCharge);
            assertEquals(chassis, created.chassis());
            assertEquals(chassis.capacity(), created.capacity());
            assertEquals(initialCharge, created.battery(), "Manufacture must retain only the funded battery charge");

            bodies = AndroidBodies.open(file);
            AndroidBodies.AndroidBody loaded = bodies.body(created.id())
                .orElseThrow();
            assertEquals(chassis, loaded.chassis());
            assertEquals(initialCharge, loaded.battery());
            bodies.rename(TEAM, created.id(), "Surveyor");
            bodies.observeBody(
                created.id(),
                loaded.state()
                    .withHealth(8));
            bodies.repair(created.id(), 12);
            bodies.setBattery(created.id(), initialCharge - 1);
            bodies.activate(OPERATOR, TEAM, created.id(), capsule(), biological());
            BodyState observed = loaded.state()
                .withHealth(10);
            bodies.observeOperator(OPERATOR, observed);
            bodies.deactivate(OPERATOR, observed);
            bodies.warnPortalTransfer(created.id(), 60);
            bodies.setDestructionTicks(created.id(), 40);
            assertTrue(bodies.cancelPortalWarning(created.id()));
            bodies.armDestruction(created.id(), 60);
            bodies.setDestructionTicks(created.id(), 40);
            UUID survivingTeam = new UUID(40L, 41L);
            bodies.mergeTeams(TEAM, survivingTeam);
            bodies.checkpoint();

            AndroidBodies.AndroidBody saved = AndroidBodies.open(file)
                .body(created.id())
                .orElseThrow();
            assertEquals(chassis, saved.chassis(), "Physical updates must not replace the chassis tier");
            assertEquals(chassis.capacity(), saved.capacity());
            assertEquals(initialCharge - 1, saved.battery(), "Physical updates must not refill the battery");
            assertEquals(survivingTeam, saved.teamId());
            assertEquals("Surveyor", saved.name());
            assertEquals(
                observed.write(),
                saved.state()
                    .write());
            assertEquals(40, saved.destructionTicks());
        }
    }

    /** Product contract: manufacture cannot mint charge outside the native chassis battery's capacity. */
    @Test
    void manufactureRejectsInvalidChargeWithoutPublishingABody() throws Exception {
        AndroidBodies bodies = AndroidBodies.open(new File(directory, "invalid-charge.dat"));
        AndroidBodies.Anchor dock = new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20);
        BodyState initial = BodyState.androidAt(7, 12.5, 64, 20.5, 20);
        for (Chassis chassis : Chassis.values()) {
            assertThrows(IllegalArgumentException.class, () -> manufacture(bodies, TEAM, dock, initial, chassis, -1));
            assertThrows(
                IllegalArgumentException.class,
                () -> manufacture(bodies, TEAM, dock, initial, chassis, chassis.capacity() + 1));
        }
        assertTrue(
            bodies.allBodies()
                .isEmpty());
        AndroidBodies.AndroidBody empty = manufacture(bodies, TEAM, dock, initial, Chassis.EV, 0);
        assertEquals(0, empty.battery());
        assertThrows(IllegalArgumentException.class, () -> bodies.setBattery(empty.id(), empty.capacity() + 1));
        assertEquals(
            0,
            bodies.body(empty.id())
                .orElseThrow()
                .battery());
    }

    /** Product contract: capsule hazards affect a distinct biological body and survive return from remote control. */
    @Test
    void capsuleHazardsKeepBiologicalIdentityAndStateSeparateFromTheAndroid() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody android = manufacture(bodies);
        bodies.activate(OPERATOR, TEAM, android.id(), capsule(), biological());
        bodies.deactivate(OPERATOR, android.state());
        UUID biologicalId = bodies.biological(OPERATOR)
            .orElseThrow()
            .id();
        bodies.activate(OPERATOR, TEAM, android.id(), capsule(), biological());
        assertEquals(
            biologicalId,
            bodies.biological(OPERATOR)
                .orElseThrow()
                .id());
        assertNotEquals(OPERATOR, biologicalId);
        assertNotEquals(android.id(), biologicalId);

        bodies.observeBiological(OPERATOR, biological().withHealth(9));
        assertEquals(
            9,
            bodies.biological(OPERATOR)
                .orElseThrow()
                .state()
                .health());
        bodies.observeOperator(
            OPERATOR,
            android.state()
                .withHealth(4));
        BodyState returned = bodies.deactivate(
            OPERATOR,
            android.state()
                .withHealth(4));

        assertEquals(9, returned.health());
        assertEquals(
            4,
            bodies.body(android.id())
                .orElseThrow()
                .state()
                .health());
        assertThrows(IllegalStateException.class, () -> bodies.observeBiological(OPERATOR, returned));
        bodies.activate(OPERATOR, TEAM, android.id(), capsule(), returned);
        bodies.deactivate(
            OPERATOR,
            bodies.body(android.id())
                .orElseThrow()
                .state());
        AndroidBodies restored = AndroidBodies.open(file);
        assertEquals(
            biologicalId,
            restored.biological(OPERATOR)
                .orElseThrow()
                .id());
        assertEquals(
            9,
            restored.biological(OPERATOR)
                .orElseThrow()
                .state()
                .health());
    }

    /** Product contract: one operator and one team-owned android can belong to only one live session. */
    @Test
    void controlIsExclusiveAndRejectsAnotherTeamsBody() throws Exception {
        AndroidBodies bodies = AndroidBodies.open(new File(directory, "bodies.dat"));
        AndroidBodies.AndroidBody first = manufacture(bodies);
        AndroidBodies.AndroidBody second = manufacture(bodies);
        UUID otherOperator = new UUID(30L, 31L);
        assertThrows(
            IllegalStateException.class,
            () -> bodies.activate(OPERATOR, UUID.randomUUID(), first.id(), capsule(), biological()));

        AndroidBodies.Session active = bodies.activate(OPERATOR, TEAM, first.id(), capsule(), biological());

        assertEquals(first.id(), active.bodyId());
        assertEquals(
            active,
            bodies.session(OPERATOR)
                .orElseThrow());
        assertThrows(
            IllegalStateException.class,
            () -> bodies.activate(OPERATOR, TEAM, second.id(), capsule(), biological()));
        assertThrows(
            IllegalStateException.class,
            () -> bodies.activate(otherOperator, TEAM, first.id(), capsule(), biological()));
        assertThrows(IllegalStateException.class, () -> bodies.observeBody(first.id(), first.state()));
        assertThrows(IllegalStateException.class, () -> bodies.destroy(first.id()));
        assertEquals(
            1,
            bodies.sessions()
                .size());
    }

    /** Product contract: ending control restores the biological state and retains the android's latest state. */
    @Test
    void deactivationReturnsBiologicalStateAndPreservesArmedAndroid() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody body = manufacture(bodies);
        BodyState biological = biological().withHealth(15);
        bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological);
        bodies.armDestruction(body.id(), 100);
        bodies.setDestructionTicks(body.id(), 80);
        BodyState damagedAndroid = body.state()
            .withHealth(4);

        BodyState returned = bodies.deactivate(OPERATOR, damagedAndroid);

        assertEquals(biological.write(), returned.write());
        assertFalse(
            bodies.session(OPERATOR)
                .isPresent());
        assertEquals(
            damagedAndroid.write(),
            bodies.body(body.id())
                .orElseThrow()
                .state()
                .write());
        AndroidBodies restored = AndroidBodies.open(file);
        assertEquals(
            80,
            restored.body(body.id())
                .orElseThrow()
                .destructionTicks());
        assertThrows(
            IllegalStateException.class,
            () -> restored.activate(OPERATOR, TEAM, body.id(), capsule(), biological));
        assertThrows(IllegalArgumentException.class, () -> restored.setDestructionTicks(body.id(), -1));
        assertThrows(IllegalArgumentException.class, () -> restored.setDestructionTicks(body.id(), 81));
    }

    /** Product contract: leaving a portal cancels only its pending warning, before the detonation deadline. */
    @Test
    void portalWarningsCancelWithoutMakingCommittedDestructionReversible() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        UUID id = manufacture(bodies).id();
        bodies.warnPortalTransfer(id, 60);
        bodies.setDestructionTicks(id, 40);
        bodies.warnPortalTransfer(id, 60);
        assertEquals(
            40,
            bodies.body(id)
                .orElseThrow()
                .destructionTicks());
        bodies = AndroidBodies.open(file);
        assertTrue(
            bodies.body(id)
                .orElseThrow()
                .portalWarning());
        assertTrue(bodies.cancelPortalWarning(id));
        assertFalse(
            AndroidBodies.open(file)
                .body(id)
                .orElseThrow()
                .armed());

        bodies.warnPortalTransfer(id, 60);
        bodies.setDestructionTicks(id, 0);
        assertFalse(bodies.cancelPortalWarning(id));
        assertTrue(
            bodies.body(id)
                .orElseThrow()
                .armed());

        UUID permanent = manufacture(bodies).id();
        bodies.warnPortalTransfer(permanent, 60);
        bodies.setDestructionTicks(permanent, 20);
        bodies.armDestruction(permanent, 60);
        assertEquals(
            20,
            bodies.body(permanent)
                .orElseThrow()
                .destructionTicks());
        bodies.warnPortalTransfer(permanent, 60);
        assertFalse(bodies.cancelPortalWarning(permanent));
        assertFalse(
            bodies.body(permanent)
                .orElseThrow()
                .portalWarning());
        assertTrue(
            AndroidBodies.open(file)
                .body(permanent)
                .orElseThrow()
                .armed());
    }

    /** Integration contract: crash recovery ends control without losing either selected body or the armed marker. */
    @Test
    void reopeningEndsSavedSessionsWithoutResumingThem() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody body = manufacture(bodies);
        BodyState biological = biological().withHealth(12);
        bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological);
        BodyState android = body.state()
            .withHealth(6);
        bodies.observeOperator(OPERATOR, android);
        bodies.armDestruction(body.id(), 50);
        bodies.setBattery(body.id(), 40);
        bodies.checkpoint();

        AndroidBodies recovered = AndroidBodies.open(file);

        assertTrue(
            recovered.sessions()
                .isEmpty());
        assertEquals(
            biological.write(),
            recovered.biological(OPERATOR)
                .orElseThrow()
                .state()
                .write());
        AndroidBodies.AndroidBody saved = recovered.body(body.id())
            .orElseThrow();
        assertEquals(
            android.write(),
            saved.state()
                .write());
        assertEquals(40, saved.battery());
        assertTrue(saved.armed());
        assertTrue(
            AndroidBodies.open(file)
                .sessions()
                .isEmpty());
    }

    /** Integration contract: failed disk publication must leave the prior body assignment and state intact. */
    @Test
    void failedCommitDoesNotPublishAnAssignmentOrReplaceBiologicalState() throws Exception {
        File storage = new File(directory, "storage");
        File file = new File(storage, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody body = manufacture(bodies);
        bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological());
        bodies.deactivate(OPERATOR, body.state());
        File retained = new File(directory, "retained");
        Files.move(storage.toPath(), retained.toPath());
        Files.write(storage.toPath(), new byte[] { 1 });

        assertThrows(
            IOException.class,
            () -> bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological().withHealth(3)));

        assertTrue(
            bodies.sessions()
                .isEmpty());
        assertEquals(
            20,
            bodies.biological(OPERATOR)
                .orElseThrow()
                .state()
                .health());
        assertEquals(
            body.state()
                .write(),
            bodies.body(body.id())
                .orElseThrow()
                .state()
                .write());
        AndroidBodies committed = AndroidBodies.open(new File(retained, "bodies.dat"));
        assertTrue(
            committed.sessions()
                .isEmpty());
        assertEquals(
            20,
            committed.biological(OPERATOR)
                .orElseThrow()
                .state()
                .health());
    }

    /** Product contract: returned operators remain authoritative even after ordinary travel to another dimension. */
    @Test
    void inactiveBiologicalObservationAndTeamMergeRetainBodyState() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody body = manufacture(bodies);
        bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological());
        bodies.observeOperator(
            OPERATOR,
            body.state()
                .withHealth(8));
        UUID survivingTeam = new UUID(40L, 41L);

        bodies.mergeTeams(TEAM, survivingTeam);
        BodyState travelled = BodyState.androidAt(9, 100, 65, 100, 20);
        bodies.observeOperator(OPERATOR, travelled);
        bodies.checkpoint();

        assertTrue(
            bodies.sessions()
                .isEmpty());
        assertTrue(
            bodies.bodies(TEAM)
                .isEmpty());
        assertEquals(
            survivingTeam,
            bodies.body(body.id())
                .orElseThrow()
                .teamId());
        AndroidBodies restored = AndroidBodies.open(file);
        assertEquals(
            8,
            restored.body(body.id())
                .orElseThrow()
                .state()
                .health());
        assertEquals(
            travelled.write(),
            restored.biological(OPERATOR)
                .orElseThrow()
                .state()
                .write());
    }

    /** Product contract: depleted bodies cannot begin control and repair cannot undo irreversible destruction. */
    @Test
    void batteryRepairAndDestructionKeepTheirLifecycleRules() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody body = manufacture(bodies);
        bodies.setBattery(body.id(), 0);
        assertThrows(
            IllegalStateException.class,
            () -> bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological()));
        NBTTagCompound equipped = body.state()
            .withHealth(3)
            .write();
        NBTTagCompound stack = new ItemStack(Items.diamond, 7).writeToNBT(new NBTTagCompound());
        stack.setByte("Slot", (byte) 0);
        equipped.getTagList("inventory", 10)
            .appendTag(stack);
        bodies.observeBody(body.id(), BodyState.read(equipped));
        bodies.repair(body.id(), 10);
        assertEquals(
            10,
            bodies.body(body.id())
                .orElseThrow()
                .state()
                .health());
        assertThrows(IllegalArgumentException.class, () -> bodies.repair(body.id(), 5));
        bodies.armDestruction(body.id(), 20);
        assertThrows(IllegalStateException.class, () -> bodies.repair(body.id(), 20));

        List<ItemStack> drops = bodies.destroy(body.id());

        assertEquals(1, drops.size());
        assertEquals(
            Items.diamond,
            drops.get(0)
                .getItem());
        assertEquals(7, drops.get(0).stackSize);
        assertFalse(
            bodies.body(body.id())
                .isPresent());
        assertTrue(
            AndroidBodies.open(file)
                .allBodies()
                .isEmpty());
        assertThrows(IllegalStateException.class, () -> bodies.destroy(body.id()));
    }

    /** Integration contract: malformed checkpoints fail closed instead of accepting another body identity. */
    @Test
    void reopeningRejectsDuplicateBodiesAndWrongDockDimension() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        manufacture(bodies);
        NBTTagCompound committed = read(file);
        NBTTagCompound duplicates = (NBTTagCompound) committed.copy();
        NBTTagList androids = duplicates.getTagList("androids", 10);
        androids.appendTag(
            androids.getCompoundTagAt(0)
                .copy());
        write(file, duplicates);
        assertThrows(IOException.class, () -> AndroidBodies.open(file));

        committed.getTagList("androids", 10)
            .getCompoundTagAt(0)
            .getCompoundTag("dock")
            .setInteger("dimension", 99);
        write(file, committed);
        assertThrows(IOException.class, () -> AndroidBodies.open(file));
    }

    /** Integration contract: one persisted body cannot be assigned to two operators, even during recovery. */
    @Test
    void reopeningRejectsDuplicateAssignmentsAndUnsupportedVersions() throws Exception {
        File file = new File(directory, "bodies.dat");
        AndroidBodies bodies = AndroidBodies.open(file);
        AndroidBodies.AndroidBody body = manufacture(bodies);
        UUID otherOperator = new UUID(60L, 61L);
        bodies.activate(
            otherOperator,
            TEAM,
            body.id(),
            new AndroidBodies.Anchor(UUID.randomUUID(), 0, 15, 64, 20),
            biological());
        bodies.deactivate(otherOperator, body.state());
        bodies.activate(OPERATOR, TEAM, body.id(), capsule(), biological());
        NBTTagCompound committed = read(file);
        NBTTagCompound duplicate = (NBTTagCompound) committed.getTagList("sessions", 10)
            .getCompoundTagAt(0)
            .copy();
        duplicate.setString(
            "id",
            UUID.randomUUID()
                .toString());
        duplicate.setString("operator", otherOperator.toString());
        committed.getTagList("sessions", 10)
            .appendTag(duplicate);
        write(file, committed);
        assertThrows(IOException.class, () -> AndroidBodies.open(file));

        committed.setInteger("version", committed.getInteger("version") + 1);
        write(file, committed);
        assertThrows(IOException.class, () -> AndroidBodies.open(file));
    }

    private static AndroidBodies.AndroidBody manufacture(AndroidBodies bodies) throws IOException {
        return manufacture(
            bodies,
            TEAM,
            new AndroidBodies.Anchor(UUID.randomUUID(), 7, 12, 64, 20),
            BodyState.androidAt(7, 12.5, 64, 20.5, 20),
            Chassis.EV,
            100);
    }

    private static AndroidBodies.AndroidBody manufacture(AndroidBodies bodies, UUID team, AndroidBodies.Anchor dock,
        BodyState state, Chassis chassis, long charge) throws IOException {
        UUID id = bodies.manufacture(UUID.randomUUID(), team, dock, state, chassis, charge);
        return bodies.body(id)
            .orElseThrow();
    }

    private static AndroidBodies.Anchor capsule() {
        return new AndroidBodies.Anchor(new UUID(50L, 51L), 0, 12, 64, 20);
    }

    private static BodyState biological() {
        return BodyState.androidAt(0, 12.5, 64, 20.5, 20);
    }

    private static NBTTagCompound read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    private static void write(File file, NBTTagCompound tag) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(tag, output);
        }
    }
}
