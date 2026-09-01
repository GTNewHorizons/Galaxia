package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

/** Self-contained recipe data used by recipe books and production modules. */
public record RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, List<Resource> itemInputs,
    List<Resource> itemOutputs, List<Resource> fluidInputs, List<Resource> fluidOutputs, int duration, int eut) {

    public RecipeSnapshot {
        if (duration < 0) duration = 0;
        if (eut < 0) eut = 0;
        itemInputs = immutable(itemInputs);
        itemOutputs = immutable(itemOutputs);
        fluidInputs = immutable(fluidInputs);
        fluidOutputs = immutable(fluidOutputs);
    }

    /** Creates an identity-only snapshot. Recipe books reject it until server content resolution completes. */
    public static RecipeSnapshot unresolved(byte recipeMapOrdinal, int recipeIndex, long contentHash) {
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            0,
            0);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int duration, int eut) {
        return resolved(recipeMapOrdinal, recipeIndex, inputs, outputs, fluidInputs, fluidOutputs, null, duration, eut);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances, int duration,
        int eut) {
        return resolved(
            recipeMapOrdinal,
            recipeIndex,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            null,
            duration,
            eut);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances,
        int[] fluidOutputChances, int duration, int eut) {
        List<Resource> resolvedItemInputs = itemResources(inputs, null);
        List<Resource> resolvedItemOutputs = itemResources(outputs, outputChances);
        List<Resource> resolvedFluidInputs = fluidResources(fluidInputs, null);
        List<Resource> resolvedFluidOutputs = fluidResources(fluidOutputs, fluidOutputChances);
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            computeContentHash(
                resolvedItemInputs,
                resolvedItemOutputs,
                resolvedFluidInputs,
                resolvedFluidOutputs,
                duration,
                eut),
            resolvedItemInputs,
            resolvedItemOutputs,
            resolvedFluidInputs,
            resolvedFluidOutputs,
            duration,
            eut);
    }

    void validateForBook() {
        if (Byte.toUnsignedInt(recipeMapOrdinal) == 0) {
            throw new IllegalArgumentException("Recipe map ordinal must identify a supported map");
        }
        if (recipeIndex < 0) throw new IllegalArgumentException("Recipe index must be non-negative");
        if (duration <= 0) throw new IllegalArgumentException("Recipe duration must be positive");
        validateKind(itemInputs, ItemStackWrapper.class, "itemInputs");
        validateKind(itemOutputs, ItemStackWrapper.class, "itemOutputs");
        validateKind(fluidInputs, FluidKey.class, "fluidInputs");
        validateKind(fluidOutputs, FluidKey.class, "fluidOutputs");
        validateInputs(itemInputs, "itemInputs");
        validateInputs(fluidInputs, "fluidInputs");
        validateOutputs(itemOutputs, "itemOutputs");
        validateOutputs(fluidOutputs, "fluidOutputs");
        if (itemInputs.isEmpty() && itemOutputs.isEmpty() && fluidInputs.isEmpty() && fluidOutputs.isEmpty()) {
            throw new IllegalArgumentException("Recipe snapshot has no resolved content");
        }
        long expectedHash = computeContentHash(itemInputs, itemOutputs, fluidInputs, fluidOutputs, duration, eut);
        if (contentHash != expectedHash) {
            throw new IllegalArgumentException("Recipe snapshot content hash does not match resolved content");
        }
    }

    private static List<Resource> immutable(@Nullable List<Resource> resources) {
        return resources == null ? List.of() : List.copyOf(resources);
    }

    private static List<Resource> itemResources(@Nullable ItemStack[] stacks, @Nullable int[] chances) {
        validateChanceCount(stacks == null ? 0 : stacks.length, chances);
        if (stacks == null || stacks.length == 0) return List.of();
        List<Resource> resources = new ArrayList<>(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            ItemStackWrapper key = ItemStackWrapper.of(stack);
            long amount = stack == null ? 0L : stack.stackSize;
            resources.add(chances == null ? new Resource(key, amount) : new Resource(key, amount, chances[i]));
        }
        return List.copyOf(resources);
    }

    private static List<Resource> fluidResources(@Nullable FluidStack[] stacks, @Nullable int[] chances) {
        validateChanceCount(stacks == null ? 0 : stacks.length, chances);
        if (stacks == null || stacks.length == 0) return List.of();
        List<Resource> resources = new ArrayList<>(stacks.length);
        for (int i = 0; i < stacks.length; i++) {
            FluidStack stack = stacks[i];
            FluidKey key = stack == null ? null : FluidKey.of(stack);
            long amount = stack == null ? 0L : stack.amount;
            resources.add(chances == null ? new Resource(key, amount) : new Resource(key, amount, chances[i]));
        }
        return List.copyOf(resources);
    }

    private static void validateChanceCount(int resourceCount, @Nullable int[] chances) {
        if (chances != null && chances.length != resourceCount) {
            throw new IllegalArgumentException("Recipe output chance count does not match outputs");
        }
    }

    private static void validateKind(List<Resource> resources, Class<? extends InventoryKey> kind, String field) {
        for (Resource resource : resources) {
            if (!kind.isInstance(resource.key())) {
                throw new IllegalArgumentException("Recipe " + field + " contains the wrong resource type");
            }
        }
    }

    private static void validateInputs(List<Resource> resources, String field) {
        for (Resource resource : resources) {
            if (resource.hasChance()) {
                throw new IllegalArgumentException("Recipe " + field + " must not contain output chances");
            }
        }
    }

    private static void validateOutputs(List<Resource> resources, String field) {
        if (resources.isEmpty()) return;
        boolean hasChance = resources.get(0)
            .hasChance();
        for (Resource resource : resources) {
            if (hasChance != resource.hasChance()) {
                throw new IllegalArgumentException("Recipe " + field + " mixes present and absent output chances");
            }
        }
    }

    private static long computeContentHash(List<Resource> itemInputs, List<Resource> itemOutputs,
        List<Resource> fluidInputs, List<Resource> fluidOutputs, int duration, int eut) {
        long hash = 1L;
        hash = hashItems(hash, itemInputs);
        hash = hashItems(hash, itemOutputs);
        hash = hashChances(hash, itemOutputs);
        hash = hashFluids(hash, fluidInputs);
        hash = hashFluids(hash, fluidOutputs);
        hash = hashChances(hash, fluidOutputs);
        hash = hash * 31 + duration;
        return hash * 31 + eut;
    }

    private static long hashItems(long hash, List<Resource> resources) {
        for (Resource resource : resources) {
            ItemStackWrapper item = (ItemStackWrapper) resource.key();
            hash = hash * 31 + Item.getIdFromItem(item.item());
            hash = hash * 31 + item.meta();
            hash = hash * 31 + resource.amount();
            hash = hash * 31 + Objects.hashCode(item.nbt());
        }
        return hash;
    }

    private static long hashFluids(long hash, List<Resource> resources) {
        for (Resource resource : resources) {
            FluidKey fluid = (FluidKey) resource.key();
            Fluid fluidType = fluid.fluid();
            hash = hash * 31 + (fluidType == null ? 0
                : fluidType.getName()
                    .hashCode());
            hash = hash * 31 + resource.amount();
            hash = hash * 31 + Objects.hashCode(fluid.tag());
        }
        return hash;
    }

    private static long hashChances(long hash, List<Resource> resources) {
        for (Resource resource : resources) {
            if (resource.hasChance()) hash = hash * 31 + resource.effectiveChance();
        }
        return hash;
    }

    public static final class Resource {

        private static final int NO_CHANCE = -1;
        private static final int MAX_CHANCE = 10_000;

        private final InventoryKey key;
        private final long amount;
        private final int chance;

        public Resource(InventoryKey key, long amount) {
            this(key, amount, NO_CHANCE, true);
        }

        public Resource(InventoryKey key, long amount, int chance) {
            this(key, amount, chance, false);
        }

        private Resource(InventoryKey key, long amount, int chance, boolean absentChance) {
            this.key = validKey(key);
            this.amount = validAmount(amount);
            this.chance = validChance(chance, absentChance);
        }

        private static InventoryKey validKey(InventoryKey key) {
            if (key == null) throw new IllegalArgumentException("Recipe resource key must not be null");
            if ((key instanceof ItemStackWrapper item && item.item() == null)
                || (key instanceof FluidKey fluid && fluid.fluid() == null)) {
                throw new IllegalArgumentException("Recipe resource key must identify an item or fluid");
            }
            return key;
        }

        private static long validAmount(long amount) {
            if (amount <= 0L || amount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Recipe resource amount must be between 1 and 2147483647");
            }
            return amount;
        }

        private static int validChance(int chance, boolean absentChance) {
            if (absentChance) return NO_CHANCE;
            if (chance < 0 || chance > MAX_CHANCE) {
                throw new IllegalArgumentException("Recipe resource chance must be between 0 and 10000");
            }
            return chance;
        }

        public InventoryKey key() {
            return key;
        }

        public long amount() {
            return amount;
        }

        public boolean hasChance() {
            return chance != NO_CHANCE;
        }

        public int effectiveChance() {
            return hasChance() ? chance : MAX_CHANCE;
        }

        public @Nullable ItemStack itemStack() {
            return key instanceof ItemStackWrapper item ? item.toStack((int) amount) : null;
        }

        public @Nullable FluidStack fluidStack() {
            return key instanceof FluidKey fluid ? fluid.toStack((int) amount) : null;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Resource resource)) return false;
            return amount == resource.amount && chance == resource.chance && key.equals(resource.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, amount, chance);
        }
    }
}
