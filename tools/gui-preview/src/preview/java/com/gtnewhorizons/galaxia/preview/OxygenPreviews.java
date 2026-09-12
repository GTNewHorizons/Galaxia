package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenFiller;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenCollectorGUI;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenFillerGUI;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenPylonGUI;
import dev.modularui.preview.PreviewEntrypoint;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

final class OxygenPreviews {

    private OxygenPreviews() {}

    static PreviewEntrypoint collector() {
        return PreviewEntrypoint.of(OxygenCollectorGUI.class, context -> {
            PreviewSupport.initializeClient();
            ConfigMachines.collector.maxEnergyBuffer = 8_000;
            ConfigMachines.collector.maxOxygenBuffer = 16_000;
            TileEntityOxygenCollector tile = PreviewSupport.clientTile(new TileEntityOxygenCollector());
            tile.storedEnergy = 5_500;
            tile.active = true;
            tile.cachedLeafCount = 42;
            tile.getOxygenTank().fill(new FluidStack(FluidRegistry.getFluid("air"), 9_000), true);
            return OxygenCollectorGUI.build(tile, null, PreviewSupport.sync(context));
        });
    }

    static PreviewEntrypoint filler() {
        return PreviewEntrypoint.of(OxygenFillerGUI.class, context -> {
            PreviewSupport.initializeClient();
            ConfigMachines.filler.maxEnergyBuffer = 2_000;
            ConfigMachines.filler.maxOxygenBuffer = 10_000;
            TileEntityOxygenFiller tile = PreviewSupport.clientTile(new TileEntityOxygenFiller());
            tile.storedEnergy = 1_250;
            tile.active = true;
            tile.getOxygenTank().fill(new FluidStack(FluidRegistry.getFluid("air"), 7_000), true);
            return OxygenFillerGUI.build(tile, null, PreviewSupport.sync(context));
        });
    }

    static PreviewEntrypoint pylon() {
        return PreviewEntrypoint.of(OxygenPylonGUI.class, context -> {
            PreviewSupport.initializeClient();
            ConfigMachines.pylon.maxEnergyBuffer = 12_000;
            ConfigMachines.pylon.maxOxygenBuffer = 24_000;
            TileEntityOxygenPylon tile = PreviewSupport.clientTile(new TileEntityOxygenPylon());
            tile.storedEnergy = 9_000;
            tile.active = true;
            tile.lastChargedCount = 3;
            tile.getOxygenTank().fill(new FluidStack(FluidRegistry.getFluid("air"), 18_000), true);
            return OxygenPylonGUI.build(tile, null, PreviewSupport.sync(context));
        });
    }
}
