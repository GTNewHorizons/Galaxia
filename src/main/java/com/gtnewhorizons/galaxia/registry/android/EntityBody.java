package com.gtnewhorizons.galaxia.registry.android;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;

import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** Physical projection of an inactive android or the biological body held in a capsule. */
public final class EntityBody extends EntityLiving implements IEntityAdditionalSpawnData {

    public static final String ANDROID_HURT_SOUND = "mob.irongolem.hit";
    public static final String ANDROID_DEATH_SOUND = "mob.irongolem.death";

    private UUID bodyId;
    private UUID operatorId;
    private GameProfile operatorProfile;
    @SideOnly(Side.CLIENT)
    private ResourceLocation biologicalSkin;
    @SideOnly(Side.CLIENT)
    private ResourceLocation biologicalCape;
    @SideOnly(Side.CLIENT)
    private boolean skinRequested;
    private BodyState projected;
    private boolean processingDamage;
    private boolean tickingPhysical;
    private final ItemStack[] clientEquipment = new ItemStack[5];

    public EntityBody(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        setCanPickUpLoot(false);
    }

    public EntityBody(World world, UUID bodyId, @Nullable GameProfile operatorProfile) {
        this(world);
        this.bodyId = bodyId;
        this.operatorId = operatorProfile == null ? null : operatorProfile.getId();
        if (operatorProfile != null) bindOperatorProfile(operatorProfile);
        this.entityUniqueID = bodyId;
    }

    @Nullable
    public GameProfile operatorProfile() {
        return operatorProfile;
    }

    void bindOperatorProfile(GameProfile profile) {
        operatorProfile = profile;
        setAlwaysRenderNameTag(true);
    }

    @Override
    public boolean hasCustomNameTag() {
        return operatorProfile != null || super.hasCustomNameTag();
    }

    @Override
    public String getCustomNameTag() {
        return operatorProfile == null ? super.getCustomNameTag()
            : StatCollector
                .translateToLocalFormatted("galaxia.android.body.biological.name", operatorProfile.getName());
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation biologicalSkin() {
        loadProfileTextures();
        return biologicalSkin == null ? AbstractClientPlayer.locationStevePng : biologicalSkin;
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    public ResourceLocation biologicalCape() {
        loadProfileTextures();
        return biologicalCape;
    }

    @SideOnly(Side.CLIENT)
    private void loadProfileTextures() {
        if (!skinRequested && operatorProfile != null) {
            skinRequested = true;
            Minecraft.getMinecraft()
                .func_152342_ad()
                .func_152790_a(operatorProfile, (type, location) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) biologicalSkin = location;
                    else if (type == MinecraftProfileTexture.Type.CAPE) biologicalCape = location;
                }, true);
        }
    }

    public UUID bodyId() {
        return bodyId;
    }

    @Nullable
    public UUID operatorId() {
        return operatorId;
    }

    public boolean isBiological() {
        return operatorId != null;
    }

    public BodyState currentState() {
        BodyState state = AndroidControl.stateOf(this);
        if (state != null && state != projected && !tickingPhysical) {
            AndroidControl.applyPhysical(this, state);
            projected = state;
        }
        return state;
    }

    public void writeBauble(int slot, ItemStack stack) {
        publishState(currentState().withBauble(slot, stack));
    }

    public int lowOxygenDuration() {
        return currentState().lowOxygenDuration();
    }

    public void setLowOxygenDuration(int duration) {
        publishState(currentState().withLowOxygenDuration(duration));
    }

    private void publishState(BodyState state) {
        projected = state.withPhysicalState(this);
        AndroidControl.observe(this, projected);
    }

    @Override
    public void onUpdate() {
        if (worldObj.isRemote) {
            super.onUpdate();
            return;
        }
        BodyState state = currentState();
        if (state == null) {
            setDead();
            return;
        }
        if (!isBiological()) AndroidControl.bodies()
            .body(bodyId)
            .ifPresent(body -> {
                if (!body.name()
                    .equals(super.getCustomNameTag())) {
                    setCustomNameTag(body.name());
                    setAlwaysRenderNameTag(
                        !body.name()
                            .isEmpty());
                }
            });
        tickingPhysical = true;
        try {
            super.onUpdate();
            if (isDead) return;
            if (isBiological()) {
                setPosition(state.x(), state.y(), state.z());
                motionX = motionY = motionZ = 0;
                fallDistance = 0;
            }
            // Hazards and equipment may have changed canonical fields during the living update.
            BodyState latest = AndroidControl.stateOf(this);
            if (latest == null) return;
            if (isBiological()) {
                BodyState.Metabolism metabolism = latest
                    .tickBiological(this, ConfigMachines.android.biologicalMetabolismPeriodTicks);
                projected = metabolism.state();
                AndroidControl.observe(this, projected);
                if (metabolism.starvationDue() && (getHealth() > 10 || worldObj.difficultySetting == EnumDifficulty.HARD
                    || getHealth() > 1 && worldObj.difficultySetting == EnumDifficulty.NORMAL)) {
                    attackEntityFrom(DamageSource.starve, 1);
                    if (isDead) return;
                    BodyState surviving = AndroidControl.stateOf(this);
                    if (surviving != null) publishState(surviving);
                }
            } else publishState(latest);
        } finally {
            tickingPhysical = false;
        }
    }

    @Override
    protected void updateEntityActionState() {}

    @Override
    protected boolean isAIEnabled() {
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected String getHurtSound() {
        return isBiological() ? super.getHurtSound() : ANDROID_HURT_SOUND;
    }

    @Override
    protected String getDeathSound() {
        return isBiological() ? super.getDeathSound() : ANDROID_DEATH_SOUND;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return !isBiological();
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        if (worldObj.isRemote) return clientEquipment[slot];
        BodyState state = AndroidControl.stateOf(this);
        return state == null ? null : state.equipment(slot);
    }

    @Override
    public ItemStack getHeldItem() {
        return getEquipmentInSlot(0);
    }

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[] { getEquipmentInSlot(1), getEquipmentInSlot(2), getEquipmentInSlot(3),
            getEquipmentInSlot(4) };
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {
        if (worldObj.isRemote) {
            clientEquipment[slot] = ItemStack.copyItemStack(stack);
        } else {
            BodyState state = currentState();
            if (state != null) {
                publishState(state.withEquipment(slot, stack, this));
            }
        }
    }

    @Override
    protected float applyArmorCalculations(DamageSource source, float amount) {
        BodyState state = currentState();
        if (state == null) return amount;
        ItemStack[] armor = state.armor();
        float remaining = ISpecialArmor.ArmorProperties.ApplyArmor(this, armor, source, amount);
        publishState(state.withArmor(armor, this));
        return remaining;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean previous = processingDamage;
        processingDamage = true;
        try {
            return super.attackEntityFrom(source, amount);
        } finally {
            processingDamage = previous;
        }
    }

    @Override
    public void setHealth(float health) {
        // Cleanup must not persist a dead body without going through the damage lifecycle.
        if (health <= 0 && !processingDamage && (worldObj == null || !worldObj.isRemote)) return;
        super.setHealth(health);
    }

    @Override
    public void onDeath(DamageSource source) {
        // Direct removal discards this projection. Only a lethal hit retires the authoritative body.
        if (processingDamage && getHealth() <= 0 && !worldObj.isRemote)
            AndroidControl.destroyPhysicalBody(this, source);
        setDead();
    }

    @Override
    public void travelToDimension(int destination) {
        AndroidControl.beforeDimensionTransfer(this, destination);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        if (bodyId != null) tag.setString("bodyId", bodyId.toString());
        if (operatorId != null) tag.setString("operatorId", operatorId.toString());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        bodyId = UUID.fromString(tag.getString("bodyId"));
        operatorId = tag.hasKey("operatorId") ? UUID.fromString(tag.getString("operatorId")) : null;
        entityUniqueID = bodyId;
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        PacketUtil.writeId(buffer, bodyId);
        buffer.writeBoolean(operatorId != null);
        if (operatorId != null) PacketUtil.writeId(buffer, operatorId);
        NBTTagCompound profile = new NBTTagCompound();
        if (operatorProfile != null) NBTUtil.func_152460_a(profile, operatorProfile);
        ByteBufUtils.writeTag(buffer, profile);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        bodyId = PacketUtil.readId(buffer);
        operatorId = buffer.readBoolean() ? PacketUtil.readId(buffer) : null;
        GameProfile profile = NBTUtil.func_152459_a(ByteBufUtils.readTag(buffer));
        if (operatorId != null) {
            if (profile == null || !operatorId.equals(profile.getId()))
                throw new IllegalArgumentException("Biological body spawn is missing its operator profile");
            bindOperatorProfile(profile);
        }
    }
}
