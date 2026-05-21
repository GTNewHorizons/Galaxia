package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

public class TileStationController extends TileStationBase<TileStationController> {

    private UUID owner;
    private CelestialAsset.ID backingStation;

    public final ArbitraryShapeDefinition<TileStationController> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStationController>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
        .addElements(
            BASE_VALID_BLOCKS.stream()
                .map(b -> GalaxiaStructureUtility.ofBlock(b, 0)))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((stationController, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;

                stationController.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .withSearchRadius(ConfigStructures.enclosed.searchRadius)
        .enclosed()
        .build();

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();

        graph = new StationGraph(this);
        graph.addListener(this);
        graph.rebuild();

        if (backingStation != null) {
            CelestialAssetStore.enableAsset(backingStation);
            if (CelestialAssetStore.findAsset(backingStation) instanceof Station station) {
                // Re
                if (station.getController() == null) {
                    station.setController(this.here);
                }

                return;
            }

            // Station either not found or not station (weird?), register a new one
        }

        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(this.worldObj.provider.dimensionId);
        Station station = (Station) CelestialAsset.create(objectId, CelestialAsset.Kind.STATION, true);
        station.setController(this.here);
        backingStation = station.assetId;

        CelestialAssetStore.registerAsset(owner, station);
    }

    @Override
    public int getSearchRadius() {
        return STRUCTURE_DEFINITION.getSearchRadius();
    }

    @Override
    public void onStructureDisformed() {
        if (graph != null) {
            graph.destroy();
            graph = null;
        }
        super.onStructureDisformed();
        if (backingStation != null) {
            CelestialAssetStore.disableAsset(backingStation);
        }
    }

    public StationGraph getGraph() {
        return graph;
    }

    public int getVolume() {
        int own = STRUCTURE_DEFINITION.getVolume();
        if (graph == null) return own;
        int sum = own;
        for (TileStationSecondary<?> s : graph.iterateOver(TileStationSecondary.class)) {
            sum += s.getVolume();
        }
        return sum;
    }

    public List<IDistributedInventory> getConnectedInventories() {
        if (graph == null) return List.of();
        return graph.connectedInventories()
            .toList();
    }

    @Override
    public ForgeDirection getPlacedFacing() {
        return placedFacing;
    }

    @Override
    public IStructureDefinition<TileStationController> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
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
    public void tick() {
        super.tick();

        if (graph != null) {
            for (TileStationBase<?> secondary : graph.iterateOver(TileStationBase.class)) {
                secondary.tick();
            }

            graph.getAttachments()
                .forEach(IStationAttachment::tick);
        }
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }

        BooleanSyncValue structureValidSync = new BooleanSyncValue(() -> structureValid, () -> structureValid);
        syncManager.syncValue("structureValid", 0, structureValidSync);

        BooleanSyncValue oxygenatedSync = new BooleanSyncValue(() -> isOxygenated(), () -> isOxygenated());
        syncManager.syncValue("oxygenated", 0, oxygenatedSync);

        return new ModularPanel("galaxia:station_controller").size(210, 130)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.station_controller.title"))
                    .asWidget()
                    .pos(8, 8))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                boolean valid = structureValidSync.getBoolValue();
                String structure = StatCollector.translateToLocal("galaxia.gui.station_controller.structure");
                String status = StatCollector
                    .translateToLocal(valid ? "galaxia.gui.status_valid" : "galaxia.gui.status_invalid");
                EnumChatFormatting color = valid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
                return structure + ": " + color + status + EnumChatFormatting.RESET;
            })).pos(10, 30))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                boolean oxy = oxygenatedSync.getBoolValue();
                String oxygen = StatCollector.translateToLocal("galaxia.gui.station_controller.oxygen");
                String status = StatCollector
                    .translateToLocal(oxy ? "galaxia.gui.status_yes" : "galaxia.gui.status_no");
                EnumChatFormatting color = oxy ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
                return oxygen + ": " + color + status + EnumChatFormatting.RESET;
            })).pos(10, 50))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
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
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
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
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("ownerMost") && nbt.hasKey("ownerLeast")) {
            owner = new UUID(nbt.getLong("ownerMost"), nbt.getLong("ownerLeast"));
        }

        if (nbt.hasKey("backingStationMost") && nbt.hasKey("backingStationLeast")) {
            backingStation = CelestialAsset.ID
                .from(new UUID(nbt.getLong("backingStationMost"), nbt.getLong("backingStationLeast")));
        }
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public CelestialAsset.ID getBackingStation() {
        return backingStation;
    }

    @Override
    public void invalidate() {
        if (graph != null) {
            graph.destroy();
            graph = null;
        }
        super.invalidate();
        if (backingStation != null) {
            if (isChunkUnloading) {
                CelestialAssetStore.disableAsset(backingStation);
            } else {
                CelestialAssetStore.destroyAsset(backingStation);
            }
        }
    }

    @Override
    public void onGraphRebuilt(TileStationController controller) {
        markDirty();
    }

}
