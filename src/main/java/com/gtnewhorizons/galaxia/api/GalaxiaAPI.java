package com.gtnewhorizons.galaxia.api;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;
import static com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry.GALAXIA_DIMENSIONS;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.core.network.OxygenSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.hazards.HazardTemperature;
import com.gtnewhorizons.galaxia.registry.interfaces.IZeroGMovementProvider;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenMask;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemProtectionShield;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemSporeFilter;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemThermalProtection;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemWitherProtection;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IMachineBlockUpdateable;

/**
 * API underpinning planetary mechanics
 */
public final class GalaxiaAPI {

    /**
     * Gets the gravity on the planet, or returns 1 if failed
     *
     * @param e The entity to check effects on
     * @return Gravity on the entity, or 1 if failed
     */
    public static double getGravity(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.gravity();
        // for some cases clamping might be required
    }

    /**
     * Gets the effects on the planet, or returns defaults if failed
     *
     * @param e The entity to check effects on
     * @return Effects on the entity, or defaults if failed
     */
    public static EffectBuilder getEffects(Entity e) {
        if (e == null || e.worldObj == null) return new EffectBuilder();
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return new EffectBuilder();
        return def.effects();
    }

    public static EffectBuilder getEffects(int dimension) {
        DimensionDef def = SolarSystemRegistry.getById(dimension);
        if (def == null) return new EffectBuilder();
        return def.effects();
    }

    /**
     * Gets the air resistance on the planet, or returns 1 if failed
     *
     * @param e The entity to check effects on
     * @return Air resistance on the entity, or 1 if failed
     */
    public static double getAirResistance(Entity e) {
        if (e == null || e.worldObj == null) return 1.0;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return 1.0;
        return def.airResistance();
    }

    /**
     * Gets whether speed is cancelled
     *
     * @param e The entity to check effects on
     * @return Boolean : True => Speed cancellation enabled
     */
    public static boolean getSpeedCancelation(Entity e) {
        if (e == null || e.worldObj == null) return false;
        DimensionDef def = SolarSystemRegistry.getById(e.dimension);
        if (def == null) return false;
        return def.removeSpeedCancelation();
    }

    /**
     * @param player player
     * @return average amount of oxygen in all of player's tanks (synced with HUD
     *         bars)
     */
    public static float getPlayerOxygenLevel(EntityPlayer player) {
        float maximum = 0;
        float current = 0;
        for (int index : Galaxia.oxygenSlots) {
            ItemStack tank = BaublesApi.getBaubles(player)
                .getStackInSlot(index);
            if (tank != null && tank.getItem() instanceof ItemOxygenTank tankItem) {
                maximum += 1;
                current += tankItem.getPercentFull(tank);
            }
        }
        if (maximum == 0) return 0;
        return current / maximum;
    }

    /**
     * @param player player
     * @return player's temperature (synced with HUD bars)
     */
    public static float getPlayerTemperature(@Nonnull EntityPlayer player) {
        if (!isInGalaxiaDimension(player)) {
            return .5f;
        }
        EffectBuilder def = SolarSystemRegistry.getById(player.dimension)
            .effects();

        int temp = def.getTemperature(player.worldObj);
        int acceptableMaxTemp = HazardTemperature.getAcceptableMaxTemp(player);
        int acceptableMinTemp = HazardTemperature.getAcceptableMinTemp(player);

        if (temp < acceptableMinTemp) {
            return 0;
        } else if (temp >= acceptableMaxTemp) {
            return 1;
        }

        return (float) (temp - acceptableMinTemp) / (acceptableMaxTemp - acceptableMinTemp);
    }

    private static boolean hasBaubleInSlots(EntityPlayer player, int[] slots, Class<?> itemClass) {
        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;

        for (int slot : slots) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (stack != null && itemClass.isInstance(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static <T> int sumBaubleProtection(EntityPlayer player, int[] slots, Class<T> clazz,
        ToIntFunction<T> getter) {
        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return 0;

        int total = 0;
        for (int i : slots) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack != null && clazz.isInstance(stack.getItem())) {
                T item = clazz.cast(stack.getItem());
                total += getter.applyAsInt(item);
            }
        }
        return total;
    }

    public static boolean hasOxygenTank(@Nonnull EntityPlayer player) {
        return hasBaubleInSlots(player, Galaxia.oxygenSlots, ItemOxygenTank.class);
    }

    public static boolean hasOxygenmask(@Nonnull EntityPlayer player) {
        return hasBaubleInSlots(player, Galaxia.oxygenMaskSlots, ItemOxygenMask.class);
    }

    public static boolean hasSporeFilter(@Nonnull EntityPlayer player) {
        return hasBaubleInSlots(player, Galaxia.sporeFilterSlots, ItemSporeFilter.class);
    }

    public static boolean hasWitherProtection(@Nonnull EntityPlayer player) {
        return hasBaubleInSlots(player, Galaxia.witherSlots, ItemWitherProtection.class);
    }

    public static boolean hasThermalProtection(@Nonnull EntityPlayer player) {
        return hasBaubleInSlots(player, Galaxia.thermalSlot, ItemThermalProtection.class);
    }

    public static int getRadiationProtection(@Nonnull EntityPlayer player) {
        return sumBaubleProtection(
            player,
            Galaxia.shieldSlots,
            ItemProtectionShield.class,
            ItemProtectionShield::getRadiationProtection);
    }

    public static int getThermalProtection(@Nonnull EntityPlayer player, boolean heat) {
        return sumBaubleProtection(
            player,
            Galaxia.thermalSlot,
            ItemThermalProtection.class,
            item -> heat ? item.getHeatProtection() : item.getColdProtection());
    }

    public static int getPressureProtection(@Nonnull EntityPlayer player, boolean highPressure) {
        return sumBaubleProtection(
            player,
            Galaxia.shieldSlots,
            ItemProtectionShield.class,
            item -> highPressure ? item.getPressureProtectionHigh() : item.getPressureProtectionLow());
    }

    private static IZeroGMovementProvider getZeroGMovementProvider(EntityPlayer player) {
        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return null;

        for (int slot : Galaxia.rcsSlot) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof IZeroGMovementProvider provider) {
                return provider;
            }
        }
        return null;
    }

    public static void setZeroGMovement(@Nonnull EntityPlayer player, boolean enabled) {
        IZeroGMovementProvider provider = getZeroGMovementProvider(player);
        if (provider != null) provider.setEnabled(enabled);
    }

    public static boolean hasZeroGMovementCapability(@Nonnull EntityPlayer player) {
        if (ConfigPlayer.ConfigPlayerGlobal.applyZeroGravityMovement && player.capabilities.isCreativeMode) {
            return true;
        }
        IZeroGMovementProvider provider = getZeroGMovementProvider(player);
        return provider != null && provider.isEnabled();
    }

    public static boolean checkOxygenAndDrain(@Nonnull EntityPlayer player, int oxygenPercent) {
        boolean found = false;

        for (int index : Galaxia.oxygenSlots) {
            ItemStack tank = BaublesApi.getBaubles(player)
                .getStackInSlot(index);
            if (tank == null || !(tank.getItem() instanceof ItemOxygenTank tankItem)) {
                continue;
            }

            if (tankItem.drainTank(tank, (100 - oxygenPercent) / 5)) {
                GALAXIA_NETWORK
                    .sendTo(new OxygenSyncPacket(index, tankItem.getCurrentOxygen(tank)), (EntityPlayerMP) player);

                found = true;
                break;
            }
        }
        return found;
    }

    /**
     * Returns the capacity clusters for the given facility and capacity module kind.
     *
     * @param facilityId the facility asset ID
     * @param kind       the capacity module kind (STORAGE, TANK, BATTERY)
     * @return list of capacity clusters; empty if the facility is not found, has no layout,
     *         or the kind is not a capacity module
     */
    public static List<CapacityCluster> getCapacityClusters(CelestialAsset.ID facilityId, FacilityModuleKind kind) {
        CelestialAsset asset = CelestialAssetStore.findAsset(facilityId);
        if (!(asset instanceof AutomatedFacility facility) || !facility.hasStationLayout()) {
            return Collections.emptyList();
        }
        return facility.layoutCache()
            .getCapacityClusters(kind);
    }

    /**
     * Returns the cached maintenance coverage coordinates for the given facility.
     * Coverage tiles are the 8 surrounding tiles of each enabled Maintenance Bay.
     *
     * @param facilityId the facility asset ID
     * @return set of covered tile coordinates; empty if the facility is not found or has no layout
     */
    public static Set<StationTileCoord> getMaintenanceCoverage(CelestialAsset.ID facilityId) {
        CelestialAsset asset = CelestialAssetStore.findAsset(facilityId);
        if (!(asset instanceof AutomatedFacility facility) || !facility.hasStationLayout()) {
            return Collections.emptySet();
        }
        return facility.layoutCache()
            .getMaintenanceCoverage();
    }

    public static boolean isInGalaxiaDimension(Entity e) {
        return GALAXIA_DIMENSIONS.contains(e.dimension);
    }

    /**
     * static ResourceLocation overload that doesn't require "galaxia" as input
     *
     * @param location file location in galaxia folder
     * @return file location
     */
    public static ResourceLocation LocationGalaxia(String location) {
        return new ResourceLocation(Galaxia.MODID, location);
    }

    /**
     * Static shortcut to FML bus registration
     *
     * @param obj object to register
     */
    public static void FMLBusRegister(Object obj) {
        FMLCommonHandler.instance()
            .bus()
            .register(obj);
    }

    /**
     * Static shortcut to forge bus registration
     *
     * @param obj object to register
     */
    public static void ForgeBusRegister(Object obj) {
        MinecraftForge.EVENT_BUS.register(obj);
    }

    /**
     * Checks if gt is loaded (who would guess)
     */
    public static boolean isGregTechLoaded() {
        return Loader.isModLoaded("gregtech");
    }

    public static @Nullable TileStation getStationAround(@Nonnull World world, int dim, int x, int y, int z) {
        CelestialObjectId id = GalaxiaCelestialAPI.getObjectFromDimension(dim);
        if (id == CelestialObjectId.INVALID) return null;

        Set<CelestialAsset.ID> assets = CelestialAssetStore.getAssetsOnBody(id);
        for (var assetId : assets) {
            CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
            if (!(asset instanceof Station station)) continue;
            if (station.isDisabled()) continue;
            BlockPos pos = station.getController();
            if (pos == null) continue;
            if (!(world.getTileEntity(pos.x(), pos.y(), pos.z()) instanceof TileStation tileStation)) continue;

            if (tileStation.isInside(x, y, z)) return tileStation;
        }

        return null;
    }

    public static boolean isMachineBlock(Block block, int blockMetadata) {
        if (isGregTechLoaded()) {
            return GregTechAPI.isMachineBlock(block, blockMetadata);
        }

        return false;
    }

    /**
     * Adds a Multi-Machine Block, like my Machine Casings for example. You should call @causeMachineUpdate
     * in @Block.breakBlock and in {@link Block#onBlockAdded} of your registered Block. You don't need to register
     * TileEntities which implement {@link IMachineBlockUpdateable}
     *
     * @param aBlock the Block
     * @param aMeta  the Metadata of the Blocks as Bitmask! -1 or ~0 for all Meta-values
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean registerMachineBlock(Block aBlock, int aMeta) {
        if (isGregTechLoaded()) {
            return GregTechAPI.registerMachineBlock(aBlock, -1);
        }

        return false;
    }

    /**
     * Causes a Machineblock Update This update will cause surrounding MultiBlock Machines to update their
     * Configuration. You should call this Function in @Block.breakBlock and in @Block.onBlockAdded of your Machine.
     *
     * @param aWorld is being the World
     * @param aX     is the X-Coord of the update causing Block
     * @param aY     is the Y-Coord of the update causing Block
     * @param aZ     is the Z-Coord of the update causing Block
     */
    public static boolean causeMachineUpdate(World aWorld, int aX, int aY, int aZ) {
        if (isGregTechLoaded()) {
            return GregTechAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }

        return false;
    }

}
