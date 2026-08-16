package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketEditorUI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import dev.modularui.preview.PreviewEntrypoint;
import java.util.HashMap;
import java.util.Map;

final class RocketPreviews {

    private RocketPreviews() {}

    static PreviewEntrypoint emptyRocketEditor() {
        return rocketEditor(BlueprintState.EMPTY);
    }

    static PreviewEntrypoint invalidRocketEditor() {
        return rocketEditor(BlueprintState.INVALID);
    }

    private static PreviewEntrypoint rocketEditor(BlueprintState blueprintState) {
        return PreviewEntrypoint.of(RocketEditorUI.class, context -> {
            initializeRocketParts();
            TileEntitySilo silo = new TileEntitySilo();
            PreviewWorld world = PreviewWorld.create(silo);
            silo.setDesignBlueprint(createBlueprint(blueprintState));
            return RocketEditorUI.build(
                PreviewSupport.posGuiData(world, 0, 0, 0),
                PreviewSupport.sync(context),
                PreviewSupport.settings());
        });
    }

    private static RocketBlueprint createBlueprint(BlueprintState state) {
        RocketBlueprint blueprint = new RocketBlueprint();
        if (state == BlueprintState.EMPTY) return blueprint;
        RocketPartRegistry registry = RocketPartRegistry.instance();
        blueprint.setName(state == BlueprintState.VALID ? "Mars Lander" : "Incomplete Rocket");
        blueprint.addPart(new RocketPartInstance(registry.get(0), 0, 0, 0, false));
        if (state == BlueprintState.INVALID) return blueprint;
        blueprint.addPart(new RocketPartInstance(registry.get(1), 0, 3, 0, false));
        blueprint.addPart(new RocketPartInstance(registry.get(2), 0, 8, 0, false));
        return blueprint;
    }

    static PreviewEntrypoint moduleAssembler() {
        return PreviewEntrypoint.of(TileEntityModuleAssembler.class, context -> {
            initializeRocketParts();
            TileEntityModuleAssembler assembler = new TileEntityModuleAssembler();
            PreviewWorld world = PreviewWorld.create(assembler);
            PreviewSupport.setField(assembler, "structureValid", true);
            Map<Integer, Integer> stock = new HashMap<>();
            RocketPartRegistry.instance().getAll().forEach(part -> stock.put(part.id(), 4));
            PreviewSupport.setField(assembler, "moduleStock", stock);
            return assembler.buildUI(
                PreviewSupport.posGuiData(world, 0, 0, 0),
                PreviewSupport.sync(context),
                PreviewSupport.settings());
        });
    }

    private static void initializeRocketParts() {
        PreviewSupport.initializeClient();
        if (RocketPartRegistry.instance().getAll().isEmpty()) {
            RocketPartRegistry.instance().registerAll();
        }
    }

    private enum BlueprintState {
        EMPTY,
        VALID,
        INVALID
    }
}
