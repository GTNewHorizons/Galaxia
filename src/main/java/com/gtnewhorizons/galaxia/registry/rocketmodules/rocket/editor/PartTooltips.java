package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;

/** Module properties shared by the rocket editor and machine catalogues. */
public final class PartTooltips {

    private static final double STANDARD_GRAVITY_MPS2 = 9.80665;

    private PartTooltips() {}

    public static void describe(RocketPartDef def, RichTooltip tooltip) {
        line(tooltip, "galaxia.rocket_part.stat.size", def.width(), def.height());
        line(tooltip, "galaxia.rocket_part.stat.mass", String.format("%,.0f", def.dryMassKg()));
        if (def.engine() != null) {
            line(
                tooltip,
                "galaxia.rocket_part.stat.thrust",
                String.format(
                    "%,.0f",
                    def.engine()
                        .thrustNewtons() / STANDARD_GRAVITY_MPS2));
            line(
                tooltip,
                "galaxia.rocket_part.stat.isp",
                String.format(
                    "%.0f",
                    def.engine()
                        .exhaustVelocityMps() / STANDARD_GRAVITY_MPS2));
            if (def.fuel() != null) line(
                tooltip,
                "galaxia.rocket_part.stat.burns",
                def.fuel()
                    .name());
            else for (var fuel : def.engine()
                .propellants()
                .keySet()) {
                    line(
                        tooltip,
                        "galaxia.rocket_part.stat.burns",
                        fuel.toStack(1)
                            .getLocalizedName());
                }
        }
        if (!def.storage()
            .propellantCapacities()
            .isEmpty()) {
            long capacity = def.storage()
                .propellantCapacities()
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
            line(tooltip, "galaxia.rocket_part.stat.capacity", GalaxiaGuiStyle.fluid(capacity));
            if (def.fuel() != null) line(
                tooltip,
                "galaxia.rocket_part.stat.full_mass",
                String.format(
                    "%,.0f",
                    def.dryMassKg() + def.fuel()
                        .massOf(capacity)));
        }
        if (def.storage()
            .freightFluidCapacity() > 0)
            line(
                tooltip,
                "galaxia.rocket_part.stat.capacity",
                GalaxiaGuiStyle.fluid(
                    def.storage()
                        .freightFluidCapacity()));
        if (def.storage()
            .itemSlots() > 0)
            line(
                tooltip,
                "galaxia.rocket_part.stat.slots",
                def.storage()
                    .itemSlots());
        if (def.seats() > 0) line(tooltip, "galaxia.rocket_part.stat.seats", def.seats());
    }

    private static void line(RichTooltip tooltip, String key, Object... values) {
        tooltip.addLine(IKey.str(StatCollector.translateToLocalFormatted(key, values)));
    }
}
