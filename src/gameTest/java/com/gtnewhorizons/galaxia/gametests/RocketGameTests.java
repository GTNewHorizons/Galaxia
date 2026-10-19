package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;
import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.requireScreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketDestination;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuels;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.SiloAutomation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryNetwork;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import baubles.api.BaublesApi;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class RocketGameTests {

    private RocketGameTests() {}

    /**
     * Product contract: modules are assembled from input buses, travel over the gantry, the rocket is fuelled and
     * loaded from hatches, flies to another silo and unloads its cargo there.
     */
    @GameTest(timeoutTicks = 6000)
    public static void rocketFliesCargoBetweenSilos(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(1200)
            .server("build two silos, an assembler and the gantry between them", () -> fixture.build(helper))
            .awaitServer("every multiblock formed", fixture::assertFormed)
            .server("the network indexes one assembler and one silo", fixture::assertNetwork)
            .server("queue a decoupler with its materials in the input bus", fixture::queueDecoupler)
            .awaitServer("the decoupler was assembled from the bus", fixture::assertDecouplerCrafted)
            .server("order the rocket design", fixture::orderRocket)
            .awaitServer("every module arrived over the gantry", fixture::assertRocketReady)
            .server("fill the fuel hatch and the input bus", () -> fixture.supply(32, 0))
            .awaitServer("tanks are full and the cargo is aboard", () -> fixture.assertLoaded(32))
            .server("look at the silo, gantry and assembler", () -> fixture.view(4, 9, -14, 0, 22))
            .awaitClient("camera in place", c -> fixture.assertCamera(4, 9, -14))
            .capture("silo-rocket-gantry")
            .server("open the silo screen", () -> fixture.openSilo(fixture.silo))
            .awaitClient("silo screen open", c -> requireScreen("galaxia:rocket_editor"))
            .capture("silo-editor-ready")
            .click(control("silo.toolbar.cargo"))
            .awaitClient("cargo panel open", c -> {
                if (GuiTestSupport.findNamedWidget("silo.cargo.launch") == null) {
                    throw new AssertionError("Cargo panel is not open");
                }
            })
            .awaitClient("hidden cargo slots are switched off in the container", c -> {
                long enabled = Minecraft.getMinecraft().thePlayer.openContainer.inventorySlots.stream()
                    .filter(slot -> ((Slot) slot).func_111238_b())
                    .count();
                // One 27 slot hold plus the 36 player slots
                if (enabled != 27 + 36) throw new AssertionError("Container has " + enabled + " enabled slots");
            })
            .capture("silo-cargo-panel")
            .click(control("silo.cargo.launch"))
            .awaitServer("the rocket left", fixture::assertLaunched)
            .escape()
            .awaitServer("the rocket landed in the second silo", fixture::assertLanded)
            .awaitServer("the cargo went into the output bus", () -> fixture.assertUnloaded(32))
            .succeed();
    }

    /**
     * Product contract: automation builds the inserted schematic, loads only requested items, launches and stops
     * once the request is delivered.
     */
    @GameTest(timeoutTicks = 6000)
    public static void automationDeliversRequestsAndStops(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(1500)
            .server("build two silos, an assembler and the gantry between them", () -> fixture.build(helper))
            .awaitServer("every multiblock formed", fixture::assertFormed)
            .server("insert a schematic, request cobblestone and supply the silo", () -> {
                fixture.stockModules();
                fixture.configureAutomation(16);
                fixture.supply(40, 8);
            })
            .awaitClient("the silo reached the client", c -> fixture.assertClientHas(0, 0, 0))
            .server("open the automation panel", () -> fixture.openSilo(fixture.silo))
            .awaitClient("silo screen open", c -> requireScreen("galaxia:rocket_editor"))
            .click(control("silo.toolbar.automation"))
            .awaitClient("automation panel open", c -> {
                if (GuiTestSupport.findNamedWidget("silo.automation.enabled") == null) {
                    throw new AssertionError("Automation panel is not open");
                }
            })
            .capture("silo-automation-panel")
            .escape()
            .awaitServer("the request was delivered and automation stopped", fixture::assertAutomationComplete)
            .server("only the requested items flew", fixture::assertOnlyRequestedFlew)
            .succeed();
    }

    /** Product contract: wrenching turns the silo, sneaking on its front mirrors it, and it still forms that way. */
    @GameTest(timeoutTicks = 1200)
    public static void wrenchTurnsAndMirrorsSilo(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("place a lone silo controller and wrench it", () -> {
                fixture.prepare(helper);
                fixture.place(0, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
                TileEntitySilo silo = fixture.tile(0, 0, 0);
                silo.setFacing(ForgeDirection.NORTH);
                if (!silo.onWrench(fixture.player, ForgeDirection.EAST) || silo.getExtendedFacing()
                    .getDirection() != ForgeDirection.EAST) {
                    throw new AssertionError("Wrenching a side must turn the front towards it");
                }
                if (silo.onWrench(fixture.player, ForgeDirection.UP)) {
                    throw new AssertionError("A silo must not face up");
                }
                fixture.player.setSneaking(true);
                boolean mirrored = silo.onWrench(fixture.player, ForgeDirection.EAST);
                fixture.player.setSneaking(false);
                if (!mirrored || silo.getExtendedFacing()
                    .getFlip() != Flip.HORIZONTAL) {
                    throw new AssertionError("Sneak-wrenching the front must mirror the silo");
                }
                silo.construct(null, false);
                silo.markStructureDirty();
            })
            .awaitServer("the mirrored, turned silo forms", () -> {
                if (!fixture.<TileEntitySilo>tile(0, 0, 0)
                    .isStructureValid()) throw new AssertionError("Rotated silo did not form");
            })
            .succeed();
    }

    /** Product contract: right clicking a tank frame offers every core it can hold and places the picked one. */
    @GameTest(timeoutTicks = 1200)
    public static void tankCorePickerSelectsFuel(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("place a silo", () -> {
                fixture.prepare(helper);
                fixture.place(0, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
                fixture.silo = fixture.tile(0, 0, 0);
                fixture.silo.construct(null, false);
                fixture.view(0, 2, -6, 0, 10);
            })
            .awaitClient("the silo reached the client", c -> fixture.assertClientHas(0, 0, 0))
            .server("open the silo", () -> fixture.openSilo(fixture.silo))
            .awaitClient("silo screen open", c -> requireScreen("galaxia:rocket_editor"))
            .capture("silo-editor-open")
            .click(control("silo.category.fuel_tanks"))
            .rightClick(control("silo.frame." + FuelTankFrame.WIDE_3.label))
            .awaitClient("core picker open", c -> {
                if (GuiTestSupport.findNamedWidget("silo.fuel_picker." + RocketFuels.ANTIMATTER.key()) == null) {
                    throw new AssertionError("Wide frames must offer antimatter cores");
                }
            })
            .capture("silo-core-picker")
            .click(control("silo.fuel_picker." + RocketFuels.RP1.key()))
            .rightClick(control("silo.frame." + FuelTankFrame.SLIM_3.label))
            .awaitClient("slim picker open", c -> {
                if (GuiTestSupport.findNamedWidget("silo.fuel_picker." + RocketFuels.NITROFUEL.key()) == null) {
                    throw new AssertionError("Slim picker did not open");
                }
                if (GuiTestSupport.findNamedWidget("silo.fuel_picker." + RocketFuels.ANTIMATTER.key()) != null) {
                    throw new AssertionError("Slim frames must not offer antimatter cores");
                }
            })
            .capture("silo-slim-core-picker")
            .click(control("silo.fuel_picker." + RocketFuels.NITROFUEL.key()))
            .capture("silo-editor-tanks")
            .click(control("silo.toolbar.cargo"))
            .awaitClient("cargo panel open", c -> {
                if (GuiTestSupport.findNamedWidget("silo.cargo.launch") == null) {
                    throw new AssertionError("Cargo panel is not open");
                }
            })
            .awaitClient("no tank bars without a rocket", c -> {
                for (var panel : com.cleanroommc.modularui.screen.ModularScreen.getCurrent()
                    .getPanelManager()
                    .getOpenPanels()) {
                    for (var widget : com.cleanroommc.modularui.utils.TreeUtil.flatListBFS(
                        panel,
                        w -> w.getName() != null && w.getName()
                            .startsWith("silo.cargo.tank."))) {
                        if (widget.isEnabled()) throw new AssertionError(widget.getName() + " is shown");
                    }
                }
            })
            .capture("silo-cargo-empty")
            .succeed();
    }

    /**
     * Product contract: rails connect on all six faces like pipes, and a module rides two hooks up a shaft, round flat
     * corners and down again into the assembler.
     */
    @GameTest(timeoutTicks = 2400)
    public static void moduleRidesHooksOverShaftsAndCorners(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(600)
            .server("build the machines with a detour over shafts and corners", () -> {
                fixture.build(helper);
                fixture.buildDetour();
                fixture.buildJunctions();
            })
            .awaitServer("every multiblock formed", fixture::assertFormed)
            .server("the detour is the only route", fixture::assertDetourNetwork)
            .server("look at the detour", () -> fixture.view(14, 7, 13, 145, 12))
            .awaitClient("camera at the detour", c -> fixture.assertCamera(14, 7, 13))
            .server("send a long tank from the silo to the assembler", fixture::sendTank)
            .awaitServer("the tank climbs the shaft", () -> fixture.assertModuleAt(5, 4, 3))
            .capture("gantry-hooks-shaft")
            .awaitServer("the tank rounds the first corner", () -> fixture.assertModuleAt(5, 7, 2))
            .capture("gantry-hooks-corner")
            .awaitServer("the tank runs along the top", () -> fixture.assertModuleAt(7, 7, 2))
            .capture("gantry-hooks-top")
            .awaitServer("the tank goes down the second shaft", () -> fixture.assertModuleAt(8, 5, 3))
            .capture("gantry-hooks-descent")
            .awaitServer("the tank reached the assembler", fixture::assertTankDelivered)
            .awaitClient("the tank left the rails on the client too", c -> {
                if (Minecraft.getMinecraft().theWorld
                    .getTileEntity(fixture.x + 9, fixture.y + 1, fixture.z + 3) instanceof TileEntityGantry terminal
                    && terminal.clientModuleId != -1) {
                    throw new AssertionError("Tank still drawn at the terminal");
                }
            })
            .server("look at the junctions", () -> fixture.view(2, 6, -12, 0, 25))
            .awaitClient("camera at the junctions", c -> fixture.assertCamera(2, 6, -12))
            .capture("gantry-junctions")
            // The shaft by the silo faces its bottom bend, the top bend turns north and twists to meet it
            .server("look at the twisting bend", () -> fixture.view(3, 8, 6, 214, 35))
            .awaitClient("camera at the twisting bend", c -> fixture.assertCamera(3, 8, 6))
            .capture("gantry-twist")
            .succeed();
    }

    /** Visual check: the assembler screen and the space gear drawn on the player. */
    @GameTest(timeoutTicks = 1600)
    public static void assemblerScreenAndWornGear(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build the assembler with a stocked bus", () -> {
                fixture.build(helper);
                fixture.stockModules();
                IGregTechTileEntity bus = fixture.machine(11, 2, 6);
                bus.setInventorySlotContents(0, GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 5));
            })
            .awaitServer("every multiblock formed", fixture::assertFormed)
            .awaitClient("the assembler reached the client", c -> {
                if (!(Minecraft.getMinecraft().theWorld.getTileEntity(
                    fixture.x + 10,
                    fixture.y + 1,
                    fixture.z + 6) instanceof TileEntityModuleAssembler)) {
                    throw new AssertionError("Assembler is not on the client yet");
                }
            })
            .server("stand next to the assembler", () -> fixture.view(10, 2, 1, 0, 20))
            .awaitClient("camera next to the assembler", c -> fixture.assertCamera(10, 2, 1))
            .server(
                "open the assembler",
                () -> GuiFactories.tileEntity()
                    .open(fixture.player, fixture.x + 10, fixture.y + 1, fixture.z + 6))
            .awaitClient("assembler screen open", c -> requireScreen("galaxia:module_assembler"))
            .capture("assembler-screen")
            // The screen only takes input once the server side of the GUI is open as well
            .awaitServer("assembler container open", () -> {
                if (fixture.player.openContainer == fixture.player.inventoryContainer) {
                    throw new AssertionError("Assembler container is not open on the server");
                }
            })
            .awaitClient("assembler screen still open", c -> requireScreen("galaxia:module_assembler"))
            // Pressed directly: injected clicks right after opening are unreliable under lwjgl3ify
            .awaitClient("open the fuel tank category", c -> press("assembler.category.fuel_tanks"))
            .awaitClient("tank groups are folded", c -> {
                if (GuiTestSupport.findNamedWidget("assembler.group.5x5") == null) {
                    throw new AssertionError("Folded 5x5 group missing");
                }
            })
            .capture("assembler-tank-groups")
            .awaitClient("unfold the 5x5 group", c -> press("assembler.group.5x5"))
            .awaitClient("the 5x5 group unfolded", c -> {
                if (GuiTestSupport.findNamedWidget("assembler.group.5x5.fold") == null) {
                    throw new AssertionError("5x5 group did not unfold");
                }
            })
            .capture("assembler-tank-group-open")
            .server("debug buttons are for creative players", fixture::creative)
            .awaitClient("switch on infinite stock", c -> press("assembler.infinite_stock"))
            .awaitServer("every module is in stock", () -> {
                if (!fixture.assembler.isInfiniteStock()) {
                    throw new AssertionError("Infinite stock is off");
                }
            })
            .awaitClient("the screen shows infinite stock", c -> {
                var stock = (com.cleanroommc.modularui.widgets.TextWidget<?>) GuiTestSupport
                    .findNamedWidget("assembler.stock");
                if (stock == null || !stock.getKey()
                    .get()
                    .contains("∞")) throw new AssertionError("Stock does not read infinite yet");
            })
            .capture("assembler-infinite-stock")
            .server("close the screen and put on the space gear", () -> {
                fixture.player.closeScreen();
                fixture.equipGear();
                fixture.view(-12, 3, -6, 0, 10);
            })
            .client("look at the player from the front", c -> {
                var settings = Minecraft.getMinecraft().gameSettings;
                int oldView = settings.thirdPersonView;
                c.afterTest(() -> settings.thirdPersonView = oldView);
                settings.thirdPersonView = 2;
            })
            .awaitClient("camera in place", c -> fixture.assertCamera(-12, 3, -6))
            .capture("gear-front")
            .server("turn around", () -> fixture.view(-12, 3, -6, 180, 10))
            .awaitClient("turned", c -> {
                if (Math.abs(MathHelper.wrapAngleTo180_float(Minecraft.getMinecraft().thePlayer.rotationYaw - 180))
                    > 5) {
                    throw new AssertionError("Player has not turned yet");
                }
            })
            .capture("gear-back")
            // Hatches take the texture of the casing they replace
            .client("first person again", c -> Minecraft.getMinecraft().gameSettings.thirdPersonView = 0)
            .server("look at the assembler bus", () -> fixture.view(11, 2, 9, 180, 15))
            .awaitClient("camera at the assembler bus", c -> fixture.assertCamera(11, 2, 9))
            .capture("assembler-hatch")
            .server("look at the silo hatches", () -> fixture.view(0, 0, -4, 0, 30))
            .awaitClient("camera at the silo hatches", c -> fixture.assertCamera(0, 0, -4))
            .capture("silo-hatches")
            .server("hold a gantry shaft and drop another one", fixture::showGantryItems)
            .awaitClient("the shaft is in the player's hand", c -> {
                ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
                if (held == null || Block.getBlockFromItem(held.getItem()) != GalaxiaBlocksEnum.GANTRY.get()) {
                    throw new AssertionError("Hand holds " + held);
                }
                // The new item is raised into view over a few ticks
                long now = Minecraft.getMinecraft().theWorld.getTotalWorldTime();
                if (fixture.heldSince < 0) fixture.heldSince = now;
                if (now - fixture.heldSince < 10) throw new AssertionError("Item is still being raised");
            })
            .awaitClient("the dropped shaft reached the client", c -> {
                if (Minecraft.getMinecraft().theWorld.loadedEntityList.stream()
                    .noneMatch(e -> e instanceof EntityItem)) throw new AssertionError("No dropped item yet");
            })
            .capture("gantry-item")
            .succeed();
    }

    /** Product contract: a rocket blueprint is held in both hands like a map, an empty one as a blank sheet */
    @GameTest(timeoutTicks = 800)
    public static void blueprintIsHeldLikeAMap(GameTestHelper helper) {
        EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
        ItemStack drawn = new ItemStack(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem());
        ItemRocketSchematic.setBlueprint(drawn, new Fixture().design());
        ItemStack blank = new ItemStack(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem());
        long[] heldSince = { -1 };
        ClientTest.scenario(helper)
            .client("first person", c -> {
                var settings = Minecraft.getMinecraft().gameSettings;
                int oldView = settings.thirdPersonView;
                c.afterTest(() -> settings.thirdPersonView = oldView);
                settings.thirdPersonView = 0;
            })
            .server("hold a blueprint, looking down a little", () -> {
                int slot = player.inventory.currentItem;
                ItemStack old = player.inventory.getStackInSlot(slot);
                helper.afterTest(() -> player.inventory.setInventorySlotContents(slot, old));
                player.inventory.setInventorySlotContents(slot, drawn);
                player.playerNetServerHandler
                    .setPlayerLocation(player.posX, player.posY, player.posZ, player.rotationYaw, 30);
            })
            .awaitClient("the blueprint is raised", c -> awaitHeld(heldSince, true))
            .capture("blueprint-held")
            .server("hold an empty blueprint", () -> {
                heldSince[0] = -1;
                player.inventory.setInventorySlotContents(player.inventory.currentItem, blank);
            })
            .awaitClient("the empty blueprint is raised", c -> awaitHeld(heldSince, false))
            .capture("blueprint-empty")
            .succeed();
    }

    /** Waits until the hand holds a blueprint, with or without a design, and it has been raised into view */
    private static void awaitHeld(long[] heldSince, boolean withDesign) {
        ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemRocketSchematic)
            || ItemRocketSchematic.hasBlueprint(held) != withDesign) throw new AssertionError("Hand holds " + held);
        long now = Minecraft.getMinecraft().theWorld.getTotalWorldTime();
        if (heldSince[0] < 0) heldSince[0] = now;
        if (now - heldSince[0] < 10) throw new AssertionError("Blueprint is still being raised");
    }

    /**
     * Presses a named button through its own input handler
     *
     * @param name Widget name
     */
    private static void press(String name) {
        var screen = com.cleanroommc.modularui.screen.ModularScreen.getCurrent();
        if (screen == null) throw new AssertionError("No screen open");
        for (var panel : screen.getPanelManager()
            .getOpenPanels()) {
            for (var widget : com.cleanroommc.modularui.utils.TreeUtil
                .flatListBFS(panel, w -> w != panel && name.equals(w.getName()))) {
                if (widget instanceof com.cleanroommc.modularui.api.widget.Interactable button && widget.isEnabled()) {
                    button.onMousePressed(0);
                    return;
                }
            }
        }
        throw new AssertionError(name + " is not available yet");
    }

    private static final class Fixture {

        private final List<BlockPos> placed = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private int x;
        private int y;
        private int z;
        private TileEntitySilo silo;
        private TileEntitySilo target;
        private TileEntityModuleAssembler assembler;
        private int stockBefore;
        private long heldSince = -1;
        private RocketBlueprint design;
        private final List<Runnable> helperCleanup = new ArrayList<>();

        void prepare(GameTestHelper helper) {
            world = helper.getWorld();
            player = (EntityPlayerMP) world.playerEntities.get(0);
            x = (int) player.posX + 24;
            y = 140;
            z = (int) player.posZ;
            double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
            boolean oldAllowFlying = player.capabilities.allowFlying;
            boolean oldFlying = player.capabilities.isFlying;
            // The viewpoints hang in the air, falling would take the player out of GUI range
            player.capabilities.allowFlying = true;
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            helper.afterTest(() -> {
                helperCleanup.forEach(Runnable::run);
                player.closeScreen();
                for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z());
                player.capabilities.allowFlying = oldAllowFlying;
                player.capabilities.isFlying = oldFlying;
                player.sendPlayerAbilities();
                player.setPositionAndUpdate(oldX, oldY, oldZ);
            });
        }

        /**
         * Silo facing north at the origin, an assembler east of it with its terminal level with the silo's east
         * terminal, four rails in between, and a second silo further east as the destination
         */
        void build(GameTestHelper helper) {
            prepare(helper);
            place(0, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
            silo = tile(0, 0, 0);
            silo.setFacing(ForgeDirection.NORTH);
            constructAndRecord(silo);

            place(10, 1, 6, GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get(), 0);
            assembler = tile(10, 1, 6);
            assembler.setFacing(ForgeDirection.NORTH);
            constructAndRecord(assembler);

            // Construct fills terminal spots with casing, players place the terminals themselves
            place(4, 1, 3, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0);
            place(9, 1, 3, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0);
            for (int dx = 5; dx <= 8; dx++) place(dx, 1, 3, GalaxiaBlocksEnum.GANTRY.get(), 0);

            place(40, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
            target = tile(40, 0, 0);
            target.setFacing(ForgeDirection.NORTH);
            constructAndRecord(target);

            // Hatches replace sheeting on the bottom row in front of the controllers
            placeMachine(2, -1, 0, ItemList.Hatch_Input_IV.get(1));
            placeMachine(-2, -1, 0, ItemList.Hatch_Input_Bus_LV.get(1));
            placeMachine(41, -1, 0, ItemList.Hatch_Output_Bus_LV.get(1));
            placeMachine(11, 2, 6, ItemList.Hatch_Input_Bus_LV.get(1));

            silo.setDestination(RocketDestination.ofSilo(world.provider.dimensionId, target.getPos()));
            silo.markStructureDirty();
            target.markStructureDirty();
            assembler.markStructureDirty();
            fly();
            player.setPositionAndUpdate(x - 6.5, y + 2, z - 8.5);
        }

        /** Switches to creative for the rest of the test */
        void creative() {
            WorldSettings.GameType old = player.theItemInWorldManager.getGameType();
            player.setGameType(WorldSettings.GameType.CREATIVE);
            helperCleanup.add(() -> player.setGameType(old));
        }

        /**
         * Puts a Gantry Shaft in the player's hand and one on a block in front of the silo hatches, so the item renders
         * in the hotbar, in first person and on the ground
         */
        void showGantryItems() {
            int slot = player.inventory.currentItem;
            ItemStack old = player.inventory.getStackInSlot(slot);
            player.inventory.setInventorySlotContents(slot, new ItemStack(GalaxiaBlocksEnum.GANTRY.get(), 3));
            helperCleanup.add(() -> player.inventory.setInventorySlotContents(slot, old));

            place(1, -1, -2, Blocks.stone, 0);
            EntityItem dropped = new EntityItem(
                world,
                x + 1.5,
                y + 0.1,
                z - 1.5,
                new ItemStack(GalaxiaBlocksEnum.GANTRY.get()));
            dropped.motionX = dropped.motionY = dropped.motionZ = 0;
            dropped.delayBeforeCanPickup = Short.MAX_VALUE;
            world.spawnEntityInWorld(dropped);
            helperCleanup.add(dropped::setDead);
        }

        /** Landing on a structure ends flight, so every viewpoint switches it back on before moving there */
        void fly() {
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
        }

        void constructAndRecord(GalaxiaMultiblockBase<?> multiblock) {
            // Record everything the construct places so the test can clean up after itself
            List<BlockPos> before = new ArrayList<>();
            for (int dx = -6; dx <= 50; dx++) for (int dy = -3; dy <= 13; dy++) for (int dz = -6; dz <= 12; dz++) {
                if (!world.isAirBlock(x + dx, y + dy, z + dz)) before.add(new BlockPos(x + dx, y + dy, z + dz));
            }
            multiblock.construct(null, false);
            for (int dx = -6; dx <= 50; dx++) for (int dy = -3; dy <= 13; dy++) for (int dz = -6; dz <= 12; dz++) {
                BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                if (!world.isAirBlock(pos.x(), pos.y(), pos.z()) && !before.contains(pos) && !placed.contains(pos)) {
                    placed.add(pos);
                }
            }
        }

        void place(int dx, int dy, int dz, Block block, int meta) {
            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlock(pos.x(), pos.y(), pos.z(), block, meta, 3);
        }

        void placeMachine(int dx, int dy, int dz, ItemStack machine) {
            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlockToAir(pos.x(), pos.y(), pos.z());
            ((ItemMachines) machine.getItem()).placeBlockAt(
                machine,
                player,
                world,
                pos.x(),
                pos.y(),
                pos.z(),
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                machine.getItemDamage());
        }

        @SuppressWarnings("unchecked")
        <T> T tile(int dx, int dy, int dz) {
            return (T) world.getTileEntity(x + dx, y + dy, z + dz);
        }

        IGregTechTileEntity machine(int dx, int dy, int dz) {
            return tile(dx, dy, dz);
        }

        void assertFormed() {
            if (!silo.isStructureValid()) throw new AssertionError("Launch silo did not form");
            if (!target.isStructureValid()) throw new AssertionError("Destination silo did not form");
            if (!assembler.isStructureValid()) throw new AssertionError("Assembler did not form");
            if (silo.getHatches()
                .inputHatchCount() != 1
                || silo.getHatches()
                    .inputBusCount() != 1) {
                throw new AssertionError("Silo hatches were not picked up by the structure");
            }
        }

        void assertNetwork() {
            if (silo.getGantryTerminal() == null) throw new AssertionError("Silo has no connected terminal");
            GantryNetwork network = silo.getGantryTerminal()
                .getNetwork();
            if (network.size() != 6) throw new AssertionError("Expected 6 gantries, found " + network.size());
            if (network.getProviders()
                .size() != 1
                || network.getReceivers()
                    .size() != 1) {
                throw new AssertionError("Network must index one assembler and one silo");
            }
        }

        void queueDecoupler() {
            IGregTechTileEntity bus = machine(11, 2, 6);
            bus.setInventorySlotContents(0, GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 8));
            bus.setInventorySlotContents(1, GTOreDictUnificator.get(OrePrefixes.screw, Materials.Steel, 8));
            assembler.queueCraft(RocketPartRegistry.DECOUPLER, 1);
        }

        void assertDecouplerCrafted() {
            if (assembler.getStockCount(RocketPartRegistry.DECOUPLER) != 1) {
                throw new AssertionError("Decoupler not assembled yet, status " + assembler.getStatus());
            }
            if (machine(11, 2, 6).getStackInSlot(0) != null) {
                throw new AssertionError("Assembling must consume the steel plates");
            }
        }

        RocketBlueprint design() {
            if (design != null) return design;
            RocketPartRegistry registry = RocketPartRegistry.instance();
            design = new RocketBlueprint();
            design.setName("Test Hauler");
            // The engine comes second although it only touches the tank: modules are requested in this order and the
            // silo has to take the engine before anything it is attached to has arrived
            design.addPart(new RocketPartInstance(RocketPartRegistry.CAPSULE, 1, 0, 0, false));
            design.addPartUnchecked(new RocketPartInstance(registry.getByKey("engine_nitrofuel"), 1, 10, 0, false));
            design.addPart(new RocketPartInstance(RocketPartRegistry.STORAGE, 1, 3, 0, false));
            design.addPart(
                new RocketPartInstance(
                    registry.getFuelTank(FuelTankFrame.WIDE_3, RocketFuels.NITROFUEL),
                    0,
                    7,
                    0,
                    false));
            if (design.getParts()
                .size() != 4
                || !design.analyze()
                    .viable())
                throw new AssertionError("Test design is not connected");
            return design;
        }

        /**
         * Replaces the straight rails between the silo and the assembler with a detour: up a shaft by the silo, north,
         * east along the top, south and down a shaft by the assembler
         */
        void buildDetour() {
            world.setBlockToAir(x + 6, y + 1, z + 3);
            world.setBlockToAir(x + 7, y + 1, z + 3);
            for (int dy = 2; dy <= 7; dy++) {
                place(8, dy, 3, GalaxiaBlocksEnum.GANTRY.get(), 0);
                place(5, dy, 3, GalaxiaBlocksEnum.GANTRY.get(), 0);
            }
            for (int dx = 5; dx <= 8; dx++) place(dx, 7, 2, GalaxiaBlocksEnum.GANTRY.get(), 0);
        }

        /** Every way rails can meet, away from the machines: a cross, a T, a junction with a shaft and a lone rail */
        void buildJunctions() {
            int dz = -6;
            for (int dx = 0; dx <= 4; dx++) place(dx, 3, dz, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(2, 3, dz - 1, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(2, 3, dz + 1, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(4, 3, dz + 1, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(0, 4, dz, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(0, 5, dz, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(6, 3, dz, GalaxiaBlocksEnum.GANTRY.get(), 0);
        }

        void assertDetourNetwork() {
            TileEntityGantryTerminal from = tile(4, 1, 3);
            GantryNetwork network = from.getNetwork();
            // Two terminals, 6 + 6 shaft rails, 4 along the top and the two rails next to the terminals
            if (network.size() != 20) throw new AssertionError("Network has " + network.size() + " members");
            TileEntityGantryTerminal to = tile(9, 1, 3);
            if (!network.canReach(from, to)) throw new AssertionError("No route over the detour");
        }

        void sendTank() {
            IRocketPartDef tank = RocketPartRegistry.instance()
                .getFuelTank(FuelTankFrame.MEDIUM_5, RocketFuels.NITROFUEL);
            stockBefore = assembler.getStockCount(tank);
            if (!GantryAPI.send(new RocketPartInstance(tank, 0, 0, 0, false), tile(4, 1, 3), tile(9, 1, 3))) {
                throw new AssertionError("Terminal refused the tank");
            }
        }

        void assertModuleAt(int dx, int dy, int dz) {
            TileEntityGantry gantry = tile(dx, dy, dz);
            if (gantry.getModule() == null) throw new AssertionError("Tank is not at " + dx + ", " + dy + ", " + dz);
        }

        void assertTankDelivered() {
            IRocketPartDef tank = RocketPartRegistry.instance()
                .getFuelTank(FuelTankFrame.MEDIUM_5, RocketFuels.NITROFUEL);
            if (assembler.getStockCount(tank) != stockBefore + 1) throw new AssertionError("Tank not delivered yet");
        }

        void stockModules() {
            for (RocketPartInstance part : design().getParts()) assembler.debugAdjustStock(part.def(), 1);
        }

        void orderRocket() {
            stockModules();
            silo.setDesignBlueprint(design());
            if (!silo.orderModules()) throw new AssertionError("Order refused: " + silo.getLastProblem());
        }

        void assertRocketReady() {
            if (silo.getBuildStatus() != RocketBuildStatus.READY) {
                throw new AssertionError("Rocket not assembled yet: " + silo.getBuildStatus());
            }
            int built = silo.getBuiltBlueprint()
                .getParts()
                .size();
            if (built != design().getParts()
                .size()) throw new AssertionError("Rocket was built with " + built + " of the ordered modules");
        }

        /**
         * Fills the fuel hatch and puts items into the silo's input bus
         *
         * @param cobblestone Cobblestone to put in
         * @param dirt        Dirt to put in, never requested
         */
        void supply(int cobblestone, int dirt) {
            MTEHatchInput hatch = (MTEHatchInput) machine(2, -1, 0).getMetaTileEntity();
            hatch.fill(new FluidStack(RocketFuels.NITROFUEL.fluid(), 32_000), true);
            IGregTechTileEntity bus = machine(-2, -1, 0);
            bus.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, cobblestone));
            if (dirt > 0) bus.setInventorySlotContents(1, new ItemStack(Blocks.dirt, dirt));
        }

        void assertLoaded(int cobblestone) {
            long fuel = silo.getLoadout()
                .getTotalFuel(silo.getBuiltBlueprint());
            if (fuel < FuelTankFrame.WIDE_3.capacity()) throw new AssertionError("Tank only holds " + fuel + " mB");
            if (countCargo(silo, Blocks.cobblestone) != cobblestone) {
                throw new AssertionError("Cargo hold has " + countCargo(silo, Blocks.cobblestone) + " cobblestone");
            }
        }

        void view(int dx, int dy, int dz, float yaw, float pitch) {
            fly();
            player.playerNetServerHandler.setPlayerLocation(x + dx + 0.5, y + dy, z + dz + 0.5, yaw, pitch);
        }

        void assertCamera(int dx, int dy, int dz) {
            var camera = Minecraft.getMinecraft().thePlayer;
            if (Math.abs(camera.posX - (x + dx + 0.5)) > 0.5 || Math.abs(camera.posZ - (z + dz + 0.5)) > 0.5) {
                throw new AssertionError("Camera has not moved yet");
            }
        }

        void assertClientHas(int dx, int dy, int dz) {
            if (!(Minecraft.getMinecraft().theWorld.getTileEntity(x + dx, y + dy, z + dz) instanceof TileEntitySilo)) {
                throw new AssertionError("Silo is not on the client yet");
            }
        }

        void equipGear() {
            IInventory baubles = BaublesApi.getBaubles(player);
            List<Integer> touched = new ArrayList<>();
            ItemStack[] tanks = { new ItemStack(GalaxiaItemList.OXYGEN_TANK_T2.getItem()),
                new ItemStack(GalaxiaItemList.OXYGEN_TANK_T3.getItem()) };
            for (int i = 0; i < Math.min(tanks.length, Galaxia.oxygenSlots.length); i++) {
                baubles.setInventorySlotContents(Galaxia.oxygenSlots[i], tanks[i]);
                touched.add(Galaxia.oxygenSlots[i]);
            }
            baubles.setInventorySlotContents(
                Galaxia.oxygenMaskSlots[0],
                new ItemStack(GalaxiaItemList.OXYGEN_MASK.getItem()));
            baubles.setInventorySlotContents(
                Galaxia.thermalSlot[0],
                new ItemStack(GalaxiaItemList.THERMAL_PROTECTION_COLD.getItem()));
            baubles.setInventorySlotContents(
                Galaxia.rcsSlot[0],
                new ItemStack(GalaxiaItemList.REACTION_CONTROL_SYSTEM_T1.getItem()));
            touched.add(Galaxia.oxygenMaskSlots[0]);
            touched.add(Galaxia.thermalSlot[0]);
            touched.add(Galaxia.rcsSlot[0]);
            helperCleanup.add(() -> { for (int slot : touched) baubles.setInventorySlotContents(slot, null); });
        }

        void openSilo(TileEntitySilo tile) {
            player.closeScreen();
            fly();
            // GUIs close as soon as the player is more than 8 blocks away
            player.playerNetServerHandler
                .setPlayerLocation(tile.xCoord + 0.5, tile.yCoord + 1, tile.zCoord - 2.5, 0, 20);
            GuiFactories.tileEntity()
                .open(player, tile.xCoord, tile.yCoord, tile.zCoord);
        }

        void assertLaunched() {
            if (silo.getBuildStatus() == RocketBuildStatus.READY) {
                throw new AssertionError("Rocket still on the pad: " + silo.getLastProblem());
            }
        }

        void assertLanded() {
            if (target.getBuildStatus() != RocketBuildStatus.READY) {
                throw new AssertionError("Nothing landed yet");
            }
        }

        void assertUnloaded(int cobblestone) {
            if (target.isUnloading()) throw new AssertionError("Still unloading");
            IGregTechTileEntity bus = machine(41, -1, 0);
            int found = 0;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null && stack.getItem() == new ItemStack(Blocks.cobblestone).getItem()) {
                    found += stack.stackSize;
                }
            }
            if (found != cobblestone) throw new AssertionError("Output bus holds " + found + " cobblestone");
        }

        void configureAutomation(int cobblestone) {
            ItemStack schematic = new ItemStack(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem());
            ItemRocketSchematic.setBlueprint(schematic, design());
            SiloAutomation automation = silo.getAutomation();
            automation.getSchematicSlot()
                .setStackInSlot(0, schematic);
            SiloAutomation.Request request = automation.getRequests()
                .get(0);
            request.setTemplate(new ItemStack(Blocks.cobblestone));
            request.setAmount(cobblestone);
            automation.setEnabled(true);
        }

        void assertAutomationComplete() {
            SiloAutomation automation = silo.getAutomation();
            if (automation.getStatus() != SiloAutomation.Status.COMPLETE) {
                throw new AssertionError("Automation status " + automation.getStatus() + " " + silo.getLastProblem());
            }
        }

        void assertOnlyRequestedFlew() {
            if (countCargo(target, Blocks.cobblestone) + countOutput(Blocks.cobblestone) != 16) {
                throw new AssertionError("Exactly the requested cobblestone must arrive");
            }
            if (countCargo(target, Blocks.dirt) + countOutput(Blocks.dirt) != 0) {
                throw new AssertionError("Unrequested dirt must stay behind");
            }
        }

        int countOutput(Block block) {
            IGregTechTileEntity bus = machine(41, -1, 0);
            int found = 0;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null && stack.getItem() == new ItemStack(block).getItem()) found += stack.stackSize;
            }
            return found;
        }

        static int countCargo(TileEntitySilo tile, Block block) {
            int found = 0;
            for (RocketPartInstance part : tile.getBuiltBlueprint()
                .getParts()) {
                if (!(part.def() instanceof StoragePartDef)) continue;
                for (ItemStack stack : tile.getLoadout()
                    .getCargo(part)) {
                    if (stack != null && stack.getItem() == new ItemStack(block).getItem()) found += stack.stackSize;
                }
            }
            return found;
        }
    }
}
