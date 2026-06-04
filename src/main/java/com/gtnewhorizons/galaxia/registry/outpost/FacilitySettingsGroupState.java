package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.List;
import java.util.function.Consumer;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroupRegistry;

final class FacilitySettingsGroupState {

    private final SettingsGroupRegistry registry = new SettingsGroupRegistry();

    SettingsGroupRegistry registry() {
        return registry;
    }

    void applyGroupsToModules(List<ModuleInstance> modules) {
        for (SettingsGroup group : registry.groups()
            .values()) {
            applySettingsToGroup(group, modules);
        }
    }

    void syncRecipeGroupsFromModules(List<ModuleInstance> modules) {
        for (SettingsGroup group : registry.groups()
            .values()) {
            if (!(group.settings() instanceof RecipeModuleSettings recipeSettings)) continue;
            for (StationTileCoord coord : group.members()) {
                ModuleInstance module = moduleAtAnchor(modules, coord);
                if (module != null && module.component() instanceof IRecipeModule recipeModule) {
                    recipeSettings.setConfig(recipeModule.getRecipeConfig());
                    break;
                }
            }
        }
    }

    void attachPrivateGroupIfSupported(ModuleInstance module, Consumer<ModuleInstance.ID> dirtyModuleSink) {
        if (FacilityModuleRegistry.get(module.kind())
            .settingsGroups() && module.groupId() == 0) {
            attach(module, registry.create(module.kind(), privateSettingsFor(module)), dirtyModuleSink);
        }
    }

    void attach(ModuleInstance module, SettingsGroup group, Consumer<ModuleInstance.ID> dirtyModuleSink) {
        registry.require(group.id(), module.kind());
        registry.addMember(group.id(), module.anchor());
        module.setGroupId(group.id());
        applySettingsToModule(group.settings(), module);
        dirtyModuleSink.accept(module.id);
    }

    void detach(ModuleInstance module) {
        if (module.groupId() == 0) return;
        short oldGroupId = module.groupId();
        registry.removeMember(oldGroupId, module.anchor());
        module.setGroupId((short) 0);
    }

    ModuleSettings copySettings(ModuleInstance module) {
        requireSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup group = registry.require(module.groupId(), module.kind());
            return module.component()
                .copySettings(module, group.settings());
        }
        return module.component()
            .createPrivateSettings(module);
    }

    ModuleSettings privateSettingsFor(ModuleInstance module) {
        requireSupported(module);
        return module.component()
            .createPrivateSettings(module);
    }

    void markMembersDirty(SettingsGroup group, List<ModuleInstance> modules,
        Consumer<ModuleInstance.ID> dirtyModuleSink) {
        for (StationTileCoord coord : group.members()) {
            ModuleInstance module = moduleAtAnchor(modules, coord);
            if (module != null) {
                dirtyModuleSink.accept(module.id);
            }
        }
    }

    void applySettingsToGroup(SettingsGroup group, List<ModuleInstance> modules) {
        for (StationTileCoord coord : group.members()) {
            ModuleInstance module = moduleAtAnchor(modules, coord);
            if (module != null) {
                applySettingsToModule(group.settings(), module);
            }
        }
    }

    static void requireSupported(ModuleInstance module) {
        if (module == null) {
            throw new IllegalArgumentException("Settings group module must not be null");
        }
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            throw new IllegalStateException("Settings groups are not supported for module kind " + module.kind());
        }
    }

    private static ModuleInstance moduleAtAnchor(List<ModuleInstance> modules, StationTileCoord coord) {
        if (coord == null) return null;
        for (ModuleInstance module : modules) {
            if (coord.equals(module.anchorOrNull())) return module;
        }
        return null;
    }

    static void applySettingsToModule(ModuleSettings settings, ModuleInstance module) {
        module.component()
            .applySettings(module, settings);
    }
}
