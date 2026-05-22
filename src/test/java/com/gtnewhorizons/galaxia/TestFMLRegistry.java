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
            if (!bootstrapMinecraftRegistries()) {
                init = true;
                return;
            }
            CelestialRegistry.freezeAndBake();
            init = true;
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

    private static boolean bootstrapMinecraftRegistries() {
        try {
            Bootstrap.func_151354_b();
            return true;
        } catch (NullPointerException e) {
            if (!isKnownFireBootstrapFailure(e)) throw e;
            return false;
        }
    }

    private static boolean isKnownFireBootstrapFailure(NullPointerException e) {
        StackTraceElement[] stack = e.getStackTrace();
        return stack.length > 0 && "net.minecraft.block.BlockFire".equals(stack[0].getClassName())
            && "func_149843_e".equals(stack[0].getMethodName());
    }
}
