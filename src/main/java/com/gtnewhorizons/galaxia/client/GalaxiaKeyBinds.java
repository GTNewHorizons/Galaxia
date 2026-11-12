package com.gtnewhorizons.galaxia.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Loader;

/** Class contianing Galaxia keybinds and a method to register them. */
public class GalaxiaKeyBinds {

    /** Keybind for toggling the RCS, defaults to C and has language key key.galaxia.toggle_rcs. */
    public static final KeyBinding TOGGLE_REACTION_CONTROL_SYSTEM = register(
        new KeyBinding("key.galaxia.toggle_rcs", Keyboard.KEY_C, "key.categories.galaxia"));

    /** Keybind for launching a rocket, defaults to Space and has language key key.galaxia.launch_rocket. */
    public static final KeyBinding LAUNCH_ROCKET = register(
        new KeyBinding("key.galaxia.launch_rocket", Keyboard.KEY_SPACE, "key.categories.galaxia"));

    /** Keybind for opening the offscreen starmap, defaults to G and has language key key.galaxia.starmap_offscreen. */
    public static final KeyBinding OPEN_OFFSCREEN_STARMAP = Loader.isModLoaded("offscreen")
        ? register(new KeyBinding("key.galaxia.starmap_offscreen", Keyboard.KEY_G, "key.categories.galaxia"))
        : null;

    /** Keybind for ending android control, defaults to End and has language key key.galaxia.end_android_control. */
    public static final KeyBinding END_ANDROID_CONTROL = register(
        new KeyBinding("key.galaxia.end_android_control", Keyboard.KEY_END, "key.categories.galaxia"));

    /** Register a keybind
     * @param binding    The keybind to register
     * @return binding   The keybind registered, same as the parameter
     */
    private static KeyBinding register(KeyBinding binding) {
        ClientRegistry.registerKeyBinding(binding);
        return binding;
    }

    public static void init() {
        // intentionally empty, used to trigger class into loading
    }
}
