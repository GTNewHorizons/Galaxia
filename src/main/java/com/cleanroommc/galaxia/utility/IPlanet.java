package com.cleanroommc.galaxia.utility;

import com.cleanroommc.galaxia.dimension.DimensionDef;

public interface IPlanet {

    default Planet meta() {
        return getClass().getAnnotation(Planet.class);
    }

    default int getId() {
        return meta().id();
    }

    default String getName() {
        return meta().name();
    }

    DimensionDef buildDimension();
}
