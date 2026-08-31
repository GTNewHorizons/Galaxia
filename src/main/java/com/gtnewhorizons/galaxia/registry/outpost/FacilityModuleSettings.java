package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class FacilityModuleSettings {

    record AttachmentPlan(ModuleInstance.ID moduleId, @Nullable SettingsGroup.ID sharedGroupId,
        ModuleSettings settings) {}

    enum Status {
        CHANGED,
        UNCHANGED,
        REJECTED
    }

    record Outcome(Status status, @Nullable FacilityCommand.Rejection rejection,
        Set<ModuleInstance.ID> affectedModuleIds) {

        Outcome {
            affectedModuleIds = Set.copyOf(affectedModuleIds);
        }

        static Outcome changed(Collection<ModuleInstance.ID> affectedModuleIds) {
            return new Outcome(Status.CHANGED, null, Set.copyOf(affectedModuleIds));
        }

        static Outcome unchanged() {
            return new Outcome(Status.UNCHANGED, null, Set.of());
        }

        static Outcome rejected(FacilityCommand.Rejection rejection) {
            return new Outcome(Status.REJECTED, rejection, Set.of());
        }
    }

    private final Map<ModuleInstance.ID, ModuleSettings> privateSettings = new LinkedHashMap<>();
    private final Map<SettingsGroup.ID, SettingsGroup> groups = new LinkedHashMap<>();
    private final Map<ModuleInstance.ID, SettingsGroup.ID> membership = new LinkedHashMap<>();
    private int nextGroupId = 1;

    FacilityModuleSettingsSnapshot snapshot() {
        return new FacilityModuleSettingsSnapshot(privateSettings, groups, membership);
    }

    void restore(FacilityModuleSettingsSnapshot restored, List<ModuleInstance> modules) {
        if (restored == null || modules == null) throw new IllegalArgumentException("Module settings restore is null");
        Map<ModuleInstance.ID, ModuleInstance> modulesById = new LinkedHashMap<>();
        for (ModuleInstance module : modules) {
            if (modulesById.put(module.id, module) != null) {
                throw new IllegalArgumentException("Duplicate module ID during settings restore: " + module.id);
            }
        }
        Map<SettingsGroup.ID, Integer> memberCounts = new LinkedHashMap<>();
        for (Map.Entry<ModuleInstance.ID, SettingsGroup.ID> entry : restored.membership()
            .entrySet()) {
            ModuleInstance module = modulesById.get(entry.getKey());
            SettingsGroup group = restored.groups()
                .get(entry.getValue());
            if (module == null || group == null || !supports(module) || group.kind() != module.kind()) {
                throw new IllegalArgumentException("Invalid shared settings membership for module " + entry.getKey());
            }
            if (restored.privateSettings()
                .containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Module has both private and shared settings: " + entry.getKey());
            }
            module.component()
                .validateModuleSettings(module, group.settings());
            memberCounts.merge(group.id(), 1, Integer::sum);
        }
        for (SettingsGroup.ID groupId : restored.groups()
            .keySet()) {
            if (!memberCounts.containsKey(groupId)) {
                throw new IllegalArgumentException("Restored settings group is empty: " + groupId);
            }
        }
        for (Map.Entry<ModuleInstance.ID, ModuleSettings> entry : restored.privateSettings()
            .entrySet()) {
            ModuleInstance module = modulesById.get(entry.getKey());
            if (module == null || !supports(module)) {
                throw new IllegalArgumentException("Invalid private settings owner: " + entry.getKey());
            }
            module.component()
                .validateModuleSettings(module, entry.getValue());
        }
        for (ModuleInstance module : modules) {
            if (supports(module) != (restored.privateSettings()
                .containsKey(module.id)
                ^ restored.membership()
                    .containsKey(module.id))) {
                throw new IllegalArgumentException("Module does not have exactly one settings owner: " + module.id);
            }
        }
        long restoredNextGroupId = restored.groups()
            .keySet()
            .stream()
            .mapToLong(id -> id.value())
            .max()
            .orElse(0L) + 1L;
        if (restoredNextGroupId > Integer.MAX_VALUE) {
            throw new IllegalStateException("Settings group ID space exhausted");
        }
        for (ModuleInstance module : modules) {
            if (!supports(module)) continue;
            ModuleSettings settings = restored.privateSettings()
                .get(module.id);
            if (settings == null) settings = restored.groups()
                .get(
                    restored.membership()
                        .get(module.id))
                .settings();
            module.component()
                .applyModuleSettings(module, settings);
        }
        privateSettings.clear();
        privateSettings.putAll(restored.privateSettings());
        groups.clear();
        groups.putAll(restored.groups());
        membership.clear();
        membership.putAll(restored.membership());
        nextGroupId = (int) restoredNextGroupId;
    }

    boolean supports(ModuleInstance module) {
        return module != null && FacilityModuleRegistry.get(module.kind())
            .settingsGroups();
    }

    void attachPrivate(ModuleInstance module) {
        requireSupported(module);
        if (privateSettings.containsKey(module.id) || membership.containsKey(module.id)) {
            throw new IllegalStateException("Module already has settings ownership: " + module.id);
        }
        ModuleSettings settings = module.component()
            .captureModuleSettings(module);
        module.component()
            .validateModuleSettings(module, settings);
        privateSettings.put(module.id, settings);
    }

    AttachmentPlan prepareAttachment(ModuleInstance target, @Nullable ModuleInstance copySource,
        @Nullable SettingsGroup.ID requestedGroupId) {
        requireSupported(target);
        ModuleSettings settings;
        SettingsGroup.ID sharedGroupId = null;
        if (copySource != null) {
            requireSupported(copySource);
            if (copySource.kind() != target.kind()) {
                throw new IllegalArgumentException("Build settings source kind does not match target kind");
            }
            settings = effectiveSettings(copySource.id);
            IModuleComponent.SettingsCopySpec copySpec = copySource.component()
                .prepareSettingsCopy(copySource, target);
            target.component()
                .validateModuleSettings(target, settings);
            target.component()
                .applyModuleSettings(target, settings);
            target.component()
                .applySettingsCopy(target, copySpec);
        } else if (requestedGroupId != null) {
            SettingsGroup group = requireGroup(requestedGroupId);
            if (group.kind() != target.kind()) {
                throw new IllegalArgumentException("Build settings group kind does not match target kind");
            }
            settings = group.settings();
            target.component()
                .validateModuleSettings(target, settings);
            target.component()
                .applyModuleSettings(target, settings);
            sharedGroupId = requestedGroupId;
        } else {
            settings = target.component()
                .captureModuleSettings(target);
            target.component()
                .validateModuleSettings(target, settings);
        }
        return new AttachmentPlan(target.id, sharedGroupId, settings);
    }

    void attach(AttachmentPlan plan) {
        if (privateSettings.containsKey(plan.moduleId()) || membership.containsKey(plan.moduleId())) {
            throw new IllegalStateException("Module already has settings ownership: " + plan.moduleId());
        }
        if (plan.sharedGroupId() == null) {
            privateSettings.put(plan.moduleId(), plan.settings());
        } else {
            SettingsGroup group = requireGroup(plan.sharedGroupId());
            if (!group.settings()
                .equals(plan.settings())) {
                throw new IllegalStateException("Prepared settings do not match shared group " + plan.sharedGroupId());
            }
            membership.put(plan.moduleId(), plan.sharedGroupId());
        }
    }

    boolean canJoin(com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind kind,
        SettingsGroup.ID groupId) {
        SettingsGroup group = groupId == null ? null : groups.get(groupId);
        return group != null && group.kind() == kind;
    }

    void remove(ModuleInstance.ID moduleId) {
        if (moduleId == null) return;
        privateSettings.remove(moduleId);
        SettingsGroup.ID oldGroupId = membership.remove(moduleId);
        removeGroupIfEmpty(oldGroupId);
    }

    ModuleSettings effectiveSettings(ModuleInstance.ID moduleId) {
        ModuleSettings settings = privateSettings.get(moduleId);
        if (settings != null) return settings;
        SettingsGroup.ID groupId = membership.get(moduleId);
        SettingsGroup group = groupId == null ? null : groups.get(groupId);
        if (group == null) {
            throw new IllegalStateException("Module has no settings owner: " + moduleId);
        }
        return group.settings();
    }

    Outcome createGroup(ModuleInstance module, String displayName) {
        try {
            requireSupported(module);
            ModuleSettings settings = effectiveSettings(module.id);
            SettingsGroup.ID groupId = nextGroupId();
            SettingsGroup group = new SettingsGroup(groupId, module.kind(), displayName, settings);
            detachOwnership(module.id);
            groups.put(groupId, group);
            membership.put(module.id, groupId);
            nextGroupId++;
            return Outcome.changed(Set.of(module.id));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
        }
    }

    Outcome renameGroup(SettingsGroup.ID groupId, String displayName) {
        try {
            SettingsGroup current = requireGroup(groupId);
            SettingsGroup renamed = current.withDisplayName(displayName);
            if (renamed.equals(current)) return Outcome.unchanged();
            groups.put(groupId, renamed);
            return Outcome.changed(membersOf(groupId));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
        }
    }

    Outcome joinGroup(ModuleInstance module, SettingsGroup.ID groupId) {
        try {
            requireSupported(module);
            SettingsGroup group = requireGroup(groupId);
            if (group.kind() != module.kind()) {
                return Outcome.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
            }
            if (groupId.equals(membership.get(module.id))) return Outcome.unchanged();
            module.component()
                .validateModuleSettings(module, group.settings());
            module.component()
                .applyModuleSettings(module, group.settings());
            detachOwnership(module.id);
            membership.put(module.id, groupId);
            return Outcome.changed(Set.of(module.id));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
        }
    }

    Outcome leaveGroup(ModuleInstance module) {
        try {
            requireSupported(module);
            SettingsGroup.ID groupId = membership.get(module.id);
            if (groupId == null) return Outcome.unchanged();
            ModuleSettings settings = requireGroup(groupId).settings();
            module.component()
                .validateModuleSettings(module, settings);
            membership.remove(module.id);
            privateSettings.put(module.id, settings);
            removeGroupIfEmpty(groupId);
            return Outcome.changed(Set.of(module.id));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
        }
    }

    Outcome copySettings(ModuleInstance source, List<ModuleInstance> targets) {
        if (source == null || targets == null || targets.isEmpty()) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_MODULE_TARGETS);
        }
        try {
            requireSupported(source);
            ModuleSettings sourceSettings = effectiveSettings(source.id);
            Set<ModuleInstance.ID> targetIds = new LinkedHashSet<>();
            Set<ModuleInstance.ID> changedTargetIds = new LinkedHashSet<>();
            Map<ModuleInstance.ID, IModuleComponent.SettingsCopySpec> copySpecs = new LinkedHashMap<>();
            for (ModuleInstance target : targets) {
                requireSupported(target);
                if (!targetIds.add(target.id) || source.id.equals(target.id) || source.kind() != target.kind()) {
                    return Outcome.rejected(FacilityCommand.Rejection.INVALID_MODULE_TARGETS);
                }
                IModuleComponent.SettingsCopySpec copySpec = source.component()
                    .prepareSettingsCopy(source, target);
                copySpecs.put(target.id, copySpec);
                target.component()
                    .validateModuleSettings(target, sourceSettings);
                if (copyWouldChange(target, sourceSettings, copySpec)) {
                    changedTargetIds.add(target.id);
                }
            }
            if (changedTargetIds.isEmpty()) return Outcome.unchanged();
            applyChangedCopies(targets, sourceSettings, copySpecs, changedTargetIds);
            return Outcome.changed(changedTargetIds);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_MODULE_TARGETS);
        }
    }

    private boolean copyWouldChange(ModuleInstance target, ModuleSettings sourceSettings,
        IModuleComponent.SettingsCopySpec copySpec) {
        IModuleComponent.SettingsCopySpec currentCopySpec = target.component()
            .prepareSettingsCopy(target, target);
        return !privateSettings.containsKey(target.id) || !sourceSettings.equals(effectiveSettings(target.id))
            || !copySpec.equals(currentCopySpec);
    }

    private void applyChangedCopies(List<ModuleInstance> targets, ModuleSettings sourceSettings,
        Map<ModuleInstance.ID, IModuleComponent.SettingsCopySpec> copySpecs, Set<ModuleInstance.ID> changedTargetIds) {
        for (ModuleInstance target : targets) {
            if (!changedTargetIds.contains(target.id)) continue;
            target.component()
                .applyModuleSettings(target, sourceSettings);
            target.component()
                .applySettingsCopy(target, copySpecs.get(target.id));
        }
        for (ModuleInstance target : targets) {
            if (!changedTargetIds.contains(target.id)) continue;
            detachOwnership(target.id);
            privateSettings.put(target.id, sourceSettings);
        }
    }

    boolean canCopySettings(ModuleInstance source, ModuleInstance target) {
        try {
            requireSupported(source);
            requireSupported(target);
            if (source.id.equals(target.id) || source.kind() != target.kind()) return false;
            ModuleSettings settings = effectiveSettings(source.id);
            source.component()
                .prepareSettingsCopy(source, target);
            target.component()
                .validateModuleSettings(target, settings);
            return true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return false;
        }
    }

    Outcome replaceEffectiveSettings(ModuleInstance module, ModuleSettings replacement,
        List<ModuleInstance> allModules) {
        try {
            requireSupported(module);
            module.component()
                .validateModuleSettings(module, replacement);
            ModuleSettings current = effectiveSettings(module.id);
            if (current.equals(replacement)) return Outcome.unchanged();
            SettingsGroup.ID groupId = membership.get(module.id);
            if (groupId == null) {
                module.component()
                    .applyModuleSettings(module, replacement);
                privateSettings.put(module.id, replacement);
                return Outcome.changed(Set.of(module.id));
            }
            SettingsGroup group = requireGroup(groupId);
            List<ModuleInstance> members = modules(groupId, allModules);
            for (ModuleInstance member : members) {
                member.component()
                    .validateModuleSettings(member, replacement);
            }
            for (ModuleInstance member : members) {
                member.component()
                    .applyModuleSettings(member, replacement);
            }
            groups.put(groupId, group.withSettings(replacement));
            return Outcome.changed(
                members.stream()
                    .map(member -> member.id)
                    .toList());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return Outcome.rejected(FacilityCommand.Rejection.INVALID_MODULE_CONFIG);
        }
    }

    private void requireSupported(ModuleInstance module) {
        if (!supports(module)) {
            throw new IllegalArgumentException("Module does not support facility settings");
        }
    }

    private SettingsGroup requireGroup(SettingsGroup.ID groupId) {
        SettingsGroup group = groupId == null ? null : groups.get(groupId);
        if (group == null) throw new IllegalStateException("Missing settings group " + groupId);
        return group;
    }

    private SettingsGroup.ID nextGroupId() {
        if (nextGroupId <= 0) throw new IllegalStateException("Settings group ID space exhausted");
        return new SettingsGroup.ID(nextGroupId);
    }

    private void detachOwnership(ModuleInstance.ID moduleId) {
        privateSettings.remove(moduleId);
        SettingsGroup.ID oldGroupId = membership.remove(moduleId);
        removeGroupIfEmpty(oldGroupId);
    }

    private void removeGroupIfEmpty(@Nullable SettingsGroup.ID groupId) {
        if (groupId != null && !membership.containsValue(groupId)) groups.remove(groupId);
    }

    private Set<ModuleInstance.ID> membersOf(SettingsGroup.ID groupId) {
        Set<ModuleInstance.ID> members = new LinkedHashSet<>();
        for (Map.Entry<ModuleInstance.ID, SettingsGroup.ID> entry : membership.entrySet()) {
            if (groupId.equals(entry.getValue())) members.add(entry.getKey());
        }
        return members;
    }

    private List<ModuleInstance> modules(SettingsGroup.ID groupId, List<ModuleInstance> allModules) {
        Map<ModuleInstance.ID, ModuleInstance> byId = new LinkedHashMap<>();
        for (ModuleInstance module : allModules) byId.put(module.id, module);
        List<ModuleInstance> result = new ArrayList<>();
        for (ModuleInstance.ID memberId : membersOf(groupId)) {
            ModuleInstance member = byId.get(memberId);
            if (member == null) throw new IllegalStateException("Missing settings group member " + memberId);
            result.add(member);
        }
        return result;
    }
}
