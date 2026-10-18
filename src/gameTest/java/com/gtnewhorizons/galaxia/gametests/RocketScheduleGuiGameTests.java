package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.control;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.TreeUtil;
import com.cleanroommc.modularui.widget.AbstractScrollWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.RocketTransitPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
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
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClickTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTarget;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class RocketScheduleGuiGameTests {

    private RocketScheduleGuiGameTests() {}

    /** Integration contract: normal GUI draft editing, native server save, cancel and reopening preserve identities. */
    @GameTest(timeoutTicks = 2400)
    public static void createEditReorderSaveCancelAndReopenSchedule(GameTestHelper helper) {
        var fixture = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .client("shared QA display profile is applied", ignored -> {
                var settings = Minecraft.getMinecraft().gameSettings;
                if (settings.guiScale != 2 || settings.fovSetting != 110f || settings.clouds)
                    throw new AssertionError("Expected GUI Scale 2, Quake Pro FOV and clouds off");
            })
            .server("prepare one owned vehicle", () -> fixture.prepare(helper))
            .client(
                "register fixture mirror cleanup",
                client -> client
                    .afterTest(() -> CelestialAssetStore.CLIENT.destroyAssetInternal(fixture.station.assetId)));
        GuiTestSupport.openMap(scenario, helper)
            .click(control("starmap.toolbar.vehicles"))
            .awaitClient("vehicle schedule synchronized", ignored -> fixture.assertClientRevision())
            .click(control("rocket.schedule.add_visit"))
            .awaitClient("first draft visit visible", ignored -> assertVisitCount(1))
            .click(control("rocket.schedule.add_visit"))
            .awaitClient("second draft visit visible", ignored -> assertVisitCount(2))
            .click(control("rocket.schedule.mode"))
            .click(control("rocket.schedule.target"))
            .click(control("rocket.schedule.group_kind"))
            .click(control("rocket.schedule.group_kind"))
            .click(control("rocket.schedule.group_scope"))
            .click(control("rocket.schedule.group_name"))
            .key(Keyboard.KEY_G, 'g')
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.repair"))
            .click(control("rocket.schedule.threshold"))
            .key(Keyboard.KEY_END, '\0');
        for (int i = 0; i < 8; i++) scenario.key(Keyboard.KEY_BACK, '\b');
        scenario.key(Keyboard.KEY_3, '3')
            .key(Keyboard.KEY_5, '5')
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.refuel"))
            .server(
                "draft has not changed accepted schedule",
                () -> {
                    if (!fixture.rocket.schedule()
                        .visits()
                        .isEmpty()) throw new AssertionError("Draft leaked to server");
                })
            .click(control("rocket.schedule.save"))
            .awaitServer("created route and edited action accepted", fixture::assertCreated)
            .awaitClient("saved revision synchronized", ignored -> fixture.assertClientRevision())
            .click(
                ClientTarget.of(
                    "open second destination Settings",
                    ignored -> GuiTestSupport.namedTarget("rocket.schedule.settings." + fixture.second)))
            .click(
                ClientTarget.of(
                    "expand incoming travel inside destination Settings",
                    ignored -> GuiTestSupport
                        .namedTarget("rocket.schedule.travel." + fixture.first + "." + fixture.second)))
            .click(control("rocket.schedule.allowance"))
            .key(Keyboard.KEY_END, '\0')
            .key(Keyboard.KEY_1, '1')
            .key(Keyboard.KEY_2, '2')
            .key(Keyboard.KEY_PERIOD, '.')
            .key(Keyboard.KEY_5, '5')
            .click(control("rocket.schedule.save"))
            .awaitServer("editable km/s limit is stored in m/s", () -> {
                var limit = fixture.rocket.schedule()
                    .routes()
                    .get(new VehicleSchedule.Leg(fixture.first, fixture.second));
                if (limit.allowance()
                    .isEmpty()
                    || limit.allowance()
                        .getAsDouble() != 12500)
                    throw new AssertionError("12.5 km/s was not saved as 12500 m/s");
            })
            .awaitClient("converted budget revision synchronized", ignored -> fixture.assertClientRevision())
            .click(
                ClientTarget.of(
                    "move second visit up",
                    ignored -> GuiTestSupport.namedTarget("rocket.schedule.visit.up." + fixture.second)))
            .click(control("rocket.schedule.cancel"))
            .awaitClient("cancel closes only the editor", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.content") != null)
                    throw new AssertionError("Schedule editor is still open");
                GuiTestSupport.requireScreen("galactic_orbital_map");
            })
            .server("cancel kept accepted visit order", fixture::assertCreated)
            .click(control("starmap.toolbar.vehicles"))
            .awaitClient("reopening discarded canceled reordering", ignored -> fixture.assertClientOrder(false))
            .click(
                ClientTarget.of(
                    "move second visit up",
                    ignored -> GuiTestSupport.namedTarget("rocket.schedule.visit.up." + fixture.second)))
            .click(control("rocket.schedule.save"))
            .awaitServer("reordering saved with action identity intact", fixture::assertReordered)
            .awaitClient("new revision synchronized", ignored -> fixture.assertClientRevision())
            .click(control("rocket.schedule.close"))
            .awaitClient("editor closed", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.content") != null)
                    throw new AssertionError("Schedule editor is still open");
            })
            .click(control("starmap.toolbar.vehicles"))
            .awaitClient("saved order restored on reopening", ignored -> fixture.assertClientOrder(true))
            .click(control("rocket.schedule.start"))
            .awaitClient("unfinished vehicle dispatch is rejected by the server", ignored -> {
                var status = GuiTestSupport.findNamedWidget("rocket.schedule.status");
                String actual = status instanceof TextWidget<?>text ? EnumChatFormatting.getTextWithoutFormattingCodes(
                    text.getKey()
                        .getFormatted())
                    : null;
                String expected = EnumChatFormatting
                    .getTextWithoutFormattingCodes(StatCollector.translateToLocal("galaxia.rocket.schedule.blocked"));
                if (!expected.equals(actual)) throw new AssertionError(
                    "Server dispatch rejection has not synchronized. Expected " + expected + ", got " + actual);
                fixture.assertClientOrder(true);
            })
            .capture("rocket-schedule-saved-and-reopened");
        for (int i = 0; i < 6; i++) {
            int expected = i + 3;
            scenario.click(control("rocket.schedule.add_visit"))
                .awaitClient("new destination remains editable", ignored -> assertVisitCount(expected));
        }
        final int[] scroll = { 0 };
        scenario.awaitClient("new destination is revealed below the first viewport", ignored -> {
            scroll[0] = visitScroll();
            if (scroll[0] <= 0) throw new AssertionError("The added destination was not revealed");
        })
            .click(control("rocket.schedule.mode"))
            .awaitClient("changing a setting preserves destination scroll", ignored -> {
                if (Math.abs(visitScroll() - scroll[0]) > 1)
                    throw new AssertionError("Destination list jumped after a button update");
            })
            .click(control("rocket.schedule.mode"))
            .click(ClientTarget.of("expand the new destination's incoming Travel", ignored -> incomingTravelTarget()))
            .click(control("rocket.schedule.allowance"))
            .key(Keyboard.KEY_1, '1')
            .key(Keyboard.KEY_2, '2')
            .key(Keyboard.KEY_PERIOD, '.')
            .key(Keyboard.KEY_5, '5')
            .click(control("rocket.schedule.body"))
            .awaitClient(
                "Body opens the actual Star Map",
                ignored -> GuiTestSupport.requireScreen("galactic_orbital_map"))
            .key(Keyboard.KEY_ESCAPE, '\0')
            .awaitClient("Escape restores the same draft and scroll", ignored -> assertReturnedDraft(scroll[0]))
            .click(control("rocket.schedule.body"));
        GuiTestSupport.frameInnerSolarSystem(scenario)
            .rightClick(
                ClientTarget.of(
                    "open Earth destination context",
                    ignored -> GuiTestSupport
                        .bodyTarget(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), 1)))
            .click(control("rocket.schedule.body.cancel"))
            .awaitClient("Cancel restores the same draft and scroll", ignored -> assertReturnedDraft(scroll[0]))
            .click(control("rocket.schedule.body"));
        GuiTestSupport.frameInnerSolarSystem(scenario)
            .rightClick(
                ClientTarget.of(
                    "select Earth as the destination",
                    ignored -> GuiTestSupport
                        .bodyTarget(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), 1)))
            .capture("rocket-destination-actual-map-selection")
            .click(control("rocket.schedule.body.confirm"))
            .awaitClient(
                "Select restores the stable destination draft and scroll",
                ignored -> assertReturnedDraft(scroll[0]))
            .click(control("rocket.schedule.context"))
            .click(control("rocket.schedule.target"))
            .click(control("rocket.schedule.group_kind"))
            .click(control("rocket.schedule.group_kind"))
            .click(control("rocket.schedule.group_name"));
        for (char letter : "future".toCharArray()) scenario.key(
            Keyboard.getKeyIndex(
                String.valueOf(letter)
                    .toUpperCase(java.util.Locale.ROOT)),
            letter);
        scenario.click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.save"))
            .awaitServer("new destination body context and endpoint settings are saved", () -> {
                var visits = fixture.rocket.schedule()
                    .visits();
                if (visits.size() != 8) throw new AssertionError("New destinations have not reached the server");
                var last = visits.getLast();
                if (!last.place()
                    .body()
                    .equals(fixture.rocket.bodyKey())
                    || last.place()
                        .context() != VehicleSchedule.Context.SURFACE
                    || !(last.place()
                        .target() instanceof VehicleSchedule.Group group)
                    || group.kind() != CelestialAsset.Endpoint.Kind.SILO
                    || !group.routingName()
                        .equals("future"))
                    throw new AssertionError("The newly added destination was not edited");
                var incoming = fixture.rocket.schedule()
                    .routes()
                    .get(
                        new VehicleSchedule.Leg(
                            visits.get(6)
                                .id(),
                            last.id()));
                if (incoming.allowance()
                    .isEmpty()
                    || incoming.allowance()
                        .getAsDouble() != 12500)
                    throw new AssertionError("The map round trip lost the typed flight limit");
                if (!visits.getFirst()
                    .id()
                    .equals(fixture.second)
                    || !visits.get(1)
                        .id()
                        .equals(fixture.first))
                    throw new AssertionError("Editing a new destination changed existing visit identity");
            })
            .capture("rocket-schedule-new-destination-edited-with-scroll")
            .succeed();
    }

    /** Integration contract: persisted passenger assignment drives the same map before its achievement unlock. */
    @GameTest(timeoutTicks = 2400)
    public static void passengerTransitSurvivesReloadAndKeepsLogicalStopsOnTheSharedMap(GameTestHelper helper) {
        var fixture = new TransitFixture();
        ClientTest.scenario(helper)
            .server("prepare a boarded orbital stop with a locked map", () -> fixture.prepare(helper))
            .afterTest(client -> {
                RocketTransitPacket.Handler.clear();
                if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer.closeScreen();
            })
            .server("dispatch through the authoritative runtime", fixture::start)
            .awaitClient("passenger map opens with live journey progress", ignored -> fixture.assertInFlight())
            .key(Keyboard.KEY_ESCAPE, '\0')
            .awaitClient("flight map remains active after escape", ignored -> fixture.assertInFlight())
            .capture("rocket-passenger-flight")
            .server("reload the persisted passenger and paid journey", fixture::reload)
            .awaitClient("the same vehicle remains visible after reload", ignored -> fixture.assertInFlight())
            .withinTicks(1600)
            .awaitServerAccelerated("reach a logical orbital stop", 10, fixture::assertOrbitalStop)
            .awaitClient("logical stop remains on the shared map", ignored -> fixture.assertStop())
            .key(Keyboard.KEY_ESCAPE, '\0')
            .awaitClient("logical stop cannot expose world controls", ignored -> fixture.assertStop())
            .click(control("starmap.toolbar.vehicles"))
            .awaitClient("vehicle management remains available during the stop", ignored -> {
                if (GuiTestSupport.findNamedWidget("rocket.schedule.content") == null)
                    throw new AssertionError("Vehicle management did not open");
            })
            .click(control("rocket.schedule.overview"))
            .awaitClient(
                "overview shows the actual vehicle and remaining propellant",
                ignored -> fixture.assertOverview())
            .capture("rocket-passenger-vehicle-overview")
            .click(control("rocket.schedule.overview"))
            .click(control("rocket.schedule.close"))
            .capture("rocket-passenger-logical-stop")
            .server("remove the isolated passenger fixture", fixture::restore)
            .awaitClient(
                "server clears the passenger assignment",
                ignored -> {
                    if (RocketTransitPacket.Handler.current() != null)
                        throw new AssertionError("Passenger assignment has not cleared");
                })
            .awaitClient(
                "released passenger map closes",
                ignored -> {
                    if (ModularScreen.getCurrent() != null)
                        throw new AssertionError("Released transit map is still open");
                })
            .succeed();
    }

    /** Integration contract: item cargo, deploy supplies and propellant targets retain their typed identities. */
    @GameTest(timeoutTicks = 2400)
    public static void cargoRefuelAndDeployActionsUseRealResourceSelectors(GameTestHelper helper) {
        var fixture = new Fixture();
        var scenario = ClientTest.scenario(helper)
            .server("prepare vehicle and selectable item and fluid", () -> fixture.prepare(helper))
            .client(
                "register fixture mirror cleanup",
                client -> client
                    .afterTest(() -> CelestialAssetStore.CLIENT.destroyAssetInternal(fixture.station.assetId)));
        GuiTestSupport.openMap(scenario, helper)
            .click(control("starmap.toolbar.vehicles"))
            .awaitClient("vehicle schedule synchronized", ignored -> fixture.assertClientRevision())
            .click(control("rocket.schedule.add_visit"))
            .awaitClient("draft visit visible", ignored -> assertVisitCount(1))
            .click(control("rocket.schedule.context"))
            .click(control("rocket.schedule.target"))
            .click(control("rocket.schedule.group_kind"))
            .click(control("rocket.schedule.group_scope"))
            .click(control("rocket.schedule.group_name"))
            .key(Keyboard.KEY_G, 'g')
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.unload"))
            .click(control("rocket.schedule.resource"))
            .click(
                ClientTarget.of(
                    "satellite dish cargo",
                    ignored -> resourceTarget(
                        fixture.dish.toStack(1)
                            .getDisplayName())))
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.load"))
            .click(control("rocket.schedule.resource"))
            .click(
                ClientTarget.of(
                    "cargo to load",
                    ignored -> resourceTarget(
                        fixture.dish.toStack(1)
                            .getDisplayName())))
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.refuel"))
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.save"))
            .awaitServer("tank to full accepted at orbital Silo", fixture::assertFullRefuel)
            .awaitClient("full refuel revision synchronized", ignored -> fixture.assertClientRevision())
            .click(control("rocket.schedule.close"))
            .click(control("starmap.toolbar.vehicles"))
            .awaitClient("full refuel reopened", ignored -> fixture.assertClientRevision())
            .click(
                ClientTarget.of(
                    "open saved tank-to-full action",
                    ignored -> GuiTestSupport.namedTarget("rocket.schedule.action.amount." + fixture.refuelAction)))
            .awaitClient("full refuel requires no numeric input", ignored -> {
                var details = GuiTestSupport.findNamedWidget("rocket.schedule.inspector");
                if (details == null) throw new AssertionError("Action inspector has not opened");
                if (!TreeUtil.flatListBFS(details, widget -> widget instanceof TextFieldWidget)
                    .isEmpty()) throw new AssertionError("Full refuel still requires numeric input");
            })
            .capture("rocket-refuel-full-reopened")
            .click(control("rocket.schedule.refuel.mode"))
            .click(control("rocket.schedule.add_fluid"))
            .click(
                ClientTarget
                    .of("select exact fluid target", ignored -> resourceTarget(FluidRegistry.WATER.getLocalizedName())))
            .click(ClientTarget.of("refuel target amount", ignored -> singleAmountTarget()))
            .key(Keyboard.KEY_END, '\0');
        for (int i = 0; i < 8; i++) scenario.key(Keyboard.KEY_BACK, '\b');
        scenario.key(Keyboard.KEY_2, '2')
            .key(Keyboard.KEY_0, '0')
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.deploy"))
            .click(control("rocket.schedule.resource"))
            .click(
                ClientTarget.of(
                    "satellite dish deploy supplies",
                    ignored -> resourceTarget(
                        fixture.dish.toStack(1)
                            .getDisplayName())))
            .click(control("rocket.schedule.back"))
            .click(control("rocket.schedule.save"))
            .awaitServer("typed resource actions accepted", () -> fixture.assertResourceActions(false))
            .awaitClient("saved resource actions synchronized", ignored -> fixture.assertClientRevision())
            .click(
                ClientTarget.of(
                    "edit cargo resource directly on destination card",
                    ignored -> GuiTestSupport
                        .namedTarget("rocket.schedule.action.resource." + fixture.resourceActionIds.getFirst())))
            .click(
                ClientTarget.of(
                    "keep selected cargo",
                    ignored -> resourceTarget(
                        fixture.dish.toStack(1)
                            .getDisplayName())))
            .click(control("rocket.schedule.back"))
            .click(
                ClientTarget.of(
                    "edit cargo amount directly on destination card",
                    ignored -> GuiTestSupport
                        .namedTarget("rocket.schedule.action.amount." + fixture.resourceActionIds.getFirst())))
            .click(control("rocket.schedule.amount"))
            .key(Keyboard.KEY_END, '\0')
            .key(Keyboard.KEY_BACK, '\b')
            .key(Keyboard.KEY_1, '1')
            .click(control("rocket.schedule.back"))
            .click(
                ClientTarget.of(
                    "open deploy settings from its destination card",
                    ignored -> GuiTestSupport.namedTarget("rocket.schedule.action.edit." + fixture.deployAction)))
            .click(
                ClientTarget.of(
                    "move deploy action up",
                    ignored -> GuiTestSupport.namedTarget("rocket.schedule.action.up." + fixture.deployAction)))
            .click(control("rocket.schedule.save"))
            .awaitServer("action reorder preserves cargo and propellant", () -> fixture.assertResourceActions(true))
            .awaitClient("resource action reorder synchronized", ignored -> fixture.assertClientRevision())
            .capture("rocket-schedule-item-fluid-actions")
            .succeed();
    }

    private static void assertReturnedDraft(int scroll) {
        GuiTestSupport.requireScreen("galaxia:vehicle_schedule");
        assertVisitCount(8);
        int restored = visitScroll();
        if (Math.abs(restored - scroll) > 1) throw new AssertionError(
            "Map navigation lost destination scroll. Expected " + scroll + ", got " + restored);
        var field = GuiTestSupport.findNamedWidget("rocket.schedule.allowance");
        if (!(field instanceof TextFieldWidget limit) || !"12.5".equals(limit.getText()))
            throw new AssertionError("Map navigation lost the focused flight-limit edit");
    }

    private static ClickTarget incomingTravelTarget() {
        var inspector = GuiTestSupport.findNamedWidget("rocket.schedule.inspector");
        if (inspector == null) return null;
        var rows = TreeUtil.flatListBFS(
            inspector,
            widget -> widget.getName() != null && widget.getName()
                .startsWith("rocket.schedule.travel."));
        return rows.size() == 1 ? GuiTestSupport.namedTarget(
            rows.getFirst()
                .getName())
            : null;
    }

    private static ClickTarget resourceTarget(String displayName) {
        var details = GuiTestSupport.findNamedWidget("rocket.schedule.choices");
        if (details == null) return null;
        var matches = TreeUtil
            .flatListBFS(
                details,
                widget -> widget instanceof ButtonWidget<?>button && button.getOverlay() instanceof IKey key
                    && displayName.equals(EnumChatFormatting.getTextWithoutFormattingCodes(key.getFormatted())))
            .stream()
            .toList();
        if (matches.size() != 1) return null;
        var button = matches.getFirst();
        return new ClickTarget(
            button,
            GuiTestSupport.widgetBounds(button),
            () -> button.isValid() && button.getPanel()
                .isBelowMouse(button));
    }

    private static ClickTarget singleAmountTarget() {
        var details = GuiTestSupport.findNamedWidget("rocket.schedule.inspector");
        if (details == null) return null;
        var fields = TreeUtil.flatListByType(details, TextFieldWidget.class);
        if (fields.size() != 1) return null;
        var field = fields.getFirst();
        return new ClickTarget(
            field,
            GuiTestSupport.widgetBounds(field),
            () -> field.isValid() && field.getPanel()
                .isBelowMouse(field));
    }

    private static void assertVisitCount(int expected) {
        var screen = ModularScreen.getCurrent();
        if (screen == null) throw new AssertionError("No ModularUI screen");
        long count = screen.getPanelManager()
            .getOpenPanels()
            .stream()
            .flatMap(
                panel -> TreeUtil.flatListBFS(
                    panel,
                    widget -> widget.getName() != null && widget.getName()
                        .startsWith("rocket.schedule.select."))
                    .stream())
            .count();
        if (count != expected) throw new AssertionError("Expected " + expected + " visible visits, got " + count);
    }

    private static EntityPlayerMP serverPlayer(GameTestHelper helper) {
        for (Object candidate : helper.getWorld().playerEntities) {
            if (candidate instanceof EntityPlayerMP player) return player;
        }
        throw new AssertionError("Integrated client player has not joined");
    }

    private static final class TransitFixture {

        private EntityPlayerMP player;
        private Rocket rocket;
        private EntityRocket entity;
        private NBTTagCompound original;
        private Map<FluidKey, Long> initialPropellant;
        private double originalX;
        private double originalY;
        private double originalZ;

        void prepare(GameTestHelper helper) {
            player = serverPlayer(helper);
            if (GTTeamsCompat.hasAchievement(player, Achievement.STAR_MAP_ACTIVATION))
                throw new AssertionError("Transit fixture requires the initial locked Star Map");
            var registry = Galaxia.proxy.serverRuntime()
                .rockets();
            original = registry.serializeNBT();
            originalX = player.posX;
            originalY = player.posY;
            originalZ = player.posZ;
            helper.afterTest(this::restore);
            var design = new RocketBlueprint();
            int row = 0;
            for (var definition : List.of(
                RocketPartRegistry.CAPSULE_T1,
                RocketPartRegistry.STORAGE_T1,
                RocketPartRegistry.FUEL_TANK_T1,
                RocketPartRegistry.ENGINE_T1)) {
                if (!design.addPart(new RocketPartInstance(definition, 0, row, 0, false)))
                    throw new AssertionError("Production module does not fit the fixture blueprint");
                row += definition.height();
            }
            rocket = Rocket.construct(
                GTTeamsCompat.getTeam(player),
                CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                UUID.randomUUID(),
                design);
            for (var slot : rocket.buildSlots()) {
                if (!rocket.acceptProduced(
                    Rocket.Module.produced(
                        rocket.id(),
                        slot,
                        100,
                        new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))))
                    throw new AssertionError("Production module was not accepted");
            }
            if (!rocket.assemble(rocket.assemblySiloId()))
                throw new AssertionError("Production fixture did not assemble");
            var capacities = new java.util.LinkedHashMap<FluidKey, Long>();
            for (var module : rocket.modules()) module.part()
                .def()
                .storage()
                .propellantCapacities()
                .forEach((fuel, amount) -> capacities.merge(fuel, amount, Math::addExact));
            capacities.forEach((fuel, amount) -> rocket.refuel(fuel, amount, false));
            initialPropellant = rocket.propellantAmounts();
            registry.register(rocket);
            entity = new EntityRocket(helper.getWorld());
            entity.setPosition(player.posX, player.posY, player.posZ);
            entity.rotationYaw = player.rotationYaw;
            if (!entity.bind(rocket)) throw new AssertionError("Production entity did not bind to the vehicle");
            entity.setProjectionVisible(true);
            helper.getWorld()
                .spawnEntityInWorld(entity);
            if (!entity.interactFirst(player))
                throw new AssertionError("Production capsule did not accept its passenger");
            var ground = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.SURFACE, null),
                List.of());
            var orbit = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                new VehicleSchedule.Place(rocket.bodyKey(), VehicleSchedule.Context.ORBIT, null),
                List.of());
            var landing = new VehicleSchedule.Visit(
                UUID.randomUUID(),
                new VehicleSchedule.Place(
                    rocket.bodyKey(),
                    VehicleSchedule.Context.SURFACE,
                    new VehicleSchedule.Group("horizon-return-pad", CelestialAsset.Endpoint.Kind.LANDING_PAD, null)),
                List.of());
            var schedule = new VehicleSchedule(List.of(ground, orbit, landing), VehicleSchedule.Mode.RUN_ONCE);
            double allowance = rocket.propulsion()
                .usableDeltaVMps();
            if (!(allowance > 0)) throw new AssertionError("Production fixture has no usable propulsion");
            schedule = schedule
                .withRouteSettings(
                    new VehicleSchedule.Leg(ground.id(), orbit.id()),
                    new VehicleSchedule.RouteSettings(allowance, RoutePriority.PRIORITIZE_DV))
                .withRouteSettings(
                    new VehicleSchedule.Leg(orbit.id(), landing.id()),
                    new VehicleSchedule.RouteSettings(0, RoutePriority.PRIORITIZE_DV));
            if (rocket.saveSchedule(rocket.teamId(), rocket.revision(), schedule) != Rocket.CommandResult.APPLIED)
                throw new AssertionError("Passenger fixture schedule was rejected");
        }

        void start() {
            if (Galaxia.proxy.serverRuntime()
                .startRocket(player, rocket.id(), rocket.revision()) != Rocket.CommandResult.APPLIED)
                throw new AssertionError("Authoritative runtime rejected the passenger fixture");
        }

        void reload() {
            var registry = Galaxia.proxy.serverRuntime()
                .rockets();
            if (initialPropellant.equals(rocket.propellantAmounts()))
                throw new AssertionError("Flight has not paid actual propellant");
            var id = rocket.id();
            var charged = rocket.propellantAmounts();
            registry.restoreNBT(registry.serializeNBT(), RocketPartRegistry.instance());
            rocket = registry.find(id);
            if (registry.passengerVehicle(player.getUniqueID()) != rocket
                || !charged.equals(rocket.propellantAmounts()))
                throw new AssertionError("Reload lost the passenger or charged the journey again");
        }

        void assertInFlight() {
            assertMap();
            var view = RocketTransitPacket.Handler.current();
            if (view == null || !view.vehicleId()
                .equals(rocket.id())
                || !view.restricted()
                || view.journey() == null
                || view.elapsedTicks() <= 0
                || view.remainingTicks() <= 0) throw new AssertionError("Live passenger journey has not synchronized");
            var progress = GuiTestSupport.findNamedWidget("rocket.transit.progress");
            if (!(progress instanceof TextWidget<?>text) || text.getKey()
                .getFormatted()
                .isEmpty()) throw new AssertionError("Journey progress is not visible");
        }

        void assertOrbitalStop() {
            var view = rocket.transitView();
            if (view.journey() != null || view.source()
                .context() != VehicleSchedule.Context.ORBIT || !view.restricted())
                throw new AssertionError("Vehicle has not reached the logical orbital stop");
        }

        void assertStop() {
            assertMap();
            var view = RocketTransitPacket.Handler.current();
            if (view == null || view.journey() != null
                || !view.restricted()
                || view.source()
                    .context() != VehicleSchedule.Context.ORBIT)
                throw new AssertionError("Logical stop has not synchronized");
        }

        void assertMap() {
            var screen = ModularScreen.getCurrent();
            if (screen == null || !GalacticMapWidget.SCREEN_NAME.equals(
                screen.getMainPanel()
                    .getName()))
                throw new AssertionError("Passenger is not on the shared Star Map");
        }

        void assertOverview() {
            var rows = GuiTestSupport.findNamedWidget("rocket.schedule.overview.rows");
            var deltaV = GuiTestSupport.findNamedWidget("rocket.schedule.summary.delta_v");
            if (rows == null || !(deltaV instanceof TextWidget<?>text))
                throw new AssertionError("Vehicle overview has not opened");
            String expected = StatCollector.translateToLocalFormatted(
                "galaxia.rocket.schedule.summary_delta_v",
                java.math.BigDecimal.valueOf(
                    rocket.propulsion()
                        .usableDeltaVMps() / 1000)
                    .setScale(3, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString());
            if (!expected.equals(
                EnumChatFormatting.getTextWithoutFormattingCodes(
                    text.getKey()
                        .getFormatted())))
                throw new AssertionError("Displayed usable delta-v does not match actual remaining propulsion");
            var labels = TreeUtil.flatListBFS(rows, widget -> widget instanceof TextWidget<?>)
                .stream()
                .map(
                    widget -> EnumChatFormatting.getTextWithoutFormattingCodes(
                        ((TextWidget<?>) widget).getKey()
                            .getFormatted()))
                .toList();
            String modules = StatCollector.translateToLocalFormatted(
                "galaxia.rocket.schedule.summary_modules",
                rocket.modules()
                    .size(),
                rocket.buildSlots()
                    .size());
            if (!labels.contains(modules)) throw new AssertionError("Concrete module count has not synchronized");
            for (var entry : rocket.propellantAmounts()
                .entrySet()) {
                String amount = StatCollector.translateToLocalFormatted(
                    "galaxia.rocket.schedule.summary_amount",
                    entry.getKey()
                        .fluid()
                        .getLocalizedName(
                            entry.getKey()
                                .toStack(1)),
                    entry.getValue());
                if (!labels.contains(amount)) throw new AssertionError("Actual propellant amount has not synchronized");
            }
        }

        void restore() {
            if (original == null) return;
            Galaxia.proxy.serverRuntime()
                .rockets()
                .restoreNBT(original, RocketPartRegistry.instance());
            if (player != null) {
                player.mountEntity(null);
                player.setPositionAndUpdate(originalX, originalY, originalZ);
            }
            if (entity != null) entity.setDead();
        }
    }

    private static int visitScroll() {
        var widget = GuiTestSupport.findNamedWidget("rocket.schedule.visits");
        if (!(widget instanceof AbstractScrollWidget<?, ?>scroll))
            throw new AssertionError("Destination viewport is unavailable");
        return scroll.getScrollY();
    }

    private static final class Fixture {

        private Rocket rocket;
        private UUID first;
        private UUID second;
        private UUID action;
        private ItemStackWrapper dish;
        private UUID deployAction;
        private UUID refuelAction;
        private List<UUID> resourceActionIds;
        private CelestialAsset station;

        void prepare(GameTestHelper helper) {
            EntityPlayerMP player = serverPlayer(helper);
            var owner = player;
            int itemSlot = (player.inventory.currentItem + 1) % 9;
            int fluidSlot = (player.inventory.currentItem + 2) % 9;
            var originalItem = player.inventory.getStackInSlot(itemSlot);
            var originalFluid = player.inventory.getStackInSlot(fluidSlot);
            helper.afterTest(() -> {
                owner.inventory.setInventorySlotContents(itemSlot, originalItem);
                owner.inventory.setInventorySlotContents(fluidSlot, originalFluid);
                owner.inventoryContainer.detectAndSendChanges();
            });
            dish = ItemStackWrapper.of(new ItemStack(GalaxiaItemList.SATELLITE_DISH.getItem()));
            player.inventory.setInventorySlotContents(itemSlot, dish.toStack(8));
            player.inventory.setInventorySlotContents(fluidSlot, new ItemStack(Items.water_bucket));
            player.inventoryContainer.detectAndSendChanges();
            station = CelestialAsset
                .create(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), CelestialAsset.Kind.STATION, true);
            station.setDisplayName("Schedule QA Station");
            CelestialAssetStore.registerAsset(GTTeamsCompat.getTeam(player), station);
            helper.afterTest(() -> CelestialAssetStore.SERVER.destroyAssetInternal(station.assetId));
            var registry = Galaxia.proxy.serverRuntime()
                .rockets();
            var original = registry.serializeNBT();
            helper.afterTest(() -> registry.restoreNBT(original, RocketPartRegistry.instance()));
            var design = new RocketBlueprint();
            design.addPart(new RocketPartInstance(RocketPartRegistry.CAPSULE_T1, 0, 0, 0, false));
            rocket = Rocket.construct(
                GTTeamsCompat.getTeam(player),
                CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                UUID.randomUUID(),
                design);
            registry.register(rocket);
        }

        void assertCreated() {
            var schedule = rocket.schedule();
            if (schedule.visits()
                .size() != 2 || schedule.mode() != VehicleSchedule.Mode.LOOP)
                throw new AssertionError("Created visits and execution mode have not reached the server");
            if (first == null) {
                first = schedule.visits()
                    .get(0)
                    .id();
                second = schedule.visits()
                    .get(1)
                    .id();
            }
            if (!schedule.visits()
                .stream()
                .map(VehicleSchedule.Visit::id)
                .toList()
                .equals(List.of(first, second))) throw new AssertionError("Canceled reordering reached the server");
            var place = schedule.visits()
                .get(1)
                .place();
            if (place.context() != VehicleSchedule.Context.ORBIT
                || !(place.target() instanceof VehicleSchedule.Group group)
                || group.kind() != CelestialAsset.Endpoint.Kind.LANDING_PAD
                || !station.assetId.id()
                    .equals(group.stationId()))
                throw new AssertionError("Orbital station landing Pad selection was not saved");
            var actions = schedule.visits()
                .get(1)
                .actions();
            if (actions.size() != 1 || !(actions.getFirst() instanceof VehicleSchedule.Repair repair)
                || repair.thresholdPercent() != 35) throw new AssertionError("Edited repair threshold was not saved");
            if (action == null) action = actions.getFirst()
                .id();
            if (!action.equals(
                actions.getFirst()
                    .id()))
                throw new AssertionError("Action identity changed");
        }

        void assertReordered() {
            var visits = rocket.schedule()
                .visits();
            if (!visits.stream()
                .map(VehicleSchedule.Visit::id)
                .toList()
                .equals(List.of(second, first)))
                throw new AssertionError("Reordered visits have not reached the server");
            if (visits.getFirst()
                .actions()
                .size() != 1
                || !visits.getFirst()
                    .actions()
                    .getFirst()
                    .id()
                    .equals(action))
                throw new AssertionError("Reordering detached the action from its visit");
        }

        void assertClientRevision() {
            var widget = GuiTestSupport.findNamedWidget("rocket.schedule.revision");
            if (!(widget instanceof TextWidget<?>text) || !text.getKey()
                .getFormatted()
                .contains(Long.toString(rocket.revision())))
                throw new AssertionError("Accepted vehicle revision has not synchronized");
            var body = GuiTestSupport.findNamedWidget("rocket.schedule.body");
            if (body instanceof ButtonWidget<?>button && button.getOverlay() instanceof IKey key) {
                String expected = CelestialRegistry.get(rocket.bodyKey())
                    .orElseThrow()
                    .displayName();
                if (!expected.equals(EnumChatFormatting.getTextWithoutFormattingCodes(key.getFormatted())))
                    throw new AssertionError(
                        "Current body label does not resolve " + rocket.bodyKey() + ": " + key.getFormatted());
            }
        }

        void assertFullRefuel() {
            var visits = rocket.schedule()
                .visits();
            if (visits.size() != 1) throw new AssertionError("Full-refuel destination has not reached the server");
            var visit = visits.getFirst();
            if (!visit.place()
                .canRefuel()
                || visit.actions()
                    .size() != 3
                || !(visit.actions()
                    .getLast() instanceof VehicleSchedule.Refuel refuel)
                || !refuel.toFull()
                || !refuel.targets()
                    .isEmpty())
                throw new AssertionError("Tank-to-full intent was not accepted at the Silo");
            if (refuelAction == null) refuelAction = refuel.id();
            if (!refuelAction.equals(refuel.id())) throw new AssertionError("Refuel identity changed");
        }

        void assertResourceActions(boolean reordered) {
            if (rocket.schedule()
                .visits()
                .size() != 1) throw new AssertionError("Resource visit has not been saved");
            var place = rocket.schedule()
                .visits()
                .getFirst()
                .place();
            if (place.context() != VehicleSchedule.Context.ORBIT
                || !(place.target() instanceof VehicleSchedule.Group group)
                || !station.assetId.id()
                    .equals(group.stationId())
                || !group.routingName()
                    .equals("g")
                || group.kind() != CelestialAsset.Endpoint.Kind.SILO)
                throw new AssertionError("Empty station group identity was not saved");
            var actions = rocket.schedule()
                .visits()
                .getFirst()
                .actions();
            if (actions.size() != 4) throw new AssertionError("Resource actions have not been saved");
            if (resourceActionIds == null) {
                resourceActionIds = actions.stream()
                    .map(VehicleSchedule.Action::id)
                    .toList();
                deployAction = resourceActionIds.get(3);
            }
            var expected = reordered ? List.of(
                resourceActionIds.get(0),
                resourceActionIds.get(1),
                resourceActionIds.get(3),
                resourceActionIds.get(2)) : resourceActionIds;
            if (!actions.stream()
                .map(VehicleSchedule.Action::id)
                .toList()
                .equals(expected)) throw new AssertionError("Resource action identities or order changed");
            if (!(actions.getFirst() instanceof VehicleSchedule.Transfer transfer) || !transfer.resource()
                .equals(dish) || transfer.direction() != VehicleSchedule.Direction.UNLOAD || transfer.amount() != 1)
                throw new AssertionError("Typed item transfer changed");
            if (!(actions.get(1) instanceof VehicleSchedule.Transfer load) || !load.resource()
                .equals(dish) || load.direction() != VehicleSchedule.Direction.LOAD || load.amount() != 1)
                throw new AssertionError("Explicit cargo loading changed");
            var refuel = actions.stream()
                .filter(VehicleSchedule.Refuel.class::isInstance)
                .map(VehicleSchedule.Refuel.class::cast)
                .findFirst()
                .orElseThrow();
            if (refuel.toFull() || !refuel.id()
                .equals(refuelAction)
                || !refuel.targets()
                    .equals(java.util.Map.of(new FluidKey(FluidRegistry.WATER, null), 20L)))
                throw new AssertionError("Propellant targets changed or became cargo");
            var deploy = actions.stream()
                .filter(VehicleSchedule.Deploy.class::isInstance)
                .map(VehicleSchedule.Deploy.class::cast)
                .findFirst()
                .orElseThrow();
            if (!deploy.cargo()
                .equals(dish) || deploy.amount() != 1) throw new AssertionError("Deploy supplies changed");
        }

        void assertClientOrder(boolean reordered) {
            assertClientRevision();
            var a = GuiTestSupport.findNamedWidget("rocket.schedule.select." + first);
            var b = GuiTestSupport.findNamedWidget("rocket.schedule.select." + second);
            if (a == null || b == null
                || (GuiTestSupport.widgetBounds(a).y > GuiTestSupport.widgetBounds(b).y) != reordered)
                throw new AssertionError("Visible visits do not match the accepted order");
        }
    }
}
