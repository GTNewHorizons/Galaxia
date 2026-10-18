package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizon.gtnhlib.network.base.NetworkUtils;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.ServerTickTaskQueue;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.Operation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.WorkRequest;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

import cpw.mods.fml.common.network.ByteBufUtils;

/** Physical landing and orbital access. Vehicles, modules and service orders remain on the world Rocket. */
public class TileEntityTransportPlatform extends GalaxiaMultiblockBase<TileEntityTransportPlatform>
    implements IGuiHolder<PosGuiData>, IRocketControllerTE.TransportEndpoint {

    private CelestialAsset.Endpoint.Kind kind;

    public TileEntityTransportPlatform() {
        this(CelestialAsset.Endpoint.Kind.LANDING_PAD);
    }

    public TileEntityTransportPlatform(CelestialAsset.Endpoint.Kind kind) {
        if (kind != CelestialAsset.Endpoint.Kind.LANDING_PAD && kind != CelestialAsset.Endpoint.Kind.DOCK) {
            throw new IllegalArgumentException("Transport platforms are Landing Pads or Docks");
        }
        this.kind = kind;
    }

    private final MachineIdentity identity = new MachineIdentity();
    private final EndpointBinding binding = new EndpointBinding();
    @Nullable
    private TileEntityGantryTerminal gantryTerminal;
    @Nullable
    private TileEntityRocketLoadingPort loadingPort;

    @Override
    public MachineIdentity machineIdentity() {
        return identity;
    }

    @Override
    public EndpointBinding endpointBinding() {
        return binding;
    }

    @Override
    public CelestialAsset.Endpoint.Kind endpointKind() {
        return kind;
    }

    @Override
    @Nullable
    public TileEntityGantryTerminal getGantryTerminal() {
        return gantryTerminal;
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        gantryTerminal = terminal;
    }

    @Override
    @Nullable
    public IDistributedInventory loadingInventory() {
        return structureValid && loadingPort != null && loadingPort.isAvailable() ? loadingPort : null;
    }

    @Override
    public BlockPos vehiclePosition() {
        int[] offset = TileEntitySilo.getRotatedOffset(0, 1, 2, currentFacing);
        return new BlockPos(xCoord + offset[0], yCoord + offset[1], zCoord + offset[2]);
    }

    /** Admission runs after physical arrival. The Rocket freezes all qualifications and work identities. */
    public Rocket.ServiceResult beginService(Rocket.Registry registry, Rocket rocket, UUID occurrenceId,
        VehicleSchedule.Repair action) {
        if (kind != CelestialAsset.Endpoint.Kind.LANDING_PAD || !isAvailableTo(rocket.teamId(), rocket.bodyKey()))
            return Rocket.ServiceResult.CONFLICT;
        var previous = rocket.service();
        if (previous != null && previous.id()
            .equals(occurrenceId)) {
            return rocket.beginService(occurrenceId, action, identity.id(), previous.returnSiloId());
        }
        if (!acceptArrival(registry, rocket)) return Rocket.ServiceResult.CONFLICT;
        var installed = new Rocket.Holder(Rocket.HolderKind.INSTALLED, rocket.id());
        if (rocket.status() != Rocket.Status.PHYSICAL || rocket.modules()
            .stream()
            .anyMatch(
                module -> !module.holder()
                    .equals(installed) || module.pendingTransfer() != null || module.gantryTransport() != null)) {
            return Rocket.ServiceResult.CONFLICT;
        }
        if (rocket.modules()
            .stream()
            .noneMatch(module -> action.qualifies(module.durabilityPercent()))) return Rocket.ServiceResult.SKIPPED;
        UUID siloId = serviceSiloId();
        if (siloId == null || gantryTerminal == null) return Rocket.ServiceResult.CONFLICT;
        var target = GantryAPI.findControllerTerminal(gantryTerminal, siloId);
        var silo = target == null ? null : target.getSilo();
        if (silo == null || !silo.isAvailableTo(rocket.teamId(), rocket.bodyKey())
            || (silo.vehicleId() != null && !silo.vehicleId()
                .equals(rocket.id())))
            return Rocket.ServiceResult.CONFLICT;
        if (registry.all()
            .stream()
            .anyMatch(
                other -> other != rocket && other.service() != null
                    && !other.service()
                        .reassembled()
                    && other.service()
                        .padId()
                        .equals(identity.id())))
            return Rocket.ServiceResult.CONFLICT;
        var result = rocket.beginService(occurrenceId, action, identity.id(), siloId);
        if (result == Rocket.ServiceResult.ACCEPTED && !silo.adoptServiceReturn(rocket)) {
            throw new IllegalStateException("The admitted service lost its receiving Silo");
        }
        return result;
    }

    /** The same native server step is used by Gantry integration tests. No service state lives on this tile. */
    public void tickServices(Rocket.Registry registry) {
        if (gantryTerminal == null) return;
        for (var rocket : registry.all()) {
            var service = rocket.service();
            if (rocket.status() == Rocket.Status.INCOMPLETE || service == null
                || service.reassembled()
                || !service.padId()
                    .equals(identity.id())
                || !isAvailableTo(rocket.teamId(), rocket.bodyKey())) continue;
            for (var module : rocket.modules()) {
                if (service.repairs()
                    .containsKey(module.id())
                    && !rocket.service()
                        .repaired()
                        .contains(module.id())
                    && !tickRepair(
                        registry,
                        rocket,
                        module,
                        service.repairs()
                            .get(module.id())))
                    continue;
                var target = GantryAPI.findControllerTerminal(gantryTerminal, service.returnSiloId());
                var silo = target == null ? null : target.getSilo();
                if (silo == null || !silo.adoptServiceReturn(rocket)) continue;
                TileEntityGantry source = null;
                if (module.holder()
                    .equals(new Rocket.Holder(Rocket.HolderKind.PAD_BUFFER, identity.id()))) source = gantryTerminal;
                else if (module.holder()
                    .kind() == Rocket.HolderKind.ASSEMBLER) {
                        var work = rocket.workFor(module.id());
                        if (work != null) source = assignedAssembler(work);
                    } else if (module.holder()
                        .kind() == Rocket.HolderKind.GANTRY && module.gantryTransport() == null) {
                            source = GantryAPI.findNode(
                                gantryTerminal,
                                module.holder()
                                    .id());
                        }
                if (source != null) GantryAPI.dispatch(
                    registry,
                    rocket,
                    module.id(),
                    service.returns()
                        .get(module.id()),
                    source,
                    target,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, service.returnSiloId()));
            }
        }
    }

    private boolean tickRepair(Rocket.Registry registry, Rocket rocket, Rocket.Module module, UUID workId) {
        var claim = rocket.workFor(module.id());
        if (claim == null) {
            var bill = module.part()
                .def()
                .repairBill();
            if (bill == null || !module.holder()
                .equals(new Rocket.Holder(Rocket.HolderKind.PAD_BUFFER, identity.id()))) return false;
            var request = WorkRequest.fromBill(
                workId,
                rocket.id(),
                module.id(),
                module.part()
                    .def()
                    .id(),
                Operation.REPAIR,
                bill);
            var accepted = GantryAPI.requestWork(gantryTerminal, rocket.teamId(), request);
            if (accepted == null) return false;
            var terminal = GantryAPI.findControllerTerminal(gantryTerminal, accepted.controllerId());
            if (terminal == null || !terminal.terminalId()
                .equals(accepted.terminalId())) {
                throw new IllegalStateException(
                    "Accepted repair Assembler left its connected component during admission");
            }
            var receiver = new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, accepted.controllerId());
            var target = new Rocket.TerminalTarget(
                accepted.terminalId(),
                accepted.controllerId(),
                terminal.dimension(),
                terminal.xCoord,
                terminal.yCoord,
                terminal.zCoord,
                receiver);
            claim = new Rocket.ModuleWork(workId, module.id(), target, bill);
            if (!rocket.reserveWork(claim)) throw new IllegalStateException("Conflicting service repair claim");
        }
        var terminal = assignedAssembler(claim);
        if (terminal == null) return false;
        var assembler = terminal.getAssembler();
        var request = WorkRequest.fromBill(
            claim.workId(),
            rocket.id(),
            module.id(),
            module.part()
                .def()
                .id(),
            Operation.REPAIR,
            claim.bill());
        if (module.holder()
            .equals(new Rocket.Holder(Rocket.HolderKind.PAD_BUFFER, identity.id()))) {
            if (assembler.tryAccept(rocket.teamId(), request)
                .accepted())
                GantryAPI.dispatch(
                    registry,
                    rocket,
                    module.id(),
                    workId,
                    gantryTerminal,
                    terminal,
                    claim.machine()
                        .receiver());
            return false;
        }
        return request.equals(assembler.completedOutput())
            && rocket.completeRepair(module.id(), workId, assembler.controllerId());
    }

    @Nullable
    private TileEntityGantryTerminal assignedAssembler(Rocket.ModuleWork work) {
        var terminal = GantryAPI.findControllerTerminal(
            gantryTerminal,
            work.machine()
                .controllerId());
        if (terminal == null || terminal.getAssembler() == null
            || !terminal.terminalId()
                .equals(
                    work.machine()
                        .terminalId())
            || terminal.dimension() != work.machine()
                .dimension()
            || terminal.xCoord != work.machine()
                .x()
            || terminal.yCoord != work.machine()
                .y()
            || terminal.zCoord != work.machine()
                .z())
            return null;
        return terminal;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj != null && !worldObj.isRemote) tickServices(
            Galaxia.proxy.serverRuntime()
                .rockets());
    }

    @Nullable
    public UUID serviceSiloId() {
        var endpoint = descriptor();
        return endpoint == null ? null : endpoint.serviceSiloId();
    }

    /** Player selection remains exact and must be physically connected when it is changed. */
    public boolean selectServiceSilo(UUID siloId) {
        if (worldObj == null || worldObj.isRemote
            || !structureValid
            || gantryTerminal == null
            || binding.assetId() == null) return false;
        var source = CelestialAssetStore.findEndpoint(identity.id());
        var target = CelestialAssetStore.findEndpoint(siloId);
        if (source == null || target == null
            || target.endpoint()
                .kind() != CelestialAsset.Endpoint.Kind.SILO
            || !source.asset().celestialObjectKey.equals(target.asset().celestialObjectKey)) return false;
        UUID team = CelestialAssetStore.getTeamId(source.asset().assetId);
        if (team == null || !team.equals(CelestialAssetStore.getTeamId(target.asset().assetId))
            || !identity.belongsTo(team)) return false;
        var terminal = GantryAPI.findControllerTerminal(gantryTerminal, siloId);
        var silo = terminal == null ? null : terminal.getSilo();
        if (silo == null || !silo.isStructureValid()
            || !silo.machineIdentity()
                .belongsTo(team))
            return false;
        source.asset()
            .setEndpointServiceSilo(identity.id(), siloId);
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    /** Explicit player command. The Rocket owns the service transition and every concrete holder. */
    public boolean rebindServiceReturn(Rocket.Registry registry, UUID vehicleId, UUID expectedServiceId,
        UUID newSiloId) {
        Rocket rocket = registry.find(vehicleId);
        var service = rocket == null ? null : rocket.service();
        if (kind != CelestialAsset.Endpoint.Kind.LANDING_PAD || worldObj == null
            || worldObj.isRemote
            || gantryTerminal == null
            || service == null
            || service.reassembled()
            || !service.id()
                .equals(expectedServiceId)
            || !service.padId()
                .equals(identity.id())
            || !isAvailableTo(rocket.teamId(), rocket.bodyKey())
            || CelestialAssetStore.findEndpoint(service.returnSiloId()) != null) return false;
        var endpoint = CelestialAssetStore.findEndpoint(newSiloId);
        if (endpoint == null || endpoint.endpoint()
            .kind() != CelestialAsset.Endpoint.Kind.SILO
            || !endpoint.asset().celestialObjectKey.equals(rocket.bodyKey())
            || !rocket.teamId()
                .equals(CelestialAssetStore.getTeamId(endpoint.asset().assetId)))
            return false;
        var terminal = GantryAPI.findControllerTerminal(gantryTerminal, newSiloId);
        var silo = terminal == null ? null : terminal.getSilo();
        if (silo == null || !silo.isAvailableTo(rocket.teamId(), rocket.bodyKey())
            || silo.vehicleId() != null && !silo.vehicleId()
                .equals(vehicleId)
            || registry.all()
                .stream()
                .anyMatch(
                    other -> !other.id()
                        .equals(vehicleId) && other.service() != null
                        && !other.service()
                            .reassembled()
                        && other.service()
                            .returnSiloId()
                            .equals(newSiloId)))
            return false;
        if (!rocket.rebindServiceReturn(expectedServiceId, identity.id(), newSiloId)) return false;
        if (rocket.status() != Rocket.Status.INCOMPLETE && !silo.adoptServiceReturn(rocket)) {
            throw new IllegalStateException("The explicitly rebound service lost its receiving Silo");
        }
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    @Nullable
    private CelestialAsset.Endpoint descriptor() {
        if (binding.assetId() == null) return null;
        var store = worldObj != null && worldObj.isRemote ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER;
        var asset = store.findAssetInternal(binding.assetId());
        return asset == null ? null : asset.endpoint(identity.id());
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (binding.assetId() == null) return endpointAssociationUI(data);
        var formed = new BooleanSyncValue(this::isStructureValid, _ -> {});
        syncManager.syncValue("platformFormed", formed);
        var panel = ModularPanel
            .defaultPanel("galaxia:transport_platform", 230, kind == CelestialAsset.Endpoint.Kind.DOCK ? 54 : 144)
            .child(
                IKey.lang(
                    kind == CelestialAsset.Endpoint.Kind.DOCK ? "tile.dock_controller.name"
                        : "tile.landing_pad_controller.name")
                    .asWidget()
                    .pos(8, 8))
            .child(
                IKey.dynamic(
                    () -> IKey
                        .lang(
                            formed.getBoolValue() ? "galaxia.transport.platform.formed"
                                : "galaxia.transport.platform.not_formed")
                        .get())
                    .asWidget()
                    .pos(8, 26));
        if (kind == CelestialAsset.Endpoint.Kind.DOCK) return panel;
        var service = new ServiceControls();
        syncManager.syncValue("platformService", service);
        return panel.child(
            IKey.lang("galaxia.transport.pad.service_silo", () -> new Object[] { service.siloName() })
                .asWidget()
                .pos(8, 44))
            .child(
                new ButtonWidget<>().name("platform.service.select_silo")
                    .pos(8, 68)
                    .size(214, 20)
                    .overlay(IKey.lang("galaxia.transport.pad.select_silo"))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                        if (mouse.mouseButton != 0 || worldObj.isRemote
                            || gantryTerminal == null
                            || !identity.canUse(data.getPlayer(), TeamAction.CONFIGURE_LOGISTICS)) return;
                        var choices = GantryAPI.findEndpointTerminals(gantryTerminal)
                            .stream()
                            .map(TileEntityGantryTerminal::getSilo)
                            .filter(Objects::nonNull)
                            .filter(TileEntitySilo::isStructureValid)
                            .filter(
                                silo -> silo.machineIdentity()
                                    .belongsTo(GTTeamsCompat.getTeam(data.getPlayer())))
                            .map(
                                silo -> silo.machineIdentity()
                                    .id())
                            .sorted(Comparator.naturalOrder())
                            .toList();
                        if (choices.isEmpty()) return;
                        int current = choices.indexOf(serviceSiloId());
                        selectServiceSilo(choices.get((current + 1) % choices.size()));
                    })))
            .child(
                new ButtonWidget<>().name("platform.service.rebind")
                    .pos(8, 92)
                    .size(214, 20)
                    .overlay(IKey.lang("galaxia.transport.platform.rebind_service"))
                    .setEnabledIf(
                        _ -> service.vehicleId != null && service.serviceId != null && service.selectedSilo != null)
                    .syncHandler(service))
            .child(
                IKey.dynamic(
                    () -> service.resultKey == null ? ""
                        : IKey.lang(service.resultKey)
                            .get())
                    .asWidget()
                    .pos(8, 118));
    }

    /** Client-visible selection and explicit service command, with no service state duplicated on the tile. */
    private final class ServiceControls extends InteractionSyncHandler {

        private static final int STATE = 100;
        private static final int REBIND = 101;
        @Nullable
        private UUID vehicleId;
        @Nullable
        private UUID serviceId;
        @Nullable
        private UUID selectedSilo;
        private String selectedName = "";
        @Nullable
        private String resultKey;
        @Nullable
        private NBTTagCompound lastState;

        private String siloName() {
            return selectedSilo == null ? IKey.lang("galaxia.transport.pad.no_silo")
                .get() : selectedName;
        }

        @Override
        public boolean onMousePressed(int button) {
            if (button != 0 || vehicleId == null || serviceId == null || selectedSilo == null) return false;
            syncToServer(REBIND, out -> {
                NetworkUtils.writeUUID(out, vehicleId);
                NetworkUtils.writeUUID(out, serviceId);
                NetworkUtils.writeUUID(out, selectedSilo);
            });
            return true;
        }

        @Override
        public void readOnServer(int id, PacketBuffer in) {
            if (id != REBIND || in.readableBytes() != 48
                || !(getSyncManager().getPlayer() instanceof EntityPlayerMP player)) return;
            UUID requestedVehicle = NetworkUtils.readUUID(in);
            UUID requestedService = NetworkUtils.readUUID(in);
            UUID requestedSilo = NetworkUtils.readUUID(in);
            ServerTickTaskQueue.schedule(() -> {
                if (!isValid() || worldObj == null
                    || worldObj.isRemote
                    || worldObj.getTileEntity(xCoord, yCoord, zCoord) != TileEntityTransportPlatform.this
                    || !identity.canUse(player, TeamAction.CONFIGURE_LOGISTICS)) return;
                boolean changed = rebindServiceReturn(
                    Galaxia.proxy.serverRuntime()
                        .rockets(),
                    requestedVehicle,
                    requestedService,
                    requestedSilo);
                resultKey = changed ? "galaxia.transport.platform.rebind_applied"
                    : "galaxia.transport.platform.rebind_rejected";
                detectAndSendChanges(true);
            });
        }

        @Override
        public void readOnClient(int id, PacketBuffer in) throws IOException {
            if (id != STATE) return;
            var state = new NbtReader(ByteBufUtils.readTag(in), "platformControls");
            vehicleId = state.tag()
                .hasKey("vehicle") ? UUID.fromString(state.string("vehicle")) : null;
            serviceId = state.tag()
                .hasKey("service") ? UUID.fromString(state.string("service")) : null;
            selectedSilo = state.tag()
                .hasKey("silo") ? UUID.fromString(state.string("silo")) : null;
            selectedName = state.string("siloName");
            resultKey = state.tag()
                .hasKey("result") ? state.string("result") : null;
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            if (!(getSyncManager().getPlayer() instanceof EntityPlayerMP player)
                || !identity.canUse(player, TeamAction.CONFIGURE_LOGISTICS)) return;
            var state = new NBTTagCompound();
            for (Rocket rocket : Galaxia.proxy.serverRuntime()
                .rockets()
                .all()) {
                var active = rocket.service();
                if (active == null || active.reassembled()
                    || !active.padId()
                        .equals(identity.id())
                    || !rocket.teamId()
                        .equals(GTTeamsCompat.getTeam(player)))
                    continue;
                state.setString(
                    "vehicle",
                    rocket.id()
                        .toString());
                state.setString(
                    "service",
                    active.id()
                        .toString());
                break;
            }
            UUID selected = serviceSiloId();
            var endpoint = selected == null ? null : CelestialAssetStore.findEndpoint(selected);
            if (selected != null) state.setString("silo", selected.toString());
            state.setString(
                "siloName",
                selected == null ? ""
                    : endpoint == null ? selected.toString()
                        : endpoint.endpoint()
                            .routingName());
            if (resultKey != null) state.setString("result", resultKey);
            if (init || !state.equals(lastState)) {
                syncToClient(STATE, out -> ByteBufUtils.writeTag(out, state));
                lastState = state;
            }
        }
    }

    @Override
    public IStructureDefinition<TileEntityTransportPlatform> getStructureDefinition() {
        return Structure.DEFINITION;
    }

    @Override
    public void setPlacedFacing(ForgeDirection direction) {
        super.setPlacedFacing(direction);
        setFacing(direction);
    }

    @Override
    protected boolean checkStructure() {
        gantryTerminal = null;
        loadingPort = null;
        return super.checkStructure();
    }

    @Override
    protected int getControllerOffsetX() {
        return 2;
    }

    @Override
    protected int getControllerOffsetY() {
        return 0;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    public Block getControllerBlock() {
        return (kind == CelestialAsset.Endpoint.Kind.DOCK ? GalaxiaBlocksEnum.DOCK_CONTROLLER
            : GalaxiaBlocksEnum.LANDING_PAD_CONTROLLER).get();
    }

    @Override
    protected void onStructureFormed() {
        refreshEndpoint();
    }

    @Override
    protected void onStructureChecked() {
        refreshEndpoint();
    }

    @Override
    protected void onStructureDisformed() {
        endpointUnavailable();
    }

    @Override
    public void invalidate() {
        endpointUnavailable();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        endpointUnavailable();
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("platformKind", kind.name());
        identity.writeToNBT(tag);
        binding.writeToNBT(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        var restoredKind = new NbtReader(tag, "transportPlatform")
            .enumValue(CelestialAsset.Endpoint.Kind.class, "platformKind");
        if (restoredKind != CelestialAsset.Endpoint.Kind.LANDING_PAD
            && restoredKind != CelestialAsset.Endpoint.Kind.DOCK) {
            throw new IllegalStateException("Invalid transport platform kind");
        }
        super.readFromNBT(tag);
        kind = restoredKind;
        identity.readFromNBT(tag);
        binding.readFromNBT(tag);
    }

    private static final class Structure {

        private static final IStructureDefinition<TileEntityTransportPlatform> DEFINITION = StructureDefinition
            .<TileEntityTransportPlatform>builder()
            .addShape("main", new String[][] { { "EE~EE" }, { "EAAAE" }, { "EAAAE" }, { "EAAAE" }, { "PETEE" } })
            .addElement('A', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_CASING.get(), 0))
            .addElement('E', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0))
            .addElement('P', StructureUtility.ofChain(StructureUtility.ofTileAdder((pad, tile) -> {
                if (!(tile instanceof TileEntityRocketLoadingPort port)) return false;
                pad.loadingPort = port;
                return true;
            }, GalaxiaBlocksEnum.ROCKET_LOADING_PORT.get(), 0),
                StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0)))
            .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((pad, tile) -> {
                if (!(tile instanceof TileEntityGantryTerminal terminal)) return false;
                pad.setGantryTerminal(terminal);
                terminal.connectPlatform(pad);
                return true;
            }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
                StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0)))
            .build();
    }
}
