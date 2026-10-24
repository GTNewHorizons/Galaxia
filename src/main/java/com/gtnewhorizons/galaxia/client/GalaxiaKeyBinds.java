package com.gtnewhorizons.galaxia.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public class GalaxiaKeyBinds {

    public static final KeyBinding TOGGLE_REACTION_CONTROL_SYSTEM = register(
        new KeyBinding("key.galaxia.toggle_rcs", Keyboard.KEY_C, "key.categories.galaxia"));

    public static final KeyBinding OPEN_VEHICLE_SCHEDULE = register(
        new KeyBinding("galaxia.rocket.schedule.key", Keyboard.KEY_SPACE, "key.categories.galaxia"));

    public static final KeyBinding OPEN_OFFSCREEN_STARMAP = register(
        new KeyBinding("key.galaxia.starmap_offscreen", Keyboard.KEY_G, "key.categories.galaxia"));

    public static final KeyBinding END_ANDROID_CONTROL = register(
        new KeyBinding("key.galaxia.end_android_control", Keyboard.KEY_END, "key.categories.galaxia"));

    private static KeyBinding register(KeyBinding binding) {
        ClientRegistry.registerKeyBinding(binding);
        return binding;
    }

    public static void init() {
        // intentionally empty, used to trigger class into loading
    }
}
