package com.gtnewhorizons.galaxia.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public class GalaxiaKeyBinds {

    public static final KeyBinding TOGGLE_REACTION_CONTROL_SYSTEM = register(
        new KeyBinding("key.galaxia.toggle_rcs", Keyboard.KEY_C, "key.categories.galaxia"));

    public static final KeyBinding LAUNCH_ROCKET = register(
        new KeyBinding("key.galaxia.launch_rocket", Keyboard.KEY_SPACE, "key.categories.galaxia"));

    private static KeyBinding register(KeyBinding binding) {
        ClientRegistry.registerKeyBinding(binding);
        return binding;
    }

    public static void init() {
        // intentionally empty, used to trigger class into loading
    }
}
