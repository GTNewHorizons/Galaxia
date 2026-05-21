package com.gtnewhorizons.galaxia.testing;

import java.lang.reflect.Method;
import java.util.BitSet;

import net.minecraft.item.Item;

import cpw.mods.fml.common.registry.GameData;

public final class TestItemRegistry {

    private TestItemRegistry() {}

    public static void register(int id, String key, Item item) {
        Object registry = GameData.getItemRegistry();
        for (Method method : registry.getClass()
            .getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 4 && parameters[0] == int.class
                && parameters[1] == String.class
                && parameters[2].isAssignableFrom(Item.class)
                && parameters[3] == BitSet.class) {
                try {
                    method.setAccessible(true);
                    method.invoke(registry, id, key, item, new BitSet());
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("Item registry raw add method not found");
    }
}
