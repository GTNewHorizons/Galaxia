package com.cleanroommc.galaxia.world;

import com.cleanroommc.galaxia.dimension.SpaceWorldBuilder;
import com.cleanroommc.galaxia.dimension.WorldProviderSpace;

public class WorldProviderCalx extends WorldProviderSpace {

    public WorldProviderCalx() {
        SpaceWorldBuilder.configure(this)
            .sky(true)
            .fog(0.15, 0.1, 0.3)
            .avgGround(80)
            .biome(new BiomeGenCalx(150))
            .build();
    }

    @Override
    public String getDimensionName() {
        return "Calx (Moon)";
    }
}
