package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;
import com.gtnewhorizons.galaxia.utility.IPlanet;

public abstract class BasePlanet implements IPlanet {

    private int mass;
    private int orbitalDistance;

    @Override
    public DimensionDef buildDimension() {
        return DEF;
    }

    public DimensionDef getDef() {
        return DEF;
    }

    protected final DimensionDef DEF;

    protected BasePlanet() {
        DEF = createBuilder().build();
    }

    protected DimensionBuilder createBuilder() {
        return new DimensionBuilder().enumValue(getPlanetEnum())
            .provider(getProviderClass());
    }

    public int getOrbitalDistance() {
        return this.orbitalDistance;
    };

    public void setOrbitalDistance(int orbitalDistance) {
        this.orbitalDistance = orbitalDistance;
    }

    public int getMass() {
        return this.mass;
    };

    public void setMass(int mass) {
        this.mass = mass;
    }

    protected abstract PlanetEnum getPlanetEnum();

    protected abstract Class<? extends WorldProvider> getProviderClass();
}
