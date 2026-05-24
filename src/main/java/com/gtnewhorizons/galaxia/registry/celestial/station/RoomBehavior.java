package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehavior;

public class RoomBehavior implements IStationBehavior {

    public static List<Block> ALL_VALID_ROOM_BLOCKS = List.of(
        GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
        GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
        GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());

    @Override
    public String getUnlocalizedName() {
        return "galaxia.behavior.room";
    }

    @Override
    public IStructureDefinition<TileStation> buildStructureDefinition(DimensionDef def) {
        return ArbitraryShapeDefinition.<TileStation>builder()
            .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
            .addElements(
                def.validSpaceStationBlocks()
                    .stream()
                    .filter(b -> ALL_VALID_ROOM_BLOCKS.contains(b))
                    .map(b -> GalaxiaStructureUtility.ofBlock(b, 0)))
            .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((station, tileEntity) -> {
                if (tileEntity instanceof TileEntityAirlock airlock) {
                    if (!airlock.isStructureValid()) return false;
                    station.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
            .addElement(
                GalaxiaStructureUtility.ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addCoolingCoil,
                    GalaxiaBlocksEnum.COOLING_COIL.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addHeatingCoil,
                    GalaxiaBlocksEnum.HEATING_COIL.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addAirPurifier,
                    GalaxiaBlocksEnum.AIR_PURIFIER.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility.ofBlockPosAdderNoMetaForceCheck(
                    TileStation::addWitherBlocker,
                    GalaxiaBlocksEnum.WITHER_BLOCKER.get(),
                    0))
            .addElement(
                GalaxiaStructureUtility
                    .ofBlockPosAdderNoMetaForceCheck(TileStation::addOxygenator, GalaxiaBlocksEnum.OXYGENATOR.get(), 0))
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
        BooleanSyncValue sealedSync = new BooleanSyncValue(() -> station.isSealed(), () -> station.isSealed());
        syncManager.syncValue("sealed", 0, sealedSync);

        return List.of(new TextWidget<>(IKey.dynamic(() -> {
            boolean sealed = sealedSync.getBoolValue();
            String label = StatCollector.translateToLocal("galaxia.gui.station_controller.sealed");
            String status = StatCollector.translateToLocal(sealed ? "galaxia.gui.status_yes" : "galaxia.gui.status_no");
            EnumChatFormatting color = sealed ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            return label + ": " + color + status + EnumChatFormatting.RESET;
        })).pos(10, yOffset));
    }
}
