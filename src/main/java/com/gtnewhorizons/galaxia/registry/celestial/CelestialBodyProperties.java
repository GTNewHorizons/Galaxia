package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialBodyProperties(boolean visitable, boolean canCreateStation, boolean canCreateOutpost,
    String oreProfile, List<ItemStack> ores, List<GtOreVeinDefinition> gtOreVeins, double radiation, double temperature,
    Map<String, String> metadata) {

    public CelestialBodyProperties {
        ores = copyOres(ores);
        gtOreVeins = gtOreVeins == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(gtOreVeins));
        metadata = metadata == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static List<ItemStack> copyOres(List<ItemStack> ores) {
        if (ores == null || ores.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack ore : ores) {
            if (ore == null) {
                continue;
            }
            ItemStack copy = ore.copy();
            copy.stackSize = 1;
            copies.add(copy);
        }
        return Collections.unmodifiableList(copies);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean visitable;
        private boolean canCreateStation;
        private boolean canCreateOutpost;
        private String oreProfile = "";
        private final List<ItemStack> ores = new ArrayList<>();
        private final List<GtOreVeinDefinition> gtOreVeins = new ArrayList<>();
        private double radiation;
        private double temperature;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder visitable(boolean value) {
            this.visitable = value;
            return this;
        }

        public Builder canCreateStation(boolean value) {
            this.canCreateStation = value;
            return this;
        }

        public Builder canCreateOutpost(boolean value) {
            this.canCreateOutpost = value;
            return this;
        }

        public Builder oreProfile(String value) {
            this.oreProfile = value;
            return this;
        }

        public Builder ore(ItemStack value) {
            if (value != null) {
                ItemStack copy = value.copy();
                copy.stackSize = 1;
                this.ores.add(copy);
            }
            return this;
        }

        public Builder ores(ItemStack... values) {
            if (values == null) {
                return this;
            }
            for (ItemStack value : values) {
                ore(value);
            }
            return this;
        }

        public Builder gtOreVein(GtOreVeinDefinition vein) {
            if (vein != null) {
                gtOreVeins.add(vein);
            }
            return this;
        }

        public Builder gtOreVeins(GtOreVeinDefinition... veins) {
            if (veins == null) {
                return this;
            }
            for (GtOreVeinDefinition vein : veins) {
                gtOreVein(vein);
            }
            return this;
        }

        public Builder radiation(double value) {
            this.radiation = value;
            return this;
        }

        public Builder temperature(double value) {
            this.temperature = value;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public CelestialBodyProperties build() {
            return new CelestialBodyProperties(
                visitable,
                canCreateStation,
                canCreateOutpost,
                oreProfile,
                ores,
                gtOreVeins,
                radiation,
                temperature,
                metadata);
        }
    }
}
