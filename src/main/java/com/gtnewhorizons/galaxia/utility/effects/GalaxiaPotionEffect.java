package com.gtnewhorizons.galaxia.utility.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Base class for Galaxia potion effects.
 * <p>
 * Centralizes shared behavior:
 * - Texture atlas binding for status icons
 * - Default no-op effect tick behavior
 * - Always-ready ticking policy
 * </p>
 */
public abstract class GalaxiaPotionEffect extends Potion {

    private static final ResourceLocation POTION_ATLAS = new ResourceLocation(
        "galaxia",
        "textures/effect/potion_effects.png");

    /**
     * Creates a Galaxia potion effect with configurable visual and gameplay properties.
     *
     * @param id          potion ID
     * @param isBadEffect whether Minecraft should treat this as a negative effect
     * @param color       ARGB/RGB effect color shown in UI
     * @param potionName  translation key, for example {@code galaxia.effect.low_oxygen}
     * @param iconX       x index within the potion atlas grid
     * @param iconY       y index within the potion atlas grid
     */
    protected GalaxiaPotionEffect(int id, boolean isBadEffect, int color, String potionName, int iconX, int iconY) {
        super(id, isBadEffect, color);
        setPotionName(potionName);
        setIconIndex(iconX, iconY);
    }

    /**
     * Binds Galaxia's custom potion atlas before delegating to vanilla icon lookup.
     */
    @SideOnly(Side.CLIENT)
    @Override
    public int getStatusIconIndex() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(POTION_ATLAS);
        return super.getStatusIconIndex();
    }

    /**
     * Default no-op tick behavior. Override in concrete effects if periodic logic is needed.
     */
    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {}

    /**
     * Returns {@code true} each tick so {@link #performEffect(EntityLivingBase, int)} can run continuously
     * when overridden by child classes.
     */
    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }
}
