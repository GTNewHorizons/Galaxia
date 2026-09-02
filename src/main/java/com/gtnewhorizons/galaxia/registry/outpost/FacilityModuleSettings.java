package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class FacilityModuleSettings {

    record Outcome(boolean changed, Set<ModuleInstance.ID> affectedModuleIds) {

        Outcome {
            affectedModuleIds = Set.copyOf(affectedModuleIds);
        }

        static Outcome changed(Collection<ModuleInstance.ID> affectedModuleIds) {
            return new Outcome(true, Set.copyOf(affectedModuleIds));
        }

        static Outcome unchanged() {
            return new Outcome(false, Set.of());
        }
    }

    private final List<ModuleInstance> modules;
    private final Map<SettingsGroup.ID, SettingsGroup> groups = new LinkedHashMap<>();
    private long nextGroupId = 1;

    FacilityModuleSettings(List<ModuleInstance> modules) {
        if (modules == null) throw new IllegalArgumentException("Module list must not be null");
        this.modules = modules;
    }

    void restore(Collection<SettingsGroup> restoredGroups) {
        if (restoredGroups == null) throw new IllegalArgumentException("Module settings restore is null");
        Map<SettingsGroup.ID, SettingsGroup> candidates = new LinkedHashMap<>();
        for (SettingsGroup group : restoredGroups) {
            if (group == null || candidates.put(group.id(), group) != null) {
                throw new IllegalArgumentException("Duplicate or null restored settings group");
            }
        }
        Set<ModuleInstance.ID> moduleIds = new LinkedHashSet<>();
        Set<SettingsGroup.ID> populatedGroups = new LinkedHashSet<>();
        for (ModuleInstance module : modules) {
            if (module == null || !moduleIds.add(module.id)) {
                throw new IllegalArgumentException("Duplicate or null module during settings restore");
            }
            ModuleInstance.SettingsBinding binding = module.settingsBinding();
            if (supports(module) != (binding != null)) {
                throw new IllegalArgumentException("Module does not have exactly one settings binding: " + module.id);
            }
            if (binding == null) continue;
            validateSettings(module, effectiveSettings(module, binding, candidates));
            if (binding instanceof ModuleInstance.SettingsBinding.Shared shared) {
                SettingsGroup group = candidates.get(shared.groupId());
                if (group == null || group.kind() != module.kind()) {
                    throw new IllegalArgumentException("Invalid shared settings binding for module " + module.id);
                }
                populatedGroups.add(shared.groupId());
            }
        }
        if (!populatedGroups.equals(candidates.keySet())) {
            throw new IllegalArgumentException("Restored settings groups must each have a member");
        }
        for (ModuleInstance module : modules) {
            if (module.settingsBinding() != null) applySettings(module, effectiveSettings(module, candidates));
        }
        groups.clear();
        groups.putAll(candidates);
        nextGroupId = candidates.keySet()
            .stream()
            .mapToInt(SettingsGroup.ID::value)
            .max()
            .orElse(0) + 1L;
    }

    boolean supports(ModuleInstance module) {
        return module != null && FacilityModuleRegistry.get(module.kind())
            .settingsGroups();
    }

    void attach(ModuleInstance module, @Nullable ModuleInstance.SettingsBinding preparedBinding) {
        requireSupported(module);
        if (module.settingsBinding() != null) {
            throw new IllegalStateException("Module already has a settings binding: " + module.id);
        }
        ModuleInstance.SettingsBinding binding = preparedBinding == null
            ? new ModuleInstance.SettingsBinding.Private(captureSettings(module))
            : preparedBinding;
        validateSettings(module, effectiveSettings(module, binding, groups));
        module.setSettingsBinding(binding);
    }

    ModuleInstance.SettingsBinding prepareAttachment(ModuleInstance target, @Nullable ModuleInstance copySource,
        @Nullable SettingsGroup.ID requestedGroupId) {
        requireSupported(target);
        ModuleInstance.SettingsBinding binding;
        if (copySource != null) {
            requireSupported(copySource);
            if (copySource.kind() != target.kind()) {
                throw new IllegalArgumentException("Build settings source kind does not match target kind");
            }
            ModuleSettings settings = effectiveSettings(copySource.id);
            copySource.component()
                .settingsCopyWouldChange(copySource, target);
            validateSettings(target, settings);
            applySettings(target, settings);
            target.component()
                .applySettingsCopy(copySource, target);
            binding = new ModuleInstance.SettingsBinding.Private(settings);
        } else if (requestedGroupId != null) {
            SettingsGroup group = requireGroup(requestedGroupId);
            if (group.kind() != target.kind()) {
                throw new IllegalArgumentException("Build settings group kind does not match target kind");
            }
            validateSettings(target, group.settings());
            applySettings(target, group.settings());
            binding = new ModuleInstance.SettingsBinding.Shared(requestedGroupId);
        } else {
            ModuleSettings settings = captureSettings(target);
            validateSettings(target, settings);
            binding = new ModuleInstance.SettingsBinding.Private(settings);
        }
        return binding;
    }

    boolean canJoin(com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind kind,
        SettingsGroup.ID groupId) {
        SettingsGroup group = groupId == null ? null : groups.get(groupId);
        return group != null && group.kind() == kind;
    }

    void remove(ModuleInstance.ID moduleId) {
        ModuleInstance module = module(moduleId);
        if (module == null) return;
        SettingsGroup.ID groupId = sharedGroupId(module);
        if (groupId != null && membersOf(groupId).size() == 1) groups.remove(groupId);
    }

    ModuleSettings effectiveSettings(ModuleInstance.ID moduleId) {
        return effectiveSettings(requireModule(moduleId), groups);
    }

    RecipeBook recipeBook(ModuleInstance.ID moduleId) {
        ModuleSettings settings = effectiveSettings(moduleId);
        if (settings instanceof RecipeBook recipeBook) return recipeBook;
        throw new IllegalStateException("Module has no recipe book: " + moduleId);
    }

    Outcome createGroup(ModuleInstance module, String displayName) {
        requireSupported(module);
        ModuleSettings settings = effectiveSettings(module.id);
        SettingsGroup.ID groupId = nextGroupId();
        SettingsGroup.ID oldGroupId = sharedGroupId(module);
        groups.put(groupId, new SettingsGroup(groupId, module.kind(), displayName, settings));
        module.setSettingsBinding(new ModuleInstance.SettingsBinding.Shared(groupId));
        removeGroupIfEmpty(oldGroupId);
        return Outcome.changed(Set.of(module.id));
    }

    Outcome renameGroup(SettingsGroup.ID groupId, String displayName) {
        SettingsGroup current = requireGroup(groupId);
        SettingsGroup renamed = current.withDisplayName(displayName);
        if (renamed.equals(current)) return Outcome.unchanged();
        groups.put(groupId, renamed);
        return Outcome.changed(Set.of());
    }

    Outcome joinGroup(ModuleInstance module, SettingsGroup.ID groupId) {
        requireSupported(module);
        SettingsGroup group = requireGroup(groupId);
        if (group.kind() != module.kind()) throw new IllegalArgumentException("Settings group kind does not match");
        SettingsGroup.ID oldGroupId = sharedGroupId(module);
        if (groupId.equals(oldGroupId)) return Outcome.unchanged();
        validateSettings(module, group.settings());
        applySettings(module, group.settings());
        module.setSettingsBinding(new ModuleInstance.SettingsBinding.Shared(groupId));
        removeGroupIfEmpty(oldGroupId);
        return Outcome.changed(Set.of(module.id));
    }

    Outcome leaveGroup(ModuleInstance module) {
        requireSupported(module);
        SettingsGroup.ID groupId = sharedGroupId(module);
        if (groupId == null) return Outcome.unchanged();
        ModuleSettings settings = requireGroup(groupId).settings();
        validateSettings(module, settings);
        module.setSettingsBinding(new ModuleInstance.SettingsBinding.Private(settings));
        removeGroupIfEmpty(groupId);
        return Outcome.changed(Set.of(module.id));
    }

    Outcome copySettings(ModuleInstance source, List<ModuleInstance> targets) {
        if (source == null || targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Module settings copy targets are missing");
        }
        requireSupported(source);
        ModuleSettings sourceSettings = effectiveSettings(source.id);
        Set<ModuleInstance.ID> targetIds = new LinkedHashSet<>();
        Set<ModuleInstance.ID> changedTargetIds = new LinkedHashSet<>();
        for (ModuleInstance target : targets) {
            requireSupported(target);
            if (!targetIds.add(target.id) || source.id.equals(target.id) || source.kind() != target.kind()) {
                throw new IllegalArgumentException("Invalid module settings copy target " + target.id);
            }
            boolean subtypeChanged = source.component()
                .settingsCopyWouldChange(source, target);
            validateSettings(target, sourceSettings);
            if (copyWouldChange(target, sourceSettings, subtypeChanged)) changedTargetIds.add(target.id);
        }
        if (changedTargetIds.isEmpty()) return Outcome.unchanged();
        applyChangedCopies(source, targets, sourceSettings, changedTargetIds);
        return Outcome.changed(changedTargetIds);
    }

    private boolean copyWouldChange(ModuleInstance target, ModuleSettings sourceSettings, boolean subtypeChanged) {
        return !(target.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private)
            || !sourceSettings.equals(effectiveSettings(target.id))
            || subtypeChanged;
    }

    private void applyChangedCopies(ModuleInstance source, List<ModuleInstance> targets, ModuleSettings sourceSettings,
        Set<ModuleInstance.ID> changedTargetIds) {
        for (ModuleInstance target : targets) {
            if (!changedTargetIds.contains(target.id)) continue;
            applySettings(target, sourceSettings);
            target.component()
                .applySettingsCopy(source, target);
        }
        Set<SettingsGroup.ID> oldGroups = new LinkedHashSet<>();
        for (ModuleInstance target : targets) {
            if (!changedTargetIds.contains(target.id)) continue;
            SettingsGroup.ID oldGroupId = sharedGroupId(target);
            if (oldGroupId != null) oldGroups.add(oldGroupId);
            target.setSettingsBinding(new ModuleInstance.SettingsBinding.Private(sourceSettings));
        }
        oldGroups.forEach(this::removeGroupIfEmpty);
    }

    boolean canCopySettings(ModuleInstance source, ModuleInstance target) {
        try {
            requireSupported(source);
            requireSupported(target);
            if (source.id.equals(target.id) || source.kind() != target.kind()) return false;
            ModuleSettings settings = effectiveSettings(source.id);
            source.component()
                .settingsCopyWouldChange(source, target);
            validateSettings(target, settings);
            return true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return false;
        }
    }

    Outcome replaceEffectiveSettings(ModuleInstance module, ModuleSettings replacement) {
        requireSupported(module);
        validateSettings(module, replacement);
        ModuleSettings current = effectiveSettings(module.id);
        if (current.equals(replacement)) return Outcome.unchanged();
        SettingsGroup.ID groupId = sharedGroupId(module);
        if (groupId == null) {
            applySettings(module, replacement);
            module.setSettingsBinding(new ModuleInstance.SettingsBinding.Private(replacement));
            return Outcome.changed(Set.of(module.id));
        }
        SettingsGroup group = requireGroup(groupId);
        List<ModuleInstance> members = members(groupId);
        for (ModuleInstance member : members) validateSettings(member, replacement);
        for (ModuleInstance member : members) applySettings(member, replacement);
        groups.put(groupId, group.withSettings(replacement));
        return Outcome.changed(
            members.stream()
                .map(member -> member.id)
                .toList());
    }

    List<SettingsGroup> groups() {
        return List.copyOf(groups.values());
    }

    @Nullable
    SettingsGroup group(SettingsGroup.ID groupId) {
        return groupId == null ? null : groups.get(groupId);
    }

    List<ModuleInstance> members(SettingsGroup.ID groupId) {
        return modules.stream()
            .filter(module -> groupId != null && groupId.equals(sharedGroupId(module)))
            .toList();
    }

    private void requireSupported(ModuleInstance module) {
        if (!supports(module)) throw new IllegalArgumentException("Module does not support facility settings");
    }

    private ModuleInstance requireModule(ModuleInstance.ID moduleId) {
        ModuleInstance module = module(moduleId);
        if (module == null) throw new IllegalStateException("Missing settings module " + moduleId);
        return module;
    }

    private @Nullable ModuleInstance module(ModuleInstance.ID moduleId) {
        if (moduleId == null) return null;
        for (ModuleInstance module : modules) if (moduleId.equals(module.id)) return module;
        return null;
    }

    private ModuleSettings captureSettings(ModuleInstance module) {
        if (module.recipe() != null) return RecipeBook.empty();
        return module.component()
            .captureModuleSettings(module);
    }

    private void validateSettings(ModuleInstance module, ModuleSettings settings) {
        if (module.recipe() != null) {
            if (!(settings instanceof RecipeBook)) {
                throw new IllegalStateException("Recipe module received non-recipe settings for module " + module.id);
            }
            return;
        }
        module.component()
            .validateModuleSettings(module, settings);
    }

    private void applySettings(ModuleInstance module, ModuleSettings settings) {
        validateSettings(module, settings);
        if (module.recipe() != null) return;
        module.component()
            .applyModuleSettings(module, settings);
    }

    private ModuleSettings effectiveSettings(ModuleInstance module, Map<SettingsGroup.ID, SettingsGroup> ownerGroups) {
        ModuleInstance.SettingsBinding binding = module.settingsBinding();
        if (binding == null) throw new IllegalStateException("Module has no settings binding: " + module.id);
        return effectiveSettings(module, binding, ownerGroups);
    }

    private ModuleSettings effectiveSettings(ModuleInstance module, ModuleInstance.SettingsBinding binding,
        Map<SettingsGroup.ID, SettingsGroup> ownerGroups) {
        if (binding instanceof ModuleInstance.SettingsBinding.Private privateBinding) return privateBinding.settings();
        SettingsGroup.ID groupId = ((ModuleInstance.SettingsBinding.Shared) binding).groupId();
        SettingsGroup group = ownerGroups.get(groupId);
        if (group == null || group.kind() != module.kind()) {
            throw new IllegalStateException("Invalid settings group " + groupId + " for module " + module.id);
        }
        return group.settings();
    }

    private SettingsGroup requireGroup(SettingsGroup.ID groupId) {
        SettingsGroup group = group(groupId);
        if (group == null) throw new IllegalStateException("Missing settings group " + groupId);
        return group;
    }

    private SettingsGroup.ID nextGroupId() {
        if (nextGroupId > Integer.MAX_VALUE) throw new IllegalStateException("Settings group ID space exhausted");
        return new SettingsGroup.ID((int) nextGroupId++);
    }

    private @Nullable SettingsGroup.ID sharedGroupId(ModuleInstance module) {
        return module.settingsBinding() instanceof ModuleInstance.SettingsBinding.Shared shared ? shared.groupId()
            : null;
    }

    private void removeGroupIfEmpty(@Nullable SettingsGroup.ID groupId) {
        if (groupId != null && membersOf(groupId).isEmpty()) groups.remove(groupId);
    }

    private Set<ModuleInstance.ID> membersOf(SettingsGroup.ID groupId) {
        Set<ModuleInstance.ID> memberIds = new LinkedHashSet<>();
        for (ModuleInstance member : members(groupId)) memberIds.add(member.id);
        return memberIds;
    }
}
