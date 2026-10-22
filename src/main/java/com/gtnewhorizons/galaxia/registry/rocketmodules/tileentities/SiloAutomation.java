package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;

import lombok.Getter;
import lombok.Setter;

/**
 * Train-like automation of a silo: build the rocket from the inserted schematic, load requested cargo, launch, and
 * repeat until every request has been delivered
 */
public class SiloAutomation {

    public static final int MAX_REQUESTS = 6;

    public enum Status {

        DISABLED,
        NO_SCHEMATIC,
        NO_DESTINATION,
        NO_REQUESTS,
        INVALID_SCHEMATIC,
        NO_ASSEMBLER,
        BUILDING,
        FUELING,
        LOADING,
        NOT_ENOUGH_DELTA_V,
        DESTINATION_BUSY,
        IN_FLIGHT,
        COMPLETE;

        public String langKey() {
            return "galaxia.silo.automation.status." + name().toLowerCase();
        }
    }

    /**
     * One line of the request list
     */
    public static final class Request {

        /** Item to deliver, stack size is ignored */
        @Getter
        private ItemStack template;
        @Getter
        @Setter
        private int amount;
        @Getter
        private int delivered;
        @Getter
        private int inFlight;

        public Request(ItemStack template, int amount) {
            setTemplate(template);
            this.amount = amount;
        }

        public void setTemplate(ItemStack stack) {
            if (stack == null) {
                template = null;
                return;
            }
            template = stack.copy();
            template.stackSize = 1;
        }

        public boolean matches(ItemStack stack) {
            return template != null && stack != null
                && stack.getItem() == template.getItem()
                && stack.getItemDamage() == template.getItemDamage()
                && ItemStack.areItemStackTagsEqual(stack, template);
        }

        /** Items that still have to be loaded into a rocket */
        public int remaining() {
            return Math.max(0, amount - delivered - inFlight);
        }

        public boolean isComplete() {
            return template == null || delivered >= amount;
        }

        void launched(int count) {
            inFlight += count;
        }

        void landed(int count) {
            inFlight = Math.max(0, inFlight - count);
            delivered += count;
        }

        /** Starts counting from zero again */
        public void reset() {
            delivered = 0;
            inFlight = 0;
        }

        NBTTagCompound serializeNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            if (template != null) tag.setTag("item", template.writeToNBT(new NBTTagCompound()));
            tag.setInteger("amount", amount);
            tag.setInteger("delivered", delivered);
            tag.setInteger("inFlight", inFlight);
            return tag;
        }

        static Request deserializeNBT(NBTTagCompound tag) {
            ItemStack item = tag.hasKey("item") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("item")) : null;
            Request request = new Request(item, tag.getInteger("amount"));
            request.delivered = tag.getInteger("delivered");
            request.inFlight = tag.getInteger("inFlight");
            return request;
        }
    }

    @Getter
    @Setter
    private boolean enabled = false;
    /** Rocket arrivals are sent back to the assembler as soon as their cargo is unloaded */
    @Getter
    @Setter
    private boolean returnArrivals = false;
    @Getter
    @Setter
    private Status status = Status.DISABLED;
    /** Ticks since cargo was last loaded, launches a partly filled rocket once loading stalls */
    @Getter
    @Setter
    private int idleTicks = 0;

    @Getter
    private final ItemStackHandler schematicSlot = new ItemStackHandler(1) {

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return ItemRocketSchematic.hasBlueprint(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    @Getter
    private final List<Request> requests = new ArrayList<>();

    public SiloAutomation() {
        for (int i = 0; i < MAX_REQUESTS; i++) requests.add(new Request(null, 0));
    }

    /** Blueprint of the inserted schematic, or null */
    public RocketBlueprint getSchematicBlueprint() {
        return ItemRocketSchematic.getBlueprint(schematicSlot.getStackInSlot(0));
    }

    public boolean hasRequests() {
        for (Request request : requests) {
            if (request.template != null && request.amount > 0) return true;
        }
        return false;
    }

    /** Every request has been delivered */
    public boolean isComplete() {
        if (!hasRequests()) return false;
        for (Request request : requests) {
            if (request.template != null && request.amount > 0 && !request.isComplete()) return false;
        }
        return true;
    }

    /** Items still to be loaded for every open request */
    public int totalRemaining() {
        int total = 0;
        for (Request request : requests) {
            if (request.template != null) total += request.remaining();
        }
        return total;
    }

    /**
     * Finds the open request an item belongs to
     *
     * @param stack The item to check
     *
     * @return The request, or null if the item was not requested or is already covered
     */
    public Request findOpenRequest(ItemStack stack) {
        for (Request request : requests) {
            if (request.matches(stack) && request.remaining() > 0) return request;
        }
        return null;
    }

    /**
     * Records cargo that just left
     *
     * @param cargo Every stack on board the launched rocket
     */
    public void onLaunched(List<ItemStack> cargo) {
        for (ItemStack stack : cargo) {
            for (Request request : requests) {
                if (request.matches(stack)) {
                    request.launched(stack.stackSize);
                    break;
                }
            }
        }
    }

    /**
     * Records cargo that reached the destination
     *
     * @param cargo Every stack on board the landed rocket
     */
    public void onLanded(List<ItemStack> cargo) {
        for (ItemStack stack : cargo) {
            for (Request request : requests) {
                if (request.matches(stack)) {
                    request.landed(stack.stackSize);
                    break;
                }
            }
        }
    }

    public void resetProgress() {
        for (Request request : requests) request.reset();
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("enabled", enabled);
        tag.setBoolean("returnArrivals", returnArrivals);
        tag.setInteger("status", status.ordinal());
        tag.setInteger("idle", idleTicks);
        tag.setTag("schematic", schematicSlot.serializeNBT());
        NBTTagList list = new NBTTagList();
        for (Request request : requests) list.appendTag(request.serializeNBT());
        tag.setTag("requests", list);
        return tag;
    }

    public void deserializeNBT(NBTTagCompound tag) {
        enabled = tag.getBoolean("enabled");
        returnArrivals = tag.getBoolean("returnArrivals");
        int ordinal = tag.getInteger("status");
        status = ordinal >= 0 && ordinal < Status.values().length ? Status.values()[ordinal] : Status.DISABLED;
        idleTicks = tag.getInteger("idle");
        if (tag.hasKey("schematic")) schematicSlot.deserializeNBT(tag.getCompoundTag("schematic"));
        NBTTagList list = tag.getTagList("requests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < MAX_REQUESTS; i++) {
            requests
                .set(i, i < list.tagCount() ? Request.deserializeNBT(list.getCompoundTagAt(i)) : new Request(null, 0));
        }
    }
}
