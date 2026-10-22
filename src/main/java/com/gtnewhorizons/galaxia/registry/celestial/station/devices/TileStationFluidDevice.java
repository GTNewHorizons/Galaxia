package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;

/**
 * A station machine that burns through a fluid. It fills itself from the station's storage, from pipes and from
 * containers clicked into its screen
 */
public abstract class TileStationFluidDevice extends TileStationDevice implements IFluidHandler {

    private final FluidTank tank;
    private int ticksSinceUse;

    protected TileStationFluidDevice(int capacity) {
        tank = new FluidTank(capacity) {

            @Override
            public int fill(FluidStack resource, boolean doFill) {
                if (resource == null || resource.getFluid() != fluid()) return 0;
                int filled = super.fill(resource, doFill);
                if (doFill && filled > 0) markDirty();
                return filled;
            }
        };
    }

    /** The fluid the machine burns, null while no mod provides it */
    @Nullable
    protected abstract Fluid fluid();

    /** Litres the machine burns every second it works */
    protected abstract int litresPerSecond();

    /** Lang key shown while the tank runs dry */
    protected abstract String missingFluidKey();

    public FluidTank getTank() {
        return tank;
    }

    @Override
    protected @Nullable String missingInput() {
        return fluid() == null || tank.getFluidAmount() < litresPerSecond() ? missingFluidKey() : null;
    }

    @Override
    protected void consumeInput() {
        if (++ticksSinceUse < 20) return;
        ticksSinceUse = 0;
        tank.drain(litresPerSecond(), true);
        markDirty();
    }

    @Override
    protected void supply(Station storage) {
        Fluid fluid = fluid();
        int room = tank.getCapacity() - tank.getFluidAmount();
        if (fluid == null || room <= 0) return;
        long taken = storage.updateFluids(FluidKey.of(new FluidStack(fluid, 1)), -room);
        if (taken > 0) tank.fill(new FluidStack(fluid, (int) taken), true);
    }

    @Override
    protected void addDeviceWidgets(ModularPanel panel, PanelSyncManager syncManager) {
        panel.child(new FluidSlot() {

            /** Fills from the left like the other bars instead of from the bottom */
            @Override
            public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
                FluidStack fluid = getFluidStack();
                if (fluid == null || fluid.amount <= 0 || tank.getCapacity() <= 0) return;
                int width = getArea().width - 2;
                int filled = Math
                    .max(1, Math.min(width, Math.round((float) width * fluid.amount / tank.getCapacity())));
                GuiDraw.drawFluidTexture(fluid, 1, 1, filled, getArea().height - 2, context.getCurrentDrawingZ());
            }

            @Override
            protected void addToolTip(RichTooltip tooltip) {
                super.addToolTip(tooltip);
                tooltip.addLine(
                    IKey.str(
                        StatCollector
                            .translateToLocalFormatted("galaxia.gui.station_device.fluid_use", litresPerSecond())));
            }
        }.syncHandler(
            new FluidSlotSyncHandler(tank).canDrainSlot(false)
                .filter(stack -> stack != null && stack.getFluid() == fluid()))
            .background(EnumTextures.GUI_WELL.getImage())
            .size(WIDTH - 16, 16)
            .pos(8, CONTENT_Y));
    }

    // region Fluid handling

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return tank.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid != null && fluid == fluid();
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { tank.getInfo() };
    }

    // endregion

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("tank", tank.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tank.readFromNBT(tag.getCompoundTag("tank"));
    }
}
