package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE.TransportEndpoint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class TransportPlatformGuiGameTests {

    private TransportPlatformGuiGameTests() {}

    /** Integration contract: choosing a future Silo leaves an accepted service unchanged until explicit rebind. */
    @GameTest(timeoutTicks = 2400)
    public static void explicitlyRebindDestroyedServiceSiloThroughNativePanel(GameTestHelper helper) {
        var fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("construct separate native Pad and Silo", () -> fixture.prepare(helper))
            .awaitServer("both actual structures formed", fixture::assertFormed)
            .server(
                "connect the real Gantry and prepare an admitted service with its Silo destroyed",
                fixture::prepareService)
            .awaitClient("native Pad chunk and endpoint state arrived", ignored -> fixture.assertClientPad())
            .server("open the actual native Pad panel", fixture::open)
            .awaitClient(
                "native platform panel opened",
                ignored -> GuiTestSupport.requireScreen("galaxia:transport_platform"))
            .click(control("platform.service.select_silo"))
            .awaitServer("future Silo selected without redirecting active service", fixture::assertSelectionOnly)
            .click(control("platform.service.rebind"))
            .awaitServer("explicit native command rebound this exact service", fixture::assertRebound)
            .escape()
            .server("reopen the native panel", fixture::open)
            .awaitClient(
                "reopened platform remains usable",
                ignored -> GuiTestSupport.requireScreen("galaxia:transport_platform"))
            .server("reopening retained frozen order and new Silo", fixture::assertRebound)
            .capture("native-platform-explicit-service-rebind")
            .succeed();
    }

    private static final class Fixture {

        private World world;
        private EntityPlayerMP player;
        private TileEntityTransportPlatform pad;
        private TileEntitySilo silo;
        private Rocket rocket;
        private Rocket.Service accepted;
        private NBTTagCompound originalRockets;
        private final List<CelestialAsset.ID> assets = new ArrayList<>();
        private final List<BlockPos> blocks = new ArrayList<>();
        private UUID oldSilo;
        private int x;
        private int z;
        private double oldX, oldY, oldZ;
        private boolean areaClear;

        private void prepare(GameTestHelper helper) {
            world = helper.getWorld();
            player = (EntityPlayerMP) world.playerEntities.stream()
                .filter(EntityPlayerMP.class::isInstance)
                .findFirst()
                .orElseThrow();
            x = (int) player.posX + 48;
            z = (int) player.posZ;
            oldX = player.posX;
            oldY = player.posY;
            oldZ = player.posZ;
            originalRockets = Galaxia.proxy.serverRuntime()
                .rockets()
                .serializeNBT();
            helper.afterTest(this::restore);
            for (int dx = -6; dx <= 18; dx++) for (int y = 148; y <= 174; y++) for (int dz = -2; dz <= 20; dz++) {
                if (!world.isAirBlock(x + dx, y, z + dz)) throw new AssertionError("Native fixture area is occupied");
            }
            areaClear = true;
            world.setBlock(x, 160, z, GalaxiaBlocksEnum.LANDING_PAD_CONTROLLER.get(), 0, 3);
            pad = (TileEntityTransportPlatform) world.getTileEntity(x, 160, z);
            prepareController(pad);
            pad.construct(new ItemStack(Items.stick), false);
            attachTerminal(pad);
            world.setBlock(x + 12, 160, z + 8, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0, 3);
            silo = (TileEntitySilo) world.getTileEntity(x + 12, 160, z + 8);
            prepareController(silo);
            silo.construct(new ItemStack(Items.stick), false);
            attachTerminal(silo);
            rememberBlocks();
        }

        private void prepareController(TransportEndpoint endpoint) {
            if (!endpoint.machineIdentity()
                .claim(GTTeamsCompat.getTeam(player))) throw new AssertionError("Controller team rejected");
            endpoint.endpointBinding()
                .selectStandalone();
            endpoint.setPlacedFacing(ForgeDirection.NORTH);
        }

        private void attachTerminal(TransportEndpoint endpoint) {
            var tile = (GalaxiaMultiblockBase<?>) endpoint;
            for (int dx = -6; dx <= 18; dx++) for (int y = 148; y <= 174; y++) for (int dz = -2; dz <= 20; dz++) {
                int bx = x + dx, bz = z + dz;
                if (world.getBlock(bx, y, bz) != GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get()) continue;
                world.setBlock(bx, y, bz, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0, 3);
                tile.markStructureDirty();
                tile.updateEntity();
                if (endpoint.isStructureValid() && endpoint.getGantryTerminal() != null) return;
                world.setBlock(bx, y, bz, GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0, 3);
            }
            throw new AssertionError("The actual structure has no usable Gantry terminal position");
        }

        private void rememberBlocks() {
            for (int dx = -6; dx <= 18; dx++) for (int y = 148; y <= 174; y++) for (int dz = -2; dz <= 20; dz++) {
                var pos = new BlockPos(x + dx, y, z + dz);
                if (!world.isAirBlock(pos.x(), pos.y(), pos.z()) && !blocks.contains(pos)) blocks.add(pos);
            }
        }

        private void assertFormed() {
            if (!pad.isStructureValid() || !silo.isStructureValid()
                || pad.endpointBinding()
                    .assetId() == null
                || silo.endpointBinding()
                    .assetId() == null)
                throw new AssertionError("Native structures have not registered their formed endpoints");
        }

        private void prepareService() {
            assets.add(
                pad.endpointBinding()
                    .assetId());
            assets.add(
                silo.endpointBinding()
                    .assetId());
            connectRoute(pad.getGantryTerminal(), silo.getGantryTerminal());
            var team = GTTeamsCompat.getTeam(player);
            var body = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
            oldSilo = UUID.randomUUID();
            var oldAsset = CelestialAsset.create(
                new CelestialAsset.ID(oldSilo),
                body,
                CelestialAsset.Kind.TRANSPORT_SITE,
                CelestialAsset.Status.OPERATIONAL);
            oldAsset.registerEndpoint(
                new CelestialAsset.Endpoint(
                    oldSilo,
                    CelestialAsset.Endpoint.Kind.SILO,
                    "Former service Silo",
                    new CelestialAsset.PhysicalLocation(world.provider.dimensionId, new BlockPos(x + 40, 160, z)),
                    null,
                    1));
            CelestialAssetStore.SERVER.registerAssetInternal(team, oldAsset);
            assets.add(oldAsset.assetId);
            var design = new RocketBlueprint();
            design.addPart(new RocketPartInstance(RocketPartRegistry.CAPSULE_T1, 0, 0, 0, false));
            rocket = Rocket.construct(team, body, oldSilo, design);
            rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    rocket.buildSlots()
                        .getFirst(),
                    40,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, oldSilo)));
            if (!rocket.assemble(oldSilo)) throw new AssertionError("Service prerequisite did not assemble");
            Galaxia.proxy.serverRuntime()
                .rockets()
                .register(rocket);
            if (rocket.beginService(
                UUID.randomUUID(),
                new VehicleSchedule.Repair(UUID.randomUUID(), 75),
                pad.machineIdentity()
                    .id(),
                oldSilo) != Rocket.ServiceResult.ACCEPTED)
                throw new AssertionError("Service prerequisite was not admitted");
            accepted = rocket.service();
            CelestialAssetStore.findEndpoint(
                pad.machineIdentity()
                    .id())
                .asset()
                .setEndpointServiceSilo(
                    pad.machineIdentity()
                        .id(),
                    oldSilo);
            CelestialAssetStore.SERVER.destroyAssetInternal(oldAsset.assetId);
            if (GantryAPI.findControllerTerminal(
                pad.getGantryTerminal(),
                silo.machineIdentity()
                    .id())
                == null) throw new AssertionError("Real connected replacement Silo is not reachable");
            player.setPositionAndUpdate(x + .5, 161, z - 1);
        }

        private void connectRoute(TileEntityGantryTerminal start, TileEntityGantryTerminal end) {
            var origin = new BlockPos(start.xCoord, start.yCoord, start.zCoord);
            var destination = new BlockPos(end.xCoord, end.yCoord, end.zCoord);
            var parents = new HashMap<BlockPos, BlockPos>();
            var queue = new ArrayDeque<BlockPos>();
            parents.put(origin, null);
            queue.add(origin);
            while (!queue.isEmpty() && !parents.containsKey(destination)) {
                var here = queue.removeFirst();
                for (var offset : GantryAPI.CHECK_OFFSETS) {
                    var next = new BlockPos(
                        here.x() + (int) offset.xCoord,
                        here.y() + (int) offset.yCoord,
                        here.z() + (int) offset.zCoord);
                    if (next.x() < x - 6 || next.x() > x + 18
                        || next.y() < 156
                        || next.y() > 164
                        || next.z() < z - 2
                        || next.z() > z + 20
                        || parents.containsKey(next)) continue;
                    if (!next.equals(destination) && !world.isAirBlock(next.x(), next.y(), next.z())) continue;
                    parents.put(next, here);
                    queue.addLast(next);
                }
            }
            if (!parents.containsKey(destination))
                throw new AssertionError("No free native Gantry path between structures");
            var path = new ArrayList<BlockPos>();
            for (var cursor = destination; cursor != null; cursor = parents.get(cursor)) path.addFirst(cursor);
            for (int i = 1; i < path.size() - 1; i++) {
                var pos = path.get(i);
                world.setBlock(pos.x(), pos.y(), pos.z(), GalaxiaBlocksEnum.GANTRY.get(), 0, 3);
                blocks.add(pos);
            }
            for (int i = 1; i < path.size(); i++) {
                var previous = (TileEntityGantry) world.getTileEntity(
                    path.get(i - 1)
                        .x(),
                    path.get(i - 1)
                        .y(),
                    path.get(i - 1)
                        .z());
                var next = (TileEntityGantry) world.getTileEntity(
                    path.get(i)
                        .x(),
                    path.get(i)
                        .y(),
                    path.get(i)
                        .z());
                previous.connect(next);
            }
        }

        private void open() {
            GuiFactories.tileEntity()
                .open(player, pad.xCoord, pad.yCoord, pad.zCoord);
        }

        private void assertClientPad() {
            var clientWorld = Minecraft.getMinecraft().theWorld;
            if (clientWorld == null
                || !(clientWorld
                    .getTileEntity(pad.xCoord, pad.yCoord, pad.zCoord) instanceof TileEntityTransportPlatform clientPad)
                || !pad.machineIdentity()
                    .id()
                    .equals(
                        clientPad.machineIdentity()
                            .id())
                || clientPad.endpointBinding()
                    .assetId() == null)
                throw new AssertionError("The native Pad has not reached the actual client");
        }

        private void assertSelectionOnly() {
            if (!silo.machineIdentity()
                .id()
                .equals(pad.serviceSiloId())) throw new AssertionError("Future Silo selection has not reached server");
            if (!accepted.equals(rocket.service()))
                throw new AssertionError("Selecting a future Silo implicitly redirected active service");
        }

        private void assertRebound() {
            var current = rocket.service();
            if (!current.id()
                .equals(accepted.id())
                || !current.returnSiloId()
                    .equals(
                        silo.machineIdentity()
                            .id())
                || !current.repairs()
                    .equals(accepted.repairs())
                || !current.returns()
                    .equals(accepted.returns()))
                throw new AssertionError("Explicit rebind did not preserve the exact service order");
            if (!rocket.id()
                .equals(silo.vehicleId()))
                throw new AssertionError("Replacement Silo did not reserve the same vehicle");
            if (rocket.modules()
                .getFirst()
                .holder()
                .kind() != Rocket.HolderKind.PAD_BUFFER)
                throw new AssertionError("Changing the return Silo moved a concrete module");
        }

        private void restore() {
            player.closeScreen();
            if (areaClear) rememberBlocks();
            if (pad != null && pad.endpointBinding()
                .assetId() != null) assets.add(
                    pad.endpointBinding()
                        .assetId());
            if (silo != null && silo.endpointBinding()
                .assetId() != null) assets.add(
                    silo.endpointBinding()
                        .assetId());
            Galaxia.proxy.serverRuntime()
                .rockets()
                .restoreNBT(originalRockets, RocketPartRegistry.instance());
            for (var id : assets) CelestialAssetStore.SERVER.destroyAssetInternal(id);
            for (var pos : blocks) world.setBlockToAir(pos.x(), pos.y(), pos.z());
            player.setPositionAndUpdate(oldX, oldY, oldZ);
        }
    }
}
