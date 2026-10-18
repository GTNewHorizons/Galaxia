package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

public interface IRocketControllerTE {

    MachineIdentity machineIdentity();

    ForgeDirection getPlacedFacing();

    void setPlacedFacing(ForgeDirection dir);

    boolean isStructureValid();

    ExtendedFacing getCurrentFacing();

    /** Native endpoint capability, shared by physical Silos and Landing Pads. */
    interface TransportEndpoint extends IRocketControllerTE {

        EndpointBinding endpointBinding();

        CelestialAsset.Endpoint.Kind endpointKind();

        @Nullable
        TileEntityGantryTerminal getGantryTerminal();

        @Nullable
        IDistributedInventory loadingInventory();

        BlockPos vehiclePosition();

        /** Checks the Rocket journal before admitting a vehicle to this physical endpoint. */
        default boolean reserveArrival(Rocket.Registry registry, Rocket rocket) {
            UUID endpointId = machineIdentity().id();
            var ref = CelestialAssetStore.findEndpoint(endpointId);
            UUID reserved = rocket.reservedArrivalEndpointId();
            if (ref == null || (reserved != null && !reserved.equals(endpointId))
                || !isAvailableTo(rocket.teamId(), ref.asset().celestialObjectKey)) return false;
            long occupied = registry.all()
                .stream()
                .filter(
                    other -> !other.id()
                        .equals(rocket.id()))
                .filter(
                    other -> endpointId.equals(other.currentEndpointId())
                        || endpointId.equals(other.reservedArrivalEndpointId()))
                .count();
            return occupied < ref.endpoint()
                .serviceSlots();
        }

        /** Accepts an arrival already committed by the Rocket owner without starting service activities. */
        default boolean acceptArrival(Rocket.Registry registry, Rocket rocket) {
            return machineIdentity().id()
                .equals(rocket.currentEndpointId()) && isAvailableTo(rocket.teamId(), rocket.bodyKey())
                && reserveArrival(registry, rocket);
        }

        default boolean isAvailableTo(UUID team, CelestialObjectKey body) {
            var tile = (TileEntity) this;
            if (!isStructureValid() || (tile.getWorldObj() != null && tile.getWorldObj().isRemote)
                || !machineIdentity().belongsTo(team)) return false;
            var ref = CelestialAssetStore.findEndpoint(machineIdentity().id());
            return ref != null && ref.asset().assetId.equals(endpointBinding().assetId())
                && ref.endpoint()
                    .kind() == endpointKind()
                && ref.asset()
                    .status() == CelestialAsset.Status.OPERATIONAL
                && ref.asset().celestialObjectKey.equals(body)
                && team.equals(CelestialAssetStore.getTeamId(ref.asset().assetId));
        }

        default void refreshEndpoint() {
            var tile = (TileEntity) this;
            if (tile.getWorldObj() == null || tile.getWorldObj().isRemote || !isStructureValid()) return;
            UUID team = machineIdentity().currentTeamId();
            if (team == null) return;
            var body = GalaxiaCelestialAPI.findByDimension(tile.getWorldObj().provider.dimensionId)
                .map(CelestialObject::key)
                .orElse(null);
            if (body == null) return;
            var location = new CelestialAsset.PhysicalLocation(
                tile.getWorldObj().provider.dimensionId,
                new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord));
            var station = endpointBinding().assetId() == null ? containingStation(tile, team, body) : null;
            if (endpointBinding()
                .formed(CelestialAssetStore.SERVER, machineIdentity(), team, body, endpointKind(), location, station)) {
                tile.markDirty();
                tile.getWorldObj()
                    .markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
            }
        }

        default void endpointUnavailable() {
            var tile = (TileEntity) this;
            if (tile.getWorldObj() != null && !tile.getWorldObj().isRemote) {
                endpointBinding().unavailable(CelestialAssetStore.SERVER);
            }
        }

        default void endpointDestroyed() {
            var tile = (TileEntity) this;
            if (tile.getWorldObj() != null && !tile.getWorldObj().isRemote) {
                var holder = new Rocket.Holder(
                    endpointKind() == CelestialAsset.Endpoint.Kind.SILO ? Rocket.HolderKind.SILO_BUFFER
                        : Rocket.HolderKind.PAD_BUFFER,
                    machineIdentity().id());
                GantryAPI.dropModules(
                    Galaxia.proxy.serverRuntime()
                        .rockets(),
                    holder,
                    tile);
                endpointBinding().destroyed(CelestialAssetStore.SERVER, machineIdentity());
            }
        }

        default ModularPanel endpointAssociationUI(PosGuiData data) {
            var tile = (TileEntity) this;
            return ModularPanel.defaultPanel("galaxia:transport_association", 230, 112)
                .child(
                    IKey.lang("galaxia.transport.association.required")
                        .asWidget()
                        .pos(8, 8))
                .childIf(
                    endpointKind() != CelestialAsset.Endpoint.Kind.DOCK,
                    () -> new ButtonWidget<>().pos(8, 30)
                        .size(214, 20)
                        .overlay(IKey.lang("galaxia.transport.association.standalone"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                            if (mouse.mouseButton != 0 || tile.getWorldObj().isRemote
                                || !machineIdentity().canUse(data.getPlayer(), TeamAction.CONFIGURE_LOGISTICS)
                                || endpointBinding().assetId() != null) return;
                            endpointBinding().selectStandalone();
                            tile.markDirty();
                            refreshEndpoint();
                            tile.getWorldObj()
                                .markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
                        })))
                .child(
                    new ButtonWidget<>().pos(8, 54)
                        .size(214, 20)
                        .overlay(IKey.lang("galaxia.transport.association.station"))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                            if (mouse.mouseButton != 0 || tile.getWorldObj().isRemote
                                || !machineIdentity().canUse(data.getPlayer(), TeamAction.CONFIGURE_LOGISTICS)
                                || endpointBinding().assetId() != null) return;
                            endpointBinding().selectStation();
                            tile.markDirty();
                            refreshEndpoint();
                            tile.getWorldObj()
                                .markBlockForUpdate(tile.xCoord, tile.yCoord, tile.zCoord);
                        })))
                .child(
                    IKey.lang("galaxia.transport.association.pending")
                        .asWidget()
                        .pos(8, 86));
        }

        @Nullable
        private static CelestialAsset.ID containingStation(TileEntity tile, UUID team, CelestialObjectKey body) {
            CelestialAsset.ID found = null;
            for (var asset : CelestialAssetStore.SERVER.getStateInternal(team, body)) {
                if (!(asset instanceof Station station) || station.status() != CelestialAsset.Status.OPERATIONAL)
                    continue;
                BlockPos pos = station.getController();
                if (pos == null || !tile.getWorldObj()
                    .blockExists(pos.x(), pos.y(), pos.z())) continue;
                var candidate = tile.getWorldObj()
                    .getTileEntity(pos.x(), pos.y(), pos.z());
                if (!(candidate instanceof TileStation controller) || candidate.isInvalid()
                    || !controller.isStructureValid()
                    || controller.getBackingStation() != station) continue;
                boolean inside = controller.isInside(tile.xCoord, tile.yCoord, tile.zCoord);
                if (!inside && controller.getGraph() != null) {
                    for (var piece : controller.getGraph()
                        .iterateOver(TileStationBase.class)) {
                        if (piece.isStructureValid() && piece.isInside(tile.xCoord, tile.yCoord, tile.zCoord)) {
                            inside = true;
                            break;
                        }
                    }
                }
                if (inside) {
                    if (found != null) return null;
                    found = station.assetId;
                }
            }
            return found;
        }

        /** Resolves current physical availability without loading a dimension or chunk. */
        @Nullable
        static TransportEndpoint loaded(CelestialAssetStore.EndpointRef ref) {
            var endpoint = ref.endpoint();
            var physical = endpoint.physical();
            if (physical == null || ref.asset()
                .status() != CelestialAsset.Status.OPERATIONAL) return null;
            var world = DimensionManager.getWorld(physical.dimension());
            BlockPos pos = physical.controller();
            if (world == null || !world.blockExists(pos.x(), pos.y(), pos.z())) return null;
            var tile = world.getTileEntity(pos.x(), pos.y(), pos.z());
            UUID team = CelestialAssetStore.getTeamId(ref.asset().assetId);
            if (!(tile instanceof TransportEndpoint controller) || tile.isInvalid()
                || !controller.isStructureValid()
                || !controller.machineIdentity()
                    .id()
                    .equals(endpoint.id())
                || controller.endpointKind() != endpoint.kind()
                || !ref.asset().assetId.equals(
                    controller.endpointBinding()
                        .assetId())
                || team == null
                || !controller.machineIdentity()
                    .belongsTo(team))
                return null;
            return controller;
        }
    }

    /** Persists a physical controller's chosen asset lifetime, never a second vehicle or endpoint store. */
    final class EndpointBinding {

        @Nullable
        private CelestialAsset.ID assetId;
        private boolean standalone;

        @Nullable
        public CelestialAsset.ID assetId() {
            return assetId;
        }

        public void selectStandalone() {
            if (assetId != null) throw new IllegalStateException("A registered endpoint cannot change asset lifetime");
            standalone = true;
        }

        public void selectStation() {
            if (assetId != null) throw new IllegalStateException("A registered endpoint cannot change asset lifetime");
            standalone = false;
        }

        public boolean formed(CelestialAssetStore store, MachineIdentity machine, UUID team, CelestialObjectKey body,
            CelestialAsset.Endpoint.Kind kind, CelestialAsset.PhysicalLocation location,
            @Nullable CelestialAsset.ID verifiedStation) {
            if (!machine.belongsTo(team) || kind == CelestialAsset.Endpoint.Kind.DOCK && standalone) return false;
            if (assetId != null) {
                var asset = store.findAssetInternal(assetId);
                if (asset == null || !team.equals(store.getTeamIdInternal(assetId))) return false;
                var endpoint = asset.endpoint(machine.id());
                if (endpoint == null) return false;
                if (!asset.celestialObjectKey.equals(body) || endpoint.kind() != kind
                    || !location.equals(endpoint.physical())) {
                    throw new IllegalStateException("A saved physical endpoint identity cannot be rebound");
                }
                return asset.kind == CelestialAsset.Kind.TRANSPORT_SITE && store.enableAssetInternal(assetId);
            }
            CelestialAsset asset;
            if (standalone) {
                var id = new CelestialAsset.ID(machine.id());
                if (store.findAssetInternal(id) != null || store.findEndpointInternal(machine.id()) != null) {
                    throw new IllegalStateException("Physical endpoint identity is already registered");
                }
                asset = CelestialAsset
                    .create(id, body, CelestialAsset.Kind.TRANSPORT_SITE, CelestialAsset.Status.OPERATIONAL);
            } else {
                if (verifiedStation == null) return false;
                asset = store.findAssetInternal(verifiedStation);
                if (!(asset instanceof Station) || !team.equals(store.getTeamIdInternal(verifiedStation))
                    || !asset.celestialObjectKey.equals(body)
                    || asset.status() != CelestialAsset.Status.OPERATIONAL) return false;
                if (store.findEndpointInternal(machine.id()) != null)
                    throw new IllegalStateException("Physical endpoint identity is already registered");
            }
            asset.registerEndpoint(
                new CelestialAsset.Endpoint(
                    machine.id(),
                    kind,
                    machine.id()
                        .toString(),
                    location,
                    null,
                    1));
            if (standalone) store.registerAssetInternal(team, asset);
            assetId = asset.assetId;
            return true;
        }

        public void unavailable(CelestialAssetStore store) {
            if (assetId == null) return;
            var asset = store.findAssetInternal(assetId);
            if (asset != null && asset.kind == CelestialAsset.Kind.TRANSPORT_SITE) store.disableAssetInternal(assetId);
        }

        public boolean destroyed(CelestialAssetStore store, MachineIdentity machine) {
            if (assetId == null) return false;
            var asset = store.findAssetInternal(assetId);
            if (asset == null || asset.endpoint(machine.id()) == null) return false;
            if (asset.kind == CelestialAsset.Kind.TRANSPORT_SITE) {
                return store == CelestialAssetStore.SERVER ? AssetStateSync.SERVER.destroyAsset(assetId)
                    : store.destroyAssetInternal(assetId);
            }
            return asset.removeEndpoint(machine.id());
        }

        public void writeToNBT(NBTTagCompound tag) {
            var encoded = new NBTTagCompound();
            encoded.setBoolean("standalone", standalone);
            if (assetId != null) encoded.setString(
                "asset",
                assetId.id()
                    .toString());
            tag.setTag("endpointBinding", encoded);
        }

        public void readFromNBT(NBTTagCompound tag) {
            var in = new NbtReader(tag, "transportController").compound("endpointBinding");
            standalone = in.bool("standalone");
            assetId = in.tag()
                .hasKey("asset") ? new CelestialAsset.ID(UUID.fromString(in.string("asset"))) : null;
        }
    }

    /** Stable physical participant identity, separate from block coordinates and vehicle identity. */
    final class MachineIdentity {

        private UUID id = UUID.randomUUID();
        @Nullable
        private UUID teamId;

        @Nonnull
        public UUID id() {
            return id;
        }

        @Nullable
        public UUID teamId() {
            return teamId;
        }

        @Nullable
        public UUID currentTeamId() {
            if (teamId == null) return null;
            if (TeamManager.getTeamMap()
                .containsKey(teamId)) return teamId;
            return TeamManager.getTeamMap()
                .keySet()
                .stream()
                .filter(this::belongsTo)
                .findFirst()
                .orElse(null);
        }

        public boolean claim(@Nonnull UUID ownerTeam) {
            if (teamId != null) return teamId.equals(ownerTeam);
            teamId = ownerTeam;
            return true;
        }

        public boolean belongsTo(@Nonnull UUID ownerTeam) {
            return ownerTeam.equals(teamId) || (teamId != null && GTTeamsCompat.teamOwns(ownerTeam, teamId));
        }

        public boolean canUse(@Nonnull EntityPlayer player, @Nonnull TeamAction action) {
            return belongsTo(GTTeamsCompat.getTeam(player)) && GTTeamsCompat.hasPermission(player, action);
        }

        public void writeToNBT(@Nonnull NBTTagCompound tag) {
            var encoded = new NBTTagCompound();
            encoded.setString("id", id.toString());
            if (teamId != null) encoded.setString("team", teamId.toString());
            tag.setTag("machineIdentity", encoded);
        }

        public void readFromNBT(@Nonnull NBTTagCompound tag) {
            var in = new NbtReader(tag, "rocketMachine").compound("machineIdentity");
            id = UUID.fromString(in.string("id"));
            teamId = in.tag()
                .hasKey("team") ? UUID.fromString(in.string("team")) : null;
        }
    }
}
