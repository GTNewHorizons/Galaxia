package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehavior;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehaviorWithAttachments;

import lombok.Getter;
import lombok.Setter;

public class TileStation extends TileStationBase<TileStation> {

    private IStationBehavior behavior = GalaxiaBehaviors.ROOM.get();

    @Getter
    @Setter
    private UUID owner;
    private Role controllerFlag = Role.UNDEFINED;

    @Getter
    private CelestialAsset.ID backingStation;

    @Setter
    @Getter
    private List<BlockPos> attachments = new ArrayList<>();

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
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public void addAttachment(BlockPos pos) {
        if (!attachments.contains(pos)) {
            attachments.add(pos);
        }
    }

    @Override
    public IStructureDefinition<TileStation> getStructureDefinition() {
        return behavior.getStructureDefinition();
    }

    @Override
    public int getSearchRadius() {
        return behavior.getSearchRadius();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        behavior.onStructureFormed(this);
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
        super.onStructureDisformed();
    }

    @Override
    protected boolean attemptBoot() {
        if (controllerFlag == Role.MAIN) {
            initController();
            return true;
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
                    return true;
                }
            }
        }

        if (controllerFlag == Role.SECONDARY) {
            return false;
        }

        controllerFlag = Role.MAIN;
        initController();
        return true;
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
                return;
            }
        }

        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(this.worldObj.provider.dimensionId);
        Station station = (Station) CelestialAsset.create(objectId, CelestialAsset.Kind.STATION, true);
        station.setController(this.here);
        backingStation = station.assetId;
        CelestialAssetStore.registerAsset(owner, station);
    }

    @Override
    protected void tickPostBoot() {
        behavior.tickPostBoot(this);
    }

    @Override
    public void tick() {
        super.tick();

        if (!isMainController()) return;

        for (TileStationBase<?> secondary : graph.iterateOver(TileStationBase.class)) {
            secondary.tick();
        }
        graph.getAttachments()
            .forEach(IStationAttachment::tick);
    }

    public boolean isMainController() {
        return graph != null && graph.getController() == this;
    }

    public StationGraph getGraph() {
        return graph;
    }

    public BlockPos getHere() {
        return here;
    }

    public int getVolume() {
        int own = 0;
        var def = behavior.getStructureDefinition();
        if (def instanceof ArbitraryShapeDefinition<?>asd) {
            own = asd.getVolume();
        }
        if (graph == null) return own;
        int sum = own;
        for (TileStationBase<?> s : graph.iterateOver(TileStationBase.class)) {
            if (s instanceof TileStation ts) {
                var tsDef = ts.behavior.getStructureDefinition();
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

    public boolean hasOxygen(int x, int y, int z) {
        if (isInside(x, y, z)) return isOxygenated();

        if (graph != null) {
            for (TileStationBase<?> secondary : graph.iterateOver(TileStationBase.class)) {
                if (secondary.isInside(x, y, z)) return secondary.isOxygenated();
            }
        }

        return false;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        boolean isCtrl = controllerFlag == Role.MAIN;
        List<IStationBehavior> allBehaviors = GalaxiaBehaviors.getAll();

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);

        int panelHeight = 160;
        ModularPanel panel = new ModularPanel("galaxia:station_controller").size(210, panelHeight)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.station_controller.title"))
                    .asWidget()
                    .pos(8, 8));

        // Role indicator
        panel.child(new TextWidget<>(IKey.dynamic(() -> {
            String roleKey = controllerFlag == Role.MAIN ? "galaxia.gui.role.main" : "galaxia.gui.role.secondary";
            return StatCollector.translateToLocal("galaxia.gui.role") + ": " + StatCollector.translateToLocal(roleKey);
        })).pos(10, 22));

        IntSyncValue behaviorIdx = new IntSyncValue(
            () -> allBehaviors.indexOf(TileStation.this.behavior),
            () -> allBehaviors.indexOf(TileStation.this.behavior));
        syncManager.syncValue("behaviorIdx", 0, behaviorIdx);

        panel.child(
            IKey.str(StatCollector.translateToLocal("galaxia.gui.behavior"))
                .asWidget()
                .pos(10, 38));
        panel.child(
            new ButtonWidget<>().size(120, 16)
                .pos(70, 36)
                .overlay(IKey.dynamic(() -> {
                    int idx = Math.max(0, behaviorIdx.getIntValue());
                    if (idx < allBehaviors.size()) {
                        return StatCollector.translateToLocal(
                            allBehaviors.get(idx)
                                .getUnlocalizedName());
                    }
                    return "???";
                }))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                    int next = (behaviorIdx.getIntValue() + 1) % allBehaviors.size();
                    setBehavior(allBehaviors.get(next));
                    markStructureDirty();
                })));

        // Structure status
        int structY = isCtrl ? 40 : 58;
        panel.child(new TextWidget<>(IKey.dynamic(() -> {
            boolean valid = structureValidSync.getBoolValue();
            String structure = StatCollector.translateToLocal("galaxia.gui.station_controller.structure");
            String status = StatCollector
                .translateToLocal(valid ? "galaxia.gui.status_valid" : "galaxia.gui.status_invalid");
            EnumChatFormatting color = valid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            return structure + ": " + color + status + EnumChatFormatting.RESET;
        })).pos(10, structY));

        // Behavior-specific widgets
        int behaviorY = isCtrl ? 60 : 78;
        List<Widget<?>> behaviourWidgets = behavior.buildBehaviourWidgets(this, syncManager, behaviorY);
        if (behaviourWidgets != null) {
            for (Widget<?> w : behaviourWidgets) {
                panel.child(w);
            }
        }

        // Refresh button
        int buttonY = isCtrl ? 95 : 130;
        panel.child(
            new ButtonWidget<>().size(190, 30)
                .pos(10, buttonY)
                .overlay(IKey.str(StatCollector.translateToLocal("galaxia.gui.station_controller.refresh")))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                    if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                    markStructureDirty();
                    if (graph != null) {
                        graph.rebuild();
                        for (TileStationBase<?> secondary : graph.iterateOver(TileStationBase.class)) {
                            System.out.println(secondary.here);
                        }
                    }
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

        behavior.writeToNBT(this, nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

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
                        CelestialAssetStore.destroyAsset(backingStation);
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
        controllerFlag = Role.UNDEFINED;
    }

    @Override
    public void onGraphRebuilt(TileStation controller) {
        if (!structureValid) return;
        if (controller.getGraph() != null) {
            graph = controller.getGraph();
        }
        behavior.onGraphRebuilt(this);
        if (behavior instanceof IStationBehaviorWithAttachments attacher && graph != null) {
            attacher.registerAttachments(this, graph);
        }
    }

    @Override
    public void onAttachmentConnected(BlockPos pos, IStationAttachment<?> attachment) {
        if (!(behavior instanceof IStationBehaviorWithAttachments attacher)) return;
        if (!attachments.contains(pos)) {
            attachments.add(pos);
            markDirty();
            attacher.onAttachmentsChanged(this, pos, true);
        }
    }

    @Override
    public void onAttachmentDisconnected(BlockPos pos) {
        if (!(behavior instanceof IStationBehaviorWithAttachments attacher)) return;
        if (attachments.remove(pos)) {
            markDirty();
            attacher.onAttachmentsChanged(this, pos, false);
        }
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
                default -> throw new IllegalArgumentException("Unknown Role ID: " + id);
            };
        }
    }
}
