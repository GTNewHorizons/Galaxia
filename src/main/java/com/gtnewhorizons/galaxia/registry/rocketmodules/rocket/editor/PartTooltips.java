package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import java.util.Set;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketStage;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RiderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;

/**
 * Tooltip lines describing modules and stages, shared by the silo and the assembler
 */
public final class PartTooltips {

    private PartTooltips() {}

    /**
     * Adds the stats of a module below its name
     *
     * @param def     The module
     * @param tooltip The tooltip to fill
     */
    public static void describe(IRocketPartDef def, RichTooltip tooltip) {
        line(tooltip, "galaxia.rocket_part.stat.size", def.width(), def.height());
        line(tooltip, "galaxia.rocket_part.stat.mass", String.format("%,d", def.weight()));

        if (def instanceof EnginePartDef engine) {
            line(tooltip, "galaxia.rocket_part.stat.thrust", String.format("%,.0f", engine.thrust()));
            line(tooltip, "galaxia.rocket_part.stat.isp", String.format("%.0f", engine.isp()));
            line(
                tooltip,
                "galaxia.rocket_part.stat.burns",
                engine.fuel()
                    .name());
        }
        if (def instanceof FuelTankPartDef tank) {
            line(tooltip, "galaxia.rocket_part.stat.capacity", GalaxiaGuiStyle.fluid(tank.fuelCapacity()));
            line(
                tooltip,
                "galaxia.rocket_part.stat.full_mass",
                String.format(
                    "%,.0f",
                    tank.weight() + tank.fuel()
                        .massOf(tank.fuelCapacity())));
        }
        if (def instanceof StoragePartDef storage) {
            line(tooltip, "galaxia.rocket_part.stat.slots", storage.slots());
        }
        if (def instanceof RiderPartDef rider) {
            line(tooltip, "galaxia.rocket_part.stat.seats", rider.riderCapacity());
        }
    }

    /**
     * Describes a stage of the design
     *
     * @param stage   The stage
     * @param tooltip The tooltip to fill
     */
    public static void describeStage(RocketStage stage, RichTooltip tooltip) {
        tooltip.addLine(
            IKey.str(StatCollector.translateToLocalFormatted("galaxia.rocket_editor.stage", stage.getNumber() + 1)));
        line(tooltip, "galaxia.rocket_part.stat.mass", String.format("%,.0f", stage.getDryMass()));
        line(tooltip, "galaxia.rocket_part.stat.thrust", String.format("%,.0f", stage.getTotalThrust()));
        Set<RocketFuel> fuels = stage.getBurnableFuels();
        for (RocketFuel fuel : fuels) {
            line(tooltip, "galaxia.rocket_part.stat.burns", fuel.name());
        }
        if (!stage.hasEngines()) {
            tooltip.addLine(IKey.lang("galaxia.rocket_editor.stage_payload"));
        }
    }

    private static void line(RichTooltip tooltip, String key, Object... values) {
        tooltip.addLine(IKey.str(StatCollector.translateToLocalFormatted(key, values)));
    }
}
