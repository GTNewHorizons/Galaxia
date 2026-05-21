package com.gtnewhorizons.galaxia;

import java.lang.reflect.Field;

import net.minecraft.init.Bootstrap;

import cpw.mods.fml.common.Loader;
import sun.misc.Unsafe;

public class TestFMLRegistry {

    private static boolean init = false;

    public static void init() {
        if (init) return;
        init = true;
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Loader fakeLoader = (Loader) unsafe.allocateInstance(Loader.class);

            Field instanceField = Loader.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, fakeLoader);
            Bootstrap.func_151354_b();
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }
}
