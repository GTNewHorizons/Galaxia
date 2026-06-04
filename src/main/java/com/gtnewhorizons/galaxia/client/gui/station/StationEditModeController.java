package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationEditModeController {

    enum Mode {
        IDLE,
        COPY_MODULE,
        MASS_BUILD,
        MODULE_UPGRADE,
        MASS_DECONSTRUCT,
        DESTROY
    }

    private final StationTilePickerController tilePicker;
    private Mode mode = Mode.IDLE;

    StationEditModeController(StationTilePickerController tilePicker) {
        this.tilePicker = tilePicker;
    }

    StationTilePickerController tilePicker() {
        return tilePicker;
    }

    Mode mode() {
        return mode;
    }

    boolean isActive() {
        return mode != Mode.IDLE;
    }

    boolean isTilePickerActive() {
        return tilePicker.isActive();
    }

    void startTileMode(Mode mode, String title, String confirmLabel, Predicate<StationTileCoord> compatibility,
        UnaryOperator<StationTileCoord> normalizer, Consumer<List<StationTileCoord>> confirmHandler) {
        startTileMode(
            mode,
            title,
            confirmLabel,
            (coord, selected) -> compatibility != null && compatibility.test(coord),
            normalizer,
            confirmHandler);
    }

    void startTileMode(Mode mode, String title, String confirmLabel,
        BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility, UnaryOperator<StationTileCoord> normalizer,
        Consumer<List<StationTileCoord>> confirmHandler) {
        startTileMode(mode, title, confirmLabel, compatibility, normalizer, confirmHandler, targets -> targets);
    }

    void startTileMode(Mode mode, String title, String confirmLabel,
        BiPredicate<StationTileCoord, Set<StationTileCoord>> compatibility, UnaryOperator<StationTileCoord> normalizer,
        Consumer<List<StationTileCoord>> confirmHandler, UnaryOperator<List<StationTileCoord>> selectionPruner) {
        if (mode == null || mode == Mode.IDLE) {
            throw new IllegalArgumentException("Station edit mode must be a concrete active mode");
        }
        tilePicker.start(title, confirmLabel, compatibility, normalizer, confirmHandler, selectionPruner);
        this.mode = mode;
    }

    void setSelectionFootprint(ModuleShape shape, boolean rotationEnabled) {
        tilePicker.setSelectionFootprint(shape, rotationEnabled);
    }

    void setPreviewModuleKind(@Nullable FacilityModuleKind kind) {
        tilePicker.setPreviewModuleKind(kind);
    }

    void setVisualStyle(StationTilePickerController.VisualStyle visualStyle) {
        tilePicker.setVisualStyle(visualStyle);
    }

    boolean rotateSelectionFootprint() {
        return tilePicker.rotateSelectionFootprint();
    }

    String title() {
        return tilePicker.title();
    }

    String confirmLabel() {
        return tilePicker.confirmLabel();
    }

    int selectedCount() {
        return tilePicker.selectedCount();
    }

    boolean canConfirm() {
        return isTilePickerActive() && tilePicker.canConfirm();
    }

    boolean isCompatible(StationTileCoord coord) {
        return isTilePickerActive() && tilePicker.isCompatible(coord);
    }

    boolean isCompatibleNormalized(StationTileCoord normalized) {
        return isTilePickerActive() && tilePicker.isCompatibleNormalized(normalized);
    }

    boolean isSelected(StationTileCoord coord) {
        return isTilePickerActive() && tilePicker.isSelected(coord);
    }

    boolean toggle(StationTileCoord coord) {
        return isTilePickerActive() && tilePicker.toggle(coord);
    }

    boolean toggleNormalized(StationTileCoord normalized) {
        return isTilePickerActive() && tilePicker.toggleNormalized(normalized);
    }

    StationTileCoord normalize(StationTileCoord coord) {
        return tilePicker.normalize(coord);
    }

    ModuleShape selectionFootprint() {
        return tilePicker.selectionFootprint();
    }

    boolean rotatesFootprint() {
        return tilePicker.rotatesFootprint();
    }

    int footprintRotation() {
        return tilePicker.footprintRotation();
    }

    @Nullable
    FacilityModuleKind previewModuleKind() {
        return tilePicker.previewModuleKind();
    }

    StationTilePickerController.VisualStyle visualStyle() {
        return tilePicker.visualStyle();
    }

    Set<StationTileCoord> selectedTargets() {
        return tilePicker.selectedTargets();
    }

    void confirm() {
        if (!canConfirm()) return;
        tilePicker.confirm();
        mode = Mode.IDLE;
    }

    void cancel() {
        tilePicker.cancel();
        mode = Mode.IDLE;
    }
}
