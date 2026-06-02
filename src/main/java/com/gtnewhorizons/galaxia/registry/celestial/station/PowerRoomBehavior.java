package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.gt.StationHatchElement;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.compat.structure.IExtendedStructureElement;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.core.network.StationGraphSyncHandler;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehaviorWithAttachments;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTStructureUtility;

public class PowerRoomBehavior implements IStationBehaviorWithAttachments {

    public static List<Block> ALL_VALID_POWER_ROOM_BLOCKS = List.of(
        GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
        GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
        GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());

    @Override
    public String getUnlocalizedName() {
        return "galaxia.behavior.power_room";
    }

    @Override
    public IStructureDefinition<TileStation> buildStructureDefinition(DimensionDef def) {
        return ArbitraryShapeDefinition.<TileStation>builder()
            .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
            .addElements(
                def.validSpaceStationBlocks()
                    .stream()
                    .filter(b -> ALL_VALID_POWER_ROOM_BLOCKS.contains(b))
                    .map(b -> IExtendedStructureElement.extend(b, StructureUtility.ofBlock(b, 0))))
            .addInteriorElement(GalaxiaStructureUtility.ofTileAdderCheckHints((room, tileEntity) -> {
                if (tileEntity instanceof IGregTechTileEntity gtTE) {
                    if (StationAttachmentRegistry.isRegisteredMTE(
                        gtTE.getMetaTileEntity()
                            .getClass())) {
                        room.addAttachment(new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord));
                        return true;
                    }
                }
                return false;
            }, GregTechAPI.sBlockMachines, MetaTileEntityIDs.lsc.ID))
            .addElement(
                GregTechAPI.sBlockMachines,
                GTStructureUtility.buildHatchAdder(TileStation.class)
                    .anyOf(StationHatchElement.Energy)
                    .casingIndex(1)
                    .hint(1)
                    .exclusive()
                    .build())
            .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((room, tileEntity) -> {
                if (tileEntity instanceof TileEntityAirlock airlock) {
                    if (!airlock.isStructureValid()) return false;
                    room.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
            .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
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
        return List.of(new TextWidget<>(IKey.dynamic(() -> {
            var snap = StationGraphSyncHandler.getSnapshot();
            String key = "galaxia.gui.station_controller.attachments";
            return StatCollector.translateToLocal(key) + ": " + snap.attachmentCount();
        })).pos(10, yOffset), new TextWidget<>(IKey.dynamic(() -> {
            var snap = StationGraphSyncHandler.getSnapshot();
            if (snap.attachmentCount() == 0) return "No energy sources";
            return "Energy: " + snap
                .totalStored() + " / " + snap.totalCapacity() + " EU (" + snap.attachmentCount() + " src)";
        })).pos(10, yOffset + 12));
    }
}
