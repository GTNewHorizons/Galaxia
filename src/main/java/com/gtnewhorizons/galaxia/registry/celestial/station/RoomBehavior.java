package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.gt.StationHatchElement;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.compat.structure.IExtendedStructureElement;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.TileStationItemVault;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileAirPurifier;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileCoolingCoil;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileHeatingCoil;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileOxygenator;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileWitherBlocker;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehaviorWithAttachments;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.GTStructureUtility;

public class RoomBehavior implements IStationBehaviorWithAttachments {

    public static List<Block> ALL_VALID_ROOM_BLOCKS = List.of(
        GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
        GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
        GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());

    @Override
    public String getUnlocalizedName() {
        return "galaxia.behavior.room";
    }

    @Override
    public IStructureDefinition<TileStation> buildStructureDefinition(List<Block> stationBlocks) {
        return ArbitraryShapeDefinition.<TileStation>builder()
            .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
            .addElements(
                stationBlocks.stream()
                    .filter(b -> ALL_VALID_ROOM_BLOCKS.contains(b))
                    .map(b -> IExtendedStructureElement.extend(b, StructureUtility.ofBlock(b, 0))))
            .addElement(GalaxiaStructureUtility.<TileStation>ofTileAdderCheckHintsAnyMeta((station, tileEntity) -> {
                if (tileEntity instanceof TileEntityAirlock airlock) {
                    if (!airlock.isStructureValid()) return false;
                    station.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
            .addElement(
                GregTechAPI.sBlockMachines,
                GTStructureUtility.<TileStation>buildHatchAdder()
                    .anyOf(StationHatchElement.Energy, StationHatchElement.StationPort, StationHatchElement.EnergyInput)
                    .casingIndex(1)
                    .hint(1)
                    .exclusive()
                    .build())
            .addElement(
                GalaxiaStructureUtility.<TileStation>ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addCoolingCoil,
                    GalaxiaBlocksEnum.COOLING_COIL.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.<TileStation>ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addHeatingCoil,
                    GalaxiaBlocksEnum.HEATING_COIL.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.<TileStation>ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addAirPurifier,
                    GalaxiaBlocksEnum.AIR_PURIFIER.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.<TileStation>ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addWitherBlocker,
                    GalaxiaBlocksEnum.WITHER_BLOCKER.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.<TileStation>ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addOxygenator,
                    GalaxiaBlocksEnum.OXYGENATOR.get(),
                    0))
            // Energy and fluid stores inside the room (LSC, TFFT, YOTTank, dynamo hatches) join the station
            .addInteriorElement(
                GalaxiaStructureUtility.<TileStation>ofTileAdderCheckHintsAnyMeta((room, tileEntity) -> {
                    if (tileEntity instanceof IGregTechTileEntity gtTE) {
                        IMetaTileEntity mte = gtTE.getMetaTileEntity();
                        // Energy hatches inside belong to machines the station feeds, only the wall takes them in
                        if (mte != null && !(mte instanceof MTEHatchEnergy)
                            && StationAttachmentRegistry.isRegisteredMTE(mte.getClass())) {
                            room.addAttachment(new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord));
                            return true;
                        }
                    }
                    return false;
                }, GregTechAPI.sBlockMachines, 0))
            // Machines keeping the room liveable work from inside it as well as from its walls
            .addInteriorElement(
                GalaxiaStructureUtility.<TileStation>ofTileAdderCheckHintsAnyMeta((room, tileEntity) -> {
                    int x = tileEntity.xCoord, y = tileEntity.yCoord, z = tileEntity.zCoord;
                    if (tileEntity instanceof TileOxygenator) room.addOxygenator(x, y, z);
                    else if (tileEntity instanceof TileAirPurifier) room.addAirPurifier(x, y, z);
                    else if (tileEntity instanceof TileWitherBlocker) room.addWitherBlocker(x, y, z);
                    else if (tileEntity instanceof TileCoolingCoil) room.addCoolingCoil(x, y, z);
                    else if (tileEntity instanceof TileHeatingCoil) room.addHeatingCoil(x, y, z);
                    else return false;
                    return true;
                }, GalaxiaBlocksEnum.OXYGENATOR.get(), 0))
            // Item stores inside the room join the station's shared storage
            .addInteriorElement(
                GalaxiaStructureUtility.<TileStation>ofTileAdderCheckHintsAnyMeta((room, tileEntity) -> {
                    if (!(tileEntity instanceof TileStationItemVault)) return false;
                    room.addAttachment(new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord));
                    return true;
                }, GalaxiaBlocksEnum.STATION_ITEM_VAULT.get(), 0))
            .embedDefinition(TileEntityAirlock.STRUCTURE_EMBED, TileEntityAirlock.STRUCTURE_DEFINITION)
            .withSearchRadius(ConfigStructures.enclosed.searchRadius)
            .enclosed()
            .build();
    }

    @Override
    public int getSearchRadius() {
        return ConfigStructures.enclosed.searchRadius;
    }

    @Override
    public List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset) {
        BooleanSyncValue sealedSync = new BooleanSyncValue(() -> station.isSealed(), () -> station.isSealed());
        syncManager.syncValue("sealed", 0, sealedSync);
        return List.of(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.gui.station_controller.sealed") + ": "
                        + StatCollector.translateToLocal(
                            sealedSync.getBoolValue() ? "galaxia.gui.status_yes" : "galaxia.gui.status_no"),
                    () -> sealedSync.getBoolValue() ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.BAD,
                    1f)
                .pos(10, yOffset),
            GalaxiaGuiStyle.label(() -> {
                var snap = station.getActiveGraphSyncHandler()
                    .getSnapshot();
                if (snap.fluidAttachmentCount() == 0) {
                    return StatCollector.translateToLocal("galaxia.gui.station_controller.no_fluid_tanks");
                }
                return StatCollector.translateToLocal("galaxia.gui.station_controller.fluid_storage") + ": "
                    + snap.fluidStored()
                    + " / "
                    + snap.fluidCapacity()
                    + " L ("
                    + snap.fluidAttachmentCount()
                    + ")";
            }, () -> GalaxiaGuiStyle.TEXT, 1f)
                .pos(10, yOffset + 14),
            GalaxiaGuiStyle.label(() -> {
                var snap = station.getActiveGraphSyncHandler()
                    .getSnapshot();
                if (snap.attachmentCount() == 0) {
                    return StatCollector.translateToLocal("galaxia.gui.station_controller.no_energy");
                }
                return StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station_controller.energy",
                    snap.totalStored(),
                    snap.totalCapacity(),
                    snap.attachmentCount());
            }, () -> GalaxiaGuiStyle.TEXT, 1f)
                .pos(10, yOffset + 28));
    }
}
