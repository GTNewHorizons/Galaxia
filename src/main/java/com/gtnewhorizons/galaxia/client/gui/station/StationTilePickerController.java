package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationTilePickerController {

    private String title = "";
    private String confirmLabel = "Confirm";
    private Predicate<StationTileCoord> compatibility = coord -> false;
    private UnaryOperator<StationTileCoord> normalizer = coord -> coord;
    private Consumer<List<StationTileCoord>> confirmHandler = selected -> {};
    private final Set<StationTileCoord> selected = new LinkedHashSet<>();
    private boolean active;

    void start(String title, String confirmLabel, Predicate<StationTileCoord> compatibility,
        UnaryOperator<StationTileCoord> normalizer, Consumer<List<StationTileCoord>> confirmHandler) {
        this.title = title == null ? "" : title;
        this.confirmLabel = confirmLabel == null || confirmLabel.isBlank() ? "Confirm" : confirmLabel;
        this.compatibility = compatibility == null ? coord -> false : compatibility;
        this.normalizer = normalizer == null ? coord -> coord : normalizer;
        this.confirmHandler = confirmHandler == null ? selected -> {} : confirmHandler;
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
        return normalized != null && compatibility.test(normalized);
    }

    boolean isSelected(StationTileCoord coord) {
        if (!active || coord == null) return false;
        StationTileCoord normalized = normalizer.apply(coord);
        return normalized != null && selected.contains(normalized);
    }

    boolean toggle(StationTileCoord coord) {
        if (!isCompatible(coord)) return false;
        StationTileCoord normalized = normalizer.apply(coord);
        if (selected.contains(normalized)) {
            selected.remove(normalized);
        } else {
            selected.add(normalized);
        }
        return true;
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
        compatibility = coord -> false;
        normalizer = coord -> coord;
        confirmHandler = selected -> {};
    }
}
