package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.config.ConfigStructures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationAttachment;
import com.gtnewhorizons.galaxia.registry.interfaces.IStationBehaviorWithAttachments;

public class DockBehavior implements IStationBehaviorWithAttachments {

    public static List<Block> ALL_VALID_DOCK_BLOCKS = List.of(GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get());

    @Override
    public String getUnlocalizedName() {
        return "galaxia.behavior.dock";
    }

    @Override
    public IStructureDefinition<TileStation> buildStructureDefinition(DimensionDef def) {
        return ArbitraryShapeDefinition.<TileStation>builder()
            .addControllerBlock(GalaxiaBlocksEnum.STATION_CONTROLLER.get())
            .addElements(
                def.validSpaceStationBlocks()
                    .stream()
                    .filter(b -> ALL_VALID_DOCK_BLOCKS.contains(b))
                    .map(b -> GalaxiaStructureUtility.ofBlock(b, 0)))
            .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dock, tileEntity) -> {
                if (tileEntity instanceof TileEntityAirlock airlock) {
                    if (!airlock.isStructureValid()) return false;
                    dock.registerAirlock(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
            .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dock, tileEntity) -> {
                if (tileEntity instanceof IStationAttachment) {
                    BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                    dock.addAttachment(pos);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.HAMMER_TARGET.get(), 0))
            .addElement(GalaxiaStructureUtility.ofTileAdderCheckHintsAnyMeta((dock, tileEntity) -> {
                if (tileEntity instanceof IStationAttachment) {
                    BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                    dock.addAttachment(pos);
                    return true;
                }
                return false;
            }, GalaxiaBlocksEnum.HAMMER_CANNON.get(), 0))
            .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
            .withSearchRadius(ConfigStructures.open.searchRadius)
            .open()
            .build();
    }

    @Override
    public int getSearchRadius() {
        return 0;
    }

    @Override
    public List<Widget<?>> buildBehaviourWidgets(TileStation station, PanelSyncManager syncManager, int yOffset) {
        return List.of(new TextWidget<>(IKey.dynamic(() -> {
            int count = station.getAttachments()
                .size();
            String key = "galaxia.gui.station_controller.targets";
            return StatCollector.translateToLocal(key) + ": " + count;
        })).pos(10, yOffset));
    }
}
