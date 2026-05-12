package com.gtnewhorizons.galaxia.compat.structure;

import net.minecraft.block.Block;

import com.gtnewhorizon.structurelib.structure.IStructureElement;

public interface IExtendedStructureElement<T> extends IStructureElement<T> {

    /**
     * @return Block that is used in the `IStructureElement.couldBeValid` call
     */
    Block getValidBlock();
}
