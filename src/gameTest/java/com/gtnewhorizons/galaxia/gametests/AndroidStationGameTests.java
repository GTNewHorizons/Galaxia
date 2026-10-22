package com.gtnewhorizons.galaxia.gametests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.android.AndroidBodies;
import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.Chassis;
import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.android.BodyState;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;
import com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.TierEU;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class AndroidStationGameTests {

    private AndroidStationGameTests() {}

    /**
     * Integration contract: physical stations require a live hatch and respect chassis, charge and recipe inputs.
     */
    @GameTest(timeoutTicks = 600)
    public static void physicalStationLifecycle(GameTestHelper helper) {
        Fixture dock = new Fixture();
        Fixture capsule = new Fixture();
        UUID[] bodyId = new UUID[1];
        long[] observedAt = new long[1];
        long[] fundedCharge = new long[1];
        ClientTest.scenario(helper)
            .server("construct both rotated physical stations", () -> {
                dock.prepare(helper, false, 32, (WorldServer) helper.getWorld(), ForgeDirection.WEST);
                capsule.prepare(helper, true, 48, (WorldServer) helper.getWorld(), ForgeDirection.SOUTH);
                dock.tuneLocally(helper);
                BodyState original = BodyState.capture(dock.player);
                helper.afterTest(
                    () -> dock.player.playerNetServerHandler
                        .setPlayerLocation(original.x(), original.y(), original.z(), original.yaw(), original.pitch()));
            })
            .awaitServer("both stations form and receive power", () -> {
                if (!dock.station.isPowered() || !capsule.station.isPowered())
                    throw new AssertionError("Physical station fixtures have not formed");
            })
            .server("empty stations stay closed until the operator approaches", () -> {
                dock.assertDoor(false);
                capsule.assertDoor(false);
                dock.approachDoor();
            })
            .awaitServer("approaching dock opens a standing-height entry", () -> {
                dock.assertDoor(true);
                dock.assertStandingEntryClear();
            })
            .server("supply one native battery and an above-EV-voltage fixture recipe", () -> {
                ItemStack battery = ItemList.BatteryHull_EV_Full
                    .getWithCharge(1, Math.toIntExact(Chassis.EV.capacity() / 4));
                fundedCharge[0] = GTModHandler.getElectricItemCharge(battery)
                    .orElseThrow()[0];
                if (battery.stackSize != 1 || fundedCharge[0] <= 0 || fundedCharge[0] >= Chassis.EV.capacity())
                    throw new AssertionError("Manufacture fixture requires one partially charged native battery");
                dock.station.inputInventory()
                    .setStackInSlot(1, battery);
                dock.station.inputInventory()
                    .setStackInSlot(
                        0,
                        dock.recipe(
                            helper,
                            TileEntityBodyStation.MANUFACTURING_RECIPES,
                            Items.paper,
                            Chassis.EV,
                            (int) TierEU.RECIPE_IV,
                            battery));
                observedAt[0] = dock.world.getTotalWorldTime();
            })
            .awaitServer("observe the over-voltage input across ordinary hatch-powered ticks", () -> {
                if (dock.world.getTotalWorldTime() - observedAt[0] < 8)
                    throw new AssertionError("Waiting for the over-voltage observation");
            })
            .server("the EV hatch leaves an over-voltage recipe and its battery unconsumed", () -> {
                if (!dock.station.isPowered()) throw new AssertionError("Over-voltage fixture lost hatch power");
                for (var body : AndroidControl.bodies()
                    .bodies(dock.team)) {
                    if (body.dock()
                        .equals(dock.station.anchor())) {
                        dock.trackBody(body.id());
                        throw new AssertionError("EV hatch powered an above-voltage manufacturing recipe");
                    }
                }
                if (dock.station.getStackInSlot(0) == null || dock.station.getStackInSlot(1) == null
                    || dock.station.getStackInSlot(1).stackSize != 1)
                    throw new AssertionError("Over-voltage recipe consumed a physical input");
            })
            .server("replace only the paper with a paid local manufacturing recipe", () -> {
                ItemStack battery = dock.station.getStackInSlot(1);
                dock.station.inputInventory()
                    .setStackInSlot(
                        0,
                        dock.recipe(
                            helper,
                            TileEntityBodyStation.MANUFACTURING_RECIPES,
                            Items.paper,
                            Chassis.EV,
                            1,
                            battery));
            })
            .awaitServer("powered manufacture consumes the paid input and produces one body", () -> {
                List<AndroidBodies.AndroidBody> bodies = AndroidControl.bodies()
                    .bodies(dock.team)
                    .stream()
                    .filter(
                        body -> body.dock()
                            .equals(dock.station.anchor()))
                    .toList();
                if (bodies.isEmpty()) throw new AssertionError("Manufacture has not finished");
                if (bodies.size() != 1) throw new AssertionError("One paid recipe created multiple bodies");
                bodyId[0] = bodies.getFirst()
                    .id();
                dock.trackBody(bodyId[0]);
                if (dock.station.getStackInSlot(0) != null) throw new AssertionError("Manufacture kept its input");
                if (dock.station.getStackInSlot(1) != null)
                    throw new AssertionError("Manufacture kept its paid battery");
                if (bodies.getFirst()
                    .chassis() != Chassis.EV) throw new AssertionError("Manufacture ignored the recipe's chassis tier");
                if (bodies.getFirst()
                    .battery() < fundedCharge[0]
                    || bodies.getFirst()
                        .battery()
                        >= bodies.getFirst()
                            .capacity())
                    throw new AssertionError("Manufacture discarded native charge or minted a full battery");
                BodyState inside = dock.station.captureOccupant(dock.player);
                BodyState created = bodies.getFirst()
                    .state();
                if (Math.abs(created.x() - inside.x()) > .01 || Math.abs(created.y() - inside.y()) > .01
                    || Math.abs(created.z() - inside.z()) > .01)
                    throw new AssertionError("Assembler produced its android outside its chamber");
                try {
                    if (!bodyId[0]
                        .equals(AndroidControl.manufacture(dock.team, dock.station.anchor(), bodyId[0], Chassis.EV, 0)))
                        throw new AssertionError("Replaying a completed job changed its body identity");
                } catch (IOException ex) {
                    throw new AssertionError("Occupied chamber blocked completed job recovery", ex);
                }
                dock.assertDoor(true);
            })
            .server("move the same damaged body outside and begin the charging observation", () -> {
                var body = AndroidControl.bodies()
                    .body(bodyId[0])
                    .orElseThrow();
                dock.moveBody(
                    body.id(),
                    body.state()
                        .withHealth(7)
                        .restingAt(dock.x + .5, dock.y, dock.z - 2.5));
                AndroidControl.bodies()
                    .setBattery(body.id(), 0);
                observedAt[0] = dock.world.getTotalWorldTime();
                capsule.approachDoor();
            })
            .awaitServer("approaching capsule opens a standing-height entry", () -> {
                dock.assertDoor(false);
                capsule.assertDoor(true);
                capsule.assertStandingEntryClear();
                if (capsule.station.connectionStatus(capsule.player) != TileEntityBodyStation.ConnectionStatus.NONE)
                    throw new AssertionError("Capsule confirmed a connection before the operator entered");
            })
            .server("standing operator fits and can click each rear controller", () -> {
                BodyState inside = capsule.station.captureOccupant(capsule.player);
                capsule.player.playerNetServerHandler.setPlayerLocation(inside.x(), inside.y(), inside.z(), 0, 0);
                if (!capsule.station.contains(capsule.player))
                    throw new AssertionError("Rotated capsule rejected its standing interior occupant");
                if (capsule.station.connectionStatus(capsule.player)
                    != TileEntityBodyStation.ConnectionStatus.CAPSULE_READY)
                    throw new AssertionError("Standing capsule occupant did not receive the ready cue");
            })
            .awaitServer("outside body stays uncharged across ordinary station ticks", () -> {
                if (dock.world.getTotalWorldTime() - observedAt[0] < 24)
                    throw new AssertionError("Waiting for the outside charging observation");
                if (AndroidControl.bodies()
                    .body(bodyId[0])
                    .orElseThrow()
                    .battery() != 0) throw new AssertionError("Dock charged an android outside its chamber");
            })
            .capture("android-capsule-position-confirmed")
            .server("each rear controller is accessible from inside", () -> {
                dock.assertControllerAccessibleFromInside();
                capsule.assertControllerAccessibleFromInside();
            })
            .server("return the same damaged body to its own chamber", () -> {
                BodyState inside = dock.station.captureOccupant(dock.player);
                var body = AndroidControl.bodies()
                    .body(bodyId[0])
                    .orElseThrow();
                dock.moveBody(
                    body.id(),
                    body.state()
                        .restingAt(inside.x(), inside.y(), inside.z()));
            })
            .awaitServer("electrical charging raises battery without healing damage", () -> {
                var body = AndroidControl.bodies()
                    .body(bodyId[0])
                    .orElseThrow();
                if (body.battery() == 0) throw new AssertionError("The dock has not charged its occupant");
                if (body.state()
                    .health() != 7) throw new AssertionError("Charging repaired chassis damage without materials");
            })
            .server("supply repair material authored for another chassis", () -> {
                dock.station.inputInventory()
                    .setStackInSlot(
                        0,
                        dock.recipe(helper, TileEntityBodyStation.REPAIR_RECIPES, Items.string, Chassis.LUV, 1));
                observedAt[0] = dock.world.getTotalWorldTime();
            })
            .awaitServer("observe wrong-chassis repair across ordinary station ticks", () -> {
                if (dock.world.getTotalWorldTime() - observedAt[0] < 8)
                    throw new AssertionError("Waiting for the wrong-chassis repair observation");
            })
            .server("wrong-chassis material remains unpaid and proper repair material replaces it", () -> {
                if (!dock.station.isPowered()) throw new AssertionError("Wrong-chassis fixture lost hatch power");
                var body = AndroidControl.bodies()
                    .body(bodyId[0])
                    .orElseThrow();
                if (body.state()
                    .health() != 7 || dock.station.getStackInSlot(0) == null)
                    throw new AssertionError("EV body consumed or used another chassis's repair material");
                dock.station.inputInventory()
                    .setStackInSlot(
                        0,
                        dock.recipe(helper, TileEntityBodyStation.REPAIR_RECIPES, Items.string, Chassis.EV, 1));
            })
            .awaitServer("a material-consuming repair restores the configured amount", () -> {
                var body = AndroidControl.bodies()
                    .body(bodyId[0])
                    .orElseThrow();
                if (body.state()
                    .health() < 11) throw new AssertionError("Repair has not finished");
                if (body.state()
                    .health() != 11) throw new AssertionError("Repair exceeded its fixture operation");
                if (dock.station.getStackInSlot(0) != null) throw new AssertionError("Repair kept its input");
            })
            .server("remove the real energy hatch while the controller retains buffered energy", () -> {
                if (dock.station.storedEnergy() <= 0)
                    throw new AssertionError("Hatch-loss regression requires retained controller energy");
                if (!dock.world.setBlockToAir(dock.x, dock.y - 1, dock.z))
                    throw new AssertionError("Cannot remove the real station energy hatch");
                dock.station.markStructureDirty();
            })
            .awaitServer("a missing hatch disables the station despite its internal buffer", () -> {
                if (dock.station.isPowered()) throw new AssertionError("Station remained powered without its hatch");
                if (dock.station.storedEnergy() <= 0)
                    throw new AssertionError("Station lost power only because its internal buffer emptied");
            })
            .succeed();
    }

    static final class Fixture {

        final Set<BlockPos> placed = new HashSet<>();
        final Set<UUID> bodyIds = new HashSet<>();
        TileEntityBodyStation station;
        WorldServer world;
        EntityPlayerMP player;
        UUID team;
        int x;
        int y = 128;
        int z;
        IGregTechTileEntity energyHatch;

        void prepare(GameTestHelper helper, boolean capsule, int xOffset) {
            prepare(helper, capsule, xOffset, (WorldServer) helper.getWorld());
        }

        void prepare(GameTestHelper helper, boolean capsule, int xOffset, WorldServer destination) {
            prepare(helper, capsule, xOffset, destination, ForgeDirection.NORTH);
        }

        private void prepare(GameTestHelper helper, boolean capsule, int xOffset, WorldServer destination,
            ForgeDirection facing) {
            world = destination;
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            team = GTTeamsCompat.getTeam(player);
            x = (int) Math.floor(player.posX) + xOffset;
            z = (int) Math.floor(player.posZ);
            helper.afterTest(this::cleanup);
            if (destination != helper.getWorld()) {
                ForgeChunkManager.Ticket ticket = ForgeChunkManager
                    .requestTicket(Galaxia.instance, destination, ForgeChunkManager.Type.NORMAL);
                if (ticket == null) throw new AssertionError("Cannot retain the destination fixture dimension");
                ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(x >> 4, z >> 4));
                helper.afterTest(() -> ForgeChunkManager.releaseTicket(ticket));
                helper
                    .recordDiagnostic("Retained destination fixture in dimension " + destination.provider.dimensionId);
            }
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                place(x + dx, y - 1, z + dz, Blocks.stone);
            }
            place(x, y, z, (capsule ? GalaxiaBlocksEnum.CONTROL_CAPSULE : GalaxiaBlocksEnum.ANDROID_DOCK).get());
            station = (TileEntityBodyStation) world.getTileEntity(x, y, z);
            if (station == null) {
                throw new AssertionError("Android station fixture has no tile: " + placementDetails(x, y, z));
            }
            station.setPlacedFacing(facing);
            station.setFacing(facing);
            station.onPlaced(player);
            station.getStructureDefinition()
                .iterate("main", world, station.getCurrentFacing(), x, y, z, 1, 2, 2, (_, _, bx, by, bz, _, _, _) -> {
                    reserve(bx, by, bz);
                    return true;
                });
            station.construct(new ItemStack(Items.stick), false);
            ItemStack hatch = ItemList.Hatch_Energy_EV.get(1);
            reserve(x, y - 1, z);
            world.setBlockToAir(x, y - 1, z);
            if (!((ItemMachines) hatch.getItem()).placeBlockAt(
                hatch,
                player,
                world,
                x,
                y - 1,
                z,
                ForgeDirection.SOUTH.ordinal(),
                .5F,
                .5F,
                .5F,
                hatch.getItemDamage())) throw new AssertionError("Cannot place the station energy hatch");
            energyHatch = (IGregTechTileEntity) world.getTileEntity(x, y - 1, z);
            energyHatch.setFrontFacing(
                station.getCurrentFacing()
                    .getRelativeBackInWorld());
            energyHatch.increaseStoredEnergyUnits(energyHatch.getEUCapacity(), true);
            station.markStructureDirty();
        }

        void trackBody(UUID id) {
            bodyIds.add(id);
        }

        private void moveBody(UUID id, BodyState state) {
            for (Object value : world.loadedEntityList) {
                if (value instanceof EntityBody body && id.equals(body.bodyId()) && !body.isDead) {
                    body.setPositionAndUpdate(state.x(), state.y(), state.z());
                    AndroidControl.bodies()
                        .observeBody(id, state);
                    return;
                }
            }
            throw new AssertionError("Manufactured android has no physical projection to move");
        }

        private void assertControllerAccessibleFromInside() {
            BodyState inside = station.captureOccupant(player);
            ForgeDirection back = station.getCurrentFacing()
                .getRelativeBackInWorld();
            Vec3 start = Vec3.createVectorHelper(inside.x(), inside.y() + player.getEyeHeight(), inside.z());
            Vec3 end = Vec3
                .createVectorHelper(inside.x() + back.offsetX * .51, inside.y() + .5, inside.z() + back.offsetZ * .51);
            MovingObjectPosition hit = world.rayTraceBlocks(start, end);
            if (hit == null || hit.blockX != x || hit.blockY != y || hit.blockZ != z)
                throw new AssertionError("Station controller is not directly accessible on the interior rear wall");
        }

        void assertDoor(boolean open) {
            ForgeDirection back = station.getCurrentFacing()
                .getRelativeBackInWorld();
            int bx = x - back.offsetX * 2, bz = z - back.offsetZ * 2;
            for (int dy = 0; dy < 2; dy++) {
                if (!(world.getBlock(bx, y + dy, bz) instanceof BlockAirlockDoor door))
                    throw new AssertionError("Body station has no two-block airlock entrance");
                if (door.isOpen(world, bx, y + dy, bz) != open)
                    throw new AssertionError("Body station door is not " + (open ? "open" : "closed"));
            }
        }

        void approachDoor() {
            ForgeDirection back = station.getCurrentFacing()
                .getRelativeBackInWorld();
            player.playerNetServerHandler
                .setPlayerLocation(x + .5 - back.offsetX * 3, y, z + .5 - back.offsetZ * 3, 0, 0);
        }

        private void assertStandingEntryClear() {
            ForgeDirection back = station.getCurrentFacing()
                .getRelativeBackInWorld();
            AxisAlignedBB standing = player.boundingBox.copy();
            if (standing.maxY - standing.minY < 1.75) {
                throw new AssertionError("Entry regression requires a standing player's collision bounds");
            }
            AxisAlignedBB outside = standing.offset(
                x + .5 - back.offsetX * 3 - player.posX,
                y - standing.minY,
                z + .5 - back.offsetZ * 3 - player.posZ);
            AxisAlignedBB entryPath = outside.addCoord(back.offsetX * 2, 0, back.offsetZ * 2);
            if (!world.getCollidingBoundingBoxes(player, entryPath)
                .isEmpty()) {
                throw new AssertionError("Standing player cannot pass through station entrance: " + entryPath);
            }
            AxisAlignedBB interior = outside.offset(back.offsetX * 2, 0, back.offsetZ * 2);
            if (!world.getCollidingBoundingBoxes(player, interior)
                .isEmpty()) {
                throw new AssertionError("Station interior intersects a standing player: " + interior);
            }
            if (world.getCollidingBoundingBoxes(
                player,
                interior.copy()
                    .offset(0, -.05, 0))
                .isEmpty()) {
                throw new AssertionError("Station interior has no supporting floor at the anchor's standing height");
            }
        }

        private void reserve(int bx, int by, int bz) {
            // An unvisited dimension may provide EmptyChunk until it is explicitly loaded.
            world.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
            BlockPos pos = new BlockPos(bx, by, bz);
            if (!placed.contains(pos) && !world.isAirBlock(bx, by, bz)) {
                throw new AssertionError("Android station fixture space is occupied");
            }
            placed.add(pos);
        }

        private void place(int bx, int by, int bz, Block block) {
            reserve(bx, by, bz);
            boolean changed = world.setBlock(bx, by, bz, block, 0, 3);
            if (!changed || world.getBlock(bx, by, bz) != block) {
                throw new AssertionError(
                    "Android station fixture placement failed: changed=" + changed
                        + ", expected="
                        + Block.blockRegistry.getNameForObject(block)
                        + ", "
                        + placementDetails(bx, by, bz));
            }
        }

        private String placementDetails(int bx, int by, int bz) {
            return "dimension=" + world.provider.dimensionId
                + ", world="
                + world.getClass()
                    .getName()
                + ", position="
                + bx
                + ","
                + by
                + ","
                + bz
                + ", block="
                + Block.blockRegistry.getNameForObject(world.getBlock(bx, by, bz))
                + ", chunk="
                + world.getChunkFromBlockCoords(bx, bz)
                    .getClass()
                    .getName()
                + ", loadOnProvide="
                + world.theChunkProviderServer.loadChunkOnProvideRequest;
        }

        private void tuneLocally(GameTestHelper helper) {
            int oldDock = ConfigMachines.android.dockEuPerTick;
            int oldCharge = ConfigMachines.android.chargeEuPerTick;
            float oldRepair = ConfigMachines.android.repairHealth;
            ConfigMachines.android.dockEuPerTick = 8;
            ConfigMachines.android.chargeEuPerTick = 16;
            ConfigMachines.android.repairHealth = 4;
            helper.afterTest(() -> {
                ConfigMachines.android.dockEuPerTick = oldDock;
                ConfigMachines.android.chargeEuPerTick = oldCharge;
                ConfigMachines.android.repairHealth = oldRepair;
            });
        }

        private ItemStack recipe(GameTestHelper helper, RecipeMap<RecipeMapBackend> map,
            net.minecraft.item.Item material, Chassis chassis, int eut, ItemStack... components) {
            ItemStack input = new ItemStack(material);
            NBTTagCompound identity = new NBTTagCompound();
            identity.setString(
                "androidStationQaFixture",
                UUID.randomUUID()
                    .toString());
            input.setTagCompound(identity);
            List<ItemStack> recipeInputs = new ArrayList<>();
            recipeInputs.add(input.copy());
            for (ItemStack component : components) recipeInputs.add(component.copy());
            Collection<GTRecipe> recipes = GTValues.RA.stdBuilder()
                .itemInputs(recipeInputs.toArray(new ItemStack[0]))
                .metadata(TileEntityBodyStation.CHASSIS, chassis)
                .nbtSensitive()
                .duration(5)
                .eut(eut)
                .addTo(map);
            if (recipes.isEmpty()) throw new AssertionError("Fixture GT recipe was rejected");
            helper.afterTest(() -> recipes.forEach(map.getBackend()::removeRecipe));
            return input;
        }

        private void cleanup() {
            for (UUID id : bodyIds) {
                if (AndroidControl.bodies()
                    .body(id)
                    .isPresent()) {
                    try {
                        AndroidControl.bodies()
                            .destroy(id);
                    } catch (IOException ex) {
                        throw new AssertionError("Cannot remove fixture body", ex);
                    }
                }
            }
            for (Object value : new ArrayList<>(world.loadedEntityList)) {
                if (value instanceof EntityBody body && bodyIds.contains(body.bodyId())) {
                    body.setDead();
                    world.removeEntity(body);
                }
            }
            for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z());
        }
    }
}
