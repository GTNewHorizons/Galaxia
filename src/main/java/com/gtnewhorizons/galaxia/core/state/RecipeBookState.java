package com.gtnewhorizons.galaxia.core.state;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

public final class RecipeBookState {

    private static final String PATH = "recipeBook";
    private static final int MAX_RECIPE_RESOURCES = 64;
    private static final int MAX_DISPLAY_NAME_BYTES = 1024;

    private RecipeBookState() {}

    public static NBTTagCompound encode(RecipeBook book) {
        if (book == null) throw fail(PATH, "must not be null");
        NBTTagCompound out = new NBTTagCompound();
        out.setString(
            "mode",
            book.mode()
                .name());
        out.setString(
            "notDoablePolicy",
            book.notDoablePolicy()
                .name());
        NBTTagList recipes = new NBTTagList();
        for (SavedRecipe saved : book.recipes()) recipes.appendTag(encodeRecipe(saved));
        out.setTag("recipes", recipes);
        return out;
    }

    public static RecipeBook decode(NBTTagCompound encoded) {
        return decode(new NbtReader(encoded, PATH));
    }

    static RecipeBook decode(NbtReader in) {
        RecipeSchedulerMode mode = in.enumValue(RecipeSchedulerMode.class, "mode");
        NotDoablePolicy policy = in.enumValue(NotDoablePolicy.class, "notDoablePolicy");
        NBTTagList recipeTags = in.compounds("recipes");
        if (recipeTags.tagCount() > RecipeBook.MAX_RECIPES) throw fail(in.path() + ".recipes", "too many recipes");
        List<SavedRecipe> recipes = new ArrayList<>(recipeTags.tagCount());
        for (int i = 0; i < recipeTags.tagCount(); i++) {
            recipes.add(decodeRecipe(in.element("recipes", i, recipeTags.getCompoundTagAt(i))));
        }
        try {
            return new RecipeBook(recipes, mode, policy);
        } catch (RuntimeException ex) {
            throw fail(in.path(), "invalid recipe book", ex);
        }
    }

    private static NBTTagCompound encodeRecipe(SavedRecipe saved) {
        NBTTagCompound tag = new NBTTagCompound();
        RecipeSnapshot recipe = saved.recipe();
        tag.setInteger("map", recipe.recipeMapOrdinal() & 0xFF);
        tag.setInteger("index", recipe.recipeIndex());
        tag.setLong("hash", recipe.contentHash());
        validateRecipeResources(
            recipe.itemInputs(),
            recipe.itemOutputs(),
            recipe.fluidInputs(),
            recipe.fluidOutputs(),
            PATH);
        writeResources(tag, "itemInputs", recipe.itemInputs());
        writeResources(tag, "itemOutputs", recipe.itemOutputs());
        writeResources(tag, "fluidInputs", recipe.fluidInputs());
        writeResources(tag, "fluidOutputs", recipe.fluidOutputs());
        tag.setInteger("duration", recipe.duration());
        tag.setInteger("eut", recipe.eut());
        tag.setBoolean("enabled", saved.enabled());
        tag.setLong("requestAmount", saved.requestAmount());
        tag.setInteger("priority", saved.priority() & 0xFF);
        tag.setInteger("orderSize", saved.orderSize() & 0xFF);
        tag.setString("displayName", displayName(saved.displayName(), PATH + ".displayName"));
        return tag;
    }

    private static SavedRecipe decodeRecipe(NbtReader in) {
        int map = in.integer("map");
        int priority = in.integer("priority");
        int orderSize = in.integer("orderSize");
        if (map < 1 || map > 255) throw fail(in.path() + ".map", "must be between 1 and 255");
        if (priority < 0 || priority > Byte.MAX_VALUE) throw fail(in.path() + ".priority", "out of range");
        if (orderSize < 1 || orderSize > Byte.MAX_VALUE) throw fail(in.path() + ".orderSize", "out of range");
        try {
            int eut = in.integer("eut");
            if (eut < 0) throw fail(in.path() + ".eut", "must not be negative");
            validateItemMetadata(in, "itemInputs");
            validateItemMetadata(in, "itemOutputs");
            List<Resource> itemInputs = readResources(in, "itemInputs");
            List<Resource> itemOutputs = readResources(in, "itemOutputs");
            List<Resource> fluidInputs = readResources(in, "fluidInputs");
            List<Resource> fluidOutputs = readResources(in, "fluidOutputs");
            validateRecipeResources(itemInputs, itemOutputs, fluidInputs, fluidOutputs, in.path());
            RecipeSnapshot snapshot = new RecipeSnapshot(
                (byte) map,
                in.integer("index"),
                in.longValue("hash"),
                itemInputs,
                itemOutputs,
                fluidInputs,
                fluidOutputs,
                in.integer("duration"),
                eut);
            return new SavedRecipe(
                snapshot,
                in.bool("enabled"),
                in.longValue("requestAmount"),
                (byte) priority,
                (byte) orderSize,
                displayName(in.string("displayName"), in.path() + ".displayName"));
        } catch (RuntimeException ex) {
            throw fail(in.path(), "invalid recipe", ex);
        }
    }

    private static void writeResources(NBTTagCompound target, String key, List<Resource> resources) {
        String path = PATH + "." + key;
        resourceCount(resources.size(), path);
        NBTTagList out = new NBTTagList();
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            NBTTagCompound encoded;
            try {
                encoded = InventoryKeyState.encode(resource.key());
            } catch (RuntimeException ex) {
                throw fail(path + "[" + i + "]", "invalid recipe resource", ex);
            }
            encoded.setLong("amount", resource.amount());
            if (resource.hasChance()) encoded.setInteger("chance", resource.effectiveChance());
            out.appendTag(encoded);
        }
        target.setTag(key, out);
    }

    private static List<Resource> readResources(NbtReader in, String key) {
        NBTTagList tags = in.compounds(key);
        resourceCount(tags.tagCount(), in.path() + "." + key);
        List<Resource> resources = new ArrayList<>(tags.tagCount());
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader resourceIn = in.element(key, i, tags.getCompoundTagAt(i));
            InventoryKey decoded = decodeKey(resourceIn);
            boolean hasChance = resourceIn.tag()
                .hasKey("chance");
            long amount = positiveLong(resourceIn, "amount");
            int chance = hasChance ? resourceIn.integer("chance") : -1;
            try {
                resources.add(hasChance ? new Resource(decoded, amount, chance) : new Resource(decoded, amount));
            } catch (RuntimeException ex) {
                throw fail(resourceIn.path(), "invalid recipe resource", ex);
            }
        }
        return List.copyOf(resources);
    }

    private static void validateItemMetadata(NbtReader in, String key) {
        NBTTagList tags = in.compounds(key);
        resourceCount(tags.tagCount(), in.path() + "." + key);
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader resourceIn = in.element(key, i, tags.getCompoundTagAt(i));
            NBTTagCompound stack = resourceIn.compound("stack")
                .tag();
            if (!stack.hasKey("Damage", NBT.TAG_SHORT) || stack.getShort("Damage") < 0) {
                throw fail(resourceIn.path() + ".stack.Damage", "invalid recipe item metadata");
            }
        }
    }

    private static void validateRecipeResources(List<Resource> itemInputs, List<Resource> itemOutputs,
        List<Resource> fluidInputs, List<Resource> fluidOutputs, String path) {
        validateItems(itemInputs, path + ".itemInputs");
        validateItems(itemOutputs, path + ".itemOutputs");
        validateFluids(fluidInputs, path + ".fluidInputs");
        validateFluids(fluidOutputs, path + ".fluidOutputs");
        validateInputs(itemInputs, path + ".itemInputs");
        validateInputs(fluidInputs, path + ".fluidInputs");
        validateOutputs(itemOutputs, path + ".itemOutputs");
        validateOutputs(fluidOutputs, path + ".fluidOutputs");
    }

    private static void validateItems(List<Resource> resources, String path) {
        for (int i = 0; i < resources.size(); i++) {
            if (!(resources.get(i)
                .key() instanceof ItemStackWrapper item)) throw fail(path + "[" + i + "]", "expected item");
            if (item.meta() < 0) throw fail(path + "[" + i + "].stack.Damage", "invalid recipe item metadata");
        }
    }

    private static void validateFluids(List<Resource> resources, String path) {
        for (int i = 0; i < resources.size(); i++) {
            if (!(resources.get(i)
                .key() instanceof FluidKey)) throw fail(path + "[" + i + "]", "expected fluid");
        }
    }

    private static void validateInputs(List<Resource> resources, String path) {
        for (int i = 0; i < resources.size(); i++) {
            if (resources.get(i)
                .hasChance()) throw fail(path + "[" + i + "].chance", "is not valid for an input");
        }
    }

    private static void validateOutputs(List<Resource> resources, String path) {
        if (resources.isEmpty()) return;
        boolean hasChance = resources.get(0)
            .hasChance();
        for (int i = 1; i < resources.size(); i++) {
            if (resources.get(i)
                .hasChance() != hasChance) {
                throw fail(path + "[" + i + "].chance", "must be present for either every output or no output");
            }
        }
    }

    private static InventoryKey decodeKey(NbtReader in) {
        try {
            return InventoryKeyState.decode(in.tag());
        } catch (RuntimeException ex) {
            throw fail(in.path(), "invalid inventory resource", ex);
        }
    }

    private static void resourceCount(int count, String path) {
        if (count > MAX_RECIPE_RESOURCES) throw fail(path, "has more than 64 entries");
    }

    private static String displayName(String value, String path) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_DISPLAY_NAME_BYTES) {
            throw fail(path, "exceeds 1024 UTF-8 bytes");
        }
        return value;
    }

    private static long positiveLong(NbtReader in, String key) {
        long value = in.longValue(key);
        if (value <= 0L) throw fail(in.path() + "." + key, "must be positive");
        return value;
    }

    private static IllegalStateException fail(String path, String message) {
        return new IllegalStateException("[STATE] " + path + ": " + message);
    }

    private static IllegalStateException fail(String path, String message, Throwable cause) {
        return new IllegalStateException("[STATE] " + path + ": " + message, cause);
    }
}
