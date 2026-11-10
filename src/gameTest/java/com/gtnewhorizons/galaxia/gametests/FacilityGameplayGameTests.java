package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.station.StationManagementScreen;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetCreateRequestPacket;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientScenario;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

/**
 * Real client command integration in the disposable Horizon world, without mouse or keyboard coverage.
 * Assets and changed fixture facts are cleaned up. Prospecting scan progress and cross-body deliveries can remain
 * until world shutdown.
 */
@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "NotEnoughItems" })
public final class FacilityGameplayGameTests {

    private FacilityGameplayGameTests() {}

    /** Integration contract: paid construction refunds on cancellation and consumes materials on completion. */
    @GameTest(timeoutTicks = 1200)
    public static void paidConstructionCanBeCancelledRefundedAndCompleted(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        GuiTestSupport.openMap(helper, 2)
            .server("prepare construction materials", () -> {
                fixture.prepare(helper);
                fixture.prepareModuleConstruction();
            })
            .client("register client cleanup", c -> c.afterTest(fixture::cleanupClient))
            .awaitClient("empty station synchronized", c -> fixture.facility(true, 0))
            .client(
                "show station while API commands build modules",
                c -> StationManagementScreen.open(fixture.ids.getFirst(), false))
            .awaitClient("station observation visible", c -> GuiTestSupport.requireStation())
            .client(
                "request paid storage construction",
                c -> CelestialClient.createModules(
                    fixture.ids.getFirst(),
                    FacilityModuleKind.STORAGE,
                    false,
                    List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))))
            .awaitServer(
                "construction accepted and materials debited",
                () -> fixture.assertCancellationBuildPaid(false))
            .awaitClient(
                "paid construction synchronized before cancellation",
                c -> fixture.assertCancellationBuildPaid(true))
            .capture("api-construction-before-cancel")
            .client(
                "cancel paid construction from client",
                c -> CelestialClient.cancelModuleOperation(fixture.ids.getFirst(), fixture.cancelledModuleId))
            .awaitServer(
                "server returned materials and removed construction",
                () -> fixture.assertConstructionRefunded(false))
            .awaitClient("refund and removed tile synchronized", c -> fixture.assertConstructionRefunded(true))
            .client("request paid storage and tank", c -> {
                CelestialClient.createModules(
                    fixture.ids.getFirst(),
                    FacilityModuleKind.STORAGE,
                    false,
                    List.of(ModulePlacement.at(StationTileCoord.of(1, 0))));
                CelestialClient.createModules(
                    fixture.ids.getFirst(),
                    FacilityModuleKind.TANK,
                    false,
                    List.of(ModulePlacement.at(StationTileCoord.of(-1, 0))));
            })
            .awaitServer("server accepted construction operations", () -> fixture.assertModules(false, false))
            .awaitClient("construction orders visible on client", c -> fixture.assertModules(true, false))
            .capture("api-module-construction-in-progress")
            .withinTicks(900)
            .awaitServerAccelerated(
                "simulation ticks completed both modules",
                10,
                () -> fixture.assertModules(false, true))
            .withinTicks(150)
            .awaitClient("completed modules and paid inventory synchronized", c -> fixture.assertModules(true, true))
            .capture("api-module-construction-complete")
            .succeed();
    }

    /**
     * Integration contract: upgrade cancellation refunds exactly, and a retry preserves identity and increases
     * capacity.
     */
    @GameTest(timeoutTicks = 700)
    public static void paidStorageUpgradeCanBeCancelledRetriedAndCompleted(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        GuiTestSupport.openMap(helper, 2)
            .server("prepare operational storage and exact upgrade materials", () -> {
                fixture.prepare(helper);
                fixture.prepareStorageUpgrade();
            })
            .client("register client cleanup", c -> c.afterTest(fixture::cleanupClient))
            .awaitClient("original storage synchronized", c -> fixture.assertUpgradeState(true, ModuleTier.HV))
            .client("show storage before ordering and cancelling upgrades", c -> fixture.showStation(0))
            .awaitClient("station observation visible", c -> GuiTestSupport.requireStation())
            .client(
                "request paid storage upgrade",
                c -> CelestialClient
                    .planModuleTierUpgrade(fixture.ids.getFirst(), fixture.upgradedModuleId, ModuleTier.EV, true))
            .awaitServer("upgrade accepted and materials reserved", () -> fixture.assertUpgradePaid(false))
            .awaitClient("paid upgrade synchronized", c -> fixture.assertUpgradePaid(true))
            .client(
                "cancel paid upgrade",
                c -> CelestialClient.cancelModuleOperation(fixture.ids.getFirst(), fixture.upgradedModuleId))
            .awaitServer("original module and materials restored", () -> fixture.assertUpgradeRefunded(false))
            .awaitClient("cancellation and refund synchronized", c -> fixture.assertUpgradeRefunded(true))
            .client(
                "order upgrade again using refunded materials",
                c -> CelestialClient
                    .planModuleTierUpgrade(fixture.ids.getFirst(), fixture.upgradedModuleId, ModuleTier.EV, true))
            .awaitServer("new paid upgrade accepted", () -> fixture.assertUpgradePaid(false))
            .awaitClient("new paid upgrade synchronized", c -> fixture.assertUpgradePaid(true))
            .withinTicks(600)
            .awaitServerAccelerated(
                "simulation ticks completed EV storage upgrade",
                10,
                () -> fixture.assertUpgradeState(false, ModuleTier.EV))
            .withinTicks(150)
            .awaitClient(
                "upgraded identity location and capacity synchronized",
                c -> fixture.assertUpgradeState(true, ModuleTier.EV))
            .succeed();
    }

    /** Integration contract: independent station and asteroid routes launch together and conserve their own cargo. */
    @GameTest(timeoutTicks = 1000)
    public static void hammerRoutesLaunchTogetherAndConserveCargo(GameTestHelper helper) {
        Fixture station = new Fixture();
        Fixture asteroids = new Fixture();
        Fixture outbound = new Fixture();
        Fixture inbound = new Fixture();
        List<Fixture> routes = List.of(station, asteroids, outbound, inbound);
        List<Fixture> crossBody = List.of(asteroids, outbound, inbound);
        ClientTest.scenario(helper)
            .server("prepare four isolated HAMMER routes", () -> {
                for (Fixture fixture : routes) fixture.prepare(helper);
                station.prepareHammer();
                asteroids.prepareHammer(true);
                var planet = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
                outbound.prepareHammer(
                    outbound.addFacility(outbound.generatedAsteroid(11), CelestialAsset.Kind.AUTOMATED_OUTPOST),
                    outbound.addFacility(planet, CelestialAsset.Kind.AUTOMATED_STATION),
                    HammerVariant.BIG);
                inbound.prepareHammer(
                    inbound.addFacility(planet, CelestialAsset.Kind.AUTOMATED_STATION),
                    inbound.addFacility(inbound.generatedAsteroid(12), CelestialAsset.Kind.AUTOMATED_OUTPOST),
                    HammerVariant.BIG);
            })
            .client(
                "register all route cleanup",
                c -> { for (Fixture fixture : routes) c.afterTest(fixture::cleanupClient); })
            .awaitClient("all eight route endpoints synchronized", c -> {
                for (Fixture fixture : routes) {
                    fixture.facility(true, 0);
                    fixture.facility(true, 1);
                }
            })
            .client(
                "start all four shipments without waiting between requests",
                c -> { for (Fixture fixture : routes) fixture.configureShipment(true); })
            .withinTicks(300)
            .awaitServer(
                "all cross-body trajectories conserve cargo and spend launch energy",
                () -> { for (Fixture fixture : crossBody) fixture.assertInFlight(false); })
            .server(
                "remember the three server-owned flights",
                () -> { for (Fixture fixture : crossBody) fixture.recordFlight(); })
            .awaitClient(
                "the same cross-body trajectories and inventories synchronized",
                c -> { for (Fixture fixture : crossBody) fixture.assertInFlight(true); })
            .withinTicks(30)
            .awaitServer(
                "ordinary ticks advance every cross-body shipment",
                () -> { for (Fixture fixture : crossBody) fixture.assertFlightAdvanced(); })
            .withinTicks(300)
            .awaitServer(
                "station shipment delivered cargo and retained its reserve",
                () -> station.assertDelivered(false))
            .awaitClient("station delivery and logistics settings synchronized", c -> station.assertDelivered(true))
            .succeed();
    }

    /** Integration contract: stock targets and construction demand both need explicit receiver import permission. */
    @GameTest(timeoutTicks = 1500)
    public static void stockAndConstructionImportsRequirePermission(GameTestHelper helper) {
        Fixture stock = new Fixture();
        Fixture construction = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .server("prepare independent stock and construction shipments", () -> {
                stock.prepare(helper);
                stock.prepareHammer();
                construction.prepare(helper);
                construction.prepareConstructionShipment();
            })
            .client("register both fixture mirrors for cleanup", c -> {
                c.afterTest(stock::cleanupClient);
                c.afterTest(construction::cleanupClient);
            })
            .awaitClient("all four import endpoints synchronized", c -> {
                stock.facility(true, 0);
                stock.facility(true, 1);
                construction.facility(true, 0);
                construction.facility(true, 1);
            })
            .client("request stock and construction with both receiver imports disabled", c -> {
                stock.configureShipment(false);
                CelestialClient.configureHammer(
                    construction.ids.getFirst(),
                    construction.hammerId,
                    AllowShootingConfig.ALWAYS,
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
                CelestialClient.updateLogisticsConfig(
                    construction.ids.getFirst(),
                    construction.cargo,
                    new LogisticsResourceConfig(0, 1, false, true),
                    LogisticsConfigAccessMode.FULL);
                CelestialClient.createModules(
                    construction.ids.get(1),
                    FacilityModuleKind.STORAGE,
                    false,
                    List.of(ModulePlacement.at(StationTileCoord.of(1, 0))));
            })
            .awaitServer("both demand types remain blocked", () -> {
                stock.assertImportBlocked(false);
                construction.assertConstructionImport(false, false);
            })
            .awaitClient("both blocked states synchronized", c -> {
                stock.assertImportBlocked(true);
                construction.assertConstructionImport(true, false);
            });
        scenario.serverSequence()
            .thenIdle(100);
        scenario.server("ordinary dispatch ticks respect both disabled imports", () -> {
            stock.assertImportBlocked(false);
            construction.assertConstructionImport(false, false);
        })
            .client("client still sees blocked stock and construction", c -> {
                stock.assertImportBlocked(true);
                construction.assertConstructionImport(true, false);
            })
            .client("authorize both imports without adding a standing construction stock target", c -> {
                var config = stock.facility(true, 1).logisticsConfig.get(stock.cargo);
                CelestialClient.updateLogisticsConfig(
                    stock.ids.get(1),
                    stock.cargo,
                    config.withImportEnabled(true),
                    LogisticsConfigAccessMode.FULL);
                CelestialClient.updateLogisticsConfig(
                    construction.ids.get(1),
                    construction.cargo,
                    new LogisticsResourceConfig(0, 1, true, false),
                    LogisticsConfigAccessMode.FULL);
            })
            .withinTicks(900)
            .awaitServerAccelerated("both permitted shipments settle and construction completes", 10, () -> {
                stock.assertDelivered(false);
                construction.assertConstructionImport(false, true);
            })
            .withinTicks(150)
            .awaitClient("conserved stock and completed paid construction synchronized", c -> {
                stock.assertDelivered(true);
                construction.assertConstructionImport(true, true);
            })
            .succeed();
    }

    /**
     * Integration contract: losing a receiver preserves the launched trajectory and loses cargo at arrival without
     * refund.
     */
    @GameTest(timeoutTicks = 2400)
    public static void destroyingReceiverDoesNotRefundInFlightCargo(GameTestHelper helper) {
        destroyEndpointDuringFlight(helper, 1);
    }

    /** Integration contract: losing the supplier after launch still delivers cargo to the surviving receiver. */
    @GameTest(timeoutTicks = 2400)
    public static void destroyingSupplierStillDeliversInFlightCargo(GameTestHelper helper) {
        destroyEndpointDuringFlight(helper, 0);
    }

    private static void destroyEndpointDuringFlight(GameTestHelper helper, int removedEndpoint) {
        Fixture fixture = new Fixture();
        String endpoint = removedEndpoint == 0 ? "supplier" : "receiver";
        ClientTest.scenario(helper)
            .server("prepare asteroid outposts and charged BIG HAMMER", () -> {
                fixture.prepare(helper);
                fixture.prepareHammer(true);
            })
            .client("register client cleanup", c -> c.afterTest(fixture::cleanupClient))
            .awaitClient("both endpoints synchronized", c -> {
                fixture.facility(true, 0);
                fixture.facility(true, 1);
            })
            .client("launch cross-asteroid shipment", c -> fixture.configureShipment(true))
            .withinTicks(300)
            .awaitServer("cargo left the supplier", () -> fixture.assertInFlight(false))
            .server("remember the launched flight", fixture::recordFlight)
            .awaitClient("real flight synchronized", c -> fixture.assertInFlight(true))
            .capture("api-flight-before-endpoint-loss")
            .client("destroy " + endpoint + " outpost while cargo is in flight", c -> {
                if (!CelestialClient.destroyAsset(fixture.ids.get(removedEndpoint)))
                    throw new AssertionError("Client could not submit " + endpoint + " destruction");
            })
            .awaitServer(
                endpoint + " removed without cancelling flight",
                () -> fixture.assertEndpointLost(false, removedEndpoint, false))
            .awaitClient(
                "removed " + endpoint + " and continuing flight synchronized",
                c -> fixture.assertEndpointLost(true, removedEndpoint, false))
            .withinTicks(1800)
            .awaitServerAccelerated(
                "cargo resolved when its original flight ends",
                10,
                () -> fixture.assertEndpointLost(false, removedEndpoint, true))
            .withinTicks(150)
            .awaitClient(
                "completed flight and surviving outpost synchronized",
                c -> fixture.assertEndpointLost(true, removedEndpoint, true))
            .succeed();
    }

    /** Integration contract: station sites and asteroid outpost sites retain their supported body identities. */
    @GameTest(timeoutTicks = 1800)
    public static void stationAndAsteroidOutpostSitesOnSeveralBodies(GameTestHelper helper) {
        createAsteroidAssets(helper, false, 3);
    }

    /** Integration contract: communication and prospecting requests retain their kind and asteroid identity. */
    @GameTest(timeoutTicks = 1800)
    public static void satelliteSitesOnSeveralAsteroids(GameTestHelper helper) {
        createAsteroidAssets(helper, true, 3);
    }

    private static void createAsteroidAssets(GameTestHelper helper, boolean satellites, int bodyCount) {
        Fixture fixture = new Fixture();
        ClientScenario scenario = ClientTest.scenario(helper)
            .server("prepare discovered asteroid targets", () -> {
                fixture.prepare(helper);
                fixture.prepareAsteroids(satellites ? 3 : 0, bodyCount);
            })
            .client("register client cleanup", c -> c.afterTest(fixture::cleanupClient));
        for (int bodyIndex = 0; bodyIndex < bodyCount; bodyIndex++) {
            for (int kindIndex = 0; kindIndex < 2; kindIndex++) {
                int body = bodyIndex;
                int kind = kindIndex;
                String step = "asteroid " + body + " kind " + kind + ": ";
                scenario.server(step + "remember existing assets", fixture::rememberExisting)
                    .client(step + "send create request", c -> fixture.requestAsset(body, kind, satellites))
                    .awaitServer(
                        step + "server created requested asset",
                        () -> fixture.findCreated(body, kind, satellites))
                    .awaitClient(
                        step + "created asset synchronized",
                        c -> fixture.assertCreatedClient(body, kind, satellites));
            }
        }
        scenario.succeed();
    }

    private static final class Fixture {

        private final List<CelestialAsset.ID> ids = new ArrayList<>();
        private final List<CelestialObjectKey> asteroids = new ArrayList<>();
        private final List<CelestialObjectKey> stationBodies = new ArrayList<>();
        private final Map<CelestialObjectKey, CelestialKnowledgeFacts> previousFacts = new LinkedHashMap<>();
        private final Map<ItemStackWrapper, Long> materials = new LinkedHashMap<>();
        private final Set<CelestialAsset.ID> beforeRequest = new HashSet<>();
        private final String name = "horizon-gameplay-" + UUID.randomUUID();
        private EntityPlayerMP player;
        private UUID teamId;
        private boolean creativeChanged;
        private boolean originalCreative;
        private CelestialAsset.ID createdId;
        private ModuleInstance.ID hammerId;
        private ModuleInstance.ID cancelledModuleId;
        private ModuleInstance.ID upgradedModuleId;
        private long initialStorageCapacity;
        private ItemStackWrapper cargo;
        private LogisticsDelivery.ID deliveryId;
        private int initialRemainingTicks;
        private long expectedFlightArrivalTick;
        private long firstMissingFlightTick = -1;
        private String launchDiagnostic;

        void showStation(int index) {
            StationManagementScreen.open(ids.get(index), false);
        }

        void prepare(GameTestHelper helper) {
            for (Object candidate : helper.getWorld().playerEntities) {
                if (candidate instanceof EntityPlayerMP actual) {
                    player = actual;
                    break;
                }
            }
            if (player == null) throw new AssertionError("Integrated client player has not joined");
            teamId = GTTeamsCompat.getTeamData(player)
                .orElseThrow()
                .getTeamId();
            helper.afterTest(this::cleanupServer);
        }

        AutomatedFacility addFacility() {
            return addFacility(
                CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                CelestialAsset.Kind.AUTOMATED_STATION);
        }

        AutomatedFacility addFacility(CelestialObjectKey bodyKey, CelestialAsset.Kind kind) {
            AutomatedFacility facility = new AutomatedFacility(
                CelestialAsset.ID.create(),
                bodyKey,
                kind,
                Buildable.Status.OPERATIONAL);
            ids.add(facility.assetId);
            CelestialAssetStore.SERVER.registerAssetInternal(teamId, facility);
            return facility;
        }

        void publish(AutomatedFacility facility) {
            AssetStateSync.SERVER.publishFullTo(player.getUniqueID(), facility.assetId);
        }

        void prepareModuleConstruction() {
            AutomatedFacility facility = addFacility();
            for (FacilityModuleKind kind : List.of(FacilityModuleKind.STORAGE, FacilityModuleKind.TANK)) {
                FacilityModuleRegistry.operationCost(
                    FacilityModuleRegistry.get(kind)
                        .getTierData(kind.defaultTier())
                        .constructionCost())
                    .forEach((resource, amount) -> materials.merge(resource, amount, Long::sum));
            }
            materials.forEach(
                (resource, amount) -> {
                    if (facility.insert(resource, amount) != amount)
                        throw new AssertionError("Fixture material capacity");
                });
            facility.setEnergyStored(facility.energyCapacity());
            publish(facility);
        }

        AutomatedFacility facility(boolean client, int index) {
            CelestialAsset asset = (client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER)
                .findAssetInternal(ids.get(index));
            if (!(asset instanceof AutomatedFacility facility)) throw new AssertionError("Facility not synchronized");
            return facility;
        }

        void assertModules(boolean client, boolean complete) {
            AutomatedFacility facility = facility(client, 0);
            if (facility.modules()
                .size() != 2) throw new AssertionError("Expected storage and tank modules");
            for (FacilityModuleKind kind : List.of(FacilityModuleKind.STORAGE, FacilityModuleKind.TANK)) {
                StationTileCoord tile = StationTileCoord.of(kind == FacilityModuleKind.STORAGE ? 1 : -1, 0);
                ModuleInstance module = facility.stationLayout()
                    .moduleAt(tile);
                if (module == null || module.kind() != kind) throw new AssertionError("Wrong module at " + tile);
                if (complete && !module.isOperational()) throw new AssertionError("Module still building: " + kind);
                if (!complete && module.operationOrNull() == null)
                    throw new AssertionError("Paid construction must start a timed operation");
            }
            if (complete) materials.forEach(
                (resource, amount) -> {
                    if (facility.itemAmount(resource) != 0)
                        throw new AssertionError("Construction did not consume materials");
                });
        }

        void assertCancellationBuildPaid(boolean client) {
            AutomatedFacility facility = facility(client, 0);
            ModuleInstance module = facility.stationLayout()
                .moduleAt(StationTileCoord.of(1, 0));
            if (module == null || facility.modules()
                .size() != 1 || module.kind() != FacilityModuleKind.STORAGE || !module.isInConstruction())
                throw new AssertionError("Expected a live paid storage construction");
            Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(
                FacilityModuleRegistry.get(FacilityModuleKind.STORAGE)
                    .getTierData(FacilityModuleKind.STORAGE.defaultTier())
                    .constructionCost());
            if (cost.isEmpty()) throw new AssertionError("Paid construction requires a nonempty material cost");
            materials.forEach((resource, amount) -> {
                if (facility.itemAmount(resource) != amount - cost.getOrDefault(resource, 0L))
                    throw new AssertionError("Construction materials have not been debited");
            });
            if (!client) cancelledModuleId = module.id;
            else if (!module.id.equals(cancelledModuleId))
                throw new AssertionError("Client construction identity differs from the server");
        }

        void assertConstructionRefunded(boolean client) {
            AutomatedFacility facility = facility(client, 0);
            if (!facility.modules()
                .isEmpty() || facility.moduleById(cancelledModuleId) != null
                || facility.stationLayout()
                    .isOccupied(StationTileCoord.of(1, 0)))
                throw new AssertionError("Cancelled construction must release its module and tile");
            materials.forEach((resource, amount) -> {
                if (facility.itemAmount(resource) != amount)
                    throw new AssertionError("Cancellation must return exactly the deposited construction materials");
            });
        }

        void prepareStorageUpgrade() {
            originalCreative = player.capabilities.isCreativeMode;
            creativeChanged = true;
            player.capabilities.isCreativeMode = false;
            AutomatedFacility facility = addFacility();
            FacilityCommand.Result built = facility.applyCommand(
                new FacilityCommand.BuildModules(
                    facility.assetId,
                    FacilityModuleKind.STORAGE,
                    FacilityModuleKind.STORAGE.defaultShape(),
                    new IModuleComponent.BuildPhysicalSpec.Tier(ModuleTier.HV),
                    null,
                    true,
                    List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
                new FacilityCommand.Authority(true, true));
            if (built.status() != FacilityCommand.Status.CHANGED)
                throw new AssertionError("Could not seed operational storage prerequisite");
            upgradedModuleId = facility.modules()
                .getFirst().id;
            initialStorageCapacity = facility.itemCapacity();
            materials.putAll(
                FacilityModuleRegistry.operationCost(
                    FacilityModuleRegistry.get(FacilityModuleKind.STORAGE)
                        .getTierData(ModuleTier.EV)
                        .constructionCost()));
            if (materials.isEmpty()) throw new AssertionError("Paid upgrade needs a nonempty material cost");
            materials.forEach(
                (resource, amount) -> {
                    if (facility.insert(resource, amount) != amount)
                        throw new AssertionError("Upgrade material capacity");
                });
            publish(facility);
        }

        ModuleInstance upgradeModule(boolean client) {
            AutomatedFacility facility = facility(client, 0);
            ModuleInstance module = facility.stationLayout()
                .moduleAt(StationTileCoord.of(1, 0));
            if (facility.modules()
                .size() != 1 || module == null
                || !module.id.equals(upgradedModuleId)
                || module.kind() != FacilityModuleKind.STORAGE)
                throw new AssertionError("Upgrade must preserve the existing storage identity and tile");
            return module;
        }

        void assertUpgradePaid(boolean client) {
            ModuleInstance module = upgradeModule(client);
            if (module.tier() != ModuleTier.HV || module.operationOrNull() == null)
                throw new AssertionError("Paid upgrade must be accepted before the old tier is replaced");
            AutomatedFacility facility = facility(client, 0);
            materials.forEach(
                (resource, amount) -> {
                    if (facility.itemAmount(resource) != 0)
                        throw new AssertionError("Upgrade materials were not debited");
                });
        }

        void assertUpgradeRefunded(boolean client) {
            assertUpgradeState(client, ModuleTier.HV);
            AutomatedFacility facility = facility(client, 0);
            materials.forEach((resource, amount) -> {
                if (facility.itemAmount(resource) != amount)
                    throw new AssertionError("Cancelled upgrade did not restore exact materials: " + resource);
            });
        }

        void assertUpgradeState(boolean client, ModuleTier expectedTier) {
            ModuleInstance module = upgradeModule(client);
            if (module.tier() != expectedTier || !module.isOperational() || module.operationOrNull() != null)
                throw new AssertionError(
                    "Expected operational storage at tier " + expectedTier
                        + ", actual tier="
                        + module.tier()
                        + ", operational="
                        + module.isOperational()
                        + ", operation present="
                        + (module.operationOrNull() != null));
            long capacity = facility(client, 0).itemCapacity();
            if (expectedTier == ModuleTier.EV ? capacity <= initialStorageCapacity : capacity != initialStorageCapacity)
                throw new AssertionError("Completed storage upgrade must increase usable item capacity");
        }

        void prepareHammer() {
            prepareHammer(false);
        }

        void prepareHammer(boolean crossAsteroid) {
            AutomatedFacility supplier = crossAsteroid
                ? addFacility(generatedAsteroid(9), CelestialAsset.Kind.AUTOMATED_OUTPOST)
                : addFacility();
            AutomatedFacility requester = crossAsteroid
                ? addFacility(generatedAsteroid(10), CelestialAsset.Kind.AUTOMATED_OUTPOST)
                : addFacility();
            prepareHammer(supplier, requester, crossAsteroid ? HammerVariant.BIG : HammerVariant.BASE);
        }

        void prepareHammer(AutomatedFacility supplier, AutomatedFacility requester, HammerVariant variant) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("horizonFixture", name);
            cargo = new ItemStackWrapper(Items.iron_ingot, 0, tag);
            if (supplier.insert(cargo, 96) != 96) throw new AssertionError("Fixture cargo capacity");
            FacilityCommand.Result built = supplier.applyCommand(
                new FacilityCommand.BuildModules(
                    supplier.assetId,
                    FacilityModuleKind.HAMMER,
                    FacilityModuleKind.HAMMER.defaultShape(),
                    new IModuleComponent.BuildPhysicalSpec.Hammer(ModuleTier.LuV, variant),
                    null,
                    true,
                    List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
                new FacilityCommand.Authority(true, true));
            if (built.status() != FacilityCommand.Status.CHANGED) throw new AssertionError("Fixture HAMMER rejected");
            ModuleInstance module = supplier.modules()
                .getFirst();
            hammerId = module.id;
            ModuleHammer hammer = (ModuleHammer) module.component();
            hammer.setEnergyStored(hammer.energyCapacity());
            publish(supplier);
            publish(requester);
        }

        void prepareConstructionShipment() {
            prepareHammer();
            AutomatedFacility supplier = facility(false, 0);
            AutomatedFacility requester = facility(false, 1);
            if (supplier.extract(cargo, 96) != 96) throw new AssertionError("Could not replace fixture cargo");
            materials.putAll(
                FacilityModuleRegistry.operationCost(
                    FacilityModuleRegistry.get(FacilityModuleKind.STORAGE)
                        .getTierData(FacilityModuleKind.STORAGE.defaultTier())
                        .constructionCost()));
            if (materials.isEmpty()) throw new AssertionError("Storage construction needs a nonempty material cost");
            cargo = materials.keySet()
                .iterator()
                .next();
            if (supplier.insert(cargo, 1) != 1) throw new AssertionError("Could not seed the missing ingredient");
            materials.forEach((resource, amount) -> {
                long present = amount - (resource.equals(cargo) ? 1 : 0);
                if (requester.insert(resource, present) != present)
                    throw new AssertionError("Could not seed partial construction materials");
            });
            requester.setEnergyStored(requester.energyCapacity());
            publish(supplier);
            publish(requester);
        }

        void assertConstructionImport(boolean client, boolean complete) {
            AutomatedFacility supplier = facility(client, 0);
            AutomatedFacility requester = facility(client, 1);
            ModuleInstance module = requester.stationLayout()
                .moduleAt(StationTileCoord.of(1, 0));
            if (module == null || requester.modules()
                .size() != 1 || module.kind() != FacilityModuleKind.STORAGE)
                throw new AssertionError("Expected the ordered storage construction at its chosen tile");
            if (!supplier.logisticsConfig.get(cargo)
                .equals(new LogisticsResourceConfig(0, 1, false, true))
                || !requester.logisticsConfig.get(cargo)
                    .equals(new LogisticsResourceConfig(0, 1, complete, false)))
                throw new AssertionError("Construction import must retain a zero standing stock target");
            if (supplier.itemAmount(cargo) != (complete ? 0 : 1))
                throw new AssertionError("Missing ingredient must remain at supplier until import is authorized");
            if (complete ? !module.isOperational() || module.operationOrNull() != null
                : module.isOperational() || module.operationOrNull() == null)
                throw new AssertionError("Construction completion does not match material import authorization");
            materials.forEach((resource, amount) -> {
                if (requester.itemAmount(resource) != 0)
                    throw new AssertionError("Construction must reserve and consume its materials: " + resource);
            });
            if (!client && LogisticStore.inboundAmounts(requester.assetId, cargo)
                .allPending() != 0) throw new AssertionError("Unexpected outstanding material delivery");
        }

        CelestialObjectKey generatedAsteroid(int ordinal) {
            CelestialObjectKey key = CelestialObjectKey.minorBody(
                new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.generatedSlot(ordinal)));
            var body = CelestialRegistry.get(key)
                .orElseThrow(() -> new AssertionError("Generated asteroid is absent: " + key));
            if (!body.properties()
                .canCreateOutpost()) throw new AssertionError("Asteroid cannot host an outpost: " + key);
            return body.key();
        }

        void configureShipment(boolean importEnabled) {
            CelestialClient.configureHammer(
                ids.getFirst(),
                hammerId,
                AllowShootingConfig.ALWAYS,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF);
            CelestialClient.updateLogisticsConfig(
                ids.getFirst(),
                cargo,
                new LogisticsResourceConfig(32, 64, false, true),
                LogisticsConfigAccessMode.FULL);
            CelestialClient.updateLogisticsConfig(
                ids.get(1),
                cargo,
                new LogisticsResourceConfig(64, 64, importEnabled, false),
                LogisticsConfigAccessMode.FULL);
        }

        LogisticsDelivery shipment(boolean client) {
            List<LogisticsDelivery> matches = (client ? CelestialClient.clientDeliveries()
                : LogisticStore.activeDeliveries()).stream()
                    .filter(
                        delivery -> delivery.data.fromAssetId()
                            .equals(ids.getFirst())
                            && delivery.data.toAssetId()
                                .equals(ids.get(1))
                            && delivery.data.resourceId()
                                .equals(cargo))
                    .toList();
            if (matches.size() != 1) {
                if (!client) inspectMissingLaunch();
                throw new AssertionError(
                    "Expected exactly one fixture shipment, got " + matches.size()
                        + ", launch diagnostic="
                        + launchDiagnostic);
            }
            return matches.getFirst();
        }

        void inspectMissingLaunch() {
            if (launchDiagnostic != null) return;
            AutomatedFacility supplier = facility(false, 0);
            AutomatedFacility requester = facility(false, 1);
            if (!supplier.logisticsConfig.get(cargo)
                .isSupplyEnabled()
                || !requester.logisticsConfig.get(cargo)
                    .isImportEnabled())
                return;
            ModuleInstance module = supplier.moduleById(hammerId);
            ModuleHammer hammer = (ModuleHammer) module.component();
            double time = GalaxiaCelestialAPI.currentOrbitalTime();
            var status = HammerDispatchPlanner.inspect(supplier, module, List.of(requester), time);
            launchDiagnostic = "status=" + status
                + ", moduleStatus="
                + module.status()
                + ", variant="
                + hammer.variant()
                + ", canFire="
                + hammer.canFire()
                + ", probeCooldown="
                + hammer.routeProbeCooldownTicks()
                + ", sourceStock="
                + supplier.itemAmount(cargo)
                + ", requestedStock="
                + requester.itemAmount(cargo);
            Galaxia.LOG.info("[Horizon gameplay] Cross-asteroid launch diagnostic: {}", launchDiagnostic);
        }

        void assertInFlight(boolean client) {
            AutomatedFacility supplier = facility(client, 0);
            AutomatedFacility requester = facility(client, 1);
            LogisticsDelivery delivery = shipment(client);
            if (supplier.itemAmount(cargo) != 32 || requester.itemAmount(cargo) != 0 || delivery.data.amount() != 64)
                throw new AssertionError(
                    "Cross-body cargo must remain conserved: 32 at source, 64 in flight, 0 at destination");
            if (delivery.isArrived() || delivery.data.fromBodyKey()
                .equals(delivery.data.toBodyKey())
                || !delivery.data.fromBodyKey()
                    .equals(supplier.celestialObjectKey)
                || !delivery.data.toBodyKey()
                    .equals(requester.celestialObjectKey)
                || delivery.data.transferRoute() == null
                || !delivery.data.transferRoute()
                    .hasTrajectoryGeometry())
                throw new AssertionError("Expected an active trajectory between the two distinct asteroid keys");
            if (client && !delivery.deliveryId.equals(deliveryId))
                throw new AssertionError("Client must observe the server-owned shipment identity");
            ModuleHammer hammer = (ModuleHammer) supplier.moduleById(hammerId)
                .component();
            if (hammer.energyStored() >= hammer.energyCapacity())
                throw new AssertionError("The real launch must spend energy from the charged BIG HAMMER");
            if (!client && LogisticStore.inboundAmounts(requester.assetId, cargo)
                .allPending() != 64)
                throw new AssertionError("The launched cargo must count toward the outstanding request");
        }

        void assertEndpointLost(boolean client, int removedEndpoint, boolean flightComplete) {
            var store = client ? CelestialAssetStore.CLIENT : CelestialAssetStore.SERVER;
            if (store.findAssetInternal(ids.get(removedEndpoint)) != null)
                throw new AssertionError("Destroyed outpost still exists");
            if (removedEndpoint == 1) {
                if (facility(client, 0).itemAmount(cargo) != 32)
                    throw new AssertionError("Lost receiver must not refund in-flight cargo to the supplier");
            } else if (facility(client, 1).itemAmount(cargo) != (flightComplete ? 64 : 0)) {
                throw new AssertionError("Surviving receiver must receive the launched cargo only at arrival");
            }
            var deliveries = client ? CelestialClient.clientDeliveries() : LogisticStore.activeDeliveries();
            LogisticsDelivery flight = deliveries.stream()
                .filter(delivery -> delivery.deliveryId.equals(deliveryId))
                .findFirst()
                .orElse(null);
            if (flightComplete) {
                if (flight != null) throw new AssertionError("Original flight has not ended yet");
                if (!client) {
                    if (firstMissingFlightTick < 0) firstMissingFlightTick = player.worldObj.getTotalWorldTime();
                    if (firstMissingFlightTick < expectedFlightArrivalTick)
                        throw new AssertionError("Endpoint destruction removed the flight before its original arrival");
                }
                if (!client && LogisticStore.inboundAmounts(ids.get(1), cargo)
                    .allPending() != 0) throw new AssertionError("Completed delivery still reserves inbound cargo");
            } else {
                if (flight == null || flight.isArrived()
                    || flight.getRemainingTicks() > initialRemainingTicks
                    || !flight.data.fromAssetId()
                        .equals(ids.get(0))
                    || !flight.data.toAssetId()
                        .equals(ids.get(1))
                    || flight.data.amount() != 64)
                    throw new AssertionError(
                        "Endpoint removal must retain the same source, destination, cargo and active flight");
            }
        }

        void recordFlight() {
            LogisticsDelivery delivery = shipment(false);
            deliveryId = delivery.deliveryId;
            initialRemainingTicks = delivery.getRemainingTicks();
            expectedFlightArrivalTick = player.worldObj.getTotalWorldTime() + initialRemainingTicks;
            Galaxia.LOG.info(
                "[Horizon gameplay] Cross-asteroid launch {} -> {}, flight {} ticks, total dV {}",
                delivery.data.fromBodyKey(),
                delivery.data.toBodyKey(),
                initialRemainingTicks,
                delivery.data.transferRoute()
                    .totalDv());
        }

        void assertFlightAdvanced() {
            LogisticsDelivery delivery = shipment(false);
            if (!delivery.deliveryId.equals(deliveryId) || delivery.getRemainingTicks() >= initialRemainingTicks)
                throw new AssertionError("Ordinary server ticks have not advanced the same shipment");
        }

        void assertImportBlocked(boolean client) {
            AutomatedFacility supplier = facility(client, 0);
            AutomatedFacility requester = facility(client, 1);
            if (!supplier.logisticsConfig.get(cargo)
                .equals(new LogisticsResourceConfig(32, 64, false, true))
                || !requester.logisticsConfig.get(cargo)
                    .equals(new LogisticsResourceConfig(64, 64, false, false)))
                throw new AssertionError(
                    "Expected an exporting supplier and a receiver with a stock target but disabled import");
            if (supplier.itemAmount(cargo) != 96 || requester.itemAmount(cargo) != 0)
                throw new AssertionError("Receiver stock target moved cargo despite disabled import");
            ModuleHammer hammer = (ModuleHammer) supplier.moduleById(hammerId)
                .component();
            if (hammer.energyStored() != hammer.energyCapacity())
                throw new AssertionError("HAMMER spent launch energy for a receiver with disabled import");
            if (!client && LogisticStore.inboundAmounts(requester.assetId, cargo)
                .allPending() != 0) throw new AssertionError("Disabled import created an inbound delivery");
        }

        void assertDelivered(boolean client) {
            AutomatedFacility supplier = facility(client, 0);
            AutomatedFacility requester = facility(client, 1);
            if (supplier.itemAmount(cargo) != 32 || requester.itemAmount(cargo) != 64) throw new AssertionError(
                "Expected conserved cargo: supplier reserve 32, recipient 64, got " + supplier.itemAmount(cargo)
                    + "/"
                    + requester.itemAmount(cargo));
            if (!supplier.logisticsConfig.get(cargo)
                .isSupplyEnabled()
                || !requester.logisticsConfig.get(cargo)
                    .isImportEnabled())
                throw new AssertionError("Client logistics command settings are missing");
            if (!client && LogisticStore.inboundAmounts(requester.assetId, cargo)
                .allPending() != 0) throw new AssertionError("Cargo remains in flight after delivery");
        }

        void prepareAsteroids(int firstOrdinal, int bodyCount) {
            Map<CelestialObjectKey, CelestialKnowledgeFacts> snapshot = CelestialKnowledgeService.snapshot(teamId);
            for (int ordinal = firstOrdinal; ordinal < firstOrdinal + bodyCount; ordinal++) {
                CelestialObjectKey key = CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.generatedSlot(ordinal)));
                var body = CelestialRegistry.get(key)
                    .orElseThrow(
                        () -> new AssertionError("Generated asteroid is absent from the runtime registry: " + key));
                if (!body.properties()
                    .canCreateOutpost()) {
                    throw new AssertionError("Generated asteroid cannot host an outpost: " + key);
                }
                Galaxia.LOG.info("[Horizon gameplay] Resolved asteroid target {} with outpost support", body.key());
                asteroids.add(key);
                previousFacts.put(key, snapshot.get(key));
                CelestialKnowledgeService.putFacts(teamId, key, CelestialKnowledgeFacts.discoveredUnknown());
            }
            for (var body : CelestialRegistry.getAll()) {
                if (body.properties()
                    .canCreateStation()) stationBodies.add(body.key());
                if (stationBodies.size() == 3) break;
            }
            if (stationBodies.size() != 3) throw new AssertionError("Need three registered station targets");
        }

        void rememberExisting() {
            beforeRequest.clear();
            CelestialAssetStore.SERVER.assetsViewInternal()
                .forEach(asset -> beforeRequest.add(asset.assetId));
            createdId = null;
        }

        void requestAsset(int body, int kind, boolean satellite) {
            AssetCreateRequestPacket request = satellite
                ? AssetCreateRequestPacket.createSatellite(asteroids.get(body), SatelliteKind.values()[kind], false)
                : AssetCreateRequestPacket.createFacility(
                    target(body, kind, satellite),
                    name + "-" + body + "-" + kind,
                    kind == 0 ? CelestialAsset.Kind.AUTOMATED_STATION : CelestialAsset.Kind.AUTOMATED_OUTPOST,
                    false);
            Galaxia.GALAXIA_NETWORK.sendToServer(request);
        }

        void findCreated(int body, int kind, boolean satellite) {
            for (CelestialAsset asset : CelestialAssetStore.SERVER
                .getStateInternal(teamId, target(body, kind, satellite))) {
                if (beforeRequest.contains(asset.assetId) || !matches(asset, kind, satellite)) continue;
                if (!satellite && !asset.displayName()
                    .equals(name + "-" + body + "-" + kind)) continue;
                createdId = asset.assetId;
                if (!ids.contains(createdId)) ids.add(createdId);
                assertCreated(asset, body, kind, satellite);
                return;
            }
            throw new AssertionError("Requested asteroid asset has not been created");
        }

        void assertCreatedClient(int body, int kind, boolean satellite) {
            assertCreated(CelestialClient.getByAssetId(createdId), body, kind, satellite);
        }

        CelestialObjectKey target(int body, int kind, boolean satellite) {
            return !satellite && kind == 0 ? stationBodies.get(body) : asteroids.get(body);
        }

        boolean matches(CelestialAsset asset, int kind, boolean satellite) {
            return satellite ? asset instanceof Satellite value && value.satelliteKind() == SatelliteKind.values()[kind]
                : asset.kind
                    == (kind == 0 ? CelestialAsset.Kind.AUTOMATED_STATION : CelestialAsset.Kind.AUTOMATED_OUTPOST);
        }

        void assertCreated(CelestialAsset asset, int body, int kind, boolean satellite) {
            if (asset == null || !matches(asset, kind, satellite)
                || !asset.celestialObjectKey.equals(target(body, kind, satellite))
                || asset.status() != Buildable.Status.CONSTRUCTION_SITE)
                throw new AssertionError("Created asset must retain requested body, kind and construction status");
        }

        void cleanupServer() {
            ids.forEach(AssetStateSync.SERVER::destroyAsset);
            if (!previousFacts.isEmpty()) {
                Map<CelestialObjectKey, CelestialKnowledgeFacts> current = new LinkedHashMap<>(
                    CelestialKnowledgeService.snapshot(teamId));
                previousFacts.forEach((key, previous) -> {
                    if (previous == null) current.remove(key);
                    else current.put(key, previous);
                });
                CelestialKnowledgeService.restore(teamId, current);
            }
            if (creativeChanged) player.capabilities.isCreativeMode = originalCreative;
        }

        void cleanupClient() {
            ids.forEach(CelestialAssetStore.CLIENT::destroyAssetInternal);
        }
    }
}
