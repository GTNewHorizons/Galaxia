package com.gtnewhorizons.galaxia.testing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.stats.StatList;
import net.minecraft.util.RegistryNamespaced;

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
        bootstrapMinecraftRegistries();
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

    private static void bootstrapMinecraftRegistries() {
        try {
            if (Block.blockRegistry.getObject("fire") == null) {
                Block.registerBlocks();
            }
            repairStaticRegistryFields(Blocks.class, Block.blockRegistry);
            BlockFire.func_149843_e();

            if (Item.itemRegistry.getObject("diamond") == null) {
                Item.registerItems();
            }
            repairStaticRegistryFields(net.minecraft.init.Items.class, Item.itemRegistry);
            StatList.func_151178_a();
            invokeVanillaDispenserBootstrap();
            setBootstrapInitialized();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to initialize Minecraft registries for tests", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass()
            .getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void repairStaticRegistryFields(Class<?> registryHolder, RegistryNamespaced registry)
        throws ReflectiveOperationException {
        Unsafe unsafe = getUnsafe();
        for (Field field : registryHolder.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

            Object value = registry.getObject(field.getName());
            if (value == null || !field.getType()
                .isInstance(value)) continue;

            unsafe.putObjectVolatile(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
        }
    }

    private static void invokeVanillaDispenserBootstrap() throws ReflectiveOperationException {
        Method method = Bootstrap.class.getDeclaredMethod("func_151353_a");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void setBootstrapInitialized() throws ReflectiveOperationException {
        Field initialized = Bootstrap.class.getDeclaredField("field_151355_a");
        initialized.setAccessible(true);
        initialized.setBoolean(null, true);
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }
}
