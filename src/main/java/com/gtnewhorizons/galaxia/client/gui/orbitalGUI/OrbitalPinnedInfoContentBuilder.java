package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.celestial.GtOreVeinDefinition;

final class OrbitalPinnedInfoContentBuilder {

    List<PinnedInfoRow> buildRows(OrbitalCelestialBody body) {
        List<PinnedInfoRow> rows = new ArrayList<>();
        rows.add(new PinnedInfoRow("Name", body.displayName()));
        rows.add(new PinnedInfoRow("Type", formatObjectClass(body.objectClass())));
        rows.add(new PinnedInfoRow("Landable", isLandable(body) ? "Yes" : "No"));
        rows.add(new PinnedInfoRow("Dangers", buildDangerSummary(body)));
        if (body.objectClass() != CelestialObjectClass.STAR && body.objectClass() != CelestialObjectClass.GALAXY) {
            rows.add(new PinnedInfoRow("Surface", formatSurfaceType(body)));
            if (!body.properties().gtOreVeins().isEmpty()) {
                rows.add(PinnedInfoRow.section("Veins"));
                for (GtOreVeinDefinition vein : body.properties().gtOreVeins()) {
                    rows.add(PinnedInfoRow.inlineItems(vein.displayName(), resolveGtVeinDisplayItems(vein)));
                }
            } else if (body.properties().ores().isEmpty()) {
                rows.add(new PinnedInfoRow("Ores", "Undefined"));
            } else {
                rows.add(new PinnedInfoRow("Ores", "", body.properties().ores()));
            }
        }
        return rows;
    }

    private String buildDangerSummary(OrbitalCelestialBody body) {
        List<String> dangers = new ArrayList<>();
        if (body.properties().radiation() >= 0.25) {
            dangers.add("Radiation");
        }
        if (body.properties().temperature() > 360) {
            dangers.add("Heat");
        }
        if (body.properties().temperature() > 0 && body.properties().temperature() < 120) {
            dangers.add("Cold");
        }
        if (!body.properties().visitable() && body.properties().canCreateOutpost()) {
            dangers.add("Remote");
        }
        return dangers.isEmpty() ? "None" : String.join(", ", dangers);
    }

    private String formatObjectClass(CelestialObjectClass objectClass) {
        String raw = objectClass.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private boolean isLandable(OrbitalCelestialBody body) {
        return switch (body.objectClass()) {
            case PLANET, MOON, ASTEROID -> body.properties().visitable();
            default -> false;
        };
    }

    private String formatSurfaceType(OrbitalCelestialBody body) {
        String surfaceType = body.properties().metadata().get("surface");
        if (surfaceType == null || surfaceType.isEmpty()) {
            return "Undefined";
        }
        return formatInfoToken(surfaceType);
    }

    private String formatInfoToken(String value) {
        String[] parts = value.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private List<ItemStack> resolveGtVeinDisplayItems(GtOreVeinDefinition vein) {
        List<ItemStack> items = new ArrayList<>();
        if (vein == null) {
            return items;
        }
        for (String oreName : vein.ores()) {
            ItemStack stack = resolveGtOreDisplayStack(oreName);
            if (stack != null) {
                items.add(stack);
            }
        }
        return items;
    }

    private ItemStack resolveGtOreDisplayStack(String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return null;
        }

        String materialKey = oreName.replaceAll("[^A-Za-z0-9]", "");
        String[] oreDictKeys = new String[] {
            "ore" + materialKey,
            "gem" + materialKey,
            "dust" + materialKey,
            "dustImpure" + materialKey,
            "crushed" + materialKey
        };

        for (String oreDictKey : oreDictKeys) {
            List<ItemStack> matches = OreDictionary.getOres(oreDictKey, false);
            if (matches == null || matches.isEmpty()) {
                continue;
            }
            ItemStack match = matches.get(0);
            if (match != null) {
                return match.copy();
            }
        }
        return null;
    }
}
