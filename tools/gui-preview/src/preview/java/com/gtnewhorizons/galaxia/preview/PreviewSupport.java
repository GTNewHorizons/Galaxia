package com.gtnewhorizons.galaxia.preview;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import dev.modularui.preview.PreviewEntrypoint;
import cpw.mods.fml.relauncher.FMLInjectionData;
import gregtech.GTMod;
import gregtech.api.enums.Materials;
import gregtech.common.GTProxy;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.gtnewhorizon.gtnhlib.teams.TeamManagerClient;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import sun.misc.Unsafe;

final class PreviewSupport {

    private PreviewSupport() {}

    static void initializeClient() {
        initializeFmlSide();
        initializeFmlData();
        initializeLaunchBlackboard();
        initializeGregTechProxy();
        setVanillaBootstrap(true);
        try {
            if (!hasRawRegistryEntry(Block.blockRegistry, "water")) Block.registerBlocks();
            if (!hasRawRegistryEntry(Block.blockRegistry, "grass")) {
                throw new IllegalStateException("Vanilla block registration did not populate grass");
            }
            Class.forName("net.minecraft.init.Blocks", true, PreviewSupport.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to initialize vanilla block constants", exception);
        } finally {
            setVanillaBootstrap(false);
        }
        registerVanillaItem(264, "diamond", "item.diamond");
        registerVanillaItem(265, "iron_ingot", "item.ingotIron");
        registerVanillaItem(266, "gold_ingot", "item.ingotGold");
        registerVanillaItem(288, "feather", "item.feather");
        registerVanillaItem(331, "redstone", "item.redstone");
        if (Materials.Air.getGas(1L) == null) {
            Fluid air = FluidRegistry.getFluid("air");
            if (air == null) {
                air = new Fluid("air");
                FluidRegistry.registerFluid(air);
            }
            Materials.Air.mGas = air;
        }
        EntityClientPlayerMP player = player();
        player.dimension = 0;
    }

    static void initializeStarmap() {
        initializeClient();
        OrbitalMechanics.registerMinorBodyResolver(AsteroidFieldOrbitResolver.INSTANCE);
        CelestialRegistry.freezeAndBake();
    }

    static void initializePreviewTeam() {
        initializeClient();
        if (!TeamDataRegistry.getRegisteredKeys().contains(GalaxiaTeamData.ID)) {
            TeamDataRegistry.register(GalaxiaTeamData.ID, GalaxiaTeamData::new);
        }
        TeamManager.clear();
        Team team = TeamManager.createTeam("Preview", player().getUniqueID());
        setStaticField(TeamManagerClient.class, "TEAM", team);
    }

    static void initializeFacilityModules() {
        initializeClient();
        if (!FacilityModuleRegistry.isRegistered(FacilityModuleKind.POWER)) FacilityModuleRegistry.init();
    }

    static PanelSyncManager sync(PreviewEntrypoint.Context context) {
        return (PanelSyncManager) context.panelSyncManager();
    }

    static GuiData guiData() {
        return new GuiData(player());
    }

    static PosGuiData posGuiData(PreviewWorld world, int x, int y, int z) {
        return new PosGuiData(player(), x, y, z) {
            @Override
            public net.minecraft.world.World getWorld() {
                return world;
            }
        };
    }

    static UISettings settings() {
        return new UISettings();
    }

    static EntityClientPlayerMP player() {
        return Minecraft.getMinecraft().thePlayer;
    }

    static <T extends net.minecraft.tileentity.TileEntity> T clientTile(T tile) {
        tile.setWorldObj(PreviewWorld.create());
        return tile;
    }

    static void setField(Object target, String name, Object value) {
        setField(target.getClass(), target, name, value);
    }

    static void setStaticField(Class<?> type, String name, Object value) {
        setField(type, null, name, value);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) {
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not set preview field " + name, exception);
            }
        }
        throw new IllegalArgumentException("Preview field does not exist: " + name);
    }

    private static void setVanillaBootstrap(boolean active) {
        try {
            Class<?> loader = Class.forName("cpw.mods.fml.common.Loader");
            loader.getMethod(active ? "beginVanillaBootstrap" : "endVanillaBootstrap").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Preview Loader does not support vanilla registry bootstrapping", exception);
        }
    }

    private static boolean hasRawRegistryEntry(Object registry, String name) {
        try {
            return registry.getClass().getMethod("getRaw", String.class).invoke(registry, name) != null;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect the vanilla block registry", exception);
        }
    }

    private static void registerVanillaItem(int id, String name, String unlocalizedName) {
        if (!Item.itemRegistry.containsKey(name)) {
            Item.itemRegistry.addObject(id, name, new Item().setUnlocalizedName(unlocalizedName));
        }
    }

    private static void initializeFmlSide() {
        try {
            Class<?> log = Class.forName("cpw.mods.fml.relauncher.FMLRelaunchLog");
            Field field = log.getDeclaredField("side");
            field.setAccessible(true);
            if (field.get(null) == null) field.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize the local FML side", exception);
        }
    }

    private static void initializeLaunchBlackboard() {
        if (Launch.blackboard == null) Launch.blackboard = new HashMap<>();
        Launch.blackboard.putIfAbsent("fml.deobfuscatedEnvironment", false);
    }

    private static void initializeFmlData() {
        Object[] data = FMLInjectionData.data();
        if (data[6] == null) {
            setField(FMLInjectionData.class, null, "minecraftHome", new File(".").getAbsoluteFile());
        }
    }

    private static void initializeGregTechProxy() {
        if (GTMod.proxy != null) return;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            GTMod.proxy = (GTProxy) ((Unsafe) field.get(null)).allocateInstance(GTProxy.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the local GregTech preview proxy", exception);
        }
    }
}
