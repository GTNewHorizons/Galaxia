package com.gtnewhorizons.galaxia.manual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.Display;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner.RoutePriority;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.VehicleSchedule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities.EntityRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE.TransportEndpoint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketLoadingPort;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Manual world fixture. Uses the real vehicle, endpoint and machine owners. */
@Mod(
    modid = "galaxia_rocket_lab",
    name = "Rocket Gameplay Lab (local)",
    version = "local",
    dependencies = "required-after:galaxia;required-after:gregtech")
public final class RocketGameplayLab {

    private static final double REPAIR_THRESHOLD = 75;
    private static volatile boolean ready;
    private EntityPlayerMP pending;
    private TileEntitySilo silo;
    private TileEntityTransportPlatform pad;
    private TileEntityModuleAssembler firstAssembler;
    private TileEntityModuleAssembler secondAssembler;
    private Rocket rocket;
    private boolean powered = true;
    private boolean serviceObserved;
    private boolean serviceVerified;
    private final Map<FluidKey, Long> fuelTargets = new LinkedHashMap<>();
    private final ItemStackWrapper iron = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
    private final ItemStackWrapper gold = ItemStackWrapper.of(new ItemStack(Items.gold_ingot));

    @Mod.EventHandler
    public void init(FMLPostInitializationEvent event) {
        ConfigMachines.loadingPort.tankCapacity = 256000;
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(new OpenWorld());
    }

    @Mod.EventHandler
    public void commands(FMLServerStartingEvent event) {
        ready = false;
        event.registerServerCommand(new Command());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player && rocket == null) pending = player;
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pending != null && GTTeamsCompat.getTeam(pending) != null) {
            EntityPlayerMP player = pending;
            pending = null;
            try {
                prepare(player);
                ready = true;
                System.out.println(
                    "ROCKET_LAB_READY vehicle=" + rocket.id() + " silo=0,128,0 pad=32,128,0 repair=75 start=manual");
            } catch (RuntimeException ex) {
                Galaxia.LOG.error("ROCKET_LAB_SETUP_FAILED", ex);
                say(player, "Setup failed. See launch.log.");
            }
        }
        if (powered) {
            if (firstAssembler != null) firstAssembler.injectEnergyUnits(ForgeDirection.SOUTH, 480, 16);
            if (secondAssembler != null) secondAssembler.injectEnergyUnits(ForgeDirection.SOUTH, 480, 16);
        }
        if (silo != null) {
            var file = new java.io.File(
                ((net.minecraft.world.storage.SaveHandler) silo.getWorldObj()
                    .getSaveHandler()).getWorldDirectory(),
                "lab-command.txt");
            if (file.isFile()) try {
                var lines = java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                java.nio.file.Files.delete(file.toPath());
                for (Object candidate : silo.getWorldObj().playerEntities)
                    if (candidate instanceof EntityPlayerMP player) {
                        for (String line : lines) new Command().processCommand(
                            player,
                            line.trim()
                                .split(" +"));
                        break;
                    }
            } catch (Exception ex) {
                Galaxia.LOG.error("Rocket lab command failed", ex);
            }
        }
        if (rocket != null && rocket.service() != null) {
            if (!serviceObserved) {
                serviceObserved = true;
                System.out.println(
                    "ROCKET_LAB_REPAIR_STARTED vehicle=" + rocket.id()
                        + " returnSilo="
                        + rocket.service()
                            .returnSiloId()
                        + " repairing="
                        + rocket.service()
                            .repairs()
                            .size()
                        + " skipped="
                        + (rocket.modules()
                            .size()
                            - rocket.service()
                                .repairs()
                                .size()));
            }
            if (!serviceVerified && rocket.service()
                .reassembled()
                && !rocket.isRunning()
                && rocket.status() == Rocket.Status.PHYSICAL
                && silo.machineIdentity()
                    .id()
                    .equals(rocket.currentEndpointId())
                && rocket.id()
                    .equals(silo.vehicleId())
                && rocket.service()
                    .repairs()
                    .size() == 2
                && rocket.modules()
                    .stream()
                    .allMatch(
                        module -> rocket.service()
                            .repairs()
                            .containsKey(module.id()) ? module.durabilityPercent() == 100
                                : module.durabilityPercent() > REPAIR_THRESHOLD)) {
                serviceVerified = true;
                System.out.println(
                    "ROCKET_LAB_SERVICE_RETURN_VERIFIED vehicle=" + rocket.id()
                        + " silo="
                        + silo.machineIdentity()
                            .id()
                        + " modules="
                        + rocket.modules()
                            .size());
            }
        }
    }

    private void prepare(EntityPlayerMP player) {
        WorldServer world = (WorldServer) player.worldObj;
        for (int x = -16; x <= 52; x++) for (int z = -14; z <= 34; z++) {
            world.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
            world.setBlock(x, 127, z, (x % 8 == 0 && z % 8 == 0) ? Blocks.glowstone : Blocks.stonebrick, 0, 2);
        }
        world.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        world.setWorldTime(6000);
        player.setGameType(WorldSettings.GameType.CREATIVE);
        player.capabilities.isFlying = true;
        player.sendPlayerAbilities();
        player.playerNetServerHandler.setPlayerLocation(-13.5, 140, -13.5, -40, 20);
        UUID team = GTTeamsCompat.getTeam(player);
        GTTeamsCompat.awardAchievement(team, Achievement.STAR_MAP_ACTIVATION);

        world.setBlock(0, 128, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0, 3);
        silo = (TileEntitySilo) world.getTileEntity(0, 128, 0);
        prepareEndpoint(silo, team);
        attachPort(silo);
        attachTerminal(silo);
        var design = new RocketBlueprint();
        int row = 0;
        for (var definition : List.of(
            RocketPartRegistry.CAPSULE_T1,
            RocketPartRegistry.STORAGE_T1,
            RocketPartRegistry.FUEL_TANK_T1,
            RocketPartRegistry.ENGINE_T1)) {
            if (!design.addPart(new RocketPartInstance(definition, 0, row, 0, false)))
                throw new IllegalStateException("Basic blueprint does not fit");
            row += definition.height();
        }
        silo.setDesignBlueprint(design);
        silo.orderModules();
        rocket = silo.rocket();
        if (rocket == null) throw new IllegalStateException("Native Silo did not accept construction");
        for (var slot : rocket.buildSlots()) {
            var definition = slot.part()
                .def();
            double durability = definition == RocketPartRegistry.STORAGE_T1
                || definition == RocketPartRegistry.FUEL_TANK_T1 ? 40 : 95;
            if (!rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    slot,
                    durability,
                    new Rocket.Holder(
                        Rocket.HolderKind.SILO_BUFFER,
                        silo.machineIdentity()
                            .id()))))
                throw new IllegalStateException("Prebuilt module was rejected");
        }
        if (!rocket.assemble(
            silo.machineIdentity()
                .id(),
            VehicleSchedule.Place.at(
                CelestialAssetStore.findEndpoint(
                    silo.machineIdentity()
                        .id()))))
            throw new IllegalStateException("Native vehicle did not assemble");
        for (var module : rocket.modules()) module.part()
            .def()
            .storage()
            .propellantCapacities()
            .forEach((key, amount) -> fuelTargets.merge(key, amount, Long::sum));
        fuelTargets.forEach((key, amount) -> rocket.refuel(key, amount, false));
        rocket.insertCargo(iron, 64, false);

        world.setBlock(32, 128, 0, GalaxiaBlocksEnum.LANDING_PAD_CONTROLLER.get(), 0, 3);
        pad = (TileEntityTransportPlatform) world.getTileEntity(32, 128, 0);
        prepareEndpoint(pad, team);
        attachPort(pad);
        attachTerminal(pad);
        var unavailableAssembler = assembler(world, player, 32, 129, 10);
        firstAssembler = assembler(world, player, 14, 129, 18);
        secondAssembler = assembler(world, player, 32, 129, 18);
        int input = 0;
        var repairItems = new LinkedHashMap<ItemStackWrapper, Long>();
        for (var definition : List.of(
            RocketPartRegistry.CAPSULE_T1,
            RocketPartRegistry.STORAGE_T1,
            RocketPartRegistry.FUEL_TANK_T1,
            RocketPartRegistry.ENGINE_T1)) {
            definition.repairBill()
                .inputs()
                .forEach(
                    (key, amount) -> {
                        if (key instanceof ItemStackWrapper item) repairItems.merge(item, amount, Long::sum);
                    });
        }
        for (var key : repairItems.keySet()) {
            firstAssembler.setInventorySlotContents(input, key.toStack(64));
            secondAssembler.setInventorySlotContents(input++, key.toStack(64));
        }
        connect(
            world,
            position(silo.getGantryTerminal()),
            new BlockPos(-4, 129, 5),
            new BlockPos(30, 129, 5),
            new BlockPos(31, 128, 5),
            new BlockPos(32, 128, 5),
            position(pad.getGantryTerminal()));
        connect(
            world,
            new BlockPos(30, 129, 5),
            new BlockPos(30, 129, 7),
            position(unavailableAssembler.getGantryTerminal()));
        connect(world, new BlockPos(12, 129, 5), new BlockPos(12, 129, 13), new BlockPos(30, 129, 13));
        connect(
            world,
            new BlockPos(12, 129, 13),
            new BlockPos(12, 129, 15),
            position(firstAssembler.getGantryTerminal()));
        connect(
            world,
            new BlockPos(30, 129, 13),
            new BlockPos(30, 129, 15),
            position(secondAssembler.getGantryTerminal()));
        if (!pad.selectServiceSilo(
            silo.machineIdentity()
                .id()))
            throw new IllegalStateException("Pad rejected its connected service Silo");
        rename(silo, "LAB A: Silo");
        rename(pad, "LAB B: Landing Pad");
        var sourcePort = (TileEntityRocketLoadingPort) silo.loadingInventory();
        var padPort = (TileEntityRocketLoadingPort) pad.loadingInventory();
        sourcePort.insertIntoOwnStorage(iron, 512);
        fuelTargets.forEach((key, amount) -> { sourcePort.insertIntoOwnFluidStorage(key, 200000); });
        configure();
        sign(world, -5, 128, -3, "A: SAME SILO", "Ready rocket", "/rocketlab start", "Watch from outside");
        sign(world, 28, 128, -3, "B: LANDING PAD", "Unload / repair", "Repair threshold 75%", "Return via Gantry");
        sign(world, 30, 128, 8, "ASSEMBLER 1", "No power", "No repair materials", "UNAVAILABLE");
        sign(world, 12, 128, 14, "ASSEMBLER 2", "Powered", "Repair materials", "READY");
        sign(world, 30, 128, 14, "ASSEMBLER 3", "Powered", "Repair materials", "READY");
        world.setBlock(-8, 128, 1, Blocks.chest);
        var chest = (TileEntityChest) world.getTileEntity(-8, 128, 1);
        chest.setInventorySlotContents(0, new ItemStack(Items.gold_ingot, 64));
        chest.setInventorySlotContents(1, new ItemStack(Items.iron_ingot, 64));
        chest.setInventorySlotContents(2, new ItemStack(GalaxiaItemList.ITEM_GALACTIC_MAP.getItem()));
        player.inventory.addItemStackToInventory(new ItemStack(GalaxiaItemList.ITEM_GALACTIC_MAP.getItem()));
        player.inventory.addItemStackToInventory(new ItemStack(Items.gold_ingot, 32));
        player.inventory.currentItem = 8;
        world.setSpawnLocation(-10, 129, -8);
        player.playerNetServerHandler.setPlayerLocation(-13.5, 140, -13.5, -40, 20);
        silo.sync();
        help(player);
        System.out.println(
            "ROCKET_LAB_PORTS silo=" + sourcePort.xCoord
                + ","
                + sourcePort.yCoord
                + ","
                + sourcePort.zCoord
                + " pad="
                + padPort.xCoord
                + ","
                + padPort.yCoord
                + ","
                + padPort.zCoord);
    }

    private static void rename(TransportEndpoint endpoint, String name) {
        var ref = CelestialAssetStore.findEndpoint(
            endpoint.machineIdentity()
                .id());
        ref.asset()
            .renameEndpoint(
                endpoint.machineIdentity()
                    .id(),
                name);
    }

    private static void prepareEndpoint(TransportEndpoint endpoint, UUID team) {
        endpoint.machineIdentity()
            .claim(team);
        endpoint.endpointBinding()
            .selectStandalone();
        endpoint.setPlacedFacing(ForgeDirection.NORTH);
        var tile = (GalaxiaMultiblockBase<?>) endpoint;
        tile.construct(new ItemStack(Items.stick), false);
        tile.markStructureDirty();
        tile.updateEntity();
        endpoint.refreshEndpoint();
        if (!endpoint.isStructureValid()) throw new IllegalStateException("Unformed endpoint " + tile.getClass());
    }

    private static void attachPort(TransportEndpoint endpoint) {
        if (endpoint.loadingInventory() != null) return;
        replace(
            (GalaxiaMultiblockBase<?>) endpoint,
            GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(),
            GalaxiaBlocksEnum.ROCKET_LOADING_PORT.get(),
            () -> endpoint.loadingInventory() != null);
    }

    private static void attachTerminal(TransportEndpoint endpoint) {
        if (endpoint.getGantryTerminal() != null) return;
        replace(
            (GalaxiaMultiblockBase<?>) endpoint,
            GalaxiaBlocksEnum.LAUNCHPAD_SHEETING.get(),
            GalaxiaBlocksEnum.GANTRY_TERMINAL.get(),
            () -> endpoint.getGantryTerminal() != null);
    }

    private static void replace(GalaxiaMultiblockBase<?> tile, Block casing, Block replacement,
        java.util.function.BooleanSupplier captured) {
        var world = tile.getWorldObj();
        for (int x = tile.xCoord - 7; x <= tile.xCoord + 7; x++)
            for (int y = tile.yCoord - 3; y <= tile.yCoord + 12; y++)
                for (int z = tile.zCoord - 10; z <= tile.zCoord + 10; z++) {
                    if (world.getBlock(x, y, z) != casing) continue;
                    world.setBlock(x, y, z, replacement, 0, 3);
                    tile.markStructureDirty();
                    tile.updateEntity();
                    if (tile.isStructureValid() && captured.getAsBoolean()) return;
                    world.setBlock(x, y, z, casing, 0, 3);
                }
        throw new IllegalStateException("No structural slot for " + replacement);
    }

    private static TileEntityModuleAssembler assembler(WorldServer world, EntityPlayerMP player, int x, int y, int z) {
        world.setBlock(x, y, z, GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get(), 0, 3);
        var tile = (TileEntityModuleAssembler) world.getTileEntity(x, y, z);
        tile.machineIdentity()
            .claim(GTTeamsCompat.getTeam(player));
        tile.setPlacedFacing(ForgeDirection.NORTH);
        tile.construct(new ItemStack(Items.stick), false);
        tile.markStructureDirty();
        tile.updateEntity();
        if (tile.getGantryTerminal() == null) replace(
            tile,
            GalaxiaBlocksEnum.RUSTY_PANEL.get(),
            GalaxiaBlocksEnum.GANTRY_TERMINAL.get(),
            () -> tile.getGantryTerminal() != null);
        return tile;
    }

    private static BlockPos position(TileEntity tile) {
        return new BlockPos(tile.xCoord, tile.yCoord, tile.zCoord);
    }

    private static void connect(WorldServer world, BlockPos... corners) {
        TileEntityGantry previous = null;
        for (int segment = 1; segment < corners.length; segment++) {
            var current = corners[segment - 1];
            var end = corners[segment];
            while (true) {
                TileEntity tile = current.getTE(world);
                if (!(tile instanceof TileEntityGantry)) {
                    if (!world.isAirBlock(current.x(), current.y(), current.z()))
                        throw new IllegalStateException("Lab rail intersects a machine at " + current);
                    world.setBlock(current.x(), current.y(), current.z(), GalaxiaBlocksEnum.GANTRY.get(), 0, 3);
                    tile = current.getTE(world);
                }
                var node = (TileEntityGantry) tile;
                if (previous != null) previous.connect(node);
                previous = node;
                if (current.equals(end)) break;
                if (current.x() != end.x()) current = new BlockPos(
                    current.x() + Integer.signum(end.x() - current.x()),
                    current.y(),
                    current.z());
                else if (current.y() != end.y()) current = new BlockPos(
                    current.x(),
                    current.y() + Integer.signum(end.y() - current.y()),
                    current.z());
                else current = new BlockPos(
                    current.x(),
                    current.y(),
                    current.z() + Integer.signum(end.z() - current.z()));
            }
        }
    }

    private void configure() {
        if (rocket.isRunning())
            throw new IllegalStateException("Finish the current trip before changing the lab preset");
        var home = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                silo.machineIdentity()
                    .id()));
        var destination = VehicleSchedule.Place.at(
            CelestialAssetStore.findEndpoint(
                pad.machineIdentity()
                    .id()));
        var actions = new ArrayList<VehicleSchedule.Action>();
        actions.add(new VehicleSchedule.Transfer(UUID.randomUUID(), iron, 64, VehicleSchedule.Direction.UNLOAD));
        actions.add(new VehicleSchedule.Repair(UUID.randomUUID(), REPAIR_THRESHOLD));
        var departure = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            home,
            List.of(VehicleSchedule.Refuel.full(UUID.randomUUID())));
        var arrival = new VehicleSchedule.Visit(UUID.randomUUID(), destination, actions);
        var returned = new VehicleSchedule.Visit(
            UUID.randomUUID(),
            home,
            List.of(
                new VehicleSchedule.Transfer(UUID.randomUUID(), iron, 64, VehicleSchedule.Direction.LOAD),
                VehicleSchedule.Refuel.full(UUID.randomUUID())));
        var schedule = new VehicleSchedule(List.of(departure, arrival, returned), VehicleSchedule.Mode.RUN_ONCE);
        double allowance = Math.floor(
            rocket.propulsion()
                .usableDeltaVMps());
        for (var leg : schedule.routes()
            .keySet())
            schedule = schedule.withRouteSettings(
                leg,
                new VehicleSchedule.RouteSettings(
                    leg.fromVisit()
                        .equals(arrival.id()) ? 0 : allowance,
                    RoutePriority.PRIORITIZE_DV));
        if (rocket.saveSchedule(rocket.teamId(), rocket.revision(), schedule) != Rocket.CommandResult.APPLIED)
            throw new IllegalStateException("Demo schedule rejected");
    }

    private static void sign(WorldServer world, int x, int y, int z, String... lines) {
        world.setBlock(x, y, z, Blocks.standing_sign, 8, 3);
        var sign = (TileEntitySign) world.getTileEntity(x, y, z);
        System.arraycopy(lines, 0, sign.signText, 0, Math.min(lines.length, 4));
        sign.markDirty();
        world.markBlockForUpdate(x, y, z);
    }

    private static void say(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText("[Rocket Lab] " + message));
    }

    private void help(EntityPlayerMP player) {
        say(player, "Ready: Silo (0,128,0), Landing Pad (32,128,0), three Assemblers behind the Pad.");
        say(player, "Use /rocketlab panel to view the real schedule, then Start. Or /rocketlab start.");
        say(
            player,
            "Default: Silo tank to full -> launch -> Pad unload/repair -> Gantry returns to SAME Silo -> tank to full.");
        say(player, "Cargo and fuel tank start at 40%, capsule and engine at 95%. Repair threshold is 75%.");
        say(player, "Healthy modules go straight to the Silo. Only the two worn modules visit Assemblers.");
        say(player, "The nearest Assembler has no power or materials. The other two are supplied and powered.");
        say(player, "While idle: /rocketlab service restores the repair preset. Start is always manual.");
        say(player, "/rocketlab status reports actual state. /rocketlab power toggles fixture energy supply.");
    }

    private final class Command extends CommandBase {

        @Override
        public String getCommandName() {
            return "rocketlab";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/rocketlab [panel|start|service|status|power]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            var player = getCommandSenderAsPlayer(sender);
            if (rocket == null) {
                say(player, "Setup has not completed.");
                return;
            }
            String action = args.length == 0 ? "help" : args[0];
            if (action.equals("start")) say(
                player,
                "Start: " + Galaxia.proxy.serverRuntime()
                    .startRocket(player, rocket.id(), rocket.revision()));
            else if (action.equals("service")) {
                configure();
                say(player, "Service preset saved. Refueling only at Silo, repair threshold 75%. Start when ready.");
            } else if (action.equals("panel")) {
                for (Object candidate : player.worldObj.loadedEntityList) {
                    if (candidate instanceof EntityRocket entity && entity.isCurrentProjection(rocket)) {
                        player.playerNetServerHandler
                            .setPlayerLocation(entity.posX - 3, entity.posY + 1, entity.posZ - 4, -35, 10);
                        GuiFactories.entity()
                            .open(player, entity);
                        return;
                    }
                }
                say(player, "The vehicle is in transit. Open the Star Map.");
            } else if (action.equals("power")) {
                powered = !powered;
                say(player, "Lab power=" + powered);
            } else if (action.equals("status")) say(
                player,
                "Vehicle=" + rocket.id()
                    + " status="
                    + rocket.status()
                    + " running="
                    + rocket.isRunning()
                    + " place="
                    + rocket.location()
                    + " cargo iron="
                    + rocket.cargoAmount(iron)
                    + " gold="
                    + rocket.cargoAmount(gold)
                    + " fuel="
                    + rocket.propellantAmounts());
            else help(player);
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class OpenWorld {

        private boolean opened;
        private int captureTicks;

        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (!opened && mc.currentScreen instanceof GuiMainMenu) {
                opened = true;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.renderDistanceChunks = 8;
                mc.launchIntegratedServer(
                    "RocketGameplayLab",
                    "Rocket Gameplay Lab",
                    new WorldSettings(20261018L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT)
                        .enableCommands());
            }
            if (ready && mc.theWorld != null && ++captureTicks == 60) {
                Display.setTitle("Galaxia: Ascent + Landing Test");
                ScreenShotHelper.saveScreenshot(
                    mc.mcDataDir,
                    "rocket-lab-ready.png",
                    mc.displayWidth,
                    mc.displayHeight,
                    mc.getFramebuffer());
                System.out.println(
                    "ROCKET_LAB_CLIENT_READY world=" + mc.theWorld.provider.dimensionId
                        + " guiScale="
                        + mc.gameSettings.guiScale
                        + " fov="
                        + mc.gameSettings.fovSetting
                        + " clouds="
                        + mc.gameSettings.clouds);
            }
        }
    }
}
