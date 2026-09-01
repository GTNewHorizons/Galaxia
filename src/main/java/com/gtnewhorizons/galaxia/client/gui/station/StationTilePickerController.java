package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationTilePickerController {

    enum VisualStyle {
        BUILD,
        DECONSTRUCT
    }

    private String title = "";
    private String confirmLabel = "Confirm";
    private BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility = (coord, selected) -> false;
    private UnaryOperator<StationTileCoord> normalizer = coord -> coord;
    private UnaryOperator<List<StationTileCoord>> selectionPruner = targets -> targets;
    private Consumer<List<ModulePlacement>> confirmHandler = selected -> {};
    private ModuleShape selectionFootprint = ModuleShape.SINGLE;
    private @Nullable FacilityModuleKind previewModuleKind;
    private VisualStyle visualStyle = VisualStyle.BUILD;
    private boolean footprintRotationEnabled;
    private int footprintRotation;
    private final LinkedHashMap<StationTileCoord, Integer> selected = new LinkedHashMap<>();
    private boolean active;

    void start(String title, String confirmLabel, Predicate<StationTileCoord> compatibility,
        UnaryOperator<StationTileCoord> normalizer, Consumer<List<StationTileCoord>> confirmHandler) {
        start(
            title,
            confirmLabel,
            (coord, selected) -> compatibility != null && compatibility.test(coord),
            normalizer,
            confirmHandler);
    }

    void start(String title, String confirmLabel, BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility,
        UnaryOperator<StationTileCoord> normalizer, Consumer<List<StationTileCoord>> confirmHandler) {
        start(title, confirmLabel, compatibility, normalizer, confirmHandler, targets -> targets);
    }

    void start(String title, String confirmLabel, BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility,
        UnaryOperator<StationTileCoord> normalizer, Consumer<List<StationTileCoord>> confirmHandler,
        UnaryOperator<List<StationTileCoord>> selectionPruner) {
        startWithPlacements(
            title,
            confirmLabel,
            compatibility,
            normalizer,
            selections -> { if (confirmHandler != null) confirmHandler.accept(coords(selections)); },
            selectionPruner);
    }

    void startWithPlacements(String title, String confirmLabel,
        BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility, UnaryOperator<StationTileCoord> normalizer,
        Consumer<List<ModulePlacement>> confirmHandler, UnaryOperator<List<StationTileCoord>> selectionPruner) {
        this.title = title == null ? "" : title;
        this.confirmLabel = confirmLabel == null || confirmLabel.isBlank() ? "Confirm" : confirmLabel;
        this.compatibility = compatibility == null ? (coord, selected) -> false : compatibility;
        this.normalizer = normalizer == null ? coord -> coord : normalizer;
        this.confirmHandler = confirmHandler == null ? selected -> {} : confirmHandler;
        this.selectionPruner = selectionPruner == null ? targets -> targets : selectionPruner;
        selectionFootprint = ModuleShape.SINGLE;
        footprintRotationEnabled = false;
        footprintRotation = 0;
        selected.clear();
        active = true;
    }

    void setSelectionFootprint(ModuleShape shape, boolean rotationEnabled) {
        this.selectionFootprint = shape == null ? ModuleShape.SINGLE : shape;
        this.footprintRotationEnabled = rotationEnabled;
        this.footprintRotation = 0;
    }

    void setPreviewModuleKind(@Nullable FacilityModuleKind kind) {
        this.previewModuleKind = kind;
    }

    void setVisualStyle(VisualStyle visualStyle) {
        this.visualStyle = visualStyle == null ? VisualStyle.BUILD : visualStyle;
    }

    boolean rotateSelectionFootprint() {
        if (!active || !footprintRotationEnabled) return false;
        footprintRotation = (footprintRotation + 1) & 3;
        return true;
    }

    boolean isActive() {
        return active;
    }

    String title() {
        return title;
    }

    String confirmLabel() {
        return confirmLabel;
    }

    int selectedCount() {
        return selected.size();
    }

    boolean canConfirm() {
        return active && !selected.isEmpty();
    }

    boolean isCompatibleNormalized(StationTileCoord normalized) {
        return active && normalized != null
            && (selected.containsKey(normalized)
                || compatibility.test(normalized, Collections.unmodifiableSet(selected.keySet())));
    }

    boolean isSelected(StationTileCoord coord) {
        if (!active || coord == null) return false;
        StationTileCoord normalized = normalize(coord);
        return normalized != null && selected.containsKey(normalized);
    }

    boolean toggleNormalized(StationTileCoord normalized) {
        if (normalized == null) return false;
        if (selected.containsKey(normalized)) {
            selected.remove(normalized);
        } else {
            if (!isCompatibleNormalized(normalized)) return false;
            selected.put(normalized, ModuleShape.normalizeRotation(footprintRotation));
        }
        pruneSelection();
        return true;
    }

    StationTileCoord normalize(StationTileCoord coord) {
        return coord == null ? null : normalizer.apply(coord);
    }

    ModuleShape selectionFootprint() {
        return selectionFootprint;
    }

    boolean rotatesFootprint() {
        return footprintRotationEnabled;
    }

    int footprintRotation() {
        return footprintRotation;
    }

    int selectedTargetRotation(StationTileCoord coord) {
        if (coord == null) return ModuleShape.normalizeRotation(footprintRotation);
        return selected.getOrDefault(coord, ModuleShape.normalizeRotation(footprintRotation));
    }

    @Nullable
    FacilityModuleKind previewModuleKind() {
        return previewModuleKind;
    }

    VisualStyle visualStyle() {
        return visualStyle;
    }

    Set<StationTileCoord> selectedTargets() {
        return Collections.unmodifiableSet(selected.keySet());
    }

    void confirm() {
        if (!canConfirm()) return;
        List<ModulePlacement> confirmed = placements();
        Consumer<List<ModulePlacement>> handler = confirmHandler;
        clear();
        handler.accept(confirmed);
    }

    void cancel() {
        clear();
    }

    private void clear() {
        active = false;
        selected.clear();
        title = "";
        confirmLabel = "Confirm";
        compatibility = (coord, selected) -> false;
        normalizer = coord -> coord;
        selectionPruner = targets -> targets;
        confirmHandler = selected -> {};
        selectionFootprint = ModuleShape.SINGLE;
        previewModuleKind = null;
        visualStyle = VisualStyle.BUILD;
        footprintRotationEnabled = false;
        footprintRotation = 0;
    }

    private void pruneSelection() {
        LinkedHashMap<StationTileCoord, Integer> currentRotations = new LinkedHashMap<>(selected);
        List<StationTileCoord> current = new ArrayList<>(selected.keySet());
        List<StationTileCoord> pruned = selectionPruner.apply(Collections.unmodifiableList(current));
        selected.clear();
        if (pruned == null) return;
        for (StationTileCoord coord : pruned) {
            if (currentRotations.containsKey(coord)) {
                selected.put(coord, currentRotations.get(coord));
            }
        }
    }

    private List<ModulePlacement> placements() {
        List<ModulePlacement> result = new ArrayList<>(selected.size());
        for (Map.Entry<StationTileCoord, Integer> entry : selected.entrySet()) {
            result.add(new ModulePlacement(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static List<StationTileCoord> coords(List<ModulePlacement> placements) {
        List<StationTileCoord> result = new ArrayList<>(placements.size());
        for (ModulePlacement placement : placements) {
            result.add(placement.anchor());
        }
        return result;
    }
}
