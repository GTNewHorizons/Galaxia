package com.gtnewhorizons.galaxia.registry.hazards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class EnvironmentalHazardTest {

    private final int[] originalShieldSlots = Galaxia.shieldSlots;

    @BeforeAll
    static void initMinecraft() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @AfterEach
    void restoreSlots() {
        Galaxia.shieldSlots = originalShieldSlots;
    }

    /** Product contract: non-player bodies receive ordinary radiation but have no player oxygen or RCS requirement. */
    @Test
    void livingBodiesReceiveSharedHazardsWithoutPlayerOnlyRequirements() {
        Galaxia.shieldSlots = new int[] { 0 };
        LivingBody body = new LivingBody();
        EffectBuilder effects = EffectBuilder.builder()
            .radiation(1)
            .oxygenPercent(0)
            .build();

        assertEquals(HazardWarnings.HIGH_RADIATION, new HazardRadiation().apply(effects, body, Optional.empty()));
        assertTrue(body.receivedDamage > 0, "Hazardous radiation must damage an unprotected living body");
        assertEquals(HazardWarnings.FINE, new HazardOxygen().apply(effects, body, Optional.empty()));
        assertEquals(HazardWarnings.FINE, new HazardZeroG().apply(effects, body, Optional.empty()));
    }

    /** Harness exception: records the Minecraft damage boundary without starting a server or a player connection. */
    private static final class LivingBody extends EntityLivingBase {

        private float receivedDamage;

        private LivingBody() {
            super(null);
        }

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            receivedDamage += amount;
            return true;
        }

        @Override
        public ItemStack getHeldItem() {
            return null;
        }

        @Override
        public ItemStack getEquipmentInSlot(int slot) {
            return null;
        }

        @Override
        public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

        @Override
        public ItemStack[] getLastActiveItems() {
            return new ItemStack[4];
        }
    }
}
