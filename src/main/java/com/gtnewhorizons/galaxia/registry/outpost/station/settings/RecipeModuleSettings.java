package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;

public final class RecipeModuleSettings implements ModuleSettings {

    private final RecipeBook book;

    public RecipeModuleSettings(RecipeBook book) {
        this.book = Objects.requireNonNull(book, "book");
    }

    public RecipeBook book() {
        return book;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecipeModuleSettings settings && book.equals(settings.book);
    }

    @Override
    public int hashCode() {
        return book.hashCode();
    }
}
