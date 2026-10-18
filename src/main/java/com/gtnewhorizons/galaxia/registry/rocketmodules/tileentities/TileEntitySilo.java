package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.Operation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler.WorkRequest;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

/** Physical assembly coordinator. The world Rocket owns every produced module and paid work claim. */
public class TileEntitySilo extends GalaxiaMultiblockBase<TileEntitySilo>
    implements IGuiHolder<PosGuiData>, IRocketControllerTE.TransportEndpoint {

    private final MachineIdentity identity = new MachineIdentity();
    private final EndpointBinding endpointBinding = new EndpointBinding();
    @Nullable
    private TileEntityRocketLoadingPort loadingPort;
    @Nullable
    private UUID vehicleId;
    private RocketBlueprint designBlueprint = new RocketBlueprint();
    private RocketBlueprint clientBuiltView = new RocketBlueprint();
    private RocketBuildStatus clientBuildStatus = RocketBuildStatus.IDLE;
    private EntityRocket entityRocket;
    private TileEntityGantryTerminal gantryTerminal;
    private String pendingSchematicName = "";
    public boolean shouldRender = true;

    public static final int SILO_DEFAULT_X_OFFSET = 0;
    public static final int SILO_DEFAULT_Y_OFFSET = 1;
    public static final int SILO_DEFAULT_Z_OFFSET = 3;
    private static final String STRUCTURE_PIECE_MAIN = "main";

    @Override
    public MachineIdentity machineIdentity() {
        return identity;
    }

    @Override
    public EndpointBinding endpointBinding() {
        return endpointBinding;
    }

    @Override
    @Nullable
    public IDistributedInventory loadingInventory() {
        return structureValid && loadingPort != null && loadingPort.isAvailable() ? loadingPort : null;
    }

    @Override
    public CelestialAsset.Endpoint.Kind endpointKind() {
        return CelestialAsset.Endpoint.Kind.SILO;
    }

    @Nullable
    public UUID vehicleId() {
        return vehicleId;
    }

    @Nullable
    public Rocket rocket() {
        if (vehicleId == null || worldObj == null || worldObj.isRemote) return null;
        return Galaxia.proxy.serverRuntime()
            .rockets()
            .find(vehicleId);
    }

    public RocketBlueprint getDesignBlueprint() {
        return designBlueprint;
    }

    public RocketBuildStatus getBuildStatus() {
        if (worldObj != null && worldObj.isRemote) return clientBuildStatus;
        Rocket rocket = rocket();
        if (vehicleId != null)
            return rocket != null && rocket.status() == Rocket.Status.PHYSICAL ? RocketBuildStatus.READY
                : RocketBuildStatus.ASSEMBLING;
        return designBlueprint.isEmpty() ? RocketBuildStatus.IDLE : RocketBuildStatus.DESIGNED;
    }

    public RocketBlueprint getBuiltBlueprint() {
        if (worldObj != null && worldObj.isRemote) return clientBuiltView;
        Rocket rocket = rocket();
        return rocket == null ? new RocketBlueprint() : rocket.builtBlueprint();
    }

    public void setDesignBlueprint(RocketBlueprint blueprint) {
        if (!getBuildStatus().canEdit()) return;
        designBlueprint = blueprint.copy();
        sync();
    }

    public void updateBuildStatusAfterEdit() {
        sync();
    }

    public String getPendingSchematicName() {
        return pendingSchematicName;
    }

    public void setPendingSchematicName(String name) {
        pendingSchematicName = name;
    }

    public TileEntityGantryTerminal getGantryTerminal() {
        return gantryTerminal;
    }

    public void setGantryTerminal(TileEntityGantryTerminal terminal) {
        gantryTerminal = terminal;
    }

    public void orderModules() {
        if (worldObj == null || worldObj.isRemote
            || !structureValid
            || !getBuildStatus().canOrder()
            || identity.teamId() == null
            || !designBlueprint.analyze()
                .viable())
            return;
        if (designBlueprint.getParts()
            .stream()
            .anyMatch(
                part -> part.def()
                    .manufactureBill() == null))
            return;
        var endpoint = CelestialAssetStore.findEndpoint(identity.id());
        if (endpoint == null) return;
        UUID team = CelestialAssetStore.getTeamId(endpoint.asset().assetId);
        if (team == null || !identity.belongsTo(team)) return;
        var rocket = Rocket.construct(team, endpoint, designBlueprint);
        Galaxia.proxy.serverRuntime()
            .rockets()
            .register(rocket);
        vehicleId = rocket.id();
        tickConstruction(rocket);
        sync();
    }

    private void tickConstruction(Rocket rocket) {
        if (gantryTerminal == null || !structureValid) return;
        for (var slot : rocket.buildSlots()) {
            Rocket.Module module = rocket.modules()
                .stream()
                .filter(
                    candidate -> candidate.id()
                        .equals(slot.moduleId()))
                .findFirst()
                .orElse(null);
            Rocket.ModuleWork claim = rocket.workFor(slot.moduleId());
            if (module == null && claim == null) {
                var bill = slot.part()
                    .def()
                    .manufactureBill();
                if (bill == null) continue;
                var request = WorkRequest.fromBill(
                    slot.requestId(),
                    rocket.id(),
                    slot.moduleId(),
                    slot.part()
                        .def()
                        .id(),
                    Operation.MANUFACTURE,
                    bill);
                var accepted = GantryAPI.requestWork(gantryTerminal, rocket.teamId(), request);
                if (accepted == null) continue;
                var terminal = GantryAPI.findEndpointTerminals(gantryTerminal)
                    .stream()
                    .filter(
                        candidate -> candidate.terminalId()
                            .equals(accepted.terminalId()))
                    .findFirst()
                    .orElse(null);
                if (terminal == null)
                    throw new IllegalStateException("Accepted Assembler left its connected component during admission");
                var target = new Rocket.TerminalTarget(
                    terminal.terminalId(),
                    accepted.controllerId(),
                    worldObj.provider.dimensionId,
                    terminal.xCoord,
                    terminal.yCoord,
                    terminal.zCoord,
                    new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, accepted.controllerId()));
                claim = new Rocket.ModuleWork(slot.requestId(), slot.moduleId(), target, bill);
                if (!rocket.reserveWork(claim)) throw new IllegalStateException("Conflicting vehicle work claim");
            }
            if (claim == null) continue;
            var target = claim.machine();
            if (target.dimension() != worldObj.provider.dimensionId
                || !worldObj.blockExists(target.x(), target.y(), target.z())) continue;
            var tile = worldObj.getTileEntity(target.x(), target.y(), target.z());
            if (!(tile instanceof TileEntityGantryTerminal terminal) || !terminal.terminalId()
                .equals(target.terminalId())) continue;
            var assembler = terminal.getAssembler();
            if (assembler == null || !assembler.controllerId()
                .equals(target.controllerId())) continue;
            if (module == null) {
                var request = WorkRequest.fromBill(
                    claim.workId(),
                    rocket.id(),
                    slot.moduleId(),
                    slot.part()
                        .def()
                        .id(),
                    Operation.MANUFACTURE,
                    claim.bill());
                if (!assembler.tryAccept(rocket.teamId(), request)
                    .accepted()) continue;
                var output = assembler.completedOutput();
                if (output == null || !output.equals(request)) continue;
                module = Rocket.Module.produced(
                    rocket.id(),
                    slot,
                    100,
                    new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, assembler.controllerId()));
                if (!rocket.acceptProduced(module))
                    throw new IllegalStateException("Assembler produced an unreserved module");
            }
            if (module.holder()
                .equals(new Rocket.Holder(Rocket.HolderKind.ASSEMBLER, assembler.controllerId()))) {
                GantryAPI.dispatch(
                    Galaxia.proxy.serverRuntime()
                        .rockets(),
                    rocket,
                    module.id(),
                    claim.workId(),
                    terminal,
                    gantryTerminal,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, identity.id()));
            }
        }
        var place = assemblyPlace();
        if (place != null && rocket.assemble(identity.id(), place)) sync();
    }

    /** Checks the existing vehicle's reserved receiving position without advancing its lifecycle. */
    public boolean acceptGantryDelivery(Rocket rocket, Rocket.Module module, UUID workId) {
        if (rocket.status() == Rocket.Status.INCOMPLETE) return false;
        var service = rocket.service();
        if (service != null && !service.reassembled()) {
            return rocket.id()
                .equals(vehicleId)
                && service.returnSiloId()
                    .equals(identity.id())
                && isAvailableTo(rocket.teamId(), rocket.bodyKey())
                && workId.equals(
                    service.returns()
                        .get(module.id()));
        }
        var claim = rocket.workFor(module.id());
        return rocket.id()
            .equals(vehicleId)
            && rocket.assemblySiloId()
                .equals(identity.id())
            && identity.belongsTo(rocket.teamId())
            && claim != null
            && claim.workId()
                .equals(workId);
    }

    @Override
    public boolean reserveArrival(Rocket.Registry registry, Rocket rocket) {
        return (vehicleId == null || vehicleId.equals(rocket.id()))
            && TransportEndpoint.super.reserveArrival(registry, rocket);
    }

    @Override
    public boolean acceptArrival(Rocket.Registry registry, Rocket rocket) {
        if (!TransportEndpoint.super.acceptArrival(registry, rocket)) return false;
        if (vehicleId == null) {
            vehicleId = rocket.id();
            sync();
        }
        return true;
    }

    /** One physical Silo reserves its receiving slot for the exact accepted service order. */
    public boolean adoptServiceReturn(Rocket rocket) {
        var service = rocket.service();
        if (rocket.status() == Rocket.Status.INCOMPLETE || service == null
            || service.reassembled()
            || !service.returnSiloId()
                .equals(identity.id())
            || !isAvailableTo(rocket.teamId(), rocket.bodyKey())
            || (vehicleId != null && !vehicleId.equals(rocket.id()))) return false;
        if (vehicleId == null) {
            vehicleId = rocket.id();
            sync();
        }
        return true;
    }

    public void tickServiceReturn(Rocket rocket) {
        var place = assemblyPlace();
        if (place != null && adoptServiceReturn(rocket)
            && (rocket.resumeConstruction(identity.id(), place) || rocket.assemble(identity.id(), place))) sync();
    }

    @Nullable
    private VehicleSchedule.Place assemblyPlace() {
        var endpoint = CelestialAssetStore.findEndpoint(identity.id());
        if (endpoint == null) return null;
        return VehicleSchedule.Place.at(endpoint);
    }

    /** Frees only this coordinator's vehicle slot after its owner has committed departure. */
    public boolean releaseOccupancy(Rocket rocket) {
        if ((worldObj != null && worldObj.isRemote) || !identity.belongsTo(rocket.teamId())
            || identity.id()
                .equals(rocket.currentEndpointId())
            || (rocket.service() != null && !rocket.service()
                .reassembled()))
            return false;
        if (vehicleId == null) return true;
        if (!vehicleId.equals(rocket.id())) return false;
        vehicleId = null;
        sync();
        return true;
    }

    private static final IStructureDefinition<TileEntitySilo> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntitySilo>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            // spotless:off
                new String[][]{
                    {"         ","         ","         ","         ","         ","         ","         ","         ","  DDDDD  ","   C C   ","  PE~EE  ","  EEEEE  "},
                    {"         ","         ","         ","         ","         ","         ","         ","         "," D     D ","         "," EAAAAAE "," EAAAAAE "},
                    {" C     C "," C     C "," C     C "," C     C "," C     C "," C     C "," C     C "," C     C "," D     D "," C     C ","EEAAAAAEE","EEAAAAAEE"},
                    {"CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","CB     BC","TB     BT","EEAAAAAEE","EEAAAAAEE"},
                    {" D     D "," C     C "," C     C "," C     C "," D     D "," C     C "," C     C "," C     C "," D     D "," C     C ","EEAAAAAEE","EEAAAAAEE"},
                    {" D     D ","         ","         ","         "," D     D ","         ","         ","         "," D     D ","         "," EAAAAAE "," EAAAAAE "},
                    {"  DDBDD  ","   CBC   ","   CBC   ","   CBC   ","  DDBDD  ","   CBC   ","   CBC   ","   CBC   ","  DDBDD  ","   DBD   ","  EEEEE  ","  EEEEE  "},
                    {"    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    C    ","    T    ","   EEE   ","   EEE   "}
                })
        // spotless:on
        .addElement('A', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_CASING.get(), 0))
        .addElement('B', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_ASSEMBLING_CASING.get(), 0))
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_FRAMEBOX.get(), 0))
        .addElement('D', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_REINFORCEMENT.get(), 0))
        .addElement('E', StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0))
        .addElement('P', StructureUtility.ofChain(StructureUtility.ofTileAdder((silo, tile) -> {
            if (!(tile instanceof TileEntityRocketLoadingPort port)) return false;
            silo.loadingPort = port;
            return true;
        }, GalaxiaBlocksEnum.ROCKET_LOADING_PORT.get(), 0),
            StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0)))
        .addElement('T', StructureUtility.ofChain(StructureUtility.ofTileAdder((silo, te) -> {
            if (te instanceof TileEntityGantryTerminal terminal) {
                silo.setGantryTerminal(terminal);
                terminal.connectSilo(silo);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0),
            StructureUtility.ofBlock(GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(), 0)))
        .build();

    @Override
    public IStructureDefinition<TileEntitySilo> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected boolean checkStructure() {
        gantryTerminal = null;
        loadingPort = null;
        return super.checkStructure();
    }

    @Override
    protected int getControllerOffsetX() {
        return 4;
    }

    @Override
    protected int getControllerOffsetY() {
        return 10;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.SILO_CONTROLLER.get();
    }

    @Override
    protected void onStructureFormed() {
        shouldRender = true;
        refreshEndpoint();
    }

    @Override
    protected void onStructureChecked() {
        refreshEndpoint();
    }

    @Override
    protected void onStructureDisformed() {
        shouldRender = false;
        endpointUnavailable();
    }

    public static int[] getRotatedOffset(int x, int y, int z, ExtendedFacing facing) {
        return switch (facing.getDirection()) {
            case SOUTH -> new int[] { x, y, -z };
            case NORTH -> new int[] { -x, y, z };
            case EAST -> new int[] { -z, y, -x };
            case WEST -> new int[] { z, y, x };
            default -> new int[] { x, y, -z };
        };
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (endpointBinding.assetId() == null) return endpointAssociationUI(data);
        return RocketEditorUI.build(data, syncManager, settings);
    }

    public void enterRocket(PosGuiData data) {
        if (worldObj.isRemote || getBuildStatus() != RocketBuildStatus.READY) return;
        Rocket vehicle = rocket();
        if (vehicle == null || vehicle.isRunning()) return;
        EntityRocket entity = getOrCreateEntityRocket();
        if (entity == null) return;
        entity.initializeSeats();
        entity.interactFirst(data.getPlayer());
    }

    @Nullable
    private EntityRocket getOrCreateEntityRocket() {
        Rocket vehicle = rocket();
        if (vehicle == null || vehicle.status() != Rocket.Status.PHYSICAL) return null;
        if (entityRocket != null && !entityRocket.isDead && entityRocket.isCurrentProjection(vehicle))
            return entityRocket;
        for (Entity candidate : worldObj.loadedEntityList) {
            if (candidate instanceof EntityRocket existing && !existing.isDead
                && existing.isCurrentProjection(vehicle)) {
                entityRocket = existing;
                return existing;
            }
        }
        entityRocket = new EntityRocket(worldObj);
        var position = vehiclePosition();
        entityRocket.setPosition(position.x() + 0.5, position.y(), position.z() + 0.5);
        if (!entityRocket.bind(vehicle)) {
            entityRocket = null;
            return null;
        }
        if (!worldObj.spawnEntityInWorld(entityRocket)) {
            vehicle.retireProjection(entityRocket.getUniqueID(), entityRocket.projectionGeneration());
            entityRocket.setDead();
            entityRocket = null;
        }
        return entityRocket;
    }

    @Override
    public BlockPos vehiclePosition() {
        int[] offset = getRotatedOffset(
            SILO_DEFAULT_X_OFFSET,
            SILO_DEFAULT_Y_OFFSET,
            SILO_DEFAULT_Z_OFFSET,
            currentFacing);
        return new BlockPos(xCoord + offset[0], yCoord + offset[1], zCoord + offset[2]);
    }

    public void captureSchematic(EntityPlayer player) {
        if (worldObj.isRemote) return;
        ItemStack schematic = ItemRocketSchematic.captureFromSilo(this, pendingSchematicName);
        if (schematic != null) player.inventory.addItemStackToInventory(schematic);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj == null || worldObj.isRemote) return;
        Rocket rocket = rocket();
        if (rocket == null) return;
        tickServiceReturn(rocket);
        if (rocket.status() == Rocket.Status.CONSTRUCTING) tickConstruction(rocket);
        if (shouldRender && structureValid
            && rocket.status() == Rocket.Status.PHYSICAL
            && (entityRocket == null || entityRocket.isDead)) {
            getOrCreateEntityRocket();
        }
    }

    public void sync() {
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void invalidate() {
        endpointUnavailable();
        super.invalidate();
        if (entityRocket != null && !entityRocket.isDead) entityRocket.setDead();
    }

    @Override
    public void onChunkUnload() {
        endpointUnavailable();
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        identity.writeToNBT(tag);
        endpointBinding.writeToNBT(tag);
        if (vehicleId != null) tag.setString("vehicle", vehicleId.toString());
        tag.setTag("designBlueprint", designBlueprint.serializeNBT());
        tag.setBoolean("shouldRender", shouldRender);
        tag.setString("pendingSchematicName", pendingSchematicName);
        tag.setString("buildStatus", getBuildStatus().name());
    }

    @Override
    public Packet getDescriptionPacket() {
        var tag = new NBTTagCompound();
        writeToNBT(tag);
        tag.setTag("builtView", getBuiltBlueprint().serializeNBT());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        identity.readFromNBT(tag);
        endpointBinding.readFromNBT(tag);
        vehicleId = tag.hasKey("vehicle") ? UUID.fromString(tag.getString("vehicle")) : null;
        designBlueprint = RocketBlueprint
            .deserializeNBT(tag.getCompoundTag("designBlueprint"), RocketPartRegistry.instance());
        shouldRender = tag.getBoolean("shouldRender");
        pendingSchematicName = tag.getString("pendingSchematicName");
        clientBuildStatus = RocketBuildStatus.valueOf(tag.getString("buildStatus"));
        if (worldObj != null && worldObj.isRemote) {
            clientBuiltView = RocketBlueprint
                .deserializeNBT(tag.getCompoundTag("builtView"), RocketPartRegistry.instance());
        }
    }

    @Override
    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }
}
