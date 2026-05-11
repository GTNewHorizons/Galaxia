package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationTilePickerController {

    private String title = "";
    private String confirmLabel = "Confirm";
    private BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility = (coord, selected) -> false;
    private UnaryOperator<StationTileCoord> normalizer = coord -> coord;
    private UnaryOperator<List<StationTileCoord>> selectionPruner = targets -> targets;
    private Consumer<List<StationTileCoord>> confirmHandler = selected -> {};
    private final Set<StationTileCoord> selected = new LinkedHashSet<>();
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
        this.title = title == null ? "" : title;
        this.confirmLabel = confirmLabel == null || confirmLabel.isBlank() ? "Confirm" : confirmLabel;
        this.compatibility = compatibility == null ? (coord, selected) -> false : compatibility;
        this.normalizer = normalizer == null ? coord -> coord : normalizer;
        this.confirmHandler = confirmHandler == null ? selected -> {} : confirmHandler;
        this.selectionPruner = selectionPruner == null ? targets -> targets : selectionPruner;
        selected.clear();
        active = true;
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

    boolean isCompatible(StationTileCoord coord) {
        if (!active || coord == null) return false;
        StationTileCoord normalized = normalizer.apply(coord);
        return normalized != null
            && (selected.contains(normalized) || compatibility.test(normalized, Collections.unmodifiableSet(selected)));
    }

    boolean isSelected(StationTileCoord coord) {
        if (!active || coord == null) return false;
        StationTileCoord normalized = normalizer.apply(coord);
        return normalized != null && selected.contains(normalized);
    }

    boolean toggle(StationTileCoord coord) {
        StationTileCoord normalized = normalizer.apply(coord);
        if (normalized == null) return false;
        if (selected.contains(normalized)) {
            selected.remove(normalized);
        } else {
            if (!isCompatible(coord)) return false;
            selected.add(normalized);
        }
        pruneSelection();
        return true;
    }

    Set<StationTileCoord> selectedTargets() {
        return Collections.unmodifiableSet(selected);
    }

    void confirm() {
        if (!canConfirm()) return;
        List<StationTileCoord> confirmed = new ArrayList<>(selected);
        Consumer<List<StationTileCoord>> handler = confirmHandler;
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
    }

    private void pruneSelection() {
        List<StationTileCoord> current = new ArrayList<>(selected);
        List<StationTileCoord> pruned = selectionPruner.apply(Collections.unmodifiableList(current));
        selected.clear();
        if (pruned == null) return;
        selected.addAll(pruned);
    }
}
