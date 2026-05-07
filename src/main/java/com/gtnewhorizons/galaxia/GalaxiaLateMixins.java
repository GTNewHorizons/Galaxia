package com.gtnewhorizons.galaxia;

import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizons.galaxia.mixins.GalaxiaMixins;

@LateMixin
public class GalaxiaLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.galaxia.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return IMixins.getLateMixins(GalaxiaMixins.class, loadedMods);
    }
}
