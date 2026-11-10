package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

/** How one use of an item in a station reads: its line of text and the icon beside it */
final class StationItemInteractionText {

    private StationItemInteractionText() {}

    static String textFor(StationItemInteractionModel.Entry entry) {
        String count = entry.count() > 1
            ? StatCollector.translateToLocalFormatted("galaxia.gui.station.interactions.count", entry.count())
            : "";
        return switch (entry.role()) {
            case CORE_IMPORT -> StatCollector
                .translateToLocalFormatted("galaxia.gui.station.interactions.core_import", entry.reserve());
            case HAMMER_EXPORT -> StatCollector
                .translateToLocalFormatted("galaxia.gui.station.interactions.hammer_export", entry.reserve());
            case CONSUMES -> StatCollector
                .translateToLocalFormatted("galaxia.gui.station.interactions.consumes", entry.label(), count);
            case PRODUCES -> StatCollector
                .translateToLocalFormatted("galaxia.gui.station.interactions.produces", entry.label(), count);
            case UPKEEP -> StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.interactions.upkeep_line",
                entry.label(),
                count,
                entry.amountPerMinute()
                    .toDisplayString());
        };
    }

    static ItemStack iconFor(StationItemInteractionModel.Entry entry) {
        if (entry.role() == StationItemInteractionModel.Role.CORE_IMPORT) return new ItemStack(Items.compass);
        if (entry.role() == StationItemInteractionModel.Role.HAMMER_EXPORT) return new ItemStack(Items.iron_pickaxe);
        FacilityModuleKind kind = entry.kind();
        if (kind == null) return new ItemStack(Items.compass);
        return switch (kind) {
            case HAMMER -> new ItemStack(Items.iron_pickaxe);
            case MINER -> new ItemStack(Items.diamond_pickaxe);
            case POWER, BATTERY, GEOTHERMAL_GENERATOR, DEBUG_DATA_GENERATOR -> new ItemStack(Items.redstone);
            case STORAGE -> new ItemStack(Blocks.chest);
            case TANK -> new ItemStack(Items.bucket);
            case MAINTENANCE_BAY -> new ItemStack(Blocks.anvil);
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> new ItemStack(
                Blocks.furnace);
        };
    }
}
