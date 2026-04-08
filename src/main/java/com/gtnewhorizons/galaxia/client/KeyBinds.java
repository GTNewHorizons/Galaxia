package com.gtnewhorizons.galaxia.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public class KeyBinds {

    public static final KeyBinding TOGGLE_REACTION_CONTROL_SYSTEM = new KeyBinding(
        "key.galaxia.toggle_rcs",
        Keyboard.KEY_C,
        "key.categories.galaxia");

    public static void registerAll() {
        ClientRegistry.registerKeyBinding(TOGGLE_REACTION_CONTROL_SYSTEM);
    }
}
