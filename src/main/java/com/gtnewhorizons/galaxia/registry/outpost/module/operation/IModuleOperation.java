package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

import javax.annotation.Nullable;

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

    record Hammer(ModuleTier targetTier, String targetVariantKey) implements IModuleOperation {

        public Hammer {
            if (targetVariantKey == null || targetVariantKey.isBlank()) {
                throw new IllegalArgumentException("targetVariantKey must not be null or blank");
            }
        }
    }

    record MinerFocus(ModuleTier targetTier, String targetFocusTierKey, @Nullable String targetFocusOreKey)
        implements IModuleOperation {

        public MinerFocus {
            if (targetFocusTierKey == null || targetFocusTierKey.isBlank()) {
                throw new IllegalArgumentException("targetFocusTierKey must not be null or blank");
            }
        }
    }

    enum Deconstruction implements IModuleOperation {
        INSTANCE
    }
}
