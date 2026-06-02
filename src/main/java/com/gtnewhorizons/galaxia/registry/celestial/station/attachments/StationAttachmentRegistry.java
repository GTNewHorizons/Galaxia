package com.gtnewhorizons.galaxia.registry.celestial.station.attachments;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.interfaces.IAttachmentHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IItemStorageHandler;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class StationAttachmentRegistry {

    public record ResolvedAttachment<T> (T attachment, IAttachmentHandler<T> handler) {}

    private static final Map<Class<?>, IAttachmentHandler<?>> handlers = new HashMap<>();

    public static <T> void register(Class<T> clazz, IAttachmentHandler<? super T> handler) {
        handlers.put(clazz, handler);
    }

    public static ResolvedAttachment<?> resolve(TileStation station, BlockPos pos) {
        TileEntity te = pos.getTE(station.getWorldObj());
        if (te == null) return null;

        IAttachmentHandler<?> handler = handlers.get(te.getClass());
        if (handler != null) return wrap(te, handler);

        if (GalaxiaAPI.isGregTechLoaded() && te instanceof IGregTechTileEntity gtTE) {
            IMetaTileEntity mte = gtTE.getMetaTileEntity();
            if (mte != null) {
                handler = handlers.get(mte.getClass());
                if (handler != null) return wrap(mte, handler);
            }
        }

        return null;
    }

    @SuppressWarnings("rawtypes")
    private static ResolvedAttachment<?> wrap(Object attachment, IAttachmentHandler<?> handler) {
        return new ResolvedAttachment(attachment, handler);
    }

    public static boolean isEnergyHandler(IAttachmentHandler<?> handler) {
        return handler instanceof IEnergyHandler;
    }

    public static <T> IEnergyHandler<T> asEnergyHandler(IAttachmentHandler<T> handler) {
        return (IEnergyHandler<T>) handler;
    }

    public static boolean isFluidStorageHandler(Class<?> mteClass) {
        IAttachmentHandler<?> handler = handlers.get(mteClass);
        return handler instanceof IFluidStorageHandler;
    }

    public static boolean isFluidStorageHandler(IAttachmentHandler<?> handler) {
        return handler instanceof IFluidStorageHandler;
    }

    public static boolean isItemStorageHandler(IAttachmentHandler<?> handler) {
        return handler instanceof IItemStorageHandler;
    }

    public static <T> IFluidStorageHandler<T> asFluidStorageHandler(IAttachmentHandler<T> handler) {
        return (IFluidStorageHandler<T>) handler;
    }

    public static <T> IItemStorageHandler<T> asItemStorageHandler(IAttachmentHandler<T> handler) {
        return (IItemStorageHandler<T>) handler;
    }

    public static boolean isRegisteredMTE(Class<?> mteClass) {
        return handlers.get(mteClass) != null;
    }

    public static boolean isRegisteredTileEntity(TileEntity te) {
        if (handlers.get(te.getClass()) != null) return true;
        if (te instanceof IGregTechTileEntity gtTE) {
            IMetaTileEntity mte = gtTE.getMetaTileEntity();
            return mte != null && handlers.get(mte.getClass()) != null;
        }
        return false;
    }
}
