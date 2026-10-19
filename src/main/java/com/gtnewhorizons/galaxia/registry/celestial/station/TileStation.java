package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.core.network.StationGraphSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileStationDevice;
import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehavior;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehaviorWithAttachments;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import lombok.Getter;
import lombok.Setter;

public class TileStation extends TileStationBase<TileStation> {

    public static final int COIL_COOLING_FACTOR = 1;
    public static final int COIL_HEATING_FACTOR = 1;
    public static final int OXYGEN_FACTOR = 10;
    public static final int OXYGEN_DECAY_RATE = 1;

    public static final long BASE_EUT_PER_BLOCK = 8;

    @Getter
    private IStationBehavior behavior = GalaxiaBehaviors.ROOM.get();

    private StationGraphSyncHandler activeGraphSyncHandler;

    public StationGraphSyncHandler getActiveGraphSyncHandler() {
        return activeGraphSyncHandler;
    }

    public void clearActiveGraphSyncHandler(StationGraphSyncHandler handler) {
        if (activeGraphSyncHandler == handler) {
            activeGraphSyncHandler = null;
        }
    }

    @Getter
    @Setter
    private UUID owner;
    private boolean proximityBlocked;
    private Role controllerFlag = Role.UNDEFINED;

    private CelestialAsset.ID backingStation;

    @Setter
    @Getter
    private List<BlockPos> attachments = new ArrayList<>();

    private IStructureDefinition<TileStation> DEFINITION = null;

    private final LongArraySet coolingCoils = new LongArraySet();
    private final LongArraySet heatingCoils = new LongArraySet();
    private final LongArraySet oxygenators = new LongArraySet();
    private final LongArraySet airPurifiers = new LongArraySet();
    private final LongArraySet witherBlockers = new LongArraySet();

    private double oxygenLevel = 0;

    public int getCoolingModifier() {
        return isSealed() ? (working(coolingCoils) * COIL_COOLING_FACTOR) : 0;
    }

    public void addCoolingCoil(int x, int y, int z) {
        coolingCoils.add(CoordinatePacker.pack(x, y, z));
    }

    public int getHeatingModifier() {
        return isSealed() ? (working(heatingCoils) * COIL_HEATING_FACTOR) : 0;
    }

    public void addHeatingCoil(int x, int y, int z) {
        heatingCoils.add(CoordinatePacker.pack(x, y, z));
    }

    public boolean isOxygenated() {
        return isSealed() && oxygenLevel >= 100;
    }

    public double getOxygenLevel() {
        return oxygenLevel;
    }

    public void addOxygenator(int x, int y, int z) {
        oxygenators.add(CoordinatePacker.pack(x, y, z));
    }

    public boolean hasAirPurifier() {
        return isSealed() && working(airPurifiers) > 0;
    }

    public void addAirPurifier(int x, int y, int z) {
        airPurifiers.add(CoordinatePacker.pack(x, y, z));
    }

    public boolean hasWitherBlocker() {
        return isSealed() && working(witherBlockers) > 0;
    }

    public void addWitherBlocker(int x, int y, int z) {
        witherBlockers.add(CoordinatePacker.pack(x, y, z));
    }

    /** How many of the machines at these places are doing their job */
    private int working(LongArraySet devices) {
        if (worldObj == null) return 0;
        int count = 0;
        for (long packed : devices) {
            TileEntity te = worldObj.getTileEntity(
                CoordinatePacker.unpackX(packed),
                CoordinatePacker.unpackY(packed),
                CoordinatePacker.unpackZ(packed));
            if (te instanceof TileStationDevice device && device.isWorking()) count++;
        }
        return count;
    }

    public void setBehavior(IStationBehavior newBehavior) {
        if (newBehavior == behavior) return;
        if (structureValid && behavior != null) {
            behavior.onStructureDisformed(this);
        }
        if (!(newBehavior instanceof IStationBehaviorWithAttachments) && !attachments.isEmpty()) {
            attachments.clear();
            markDirty();
        }
        this.behavior = newBehavior;
        markStructureDirty();
        markDirty();
        if (activeGraphSyncHandler != null) {
            activeGraphSyncHandler.forceDirty();
            activeGraphSyncHandler.triggerFullSync();
        }
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        reset();
    }

    /** Takes an energy hatch in the wall as a way for energy into the station */
    public boolean addEnergyInput(IGregTechTileEntity hatch) {
        addAttachment(new BlockPos(hatch.getXCoord(), hatch.getYCoord(), hatch.getZCoord()));
        return true;
    }

    public void addAttachment(BlockPos pos) {
        if (!attachments.contains(pos)) {
            attachments.add(pos);
        }
    }

    @Override
    public IStructureDefinition<TileStation> getStructureDefinition() {
        if (DEFINITION != null) return DEFINITION;

        // Dimensions without a definition allow no station blocks, so nothing forms there instead of crashing
        List<Block> stationBlocks = CelestialDimensionMaterializer.findDefinitionById(worldObj.provider.dimensionId)
            .map(DimensionDef::validSpaceStationBlocks)
            .orElse(null);
        DEFINITION = behavior.buildStructureDefinition(stationBlocks == null ? List.of() : stationBlocks);
        return DEFINITION;
    }

    @Override
    public int getSearchRadius() {
        return behavior.getSearchRadius();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (overlapsForeignStation()) {
            proximityBlocked = true;
            return;
        }
        proximityBlocked = false;
        behavior.onStructureFormed(this);
    }

    @Override
    protected void onStructureChecked() {
        // Every check takes the room's blocks again, so the inner energy hatches are sorted out after each one
        if (structureValid) dropInnerEnergyInputs();
        super.onStructureChecked();
    }

    /**
     * Energy hatches are a way for energy into the station only when built into the outer wall. Inside the room they
     * belong to machines the station feeds, even though the wall check takes every GT block for a wall
     */
    private void dropInnerEnergyInputs() {
        if (!(getStructureDefinition() instanceof ArbitraryShapeDefinition<?>def)) return;
        for (BlockPos pos : List.copyOf(attachments)) {
            if (!(pos.getTE(worldObj) instanceof IGregTechTileEntity hatch)
                || !(hatch.getMetaTileEntity() instanceof MTEHatchEnergy)) continue;
            if (def.facesOutside(worldObj, pos.x(), pos.y(), pos.z())) continue;
            attachments.remove(pos);
            if (graph != null) graph.removeAttachment(pos);
        }
    }

    @Override
    public void onStructureDisformed() {
        if (graph != null) {
            if (graph.getController() == this) {
                graph.destroy();
                graph = null;
                if (backingStation != null) {
                    CelestialAssetStore.disableAsset(backingStation);
                }
            } else {
                graph.disconnectPiece(here);
            }
        }
        behavior.onStructureDisformed(this);
        clearAllFunctionalBlocks();
        super.onStructureDisformed();
    }

    public void clearAllFunctionalBlocks() {
        coolingCoils.clear();
        heatingCoils.clear();
        oxygenators.clear();
        airPurifiers.clear();
        witherBlockers.clear();
    }

    @Override
    protected boolean attemptBoot() {
        if (controllerFlag == Role.MAIN) {
            initController();
            return super.attemptBoot();
        }

        // Try secondary boot — find an existing controller graph via airlocks
        for (BlockPos pos : airlocks) {
            if (!(pos.getTE(worldObj) instanceof TileEntityAirlock airlock)) continue;
            for (BlockPos other : airlock.getStationControllers()) {
                if (other.equals(here)) continue;
                if (!(other.getTE(worldObj) instanceof TileStationBase<?>base)) continue;
                if (base.graph != null) {
                    this.graph = base.graph;
                    graph.connectPiece(here);
                    controllerFlag = Role.SECONDARY;
                    super.attemptBoot();
                    return true;
                }
            }
        }

        if (controllerFlag == Role.SECONDARY) {
            return super.attemptBoot();
        }

        controllerFlag = Role.MAIN;
        initController();
        return super.attemptBoot();
    }

    private void initController() {
        graph = new StationGraph(this);
        graph.addListener(this);
        graph.rebuild();

        if (backingStation != null) {
            CelestialAssetStore.enableAsset(backingStation);
            if (CelestialAssetStore.findAsset(backingStation) instanceof Station station) {
                if (station.getController() == null) {
                    station.setController(this.here);
                }
                this.owner = CelestialAssetStore.getTeamId(backingStation);
                return;
            }
        }

        CelestialObjectKey bodyKey = GalaxiaCelestialAPI.findByDimension(this.worldObj.provider.dimensionId)
            .map(CelestialObject::key)
            .orElse(null);
        if (bodyKey == null) {
            return;
        }
        if (!(CelestialAsset.create(bodyKey, CelestialAsset.Kind.STATION, true) instanceof Station station)) return;
        station.setController(this.here);
        backingStation = station.assetId;
        CelestialAssetStore.registerAsset(owner, station);
    }

    private boolean overlapsForeignStation() {
        if (owner == null) return false;

        CelestialObjectKey bodyKey = GalaxiaCelestialAPI.findByDimension(worldObj.provider.dimensionId)
            .map(CelestialObject::key)
            .orElse(null);
        if (bodyKey == null) return false;

        for (CelestialAsset.ID otherId : CelestialAssetStore.getAssetsOnBody(bodyKey)) {
            CelestialAsset other = CelestialAssetStore.findAsset(otherId);
            if (!(other instanceof Station otherStation)) continue;
            if (otherStation.getController() != null && otherStation.getController()
                .equals(here)) continue;
            UUID otherTeam = CelestialAssetStore.getTeamId(otherId);
            if (otherTeam != null && otherTeam.equals(owner)) continue;

            TileStation otherTile = otherStation.getTileController();
            if (otherTile == null || !otherTile.structureValid) continue;

            if (piecesOverlap(this, otherTile)) return true;

            StationGraph otherGraph = otherTile.getGraph();
            if (otherGraph != null) {
                for (TileStationBase<?> piece : otherGraph.iterateOver(TileStationBase.class)) {
                    if (piecesOverlap(this, piece)) return true;
                }
            }
        }
        return false;
    }

    private static boolean piecesOverlap(TileStationBase<?> a, TileStationBase<?> b) {
        int dist = (a.getSearchRadius() + b.getSearchRadius()) * 2;
        int dx = Math.abs(a.here.x() - b.here.x());
        int dy = Math.abs(a.here.y() - b.here.y());
        int dz = Math.abs(a.here.z() - b.here.z());
        return dx < dist && dy < dist && dz < dist;
    }

    @Override
    protected void tickPostBoot() {
        if (proximityBlocked) return;
        behavior.tickPostBoot(this);
    }

    @Override
    public void tick() {
        if (!structureValid || proximityBlocked) return;

        super.tick();
        // Only the main room holds the station asset, secondary rooms power themselves from it too
        Station station = graph != null && graph.getController() != this ? graph.getController()
            .getBackingStation() : getBackingStation();
        if (station == null) return;

        if (station.tryConsumeEnergy(getUpkeepEUt()) && isSealed()) {

            oxygenLevel = Math
                .clamp(oxygenLevel + (double) (working(oxygenators) * OXYGEN_FACTOR) / getVolume(), 0.0, 100.0);
        } else {
            oxygenLevel = Math.max(oxygenLevel - OXYGEN_DECAY_RATE, 0);
        }

        if (!isMainController()) return;

        for (TileStationBase<?> secondary : graph.iterateOver(TileStationBase.class)) {
            secondary.tick();
        }
        graph.tickAttachments();
    }

    /** Energy the room itself needs every tick, a share per wall block. Its machines draw their own */
    public long getUpkeepEUt() {
        return getStructureBlocksAmount() * BASE_EUT_PER_BLOCK;
    }

    /** Number of functional blocks of one kind in the room, for the station terminal */
    public int countFunctionalBlocks(String kind) {
        return switch (kind) {
            case "oxygenator" -> oxygenators.size();
            case "cooling_coil" -> coolingCoils.size();
            case "heating_coil" -> heatingCoils.size();
            case "air_purifier" -> airPurifiers.size();
            case "wither_blocker" -> witherBlockers.size();
            default -> 0;
        };
    }

    public boolean isMainController() {
        return graph != null && graph.getController() == this;
    }

    /** True when this station booted as its own graph's main controller, false for absorbed secondaries. */
    public boolean isPrimary() {
        return controllerFlag == Role.MAIN;
    }

    public StationGraph getGraph() {
        return graph;
    }

    public BlockPos getHere() {
        return here;
    }

    public int getVolume() {
        var def = getStructureDefinition();
        if (def instanceof ArbitraryShapeDefinition<?>asd) {
            return asd.getVolume();
        }

        Galaxia.LOG.warn("[Station] `getVolume` called on a sealed structure defined without volume");

        return 1;
    }

    public int getStructureBlocksAmount() {
        var def = getStructureDefinition();
        if (def instanceof ArbitraryShapeDefinition<?>asd) {
            return asd.getStructureBlocksAmount();
        }

        Galaxia.LOG.warn("[Station] `getVolume` called on structure defined without amount of blocks");

        return 1;
    }

    public int getTotalVolume() {
        int own = 0;
        var def = getStructureDefinition();
        if (def instanceof ArbitraryShapeDefinition<?>asd) {
            own = asd.getVolume();
        }
        if (graph == null) return own;
        int sum = own;
        for (TileStationBase<?> s : graph.iterateOver(TileStationBase.class)) {
            if (s instanceof TileStation ts) {
                var tsDef = ts.getStructureDefinition();
                if (tsDef instanceof ArbitraryShapeDefinition<?>asd) {
                    sum += asd.getVolume();
                }
            }
        }
        return sum;
    }

    public List<IDistributedInventory> getConnectedInventories() {
        if (graph == null) return List.of();
        return graph.connectedInventories()
            .toList();
    }

    public @Nullable Station getBackingStation() {
        if (backingStation == null) return null;
        return CelestialAssetStore.findAsset(backingStation) instanceof Station station ? station : null;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        List<IStationBehavior> allBehaviors = GalaxiaBehaviors.getAll();

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);

        ModularPanel panel = GalaxiaGuiStyle.panel("galaxia:station_controller", 210, 160);
        panel.child(
            new Widget<>().size(6)
                .pos(8, 10)
                .background(
                    new DynamicDrawable(
                        () -> structureValidSync.getBoolValue() ? EnumTextures.GUI_LED_ON.getImage()
                            : EnumTextures.GUI_LED_OFF.getImage())));
        panel.child(
            GalaxiaGuiStyle.title("galaxia.gui.station_controller.title")
                .pos(18, 8));

        // Role indicator
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.role",
                        StatCollector.translateToLocal(
                            controllerFlag == Role.MAIN ? "galaxia.gui.role.main" : "galaxia.gui.role.secondary")),
                    () -> GalaxiaGuiStyle.TEXT,
                    1f)
                .pos(10, 24));

        IntSyncValue behaviorIdx = new IntSyncValue(
            () -> allBehaviors.indexOf(TileStation.this.behavior),
            () -> allBehaviors.indexOf(TileStation.this.behavior));
        syncManager.syncValue("behaviorIdx", 0, behaviorIdx);

        panel.child(
            GalaxiaGuiStyle
                .label(() -> StatCollector.translateToLocal("galaxia.gui.behavior"), () -> GalaxiaGuiStyle.TEXT, 1f)
                .pos(10, 40));
        panel.child(GalaxiaGuiStyle.textButton(IKey.dynamic(() -> {
            int idx = Math.max(0, behaviorIdx.getIntValue());
            if (idx < allBehaviors.size()) {
                return StatCollector.translateToLocal(
                    allBehaviors.get(idx)
                        .getUnlocalizedName());
            }
            return "???";
        }))
            .size(120, 16)
            .pos(80, 36)
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                int next = (behaviorIdx.getIntValue() + 1) % allBehaviors.size();
                setBehavior(allBehaviors.get(next));
                markStructureDirty();
            })));

        // Structure status
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station_controller.structure",
                        StatCollector.translateToLocal(
                            structureValidSync.getBoolValue() ? "galaxia.gui.status_valid"
                                : "galaxia.gui.status_invalid")),
                    () -> structureValidSync.getBoolValue() ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.BAD,
                    1f)
                .pos(10, 58));

        // Register station graph sync handler for all behaviors to consume
        StationGraphSyncHandler graphSyncHandler = new StationGraphSyncHandler();
        graphSyncHandler.setStation(this);
        syncManager.syncValue(StationGraphSyncHandler.KEY, graphSyncHandler);
        activeGraphSyncHandler = graphSyncHandler;

        // Behavior-specific widgets
        int behaviorY = 74;
        List<Widget<?>> behaviourWidgets = behavior.buildBehaviourWidgets(this, syncManager, behaviorY);
        if (behaviourWidgets != null) {
            for (Widget<?> w : behaviourWidgets) {
                panel.child(w);
            }
        }

        // Refresh button
        panel.child(
            GalaxiaGuiStyle.textButton(IKey.lang("galaxia.gui.station_controller.refresh"))
                .size(194, 20)
                .pos(8, 130)
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                    markStructureDirty();
                    if (graph != null) graph.rebuild();
                })));

        return panel;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte(
            "behavior",
            (byte) GalaxiaBehaviors.of(behavior)
                .getId());
        if (controllerFlag != null) {
            nbt.setByte("controllerFlag", controllerFlag.id);
        }

        if (owner != null) {
            nbt.setLong("ownerMost", owner.getMostSignificantBits());
            nbt.setLong("ownerLeast", owner.getLeastSignificantBits());
        }
        if (backingStation != null) {
            nbt.setLong(
                "backingStationMost",
                backingStation.id()
                    .getMostSignificantBits());
            nbt.setLong(
                "backingStationLeast",
                backingStation.id()
                    .getLeastSignificantBits());
        }
        nbt.setDouble("oxygenLevel", oxygenLevel);
        nbt.setBoolean("proximityBlocked", proximityBlocked);
        behavior.writeToNBT(this, nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("proximityBlocked")) {
            this.proximityBlocked = nbt.getBoolean("proximityBlocked");
        }

        if (nbt.hasKey("controllerFlag")) {
            controllerFlag = Role.fromId(nbt.getByte("controllerFlag"));
        }

        if (nbt.hasKey("behavior")) {
            behavior = GalaxiaBehaviors.byId(nbt.getByte("behavior"))
                .get();
        }

        if (nbt.hasKey("ownerMost") && nbt.hasKey("ownerLeast")) {
            owner = new UUID(nbt.getLong("ownerMost"), nbt.getLong("ownerLeast"));
        }
        if (nbt.hasKey("backingStationMost") && nbt.hasKey("backingStationLeast")) {
            backingStation = CelestialAsset.ID
                .from(new UUID(nbt.getLong("backingStationMost"), nbt.getLong("backingStationLeast")));
        }

        if (nbt.hasKey("oxygenLevel")) {
            oxygenLevel = nbt.getDouble("oxygenLevel");
        }
        behavior.readFromNBT(this, nbt);
    }

    @Override
    public void invalidate() {
        if (graph != null) {
            if (graph.getController() == this) {
                // Find a successor to transfer ownership
                TileStation successor = null;
                for (TileStation ts : graph.iterateOver(TileStation.class)) {
                    successor = ts;
                    break;
                }

                if (structureValid) {
                    structureValid = false;
                    behavior.onStructureDisformed(this);
                }

                if (successor != null && backingStation != null && !isChunkUnloading) {
                    CelestialAsset.ID assetId = this.backingStation;
                    this.backingStation = null;
                    successor.backingStation = assetId;
                    CelestialAssetStore.enableAsset(assetId);
                    if (CelestialAssetStore.findAsset(assetId) instanceof Station station) {
                        station.setController(successor.here);
                    }
                    successor.controllerFlag = Role.MAIN;
                    successor.markDirty();
                    successor.markStructureDirty();
                    successor.onMachineBlockUpdate();
                }

                graph.destroy();
                attachments.clear();
                graph = null;
                if (backingStation != null) {
                    if (isChunkUnloading) {
                        CelestialAssetStore.disableAsset(backingStation);
                    } else {
                        AssetStateSync.SERVER.destroyAsset(backingStation);
                    }
                }
            } else {
                graph.disconnectPiece(here);
            }
        }
        super.invalidate();
    }

    @Override
    public void onPieceConnected(TileStationBase<?> piece, TileStationBase<?> neighbor, BlockPos controllerPos) {
        if ((piece == this || neighbor == this) && controllerPos != null) {
            if (controllerPos.getTE(worldObj) instanceof TileStation controller) {
                graph = controller.getGraph();
            }
        }
    }

    @Override
    public void onPieceDisconnected(TileStationBase<?> piece, TileStationBase<?> neighbor) {
        if (piece != this && neighbor != this) return;
        if (structureValid) {
            structureValid = false;
            behavior.onStructureDisformed(this);
            bootState = BootState.UNINITIALIZED;
        }
        graph = null;
    }

    @Override
    public void onGraphRebuilt(TileStation controller) {
        if (!structureValid) return;
        if (controller.getGraph() != null) {
            graph = controller.getGraph();
        }
        if (proximityBlocked || overlapsForeignStation()) return;
        behavior.onGraphRebuilt(this);
        if (behavior instanceof IStationBehaviorWithAttachments attacher && graph != null) {
            attacher.registerAttachments(this, graph);
        }
    }

    @Override
    public void onAttachmentDisconnected(BlockPos pos) {
        if (attachments.remove(pos)) markDirty();
    }

    @Override
    protected int getControllerOffsetX() {
        return 0;
    }

    @Override
    protected int getControllerOffsetY() {
        return 0;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    protected void reset() {
        DEFINITION = null;
        controllerFlag = Role.UNDEFINED;
        graph = null;
        super.reset();
    }

    public enum Role {

        UNDEFINED(0),
        MAIN(1),
        SECONDARY(2);

        public final byte id;

        Role(int id) {
            assert id < 256;
            this.id = (byte) id;
        }

        public static Role fromId(byte id) {
            return switch (id) {
                case 0 -> UNDEFINED;
                case 1 -> MAIN;
                case 2 -> SECONDARY;
                default -> UNDEFINED;
            };
        }
    }
}
