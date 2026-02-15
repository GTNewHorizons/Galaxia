package com.cleanroommc.galaxia.dimension;

import com.cleanroommc.galaxia.utility.IPlanet;
import com.cleanroommc.galaxia.utility.Planet;
import cpw.mods.fml.common.discovery.ASMDataTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SolarSystemRegistry {

    public static final List<DimensionDef> DIMENSIONS = new ArrayList<>();

    public static void registerAll(ASMDataTable asm) {
        Set<ASMDataTable.ASMData> data =
            asm.getAll(Planet.class.getName());

        for (ASMDataTable.ASMData entry : data) {
            try {
                Class<?> clazz = Class.forName(entry.getClassName());
                if (!IPlanet.class.isAssignableFrom(clazz)) {
                    throw new IllegalStateException(
                        clazz.getName() + " is annotated with @Planet but does not implement IPlanet");
                }
                IPlanet planet = (IPlanet) clazz.getDeclaredConstructor().newInstance();
                DIMENSIONS.add(planet.buildDimension());
            } catch (Exception e) {
                throw new RuntimeException(
                    "Failed to register planet: " + entry.getClassName(), e);
            }
        }
    }
}
