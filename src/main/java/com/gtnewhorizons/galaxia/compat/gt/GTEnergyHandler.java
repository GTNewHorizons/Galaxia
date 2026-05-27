package com.gtnewhorizons.galaxia.compat.gt;

import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public abstract class GTEnergyHandler<T extends MTEMultiBlockBase> extends GTBaseAttachmentHandler<T>
    implements IEnergyHandler<T> {
}
