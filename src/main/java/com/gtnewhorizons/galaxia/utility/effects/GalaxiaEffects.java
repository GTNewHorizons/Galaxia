package com.gtnewhorizons.galaxia.utility.effects;

import net.minecraft.potion.Potion;

public class GalaxiaEffects {

    public static Potion lowOxygen;
    public static Potion overheating;
    public static Potion freezing;

    public static void init() {
        lowOxygen = new EffectLowOxygen(100);
        overheating = new EffectOverheating(101);
        freezing = new EffectFreezing(102);
    }
}
