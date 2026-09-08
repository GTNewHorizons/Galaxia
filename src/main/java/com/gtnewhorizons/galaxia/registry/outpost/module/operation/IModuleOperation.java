package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

public sealed interface IModuleOperation permits IModuleOperation.Tier,IModuleOperation.Hammer,IModuleOperation.MinerFocus,IModuleOperation.Deconstruction {

    IModuleOperation DECONSTRUCTION = Deconstruction.INSTANCE;

    default @Nullable ModuleTier targetTier() {
        return null;
    }

    record Tier(ModuleTier targetTier) implements IModuleOperation {

        public Tier {
            if (targetTier == null) {
                throw new IllegalArgumentException("targetTier must not be null");
            }
        }
    }

    record Hammer(ModuleTier targetTier, HammerVariant targetVariant) implements IModuleOperation {

        public Hammer {
            if (targetVariant == null) {
                throw new IllegalArgumentException("targetVariant must not be null");
            }
        }
    }

    record MinerFocus(ModuleTier targetTier, MinerFocusTier targetFocusTier, @Nullable String targetFocusOreKey)
        implements IModuleOperation {

        public MinerFocus {
            if (targetFocusTier == null) {
                throw new IllegalArgumentException("targetFocusTier must not be null");
            }
        }
    }

    enum Deconstruction implements IModuleOperation {
        INSTANCE
    }
}
