package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Collections;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;

public interface IRecipeModule extends IModuleComponent {

    String getRecipeMapName();

    /**
     * Returns additional NEI recipe transfer idents beyond the main RecipeMap's
     * unlocalizedName. Override to support category-filtered NEI pages (e.g.
     * macerator recycling).
     */
    default List<String> getAdditionalNeiTransferIdents() {
        return Collections.emptyList();
    }

}
