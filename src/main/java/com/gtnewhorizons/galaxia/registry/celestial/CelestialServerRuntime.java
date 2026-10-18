package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.gtnhlib.teams.TeamNetwork;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWorkerContribution;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocketSeat;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE.TransportEndpoint;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDiscoveryWorkerSource;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

/** Server composition and lifecycle for celestial assets, vehicles, knowledge, discovery, and satellites. */
public record CelestialServerRuntime(CelestialDiscoveryScanService scans,
    Supplier<List<CelestialDiscoveryWorkerContribution>> discoveryWorkers, Rocket.Registry rockets) {

    public static CelestialServerRuntime create() {
        CelestialKnowledgeService.clearDiscoveryDomains();
        CelestialKnowledgeService.registerDiscoveryDomain(new AsteroidFieldDiscoveryPolicy());
        OrbitalMechanics.registerMinorBodyResolver(AsteroidFieldOrbitResolver.INSTANCE);
        CelestialDiscoveryScanService scans = new CelestialDiscoveryScanService(
            CelestialKnowledgeService::discoveryDomain);
        return new CelestialServerRuntime(
            scans,
            () -> SatelliteDiscoveryWorkerSource.prospectingWorkers(CelestialKnowledgeService::discoveryScopeRevision),
            new Rocket.Registry());
    }

    public void tick() {
        SatelliteNetworkService.tickDataJobs();
        scans.tick(discoveryWorkers.get(), 1);
        tickOpeningSurveys();
        for (Rocket rocket : rockets.all()) {
            var landing = rocket.pendingSurfaceLanding();
            if (landing != null && !landingSiteValid(rocket, landing, true)) rocket.blockSurfaceLanding();
            else rocket.tick(rockets, GalaxiaCelestialAPI.currentOrbitalTime());
        }
        for (Rocket rocket : rockets.all()) materializeProjection(rocket);
        updatePassengers();
    }

    @Nullable
    public Rocket restrictedPassenger(UUID playerId) {
        Rocket rocket = rockets.passengerVehicle(playerId);
        return rocket != null && rocket.restrictsPassengers() ? rocket : null;
    }

    @Nullable
    private EntityRocket loadedProjection(Rocket rocket) {
        var identity = rocket.projection();
        if (identity == null) return null;
        for (var world : DimensionManager.getWorlds()) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityRocket found && !found.isDead && found.isCurrentProjection(rocket))
                    return found;
            }
        }
        return null;
    }

    private void materializeProjection(Rocket rocket) {
        for (var loadedWorld : DimensionManager.getWorlds()) {
            for (Entity entity : loadedWorld.loadedEntityList) {
                if (entity instanceof EntityRocketSeat seat && seat.getRocket() instanceof EntityRocket parent
                    && rocket.id()
                        .equals(parent.vehicleId())
                    && (!rocket.allowsProjection() || !parent.isCurrentProjection(rocket))) seat.setDead();
            }
            for (Entity entity : loadedWorld.loadedEntityList) {
                if (entity instanceof EntityRocket projection && rocket.id()
                    .equals(projection.vehicleId())
                    && (!rocket.allowsProjection() || !projection.isCurrentProjection(rocket))) projection.setDead();
            }
        }
        if (!rocket.allowsProjection()) return;
        var current = loadedProjection(rocket);
        if (current != null) {
            var position = rocket.projectionPosition();
            if (position != null && position.dimension() == current.worldObj.provider.dimensionId)
                current.setPosition(position.x(), position.y(), position.z());
            return;
        }
        var place = rocket.projectionPlace();
        Rocket.PhysicalPosition position;
        WorldServer world;
        if (place.target() != null) {
            if (!(place.target() instanceof VehicleSchedule.Exact exact)) return;
            var ref = CelestialAssetStore.findEndpoint(exact.endpointId());
            var controller = ref == null ? null : TransportEndpoint.loaded(ref);
            if (controller == null || !controller.isAvailableTo(rocket.teamId(), place.body())) return;
            var tile = (net.minecraft.tileentity.TileEntity) controller;
            var point = controller.vehiclePosition();
            world = (WorldServer) tile.getWorldObj();
            position = new Rocket.PhysicalPosition(
                world.provider.dimensionId,
                point.x() + .5,
                point.y(),
                point.z() + .5,
                0);
        } else {
            var dimension = GalaxiaCelestialAPI.get(place.body())
                .map(CelestialObject::dimensionEnum)
                .orElse(null);
            if (dimension == null) return;
            var site = place.surfaceSite();
            position = site == null ? rocket.physicalPosition() : site.position();
            if (position == null || position.dimension() != dimension.getId()) return;
            world = DimensionManager.getWorld(dimension.getId());
            if (world == null) {
                DimensionManager.initDimension(dimension.getId());
                world = DimensionManager.getWorld(dimension.getId());
            }
            if (world == null) return;
            world.getChunkFromBlockCoords((int) Math.floor(position.x()), (int) Math.floor(position.z()));
        }
        var entity = new EntityRocket(world);
        entity.setPositionAndRotation(position.x(), position.y(), position.z(), position.yaw(), 0);
        if (!entity.bind(rocket)) return;
        entity.setProjectionVisible(true);
        if (world.spawnEntityInWorld(entity)) entity.initializeSeats();
        else rocket.retireProjection(entity.getUniqueID(), entity.projectionGeneration());
    }

    private void updatePassengers() {
        if (rockets.all()
            .stream()
            .allMatch(
                rocket -> rocket.passengers()
                    .isEmpty()))
            return;
        for (EntityPlayerMP player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            Rocket rocket = rockets.passengerVehicle(player.getUniqueID());
            if (rocket == null) continue;
            if (rocket.restrictsPassengers()) {
                player.noClip = true;
                player.fallDistance = 0;
                player.motionX = player.motionY = player.motionZ = 0;
                var anchor = rocket.passengerAnchor();
                if (player.ridingEntity == null && anchor != null && player.dimension == anchor.dimension()) {
                    player.playerNetServerHandler.setPlayerLocation(
                        anchor.x(),
                        anchor.y() + 1,
                        anchor.z(),
                        player.rotationYaw,
                        player.rotationPitch);
                }
                continue;
            }
            var position = rocket.physicalPosition();
            if (position == null) continue;
            player.noClip = false;
            player.fallDistance = 0;
            if (player.isSneaking() && player.ridingEntity == null) {
                rocket.disembark(player.getUniqueID());
                continue;
            }
            if (player.dimension != position.dimension()) {
                player.mountEntity(null);
                WorldServer target = DimensionManager.getWorld(position.dimension());
                if (target == null) {
                    DimensionManager.initDimension(position.dimension());
                    target = DimensionManager.getWorld(position.dimension());
                }
                if (target == null) continue;
                MinecraftServer.getServer()
                    .getConfigurationManager()
                    .transferPlayerToDimension(player, position.dimension(), new Teleporter(target) {

                        @Override
                        public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
                            entity
                                .setLocationAndAngles(position.x(), position.y() + 1, position.z(), position.yaw(), 0);
                        }

                        @Override
                        public boolean makePortal(Entity entity) {
                            return true;
                        }
                    });
            }
            EntityRocket entity = loadedProjection(rocket);
            if (entity != null && !entity.isPlayerAboard(player)) {
                player.playerNetServerHandler
                    .setPlayerLocation(position.x(), position.y() + 1, position.z(), position.yaw(), 0);
                entity.restorePassenger(player);
            }
        }
    }

    public Rocket.CommandResult startRocket(EntityPlayerMP player, UUID vehicleId, long expectedRevision) {
        Rocket rocket = authorizedRocket(player, vehicleId);
        return rocket == null ? Rocket.CommandResult.FORBIDDEN : rocket.start(rocket.teamId(), expectedRevision);
    }

    public Rocket.CommandResult reviewRocketRisk(EntityPlayerMP player, UUID vehicleId, long expectedRevision,
        boolean proceed) {
        Rocket rocket = authorizedRocket(player, vehicleId);
        return rocket == null ? Rocket.CommandResult.FORBIDDEN
            : rocket.reviewLaunchRisk(rocket.teamId(), expectedRevision, proceed);
    }

    @Nullable
    private Rocket authorizedRocket(EntityPlayerMP player, UUID vehicleId) {
        Rocket rocket = rockets.find(vehicleId);
        UUID team = GTTeamsCompat.getTeam(player);
        if (rocket == null || team == null
            || !team.equals(rocket.teamId())
            || !GTTeamsCompat.hasPermission(player, TeamAction.CONFIGURE_LOGISTICS)) return null;
        return rocket;
    }

    @Nullable
    public LandingSearch beginRocketLandingSearch(EntityPlayerMP player, UUID vehicleId, long expectedRevision,
        CelestialObjectKey body, @Nullable Integer x, @Nullable Integer z) {
        var rocket = authorizedRocket(player, vehicleId);
        if (rocket == null || rocket.revision() != expectedRevision) return null;
        var anchor = currentPhysicalAnchor(rocket);
        if ((x == null || z == null) && anchor == null) return null;
        int originX = x == null ? (int) Math.floor(anchor.x()) : x;
        int originZ = z == null ? (int) Math.floor(anchor.z()) : z;
        if (Math.abs((long) originX) >= 30000000 || Math.abs((long) originZ) >= 30000000) return null;
        var world = landingWorld(body);
        return world == null ? null
            : new LandingSearch(this, world, vehicleId, body, rocket.footprint(), originX, originZ);
    }

    public Rocket.CommandResult acceptRocketLanding(EntityPlayerMP player, UUID vehicleId, long expectedRevision,
        VehicleSchedule.Place place, VehicleSchedule.RouteSettings settings) {
        var rocket = authorizedRocket(player, vehicleId);
        if (rocket == null) return Rocket.CommandResult.FORBIDDEN;
        if (rocket.revision() != expectedRevision) return Rocket.CommandResult.STALE;
        if (!landingSiteValid(rocket, place, false)) return Rocket.CommandResult.BLOCKED;
        return rockets.acceptManualLanding(vehicleId, rocket.teamId(), expectedRevision, place, settings);
    }

    public Rocket.CommandResult cancelRocketLanding(EntityPlayerMP player, UUID vehicleId, long expectedRevision) {
        var rocket = authorizedRocket(player, vehicleId);
        return rocket == null ? Rocket.CommandResult.FORBIDDEN
            : rocket.cancelManualLanding(rocket.teamId(), expectedRevision);
    }

    @Nullable
    private Rocket.PhysicalPosition currentPhysicalAnchor(Rocket rocket) {
        if (!rocket.allowsProjection()) return null;
        var projection = loadedProjection(rocket);
        if (projection != null) {
            return new Rocket.PhysicalPosition(
                projection.worldObj.provider.dimensionId,
                projection.posX,
                projection.posY,
                projection.posZ,
                projection.rotationYaw);
        }
        var place = rocket.projectionPlace();
        if (place.target() instanceof VehicleSchedule.Exact exact) {
            var endpoint = CelestialAssetStore.findEndpoint(exact.endpointId());
            var controller = endpoint == null ? null : TransportEndpoint.loaded(endpoint);
            if (controller == null || !controller.isAvailableTo(rocket.teamId(), place.body())) return null;
            var world = ((net.minecraft.tileentity.TileEntity) controller).getWorldObj();
            var position = controller.vehiclePosition();
            return new Rocket.PhysicalPosition(
                world.provider.dimensionId,
                position.x() + .5,
                position.y(),
                position.z() + .5,
                0);
        }
        if (place.context() != VehicleSchedule.Context.SURFACE) return null;
        var dimension = GalaxiaCelestialAPI.get(place.body())
            .map(CelestialObject::dimensionEnum)
            .orElse(null);
        var position = rocket.physicalPosition();
        return dimension != null && position != null && position.dimension() == dimension.getId() ? position : null;
    }

    @Nullable
    private static WorldServer landingWorld(CelestialObjectKey body) {
        var dimension = GalaxiaCelestialAPI.get(body)
            .map(CelestialObject::dimensionEnum)
            .orElse(null);
        if (dimension == null || !DimensionManager.isDimensionRegistered(dimension.getId())) return null;
        var world = DimensionManager.getWorld(dimension.getId());
        if (world == null) {
            DimensionManager.initDimension(dimension.getId());
            world = DimensionManager.getWorld(dimension.getId());
        }
        return world;
    }

    private boolean landingSiteValid(Rocket rocket, VehicleSchedule.Place place, boolean acceptedArrival) {
        var site = place.surfaceSite();
        if (site == null || !site.footprint()
            .equals(rocket.footprint())) return false;
        var world = landingWorld(place.body());
        return world != null && world.provider.dimensionId == site.dimension()
            && terrainClear(world, site, acceptedArrival ? rocket : null)
            && (acceptedArrival ? rockets.landingSiteReservedFor(rocket.id(), place)
                : rockets.landingSiteAvailable(rocket.id(), place));
    }

    private static boolean terrainClear(WorldServer world, VehicleSchedule.SurfaceSite site,
        @Nullable Rocket arriving) {
        AxisAlignedBB bounds = site.footprint()
            .bounds(site.position());
        if (bounds.minY < 1 || bounds.maxY > world.getActualHeight()
            || bounds.minX < -30000000
            || bounds.maxX >= 30000000
            || bounds.minZ < -30000000
            || bounds.maxZ >= 30000000) return false;
        if (!prepareLandingTerrain(world, bounds)) return false;
        for (int x = (int) Math.floor(bounds.minX); x < (int) Math.ceil(bounds.maxX); x++) {
            for (int z = (int) Math.floor(bounds.minZ); z < (int) Math.ceil(bounds.maxZ); z++) {
                if (world.getBlock(x, site.y() - 1, z)
                    .getMaterial()
                    .isLiquid() || !world.isSideSolid(x, site.y() - 1, z, ForgeDirection.UP)) return false;
                for (int y = site.y(); y < (int) Math.ceil(bounds.maxY); y++) {
                    if (world.getBlock(x, y, z)
                        .getMaterial()
                        .isLiquid()) return false;
                }
            }
        }
        if (!world.func_147461_a(bounds)
            .isEmpty()) return false;
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(null, bounds)) {
            if (!entity.isDead && (arriving == null || !isArrivalRepresentation(entity, arriving))) return false;
        }
        return true;
    }

    private static boolean prepareLandingTerrain(WorldServer world, AxisAlignedBB bounds) {
        for (int x = (int) Math.floor(bounds.minX) >> 4; x <= ((int) Math.ceil(bounds.maxX) - 1) >> 4; x++) {
            for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= ((int) Math.ceil(bounds.maxZ) - 1) >> 4; z++) {
                if (!world.blockExists(x << 4, 64, z << 4)) {
                    world.theChunkProviderServer.loadChunk(x, z);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isArrivalRepresentation(Entity entity, Rocket rocket) {
        if (isCurrentProjectionEntity(entity, rocket)) return true;
        return entity instanceof EntityPlayer player && rocket.passengers()
            .contains(player.getUniqueID()) && isCurrentProjectionEntity(entity.ridingEntity, rocket);
    }

    private static boolean isCurrentProjectionEntity(@Nullable Entity entity, Rocket rocket) {
        if (entity instanceof EntityRocket projection) return projection.isCurrentProjection(rocket);
        return entity instanceof EntityRocketSeat seat && seat.getRocket() instanceof EntityRocket projection
            && projection.isCurrentProjection(rocket);
    }

    /** A panel owns this transient search. Only accepting its exact result reserves world space. */
    public static final class LandingSearch {

        private static final int CANDIDATES_PER_ADVANCE = 32;
        private static final int WORLD_LIMIT = 30000000;
        private final CelestialServerRuntime runtime;
        private final WorldServer world;
        private final UUID vehicleId;
        private final CelestialObjectKey body;
        private final Rocket.Footprint footprint;
        private final int originX;
        private final int originZ;
        private final PriorityQueue<RowPoint> frontier = new PriorityQueue<>(
            Comparator.comparingLong(RowPoint::distanceSquared)
                .thenComparingInt(RowPoint::x)
                .thenComparingInt(RowPoint::z));
        private int nextRowRadius = 1;
        private boolean done;
        @Nullable
        private VehicleSchedule.Place result;

        LandingSearch(CelestialServerRuntime runtime, WorldServer world, UUID vehicleId, CelestialObjectKey body,
            Rocket.Footprint footprint, int originX, int originZ) {
            this.runtime = runtime;
            this.world = world;
            this.vehicleId = vehicleId;
            this.body = body;
            this.footprint = footprint;
            this.originX = originX;
            this.originZ = originZ;
            addRow(originZ);
        }

        public void advance() {
            if (done) return;
            for (int work = 0; work < CANDIDATES_PER_ADVANCE; work++) {
                if (frontier.isEmpty()) {
                    done = true;
                    return;
                }
                // Each new Z row starts at origin X. Admit it before any equally distant queued point.
                while (nextRowRadius <= WORLD_LIMIT - 1L + Math.abs((long) originZ)
                    && (long) nextRowRadius * nextRowRadius <= frontier.element()
                        .distanceSquared()) {
                    addRow((long) originZ - nextRowRadius);
                    addRow((long) originZ + nextRowRadius);
                    nextRowRadius++;
                }
                var point = frontier.element();
                if (!prepareTerrain(point)) return;
                frontier.remove();
                addNextInRow(point);
                result = candidate(point.x(), point.z());
                if (result != null) {
                    done = true;
                    frontier.clear();
                    return;
                }
            }
        }

        private boolean prepareTerrain(RowPoint point) {
            var bounds = footprint
                .bounds(new Rocket.PhysicalPosition(world.provider.dimensionId, point.x() + .5, 0, point.z() + .5, 0));
            if (bounds.minX < -WORLD_LIMIT || bounds.maxX >= WORLD_LIMIT
                || bounds.minZ < -WORLD_LIMIT
                || bounds.maxZ >= WORLD_LIMIT) return true;
            return prepareLandingTerrain(world, bounds);
        }

        private void addRow(long z) {
            if (Math.abs(z) >= WORLD_LIMIT) return;
            frontier.add(point(originX, (int) z, 0));
        }

        private void addNextInRow(RowPoint previous) {
            for (int step = previous.step() + 1;; step++) {
                long radius = (step + 1L) / 2;
                if (radius > WORLD_LIMIT - 1L + Math.abs((long) originX)) return;
                long x = originX + (step % 2 == 1 ? -radius : radius);
                if (Math.abs(x) >= WORLD_LIMIT) continue;
                frontier.add(point((int) x, previous.z(), step));
                return;
            }
        }

        private RowPoint point(int x, int z, int step) {
            long dx = (long) x - originX;
            long dz = (long) z - originZ;
            return new RowPoint(x, z, step, dx * dx + dz * dz);
        }

        @Nullable
        private VehicleSchedule.Place candidate(int x, int z) {
            int y = world.getTopSolidOrLiquidBlock(x, z);
            if (y < 1 || (long) y + footprint.height() > Math.min(256, world.getActualHeight())) return null;
            var site = new VehicleSchedule.SurfaceSite(world.provider.dimensionId, x, y, z, footprint);
            var place = new VehicleSchedule.Place(body, VehicleSchedule.Context.SURFACE, null, site);
            return terrainClear(world, site, null) && runtime.rockets.landingSiteAvailable(vehicleId, place) ? place
                : null;
        }

        public boolean done() {
            return done;
        }

        @Nullable
        public VehicleSchedule.Place result() {
            return result;
        }

        @Nullable
        public String problemKey() {
            return done && result == null ? "galaxia.rocket.schedule.manual.no_site" : null;
        }

        private record RowPoint(int x, int z, int step, long distanceSquared) {}
    }

    private void tickOpeningSurveys() {
        var luna = CelestialObjectKey.registered(CelestialObjectId.MOON);
        for (var team : TeamManager.getTeamMap()
            .values()) {
            if (!(team.getData(GalaxiaTeamData.ID) instanceof GalaxiaTeamData data)
                || !data.hasAchievement(GalaxiaTeamData.Achievement.STAR_MAP_ACTIVATION)) continue;
            long previousTicks = data.getOpeningSurveyTicks();
            boolean completed = data.tickOpeningSurvey(ConfigRocket.opening.lunaSurveyTicks);
            if (data.getOpeningSurveyTicks() != previousTicks || completed) {
                team.markDirty();
                if (completed || data.getOpeningSurveyTicks() % 20 == 0)
                    TeamNetwork.syncTeamData(team, GalaxiaTeamData.ID);
            }
            if (!data.isOpeningSurveyComplete() || CelestialKnowledgeService.resourceKnowledge(team.getTeamId(), luna)
                == CelestialResourceKnowledgeState.PROFILE) continue;
            CelestialKnowledgeService.putFacts(
                team.getTeamId(),
                luna,
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
            var packet = CelestialKnowledgeSyncPacket.forTeam(team.getTeamId(), scans);
            TeamManager.forEachOnlineTeamMember(team, player -> Galaxia.GALAXIA_NETWORK.sendTo(packet, player));
        }
    }

    public void mergeTeams(UUID consumedTeam, UUID survivingTeam) {
        if (consumedTeam.equals(survivingTeam)) return;
        CelestialKnowledgeService.mergeTeams(consumedTeam, survivingTeam);
        List<CelestialAsset> transferred = CelestialAssetStore.transferTeamAssets(consumedTeam, survivingTeam);
        SatelliteNetworkService.mergeTeams(consumedTeam, survivingTeam, transferred);
        scans.mergeTeams(consumedTeam, survivingTeam, discoveryWorkers.get());
        rockets.mergeTeams(consumedTeam, survivingTeam);
    }

    /** Clears per-world state. Discovery domains are process-wide registrations from {@link #create()}. */
    public void reset() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
        CelestialKnowledgeService.clearFacts();
        scans.clear();
        rockets.clear();
    }
}
