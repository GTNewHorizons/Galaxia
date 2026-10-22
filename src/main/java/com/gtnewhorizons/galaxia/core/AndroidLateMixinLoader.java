package com.gtnewhorizons.galaxia.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public class AndroidLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.galaxia.android.late.json";
    }

    @Nonnull
    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("Baubles")) {
            mixins.add("AndroidExternalMixin");
        }
        if (loadedMods.contains("GalacticraftCore")) {
            mixins.add("AndroidExternalMixin$Galacticraft");
            mixins.add("AndroidExternalMixin$GalacticraftInventory");
        }
        if (loadedMods.contains("Botania")) {
            mixins.add("AndroidEnderHandMixin");
        }
        if (loadedMods.contains("ThaumicTinkerer")) {
            mixins.add("AndroidEnderFocusMixin");
            if (loadedMods.contains("EnderStorage")) {
                mixins.add("AndroidEnderFocusMixin$EnderStorage");
            }
        }
        return mixins;
    }
}
