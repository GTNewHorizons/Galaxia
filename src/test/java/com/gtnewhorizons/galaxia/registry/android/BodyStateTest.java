package com.gtnewhorizons.galaxia.registry.android;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.StringTranslate;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.GalaxiaPlayerProperties;
import com.gtnewhorizons.galaxia.mixin.EntityFireAccessor;
import com.gtnewhorizons.galaxia.mixin.LivingEquipmentAccessor;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.mojang.authlib.GameProfile;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.common.lib.PlayerHandler;

final class BodyStateTest {

    private static final Item LOGIN_BAUBLE = new LoginBauble();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
        int itemId = 4096;
        while (Item.itemRegistry.getObjectById(itemId) != null) itemId++;
        Item.itemRegistry.addObject(itemId, "galaxia_body_state_test_bauble", LOGIN_BAUBLE);
    }

    /** Product contract: controlled android deaths identify the operator and preserve the cause. */
    @Test
    void androidDeathMessagesIncludeTheCurrentOperator() {
        StringTranslate.inject(BodyStateTest.class.getResourceAsStream("/assets/galaxia/lang/en_US.lang"));
        EntityPlayer operator = player("Pxx500");
        UUID id = UUID.randomUUID();
        AndroidBodies.AndroidBody body = new AndroidBodies.AndroidBody(
            id,
            UUID.randomUUID(),
            new AndroidBodies.Anchor(UUID.randomUUID(), 0, 0, 64, 0),
            BodyState.androidAt(0, .5, 64, .5, 20),
            1000,
            AndroidBodies.Chassis.EV,
            -1,
            false,
            "Scout");
        assertEquals(
            "Android Scout (operated by Pxx500) died",
            AndroidControl.deathMessage(body, DamageSource.generic, operator)
                .getUnformattedText());
        assertEquals(
            "Android Scout (operated by Pxx500) self-destructed",
            AndroidControl.deathMessage(body, new DamageSource("galaxia.android.selfDestruct"), operator)
                .getUnformattedText());
        assertEquals(
            "Android Scout died",
            AndroidControl.deathMessage(body, DamageSource.generic, new EntityBody(operator.worldObj, id, null))
                .getUnformattedText());
    }

    /** Integration contract: restoring another body cannot move account identity or share mutable belongings. */
    @Test
    void bodyRoundTripIsIndependentOfTheSourceAndPreservesTheOperator() {
        EntityPlayer source = player("source");
        source.inventory.mainInventory[4] = new ItemStack(Items.diamond, 7);
        source.inventory.armorInventory[2] = new ItemStack(Items.iron_chestplate);
        source.inventory.currentItem = 4;
        source.setHealth(13);
        BodyState captured = BodyState.capture(source);

        source.inventory.mainInventory[4].stackSize = 1;
        source.inventory.armorInventory[2] = null;
        source.setHealth(2);

        EntityPlayer target = player("operator");
        UUID operator = target.getUniqueID();
        target.experienceLevel = 17;
        target.getInventoryEnderChest()
            .setInventorySlotContents(0, new ItemStack(Items.emerald, 3));
        target.inventory.mainInventory[8] = new ItemStack(Items.coal);
        BodyState.read(captured.write())
            .apply(target);

        assertNotNull(target.inventory.mainInventory[4], "The selected body must retain its captured inventory");
        assertEquals(7, target.inventory.mainInventory[4].stackSize);
        assertNotNull(target.inventory.armorInventory[2]);
        assertNull(target.inventory.mainInventory[8]);
        assertEquals(4, target.inventory.currentItem);
        assertEquals(13, target.getHealth());
        assertEquals(operator, target.getUniqueID());
        assertEquals(17, target.experienceLevel);
        assertEquals(
            3,
            target.getInventoryEnderChest()
                .getStackInSlot(0).stackSize);

        target.inventory.mainInventory[4].stackSize = 2;
        EntityPlayer another = player("another");
        captured.apply(another);
        assertEquals(7, another.inventory.mainInventory[4].stackSize);
        NBTTagCompound exported = captured.write();
        exported.setFloat("health", 1);
        assertEquals(13, captured.health());
    }

    /** Bug regression: vanilla item serialization shares nested NBT unless the body boundary isolates it. */
    @Test
    void nestedItemTagsStayIndependentAcrossCaptureAndLiveRestore() {
        EntityPlayer source = player("tag-source");
        source.inventory.mainInventory[0] = new ItemStack(Items.diamond).setStackDisplayName("original hand");
        PlayerHandler.getPlayerBaubles(source).stackList[0] = new ItemStack(LOGIN_BAUBLE)
            .setStackDisplayName("original bauble");
        BodyState state = BodyState.capture(source);
        source.inventory.mainInventory[0].setStackDisplayName("changed source");
        PlayerHandler.getPlayerBaubles(source).stackList[0].setStackDisplayName("changed source");
        assertOriginalItemNames(state);

        state.equipment(0)
            .setStackDisplayName("changed equipment");
        state.bauble(0)
            .setStackDisplayName("changed bauble");
        for (ItemStack stack : state.carriedItems()) stack.setStackDisplayName("changed drop");
        assertOriginalItemNames(state);

        EntityPlayer target = player("tag-target");
        state.apply(target);
        target.inventory.mainInventory[0].setStackDisplayName("changed live hand");
        PlayerHandler.getPlayerBaubles(target).stackList[0].setStackDisplayName("changed live bauble");
        assertOriginalItemNames(state);
        state.hydrate(target);
        target.inventory.mainInventory[0].setStackDisplayName("changed login hand");
        PlayerHandler.getPlayerBaubles(target).stackList[0].setStackDisplayName("changed login bauble");
        assertOriginalItemNames(state);

        ItemStack hand = new ItemStack(Items.emerald).setStackDisplayName("replacement hand");
        BodyState equipped = state.withEquipment(0, hand, target);
        hand.setStackDisplayName("changed replacement");
        assertEquals(
            "replacement hand",
            equipped.equipment(0)
                .getDisplayName());
        assertOriginalItemNames(state);
        ItemStack armor = new ItemStack(Items.iron_chestplate).setStackDisplayName("replacement armor");
        BodyState armored = state.withArmor(new ItemStack[] { null, null, armor, null }, target);
        armor.setStackDisplayName("changed armor");
        assertEquals(
            "replacement armor",
            armored.equipment(3)
                .getDisplayName());
        ItemStack bauble = new ItemStack(LOGIN_BAUBLE).setStackDisplayName("consumed tank");
        BodyState consumed = state.withBauble(0, bauble);
        bauble.setStackDisplayName("changed caller");
        consumed.bauble(0)
            .setStackDisplayName("changed reader");
        assertEquals(
            "consumed tank",
            consumed.bauble(0)
                .getDisplayName());
        assertOriginalItemNames(state);
    }

    /** Product contract: imported and exported nested NBT cannot mutate an immutable body snapshot. */
    @Test
    void nestedNbtReadAndWriteBoundariesStayIsolated() {
        EntityPlayer source = player("nbt-source");
        source.inventory.mainInventory[0] = new ItemStack(Items.diamond).setStackDisplayName("original");
        NBTTagCompound input = BodyState.capture(source)
            .write();
        BodyState state = BodyState.read(input);
        input.getTagList("inventory", 10)
            .getCompoundTagAt(0)
            .getCompoundTag("tag")
            .getCompoundTag("display")
            .setString("Name", "changed input");
        assertEquals(
            "original",
            state.equipment(0)
                .getDisplayName());
        BodyState damaged = state.withHealth(9);
        NBTTagCompound output = damaged.write();
        output.getTagList("inventory", 10)
            .getCompoundTagAt(0)
            .getCompoundTag("tag")
            .getCompoundTag("display")
            .setString("Name", "changed output");
        assertEquals(
            "original",
            damaged.equipment(0)
                .getDisplayName());
        assertEquals(9, damaged.health());
        assertEquals(20, state.health());
    }

    private static void assertOriginalItemNames(BodyState state) {
        assertEquals(
            "original hand",
            state.equipment(0)
                .getDisplayName());
        assertEquals(
            "original bauble",
            state.bauble(0)
                .getDisplayName());
    }

    /** Capsule hunger consumes saturation first and keeps partial progress through cleanup and return. */
    @Test
    void capsuleMetabolismPersistsAcrossProjectionRecreationAndReturn() {
        EntityPlayer player = player("metabolism");
        player.worldObj.difficultySetting = EnumDifficulty.NORMAL;
        NBTTagCompound food = new NBTTagCompound();
        food.setInteger("foodLevel", 12);
        food.setFloat("foodSaturationLevel", 1);
        player.getFoodStats()
            .readNBT(food);
        BodyState initial = BodyState.capture(player);
        BodyState state = initial;
        for (int tick = 0; tick < 8; tick++) state = state.tickBiological(player, 16)
            .state();
        state = BodyState.read(state.write());
        state.apply(player);
        assertEquals(
            12,
            player.getFoodStats()
                .getFoodLevel());
        assertEquals(
            1,
            player.getFoodStats()
                .getSaturationLevel());
        state = BodyState.capture(player);
        for (int tick = 0; tick < 8; tick++) state = state.tickBiological(player, 16)
            .state();
        state.apply(player);
        assertEquals(
            12,
            player.getFoodStats()
                .getFoodLevel());
        assertEquals(
            0,
            player.getFoodStats()
                .getSaturationLevel());
        for (int tick = 0; tick < 16; tick++) state = state.tickBiological(player, 16)
            .state();
        state.apply(player);
        assertEquals(
            11,
            player.getFoodStats()
                .getFoodLevel());
        initial.apply(player);
        assertEquals(
            12,
            player.getFoodStats()
                .getFoodLevel(),
            "Ticking must not mutate an earlier snapshot");
        assertEquals(
            1,
            player.getFoodStats()
                .getSaturationLevel());
    }

    /** Starvation timing survives serialization, while peaceful mode cannot request starvation damage. */
    @Test
    void capsuleStarvationRetainsItsTimerAndRespectsPeacefulDifficulty() {
        EntityPlayer player = player("starvation");
        player.worldObj.difficultySetting = EnumDifficulty.NORMAL;
        NBTTagCompound food = new NBTTagCompound();
        food.setInteger("foodLevel", 0);
        food.setInteger("foodTickTimer", 78);
        player.getFoodStats()
            .readNBT(food);
        BodyState state = BodyState.capture(player);
        BodyState.Metabolism first = state.tickBiological(player, 1200);
        assertFalse(first.starvationDue());
        BodyState.Metabolism next = BodyState.read(
            first.state()
                .write())
            .tickBiological(player, 1200);
        assertTrue(next.starvationDue());
        assertFalse(
            next.state()
                .tickBiological(player, 1200)
                .starvationDue());
        player.worldObj.difficultySetting = EnumDifficulty.PEACEFUL;
        assertFalse(
            state.tickBiological(player, 1200)
                .starvationDue());
    }

    /** Product contract: body health bonuses survive inactivity without re-running player-only item callbacks. */
    @Test
    void inactiveProjectionRetainsBodyHealthModifiersWithoutDuplicatingThem() {
        EntityPlayer source = player("health-source");
        source.getEntityAttribute(SharedMonsterAttributes.maxHealth)
            .applyModifier(
                new AttributeModifier(UUID.fromString("3556fb85-4f3c-4c83-8122-cf1ab2bab250"), "body health", 8, 0)
                    .setSaved(false));
        source.setHealth(25);
        BodyState state = BodyState.read(
            BodyState.capture(source)
                .write());
        EntityPlayer idle = player("idle");

        state.applyPhysical(idle);
        assertEquals(28, idle.getMaxHealth());
        assertEquals(25, idle.getHealth());
        state.applyPhysical(idle);
        assertEquals(28, idle.getMaxHealth());
        assertEquals(
            25,
            state.withPhysicalState(idle)
                .health());
    }

    /** Integration contract: early login restores belongings and physiology without live equip actions. */
    @Test
    void loginHydrationRestoresPhysicalStateWithoutRunningEquipActions() {
        EntityPlayer source = player("login-source");
        source.inventory.mainInventory[1] = new ItemStack(Items.diamond, 4);
        PlayerHandler.getPlayerBaubles(source).stackList[0] = new ItemStack(LOGIN_BAUBLE);
        source.addPotionEffect(new PotionEffect(Potion.poison.id, 120, 1));
        source.setHealth(11);
        source.setAbsorptionAmount(3);
        source.setAir(80);
        ((EntityFireAccessor) source).galaxia$setFireTicks(41);
        source.setPosition(12, 76, 34);
        source.motionX = 0.25;
        source.fallDistance = 2.5F;
        GalaxiaPlayerProperties.get(source).lowOxygenDuration = 14;
        NBTTagCompound food = new NBTTagCompound();
        food.setInteger("foodLevel", 12);
        food.setInteger("foodTickTimer", 51);
        food.setFloat("foodSaturationLevel", 2.5F);
        food.setFloat("foodExhaustionLevel", 3.5F);
        source.getFoodStats()
            .readNBT(food);
        BodyState state = BodyState.capture(source);
        EntityPlayer target = player("login-target");
        target.experienceLevel = 17;
        target.dimension = 7;
        target.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 200));

        state.hydrate(target);

        assertNotNull(target.inventory.mainInventory[1]);
        assertEquals(4, target.inventory.mainInventory[1].stackSize);
        assertEquals(
            LOGIN_BAUBLE,
            PlayerHandler.getPlayerBaubles(target)
                .getStackInSlot(0)
                .getItem());
        assertEquals(17, target.experienceLevel, "Login must not repeat live equip actions");
        assertEquals(7, target.dimension, "State hydration cannot perform world transfer");
        assertEquals(11, target.getHealth());
        assertEquals(3, target.getAbsorptionAmount());
        assertEquals(80, target.getAir());
        assertEquals(41, ((EntityFireAccessor) target).galaxia$getFireTicks());
        assertEquals(12, target.posX);
        assertEquals(0.25, target.motionX);
        assertEquals(2.5F, target.fallDistance);
        assertEquals(14, GalaxiaPlayerProperties.get(target).lowOxygenDuration);
        assertNull(target.getActivePotionEffect(Potion.moveSpeed));
        assertEquals(
            120,
            target.getActivePotionEffect(Potion.poison)
                .getDuration());
        NBTTagCompound restoredFood = new NBTTagCompound();
        target.getFoodStats()
            .writeNBT(restoredFood);
        assertEquals(food, restoredFood);
    }

    private static EntityPlayer player(String name) {
        TestPlayer player = new TestPlayer(name);
        player.registerExtendedProperties(GalaxiaPlayerProperties.propertyId, new GalaxiaPlayerProperties());
        return player;
    }

    /** Harness exception: ordinary unit tests don't apply the runtime mixin transformations. */
    private static final class TestPlayer extends EntityPlayer implements EntityFireAccessor, LivingEquipmentAccessor {

        private int fireTicks;
        private final ItemStack[] previousEquipment = new ItemStack[5];

        private TestPlayer(String name) {
            super(
                new TestWorld(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        }

        @Override
        public int galaxia$getFireTicks() {
            return fireTicks;
        }

        @Override
        public void galaxia$setFireTicks(int ticks) {
            fireTicks = ticks;
        }

        @Override
        public ItemStack[] galaxia$previousEquipment() {
            return previousEquipment;
        }

        @Override
        @SuppressWarnings("unchecked")
        public HashMap<Integer, PotionEffect> galaxia$activePotions() {
            try {
                java.lang.reflect.Field field = EntityLivingBase.class.getDeclaredField("activePotionsMap");
                field.setAccessible(true);
                return (HashMap<Integer, PotionEffect>) field.get(this);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("The unit harness must bridge the runtime potion accessor", e);
            }
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 64, 0);
        }

        @Override
        public void addChatMessage(net.minecraft.util.IChatComponent message) {}

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return false;
        }
    }

    private static final class TestWorld extends World {

        private TestWorld() {
            super(
                null,
                "body-state",
                new WorldProviderSurface(),
                new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                new Profiler());
        }

        @Override
        public ChunkCoordinates getSpawnPoint() {
            return new ChunkCoordinates(0, 64, 0);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }

    private static final class LoginBauble extends Item implements IBauble {

        @Override
        public BaubleType getBaubleType(ItemStack stack) {
            return BaubleType.AMULET;
        }

        @Override
        public void onWornTick(ItemStack stack, EntityLivingBase player) {}

        @Override
        public void onEquipped(ItemStack stack, EntityLivingBase player) {
            ((EntityPlayer) player).experienceLevel++;
        }

        @Override
        public void onUnequipped(ItemStack stack, EntityLivingBase player) {}

        @Override
        public boolean canEquip(ItemStack stack, EntityLivingBase player) {
            return true;
        }

        @Override
        public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
            return true;
        }
    }
}
