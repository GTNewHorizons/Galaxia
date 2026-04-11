package com.gtnewhorizons.galaxia.outpost.network;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule.Status;

/**
 * Snapshot of a single module's state for synchronization.
 */
@Desugar
public record ModuleState(int index, Status status, float progress, long energy) {}
