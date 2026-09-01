package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

public record RecipeBook(List<SavedRecipe> recipes, RecipeSchedulerMode mode, NotDoablePolicy notDoablePolicy) {

    public static final int MAX_RECIPES = 32;

    public RecipeBook {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(notDoablePolicy, "notDoablePolicy");
        if (recipes.size() > MAX_RECIPES) {
            throw new IllegalArgumentException("Recipe book exceeds " + MAX_RECIPES + " recipes");
        }
        for (SavedRecipe recipe : recipes) {
            Objects.requireNonNull(recipe, "recipe");
            recipe.recipe()
                .validateForBook();
        }
        recipes = List.copyOf(recipes);
    }

    public static RecipeBook empty() {
        return new RecipeBook(List.of(), RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP);
    }

    public Optional<Selection> select(ScheduleState state, Random random) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(random, "random");
        int index = switch (mode) {
            case PRIORITY -> selectPriority();
            case ORDER -> selectOrder(state);
            case RANDOM -> selectRandom(random);
        };
        return index < 0 ? Optional.empty() : Optional.of(new Selection(index, recipes.get(index)));
    }

    public ScheduleState advanceAfterSuccess(ScheduleState state, Selection selection) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(selection, "selection");
        if (selection.index() >= recipes.size() || !recipes.get(selection.index())
            .equals(selection.recipe())) {
            throw new IllegalArgumentException("Selection does not belong to this recipe book");
        }
        if (mode != RecipeSchedulerMode.ORDER) return state;
        if (state.orderRemaining() > 1 && state.orderCursor() == selection.index()) {
            return new ScheduleState(state.orderCursor(), (byte) (state.orderRemaining() - 1));
        }
        int next = nextEnabledAfter(state.orderCursor());
        if (next < 0) return state;
        return new ScheduleState(
            (byte) next,
            recipes.get(next)
                .orderSize());
    }

    private int selectPriority() {
        int selected = -1;
        byte priority = -1;
        for (int i = 0; i < recipes.size(); i++) {
            SavedRecipe recipe = recipes.get(i);
            if (recipe.enabled() && recipe.priority() > priority) {
                selected = i;
                priority = recipe.priority();
            }
        }
        return selected;
    }

    private int selectOrder(ScheduleState state) {
        if (recipes.isEmpty()) return -1;
        int cursor = state.orderCursor();
        if (state.orderRemaining() > 0 && cursor < recipes.size()
            && recipes.get(cursor)
                .enabled()) {
            return cursor;
        }
        return nextEnabledAfter(cursor);
    }

    private int selectRandom(Random random) {
        int[] enabled = new int[recipes.size()];
        int count = 0;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i)
                .enabled()) enabled[count++] = i;
        }
        return count == 0 ? -1 : enabled[random.nextInt(count)];
    }

    private int nextEnabledAfter(int cursor) {
        int size = recipes.size();
        if (size == 0) return -1;
        for (int i = 1; i <= size; i++) {
            int index = (cursor + i) % size;
            if (recipes.get(index)
                .enabled()) return index;
        }
        return -1;
    }

    public record Selection(int index, SavedRecipe recipe) {

        public Selection {
            if (index < 0 || index >= MAX_RECIPES) {
                throw new IllegalArgumentException("Recipe selection index out of range: " + index);
            }
            Objects.requireNonNull(recipe, "recipe");
        }
    }

    public record ScheduleState(byte orderCursor, byte orderRemaining) {

        public static final ScheduleState RESET = new ScheduleState((byte) 0, (byte) 0);

        public ScheduleState {
            if (orderCursor < 0 || orderCursor >= MAX_RECIPES) {
                throw new IllegalArgumentException("orderCursor must be in [0, " + MAX_RECIPES + ")");
            }
            if (orderRemaining < 0) {
                throw new IllegalArgumentException("orderRemaining must be >= 0: " + orderRemaining);
            }
        }
    }

    public sealed interface Owner permits Owner.Private,Owner.Group {

        record Private(ModuleInstance.ID moduleId) implements Owner {

            public Private {
                Objects.requireNonNull(moduleId, "moduleId");
            }
        }

        record Group(SettingsGroup.ID groupId) implements Owner {

            public Group {
                Objects.requireNonNull(groupId, "groupId");
            }
        }
    }
}
