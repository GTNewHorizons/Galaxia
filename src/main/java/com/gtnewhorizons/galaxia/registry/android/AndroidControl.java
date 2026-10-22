package com.gtnewhorizons.galaxia.registry.android;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.AndroidClient;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.AndroidControlPacket;
import com.gtnewhorizons.galaxia.core.network.TetherAnchorSyncPacket;
import com.gtnewhorizons.galaxia.mixin.BlockBreakingAccessor;
import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.Anchor;
import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.AndroidBody;
import com.gtnewhorizons.galaxia.registry.android.AndroidBodies.Session;
import com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.ConnectionStatus;
import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
import com.gtnewhorizons.galaxia.registry.hazards.HazardTemperature;
import com.gtnewhorizons.galaxia.registry.items.tether.KineticTetherState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server lifecycle for the one real player and its selected physical body. */
public final class AndroidControl implements ForgeChunkManager.LoadingCallback {

    private static AndroidBodies bodies;
    private static final Map<UUID, EntityBody> projections = new HashMap<>();
    private static final Map<UUID, ForgeChunkManager.Ticket> capsuleTickets = new HashMap<>();
    private static final Set<UUID> transitioning = new HashSet<>();
    private static final Set<UUID> applying = new HashSet<>();
    private static final Map<UUID, Integer> explosionViews = new HashMap<>();
    private static final Map<UUID, Vec3> operatorPositions = new HashMap<>();
    private static final Map<UUID, ConnectionStatus> stationConnections = new HashMap<>();
    private static final int STATION_STATUS_INTERVAL_TICKS = 5;
    private static final double MOVEMENT_EPSILON = 1e-6;
    private static final float EXPLOSION_SOUND_VOLUME = 4F;
    private static final float EXPLOSION_SOUND_PITCH = .7F;
    private static final DamageSource SELF_DESTRUCTION = new DamageSource("galaxia.android.selfDestruct");
    private static int ticks;

    public static AndroidBodies bodies() {
        if (bodies == null) throw new IllegalStateException("Android bodies are not loaded");
        return bodies;
    }

    public static boolean isManaged(EntityPlayer player) {
        return bodies != null && bodies.biological(player.getUniqueID())
            .isPresent();
    }

    public static boolean isAndroid(EntityLivingBase entity) {
        if (entity instanceof EntityBody body) return !body.isBiological();
        if (!(entity instanceof EntityPlayer player)) return false;
        if (player.worldObj != null && player.worldObj.isRemote) return AndroidClient.isAndroid(player);
        return bodies != null && bodies.session(player.getUniqueID())
            .isPresent();
    }

    public static boolean locked(EntityPlayer player) {
        if (transitioning.contains(player.getUniqueID())) return true;
        return bodies != null && bodies.session(player.getUniqueID())
            .flatMap(s -> bodies.body(s.bodyId()))
            .map(body -> body.armed() && !body.canRetreatFromPortal())
            .orElse(false);
    }

    public static boolean allowsHealthChange(EntityLivingBase entity, float health) {
        return !isAndroid(entity) || health <= entity.getHealth() || applying.contains(entity.getUniqueID());
    }

    @Nullable
    public static BodyState stateOf(EntityBody entity) {
        if (bodies == null || entity.bodyId() == null) return null;
        UUID operator = entity.operatorId();
        if (operator != null) {
            if (bodies.session(operator)
                .isEmpty()) return null;
            return bodies.biological(operator)
                .filter(
                    b -> b.id()
                        .equals(entity.bodyId()))
                .map(AndroidBodies.BiologicalBody::state)
                .orElse(null);
        }
        return bodies.body(entity.bodyId())
            .filter(b -> !bodies.isControlled(b.id()))
            .map(AndroidBody::state)
            .orElse(null);
    }

    public static void applyPhysical(EntityBody entity, BodyState state) {
        applying.add(entity.getUniqueID());
        try {
            state.applyPhysical(entity);
        } finally {
            applying.remove(entity.getUniqueID());
        }
    }

    public static void observe(EntityBody entity, BodyState state) {
        UUID operator = entity.operatorId();
        if (operator != null) {
            float previousHealth = bodies().biological(operator)
                .orElseThrow()
                .state()
                .health();
            bodies().observeBiological(operator, state);
            if (state.health() < previousHealth) {
                EntityPlayerMP player = player(operator);
                if (player != null && isAndroid(player)) Galaxia.GALAXIA_NETWORK
                    .sendTo(new AndroidControlPacket.BiologicalDamage(player.getEntityId()), player);
            }
        } else bodies().observeBody(entity.bodyId(), state);
    }

    public static UUID manufacture(UUID team, Anchor dock, UUID operationId, AndroidBodies.Chassis chassis,
        long initialCharge) throws IOException {
        TileEntityBodyStation station = station(dock);
        if (station == null || station.isCapsule() || !team.equals(station.ownerTeam()))
            throw new IOException("Android manufacture requires its team's physical dock");
        if (!bodies().wasManufactured(operationId) && !station.canManufacture())
            throw new IOException("Android dock is unavailable or its chamber is occupied");
        UUID id = bodies()
            .manufacture(operationId, team, dock, station.manufacturedBodyState(), chassis, initialCharge);
        bodies().body(id)
            .ifPresent(b -> project(b.id(), null, b.state()));
        return id;
    }

    public static boolean activate(EntityPlayerMP player, Anchor capsule, UUID bodyId) {
        TileEntityBodyStation station = station(capsule);
        AndroidBody target = bodies().body(bodyId)
            .orElse(null);
        if (station == null || !station.isCapsule()
            || !station.isPowered()
            || !station.canOperate(player)
            || target == null
            || !target.teamId()
                .equals(station.ownerTeam())
            || target.armed()
            || target.battery() <= 0
            || target.state()
                .health() <= 0
            || bodies.isControlled(bodyId)
            || isAndroid(player)
            || player.isDead
            || player.getHealth() <= 0
            || player.isPlayerSleeping()
            || player.ridingEntity != null
            || player.riddenByEntity != null
            || !station.contains(player)) return false;
        UUID reservation = bodyId;
        var from = GalaxiaCelestialAPI.findByDimension(capsule.dimension());
        var to = GalaxiaCelestialAPI.findByDimension(
            target.dock()
                .dimension());
        if (from.isEmpty() || to.isEmpty()
            || !SatelliteNetworkService.reserveBandwidth(
                target.teamId(),
                reservation,
                from.get()
                    .key(),
                to.get()
                    .key(),
                ConfigMachines.android.requiredBandwidthKbps))
            return false;
        ForgeChunkManager.Ticket ticket = ForgeChunkManager
            .requestTicket(Galaxia.instance, world(capsule.dimension()), ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            SatelliteNetworkService.releaseBandwidth(target.teamId(), reservation);
            return false;
        }
        ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(capsule.x() >> 4, capsule.z() >> 4));
        transitioning.add(player.getUniqueID());
        boolean committed = false;
        try {
            settle(player);
            BodyState biologicalState = station.captureOccupant(player);
            ForgeChunkManager.forceChunk(
                ticket,
                new ChunkCoordIntPair(
                    (int) Math.floor(biologicalState.x()) >> 4,
                    (int) Math.floor(biologicalState.z()) >> 4));
            try {
                bodies.activate(player.getUniqueID(), target.teamId(), bodyId, capsule, biologicalState);
            } catch (IllegalStateException ex) {
                return false;
            }
            committed = true;
            capsuleTickets.put(player.getUniqueID(), ticket);
            removeProjection(bodyId);
            select(player, target.state());
            var biological = bodies.biological(player.getUniqueID())
                .orElseThrow();
            project(biological.id(), player.getUniqueID(), biological.state());
            sync(player, false);
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot commit body activation", ex);
        } finally {
            transitioning.remove(player.getUniqueID());
            if (!committed) {
                ForgeChunkManager.releaseTicket(ticket);
                SatelliteNetworkService.releaseBandwidth(target.teamId(), reservation);
            }
        }
    }

    public static boolean endControl(EntityPlayerMP player) {
        if (bodies == null) return false;
        Session session = bodies.session(player.getUniqueID())
            .orElse(null);
        if (session == null) return false;
        AndroidBody android = bodies.body(session.bodyId())
            .orElseThrow();
        transitioning.add(player.getUniqueID());
        try {
            settle(player);
            captureBiological(player.getUniqueID());
            BodyState biological = bodies.deactivate(player.getUniqueID(), BodyState.capture(player));
            removeProjection(
                bodies.biological(player.getUniqueID())
                    .orElseThrow()
                    .id());
            select(player, biological);
            SatelliteNetworkService.releaseBandwidth(android.teamId(), session.bodyId());
            ForgeChunkManager.Ticket ticket = capsuleTickets.remove(player.getUniqueID());
            if (ticket != null) ForgeChunkManager.releaseTicket(ticket);
            bodies.body(android.id())
                .ifPresent(b -> project(b.id(), null, b.state()));
            sync(player, false);
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot commit return to biological body", ex);
        } finally {
            operatorPositions.remove(player.getUniqueID());
            transitioning.remove(player.getUniqueID());
        }
    }

    private static void settle(EntityPlayerMP player) {
        if (player.riddenByEntity != null) player.riddenByEntity.mountEntity(null);
        if (player.ridingEntity != null) player.mountEntity(null);
        player.theItemInWorldManager.cancelDestroyingBlock(0, 0, 0);
        ((BlockBreakingAccessor) player.theItemInWorldManager).galaxia$setPendingFinish(false);
        Galaxia.GALAXIA_NETWORK.sendTo(new TetherAnchorSyncPacket(KineticTetherState.release(player)), player);
        player.closeScreen();
        if (player.inventory.getItemStack() != null) {
            player.dropPlayerItemWithRandomChoice(player.inventory.getItemStack(), false);
            player.inventory.setItemStack(null);
        }
        player.inventoryContainer.onContainerClosed(player);
        player.clearItemInUse();
    }

    private static void select(EntityPlayerMP player, BodyState state) {
        applying.add(player.getUniqueID());
        try {
            BodyState.detachForTransfer(player);
            if (player.dimension != state.dimension()) {
                WorldServer destination = world(state.dimension());
                player.mcServer.getConfigurationManager()
                    .transferPlayerToDimension(player, state.dimension(), new Teleporter(destination) {

                        @Override
                        public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
                            state.applyLocation(entity);
                        }
                    });
            }
            state.apply(player);
            player.playerNetServerHandler
                .setPlayerLocation(state.x(), state.y(), state.z(), state.yaw(), state.pitch());
            player.sendContainerToPlayer(player.inventoryContainer);
        } finally {
            applying.remove(player.getUniqueID());
        }
    }

    /** Called before any vanilla or supported mod transfer mutates the entity or either world. */
    public static boolean beforeDimensionTransfer(Entity entity, int destination) {
        if (entity instanceof EntityBody body) {
            if (body.isBiological()) return true;
            AndroidBody android = bodies().body(body.bodyId())
                .orElse(null);
            if (android == null || destination == android.dock()
                .dimension()) return false;
            arm(android.id(), entity);
            return true;
        }
        if (!(entity instanceof EntityPlayerMP player) || !isAndroid(player)
            || transitioning.contains(player.getUniqueID())) return false;
        AndroidBody android = bodies().body(
            bodies.session(player.getUniqueID())
                .orElseThrow()
                .bodyId())
            .orElseThrow();
        if (destination == android.dock()
            .dimension()) return false;
        bodies.observeOperator(player.getUniqueID(), BodyState.capture(player));
        arm(android.id(), entity);
        return true;
    }

    private static boolean touchesPortal(Entity entity) {
        return entity.worldObj.isMaterialInBB(entity.boundingBox, Material.portal);
    }

    private static void arm(UUID bodyId, Entity entity) {
        try {
            if (touchesPortal(entity)) bodies().warnPortalTransfer(bodyId, ConfigMachines.android.countdownTicks);
            else bodies().armDestruction(bodyId, ConfigMachines.android.countdownTicks);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot commit android self destruction", ex);
        }
    }

    public static boolean interceptDeath(EntityPlayerMP player, DamageSource source) {
        if (!isAndroid(player)) return false;
        UUID id = bodies().session(player.getUniqueID())
            .orElseThrow()
            .bodyId();
        IChatComponent message = deathMessage(
            bodies().body(id)
                .orElseThrow(),
            source,
            player);
        endControl(player);
        destroy(id, message);
        return true;
    }

    public static void destroyPhysicalBody(EntityBody entity, DamageSource source) {
        if (entity.isBiological()) {
            EntityPlayerMP operator = player(entity.operatorId());
            if (operator == null) throw new IllegalStateException("A controlled biological body has no operator");
            observe(
                entity,
                entity.currentState()
                    .withPhysicalState(entity)
                    .withHealth(0));
            endControl(operator);
            operator.onDeath(source);
        } else {
            bodies().body(entity.bodyId())
                .ifPresent(body -> destroy(body.id(), deathMessage(body, source, entity)));
        }
    }

    static IChatComponent deathMessage(AndroidBody body, DamageSource source, @Nullable EntityLivingBase actor) {
        String name = body.name()
            .isEmpty()
                ? body.chassis()
                    .name() + " "
                    + body.id()
                        .toString()
                        .substring(0, 8)
                : body.name();
        // An unspawned display subject keeps the operator's player identity out of the death message.
        EntityBody subject = new EntityBody(actor == null ? null : actor.worldObj, body.id(), null);
        subject.setCustomNameTag(StatCollector.translateToLocalFormatted("galaxia.android.death.name", name));
        if (actor != null) subject.setRevengeTarget(actor.func_94060_bK());
        return source.func_151519_b(subject);
    }

    private static void destroy(UUID bodyId, IChatComponent message) {
        AndroidBody body = bodies().body(bodyId)
            .orElse(null);
        if (body == null) return;
        WorldServer world = world(
            body.state()
                .dimension());
        world.theChunkProviderServer.loadChunk(
            (int) Math.floor(
                body.state()
                    .x())
                >> 4,
            (int) Math.floor(
                body.state()
                    .z())
                >> 4);
        try {
            List<ItemStack> drops = bodies.destroy(bodyId);
            removeProjection(bodyId);
            explosionViews.remove(bodyId);
            for (ItemStack stack : drops) world.spawnEntityInWorld(
                new EntityItem(
                    world,
                    body.state()
                        .x(),
                    body.state()
                        .y(),
                    body.state()
                        .z(),
                    stack));
            MinecraftServer.getServer()
                .getConfigurationManager()
                .sendChatMsg(message);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot commit android destruction", ex);
        }
    }

    public static void stationLost(Anchor anchor) {
        if (bodies == null) return;
        for (Session session : bodies.sessions()) {
            if (bodies.biological(session.operatorId())
                .orElseThrow()
                .capsule()
                .equals(anchor)) {
                EntityPlayerMP operator = player(session.operatorId());
                if (operator != null) endControl(operator);
            }
        }
    }

    public static void mergeTeams(UUID consumed, UUID surviving) {
        if (bodies == null) return;
        for (Session session : bodies.sessions()) {
            if (bodies.body(session.bodyId())
                .orElseThrow()
                .teamId()
                .equals(consumed)) {
                EntityPlayerMP operator = player(session.operatorId());
                if (operator != null) endControl(operator);
            }
        }
        try {
            bodies.mergeTeams(consumed, surviving);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot commit android team merge", ex);
        }
    }

    public static void hydrateLogin(EntityPlayerMP player, @Nullable NBTTagCompound vanilla) {
        if (bodies == null) return;
        var selected = bodies.biological(player.getUniqueID());
        if (selected.isEmpty()) {
            if (vanilla != null && vanilla.getBoolean("galaxiaManagedBody")) {
                throw new IllegalStateException("Managed player is missing its authoritative body checkpoint");
            }
            return;
        }
        BodyState state = selected.get()
            .state();
        player.dimension = state.dimension();
        player.setWorld(world(state.dimension()));
        applying.add(player.getUniqueID());
        try {
            state.hydrate(player);
        } finally {
            applying.remove(player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void load(WorldEvent.Load event) {
        if (event.world.isRemote || event.world.provider.dimensionId != 0 || bodies != null) return;
        try {
            bodies = AndroidBodies.openWorld(
                event.world.getSaveHandler()
                    .getWorldDirectory());
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load authoritative body checkpoint", ex);
        }
        ticks = 0;
    }

    @SubscribeEvent
    public void save(WorldEvent.Save event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0 && bodies != null) checkpoint();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote || bodies == null) return;
        if (event.world.provider.dimensionId != 0) {
            projections.values()
                .removeIf(entity -> entity.worldObj == event.world);
            return;
        }
        checkpoint();
        bodies = null;
        projections.clear();
        capsuleTickets.clear();
        explosionViews.clear();
        operatorPositions.clear();
        stationConnections.clear();
        applying.clear();
        transitioning.clear();
    }

    @Override
    public void ticketsLoaded(List<ForgeChunkManager.Ticket> tickets, World world) {
        // Recovery always ends control, so persisted remote-control tickets have no surviving owner.
        for (ForgeChunkManager.Ticket ticket : tickets) ForgeChunkManager.releaseTicket(ticket);
    }

    @SubscribeEvent
    public void join(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !(event.entity instanceof EntityBody body)) return;
        BodyState state = stateOf(body);
        EntityBody existing = projections.get(body.bodyId());
        if (state == null || state.dimension() != event.world.provider.dimensionId
            || body.getDistanceSq(state.x(), state.y(), state.z()) > 0.0001
            || (existing != null && existing != body
                && !existing.isDead
                && DimensionManager.getWorld(existing.dimension) == existing.worldObj
                && existing.worldObj.getEntityByID(existing.getEntityId()) == existing)) {
            event.setCanceled(true);
            return;
        }
        if (body.isBiological()) {
            EntityPlayerMP owner = player(body.operatorId());
            if (owner == null) {
                event.setCanceled(true);
                return;
            }
            body.bindOperatorProfile(owner.getGameProfile());
        }
        projections.put(body.bodyId(), body);
        body.currentState();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        stationConnections.remove(event.player.getUniqueID());
        if (!(event.player instanceof EntityPlayerMP player) || !isManaged(player)) return;
        bodies.biological(player.getUniqueID())
            .orElseThrow()
            .state()
            .reconcileHealth(player);
        sync(player, false);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        stationConnections.remove(event.player.getUniqueID());
        if (!(event.player instanceof EntityPlayerMP player) || !isManaged(player)) return;
        endControl(player);
        bodies.observeOperator(player.getUniqueID(), BodyState.capture(player));
        checkpoint();
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        stationConnections.remove(event.player.getUniqueID());
        if (event.player instanceof EntityPlayerMP player && isManaged(player)) {
            bodies.observeOperator(player.getUniqueID(), BodyState.capture(player));
            checkpoint();
            sync(player, false);
        }
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        stationConnections.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void stationStatus(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)
            || bodies == null
            || player.ticksExisted % STATION_STATUS_INTERVAL_TICKS != 0) return;
        ConnectionStatus status = stationConnection(player);
        ConnectionStatus previous = stationConnections.getOrDefault(player.getUniqueID(), ConnectionStatus.NONE);
        if (status == previous) return;
        if (status == ConnectionStatus.NONE) stationConnections.remove(player.getUniqueID());
        else stationConnections.put(player.getUniqueID(), status);
        Galaxia.GALAXIA_NETWORK.sendTo(new AndroidControlPacket.StationStatus(status), player);
    }

    private static ConnectionStatus stationConnection(EntityPlayerMP player) {
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY);
        int z = MathHelper.floor_double(player.posZ);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 0; dy++) {
            if (!player.worldObj.blockExists(x + dx, y + dy, z + dz)) continue;
            if (player.worldObj.getTileEntity(x + dx, y + dy, z + dz) instanceof TileEntityBodyStation station) {
                ConnectionStatus status = station.connectionStatus(player);
                if (status != ConnectionStatus.NONE) return status;
            }
        }
        return ConnectionStatus.NONE;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || bodies == null) return;
        ticks++;
        for (Session session : bodies.sessions()) tickSession(session);
        for (AndroidBody body : bodies.allBodies()) {
            if (!bodies.isControlled(body.id())) project(body.id(), null, body.state());
            if (!body.armed()) continue;
            EntityPlayerMP operator = null;
            for (Session session : bodies.sessions()) if (session.bodyId()
                .equals(body.id())) operator = player(session.operatorId());
            Entity actor = operator == null ? projections.get(body.id()) : operator;
            if (body.canRetreatFromPortal() && actor != null && !touchesPortal(actor)) {
                try {
                    bodies.cancelPortalWarning(body.id());
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot commit portal warning cancellation", ex);
                }
                if (operator != null) sync(operator, false);
                continue;
            }
            if (body.portalWarning() && operator != null)
                bodies.observeOperator(operator.getUniqueID(), BodyState.capture(operator));
            int remaining = body.destructionTicks();
            if (remaining > 0) {
                bodies.setDestructionTicks(body.id(), remaining - 1);
                continue;
            }
            int viewed = explosionViews.getOrDefault(body.id(), 0);
            if (viewed == 0) {
                BodyState state = body.state();
                world(state.dimension()).playSoundEffect(
                    state.x(),
                    state.y(),
                    state.z(),
                    "random.explode",
                    EXPLOSION_SOUND_VOLUME,
                    EXPLOSION_SOUND_PITCH);
                if (operator != null) sync(operator, true);
            }
            if (operator != null) {
                if (viewed < ConfigMachines.android.viewTicks) {
                    explosionViews.put(body.id(), viewed + 1);
                    continue;
                }
                endControl(operator);
            }
            destroy(body.id(), deathMessage(body, SELF_DESTRUCTION, null));
        }
        if (ticks % ConfigMachines.android.checkpointIntervalTicks == 0) checkpoint();
    }

    private static void tickSession(Session session) {
        EntityPlayerMP operator = player(session.operatorId());
        if (operator == null) return;
        AndroidBody body = bodies.body(session.bodyId())
            .orElseThrow();
        var biological = bodies.biological(session.operatorId())
            .orElseThrow();
        if (!SatelliteNetworkService.hasReservation(body.teamId(), session.bodyId())) {
            endControl(operator);
            operator.addChatMessage(new ChatComponentTranslation("galaxia.android.connection.bandwidth_lost"));
            return;
        }
        TileEntityBodyStation capsule = station(biological.capsule());
        if (capsule == null || !capsule.isPowered() || !capsule.canOperate(operator) || body.battery() == 0) {
            endControl(operator);
            return;
        }
        project(biological.id(), session.operatorId(), biological.state());
        if (body.armed() && !body.canRetreatFromPortal()) {
            operator.motionX = operator.motionY = operator.motionZ = 0;
            operator.playerNetServerHandler.setPlayerLocation(
                body.state()
                    .x(),
                body.state()
                    .y(),
                body.state()
                    .z(),
                operator.rotationYaw,
                operator.rotationPitch);
        }
        Vec3 previous = operatorPositions
            .put(operator.getUniqueID(), Vec3.createVectorHelper(operator.posX, 0, operator.posZ));
        boolean moving = operator.ridingEntity == null && previous != null
            && (Math.abs(operator.posX - previous.xCoord) > MOVEMENT_EPSILON
                || Math.abs(operator.posZ - previous.zCoord) > MOVEMENT_EPSILON);
        boolean heating = false;
        var definition = CelestialDimensionMaterializer.findDefinitionById(operator.dimension);
        if (definition.isPresent()) {
            var station = java.util.Optional.ofNullable(
                GalaxiaAPI.getStationAround(
                    operator.worldObj,
                    operator.dimension,
                    MathHelper.floor_double(operator.posX),
                    MathHelper.floor_double(operator.posY),
                    MathHelper.floor_double(operator.posZ)));
            heating = definition.get()
                .effects()
                .getTemperature(operator.worldObj) < HazardTemperature.getAcceptableMinTemp(operator, station);
        }
        long load = ConfigMachines.android
            .energyPerTick(moving, operator.isSprinting(), heating, GalaxiaAPI.getGravity(operator) == 0)
            * body.chassis()
                .energyMultiplier();
        bodies.setBattery(body.id(), Math.max(0, body.battery() - load));
        if (ticks % 10 == 0) sync(operator, false);
    }

    private static void checkpoint() {
        for (Object value : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (isManaged(player) && !transitioning.contains(player.getUniqueID()))
                bodies.observeOperator(player.getUniqueID(), BodyState.capture(player));
        }
        for (EntityBody entity : List.copyOf(projections.values())) {
            if (!entity.isDead && stateOf(entity) != null) observe(
                entity,
                entity.currentState()
                    .withPhysicalState(entity));
        }
        try {
            bodies.checkpoint();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot checkpoint physical bodies", ex);
        }
    }

    private static void captureBiological(UUID operator) {
        var biological = bodies.biological(operator)
            .orElseThrow();
        EntityBody entity = projections.get(biological.id());
        if (entity != null && !entity.isDead) observe(
            entity,
            entity.currentState()
                .withPhysicalState(entity));
    }

    private static void project(UUID id, @Nullable UUID operator, BodyState state) {
        EntityBody existing = projections.get(id);
        if (existing != null && !existing.isDead
            && DimensionManager.getWorld(existing.dimension) == existing.worldObj
            && existing.worldObj.getEntityByID(existing.getEntityId()) == existing) return;
        WorldServer world = DimensionManager.getWorld(state.dimension());
        if (world == null
            || !world.blockExists((int) Math.floor(state.x()), (int) state.y(), (int) Math.floor(state.z()))) return;
        EntityPlayerMP owner = operator == null ? null : player(operator);
        if (operator != null && owner == null) return;
        EntityBody entity = new EntityBody(world, id, owner == null ? null : owner.getGameProfile());
        entity.currentState();
        world.spawnEntityInWorld(entity);
    }

    private static void removeProjection(UUID id) {
        EntityBody entity = projections.remove(id);
        if (entity != null) {
            entity.setDead();
            entity.worldObj.removeEntity(entity);
        }
    }

    @Nullable
    private static EntityPlayerMP player(UUID id) {
        for (Object value : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (player.getUniqueID()
                .equals(id)) return player;
        }
        return null;
    }

    private static WorldServer world(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null) {
            DimensionManager.initDimension(dimension);
            world = DimensionManager.getWorld(dimension);
        }
        if (world == null) throw new IllegalStateException("Body dimension is unavailable: " + dimension);
        return world;
    }

    @Nullable
    private static TileEntityBodyStation station(Anchor anchor) {
        WorldServer world = DimensionManager.getWorld(anchor.dimension());
        if (world == null) return null;
        TileEntity tile = world.getTileEntity(anchor.x(), anchor.y(), anchor.z());
        return tile instanceof TileEntityBodyStation station && station.anchor()
            .equals(anchor) ? station : null;
    }

    private static void sync(EntityPlayerMP player, boolean explosion) {
        var body = bodies.session(player.getUniqueID())
            .flatMap(s -> bodies.body(s.bodyId()));
        AndroidControlPacket packet = body
            .map(
                b -> new AndroidControlPacket(
                    player.getEntityId(),
                    true,
                    b.battery(),
                    b.capacity(),
                    b.destructionTicks(),
                    b.portalWarning(),
                    explosion))
            .orElseGet(() -> new AndroidControlPacket(player.getEntityId(), false, 0, 0, -1, false, false));
        Galaxia.GALAXIA_NETWORK.sendToAll(packet);
    }

    @SubscribeEvent
    public void jump(LivingJumpEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP operator) || !isAndroid(operator) || locked(operator))
            return;
        AndroidBody body = bodies.body(
            bodies.session(operator.getUniqueID())
                .orElseThrow()
                .bodyId())
            .orElseThrow();
        bodies.setBattery(
            body.id(),
            Math.max(
                0,
                body.battery() - ConfigMachines.android.jumpEnergy * body.chassis()
                    .energyMultiplier()));
    }

    @SubscribeEvent
    public void heal(LivingHealEvent event) {
        if (isAndroid(event.entityLiving) && !applying.contains(event.entityLiving.getUniqueID()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void interact(PlayerInteractEvent event) {
        if (locked(event.entityPlayer)
            || (isAndroid(event.entityPlayer) && event.world.getBlock(event.x, event.y, event.z) == Blocks.ender_chest))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void sleep(PlayerSleepInBedEvent event) {
        if (isAndroid(event.entityPlayer)) event.result = EntityPlayer.EnumStatus.OTHER_PROBLEM;
    }

    @SubscribeEvent
    public void useItem(PlayerUseItemEvent.Start event) {
        if (locked(event.entityPlayer) || (isAndroid(event.entityPlayer) && event.item.getItem() instanceof ItemFood))
            event.setCanceled(true);
    }
}
