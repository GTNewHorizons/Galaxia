package com.gtnewhorizons.galaxia.registry.items.special;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Physical claim to the concrete module retained in the world's Rocket registry. */
public final class ItemRocketModule extends Item {

    public static ItemStack dropped(Rocket.ModuleDrop drop) {
        var stack = new ItemStack(GalaxiaItemList.RECOVERED_ROCKET_MODULE.getItem());
        stack.setTagCompound(drop.serializeNBT());
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon(Galaxia.TEXTURE_PREFIX + "parts/heavy_duty_plate_base");
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote || stack.stackSize <= 0
            || !stack.hasTagCompound()
            || !(world.getTileEntity(x, y, z) instanceof TileEntityTransportPlatform pad)
            || !pad.machineIdentity()
                .canUse(player, TeamAction.CONFIGURE_LOGISTICS))
            return false;
        Rocket.ModuleDrop drop;
        try {
            drop = Rocket.ModuleDrop.deserializeNBT(stack.getTagCompound());
        } catch (IllegalArgumentException | IllegalStateException invalidClaim) {
            return false;
        }
        var registry = Galaxia.proxy.serverRuntime()
            .rockets();
        var rocket = registry.find(drop.vehicleId());
        var silo = pad.serviceSiloId();
        var team = GTTeamsCompat.getTeam(player);
        var target = silo == null ? null : CelestialAssetStore.findEndpoint(silo);
        var padEndpoint = CelestialAssetStore.findEndpoint(
            pad.machineIdentity()
                .id());
        if (rocket == null || silo == null
            || team == null
            || !pad.isAvailableTo(team, rocket.bodyKey())
            || target == null
            || target.endpoint()
                .kind() != CelestialAsset.Endpoint.Kind.SILO
            || !team.equals(CelestialAssetStore.getTeamId(target.asset().assetId))
            || !rocket.bodyKey()
                .equals(target.asset().celestialObjectKey)
            || !pad.reserveArrival(registry, rocket)
            || padEndpoint == null
            || !rocket.recoverDrop(team, drop, VehicleSchedule.Place.at(padEndpoint), silo)) return false;
        stack.stackSize--;
        pad.markDirty();
        return true;
    }
}
