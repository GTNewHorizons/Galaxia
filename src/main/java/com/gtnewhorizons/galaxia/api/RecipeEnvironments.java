package com.gtnewhorizons.galaxia.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.nei.RecipeDisplayInfo;

/** Conditions attached to recipes independently of their existing GT requirements. */
public final class RecipeEnvironments {

    private static final Map<String, Tag> TAGS = new LinkedHashMap<>();
    public static final Tag MICROGRAVITY = register(
        "galaxia:microgravity",
        "galaxia.environment.microgravity",
        c -> c.gravityG() < 0.01);
    public static final Tag NO_ATMOSPHERE = register(
        "galaxia:no_atmosphere",
        "galaxia.environment.no_atmosphere",
        c -> c.pressurePa() == 0);
    public static final Requirement NONE = new Requirement(List.of());
    private static final String NBT_KEY = "galaxiaRecipeEnvironment";
    private static final double FULL_ROOM_PRESSURE_PA = 101325;

    public static final RecipeMetadataKey<Requirement> REQUIREMENT = new RecipeMetadataKey<>(
        Requirement.class,
        "galaxia_environment") {

        @Override
        public void drawInfo(@Nonnull RecipeDisplayInfo info, @Nullable Object value) {
            Requirement requirement = cast(value, NONE);
            for (String id : requirement.tags()) {
                Tag tag = TAGS.get(id);
                info.drawText(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.environment.required",
                        StatCollector
                            .translateToLocal(tag == null ? "galaxia.environment.unknown" : tag.translationKey)));
            }
        }
    };

    private RecipeEnvironments() {}

    /** Registers a named condition with Galaxia during mod initialization. */
    public static Tag register(String id, String translationKey, Predicate<Conditions> condition) {
        if (id == null || id.isEmpty() || translationKey == null || condition == null) {
            throw new IllegalArgumentException("Environment tags require an id, translation key and condition");
        }
        Tag tag = new Tag(id, translationKey, condition);
        if (TAGS.putIfAbsent(id, tag) != null) throw new IllegalArgumentException("Duplicate environment tag: " + id);
        return tag;
    }

    /** Creates a standard GT recipe builder with immutable environment requirements. */
    public static GTRecipeBuilder requiresEnvironment(Tag... tags) {
        return GTRecipeBuilder.builder()
            .metadata(REQUIREMENT, Requirement.of(tags));
    }

    public static Requirement of(GTRecipe recipe) {
        return recipe.getMetadataOrDefault(REQUIREMENT, NONE);
    }

    public static boolean matches(Requirement requirement, @Nullable MetaTileEntity machine) {
        if (requirement.tags()
            .isEmpty()) return true;
        if (machine == null || machine.getBaseMetaTileEntity() == null) return false;
        var tile = machine.getBaseMetaTileEntity();
        return requirement.matches(at(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
    }

    /** Reads authored celestial facts and cached room membership. Unknown dimensions stay unknown. */
    @Nullable
    public static Conditions at(@Nullable World world, int x, int y, int z) {
        if (world == null || world.provider == null) return null;
        int dimension = world.provider.dimensionId;
        CelestialBodyProperties body = GalaxiaCelestialAPI.findByDimension(dimension)
            .map(object -> object.properties())
            .orElse(null);
        if (body == null) return null;
        boolean orbit = OrbitDimensions.isOrbit(dimension);
        double gravity = orbit ? 0 : body.localGravityG();
        double pressure = orbit ? 0 : body.surfacePressurePa();
        TileStation room = GalaxiaAPI.getStationContaining(world, dimension, x, y, z);
        if (room != null) pressure = room.getPressureLevel() * FULL_ROOM_PRESSURE_PA;
        return new Conditions(gravity, pressure);
    }

    /** Typed authoring handle. Evaluation stays in Galaxia rather than in recipe authors. */
    public static final class Tag {

        private final String id;
        private final String translationKey;
        private final Predicate<Conditions> condition;

        private Tag(String id, String translationKey, Predicate<Conditions> condition) {
            this.id = id;
            this.translationKey = translationKey;
            this.condition = condition;
        }
    }

    /** Implemented by the GT integration. Only active requirement identifiers accompany native machine state. */
    public interface Machine {

        void galaxia$setRequirement(Requirement requirement);
    }

    /** Physical conditions at the machine controller, with pressure distinct from breathable oxygen. */
    public record Conditions(double gravityG, double pressurePa) {

        public Conditions {
            if (!Double.isFinite(gravityG) || gravityG < 0 || !Double.isFinite(pressurePa) || pressurePa < 0) {
                throw new IllegalArgumentException("Environment facts must be finite and non-negative");
            }
        }
    }

    public record Requirement(List<String> tags) {

        public Requirement {
            tags = List.copyOf(tags);
        }

        public static Requirement of(Tag... tags) {
            return new Requirement(
                Arrays.stream(tags)
                    .map(tag -> tag.id)
                    .distinct()
                    .toList());
        }

        public boolean matches(@Nullable Conditions conditions) {
            if (tags.isEmpty()) return true;
            if (conditions == null) return false;
            for (String id : tags) {
                Tag tag = TAGS.get(id);
                if (tag == null || !tag.condition.test(conditions)) return false;
            }
            return true;
        }

        public void writeToNBT(NBTTagCompound nbt) {
            NBTTagList list = new NBTTagList();
            for (String id : tags) list.appendTag(new NBTTagString(id));
            nbt.setTag(NBT_KEY, list);
        }

        public static Requirement readFromNBT(NBTTagCompound nbt) {
            NBTTagList list = nbt.getTagList(NBT_KEY, Constants.NBT.TAG_STRING);
            var tags = new java.util.ArrayList<String>(list.tagCount());
            for (int i = 0; i < list.tagCount(); i++) tags.add(list.getStringTagAt(i));
            return new Requirement(tags);
        }
    }
}
