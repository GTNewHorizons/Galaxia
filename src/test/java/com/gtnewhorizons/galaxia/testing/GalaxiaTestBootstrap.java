package com.gtnewhorizons.galaxia.testing;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.init.Bootstrap;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;

import cpw.mods.fml.common.Loader;
import sun.misc.Unsafe;

public final class GalaxiaTestBootstrap {

    private static boolean minecraftInitialized;
    private static boolean celestialRegistryInitialized;
    private static boolean facilityModulesInitialized;

    private GalaxiaTestBootstrap() {}

    public static synchronized void ensureMinecraft() {
        if (minecraftInitialized) return;

        installFakeLoader();
        Bootstrap.func_151354_b();
        minecraftInitialized = true;
    }

    public static synchronized void ensureCelestialRegistry() {
        if (celestialRegistryInitialized) return;

        ensureMinecraft();
        CelestialRegistry.freezeAndBake();
        celestialRegistryInitialized = true;
    }

    public static synchronized void ensureFacilityModules() {
        if (facilityModulesInitialized) return;

        ensureCelestialRegistry();
        FacilityModuleRegistry.init();
        facilityModulesInitialized = true;
    }

    private static void installFakeLoader() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Loader fakeLoader = (Loader) unsafe.allocateInstance(Loader.class);
            setField(fakeLoader, "mods", new ArrayList<>());
            setField(fakeLoader, "namedMods", new HashMap<>());

            Field instanceField = Loader.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, fakeLoader);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to install fake FML Loader for tests", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
