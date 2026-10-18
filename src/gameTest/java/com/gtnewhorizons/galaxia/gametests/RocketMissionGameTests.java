package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.teams.TeamNetwork;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.core.network.RocketScheduleOpenPacket;
import com.gtnewhorizons.galaxia.core.network.RocketTransitPacket;
import com.gtnewhorizons.galaxia.gt5.RocketContent;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class RocketMissionGameTests {

    private RocketMissionGameTests() {}

    /** Bug regression: normal world targeting and right click can board a physical Rocket. */
    @GameTest(timeoutTicks = 400)
    public static void physicalRocketBoardsThroughNormalRightClick(GameTestHelper helper) {
        Rocket[] vehicle = new Rocket[1];
        EntityPlayerMP[] crew = new EntityPlayerMP[1];
        ClientTest.scenario(helper)
            .server("prepare an unboarded physical Rocket", () -> {
                for (Object candidate : helper.getWorld().playerEntities)
                    if (candidate instanceof EntityPlayerMP player) {
                        crew[0] = player;
                        break;
                    }
                var player = crew[0];
                if (player == null) throw new AssertionError("Integrated player has not joined");
                var registry = Galaxia.proxy.serverRuntime()
                    .rockets();
                var saved = registry.serializeNBT();
                double x = player.posX, y = player.posY, z = player.posZ;
                boolean flying = player.capabilities.isFlying;
                int slot = player.inventory.currentItem;
                ItemStack held = player.inventory.getCurrentItem();
                helper.afterTest(() -> {
                    player.mountEntity(null);
                    for (Object candidate : List.copyOf(helper.getWorld().loadedEntityList))
                        if (candidate instanceof EntityRocket entity && vehicle[0] != null
                            && vehicle[0].id()
                                .equals(entity.vehicleId()))
                            entity.setDead();
                    registry.restoreNBT(saved, RocketPartRegistry.instance());
                    player.inventory.setInventorySlotContents(slot, held);
                    player.capabilities.isFlying = flying;
                    player.sendPlayerAbilities();
                    player.setPositionAndUpdate(x, y, z);
                });
                var rocket = basicVehicle(GTTeamsCompat.getTeam(player));
                vehicle[0] = rocket;
                registry.register(rocket);
                var entity = new EntityRocket(helper.getWorld());
                entity.setPositionAndRotation(x, 180, z + 3.5, 0, 0);
                if (!entity.bind(rocket) || !helper.getWorld()
                    .spawnEntityInWorld(entity)) throw new AssertionError("Physical Rocket was not spawned");
                entity.setProjectionVisible(true);
                player.inventory.setInventorySlotContents(slot, new ItemStack(Items.stick));
                player.capabilities.isFlying = true;
                player.sendPlayerAbilities();
                player.rotationYaw = 0;
                player.rotationPitch = 0;
                player.setPositionAndUpdate(x, 180, z);
            })
            .client("aim at the physical Rocket", ignored -> {
                var mc = Minecraft.getMinecraft();
                mc.displayGuiScreen(null);
                mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = 0;
                mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0;
            })
            .withinTicks(100)
            .awaitClient("normal mouse targeting selects the Rocket", ignored -> {
                var hit = Minecraft.getMinecraft().objectMouseOver;
                if (hit == null || !(hit.entityHit instanceof EntityRocket entity)
                    || !vehicle[0].id()
                        .equals(entity.vehicleId()))
                    throw new AssertionError("Normal world targeting cannot select the Rocket");
            })
            .useHeldItem()
            .awaitServer("normal right click boards the accepted passenger", () -> {
                if (!vehicle[0].passengers()
                    .contains(crew[0].getUniqueID()) || crew[0].ridingEntity == null)
                    throw new AssertionError("Normal right click did not board the Rocket");
            })
            .succeed();
    }

    /** Integration contract: actual deployed cargo awards native team data and synchronizes access immediately. */
    @GameTest(timeoutTicks = 1200)
    public static void openingDeploymentConsumesCargoAndUnlocksTheTeamMap(GameTestHelper helper) {
        Rocket[] vehicle = new Rocket[1];
        var payload = ItemStackWrapper.of(new ItemStack(RocketContent.PRIMITIVE_TELESCOPE));
        ClientTest.scenario(helper)
            .server("prepare the actual opening vehicle and payload", () -> {
                EntityPlayerMP player = null;
                for (Object candidate : helper.getWorld().playerEntities)
                    if (candidate instanceof EntityPlayerMP found) {
                        player = found;
                        break;
                    }
                if (player == null) throw new AssertionError("Integrated player has not joined");
                var team = GTTeamsCompat.getTeamData(player)
                    .orElseThrow();
                var data = (GalaxiaTeamData) team.getData(GalaxiaTeamData.ID);
                if (data.hasAchievement(Achievement.STAR_MAP_ACTIVATION))
                    throw new AssertionError("Opening fixture requires a locked map");
                var savedTeam = new NBTTagCompound();
                data.writeToNBT(savedTeam);
                var registry = Galaxia.proxy.serverRuntime()
                    .rockets();
                var savedVehicles = registry.serializeNBT();
                helper.afterTest(() -> {
                    if (vehicle[0] != null) {
                        var entities = List.copyOf(helper.getWorld().loadedEntityList);
                        for (Object entity : entities)
                            if (entity instanceof EntityRocket rocket && rocket.isCurrentProjection(vehicle[0]))
                                rocket.setDead();
                    }
                    registry.restoreNBT(savedVehicles, RocketPartRegistry.instance());
                    data.readFromNBT(savedTeam);
                    team.markDirty();
                    TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
                });
                var rocket = basicVehicle(team.getTeamId());
                vehicle[0] = rocket;
                if (rocket.insertCargo(payload, 1, false) != 1)
                    throw new AssertionError("Actual payload was not loaded");
                var ground = new VehicleSchedule.Visit(
                    UUID.randomUUID(),
                    new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.SURFACE, null),
                    List.of());
                var orbit = new VehicleSchedule.Visit(
                    UUID.randomUUID(),
                    new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.ORBIT, null),
                    List.of(new VehicleSchedule.Deploy(UUID.randomUUID(), payload, 1)));
                var schedule = new VehicleSchedule(List.of(ground, orbit), VehicleSchedule.Mode.RUN_ONCE);
                schedule = schedule.withRouteSettings(
                    new VehicleSchedule.Leg(ground.id(), orbit.id()),
                    new VehicleSchedule.RouteSettings(
                        rocket.propulsion()
                            .usableDeltaVMps(),
                        RoutePriority.PRIORITIZE_DV));
                if (rocket.saveSchedule(team.getTeamId(), rocket.revision(), schedule) != Rocket.CommandResult.APPLIED)
                    throw new AssertionError("Opening schedule was not accepted");
                registry.register(rocket);
                if (Galaxia.proxy.serverRuntime()
                    .startRocket(player, rocket.id(), rocket.revision()) != Rocket.CommandResult.APPLIED)
                    throw new AssertionError("Runtime rejected the opening mission");
            })
            .withinTicks(600)
            .awaitServer("real opening deployment completed", () -> {
                var rocket = vehicle[0];
                if (rocket == null || rocket.isRunning()) throw new AssertionError("Opening mission has not completed");
                if (rocket.status() != Rocket.Status.LOST || rocket.cargoAmount(payload) != 0)
                    throw new AssertionError("Uncrewed deployment did not consume its payload");
                var loaded = Rocket.deserializeNBT(rocket.serializeNBT(), RocketPartRegistry.instance());
                if (loaded.cargoAmount(payload) != 0 || loaded.isRunning())
                    throw new AssertionError("Completed deployment changed on reload");
            })
            .awaitClient("team map access synchronized", ignored -> {
                if (!GTTeamsCompat.hasAchievement(Minecraft.getMinecraft().thePlayer, Achievement.STAR_MAP_ACTIVATION))
                    throw new AssertionError("Opening achievement has not synchronized to the client");
            })
            .succeed();
    }

    /** Integration contract: the native basic vehicle rematerializes with its assigned crew in the lunar world. */
    @GameTest(timeoutTicks = 1600)
    public static void basicCrewedVehicleLandsInTheLunarWorld(GameTestHelper helper) {
        Rocket[] vehicle = new Rocket[1];
        EntityPlayerMP[] crew = new EntityPlayerMP[1];
        var reservedFuel = new AtomicReference<Map<FluidKey, Long>>();
        var acceptedSite = new AtomicReference<VehicleSchedule.Place>();
        var descent = new LandingProjectionTrace();
        var scenario = ClientTest.scenario(helper)
            .server("prepare a boarded basic lunar vehicle", () -> {
                for (Object candidate : helper.getWorld().playerEntities)
                    if (candidate instanceof EntityPlayerMP player) {
                        crew[0] = player;
                        break;
                    }
                var player = crew[0];
                if (player == null) throw new AssertionError("Integrated player has not joined");
                var team = GTTeamsCompat.getTeamData(player)
                    .orElseThrow();
                var data = (GalaxiaTeamData) team.getData(GalaxiaTeamData.ID);
                var savedTeam = new NBTTagCompound();
                data.writeToNBT(savedTeam);
                data.award(Achievement.STAR_MAP_ACTIVATION);
                team.markDirty();
                TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
                var registry = Galaxia.proxy.serverRuntime()
                    .rockets();
                var savedVehicles = registry.serializeNBT();
                var source = new Rocket.PhysicalPosition(
                    player.dimension,
                    player.posX,
                    player.posY,
                    player.posZ,
                    player.rotationYaw);
                boolean creative = player.capabilities.isCreativeMode;
                boolean protectedPlayer = player.capabilities.disableDamage;
                player.capabilities.isCreativeMode = true;
                player.capabilities.disableDamage = true;
                player.sendPlayerAbilities();
                helper.afterTest(() -> {
                    player.mountEntity(null);
                    data.readFromNBT(savedTeam);
                    team.markDirty();
                    TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
                    if (vehicle[0] != null) for (var world : DimensionManager.getWorlds()) {
                        for (Object candidate : List.copyOf(world.loadedEntityList))
                            if (candidate instanceof EntityRocket entity && vehicle[0].id()
                                .equals(entity.vehicleId())) entity.setDead();
                    }
                    registry.restoreNBT(savedVehicles, RocketPartRegistry.instance());
                    if (player.dimension != source.dimension()) {
                        var originalWorld = DimensionManager.getWorld(source.dimension());
                        MinecraftServer.getServer()
                            .getConfigurationManager()
                            .transferPlayerToDimension(player, source.dimension(), new Teleporter(originalWorld) {

                                @Override
                                public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
                                    entity.setLocationAndAngles(source.x(), source.y(), source.z(), source.yaw(), 0);
                                }
                            });
                    }
                    player.noClip = false;
                    player.setPositionAndUpdate(source.x(), source.y(), source.z());
                    player.capabilities.isCreativeMode = creative;
                    player.capabilities.disableDamage = protectedPlayer;
                    player.sendPlayerAbilities();
                });
                var rocket = basicVehicle(GTTeamsCompat.getTeam(player));
                vehicle[0] = rocket;
                registry.register(rocket);
                descent.vehicleId = rocket.id();
                MinecraftForge.EVENT_BUS.register(descent);
                helper.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(descent));
                var entity = new EntityRocket(helper.getWorld());
                entity.setPositionAndRotation(source.x(), source.y(), source.z(), source.yaw(), 0);
                entity.setProjectionVisible(true);
                if (!entity.bind(rocket) || !helper.getWorld()
                    .spawnEntityInWorld(entity) || !entity.interactFirst(player))
                    throw new AssertionError("Native capsule could not board its crew");
            })
            .awaitClient("the boarded physical vehicle is available on the client", ignored -> {
                var player = Minecraft.getMinecraft().thePlayer;
                EntityRocket entity = player.ridingEntity instanceof EntityRocket rocket ? rocket
                    : player.ridingEntity instanceof EntityRocketSeat seat ? seat.getRocket() : null;
                if (entity == null || !vehicle[0].id()
                    .equals(entity.vehicleId())
                    || entity.projectionGeneration() != vehicle[0].projection()
                        .generation())
                    throw new AssertionError("Native vehicle spawn and passenger attachment have not synchronized");
            })
            .client(
                "open the boarded vehicle through its native entry",
                ignored -> Galaxia.GALAXIA_NETWORK.sendToServer(new RocketScheduleOpenPacket(vehicle[0].id())))
            .awaitClient("the actual vehicle schedule has synchronized", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.visits") == null
                    && GuiTestSupport.findNamedWidget("rocket.schedule.overview.rows") == null)
                    throw new AssertionError("The vehicle editor is still waiting for its server view");
            })
            .click(control("rocket.schedule.manual"))
            .click(control("rocket.schedule.manual.body"));
        GuiTestSupport.frameInnerSolarSystem(scenario)
            .client("prepare deterministic map navigation mode", ignored -> {
                var map = GuiTestSupport.map();
                if (!"FOLLOW".equals(String.valueOf(map.getClickMode()))) map.toggleClickMode();
            })
            .click(control("starmap.toolbar.clickMode"))
            .click(
                ClientTarget.of(
                    "frame Earth to reveal its Moon",
                    ignored -> GuiTestSupport.bodyTarget(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD))))
            .awaitClient("Earth focus separates the lunar destination marker", ignored -> {
                var map = GuiTestSupport.map();
                var earth = map.visibleBodyBounds(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD));
                if (!"HIERARCHY".equals(String.valueOf(map.getClickMode())) || earth == null
                    || !earth.contains(map.getArea().width / 2, map.getArea().height / 2))
                    throw new AssertionError("Earth focus is still moving");
            })
            .rightClick(
                ClientTarget.of(
                    "open Moon destination context",
                    ignored -> GuiTestSupport.bodyTarget(CelestialObjectKey.registered(CelestialObjectId.MOON), 1)))
            .click(control("rocket.schedule.body.confirm"))
            .awaitClient("manual destination returns from the actual map", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.manual.search") == null)
                    throw new AssertionError("The manual flight form has not returned");
            })
            .click(control("rocket.schedule.manual.search"))
            .withinTicks(600)
            .awaitClient("the actual searched lunar site is displayed", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.manual.site") == null)
                    throw new AssertionError("No actual lunar landing site has been returned");
            })
            .capture("rocket-lunar-searched-site")
            .server("search leaves the saved itinerary and fuel untouched", () -> {
                if (vehicle[0].isRunning() || !vehicle[0].schedule()
                    .visits()
                    .isEmpty())
                    throw new AssertionError("Searching accepted a mission or changed the automated schedule");
            })
            .click(control("rocket.schedule.manual.accept"))
            .awaitServer("the lunar departure waits for return-risk review", () -> {
                var rocket = vehicle[0];
                if (rocket.scheduleView()
                    .launchWarning() == null || rocket.status() != Rocket.Status.PHYSICAL)
                    throw new AssertionError("Lunar departure has not paused for review");
                acceptedSite.set(
                    rocket.scheduleView()
                        .manualMission()
                        .visits()
                        .getLast()
                        .place());
            })
            .server("remember the untouched departure fuel", () -> reservedFuel.set(vehicle[0].propellantAmounts()))
            .awaitClient("central return-risk warning is visible", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.risk") == null)
                    throw new AssertionError("Native schedule warning has not opened");
            })
            .capture("rocket-lunar-return-risk")
            .click(control("rocket.schedule.risk.edit"))
            .awaitServer("back to schedule pauses the real departure", () -> {
                var rocket = vehicle[0];
                if (rocket.scheduleView()
                    .launchWarning() != null || rocket.status() != Rocket.Status.PHYSICAL
                    || !reservedFuel.get()
                        .equals(rocket.propellantAmounts()))
                    throw new AssertionError("Review did not preserve the physical vehicle and departure fuel");
            })
            .awaitClient("schedule editing is visible again", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.risk") != null
                    || GuiTestSupport.findNamedWidget("rocket.schedule.manual.resume") == null)
                    throw new AssertionError("Back to schedule has not restored the route editor");
            })
            .server("packaged forecast supports the actual basic lunar route", () -> {
                var rocket = vehicle[0];
                var destination = rocket.scheduleView()
                    .manualMission()
                    .visits()
                    .getLast()
                    .place();
                var estimate = OrbitalTransferPlanner.forecastAvailability(
                    GalaxiaCelestialAPI.root(),
                    GalaxiaCelestialAPI.get(rocket.bodyKey())
                        .orElseThrow(),
                    GalaxiaCelestialAPI.get(destination.body())
                        .orElseThrow(),
                    GalaxiaCelestialAPI.currentOrbitalTime(),
                    rocket.scheduleView()
                        .place()
                        .endpointKind(),
                    destination.endpointKind(),
                    rocket.propulsion()
                        .localVehicle(),
                    ConfigRocket.flight.costModel(),
                    rocket.propulsion()
                        .usableDeltaVMps());
                if (estimate.status() != OrbitalTransferPlanner.ForecastStatus.AVAILABLE
                    || estimate.availability() == null)
                    throw new AssertionError("Packaged native forecast is unavailable: " + estimate.status());
            })
            .click(control("rocket.schedule.manual.resume"))
            .awaitClient("resuming re-evaluates the return risk", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.risk") == null)
                    throw new AssertionError("Return-risk review has not reopened");
            })
            .click(control("rocket.schedule.risk.confirm"))
            .withinTicks(1000)
            .awaitServer("crew and original vehicle reached the lunar world", () -> {
                var rocket = vehicle[0];
                descent.observe(rocket);
                var player = crew[0];
                int lunarDimension = CelestialRegistry.get(CelestialObjectId.MOON)
                    .orElseThrow()
                    .dimensionEnum()
                    .getId();
                if (rocket == null || rocket.isRunning()
                    || rocket.status() != Rocket.Status.PHYSICAL
                    || !rocket.bodyKey()
                        .equals(CelestialObjectKey.registered(CelestialObjectId.MOON))
                    || player.dimension != lunarDimension)
                    throw new AssertionError(
                        "Crewed lunar arrival has not completed: " + landingDiagnostics(rocket, acceptedSite.get()));
                if (Galaxia.proxy.serverRuntime()
                    .rockets()
                    .passengerVehicle(player.getUniqueID()) != rocket
                    || Galaxia.proxy.serverRuntime()
                        .restrictedPassenger(player.getUniqueID()) != null)
                    throw new AssertionError("Arrival lost its assigned crew or physical access");
                boolean physical = false;
                for (Object candidate : player.worldObj.loadedEntityList)
                    if (candidate instanceof EntityRocket entity && entity.isCurrentProjection(rocket)
                        && entity.isPlayerAboard(player)) physical = true;
                if (!physical) throw new AssertionError("Arriving crew has not mounted the lunar vehicle projection");
                descent.assertCompleted();
                if (rocket.propellantAmounts()
                    .values()
                    .stream()
                    .anyMatch(amount -> amount <= 0))
                    throw new AssertionError("Lunar arrival exhausted its companion reserve");
                if (!acceptedSite.get()
                    .equals(rocket.location())
                    || !acceptedSite.get()
                        .surfaceSite()
                        .position()
                        .equals(rocket.physicalPosition())
                    || !rocket.schedule()
                        .visits()
                        .isEmpty())
                    throw new AssertionError("Arrival changed its accepted site or the automated schedule");
            })
            .awaitClient("lunar world synchronized", ignored -> {
                int lunarDimension = CelestialRegistry.get(CelestialObjectId.MOON)
                    .orElseThrow()
                    .dimensionEnum()
                    .getId();
                if (Minecraft.getMinecraft().thePlayer.dimension != lunarDimension)
                    throw new AssertionError("Client has not reached the lunar world");
                var view = RocketTransitPacket.Handler.current();
                if (view == null || view.restricted()
                    || !acceptedSite.get()
                        .equals(view.source()))
                    throw new AssertionError("Client has not synchronized physical access at the accepted lunar site");
            })
            .key(Keyboard.KEY_ESCAPE, '\0')
            .awaitClient("physical lunar world is visible", ignored -> {
                if (Minecraft.getMinecraft().currentScreen != null)
                    throw new AssertionError("A transit or review overlay still covers the physical arrival");
            })
            .capture("rocket-crewed-lunar-arrival")
            .succeed();
    }

    /** Integration contract: observe the native projection before spawn and during the real accepted landing. */
    public static final class LandingProjectionTrace {

        private UUID vehicleId;
        private boolean airborneSpawn;
        private int matchingJoins;
        private int landingJoins;
        private int landingTicks;
        private int projectionSamples;
        private int firstEntityTick = -1;
        private int lastEntityTick = -1;
        private double firstHeight = Double.NaN;
        private double previousHeight = Double.NaN;
        private double firstProgress = Double.NaN;
        private double lastProgress = Double.NaN;
        private int descendingSamples;
        private String failure;

        @SubscribeEvent
        public void joining(EntityJoinWorldEvent event) {
            if (event.world.isRemote || !(event.entity instanceof EntityRocket entity)
                || !java.util.Objects.equals(vehicleId, entity.vehicleId())) return;
            matchingJoins++;
            var rocket = Galaxia.proxy.serverRuntime()
                .rockets()
                .find(vehicleId);
            if (!landing(rocket)) return;
            landingJoins++;
            var anchor = rocket.physicalPosition();
            firstHeight = entity.posY;
            airborneSpawn = entity.posY > anchor.y() + rocket.footprint()
                .height();
            previousHeight = entity.posY;
            if (!airborneSpawn || Math.abs(entity.prevPosY - entity.posY) > 1e-6)
                failure = "Landing projection was not already airborne before native spawn";
        }

        private void observe(Rocket rocket) {
            if (failure != null) throw new AssertionError(failure);
            if (!landing(rocket)) return;
            landingTicks++;
            if (!Double.isFinite(firstProgress)) firstProgress = rocket.physicalManeuverProgress();
            lastProgress = rocket.physicalManeuverProgress();
            var anchor = rocket.physicalPosition();
            var world = DimensionManager.getWorld(anchor.dimension());
            if (world == null) return;
            for (Object candidate : world.loadedEntityList) {
                if (!(candidate instanceof EntityRocket entity) || !entity.isCurrentProjection(rocket)) continue;
                projectionSamples++;
                if (firstEntityTick < 0) firstEntityTick = entity.ticksExisted;
                lastEntityTick = entity.ticksExisted;
                if (Double.isFinite(previousHeight)) {
                    if (entity.posY > previousHeight + 1e-6)
                        throw new AssertionError("The landing projection rose instead of descending");
                    if (entity.posY < previousHeight - 1e-6) descendingSamples++;
                }
                if (entity.posY < anchor.y() - 1e-6)
                    throw new AssertionError("The landing projection passed below its accepted ground anchor");
                previousHeight = entity.posY;
            }
        }

        private static boolean landing(Rocket rocket) {
            return rocket != null && rocket.physicalManeuver() != null
                && rocket.physicalManeuver()
                    .kind() == OrbitalMechanics.ManeuverKind.LANDING;
        }

        private void assertCompleted() {
            if (failure != null) throw new AssertionError(failure);
            if (!airborneSpawn || descendingSamples < 2) throw new AssertionError(
                "The real landing did not expose an airborne spawn and gradual descent. " + diagnostic());
            Galaxia.LOG.info("[Horizon QA] Landing: {}", diagnostic());
        }

        private String diagnostic() {
            return "joins=" + matchingJoins
                + ", landingJoins="
                + landingJoins
                + ", landingTicks="
                + landingTicks
                + ", projectionSamples="
                + projectionSamples
                + ", descendingSamples="
                + descendingSamples
                + ", airborneSpawn="
                + airborneSpawn
                + ", height="
                + firstHeight
                + "->"
                + previousHeight
                + ", entityTicks="
                + firstEntityTick
                + "->"
                + lastEntityTick
                + ", phaseProgress="
                + firstProgress
                + "->"
                + lastProgress;
        }
    }

    private static String landingDiagnostics(Rocket rocket, VehicleSchedule.Place place) {
        if (rocket.pendingSurfaceLanding() == null) return rocket.status()
            .name();
        var site = place.surfaceSite();
        var world = DimensionManager.getWorld(site.dimension());
        if (world == null) return "target world is unloaded";
        var bounds = site.footprint()
            .bounds(site.position());
        var support = new java.util.ArrayList<String>();
        for (int x = (int) Math.floor(bounds.minX); x < (int) Math.ceil(bounds.maxX); x++) {
            for (int z = (int) Math.floor(bounds.minZ); z < (int) Math.ceil(bounds.maxZ); z++) {
                if (world.getBlock(x, site.y() - 1, z)
                    .getMaterial()
                    .isLiquid() || !world.isSideSolid(x, site.y() - 1, z, ForgeDirection.UP)) support.add(x + "," + z);
            }
        }
        var entities = world.getEntitiesWithinAABBExcludingEntity(null, bounds)
            .stream()
            .map(
                entity -> entity.getClass()
                    .getSimpleName() + ":dead="
                    + entity.isDead
                    + ":own="
                    + (entity instanceof EntityRocket projection && projection.isCurrentProjection(rocket)))
            .toList();
        return "site=" + site
            + ", reserved="
            + Galaxia.proxy.serverRuntime()
                .rockets()
                .landingSiteReservedFor(rocket.id(), place)
            + ", support="
            + support
            + ", collisions="
            + world.func_147461_a(bounds)
                .size()
            + ", entities="
            + entities;
    }

    private static Rocket basicVehicle(UUID teamId) {
        var design = new RocketBlueprint();
        int row = 0;
        for (var definition : List.of(
            RocketPartRegistry.CAPSULE_T1,
            RocketPartRegistry.STORAGE_T1,
            RocketPartRegistry.FUEL_TANK_T1,
            RocketPartRegistry.ENGINE_T1)) {
            if (!design.addPart(new RocketPartInstance(definition, 0, row, 0, false)))
                throw new AssertionError("Basic module does not fit");
            row += definition.height();
        }
        var rocket = Rocket
            .construct(teamId, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), UUID.randomUUID(), design);
        for (var slot : rocket.buildSlots()) if (!rocket.acceptProduced(
            Rocket.Module.produced(
                rocket.id(),
                slot,
                100,
                new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))))
            throw new AssertionError("Module was not accepted");
        if (!rocket.assemble(rocket.assemblySiloId())) throw new AssertionError("Vehicle did not assemble");
        for (var module : rocket.modules()) module.part()
            .def()
            .storage()
            .propellantCapacities()
            .forEach(
                (fuel, amount) -> {
                    if (rocket.refuel(fuel, amount, false) != amount)
                        throw new AssertionError("Actual propellant was not supplied");
                });
        return rocket;
    }
}
