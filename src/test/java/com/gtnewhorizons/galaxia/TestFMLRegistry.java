package com.gtnewhorizons.galaxia;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.init.Bootstrap;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

import cpw.mods.fml.common.Loader;
import sun.misc.Unsafe;

public class TestFMLRegistry {

    private static boolean init = false;

    public static synchronized void init() {
        if (init) return;
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
            Bootstrap.func_151354_b();
            CelestialRegistry.freezeAndBake();
        } catch (Throwable e) {
            // Some FML registry setup failures leave enough state for tests to run.
        }
        init = true;

    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
