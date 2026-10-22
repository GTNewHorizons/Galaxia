package com.gtnewhorizons.galaxia.gametests;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraft.potion.Potion;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.AndroidClient;
import com.gtnewhorizons.galaxia.client.GalaxiaKeyBinds;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.GalaxiaPlayerProperties;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.android.AndroidBodies;
import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.android.BodyState;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionTeleport;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.effects.GalaxiaEffects;
import com.gtnewhorizons.galaxia.registry.hazards.HazardOxygen;
import com.gtnewhorizons.galaxia.registry.hazards.HazardPressure;
import com.gtnewhorizons.galaxia.registry.hazards.HazardTemperature;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import baubles.common.lib.PlayerHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "Baubles" })
public final class AndroidControlGameTests {

    private AndroidControlGameTests() {}

    /** Capsule lifecycle preserves access, belongings, identity and parked bodies through real control and return. */
    @GameTest(timeoutTicks = 1200)
    public static void capsuleControlLifecycle(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        AndroidStationGameTests.Fixture parkingCapsule = new AndroidStationGameTests.Fixture();
        ProjectionCleanupProbe cleanupProbe = new ProjectionCleanupProbe(fixture);
        DeathMessageObserver deaths = new DeathMessageObserver();
        long[] parkedAt = new long[1];
        EntityBoat[] vehicle = new EntityBoat[1];
        int[] originalCamera = new int[1];
        ResourceLocation[] originalCape = new ResourceLocation[1];
        long[] transitionAt = new long[1];
        ClientScenario scenario = ClientTest.scenario(helper)
            .client("observe actual android death chat packets", c -> {
                MinecraftForge.EVENT_BUS.register(deaths);
                c.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(deaths));
            })
            .server("construct powered capsule, dock and communication network", () -> fixture.prepare(helper, false))
            .awaitServer("capsule and dock are formed and powered", fixture::assertPowered)
            .server("manufacture a body and verify capsule access", fixture::prepareCapsuleSelection)
            .awaitClient(
                "operator position reached the capsule before opening its panel",
                c -> fixture.assertClientControl(false))
            .server(
                "open the real capsule panel",
                () -> GuiFactories.tileEntity()
                    .open(fixture.player, fixture.capsule.x, fixture.capsule.y, fixture.capsule.z))
            .awaitClient("actual capsule selection screen is open", c -> {
                GuiTestSupport.requireScreen("control_capsule");
                if (GuiTestSupport.findNamedWidget("android.body." + fixture.bodyId) != null)
                    throw new AssertionError("Capsule exposed a flat body list before destination selection");
                if (GuiTestSupport.findNamedWidget("android.connect") == null)
                    throw new AssertionError("Connect disappeared before an android was selected");
            })
            .client("provide a native cape for the appearance regression", c -> {
                var player = Minecraft.getMinecraft().thePlayer;
                originalCape[0] = player.getLocationCape();
                player.func_152121_a(MinecraftProfileTexture.Type.CAPE, AndroidClient.ROBOT_SKIN);
                c.afterTest(() -> {
                    var current = Minecraft.getMinecraft().thePlayer;
                    if (current != null) current.func_152121_a(MinecraftProfileTexture.Type.CAPE, originalCape[0]);
                });
            });
        scenario = openSelectionMap(scenario);
        scenario = chooseDestination(scenario, CelestialObjectKey.registered(CelestialObjectId.MARS))
            .awaitClient("Mars shows only the remote chassis", c -> {
                if (GuiTestSupport.findNamedWidget("android.body." + fixture.otherPlanetBody) == null
                    || GuiTestSupport.findNamedWidget("android.body." + fixture.bodyId) != null)
                    throw new AssertionError("Mars destination mixed bodies from another world");
            });
        scenario = scenario.capture("android-mars-context-dropdown")
            .click(ClientTarget.of("dismiss android dropdown", c -> GuiTestSupport.contextMenuDismissTarget()))
            .awaitClient("outside click closes the whole android dropdown", c -> {
                if (GuiTestSupport.findNamedWidget("android.selection") != null)
                    throw new AssertionError("Android list survived context menu dismissal");
            });
        scenario = chooseDestination(scenario, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD))
            .awaitClient("Earth shows its own chassis", c -> {
                if (GuiTestSupport.findNamedWidget("android.body." + fixture.bodyId) == null
                    || GuiTestSupport.findNamedWidget("android.body." + fixture.otherPlanetBody) != null)
                    throw new AssertionError("Earth destination mixed bodies from another world");
            })
            .click(
                ClientTarget
                    .of("rename selected android", c -> GuiTestSupport.namedTarget("android.rename." + fixture.bodyId)))
            .click(GuiTestSupport.control("android.name.input"))
            .step("type a body name")
            .key(Keyboard.KEY_S, 'S')
            .key(Keyboard.KEY_C, 'c')
            .key(Keyboard.KEY_O, 'o')
            .key(Keyboard.KEY_U, 'u')
            .key(Keyboard.KEY_T, 't')
            .click(GuiTestSupport.control("android.name.save"))
            .awaitServer("body name is committed", () -> {
                if (!"Scout".equals(
                    AndroidControl.bodies()
                        .body(fixture.bodyId)
                        .orElseThrow()
                        .name()))
                    throw new AssertionError("Capsule rename did not reach the authoritative body checkpoint");
            })
            .capture("android-capsule-named-body")
            .click(
                ClientTarget
                    .of("android.body.selection", c -> GuiTestSupport.namedTarget("android.body." + fixture.bodyId)))
            .awaitClient("selection returns to the capsule with two actions", c -> {
                var screen = GuiTestSupport.requireScreen("control_capsule");
                if (GuiTestSupport.map() != null || GuiTestSupport.findNamedWidget("android.connect") == null
                    || GuiTestSupport.findNamedWidget("android.open_starmap") == null
                    || com.cleanroommc.modularui.utils.TreeUtil
                        .flatListByType(screen.getMainPanel(), com.cleanroommc.modularui.widgets.ButtonWidget.class)
                        .size() != 2)
                    throw new AssertionError("Capsule must show only Change selection and Connect");
            })
            .server("selection alone leaves the biological operator active", () -> {
                if (AndroidControl.isAndroid(fixture.player))
                    throw new AssertionError("Choosing a body connected before the Connect action");
            })
            .capture("android-selected-capsule")
            .click(GuiTestSupport.control("android.connect"))
            .awaitServer("Connect activated the selected manufactured body", fixture::assertActivated)
            .awaitClient("client executes the empty manufactured body", c -> {
                fixture.assertClientControl(true);
                transitionAt[0] = System.nanoTime();
                if (Minecraft.getMinecraft().thePlayer.inventory.mainInventory[0] != null)
                    throw new AssertionError("Biological inventory appeared in the android");
                if (Minecraft.getMinecraft().thePlayer.func_152122_n()
                    || Minecraft.getMinecraft().thePlayer.getLocationCape() != null)
                    throw new AssertionError("The android retained its operator's native cape");
            })
            .server("biological body rests inside the formed capsule", () -> {
                BodyState biological = AndroidControl.bodies()
                    .biological(fixture.player.getUniqueID())
                    .orElseThrow()
                    .state();
                if (fixture.capsule.world.getBlock(
                    (int) Math.floor(biological.x()),
                    (int) Math.floor(biological.y()) - 1,
                    (int) Math.floor(biological.z())) != GalaxiaBlocksEnum.SPACE_STATION_PANEL.get())
                    throw new AssertionError("Biological body remained outside the capsule floor");
                var back = fixture.capsule.station.getCurrentFacing()
                    .getRelativeBackInWorld();
                if (Math.abs(biological.x() - fixture.capsule.x - .5 + back.offsetX) > .01
                    || Math.abs(biological.z() - fixture.capsule.z - .5 + back.offsetZ) > .01
                    || Math.abs(biological.y() - fixture.capsule.y) > .01)
                    throw new AssertionError("Biological body is not in the standing capsule interior");
            })
            .awaitServer("inactive biology is sealed while the controlled android can exit", () -> {
                fixture.capsule.assertDoor(false);
                fixture.dock.assertDoor(true);
                if (fixture.dock.station.connectionStatus(fixture.player)
                    != com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.ConnectionStatus.DOCK_CONNECTED)
                    throw new AssertionError("Controlled android did not connect to its own charging port");
            })
            .server("equip android and exercise native player restrictions", fixture::equipAndCheckRestrictions)
            .server("prepare a parked android with inventory during active control", () -> cleanupProbe.prepare(helper))
            .awaitServer(
                "both physical projections preserve their authoritative identities",
                cleanupProbe::assertRestored)
            .server("CoFH killall removes only matching physical-body projections", cleanupProbe::cofhKillAll)
            .awaitServer(
                "CoFH cleanup restores both bodies without losing their session or belongings",
                cleanupProbe::assertRestored)
            .server(
                "dangerous world removal cannot delete either authoritative body",
                () -> cleanupProbe.remove(body -> body.worldObj.removePlayerEntityDangerously(body)))
            .awaitServer("dangerously removed projections are recreated exactly once", cleanupProbe::assertRestored)
            .server(
                "direct setDead cannot delete either authoritative body",
                () -> cleanupProbe.remove(EntityBody::setDead))
            .awaitServer("dead projections are recreated exactly once", cleanupProbe::assertRestored)
            .server(
                "direct death callback cannot destroy either body",
                () -> cleanupProbe.remove(body -> body.onDeath(DamageSource.outOfWorld)))
            .awaitServer(
                "direct death callbacks preserve both projections and inventories",
                cleanupProbe::assertRestored)
            .server(
                "direct zero-health writes cannot destroy either body",
                () -> cleanupProbe.remove(body -> body.setHealth(0)))
            .awaitServer(
                "zero-health cleanup preserves both bodies and their control session",
                cleanupProbe::assertRestored)
            .client("cleanup emits no death notifications", c -> deaths.assertMessages())
            .server("ordinary lethal damage still destroys the parked android", cleanupProbe::attackParkedBody)
            .awaitServer(
                "combat death retires the body and drops exactly one copy of its cargo",
                cleanupProbe::assertCombatDeath)
            .awaitClient(
                "parked android combat emits one named death notification",
                c -> deaths.assertMessages("Android " + cleanupProbe.androidName + " died"))
            .server("remove the isolated combat regression drops", cleanupProbe::clearDrops)
            .awaitClient("android items and battery presentation synchronized", c -> {
                fixture.assertClientControl(true);
                fixture.assertStack(
                    Minecraft.getMinecraft().thePlayer.inventory.mainInventory[0],
                    Items.iron_ingot,
                    7,
                    "android");
                if (c.screen() != null) throw new AssertionError("Native Ender Chest opened for an android");
            })
            .capture("android-controlled-body")
            .awaitClient("capture the middle of the cosmetic boot console", c -> {
                // Harness exception: native rendering is needed for this OpenGL presentation.
                if (System.nanoTime() - transitionAt[0] < 300_000_000L)
                    throw new AssertionError("Waiting for the boot console capture");
            })
            .capture("android-boot-console")
            .awaitClient("capture the console after more boot messages", c -> {
                if (System.nanoTime() - transitionAt[0] < 550_000_000L)
                    throw new AssertionError("Waiting for the scrolled console capture");
            })
            .capture("android-boot-console-scroll")
            .awaitClient("leave the boot presentation before the appearance capture", c -> {
                // Harness exception: let the brief cosmetic transition finish before capturing the actual body and HUD.
                if (System.nanoTime() - transitionAt[0] < 1_000_000_000L)
                    throw new AssertionError("Waiting for the appearance capture");
            })
            .client("show the native player skin in third person", c -> {
                originalCamera[0] = Minecraft.getMinecraft().gameSettings.thirdPersonView;
                c.afterTest(() -> Minecraft.getMinecraft().gameSettings.thirdPersonView = originalCamera[0]);
                Minecraft.getMinecraft().gameSettings.thirdPersonView = 1;
            })
            .capture("android-third-person")
            .client(
                "restore the operator's camera",
                c -> Minecraft.getMinecraft().gameSettings.thirdPersonView = originalCamera[0])
            .server("mount a real vehicle before the native return key", () -> {
                vehicle[0] = new EntityBoat(
                    fixture.dock.world,
                    fixture.player.posX,
                    fixture.player.posY,
                    fixture.player.posZ);
                fixture.dock.world.spawnEntityInWorld(vehicle[0]);
                helper.afterTest(() -> {
                    if (fixture.player.ridingEntity == vehicle[0]) fixture.player.mountEntity(null);
                    vehicle[0].setDead();
                });
                fixture.player.mountEntity(vehicle[0]);
                if (vehicle[0].riddenByEntity != fixture.player)
                    throw new AssertionError("The android did not mount its vehicle");
            })
            .client("return through the native gameplay key binding and input event", c -> {
                // Harness exception: the pinned Horizon key helper requires a GUI and cannot inject gameplay keys.
                KeyBinding.onTick(GalaxiaKeyBinds.END_ANDROID_CONTROL.getKeyCode());
                FMLCommonHandler.instance()
                    .bus()
                    .post(new InputEvent.KeyInputEvent());
            })
            .awaitServer("biological belongings returned and the android's vehicle is detached", () -> {
                fixture.assertBiological();
                if (fixture.player.ridingEntity != null || vehicle[0].riddenByEntity != null)
                    throw new AssertionError("The biological body retained the android's vehicle");
            })
            .awaitClient("client returned to the biological body", c -> {
                fixture.assertClientControl(false);
                if (Minecraft.getMinecraft().thePlayer.ridingEntity != null)
                    throw new AssertionError("Client biology retained the android's vehicle");
                transitionAt[0] = System.nanoTime();
                if (!AndroidClient.ROBOT_SKIN.equals(Minecraft.getMinecraft().thePlayer.getLocationCape()))
                    throw new AssertionError("Returning to biology did not restore the operator's native cape");
                fixture.assertStack(
                    Minecraft.getMinecraft().thePlayer.inventory.mainInventory[0],
                    Items.diamond,
                    11,
                    "biological");
            })
            .awaitClient("capture the cosmetic eye opening", c -> {
                // Harness exception: capture the native OpenGL effect after the return was acknowledged.
                if (System.nanoTime() - transitionAt[0] < 250_000_000L)
                    throw new AssertionError("Waiting for the eye opening capture");
            })
            .capture("android-return-eyes")
            .awaitServer("unattended dock closes while biology can exit the capsule", () -> {
                fixture.dock.assertDoor(false);
                fixture.capsule.assertDoor(true);
            })
            .server(
                "prepare another capsule for the parking regression",
                () -> parkingCapsule.prepare(helper, true, 64))
            .awaitServer(
                "parking capsule is powered",
                () -> {
                    if (!parkingCapsule.station.isPowered())
                        throw new AssertionError("Waiting for the parking capsule");
                })
            .server("control the same equipped android", fixture::reactivate)
            .awaitClient("equipped android is controlled again", c -> fixture.assertClientControl(true))
            .server("park the android inside the other capsule", () -> {
                BodyState inside = parkingCapsule.station.captureOccupant(fixture.player);
                fixture.player.playerNetServerHandler.setPlayerLocation(inside.x(), inside.y(), inside.z(), 0, 0);
                if (!AndroidControl.endControl(fixture.player)) throw new AssertionError("Cannot park the android");
            })
            .awaitClient("biology returns to the original capsule", c -> fixture.assertClientControl(false))
            .server("approach the parked android as an authorized active player", () -> {
                parkingCapsule.approachDoor();
                parkedAt[0] = parkingCapsule.world.getTotalWorldTime();
            })
            .awaitServer("inactive android seals its capsule against an approaching operator", () -> {
                if (parkingCapsule.world.getTotalWorldTime() - parkedAt[0] < 10)
                    throw new AssertionError("Waiting for ordinary proximity-door ticks");
                parkingCapsule.assertDoor(false);
            })
            .server(
                "reenter the original capsule before reconnecting",
                () -> fixture.player.playerNetServerHandler
                    .setPlayerLocation(fixture.biologicalX, fixture.biologicalY, fixture.biologicalZ, 0, 0))
            .awaitClient(
                "original capsule position reached the client before reconnecting",
                c -> fixture.assertClientControl(false))
            .server("take control of the same android again", fixture::reactivate)
            .awaitClient("second activation reaches the client", c -> fixture.assertClientControl(true))
            .server("the actual public player death path destroys only the android", () -> {
                fixture.recordDeparture();
                fixture.player.onDeath(DamageSource.generic);
                fixture.assertBiological();
                fixture.assertDestroyedLocally();
            })
            .awaitClient("death returned the client without a game-over screen", c -> {
                fixture.assertClientControl(false);
                if (Minecraft.getMinecraft().thePlayer.getHealth() <= 0)
                    throw new AssertionError("Android destruction killed the client player");
                if (c.screen() instanceof net.minecraft.client.gui.GuiGameOver)
                    throw new AssertionError("Android destruction opened Game Over");
                deaths.assertMessages("Android " + cleanupProbe.androidName + " died", "Android Scout died");
            });
        scenario.succeed();
    }

    /** Exercises cleanup APIs against disposable projections while their canonical bodies stay alive. */
    private static final class ProjectionCleanupProbe {

        private final Fixture fixture;
        private UUID androidId;
        private UUID biologicalId;
        private UUID sessionId;
        private BodyState androidState;
        private BodyState biologicalState;
        private long cleanupTick = -1;
        private final String cargoMarker = UUID.randomUUID()
            .toString();
        private final String androidName = "Cleanup-" + cargoMarker.substring(0, 8);

        private ProjectionCleanupProbe(Fixture fixture) {
            this.fixture = fixture;
        }

        private void prepare(GameTestHelper helper) {
            helper.afterTest(this::clearDrops);
            AndroidBodies bodies = AndroidControl.bodies();
            NBTTagCompound state = BodyState
                .androidAt(
                    fixture.dock.world.provider.dimensionId,
                    fixture.dock.x + 2.5,
                    fixture.dock.y,
                    fixture.dock.z - 2.5,
                    20)
                .write();
            ItemStack cargo = new ItemStack(Items.emerald, 13);
            NBTTagCompound marker = new NBTTagCompound();
            marker.setString("projectionCleanupQa", cargoMarker);
            cargo.setTagCompound(marker);
            NBTTagCompound savedCargo = cargo.writeToNBT(new NBTTagCompound());
            savedCargo.setByte("Slot", (byte) 0);
            NBTTagList inventory = new NBTTagList();
            inventory.appendTag(savedCargo);
            state.setTag("inventory", inventory);
            androidState = BodyState.read(state);
            try {
                androidId = bodies.manufacture(
                    UUID.randomUUID(),
                    fixture.capsule.team,
                    fixture.dock.station.anchor(),
                    androidState,
                    AndroidBodies.Chassis.EV,
                    1000000);
                fixture.dock.trackBody(androidId);
                bodies.rename(fixture.capsule.team, androidId, androidName);
            } catch (IOException ex) {
                throw new AssertionError("Cannot prepare the parked cleanup regression body", ex);
            }
            var biological = bodies.biological(fixture.player.getUniqueID())
                .orElseThrow();
            biologicalId = biological.id();
            biologicalState = biological.state();
            sessionId = bodies.session(fixture.player.getUniqueID())
                .orElseThrow()
                .id();
        }

        private EntityBody projection(UUID id) {
            EntityBody found = null;
            for (Object candidate : fixture.dock.world.loadedEntityList) {
                if (!(candidate instanceof EntityBody body) || body.isDead || !id.equals(body.bodyId())) continue;
                if (found != null) throw new AssertionError("Duplicate physical projection for " + id);
                found = body;
            }
            if (found == null) throw new AssertionError("Missing physical projection for " + id);
            return found;
        }

        private void assertCanonical() {
            AndroidBodies bodies = AndroidControl.bodies();
            var android = bodies.body(androidId)
                .orElseThrow(() -> new AssertionError("Cleanup deleted the parked android"));
            var biological = bodies.biological(fixture.player.getUniqueID())
                .orElseThrow(() -> new AssertionError("Cleanup deleted the biological body"));
            var session = bodies.session(fixture.player.getUniqueID())
                .orElseThrow(() -> new AssertionError("Cleanup disconnected the operator"));
            if (!android.id()
                .equals(androidId)
                || !biological.id()
                    .equals(biologicalId)
                || !session.id()
                    .equals(sessionId)
                || !session.bodyId()
                    .equals(fixture.bodyId))
                throw new AssertionError("Cleanup replaced an authoritative body or control session identity");
            assertBelongings(androidState, android.state());
            assertBelongings(biologicalState, biological.state());
        }

        private static void assertBelongings(BodyState expected, BodyState actual) {
            if (expected.health() != actual.health() || !expected.write()
                .getTag("inventory")
                .equals(
                    actual.write()
                        .getTag("inventory")))
                throw new AssertionError("Cleanup changed authoritative health or inventory");
        }

        private void assertRestored() {
            if (fixture.dock.world.getTotalWorldTime() <= cleanupTick)
                throw new AssertionError("Waiting for the next tick to reconcile physical projections");
            assertCanonical();
            for (UUID id : List.of(androidId, biologicalId)) {
                EntityBody body = projection(id);
                BodyState expected = id.equals(androidId) ? androidState : biologicalState;
                if (!body.getUniqueID()
                    .equals(id) || body.getHealth() != expected.health())
                    throw new AssertionError("Restored projection has stale identity or health");
                for (int slot = 0; slot < 5; slot++) {
                    if (!ItemStack.areItemStacksEqual(expected.equipment(slot), body.getEquipmentInSlot(slot)))
                        throw new AssertionError("Restored projection lost equipment slot " + slot);
                }
            }
        }

        private void remove(Consumer<EntityBody> cleanup) {
            cleanupTick = fixture.dock.world.getTotalWorldTime();
            cleanup.accept(projection(androidId));
            cleanup.accept(projection(biologicalId));
            assertCanonical();
        }

        private void attackParkedBody() {
            if (!cargoDrops().isEmpty())
                throw new AssertionError("Projection cleanup duplicated parked cargo before combat");
            cleanupTick = fixture.dock.world.getTotalWorldTime();
            if (!projection(androidId).attackEntityFrom(DamageSource.generic, 1000))
                throw new AssertionError("Parked android rejected ordinary lethal damage");
        }

        private void assertCombatDeath() {
            if (fixture.dock.world.getTotalWorldTime() < cleanupTick + 5)
                throw new AssertionError("Waiting for subsequent projection reconciliation ticks after combat");
            AndroidBodies bodies = AndroidControl.bodies();
            if (bodies.body(androidId)
                .isPresent() || !bodies.wasManufactured(androidId))
                throw new AssertionError("Combat did not retire the manufactured body");
            for (Object candidate : fixture.dock.world.loadedEntityList) {
                if (candidate instanceof EntityBody body && !body.isDead && androidId.equals(body.bodyId()))
                    throw new AssertionError("A combat-destroyed android respawned");
            }
            List<EntityItem> drops = cargoDrops();
            if (drops.size() != 1 || drops.get(0)
                .getEntityItem()
                .getItem() != Items.emerald
                || drops.get(0)
                    .getEntityItem().stackSize != 13)
                throw new AssertionError("Combat must drop exactly one copy of the parked android's cargo");
            var session = bodies.session(fixture.player.getUniqueID())
                .orElseThrow();
            var biological = bodies.biological(fixture.player.getUniqueID())
                .orElseThrow();
            if (!session.id()
                .equals(sessionId)
                || !session.bodyId()
                    .equals(fixture.bodyId)
                || !biological.id()
                    .equals(biologicalId))
                throw new AssertionError("Parked android death changed the operator's control session");
            assertBelongings(biologicalState, biological.state());
        }

        private List<EntityItem> cargoDrops() {
            List<EntityItem> drops = new ArrayList<>();
            for (Object candidate : fixture.dock.world.loadedEntityList) {
                if (!(candidate instanceof EntityItem item) || item.isDead) continue;
                ItemStack stack = item.getEntityItem();
                if (stack.hasTagCompound() && cargoMarker.equals(
                    stack.getTagCompound()
                        .getString("projectionCleanupQa")))
                    drops.add(item);
            }
            return drops;
        }

        private void clearDrops() {
            for (EntityItem item : cargoDrops()) item.setDead();
        }

        private void cofhKillAll() {
            cleanupTick = fixture.dock.world.getTotalWorldTime();
            EntityBody android = projection(androidId);
            EntityBody biological = projection(biologicalId);
            String selector = EntityList.getEntityString(android);
            if (selector == null || !selector.equals(EntityList.getEntityString(biological)))
                throw new AssertionError("Cleanup fixture has no shared registered body entity selector");
            fixture.player.mcServer.getCommandManager()
                .executeCommand(fixture.player.mcServer, "cofh killall " + selector);
            if (!android.isDead || !biological.isDead)
                throw new AssertionError("CoFH killall did not actually remove the selected body projections");
            assertCanonical();
        }
    }

    private static ClientScenario openSelectionMap(ClientScenario scenario) {
        com.cleanroommc.modularui.screen.ModularPanel[] previous = new com.cleanroommc.modularui.screen.ModularPanel[1];
        scenario = scenario
            .client(
                "remember the old capsule panel",
                c -> previous[0] = GuiTestSupport.requireScreen("control_capsule")
                    .getMainPanel())
            .click(GuiTestSupport.control("android.open_starmap"))
            .awaitClient("starmap replaced the capsule and disposed its panels", c -> {
                var screen = GuiTestSupport.requireScreen("galactic_orbital_map");
                if (previous[0].isValid() || screen.getPanelManager()
                    .getOpenPanels()
                    .size() != 1
                    || screen.getContext()
                        .getParentScreen() != null)
                    throw new AssertionError("Starmap retained a previous UI or child panel");
                if (GuiTestSupport.map() == null) throw new AssertionError("Capsule did not open its native starmap");
            });
        return GuiTestSupport.frameInnerSolarSystem(scenario);
    }

    private static ClientScenario chooseDestination(ClientScenario scenario, CelestialObjectKey destination) {
        return scenario
            .rightClick(ClientTarget.of("capsule destination", c -> GuiTestSupport.bodyTarget(destination, 1)))
            .awaitClient("right click opens the android dropdown at the planet", c -> {
                GuiTestSupport.requireScreen("galactic_orbital_map");
                var menu = GuiTestSupport.findNamedWidget("starmap.context.menu");
                var choices = GuiTestSupport.findNamedWidget("android.selection");
                if (menu == null || choices == null || !choices.areAncestorsEnabled())
                    throw new AssertionError("Right click did not open the android dropdown");
                var bounds = GuiTestSupport.widgetBounds(menu);
                if (!bounds.contains(GuiTestSupport.widgetBounds(choices)))
                    throw new AssertionError("Android choices appeared outside the context menu");
                if (GuiTestSupport.findNamedWidget("starmap.context.SELECT_BODY") != null
                    || GuiTestSupport.findNamedWidget("starmap.context.MANAGE_ASSETS") != null
                    || GuiTestSupport.findNamedWidget("starmap.context.ADD_COMMUNICATION_SATELLITE") != null)
                    throw new AssertionError("Unrelated or intermediate actions leaked into the android dropdown");
            });
    }

    /** Integration contract: a direct native dimension transfer arms destruction before any belongings leave Mars. */
    @GameTest(timeoutTicks = 1600)
    public static void nativeDimensionTransferDestroysAndroidAndDropsCargoOnDepartureSide(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ExplosionSoundObserver sound = new ExplosionSoundObserver();
        DeathMessageObserver deaths = new DeathMessageObserver();
        ClientTest.scenario(helper)
            .client("observe self-destruction death chat packets", c -> {
                MinecraftForge.EVENT_BUS.register(deaths);
                c.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(deaths));
            })
            .server("construct an Overworld capsule and Mars destination dock", () -> fixture.prepare(helper, true))
            .awaitServer("real stations have required power", fixture::assertPowered)
            .awaitClient(
                "biological source position and floor reached the client",
                c -> fixture.assertClientControl(false))
            .awaitServer(
                "source mining starts from solid ground",
                () -> {
                    if (!fixture.player.onGround) throw new AssertionError("Source actor has not reached its floor");
                })
            .server("finish a source mining gesture and activate a manufactured Mars body", () -> {
                fixture.preparePendingMining();
                fixture.activate();
                fixture.miningStarted = fixture.dock.world.getTotalWorldTime();
            })
            .withinTicks(300)
            .awaitClient("client receives Mars control and body inventory", c -> fixture.assertClientControl(true))
            .withinTicks(500)
            .awaitServerAccelerated(
                "old mining cannot harvest source coordinates in the new body world",
                10,
                fixture::assertMiningCleared)
            .server("carry android cargo and attempt the elevator and native transfer paths", () -> {
                fixture.equipAndCheckRestrictions();
                try {
                    AndroidControl.bodies()
                        .rename(fixture.capsule.team, fixture.bodyId, "Transfer Scout");
                } catch (IOException ex) {
                    throw new AssertionError("Cannot name the self-destruction fixture", ex);
                }
                fixture.recordDeparture();
                fixture.departure.addWorldAccess(sound);
                helper.afterTest(() -> fixture.departure.removeWorldAccess(sound));
                DimensionTeleport
                    .teleport(fixture.player, 0, fixture.dropX + 10, fixture.dropY + 10, fixture.dropZ + 10);
                fixture.player.mcServer.getConfigurationManager()
                    .transferPlayerToDimension(fixture.player, 0);
                if (fixture.player.dimension != fixture.departure.provider.dimensionId
                    || fixture.player.getDistanceSq(fixture.dropX, fixture.dropY, fixture.dropZ) > 0.01)
                    throw new AssertionError("Native transfer moved the android before self-destruction");
                if (!AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .armed()) throw new AssertionError("Native transfer did not arm irreversible destruction");
                fixture.assertStack(fixture.player.inventory.mainInventory[0], Items.iron_ingot, 7, "android");
            })
            .awaitServer("countdown remains locked on the departure side", () -> {
                AndroidBodies.AndroidBody body = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow();
                if (body.destructionTicks() > Math.max(1, ConfigMachines.android.countdownTicks - 12))
                    throw new AssertionError("Waiting for visible countdown synchronization");
                if (!AndroidControl.locked(fixture.player)
                    || fixture.player.dimension != fixture.departure.provider.dimensionId)
                    throw new AssertionError("Armed android escaped its dimension lock");
            })
            .capture("android-self-destruct-countdown")
            .awaitServer("explosive priming has advanced", () -> {
                var body = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow();
                if (body.destructionTicks() > ConfigMachines.android.countdownTicks / 2)
                    throw new AssertionError("Waiting for the later countdown presentation");
            })
            .capture("android-self-destruct-priming")
            .withinTicks(300)
            .awaitServer("operator returned alive and cargo stayed on Mars", () -> {
                fixture.assertBiological();
                fixture.assertDestroyedLocally();
            })
            .server("one explosion sound originated at the android", () -> {
                if (sound.positions.size() != 1)
                    throw new AssertionError("Expected one server explosion sound, got " + sound.positions.size());
                double[] position = sound.positions.get(0);
                if (Math.abs(position[0] - fixture.dropX) > .01 || Math.abs(position[1] - fixture.dropY) > .01
                    || Math.abs(position[2] - fixture.dropZ) > .01)
                    throw new AssertionError("Explosion sound was emitted away from the android's physical position");
            })
            .awaitClient(
                "client returned from Mars without retaining robot state",
                c -> fixture.assertClientControl(false))
            .awaitClient(
                "self-destruction reports the android name and correct reason exactly once",
                c -> deaths.assertMessages("Android Transfer Scout self-destructed"))
            .capture("android-biological-return")
            .succeed();
    }

    /** A real Mars connection preserves relays, survives its environment and ends cleanly when bandwidth is lost. */
    @GameTest(timeoutTicks = 1800)
    public static void marsRemoteControlAndEnvironment(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        long[] started = new long[1], battery = new long[1];
        float[] health = new float[1];
        BandwidthWarningListener warning = new BandwidthWarningListener();
        ClientScenario sequence = ClientTest.scenario(helper)
            .server("prepare real machines and communication satellites", () -> fixture.prepare(helper, true))
            .awaitServer("machines are powered", fixture::assertPowered)
            .awaitClient(
                "satellites are visible before changing dimensions",
                c -> fixture.assertClientSatelliteNetwork(0))
            .client("observe connection-loss notification", c -> {
                MinecraftForge.EVENT_BUS.register(warning);
                c.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(warning));
            })
            .server("connect the Mars body through the real reservation", () -> {
                fixture.activate();
                fixture.clearBaubles();
                var effects = com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer
                    .findDefinitionById(fixture.player.dimension)
                    .orElseThrow()
                    .effects();
                if (effects.getTemperature(fixture.player.worldObj) >= HazardTemperature.DEFAULT_MIN
                    || effects.getPressure(fixture.player.worldObj) >= HazardPressure.DEFAULT_MIN)
                    throw new AssertionError("Mars fixture must expose actual cold and low pressure");
                started[0] = fixture.dock.world.getTotalWorldTime();
                battery[0] = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .battery();
                health[0] = fixture.player.getHealth();

                if (!SatelliteNetworkService.hasReservation(fixture.capsule.team, fixture.bodyId))
                    throw new AssertionError("Mars control has no bandwidth reservation");
                for (CelestialAsset.ID id : fixture.satellites) {
                    if (CelestialAssetStore.findAsset(id) == null)
                        throw new AssertionError("Connecting consumed a communication satellite");
                }
            })
            .awaitClient("client reaches Mars", c -> fixture.assertClientControl(true))
            .withinTicks(200)
            .awaitClient(
                "satellites and reserved bandwidth remain visible",
                c -> fixture.assertClientSatelliteNetwork(ConfigMachines.android.requiredBandwidthKbps))
            .server("equip the map in the android", () -> {
                fixture.player.inventory.setInventorySlotContents(
                    fixture.player.inventory.currentItem,
                    new ItemStack(GalaxiaItemList.ITEM_GALACTIC_MAP.getItem()));
                fixture.player.inventoryContainer.detectAndSendChanges();
            })
            .awaitClient("map reaches the android", c -> {
                ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                if (held == null || held.getItem() != GalaxiaItemList.ITEM_GALACTIC_MAP.getItem())
                    throw new AssertionError("Waiting for the map");
            })
            .client("prepare clean map capture", c -> {
                boolean previous = com.cleanroommc.modularui.ModularUIConfig.guiDebugMode;
                c.afterTest(() -> com.cleanroommc.modularui.ModularUIConfig.guiDebugMode = previous);
                com.cleanroommc.modularui.ModularUIConfig.guiDebugMode = false;
            })
            .useHeldItem()
            .awaitClient("normal starmap opens", c -> GuiTestSupport.requireScreen("galactic_orbital_map"));
        GuiTestSupport.frameInnerSolarSystem(sequence)
            .capture("mars-android-satellites-reserved-bandwidth")
            .key(Keyboard.KEY_ESCAPE, '\0')
            .withinTicks(400)
            .awaitServerAccelerated("unprotected android survives sustained Mars exposure", 10, () -> {
                if (fixture.dock.world.getTotalWorldTime() - started[0] < 240)
                    throw new AssertionError("Waiting through repeated native environmental updates");
            })
            .server("cold and pressure do not damage or slow the android", () -> {
                if (!AndroidControl.isAndroid(fixture.player) || fixture.player.getHealth() != health[0])
                    throw new AssertionError("Cold or low pressure harmed the Mars android");
                if (fixture.player.isPotionActive(GalaxiaEffects.freezing)
                    || fixture.player.isPotionActive(Potion.moveSlowdown)
                    || fixture.player.isPotionActive(Potion.digSlowdown))
                    throw new AssertionError("Mars android retained biological environmental penalties");
                long elapsed = fixture.dock.world.getTotalWorldTime() - started[0];
                long spent = battery[0] - AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .battery();
                if (spent <= elapsed * ConfigMachines.android.baseEnergyPerTick)
                    throw new AssertionError("Cold exposure did not increase the android's power use");
            })
            .server("disable the actual Mars communication satellites", () -> {
                var mars = com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey
                    .registered(CelestialObjectId.MARS);
                for (CelestialAsset.ID id : fixture.satellites) {
                    CelestialAsset asset = CelestialAssetStore.findAsset(id);
                    if (asset != null && asset.celestialObjectKey.equals(mars)) CelestialAssetStore.disableAsset(id);
                }
            })
            .awaitServer("link loss returns the operator and releases the reservation", () -> {
                fixture.assertBiological();
                if (SatelliteNetworkService.hasReservation(fixture.capsule.team, fixture.bodyId))
                    throw new AssertionError("Broken link retained the android reservation");
            })
            .awaitClient("operator sees the reason for disconnection", c -> {
                fixture.assertClientControl(false);
                fixture.assertClientSatelliteNetwork(0);
                if (!warning.received) throw new AssertionError("Missing bandwidth-loss notification");
            })
            .succeed();
    }

    public static final class DeathMessageObserver {

        private final List<String> messages = new ArrayList<>();

        @SubscribeEvent
        public void chat(ClientChatReceivedEvent event) {
            if (event.message instanceof net.minecraft.util.ChatComponentTranslation translation && translation.getKey()
                .startsWith("death.attack.")) messages.add(event.message.getUnformattedText());
        }

        private void assertMessages(String... expected) {
            if (!messages.equals(Arrays.asList(expected))) throw new AssertionError(
                "Expected only " + Arrays.toString(expected) + " death messages, received " + messages);
        }
    }

    public static final class BandwidthWarningListener {

        private boolean received;

        @SubscribeEvent
        public void chat(ClientChatReceivedEvent event) {
            if (event.message instanceof net.minecraft.util.ChatComponentTranslation translation && translation.getKey()
                .equals("galaxia.android.connection.bandwidth_lost")) received = true;
        }
    }

    /** Native motor input changes energy use and the same body presents both low-charge warning levels. */
    @GameTest(timeoutTicks = 1600)
    public static void androidActivityEnergyAndBatteryWarnings(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        long[] time = new long[1], battery = new long[1], changedAt = new long[1];
        double[] rates = new double[3];
        double[] stageZ = new double[1];
        ClientScenario sequence = ClientTest.scenario(helper)
            .server("prepare real machines", () -> fixture.prepare(helper, false))
            .awaitServer("machines are powered", fixture::assertPowered)
            .server("activate and prepare an open movement lane", () -> {
                fixture.activate();
                fixture.activeX = fixture.dock.x + 8.5;
                fixture.activeZ = fixture.dock.z + .5;
                for (int x = fixture.dock.x + 7; x <= fixture.dock.x + 10; x++) {
                    for (int z = fixture.dock.z - 1; z <= fixture.dock.z + 40; z++) {
                        fixture.dock.placed.add(new BlockPos(x, fixture.dock.y - 1, z));
                        fixture.dock.world.setBlock(x, fixture.dock.y - 1, z, Blocks.stone, 0, 3);
                    }
                }
                fixture.player.playerNetServerHandler
                    .setPlayerLocation(fixture.activeX, fixture.activeY, fixture.activeZ, 0, 0);
            })
            .awaitClient("client arrives on the lane", c -> fixture.assertClientControl(true))
            .client("register input cleanup", c -> c.afterTest(() -> {
                var keys = Minecraft.getMinecraft().gameSettings;
                KeyBinding.setKeyBindState(keys.keyBindForward.getKeyCode(), false);
                KeyBinding.setKeyBindState(keys.keyBindSprint.getKeyCode(), false);
                KeyBinding.setKeyBindState(keys.keyBindJump.getKeyCode(), false);
            }));
        for (int activity = 0; activity < 3; activity++) {
            int current = activity;
            sequence.server("record starting position", () -> stageZ[0] = fixture.player.posZ)
                .client("set activity " + current, c -> {
                    var keys = Minecraft.getMinecraft().gameSettings;
                    KeyBinding.setKeyBindState(keys.keyBindForward.getKeyCode(), current > 0);
                    KeyBinding.setKeyBindState(keys.keyBindSprint.getKeyCode(), current == 2);
                })
                .awaitServer("activity reaches server", () -> {
                    if ((current > 0 && fixture.player.posZ - stageZ[0] < .25)
                        || fixture.player.isSprinting() != (current == 2))
                        throw new AssertionError("Waiting for movement state " + current);
                })
                .server("begin battery sample", () -> {
                    time[0] = fixture.dock.world.getTotalWorldTime();
                    battery[0] = AndroidControl.bodies()
                        .body(fixture.bodyId)
                        .orElseThrow()
                        .battery();
                })
                .awaitServer(
                    "sample sustained activity",
                    () -> {
                        if (fixture.dock.world.getTotalWorldTime() - time[0] < 10)
                            throw new AssertionError("Collecting battery sample");
                    })
                .server(
                    "record energy rate",
                    () -> rates[current] = (double) (battery[0] - AndroidControl.bodies()
                        .body(fixture.bodyId)
                        .orElseThrow()
                        .battery()) / (fixture.dock.world.getTotalWorldTime() - time[0]));
        }
        sequence.client("stop moving", c -> {
            var mc = Minecraft.getMinecraft();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            mc.thePlayer.setSprinting(false);
        })
            .awaitClient("android stands on its floor", c -> {
                var actor = Minecraft.getMinecraft().thePlayer;
                if (!actor.onGround || Math.abs(actor.posZ - actor.prevPosZ) > 1e-6)
                    throw new AssertionError("Waiting for movement to stop");
            })
            .server("motor activity increased battery use", () -> {
                if (!(rates[0] > 0 && rates[1] > rates[0] && rates[2] > rates[1]))
                    throw new AssertionError("Activity battery rates: " + Arrays.toString(rates));
                time[0] = fixture.dock.world.getTotalWorldTime();
                battery[0] = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .battery();
            })
            .client(
                "jump with native input",
                c -> KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindJump.getKeyCode(), true))
            .awaitServer(
                "jump reaches server",
                () -> { if (fixture.player.posY < fixture.activeY + .2) throw new AssertionError("Waiting for jump"); })
            .server("jump charges beyond passive energy", () -> {
                long elapsed = fixture.dock.world.getTotalWorldTime() - time[0];
                long spent = battery[0] - AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .battery();
                if (spent - elapsed * ConfigMachines.android.baseEnergyPerTick < ConfigMachines.android.jumpEnergy)
                    throw new AssertionError("Native jump did not charge its energy cost");
            })
            .client(
                "release jump",
                c -> KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindJump.getKeyCode(), false))
            .server("lower charge to the low battery level", () -> {
                var body = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow();
                AndroidControl.bodies()
                    .setBattery(body.id(), body.capacity() * 9 / 100);
                changedAt[0] = fixture.dock.world.getTotalWorldTime();
            })
            .awaitServer("low battery has synchronized and boot has finished", () -> {
                if (fixture.dock.world.getTotalWorldTime() - changedAt[0] < 24)
                    throw new AssertionError("Waiting for native HUD presentation");
            })
            .capture("android-low-battery")
            .server("lower charge to the critical level", () -> {
                var body = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow();
                AndroidControl.bodies()
                    .setBattery(body.id(), body.capacity() * 4 / 100);
                changedAt[0] = fixture.dock.world.getTotalWorldTime();
            })
            .awaitServer("critical battery reaches the client", () -> {
                if (fixture.dock.world.getTotalWorldTime() - changedAt[0] < 12)
                    throw new AssertionError("Waiting for charge synchronization");
            })
            .capture("android-critical-battery")
            .server("return to biology", () -> AndroidControl.endControl(fixture.player))
            .awaitClient("operator returns alive", c -> fixture.assertClientControl(false))
            .succeed();
    }

    /** Both actual portal types permit native retreat and still reject sustained cross-dimension escape. */
    @GameTest(timeoutTicks = 1600)
    public static void portalRetreatAndSelfDestruction(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientScenario sequence = ClientTest.scenario(helper)
            .server("construct powered capsule and dock", () -> fixture.prepare(helper, false))
            .awaitServer("machines have formed and have power", fixture::assertPowered);
        for (boolean nether : new boolean[] { false, true }) sequence = portalRetreatSteps(sequence, fixture, nether);
        sequence.succeed();
    }

    private static ClientScenario portalRetreatSteps(ClientScenario sequence, Fixture fixture, boolean nether) {
        String phase = nether ? "Nether: " : "End: ";
        int[] portal = new int[3];
        long[] escapedAt = new long[1];
        return sequence.withinTicks(400)
            .awaitServerAccelerated(phase + "previous portal cooldown expires outside the portal", 10, () -> {
                if (fixture.player.timeUntilPortal > 0)
                    throw new AssertionError("Waiting for the biological operator's native portal cooldown");
            })
            .server(phase + "activate and prepare an open portal lane", () -> {
                fixture.activate();
                portal[0] = fixture.dock.x + 8;
                portal[1] = fixture.dock.y;
                portal[2] = fixture.dock.z;
                for (int x = portal[0] - 1; x <= portal[0] + 1; x++) {
                    for (int z = portal[2] - 1; z <= portal[2] + 7; z++) {
                        fixture.dock.placed.add(new BlockPos(x, portal[1] - 1, z));
                        fixture.dock.world.setBlock(x, portal[1] - 1, z, Blocks.stone, 0, 3);
                    }
                }
                fixture.dock.placed.add(new BlockPos(portal[0], portal[1], portal[2]));
                if (nether) {
                    for (int x = -1; x <= 2; x++) for (int y = -1; y <= 3; y++) {
                        int px = portal[0] + x, py = portal[1] + y;
                        fixture.dock.placed.add(new BlockPos(px, py, portal[2]));
                        boolean frame = x == -1 || x == 2 || y == -1 || y == 3;
                        fixture.dock.world
                            .setBlock(px, py, portal[2], frame ? Blocks.obsidian : Blocks.portal, frame ? 0 : 1, 2);
                    }
                } else fixture.dock.world.setBlock(portal[0], portal[1], portal[2], Blocks.end_portal, 0, 2);
                fixture.activeX = portal[0] + .5;
                fixture.activeY = portal[1];
                fixture.activeZ = portal[2] + .5;
                fixture.player.playerNetServerHandler
                    .setPlayerLocation(fixture.activeX, fixture.activeY, fixture.activeZ, 0, 0);
            })
            .awaitClient(phase + "client reaches the real portal", c -> {
                var actor = Minecraft.getMinecraft().thePlayer;
                if (!AndroidClient.isAndroid(actor) || Math.abs(actor.posX - fixture.activeX) > .25
                    || Math.abs(actor.posZ - fixture.activeZ) > .25)
                    throw new AssertionError("Client has not reached the portal lane");
            })
            .withinTicks(200)
            .awaitServer(phase + "portal warning starts", () -> {
                var body = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow();
                if (!body.armed()) throw new AssertionError("Portal warning has not started");
            })
            .server(phase + "portal attempt is blocked without freezing movement", () -> {
                if (fixture.player.dimension != fixture.dock.world.provider.dimensionId)
                    throw new AssertionError("Portal moved the android to another dimension");
                if (AndroidControl.locked(fixture.player))
                    throw new AssertionError("Portal warning freezes the operator instead of allowing retreat");
            })
            .client(phase + "walk out using ordinary client movement", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                int forward = mc.gameSettings.keyBindForward.getKeyCode();
                c.afterTest(() -> KeyBinding.setKeyBindState(forward, false));
                KeyBinding.setKeyBindState(forward, true);
            })
            .awaitServer(phase + "walking away cancels the warning", () -> {
                if (fixture.player.posZ < portal[2] + 2)
                    throw new AssertionError("Movement packets have not carried the android out of the portal");
                if (AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .armed()) throw new AssertionError("Leaving the portal did not cancel its countdown");
                escapedAt[0] = fixture.dock.world.getTotalWorldTime();
            })
            .client(
                phase + "stop walking",
                c -> KeyBinding
                    .setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindForward.getKeyCode(), false))
            .withinTicks(400)
            .awaitServerAccelerated(phase + "cancelled warning does not explode later", 10, () -> {
                if (fixture.dock.world.getTotalWorldTime() - escapedAt[0] <= ConfigMachines.android.countdownTicks + 5)
                    throw new AssertionError("Waiting past the cancelled deadline");
                if (fixture.player.timeUntilPortal > 0)
                    throw new AssertionError("Waiting for the ordinary portal cooldown before reentry");
                if (!AndroidControl.isAndroid(fixture.player) || AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .isEmpty()) throw new AssertionError("The cancelled portal warning destroyed the android");
            })
            .server(
                phase + "reenter the portal and remain inside",
                () -> fixture.player.playerNetServerHandler
                    .setPlayerLocation(portal[0] + .5, portal[1], portal[2] + .5, 0, 0))
            .withinTicks(200)
            .awaitServer(phase + "sustained contact arms the warning again", () -> {
                if (!AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .armed()) throw new AssertionError("Waiting for the native portal warmup after reentry");
            })
            .withinTicks(300)
            .awaitServerAccelerated(phase + "continuous portal contact eventually destroys the android", 10, () -> {
                fixture.assertBiological();
                if (AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .isPresent()) throw new AssertionError("Sustained portal contact did not finish self-destruction");
            })
            .awaitClient(phase + "operator returns alive", c -> fixture.assertClientControl(false));
    }

    /** Native views cover inactive and controlled models, sleeves and the full-screen biological injury warning. */
    @GameTest(timeoutTicks = 1200)
    public static void androidAppearanceAndBiologicalDamage(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        float[] androidHealth = new float[1];
        List<CompletableFuture<File>> frames = new ArrayList<>();
        float[] viewYaw = new float[1];
        long[] controlledAt = new long[1];
        ClientTest.scenario(helper)
            .server("prepare the real capsule and dock", () -> fixture.prepare(helper, false))
            .awaitServer("machines have formed and have power", fixture::assertPowered)
            .server("manufacture and view the inactive android", () -> {
                fixture.manufacture();
                BodyState body = AndroidControl.bodies()
                    .body(fixture.bodyId)
                    .orElseThrow()
                    .state();
                double dx = body.x() - (fixture.dock.x + .5);
                double dz = body.z() - (fixture.dock.z + .5);
                double distance = Math.hypot(dx, dz);
                viewYaw[0] = (float) Math.toDegrees(Math.atan2(dx, -dz));
                fixture.player.playerNetServerHandler.setPlayerLocation(
                    body.x() + dx / distance * 1.8,
                    body.y(),
                    body.z() + dz / distance * 1.8,
                    viewYaw[0],
                    12);
            })
            .awaitClient("inactive entity reached the native renderer", c -> {
                boolean visible = false;
                for (Object candidate : Minecraft.getMinecraft().theWorld.loadedEntityList) {
                    if (candidate instanceof EntityBody body && fixture.bodyId.equals(body.bodyId()) && !body.isDead)
                        visible = true;
                }
                if (!visible) throw new AssertionError("The inactive android has not reached the client");
            })
            .capture("android-inactive-skin-layers")
            .server("return to the capsule and control the same body", () -> {
                fixture.positionBiologicalBody(helper);
                if (!AndroidControl.activate(fixture.player, fixture.capsule.station.anchor(), fixture.bodyId))
                    throw new AssertionError(
                        "Could not control the rendered android: " + fixture.sessionContext()
                            + ", inside="
                            + fixture.capsule.station.contains(fixture.player)
                            + ", powered="
                            + fixture.capsule.station.isPowered());
                fixture.assertActivated();
            })
            .awaitClient("control was acknowledged", c -> {
                fixture.assertClientControl(true);
                controlledAt[0] = System.nanoTime();
            })
            .awaitClient(
                "boot effect no longer obscures the skin",
                c -> {
                    if (System.nanoTime() - controlledAt[0] < 1_000_000_000L)
                        throw new AssertionError("Waiting for the skin capture");
                })
            .client("view the bare android from the front", c -> {
                Minecraft mc = Minecraft.getMinecraft();
                int originalCamera = mc.gameSettings.thirdPersonView;
                c.afterTest(() -> Minecraft.getMinecraft().gameSettings.thirdPersonView = originalCamera);
                mc.thePlayer.rotationYaw = viewYaw[0] + 180;
                mc.thePlayer.rotationPitch = 0;
                mc.gameSettings.thirdPersonView = 2;
            })
            .capture("android-controlled-skin-layers")
            .client("view the first person sleeve", c -> Minecraft.getMinecraft().gameSettings.thirdPersonView = 0)
            .capture("android-first-person-skin-layers")
            .client("frame the sky for the full-screen warning regression", c -> {
                var player = Minecraft.getMinecraft().thePlayer;
                float pitch = player.rotationPitch;
                c.afterTest(() -> Minecraft.getMinecraft().thePlayer.rotationPitch = pitch);
                player.rotationPitch = -70;
            })
            .client("capture the undamaged view", c -> frames.add(c.capture("biological-damage-baseline")))
            .awaitClient("baseline frame is available", c -> {
                if (!frames.get(0)
                    .isDone()) throw new AssertionError("Waiting for the baseline capture");
                frames.get(0)
                    .join();
            })
            .server("damage the biological body without damaging the controlled android", () -> {
                androidHealth[0] = fixture.player.getHealth();
                fixture.damageBiology();
                if (fixture.player.getHealth() != androidHealth[0])
                    throw new AssertionError("Biological injury also damaged the android");
            })
            .awaitServer("actual biological injury reaches the canonical body", () -> {
                if (AndroidControl.bodies()
                    .biological(fixture.player.getUniqueID())
                    .orElseThrow()
                    .state()
                    .health() != fixture.damagedBiologicalHealth)
                    throw new AssertionError("Biological damage has not been observed");
            })
            .client("capture the remote injury warning", c -> frames.add(c.capture("biological-damage-warning")))
            .awaitClient("biological injury produces a red warning across the display", c -> {
                if (!frames.get(1)
                    .isDone()) throw new AssertionError("Waiting for the warning capture");
                assertRedWarning(
                    frames.get(0)
                        .join(),
                    frames.get(1)
                        .join());
                fixture.assertClientControl(true);
                if (Minecraft.getMinecraft().thePlayer.getHealth() != androidHealth[0])
                    throw new AssertionError("The warning changed the android's client health");
            })
            .server("return to the wounded biological body", () -> AndroidControl.endControl(fixture.player))
            .awaitClient("original body is active again", c -> fixture.assertClientControl(false))
            .succeed();
    }

    private static void assertRedWarning(File baselineFile, File warningFile) {
        try {
            BufferedImage baseline = ImageIO.read(baselineFile);
            BufferedImage warning = ImageIO.read(warningFile);
            for (int row : new int[] { 1, 5, 9 }) {
                for (int column : new int[] { 1, 5, 9 }) {
                    int x = baseline.getWidth() * column / 10;
                    int y = baseline.getHeight() * row / 10;
                    int before = baseline.getRGB(x, y);
                    int after = warning.getRGB(x, y);
                    int beforeRed = (before >> 16 & 255) - (before >> 8 & 255);
                    int afterRed = (after >> 16 & 255) - (after >> 8 & 255);
                    if (afterRed < beforeRed + 15) throw new AssertionError(
                        "Biological injury did not tint the full display red at " + column + "," + row);
                }
            }
        } catch (IOException ex) {
            throw new AssertionError("Cannot inspect native warning captures", ex);
        }
    }

    /** Integration contract: capsule damage survives interruption and early player loading rejects stale body data. */
    @GameTest(timeoutTicks = 1200)
    public static void biologicalDamageAndCapsuleLossSurviveNativePlayerReload(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        BiologicalLifeSupportProbe lifeSupport = new BiologicalLifeSupportProbe(fixture);
        float[] beforeDrowning = new float[1];
        Runnable[] drainCapsule = new Runnable[1];
        ClientTest.scenario(helper)
            .server(
                "construct powered capsule, body dock and actual communication fleet",
                () -> fixture.prepare(helper, true, true))
            .awaitServer("capsule and dock are formed and powered", fixture::assertPowered)
            .server("seed the biological body's own food and observe native damage", () -> lifeSupport.prepare(helper))
            .server("activate the android while biology remains physical", fixture::activate)
            .awaitClient("controlled body and nearby terrain arrived", c -> fixture.assertClientControl(true))
            .awaitServer("biological projection exists at the control capsule", fixture::biological)
            .withinTicks(120)
            .awaitServer(
                "biological food and oxygen deplete while the Mars android stays unharmed",
                lifeSupport::assertDepletion)
            .server("remove the projection while its biological supplies are depleted", lifeSupport::removeProjection)
            .withinTicks(60)
            .awaitServer("replacement projection retains hunger and oxygen depletion", lifeSupport::assertRetained)
            .server("exhaust the biological body's remaining finite oxygen tank", lifeSupport::emptyTank)
            .withinTicks(80)
            .awaitServer(
                "oxygen starvation damages the biological body through the native hazard",
                lifeSupport::assertSuffocation)
            .server("remove the oxygen-starved biological projection", lifeSupport::removeProjection)
            .withinTicks(60)
            .awaitServer("oxygen deprivation progress survives projection replacement", lifeSupport::assertRetained)
            .server("refill the biological tank before drowning and return checks", lifeSupport::refillTank)
            .server("submerge the biological capsule occupant in real water", () -> {
                EntityBody body = fixture.biological();
                BodyState state = body.currentState();
                int x = (int) Math.floor(body.posX);
                int y = (int) Math.floor(body.posY);
                int z = (int) Math.floor(body.posZ);
                if (!fixture.capsule.world.isAirBlock(x, y, z) || !fixture.capsule.world.isAirBlock(x, y + 1, z))
                    throw new AssertionError("Drowning regression requires the empty capsule interior");
                drainCapsule[0] = () -> {
                    fixture.capsule.world.setBlockToAir(x, y + 1, z);
                    fixture.capsule.world.setBlockToAir(x, y, z);
                };
                helper.afterTest(drainCapsule[0]);
                fixture.capsule.world.setBlock(x, y, z, Blocks.water);
                fixture.capsule.world.setBlock(x, y + 1, z, Blocks.water);
                beforeDrowning[0] = body.getHealth();
                body.setAir(0);
                AndroidControl.observe(body, state.withPhysicalState(body));
            })
            .withinTicks(100)
            .awaitServer("uncontrolled biological body takes native drowning damage", () -> {
                EntityBody body = fixture.biological();
                if (!body.isInsideOfMaterial(net.minecraft.block.material.Material.water))
                    throw new AssertionError("Biological body did not remain submerged");
                if (lifeSupport.drowningHits == 0 || body.getHealth() >= beforeDrowning[0]) throw new AssertionError(
                    "Submerged biological body did not take drowning damage: air=" + body
                        .getAir() + ", health=" + body.getHealth() + ", drowningHits=" + lifeSupport.drowningHits);
                if (AndroidControl.bodies()
                    .biological(fixture.player.getUniqueID())
                    .orElseThrow()
                    .state()
                    .health() != body.getHealth())
                    throw new AssertionError("Drowning damage was not persisted in the biological body");
            })
            .server("drain the capsule before testing biological return", () -> drainCapsule[0].run())
            .server("ordinary damage and capsule destruction return the latest biological state", () -> {
                lifeSupport.beforeReturn = fixture.biological()
                    .currentState();
                fixture.damageBiologyAndBreakCapsule();
                lifeSupport.assertReturned();
            })
            .awaitClient("client receives the damaged biological body", c -> fixture.assertClientControl(false))
            .server(
                "native early player load restores canonical body over stale vanilla data",
                fixture::assertNativeReload)
            .awaitClient("real operator remains biological after unconnected loader fixture", c -> {
                fixture.assertClientControl(false);
                fixture.assertStack(
                    Minecraft.getMinecraft().thePlayer.inventory.mainInventory[0],
                    Items.diamond,
                    11,
                    "biological");
            })
            .succeed();
    }

    public static final class BiologicalLifeSupportProbe {

        private final Fixture fixture;
        private int starvationHits;
        private int oxygenHits;
        private int drowningHits;
        private UUID biologicalId;
        private BodyState depleted;
        private BodyState beforeReturn;
        private float androidHealth;
        private float beforeSuffocation;

        private BiologicalLifeSupportProbe(Fixture fixture) {
            this.fixture = fixture;
        }

        private void prepare(GameTestHelper helper) {
            MinecraftForge.EVENT_BUS.register(this);
            helper.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(this));
            net.minecraft.world.EnumDifficulty difficulty = fixture.capsule.world.difficultySetting;
            fixture.capsule.world.difficultySetting = net.minecraft.world.EnumDifficulty.NORMAL;
            helper.afterTest(() -> fixture.capsule.world.difficultySetting = difficulty);
            // Mars cold otherwise hits immediately before drowning and masks it through vanilla damage immunity.
            var effects = GalaxiaAPI.getEffects(fixture.player);
            int originalTemperature = effects.getBaseTemp();
            effects.setBaseTemp(273);
            helper.afterTest(() -> effects.setBaseTemp(originalTemperature));
            NBTTagCompound food = new NBTTagCompound();
            fixture.player.getFoodStats()
                .writeNBT(food);
            food.setInteger("foodLevel", 1);
            food.setFloat("foodSaturationLevel", 0);
            food.setFloat("foodExhaustionLevel", 3.9999F);
            food.setInteger("foodTickTimer", 79);
            fixture.player.getFoodStats()
                .readNBT(food);
        }

        @SubscribeEvent
        public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if (!(event.entityLiving instanceof EntityBody body) || body.worldObj.isRemote
                || !fixture.player.getUniqueID()
                    .equals(body.operatorId()))
                return;
            if (event.source == DamageSource.starve) starvationHits++;
            if (event.source == HazardOxygen.noOxygenDamage) oxygenHits++;
            if (event.source == DamageSource.drown) drowningHits++;
        }

        private int oxygen(BodyState state) {
            ItemStack tank = state.bauble(Fixture.oxygenSlot());
            return ((ItemOxygenTank) tank.getItem()).getCurrentOxygen(tank);
        }

        private void assertDepletion() {
            EntityBody body = fixture.biological();
            BodyState state = body.currentState();
            if (body.dimension != DimensionEnum.MARS.getId())
                throw new AssertionError("Biology is not exposed to native Mars hazards");
            if (oxygen(state) >= 500 || oxygen(state) <= 0)
                throw new AssertionError("Biological tank has not drained independently");
            if (state.write()
                .getCompoundTag("food")
                .getInteger("foodLevel") != 0 || starvationHits == 0)
                throw new AssertionError("Biological hunger did not deplete and apply starvation damage");
            if (fixture.player.getHealth() != fixture.player.getMaxHealth())
                throw new AssertionError("Biological starvation damaged the android");
            androidHealth = fixture.player.getHealth();
        }

        private void removeProjection() {
            EntityBody body = fixture.biological();
            biologicalId = body.bodyId();
            depleted = body.currentState();
            body.worldObj.removeEntity(body);
        }

        private void assertRetained() {
            EntityBody body = fixture.biological();
            BodyState state = body.currentState();
            if (!body.bodyId()
                .equals(biologicalId) || oxygen(state) > oxygen(depleted)
                || state.lowOxygenDuration() < depleted.lowOxygenDuration()
                || state.write()
                    .getCompoundTag("food")
                    .getInteger("foodLevel") != 0
                || state.write()
                    .getCompoundTag("food")
                    .getFloat("foodExhaustionLevel")
                    < depleted.write()
                        .getCompoundTag("food")
                        .getFloat("foodExhaustionLevel"))
                throw new AssertionError("Projection cleanup reset biological life support state");
        }

        private void emptyTank() {
            EntityBody body = fixture.biological();
            ItemStack tank = body.currentState()
                .bauble(Fixture.oxygenSlot());
            ((ItemOxygenTank) tank.getItem()).drainTank(tank, oxygen(body.currentState()));
            body.writeBauble(Fixture.oxygenSlot(), tank);
            beforeSuffocation = body.getHealth();
        }

        private void assertSuffocation() {
            EntityBody body = fixture.biological();
            if (oxygenHits == 0 || body.getHealth() >= beforeSuffocation || body.lowOxygenDuration() <= 0)
                throw new AssertionError("Empty biological oxygen tank did not trigger persisted suffocation");
            if (fixture.player.getHealth() != androidHealth)
                throw new AssertionError("Biological oxygen shortage damaged the android");
        }

        private void refillTank() {
            EntityBody body = fixture.biological();
            ItemStack tank = body.currentState()
                .bauble(Fixture.oxygenSlot());
            ((ItemOxygenTank) tank.getItem()).fillTank(tank, 1000);
            body.writeBauble(Fixture.oxygenSlot(), tank);
        }

        private void assertReturned() {
            NBTTagCompound food = new NBTTagCompound();
            fixture.player.getFoodStats()
                .writeNBT(food);
            if (!food.equals(
                beforeReturn.write()
                    .getCompoundTag("food")))
                throw new AssertionError("Returning to biology reset its hunger progress");
            ItemStack tank = PlayerHandler.getPlayerBaubles(fixture.player)
                .getStackInSlot(Fixture.oxygenSlot());
            if (((ItemOxygenTank) tank.getItem()).getCurrentOxygen(tank) != oxygen(beforeReturn))
                throw new AssertionError("Returning to biology reset its oxygen supply");
        }
    }

    /** Harness exception: observes the actual server sound broadcast without replacing its normal world listeners. */
    private static final class ExplosionSoundObserver implements net.minecraft.world.IWorldAccess {

        private final List<double[]> positions = new ArrayList<>();

        @Override
        public void playSound(String sound, double x, double y, double z, float volume, float pitch) {
            if (sound.equals("random.explode")) positions.add(new double[] { x, y, z });
        }

        @Override
        public void markBlockForUpdate(int x, int y, int z) {}

        @Override
        public void markBlockForRenderUpdate(int x, int y, int z) {}

        @Override
        public void markBlockRangeForRenderUpdate(int x, int y, int z, int x2, int y2, int z2) {}

        @Override
        public void playSoundToNearExcept(net.minecraft.entity.player.EntityPlayer player, String sound, double x,
            double y, double z, float volume, float pitch) {}

        @Override
        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {}

        @Override
        public void onEntityCreate(net.minecraft.entity.Entity entity) {}

        @Override
        public void onEntityDestroy(net.minecraft.entity.Entity entity) {}

        @Override
        public void playRecord(String record, int x, int y, int z) {}

        @Override
        public void broadcastSound(int sound, int x, int y, int z, int data) {}

        @Override
        public void playAuxSFX(net.minecraft.entity.player.EntityPlayer player, int sound, int x, int y, int z,
            int data) {}

        @Override
        public void destroyBlockPartially(int entity, int x, int y, int z, int damage) {}

        @Override
        public void onStaticEntitiesChanged() {}
    }

    private static final class Fixture {

        private final AndroidStationGameTests.Fixture capsule = new AndroidStationGameTests.Fixture();
        private final AndroidStationGameTests.Fixture dock = new AndroidStationGameTests.Fixture();
        private final List<CelestialAsset.ID> satellites = new ArrayList<>();
        private final String marker = UUID.randomUUID()
            .toString();
        private EntityPlayerMP player;
        private BodyState original;
        private WorldSettings.GameType originalGameType;
        private int originalLevel;
        private int originalTotal;
        private float originalExperience;
        private UUID bodyId;
        private UUID otherPlanetBody;
        private WorldServer departure;
        private double dropX;
        private double dropY;
        private double dropZ;
        private double activeX;
        private double activeY;
        private double activeZ;
        private double biologicalX;
        private double biologicalY;
        private double biologicalZ;
        private float damagedBiologicalHealth;
        private boolean wrotePlayerFixtureFile;
        private int miningX;
        private int miningY;
        private int miningZ;
        private int miningOpportunityTicks;
        private long miningStarted;

        void prepare(GameTestHelper helper, boolean mars) {
            prepare(helper, mars, false);
        }

        void prepare(GameTestHelper helper, boolean mars, boolean biologicalOnMars) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            if (AndroidControl.isAndroid(player))
                throw new AssertionError("Test operator already controls another body");
            if (player.openContainer != player.inventoryContainer || player.inventory.getItemStack() != null)
                throw new AssertionError("Lifecycle fixture requires the operator's containers to be settled");
            ContainerPlayer inventory = (ContainerPlayer) player.inventoryContainer;
            for (int slot = 0; slot < inventory.craftMatrix.getSizeInventory(); slot++) {
                if (inventory.craftMatrix.getStackInSlot(slot) != null)
                    throw new AssertionError("Lifecycle fixture requires an empty personal crafting matrix");
            }
            original = BodyState.capture(player);
            originalGameType = player.theItemInWorldManager.getGameType();
            originalLevel = player.experienceLevel;
            originalTotal = player.experienceTotal;
            originalExperience = player.experience;
            // Horizon server cleanup is FIFO. End control and restore the actor before the station fixtures disappear.
            helper.afterTest(this::cleanup);
            WorldServer source = (WorldServer) helper.getWorld();
            WorldServer target = source;
            if (mars || biologicalOnMars) {
                int dimension = DimensionEnum.MARS.getId();
                if (DimensionManager.getWorld(dimension) == null) DimensionManager.initDimension(dimension);
                target = DimensionManager.getWorld(dimension);
                if (target == null) throw new AssertionError("Mars did not initialize");
                if (biologicalOnMars) source = target;
            }
            int height = sharedClearHeight(source, target);
            capsule.y = height;
            dock.y = height;
            capsule.prepare(helper, true, 32, source);
            dock.prepare(helper, false, 48, target);
            helper.onEachTick("supply native energy hatches throughout control scenarios", () -> {
                capsule.energyHatch.increaseStoredEnergyUnits(capsule.energyHatch.getEUCapacity(), true);
                dock.energyHatch.increaseStoredEnergyUnits(dock.energyHatch.getEUCapacity(), true);
            });
            addCommunication(CelestialObjectId.OVERWORLD);
            if (mars) addCommunication(CelestialObjectId.MARS);
            SatelliteNetworkService.rebuild(capsule.team, 0);
            player.setGameType(WorldSettings.GameType.SURVIVAL);
            player.closeScreen();
            player.inventoryContainer.onContainerClosed(player);
            Arrays.fill(player.inventory.mainInventory, null);
            Arrays.fill(player.inventory.armorInventory, null);
            clearBaubles();
            player.inventory.currentItem = 0;
            player.inventory.mainInventory[0] = stack(Items.diamond, 11, "biological");
            player.inventory.armorInventory[2] = stack(Items.iron_chestplate, 1, "biological");
            equipBaubles("biological", 500);
            if (biologicalOnMars) {
                PlayerHandler.getPlayerBaubles(player)
                    .setInventorySlotContents(
                        Galaxia.oxygenMaskSlots[0],
                        stack(GalaxiaItemList.OXYGEN_MASK.getItem(), 1, "biological"));
                PlayerHandler.getPlayerBaubles(player)
                    .setInventorySlotContents(
                        Galaxia.thermalSlot[0],
                        stack(GalaxiaItemList.THERMAL_PROTECTION_COLD.getItem(), 1, "biological"));
            }
            player.experienceLevel = 47;
            player.experienceTotal = 4700;
            player.experience = .25F;
            positionBiologicalBody(helper);
            biologicalX = player.posX;
            biologicalY = player.posY;
            biologicalZ = player.posZ;
        }

        private void positionBiologicalBody(GameTestHelper helper) {
            BodyState interior = capsule.station.captureOccupant(player);
            if (player.dimension != capsule.world.provider.dimensionId) {
                player.mcServer.getConfigurationManager()
                    .transferPlayerToDimension(
                        player,
                        capsule.world.provider.dimensionId,
                        new Teleporter(capsule.world) {

                            @Override
                            public void placeInPortal(net.minecraft.entity.Entity actor, double x, double y, double z,
                                float yaw) {
                                actor.setLocationAndAngles(interior.x(), interior.y(), interior.z(), 0, 0);
                            }
                        });
            }
            player.playerNetServerHandler.setPlayerLocation(interior.x(), interior.y(), interior.z(), 0, 0);
            assertCollisionFree("biological capsule placement", capsule.world);
            helper.recordDiagnostic("Biological fixture is standing inside the capsule at " + player.boundingBox);
        }

        private void assertCollisionFree(String phase, WorldServer world) {
            if (!world.getCollidingBoundingBoxes(player, player.boundingBox)
                .isEmpty())
                throw new AssertionError(phase + " collides at " + player.boundingBox + ", " + sessionContext());
            if (world.getCollidingBoundingBoxes(
                player,
                player.boundingBox.copy()
                    .offset(0, -.05, 0))
                .isEmpty()) throw new AssertionError(phase + " is not standing on its test floor, " + sessionContext());
        }

        private int sharedClearHeight(WorldServer source, WorldServer target) {
            int height = 128;
            int baseX = (int) Math.floor(player.posX);
            int baseZ = (int) Math.floor(player.posZ);
            for (WorldServer world : source == target ? new WorldServer[] { source }
                : new WorldServer[] { source, target }) {
                for (int offset : new int[] { 32, 48 }) {
                    for (int chunkX = (baseX + offset - 3) >> 4; chunkX <= (baseX + offset + 3) >> 4; chunkX++) {
                        for (int chunkZ = (baseZ - 3) >> 4; chunkZ <= (baseZ + 3) >> 4; chunkZ++) {
                            world.theChunkProviderServer.loadChunk(chunkX, chunkZ);
                        }
                    }
                    // Both station footprints include the mirrored mining coordinates at capsule x + 2, z - 2.
                    for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                        height = Math.max(height, world.getTopSolidOrLiquidBlock(baseX + offset + dx, baseZ + dz) + 4);
                    }
                }
            }
            if (height + 3 >= Math.min(source.getActualHeight(), target.getActualHeight()))
                throw new AssertionError("No shared clear height for the source and destination station fixtures");
            return height;
        }

        private void addCommunication(CelestialObjectId planet) {
            for (int index = 0; index < 10; index++) {
                CelestialAsset satellite = CelestialAsset.create(
                    CelestialObjectKey.registered(planet),
                    CelestialAsset.Kind.SATELLITE,
                    true,
                    SatelliteKind.COMMUNICATION);
                satellites.add(satellite.assetId);
                CelestialAssetStore.registerAsset(capsule.team, satellite);
            }
        }

        void assertPowered() {
            if (!capsule.station.isPowered() || !dock.station.isPowered())
                throw new AssertionError("Real capsule and dock must be formed and powered");
        }

        void activate() {
            manufacture();
            assertCollisionFree("biological body before activation", capsule.world);
            if (!AndroidControl.activate(player, capsule.station.anchor(), bodyId))
                throw new AssertionError("Real powered capsule and communication network rejected activation");
            assertActivated();
            if (dock.world != capsule.world) {
                if (Galaxia.thermalSlot == null || Galaxia.thermalSlot.length == 0)
                    throw new AssertionError("No real thermal protection slot registered");
                PlayerHandler.getPlayerBaubles(player)
                    .setInventorySlotContents(
                        Galaxia.thermalSlot[0],
                        stack(GalaxiaItemList.THERMAL_PROTECTION_COLD.getItem(), 1, "android"));
            }
        }

        void prepareCapsuleSelection() {
            manufacture();
            BodyState inside = capsule.station.captureOccupant(player);
            ForgeDirection back = capsule.station.getCurrentFacing()
                .getRelativeBackInWorld();
            player.playerNetServerHandler.setPlayerLocation(
                capsule.x + .5 - back.offsetX * 3,
                capsule.y,
                capsule.z + .5 - back.offsetZ * 3,
                0,
                0);
            if (AndroidControl.activate(player, capsule.station.anchor(), bodyId))
                throw new AssertionError("Capsule accepted connection while the operator was outside");
            if (SatelliteNetworkService.hasReservation(capsule.team, bodyId))
                throw new AssertionError("Rejected outside connection reserved bandwidth");
            player.playerNetServerHandler.setPlayerLocation(inside.x(), inside.y(), inside.z(), 0, 0);
            int mars = DimensionEnum.MARS.getId();
            AndroidBodies.Anchor remoteDock = new AndroidBodies.Anchor(UUID.randomUUID(), mars, 10000, 200, 10000);
            try {
                otherPlanetBody = AndroidControl.bodies()
                    .manufacture(
                        UUID.randomUUID(),
                        capsule.team,
                        remoteDock,
                        BodyState.androidAt(mars, 10000.5, 200, 10000.5, 20),
                        AndroidBodies.Chassis.EV,
                        1000000);
                dock.trackBody(otherPlanetBody);
            } catch (IOException ex) {
                throw new AssertionError("Cannot prepare the other destination's body metadata", ex);
            }
            assertCollisionFree("biological body before capsule selection", capsule.world);
            assertOccupiedCapsuleRejectsControl();
        }

        /** Bug regression: another operator occupying the capsule rejects control without leaking its reservation. */
        private void assertOccupiedCapsuleRejectsControl() {
            AndroidBodies bodies = AndroidControl.bodies();
            UUID otherOperator = UUID.randomUUID();
            try {
                UUID blocker = bodies.manufacture(
                    UUID.randomUUID(),
                    capsule.team,
                    dock.station.anchor(),
                    BodyState.androidAt(dock.world.provider.dimensionId, dock.x + 3.5, dock.y, dock.z + 3.5, 20),
                    AndroidBodies.Chassis.EV,
                    1000000);
                dock.trackBody(blocker);
                BodyState blockerState = bodies.body(blocker)
                    .orElseThrow()
                    .state();
                bodies.activate(
                    otherOperator,
                    capsule.team,
                    blocker,
                    capsule.station.anchor(),
                    BodyState.capture(player));
                try {
                    if (AndroidControl.activate(player, capsule.station.anchor(), bodyId))
                        throw new AssertionError("An occupied capsule accepted another operator");
                    if (bodies.isControlled(bodyId) || bodies.session(player.getUniqueID())
                        .isPresent()) throw new AssertionError("Rejected capsule activation assigned its target body");
                    if (SatelliteNetworkService.hasReservation(capsule.team, bodyId))
                        throw new AssertionError("Rejected capsule activation retained its bandwidth reservation");
                } finally {
                    bodies.deactivate(otherOperator, blockerState);
                }
            } catch (IOException ex) {
                throw new AssertionError("Cannot prepare or release the occupied capsule fixture", ex);
            }
        }

        private void manufacture() {
            try {
                bodyId = AndroidControl.manufacture(
                    capsule.team,
                    dock.station.anchor(),
                    UUID.randomUUID(),
                    AndroidBodies.Chassis.EV,
                    AndroidBodies.Chassis.EV.capacity());
            } catch (IOException ex) {
                throw new AssertionError(
                    "Cannot manufacture fixture android: dockFormed=" + dock.station.isStructureValid()
                        + ", dockPowered="
                        + dock.station.isPowered()
                        + ", nearbyEntities="
                        + dock.world.getEntitiesWithinAABB(
                            net.minecraft.entity.EntityLivingBase.class,
                            net.minecraft.util.AxisAlignedBB
                                .getBoundingBox(dock.x - 2, dock.y, dock.z - 3, dock.x + 3, dock.y + 3, dock.z + 2)),
                    ex);
            }
        }

        void assertActivated() {
            assertActiveSession("immediately after activation");
            BodyState biological = AndroidControl.bodies()
                .biological(player.getUniqueID())
                .orElseThrow()
                .state();
            biologicalX = biological.x();
            biologicalY = biological.y();
            biologicalZ = biological.z();
            assertCollisionFree("controlled android placement", dock.world);
            if (player.inventory.mainInventory[0] != null || player.inventory.armorInventory[2] != null
                || PlayerHandler.getPlayerBaubles(player)
                    .getStackInSlot(rcsSlot()) != null)
                throw new AssertionError("Biological belongings crossed into the manufactured body");
            if (player.experienceLevel != 47) throw new AssertionError("Activation changed account progression");
            activeX = player.posX;
            activeY = player.posY;
            activeZ = player.posZ;
        }

        void reactivate() {
            if (!AndroidControl.activate(player, capsule.station.anchor(), bodyId)) {
                var target = AndroidControl.bodies()
                    .body(bodyId)
                    .orElseThrow();
                throw new AssertionError(
                    "Returned android could not be controlled again: capsulePower=" + capsule.station.isPowered()
                        + ", formed="
                        + capsule.station.isStructureValid()
                        + ", inside="
                        + capsule.station.contains(player)
                        + ", permission="
                        + capsule.station.canOperate(player)
                        + ", playerHealth="
                        + player.getHealth()
                        + ", riding="
                        + player.ridingEntity
                        + ", passenger="
                        + player.riddenByEntity
                        + ", battery="
                        + target.battery()
                        + ", bodyHealth="
                        + target.state()
                            .health()
                        + ", armed="
                        + target.armed()
                        + ", capsuleOccupied="
                        + AndroidControl.bodies()
                            .isCapsuleOccupied(capsule.station.anchor()));
            }
            assertStack(player.inventory.mainInventory[0], Items.iron_ingot, 7, "android");
            assertStack(player.inventory.armorInventory[0], Items.diamond_boots, 1, "android");
            assertRole(
                PlayerHandler.getPlayerBaubles(player)
                    .getStackInSlot(rcsSlot()),
                "android");
            activeX = player.posX;
            activeY = player.posY;
            activeZ = player.posZ;
        }

        void equipAndCheckRestrictions() {
            assertActiveSession("before checking native player restrictions");
            player.inventory.mainInventory[0] = stack(Items.iron_ingot, 7, "android");
            player.inventory.armorInventory[0] = stack(Items.diamond_boots, 1, "android");
            equipBaubles("android", 700);
            player.sendContainerToPlayer(player.inventoryContainer);
            player.displayGUIChest(player.getInventoryEnderChest());
            if (player.openContainer != player.inventoryContainer)
                throw new AssertionError("Native Ender Chest accepted the android actor");
            float damaged = player.getHealth() - 4;
            if (damaged <= 0) throw new AssertionError("Healing restriction fixture needs a surviving damaged body");
            player.setHealth(damaged);
            player.heal(2);
            player.setHealth(player.getMaxHealth());
            if (player.getHealth() != damaged) throw new AssertionError("Android healed without a dock repair");

            ChunkCoordinates overworldSpawn = player.getBedLocation(0);
            ChunkCoordinates localSpawn = player.getBedLocation(player.dimension);
            player.setSpawnChunk(new ChunkCoordinates(123, 90, 456), true);
            player.setSpawnChunk(new ChunkCoordinates(321, 91, 654), true, 0);
            if (!Objects.equals(overworldSpawn, player.getBedLocation(0))
                || !Objects.equals(localSpawn, player.getBedLocation(player.dimension)))
                throw new AssertionError("Android changed the operator's biological respawn anchor");

            player.getFoodStats()
                .addExhaustion(6);
            NBTTagCompound food = new NBTTagCompound();
            player.getFoodStats()
                .writeNBT(food);
            player.getFoodStats()
                .onUpdate(player);
            NBTTagCompound after = new NBTTagCompound();
            player.getFoodStats()
                .writeNBT(after);
            if (!food.equals(after)) throw new AssertionError("Android retained biological food metabolism");
            ItemStack tank = PlayerHandler.getPlayerBaubles(player)
                .getStackInSlot(oxygenSlot());
            ItemOxygenTank tankItem = (ItemOxygenTank) tank.getItem();
            int oxygen = tankItem.getCurrentOxygen(tank);
            int lowOxygen = GalaxiaPlayerProperties.get(player).lowOxygenDuration;
            new HazardOxygen().apply(
                EffectBuilder.builder()
                    .oxygenPercent(0)
                    .build(),
                player,
                Optional.empty());
            if (tankItem.getCurrentOxygen(tank) != oxygen
                || GalaxiaPlayerProperties.get(player).lowOxygenDuration != lowOxygen
                || !player.canBreatheUnderwater())
                throw new AssertionError("Android consumed breathing oxygen or accumulated suffocation state");
        }

        void assertBiological() {
            if (AndroidControl.isAndroid(player) || player.dimension != capsule.world.provider.dimensionId)
                throw new AssertionError("Operator has not returned to its biological body");
            if (player.isDead || player.getHealth() <= 0) throw new AssertionError("Operator died with the android");
            assertStack(player.inventory.mainInventory[0], Items.diamond, 11, "biological");
            assertStack(player.inventory.armorInventory[2], Items.iron_chestplate, 1, "biological");
            assertRole(
                PlayerHandler.getPlayerBaubles(player)
                    .getStackInSlot(rcsSlot()),
                "biological");
            if (player.inventory.armorInventory[0] != null)
                throw new AssertionError("Android armor crossed back to biology");
            if (player.experienceLevel != 47 || player.experienceTotal != 4700)
                throw new AssertionError("Body return changed account progression");
        }

        void assertClientSatelliteNetwork(long reservedKbps) {
            for (CelestialAsset.ID id : satellites) {
                if (CelestialAssetStore.CLIENT.findAssetInternal(id) == null)
                    throw new AssertionError("A communication satellite disappeared from the client starmap");
            }
            var state = com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState.current();
            for (CelestialObjectId body : new CelestialObjectId[] { CelestialObjectId.OVERWORLD,
                CelestialObjectId.MARS }) {
                if (state.usedKbps(com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey.registered(body))
                    != reservedKbps)
                    throw new AssertionError("Client satellite bandwidth does not match the control reservation");
            }
            if (reservedKbps > 0 && state.links()
                .stream()
                .noneMatch(link -> link.usedKbps() >= reservedKbps))
                throw new AssertionError("The starmap link does not expose reserved bandwidth");
        }

        void assertClientControl(boolean expected) {
            var actor = Minecraft.getMinecraft().thePlayer;
            if (actor == null || AndroidClient.isAndroid(actor) != expected)
                throw new AssertionError("Client body-control state has not synchronized");
            int dimension = expected ? dock.world.provider.dimensionId : capsule.world.provider.dimensionId;
            if (actor.dimension != dimension) throw new AssertionError("Client is in the wrong body dimension");
            double x = expected ? activeX : biologicalX;
            double y = expected ? activeY : biologicalY;
            double z = expected ? activeZ : biologicalZ;
            // Client player posY includes its eye offset. The bounding-box floor matches server coordinates.
            if (Math.abs(actor.posX - x) > .25 || Math.abs(actor.boundingBox.minY - y) > .25
                || Math.abs(actor.posZ - z) > .25
                || Math.abs(actor.prevPosX - actor.posX) > 1
                || Math.abs(actor.prevPosY - actor.posY) > 1
                || Math.abs(actor.prevPosZ - actor.posZ) > 1)
                throw new AssertionError("Selected client body position has not settled");
            int floorX = (int) Math.floor(x), floorY = (int) Math.floor(y) - 1, floorZ = (int) Math.floor(z);
            if (!actor.worldObj.blockExists(floorX, floorY, floorZ) || actor.worldObj
                .getCollidingBoundingBoxes(
                    actor,
                    actor.boundingBox.copy()
                        .offset(0, -.1, 0))
                .isEmpty()) throw new AssertionError("Selected body's fixture floor has not reached the client");
            if (expected && actor.worldObj.getBlock(dock.x, dock.y, dock.z) != GalaxiaBlocksEnum.ANDROID_DOCK.get())
                throw new AssertionError("Destination dock block has not reached the client");
        }

        void preparePendingMining() {
            miningX = capsule.x + 2;
            miningY = capsule.y;
            miningZ = capsule.z - 2;
            for (AndroidStationGameTests.Fixture station : new AndroidStationGameTests.Fixture[] { capsule, dock }) {
                station.world.getChunkFromBlockCoords(miningX, miningZ);
                if (!station.world.isAirBlock(miningX, miningY, miningZ))
                    throw new AssertionError("Mining transition fixture space is occupied");
                station.placed.add(new BlockPos(miningX, miningY, miningZ));
                if (!station.world.setBlock(miningX, miningY, miningZ, Blocks.dirt, 0, 3))
                    throw new AssertionError("Cannot place the source and destination mining controls");
            }
            float progress = Blocks.dirt
                .getPlayerRelativeBlockHardness(player, capsule.world, miningX, miningY, miningZ);
            if (progress <= 0 || progress >= .7F) throw new AssertionError("Mining fixture must finish asynchronously");
            // Allow enough ordinary updates even if the new actor initially receives the vanilla airborne penalty.
            miningOpportunityTicks = (int) Math.ceil(5 / progress) + 2;
            player.theItemInWorldManager.onBlockClicked(miningX, miningY, miningZ, 1);
            player.theItemInWorldManager.uncheckedTryHarvestBlock(miningX, miningY, miningZ);
            if (capsule.world.getBlock(miningX, miningY, miningZ) != Blocks.dirt)
                throw new AssertionError("Source mining completed before the body transition");
        }

        void assertMiningCleared() {
            if (dock.world.getTotalWorldTime() - miningStarted < miningOpportunityTicks)
                throw new AssertionError("Waiting for the native asynchronous mining opportunity");
            assertActiveSession("after the asynchronous mining opportunity");
            if (capsule.world.getBlock(miningX, miningY, miningZ) != Blocks.dirt
                || dock.world.getBlock(miningX, miningY, miningZ) != Blocks.dirt)
                throw new AssertionError("A previous body's mining action harvested after the selection changed");
        }

        private void assertActiveSession(String phase) {
            var session = AndroidControl.bodies()
                .session(player.getUniqueID());
            if (!AndroidControl.isAndroid(player) || session.isEmpty()
                || !session.get()
                    .bodyId()
                    .equals(bodyId))
                throw new AssertionError("Android session ended " + phase + ": " + sessionContext());
        }

        private String sessionContext() {
            AndroidBodies bodies = AndroidControl.bodies();
            String physicalBiology = "absent";
            for (Object value : capsule.world.loadedEntityList) {
                if (value instanceof EntityBody body && body.isBiological()
                    && player.getUniqueID()
                        .equals(body.operatorId())) {
                    physicalBiology = "health=" + body.getHealth() + ", dead=" + body.isDead;
                    break;
                }
            }
            return "session=" + bodies.session(player.getUniqueID())
                + ", target="
                + (bodyId == null ? "not manufactured"
                    : bodies.body(bodyId)
                        .map(
                            body -> "health=" + body.state()
                                .health() + ", battery=" + body.battery() + ", countdown=" + body.destructionTicks())
                        .orElse("absent"))
                + ", playerHealth="
                + player.getHealth()
                + ", playerDimension="
                + player.dimension
                + ", capsulePowered="
                + capsule.station.isPowered()
                + ", reserved="
                + (bodyId != null && SatelliteNetworkService.hasReservation(capsule.team, bodyId))
                + ", biologicalCheckpointHealth="
                + bodies.biological(player.getUniqueID())
                    .map(
                        body -> Float.toString(
                            body.state()
                                .health()))
                    .orElse("absent")
                + ", biologicalProjection="
                + physicalBiology
                + ", miningOpportunityTicks="
                + miningOpportunityTicks
                + ", sourceTime="
                + capsule.world.getTotalWorldTime()
                + ", destinationTime="
                + dock.world.getTotalWorldTime();
        }

        EntityBody biological() {
            for (Object value : capsule.world.loadedEntityList) {
                if (value instanceof EntityBody body && !body.isDead
                    && body.isBiological()
                    && player.getUniqueID()
                        .equals(body.operatorId()))
                    return body;
            }
            throw new AssertionError("No physical biological capsule occupant exists");
        }

        void damageBiology() {
            EntityBody biological = biological();
            float before = biological.getHealth();
            if (!biological.attackEntityFrom(DamageSource.generic, 4))
                throw new AssertionError("Capsule occupant rejected ordinary damage");
            damagedBiologicalHealth = biological.getHealth();
            if (damagedBiologicalHealth <= 0 || damagedBiologicalHealth >= before)
                throw new AssertionError("Capsule occupant was invulnerable or died in the nonlethal fixture");
        }

        void damageBiologyAndBreakCapsule() {
            damageBiology();
            capsule.world.setBlockToAir(capsule.x, capsule.y, capsule.z);
            assertBiological();
            if (player.getHealth() != damagedBiologicalHealth)
                throw new AssertionError("Capsule loss returned stale biological health");
        }

        void assertNativeReload() {
            BodyState canonical = AndroidControl.bodies()
                .biological(player.getUniqueID())
                .orElseThrow()
                .state();
            IPlayerFileData files = capsule.world.getSaveHandler()
                .getSaveHandler();
            if (files == null) throw new AssertionError("Integrated world has no native player file manager");
            EntityPlayerMP scratch = new EntityPlayerMP(
                player.mcServer,
                capsule.world,
                player.getGameProfile(),
                new ItemInWorldManager(capsule.world));
            scratch.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
            scratch.inventory.mainInventory[0] = new ItemStack(Items.coal);
            scratch.setHealth(1);
            scratch.setPosition(canonical.x() + 100, canonical.y() + 10, canonical.z() + 100);
            if (scratch.playerNetServerHandler != null || capsule.world.playerEntities.contains(scratch))
                throw new AssertionError("Loader fixture unexpectedly connected or spawned a second player");

            // The integrated owner's native loader can use embedded level.dat Player data before its .dat file.
            NBTTagCompound embedded = player.mcServer.worldServers[0].getWorldInfo()
                .getPlayerNBTTagCompound();
            String[] selectedKeys = { "Inventory", "HealF", "Health", "Pos" };
            NBTBase[] originalTags = new NBTBase[selectedKeys.length];
            boolean embeddedOwner = embedded != null && player.getCommandSenderName()
                .equals(player.mcServer.getServerOwner());
            if (embeddedOwner) {
                for (int index = 0; index < selectedKeys.length; index++) {
                    NBTBase previous = embedded.getTag(selectedKeys[index]);
                    originalTags[index] = previous == null ? null : previous.copy();
                }
            }
            try {
                wrotePlayerFixtureFile = true;
                files.writePlayerData(scratch);
                if (embeddedOwner) {
                    NBTTagCompound stale = new NBTTagCompound();
                    scratch.writeToNBT(stale);
                    for (int index = 0; index < selectedKeys.length; index++) {
                        String key = selectedKeys[index];
                        embedded.setTag(
                            key,
                            stale.getTag(key)
                                .copy());
                    }
                }
                PlayerHandler.clearPlayerBaubles(scratch);
                NBTTagCompound loaded = player.mcServer.getConfigurationManager()
                    .readPlayerDataFromFile(scratch);
                if (loaded == null || loaded.getTagList("Inventory", 10)
                    .tagCount() != 1
                    || ItemStack.loadItemStackFromNBT(
                        loaded.getTagList("Inventory", 10)
                            .getCompoundTagAt(0))
                        .getItem() != Items.coal)
                    throw new AssertionError("Native loader fixture did not actually supply stale physical data");
                assertStack(scratch.inventory.mainInventory[0], Items.diamond, 11, "biological");
                assertRole(
                    PlayerHandler.getPlayerBaubles(scratch)
                        .getStackInSlot(rcsSlot()),
                    "biological");
                if (scratch.getHealth() != canonical.health() || scratch.dimension != canonical.dimension()
                    || scratch.getDistanceSq(canonical.x(), canonical.y(), canonical.z()) > .0001)
                    throw new AssertionError("Native early player loader retained stale physical state");
            } finally {
                if (embeddedOwner) {
                    for (int index = 0; index < selectedKeys.length; index++) {
                        if (originalTags[index] == null) embedded.removeTag(selectedKeys[index]);
                        else embedded.setTag(selectedKeys[index], originalTags[index]);
                    }
                }
                // Baubles is name-keyed, so bind its live cache back to the actual connected operator.
                canonical.apply(player);
                files.writePlayerData(player);
            }
        }

        void recordDeparture() {
            departure = player.getServerForPlayer();
            dropX = player.posX;
            dropY = player.posY;
            dropZ = player.posZ;
        }

        void assertDestroyedLocally() {
            if (AndroidControl.bodies()
                .body(bodyId)
                .isPresent()) throw new AssertionError("Android chassis still exists");
            int cargo = 0;
            for (Object value : departure.loadedEntityList) {
                if (!(value instanceof EntityItem item) || !ours(item.getEntityItem())) continue;
                if (item.getDistanceSq(dropX, dropY, dropZ) > 16)
                    throw new AssertionError("Android belongings were not dropped at the departure body");
                if (item.getEntityItem()
                    .getItem() == Items.iron_ingot) cargo += item.getEntityItem().stackSize;
            }
            if (cargo != 7) throw new AssertionError("Expected one local copy of android cargo, found " + cargo);
            if (departure != capsule.world) {
                for (Object value : capsule.world.loadedEntityList) {
                    if (value instanceof EntityItem item && ours(item.getEntityItem())
                        && item.getEntityItem()
                            .getItem() == Items.iron_ingot)
                        throw new AssertionError("Android cargo crossed into the biological body's dimension");
                }
            }
        }

        private ItemStack stack(Item item, int amount, String role) {
            ItemStack stack = new ItemStack(item, amount);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("androidControlQa", marker);
            tag.setString("bodyRole", role);
            stack.setTagCompound(tag);
            return stack;
        }

        private void equipBaubles(String role, int oxygen) {
            PlayerHandler.getPlayerBaubles(player)
                .setInventorySlotContents(
                    rcsSlot(),
                    stack(GalaxiaItemList.REACTION_CONTROL_SYSTEM_T1.getItem(), 1, role));
            ItemStack tank = stack(GalaxiaItemList.OXYGEN_TANK_T1.getItem(), 1, role);
            ((ItemOxygenTank) tank.getItem()).fillTank(tank, oxygen);
            PlayerHandler.getPlayerBaubles(player)
                .setInventorySlotContents(oxygenSlot(), tank);
        }

        private void clearBaubles() {
            var baubles = PlayerHandler.getPlayerBaubles(player);
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) baubles.setInventorySlotContents(slot, null);
        }

        private static int rcsSlot() {
            if (Galaxia.rcsSlot == null || Galaxia.rcsSlot.length == 0)
                throw new AssertionError("No real RCS slot registered");
            return Galaxia.rcsSlot[0];
        }

        private static int oxygenSlot() {
            if (Galaxia.oxygenSlots == null || Galaxia.oxygenSlots.length == 0)
                throw new AssertionError("No real oxygen slot registered");
            return Galaxia.oxygenSlots[0];
        }

        private boolean ours(ItemStack stack) {
            return stack != null && stack.hasTagCompound()
                && marker.equals(
                    stack.getTagCompound()
                        .getString("androidControlQa"));
        }

        void assertStack(ItemStack stack, Item item, int amount, String role) {
            if (stack == null || stack.getItem() != item || stack.stackSize != amount)
                throw new AssertionError("Incorrect selected-body belongings");
            assertRole(stack, role);
        }

        private void assertRole(ItemStack stack, String role) {
            if (!ours(stack) || !role.equals(
                stack.getTagCompound()
                    .getString("bodyRole")))
                throw new AssertionError("Belongings came from the wrong physical body");
        }

        private void cleanup() {
            if (player == null || original == null) return;
            player.closeScreen();
            AndroidControl.endControl(player);
            if (bodyId != null && AndroidControl.bodies()
                .body(bodyId)
                .isPresent()) {
                try {
                    AndroidControl.bodies()
                        .destroy(bodyId);
                } catch (IOException ex) {
                    throw new AssertionError("Cannot remove test android", ex);
                }
            }
            for (WorldServer world : new WorldServer[] { capsule.world, dock.world }) {
                if (world == null) continue;
                for (Object value : new ArrayList<>(world.loadedEntityList)) {
                    if (value instanceof EntityBody body && bodyId != null && bodyId.equals(body.bodyId())) {
                        body.setDead();
                        world.removeEntity(body);
                    } else if (value instanceof EntityItem item && ours(item.getEntityItem())) {
                        item.setDead();
                        world.removeEntity(item);
                    }
                }
            }
            if (player.dimension != original.dimension()) {
                WorldServer destination = player.mcServer.worldServerForDimension(original.dimension());
                player.mcServer.getConfigurationManager()
                    .transferPlayerToDimension(player, original.dimension(), new Teleporter(destination) {

                        @Override
                        public void placeInPortal(net.minecraft.entity.Entity actor, double x, double y, double z,
                            float yaw) {
                            original.applyLocation(actor);
                        }
                    });
            }
            original.apply(player);
            player.setGameType(originalGameType);
            player.experienceLevel = originalLevel;
            player.experienceTotal = originalTotal;
            player.experience = originalExperience;
            player.playerNetServerHandler
                .sendPacket(new S1FPacketSetExperience(originalExperience, originalTotal, originalLevel));
            player.playerNetServerHandler
                .setPlayerLocation(original.x(), original.y(), original.z(), original.yaw(), original.pitch());
            if (AndroidControl.isManaged(player)) {
                AndroidControl.bodies()
                    .observeOperator(player.getUniqueID(), original);
                try {
                    AndroidControl.bodies()
                        .checkpoint();
                } catch (IOException ex) {
                    throw new AssertionError("Cannot restore operator checkpoint", ex);
                }
            }
            if (wrotePlayerFixtureFile) player.getServerForPlayer()
                .getSaveHandler()
                .getSaveHandler()
                .writePlayerData(player);
            for (CelestialAsset.ID satellite : satellites) AssetStateSync.SERVER.destroyAsset(satellite);
            if (capsule.team != null) SatelliteNetworkService.rebuild(capsule.team, 0);
        }
    }

}
