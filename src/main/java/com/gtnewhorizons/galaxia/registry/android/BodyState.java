package com.gtnewhorizons.galaxia.registry.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.FoodStats;

import com.gtnewhorizons.galaxia.core.GalaxiaPlayerProperties;
import com.gtnewhorizons.galaxia.mixin.EntityFireAccessor;
import com.gtnewhorizons.galaxia.mixin.LivingEquipmentAccessor;

import baubles.api.IBauble;
import baubles.common.container.InventoryBaubles;
import baubles.common.lib.PlayerHandler;

/** Selected physical state of one biological or manufactured body. */
public final class BodyState {

    private final NBTTagCompound state;

    private BodyState(NBTTagCompound state) {
        this.state = (NBTTagCompound) state.copy();
    }

    @Nonnull
    public static BodyState capture(@Nonnull EntityPlayer player) {
        NBTTagCompound state = new NBTTagCompound();
        state.setTag("inventory", player.inventory.writeToNBT(new NBTTagList()));
        state.setInteger("selectedSlot", player.inventory.currentItem);
        NBTTagCompound baubles = new NBTTagCompound();
        PlayerHandler.getPlayerBaubles(player)
            .saveNBT(baubles);
        state.setTag("baubles", baubles);
        NBTTagCompound food = new NBTTagCompound();
        player.getFoodStats()
            .writeNBT(food);
        state.setTag("food", food);
        state.setInteger("lowOxygen", GalaxiaPlayerProperties.get(player).lowOxygenDuration);
        capturePhysical(player, state);
        return new BodyState(state);
    }

    @Nonnull
    public static BodyState androidAt(int dimension, double x, double y, double z, float maxHealth) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0) {
            throw new IllegalArgumentException("A body must have positive finite maximum health");
        }
        NBTTagCompound state = new NBTTagCompound();
        state.setTag("inventory", new NBTTagList());
        NBTTagCompound baubles = new NBTTagCompound();
        baubles.setTag("Baubles.Inventory", new NBTTagList());
        state.setTag("baubles", baubles);
        NBTTagCompound food = new NBTTagCompound();
        new FoodStats().writeNBT(food);
        state.setTag("food", food);
        state.setTag("effects", new NBTTagList());
        state.setInteger("dimension", dimension);
        state.setDouble("x", x);
        state.setDouble("y", y);
        state.setDouble("z", z);
        state.setFloat("health", maxHealth);
        state.setFloat("maxHealth", maxHealth);
        NBTTagCompound maximumHealth = new NBTTagCompound();
        maximumHealth.setDouble("Base", maxHealth);
        maximumHealth.setTag("Modifiers", new NBTTagList());
        state.setTag("maxHealthAttribute", maximumHealth);
        state.setInteger("air", 300);
        return new BodyState(state);
    }

    @Nonnull
    public static BodyState read(@Nonnull NBTTagCompound state) {
        if (!state.hasKey("inventory", 9) || !state.hasKey("baubles", 10)
            || !state.hasKey("food", 10)
            || !state.hasKey("effects", 9)
            || !state.hasKey("maxHealthAttribute", 10)
            || !Float.isFinite(state.getFloat("health"))
            || !Float.isFinite(state.getFloat("maxHealth"))
            || state.getFloat("maxHealth") <= 0
            || !Double.isFinite(
                state.getCompoundTag("maxHealthAttribute")
                    .getDouble("Base"))
            || state.getCompoundTag("maxHealthAttribute")
                .getDouble("Base") <= 0) {
            throw new IllegalArgumentException("Invalid physical body state");
        }
        return new BodyState(state);
    }

    @Nonnull
    public NBTTagCompound write() {
        return (NBTTagCompound) state.copy();
    }

    public BodyState withRotation(float yaw, float pitch) {
        NBTTagCompound rotated = write();
        rotated.setFloat("yaw", yaw);
        rotated.setFloat("pitch", pitch);
        return new BodyState(rotated);
    }

    public BodyState restingAt(double x, double y, double z) {
        NBTTagCompound resting = write();
        resting.setDouble("x", x);
        resting.setDouble("y", y);
        resting.setDouble("z", z);
        resting.setDouble("motionX", 0);
        resting.setDouble("motionY", 0);
        resting.setDouble("motionZ", 0);
        resting.setFloat("fallDistance", 0);
        resting.setBoolean("onGround", true);
        return new BodyState(resting);
    }

    /** Restores a newly loaded player before JoinGame, without live equipment actions or network sends. */
    public void hydrate(@Nonnull EntityPlayer player) {
        player.clearItemInUse();
        removeEquipmentModifiers(player);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            Potion.potionTypes[effect.getPotionID()]
                .removeAttributesModifiersFromEntity(player, player.getAttributeMap(), effect.getAmplifier());
        }
        HashMap<Integer, PotionEffect> activePotions = ((LivingEquipmentAccessor) player).galaxia$activePotions();
        activePotions.clear();
        player.inventory.readFromNBT(state.getTagList("inventory", 10));
        player.inventory.currentItem = state.getInteger("selectedSlot");
        InventoryBaubles baubles = new InventoryBaubles(player);
        baubles.readNBT(state.getCompoundTag("baubles"));
        PlayerHandler.setPlayerBaubles(player, baubles);
        player.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(
                state.getCompoundTag("maxHealthAttribute")
                    .getDouble("Base"));
        restoreEquipmentModifiers(player);
        NBTTagList effects = state.getTagList("effects", 10);
        for (int index = 0; index < effects.tagCount(); index++) {
            PotionEffect effect = readEffect(effects.getCompoundTagAt(index));
            activePotions.put(effect.getPotionID(), effect);
            Potion.potionTypes[effect.getPotionID()]
                .applyAttributesModifiersToEntity(player, player.getAttributeMap(), effect.getAmplifier());
        }
        restorePhysicalFields(player);
        restorePlayerFields(player);
    }

    public void reconcileHealth(@Nonnull EntityLivingBase body) {
        restoreMaximumHealth(body);
        body.setHealth(health());
        body.setAbsorptionAmount(state.getFloat("absorption"));
    }

    /** The lifecycle owner must settle containers and transport the player before applying this state. */
    public void apply(@Nonnull EntityPlayer player) {
        detachEquipment(player);
        player.inventory.readFromNBT(state.getTagList("inventory", 10));
        player.inventory.currentItem = state.getInteger("selectedSlot");

        InventoryBaubles restored = new InventoryBaubles(player);
        restored.readNBT(state.getCompoundTag("baubles"));
        PlayerHandler.setPlayerBaubles(player, restored);
        applyPhysical(player);
        for (int slot = 0; slot < restored.getSizeInventory(); slot++) {
            ItemStack stack = restored.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof IBauble bauble) {
                bauble.onEquipped(stack, player);
            }
            restored.syncSlotToClients(slot);
        }
        // Equipment callbacks can change maximum health after the vanilla equipment and effects are restored.
        reconcileHealth(player);
        restorePlayerFields(player);
        if (player instanceof EntityPlayerMP connected && connected.playerNetServerHandler != null) {
            connected.sendPlayerAbilities();
            connected.mcServer.getConfigurationManager()
                .syncPlayerInventory(connected);
            for (int slot = 0; slot < 5; slot++) {
                connected.getServerForPlayer()
                    .getEntityTracker()
                    .func_151247_a(
                        connected,
                        new S04PacketEntityEquipment(
                            connected.getEntityId(),
                            slot,
                            connected.getEquipmentInSlot(slot)));
            }
        }
    }

    /** Restores physical state while the entity exposes equipment from this body's authoritative owner. */
    public void applyPhysical(@Nonnull EntityLivingBase body) {
        removeEquipmentModifiers(body);
        for (PotionEffect effect : new ArrayList<>(body.getActivePotionEffects())) {
            body.removePotionEffect(effect.getPotionID());
        }
        body.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .setBaseValue(
                state.getCompoundTag("maxHealthAttribute")
                    .getDouble("Base"));
        restoreEquipmentModifiers(body);
        NBTTagList effects = state.getTagList("effects", 10);
        for (int index = 0; index < effects.tagCount(); index++) {
            body.addPotionEffect(readEffect(effects.getCompoundTagAt(index)));
        }
        restorePhysicalFields(body);
    }

    private void restorePhysicalFields(EntityLivingBase body) {
        reconcileHealth(body);
        body.setAir(state.getInteger("air"));
        ((EntityFireAccessor) body).galaxia$setFireTicks(state.getInteger("fire"));
        body.fallDistance = state.getFloat("fallDistance");
        body.onGround = state.getBoolean("onGround");
        body.hurtTime = 0;
        body.deathTime = 0;
        body.attackTime = 0;
        body.hurtResistantTime = 0;
        body.attackedAtYaw = 0;
        body.isDead = false;
        applyLocation(body);
    }

    private void restorePlayerFields(EntityPlayer player) {
        player.getFoodStats()
            .readNBT(state.getCompoundTag("food"));
        GalaxiaPlayerProperties.get(player).lowOxygenDuration = state.getInteger("lowOxygen");
        player.setSprinting(false);
        player.setSneaking(false);
        player.capabilities.isFlying = false;
        player.noClip = false;
        player.inventory.markDirty();
    }

    private static PotionEffect readEffect(NBTTagCompound saved) {
        PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(saved);
        if (effect == null) throw new IllegalArgumentException("Invalid saved body potion effect");
        return effect;
    }

    private static void restoreEquipmentModifiers(EntityLivingBase body) {
        ItemStack[] equipment = ((LivingEquipmentAccessor) body).galaxia$previousEquipment();
        for (int slot = 0; slot < equipment.length; slot++) {
            ItemStack stack = body.getEquipmentInSlot(slot);
            if (stack != null) body.getAttributeMap()
                .applyAttributeModifiers(stack.getAttributeModifiers());
            equipment[slot] = ItemStack.copyItemStack(stack);
        }
    }

    /** Empties a committed source body before a locked player is transported to its next body. */
    public static void detachForTransfer(@Nonnull EntityPlayer player) {
        detachEquipment(player);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getPotionID());
        }
        Arrays.fill(player.inventory.mainInventory, null);
        Arrays.fill(player.inventory.armorInventory, null);
        player.inventory.currentItem = 0;
        InventoryBaubles empty = new InventoryBaubles(player);
        PlayerHandler.setPlayerBaubles(player, empty);
        for (int slot = 0; slot < empty.getSizeInventory(); slot++) empty.syncSlotToClients(slot);
        IAttributeInstance maximumHealth = player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        clearModifiers(maximumHealth);
        maximumHealth.setBaseValue(SharedMonsterAttributes.maxHealth.getDefaultValue());
        player.setHealth((float) maximumHealth.getAttributeValue());
        player.setAbsorptionAmount(0);
        player.setAir(300);
        ((EntityFireAccessor) player).galaxia$setFireTicks(0);
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        player.fallDistance = 0;
        player.setSprinting(false);
        player.setSneaking(false);
        player.capabilities.isFlying = false;
        player.noClip = false;
        player.deathTime = 0;
        player.hurtTime = 0;
        player.hurtResistantTime = 0;
        player.isDead = false;
        player.inventory.markDirty();
    }

    /** Applies coordinates and motion only. The lifecycle owner remains responsible for world transfer. */
    public void applyLocation(@Nonnull Entity body) {
        body.setLocationAndAngles(x(), y(), z(), yaw(), pitch());
        body.motionX = state.getDouble("motionX");
        body.motionY = state.getDouble("motionY");
        body.motionZ = state.getDouble("motionZ");
    }

    @Nonnull
    public BodyState withPhysicalState(@Nonnull EntityLivingBase body) {
        NBTTagCompound updated = write();
        capturePhysical(body, updated);
        return new BodyState(updated);
    }

    @Nonnull
    public BodyState withHealth(float health) {
        if (!Float.isFinite(health) || health < 0 || health > maxHealth()) {
            throw new IllegalArgumentException("Body health must lie within its maximum health");
        }
        NBTTagCompound updated = write();
        updated.setFloat("health", health);
        return new BodyState(updated);
    }

    /** Independent stacks for a lifecycle-owned drop or dock operation, never another mutable inventory view. */
    @Nonnull
    public List<ItemStack> carriedItems() {
        List<ItemStack> items = new ArrayList<>();
        readItems(state.getTagList("inventory", 10), items);
        readItems(
            state.getCompoundTag("baubles")
                .getTagList("Baubles.Inventory", 10),
            items);
        return List.copyOf(items);
    }

    /** Vanilla equipment slots: selected hand is zero, armor occupies one through four. */
    @Nullable
    public ItemStack equipment(int slot) {
        if (slot < 0 || slot > 4) throw new IllegalArgumentException("Invalid body equipment slot");
        int inventorySlot = slot == 0 ? state.getInteger("selectedSlot") : slot - 1 + 100;
        NBTTagList inventory = state.getTagList("inventory", 10);
        for (int index = 0; index < inventory.tagCount(); index++) {
            NBTTagCompound stack = inventory.getCompoundTagAt(index);
            if ((stack.getByte("Slot") & 255) == inventorySlot) return ItemStack.loadItemStackFromNBT(stack);
        }
        return null;
    }

    @Nullable
    public ItemStack bauble(int slot) {
        if (slot < 0) throw new IllegalArgumentException("Invalid body bauble slot");
        NBTTagList baubles = state.getCompoundTag("baubles")
            .getTagList("Baubles.Inventory", 10);
        for (int index = 0; index < baubles.tagCount(); index++) {
            NBTTagCompound stack = baubles.getCompoundTagAt(index);
            if ((stack.getByte("Slot") & 255) == slot) return ItemStack.loadItemStackFromNBT(stack);
        }
        return null;
    }

    @Nonnull
    public ItemStack[] armor() {
        ItemStack[] armor = new ItemStack[4];
        for (int slot = 0; slot < armor.length; slot++) armor[slot] = equipment(slot + 1);
        return armor;
    }

    /** Accepts the armor stacks modified by Forge's actual armor calculation and reconciles their bonuses. */
    @Nonnull
    public BodyState withArmor(@Nonnull ItemStack[] armor, @Nonnull EntityLivingBase wearer) {
        if (armor.length != 4) throw new IllegalArgumentException("Body armor must contain four slots");
        NBTTagList inventory = new NBTTagList();
        NBTTagList previous = state.getTagList("inventory", 10);
        for (int index = 0; index < previous.tagCount(); index++) {
            NBTTagCompound stack = previous.getCompoundTagAt(index);
            if ((stack.getByte("Slot") & 255) < 100) inventory.appendTag(stack.copy());
        }
        for (int slot = 0; slot < armor.length; slot++) {
            ItemStack stack = armor[slot];
            if (stack == null || stack.stackSize <= 0) continue;
            NBTTagCompound saved = stack.writeToNBT(new NBTTagCompound());
            saved.setByte("Slot", (byte) (slot + 100));
            inventory.appendTag(saved);
        }
        return withInventory(inventory, wearer);
    }

    /** Handles the vanilla equipment setter without granting an entity a second authoritative inventory. */
    @Nonnull
    public BodyState withEquipment(int slot, @Nullable ItemStack stack, @Nonnull EntityLivingBase wearer) {
        if (slot < 0 || slot > 4) throw new IllegalArgumentException("Invalid body equipment slot");
        if (slot != 0) {
            ItemStack[] armor = armor();
            armor[slot - 1] = stack;
            return withArmor(armor, wearer);
        }
        NBTTagList inventory = new NBTTagList();
        NBTTagList previous = state.getTagList("inventory", 10);
        int selected = state.getInteger("selectedSlot");
        for (int index = 0; index < previous.tagCount(); index++) {
            NBTTagCompound saved = previous.getCompoundTagAt(index);
            if ((saved.getByte("Slot") & 255) != selected) inventory.appendTag(saved.copy());
        }
        if (stack != null && stack.stackSize > 0) {
            NBTTagCompound saved = stack.writeToNBT(new NBTTagCompound());
            saved.setByte("Slot", (byte) selected);
            inventory.appendTag(saved);
        }
        return withInventory(inventory, wearer);
    }

    private BodyState withInventory(NBTTagList inventory, EntityLivingBase wearer) {
        NBTTagCompound updated = write();
        updated.setTag("inventory", inventory);
        BodyState replacement = new BodyState(updated);
        // The old snapshot also identifies bonuses when the entity's owner has already replaced its projection.
        for (int slot = 0; slot < 5; slot++) {
            ItemStack old = equipment(slot);
            if (old != null) wearer.getAttributeMap()
                .removeAttributeModifiers(old.getAttributeModifiers());
        }
        removeEquipmentModifiers(wearer);
        ItemStack[] cache = ((LivingEquipmentAccessor) wearer).galaxia$previousEquipment();
        for (int slot = 0; slot < cache.length; slot++) {
            ItemStack stack = replacement.equipment(slot);
            if (stack != null) wearer.getAttributeMap()
                .applyAttributeModifiers(stack.getAttributeModifiers());
            cache[slot] = ItemStack.copyItemStack(stack);
        }
        captureMaximumHealth(wearer, updated);
        updated.setFloat("health", Math.min(health(), wearer.getMaxHealth()));
        return new BodyState(updated);
    }

    private static void detachEquipment(EntityPlayer player) {
        player.clearItemInUse();
        InventoryBaubles previous = PlayerHandler.getPlayerBaubles(player);
        for (ItemStack stack : previous.stackList) {
            if (stack != null && stack.getItem() instanceof IBauble bauble) {
                bauble.onUnequipped(stack, player);
            }
        }
        removeEquipmentModifiers(player);
    }

    private static void readItems(NBTTagList stacks, List<ItemStack> items) {
        for (int index = 0; index < stacks.tagCount(); index++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(stacks.getCompoundTagAt(index));
            if (stack == null) throw new IllegalArgumentException("Invalid saved body item");
            items.add(stack);
        }
    }

    private static void removeEquipmentModifiers(EntityLivingBase body) {
        ItemStack[] previous = ((LivingEquipmentAccessor) body).galaxia$previousEquipment();
        for (int slot = 0; slot < previous.length; slot++) {
            if (previous[slot] != null) {
                body.getAttributeMap()
                    .removeAttributeModifiers(previous[slot].getAttributeModifiers());
            }
            ItemStack current = body.getEquipmentInSlot(slot);
            if (current != null) body.getAttributeMap()
                .removeAttributeModifiers(current.getAttributeModifiers());
            previous[slot] = null;
        }
    }

    private static void capturePhysical(EntityLivingBase body, NBTTagCompound target) {
        target.setInteger("dimension", body.dimension);
        target.setDouble("x", body.posX);
        target.setDouble("y", body.posY);
        target.setDouble("z", body.posZ);
        target.setFloat("yaw", body.rotationYaw);
        target.setFloat("pitch", body.rotationPitch);
        target.setDouble("motionX", body.motionX);
        target.setDouble("motionY", body.motionY);
        target.setDouble("motionZ", body.motionZ);
        target.setFloat("health", body.getHealth());
        captureMaximumHealth(body, target);
        target.setFloat("absorption", body.getAbsorptionAmount());
        target.setInteger("air", body.getAir());
        target.setInteger("fire", ((EntityFireAccessor) body).galaxia$getFireTicks());
        target.setFloat("fallDistance", body.fallDistance);
        target.setBoolean("onGround", body.onGround);
        NBTTagList effects = new NBTTagList();
        for (PotionEffect effect : body.getActivePotionEffects()) {
            effects.appendTag(effect.writeCustomPotionEffectToNBT(new NBTTagCompound()));
        }
        target.setTag("effects", effects);
    }

    private static void captureMaximumHealth(EntityLivingBase body, NBTTagCompound target) {
        IAttributeInstance attribute = body.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        NBTTagCompound maximumHealth = new NBTTagCompound();
        maximumHealth.setDouble("Base", attribute.getBaseValue());
        NBTTagList modifiers = new NBTTagList();
        // The vanilla writer is private and excludes unsaved modifiers, including physical item bonuses.
        for (AttributeModifier modifier : attribute.func_111122_c()) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setString("Name", modifier.getName());
            saved.setDouble("Amount", modifier.getAmount());
            saved.setInteger("Operation", modifier.getOperation());
            saved.setLong(
                "UUIDMost",
                modifier.getID()
                    .getMostSignificantBits());
            saved.setLong(
                "UUIDLeast",
                modifier.getID()
                    .getLeastSignificantBits());
            saved.setBoolean("Saved", modifier.isSaved());
            modifiers.appendTag(saved);
        }
        maximumHealth.setTag("Modifiers", modifiers);
        target.setTag("maxHealthAttribute", maximumHealth);
        target.setFloat("maxHealth", body.getMaxHealth());
    }

    private void restoreMaximumHealth(EntityLivingBase body) {
        IAttributeInstance attribute = body.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        clearModifiers(attribute);
        NBTTagCompound maximumHealth = state.getCompoundTag("maxHealthAttribute");
        attribute.setBaseValue(maximumHealth.getDouble("Base"));
        NBTTagList modifiers = maximumHealth.getTagList("Modifiers", 10);
        for (int index = 0; index < modifiers.tagCount(); index++) {
            NBTTagCompound modifier = modifiers.getCompoundTagAt(index);
            attribute.applyModifier(
                SharedMonsterAttributes.readAttributeModifierFromNBT(modifier)
                    .setSaved(modifier.getBoolean("Saved")));
        }
    }

    private static void clearModifiers(IAttributeInstance attribute) {
        for (AttributeModifier modifier : new ArrayList<>(attribute.func_111122_c()))
            attribute.removeModifier(modifier);
    }

    public int dimension() {
        return state.getInteger("dimension");
    }

    public double x() {
        return state.getDouble("x");
    }

    public double y() {
        return state.getDouble("y");
    }

    public double z() {
        return state.getDouble("z");
    }

    public float yaw() {
        return state.getFloat("yaw");
    }

    public float pitch() {
        return state.getFloat("pitch");
    }

    public float health() {
        return state.getFloat("health");
    }

    public float maxHealth() {
        return state.getFloat("maxHealth");
    }
}
