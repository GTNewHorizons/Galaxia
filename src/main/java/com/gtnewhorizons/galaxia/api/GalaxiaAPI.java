package com.gtnewhorizons.galaxia.api;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;
import static com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry.GALAXIA_DIMENSIONS;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.core.network.OxygenSyncPacket;
import com.gtnewhorizons.galaxia.registry.capabilities.ZeroGMovementProvider;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.hazards.HazardTemperature;
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
    public static boolean cancelSpeed(Entity e) {
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

        int temp = def.getTemperature(player);
        int acceptableMaxTemp = HazardTemperature.getAcceptableMaxTemp(player);
        int acceptableMinTemp = HazardTemperature.getAcceptableMinTemp(player);

        if (temp < acceptableMinTemp) {
            return 0.f;
        } else if (temp > acceptableMaxTemp) {
            return 1.f;
        }

        return (float) (temp - acceptableMinTemp) / (acceptableMaxTemp - acceptableMinTemp);
    }

    public static boolean hasOxygenTank(@Nonnull EntityPlayer player) {
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;

        for (int slot : Galaxia.oxygenSlots) {
            var stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemOxygenTank) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasOxygenmask(@Nonnull EntityPlayer player) {
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;

        for (int slot : Galaxia.oxygenMaskSlots) {
            var stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemOxygenMask) {
                return true;
            }
        }
        return false;
    }

    public static int getRadiationProtection(@Nonnull EntityPlayer player) {
        IInventory baubles = BaublesApi.getBaubles(player);
        int protection = 0;
        if (baubles == null) {
            return protection;
        }

        for (int i : Galaxia.shieldSlots) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemProtectionShield)) {
                continue;
            }
            ItemProtectionShield item = (ItemProtectionShield) stack.getItem();
            protection += item.getRadiationProtection();
        }
        return protection;
    }

    public static void setZeroGMovement(@Nonnull EntityPlayer player, boolean enabled) {
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return;

        for (int slot : Galaxia.reactionControlSystemSlot) {
            var stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ZeroGMovementProvider provider) {
                provider.setEnabled(enabled);

                return;
            }
        }
    }

    public static boolean hasSporeFilter(@Nonnull EntityPlayer player) {
        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles == null) {
            return false;
        }

        for (int i : Galaxia.sporeFilterSlots) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemSporeFilter)) {
                continue;
            }
            return true;

        }
        return false;
    }

    public static boolean hasWitherProtection(@Nonnull EntityPlayer player) {
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;

        for (int slot : Galaxia.witherSlots) {
            var stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemWitherProtection) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasThermalProtection(@Nonnull EntityPlayer player) {
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;

        for (int slot : Galaxia.thermalSlot) {
            var stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemThermalProtection) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasZeroGMovementCapability(@Nonnull EntityPlayer player) {
        if (ConfigPlayer.ConfigPlayerGlobal.applyZeroGravityMovement && player.capabilities.isCreativeMode) return true;
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return false;

        for (int slot : Galaxia.reactionControlSystemSlot) {
            var stack = baubles.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ZeroGMovementProvider provider) {
                return provider.isEnabled();
            }
        }
        return false;
    }

    public static int getThermalProtection(@Nonnull EntityPlayer player, boolean heat) {
        IInventory baubles = BaublesApi.getBaubles(player);
        int protection = 0;
        if (baubles == null) {
            return protection;
        }

        for (int i : Galaxia.thermalSlot) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemThermalProtection)) continue;

            ItemThermalProtection item = (ItemThermalProtection) stack.getItem();
            protection += heat ? item.getHeatProtection() : item.getColdProtection();
        }
        return protection;
    }

    public static int getPressureProtection(@Nonnull EntityPlayer player, boolean highPressure) {
        IInventory baubles = BaublesApi.getBaubles(player);
        int protection = 0;
        if (baubles == null) {
            return protection;
        }

        for (int i : Galaxia.shieldSlots) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemProtectionShield)) continue;

            ItemProtectionShield item = (ItemProtectionShield) stack.getItem();
            protection += highPressure ? item.getPressureProtectionHigh() : item.getPressureProtectionLow();
        }
        return protection;
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

    public static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    public static String format(String key, Object... objects) {
        return StatCollector.translateToLocalFormatted(key, objects);
    }
}
