package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.compat.gt.computer.MTEComputer;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.AssetStateSync;
import com.gtnewhorizons.galaxia.gt5.computer.ComputerContent;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.common.misc.DrillingLogicDelegate;
import gregtech.common.tileentities.machines.basic.MTEMiner;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class ComputerGameplayGameTests {

    private ComputerGameplayGameTests() {}

    /** Product contract: all local miners share the bonus, and ordinary inventory outputs are unaffected. */
    @GameTest(template = "computer_installation", timeoutTicks = 100)
    public static void returnedFlowMultipliesFacilityAndNativeMinerOutputs(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.prepare();
        CelestialAssetStore.SERVER.setSatelliteCount(fixture.team, Fixture.EARTH, SatelliteKind.PROSPECTING, 500);
        var processed = SatelliteDataKey.origin(SatelliteDataType.PROCESSED_PROSPECTING, Fixture.EARTH);
        helper.assertTrue(
            SatelliteNetworkService.offerData(fixture.team, Fixture.EARTH, processed, 500L) == 500L,
            "Fixture could not offer the processed flow");
        SatelliteNetworkService.tickDataJobs();
        helper.assertTrue(
            SatelliteNetworkService.miningYieldBonus(fixture.team, Fixture.EARTH) == 1.0D,
            "Returned flow did not provide the expected guaranteed doubling");

        var facility = new AutomatedFacility(
            new CelestialAsset.ID(new UUID(500L, 600L)),
            Fixture.EARTH,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.registerAsset(fixture.team, facility);
        helper.afterTest(() -> CelestialAssetStore.SERVER.destroyAssetInternal(facility.assetId));
        for (FacilityModuleKind kind : List.of(FacilityModuleKind.STORAGE, FacilityModuleKind.MINER)) {
            IModuleComponent.BuildPhysicalSpec spec = kind == FacilityModuleKind.MINER
                ? new IModuleComponent.BuildPhysicalSpec.Miner(kind.defaultTier(), MinerFocusTier.NONE)
                : new IModuleComponent.BuildPhysicalSpec.Tier(kind.defaultTier());
            ModulePlacement placement = kind == FacilityModuleKind.STORAGE
                ? ModulePlacement.at(StationTileCoord.of(1, 0))
                : plainMiningPlacement(facility);
            var result = facility.applyCommand(
                new FacilityCommand.BuildModules(
                    facility.assetId,
                    kind,
                    kind.defaultShape(),
                    spec,
                    null,
                    true,
                    List.of(placement)),
                new FacilityCommand.Authority(true, true));
            helper.assertTrue(
                result.status() == FacilityCommand.Status.CHANGED,
                "Fixture could not build " + kind + ": " + result.rejection());
        }
        var module = facility.modules()
            .stream()
            .filter(m -> m.kind() == FacilityModuleKind.MINER)
            .findFirst()
            .orElseThrow();
        module.component()
            .runCycle(module, facility);
        helper.assertTrue(
            facility.itemSnapshot()
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum() == 2L,
            "Automated Facility did not receive twice the mineral yield");

        var base = fixture.machine(13, 2, 0, ItemList.Machine_HV_Miner.get(1L), ForgeDirection.NORTH);
        MTEMiner miner = (MTEMiner) base.getMetaTileEntity();
        helper.setBlock(13, 1, 1, Blocks.iron_ore);
        TestPos ore = helper.absolute(13, 1, 1);
        new DrillingLogicDelegate(miner).mineBlock(base, Blocks.iron_ore, ore.x(), ore.y(), ore.z());
        long minerals = 0L;
        for (int slot = 0; slot < base.getSizeInventory(); slot++) {
            ItemStack stack = base.getStackInSlot(slot);
            if (stack != null) minerals += stack.stackSize;
            base.setInventorySlotContents(slot, null);
        }
        helper.assertTrue(minerals == 2L, "Native GT miner did not receive twice the mineral yield: " + minerals);
        helper.assertTrue(miner.pushOutputs(new ItemStack(Items.stick), 1, false, true), "Fixture output was rejected");
        long ordinary = 0L;
        for (int slot = 0; slot < base.getSizeInventory(); slot++) {
            ItemStack stack = base.getStackInSlot(slot);
            if (stack != null) ordinary += stack.stackSize;
        }
        helper.assertTrue(ordinary == 1L, "Prospecting multiplied an ordinary inventory output");
        helper.succeed();
    }

    private static ModulePlacement plainMiningPlacement(AutomatedFacility facility) {
        FacilityModuleKind kind = FacilityModuleKind.MINER;
        for (int x = StationTileCoord.MIN; x <= StationTileCoord.MAX; x++) {
            for (int y = StationTileCoord.MIN; y <= StationTileCoord.MAX; y++) {
                var anchor = StationTileCoord.of(x, y);
                var placement = ModulePlacement.at(anchor);
                if (facility.buildablePlacements(kind, kind.defaultShape(), kind.defaultTier(), List.of(placement))
                    .isEmpty()) continue;
                var candidate = kind.create(anchor, kind.defaultShape(), kind.defaultTier());
                var effects = ModuleMiner.featureMiningEffects(candidate, facility);
                if (effects.bonusRolls() == 0 && effects.outputMultiplierPercent() == 100) return placement;
            }
        }
        throw new AssertionError("Fixture has no ordinary mining placement");
    }

    /**
     * Integration contract: actual mixed racks pay local resources and return origin-bound yield through the network.
     */
    @GameTest(template = "computer_installation", timeoutTicks = 800)
    public static void physicalComputerProcessesAndStopsWithMissingInfrastructure(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        fixture.prepare();
        helper.startSequence()
            .thenWaitUntil("controller and mixed cluster form", 240, () -> {
                helper.assertTrue(fixture.controller.mMachine, "Controller did not form");
                helper.assertTrue(fixture.cluster.mMachine, "Mixed EV/IV/LuV cluster did not form");
            })
            .thenIdle(5)
            .thenExecute("no chilled coolant means no bonus", () -> fixture.assertBonus(false))
            .thenExecute("supply chilled coolant", fixture::fillCoolant)
            .thenWaitUntil("processed data returns to Mars", 100, () -> fixture.assertBonus(true))
            .thenExecute("coolant volume is conserved", fixture::assertCoolant)
            .thenExecute("exhaust all EU", fixture::stopPower)
            .thenIdle(8)
            .thenExecute("power loss stops coolant conversion", fixture::assertStoppedCoolant)
            .thenWaitUntil("previously processed data drains after power loss", 80, () -> fixture.assertBonus(false))
            .thenExecute("restore EU", () -> fixture.powered = true)
            .thenWaitUntil("flow resumes after power returns", 100, () -> fixture.assertBonus(true))
            .thenExecute("recolor the cable", () -> fixture.cable.setColorization((byte) 4))
            .thenIdle(8)
            .thenWaitUntil("mismatched paint disconnects the cluster", 80, () -> fixture.assertBonus(false))
            .thenExecute("restore matching paint", () -> fixture.cable.setColorization((byte) 3))
            .thenWaitUntil("flow resumes over the repaired cable", 100, () -> fixture.assertBonus(true))
            .thenExecute(
                "add a cable branch",
                () -> fixture.machine(5, 3, 1, ComputerContent.cable(), ForgeDirection.NORTH))
            .thenIdle(8)
            .thenWaitUntil("branching does not supply phantom capacity", 80, () -> fixture.assertBonus(false))
            .thenExecute("remove branch", () -> fixture.setAir(5, 3, 1))
            .thenWaitUntil("flow resumes on the point-to-point route", 100, () -> fixture.assertBonus(true))
            .thenExecute(
                "remove chilled coolant",
                () -> fixture.input.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, true))
            .thenIdle(8)
            .thenWaitUntil(
                "coolant loss stops processing after outgoing buffers drain",
                80,
                () -> fixture.assertBonus(false))
            .thenExecute("refill coolant", fixture::fillCoolant)
            .thenWaitUntil("flow resumes after refilling", 100, () -> fixture.assertBonus(true))
            .thenExecute("unload cluster controller while its connector remains loaded", fixture::unloadCluster)
            .thenWaitUntil("unloaded hardware supplies no processing", 80, () -> fixture.assertBonus(false))
            .thenSucceed();
    }

    private static final class Fixture {

        private static final CelestialObjectKey EARTH = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        private static final CelestialObjectKey MARS = CelestialObjectKey.registered(CelestialObjectId.MARS);
        private final GameTestHelper helper;
        private final WorldServer world;
        private UUID owner;
        private final List<TestPos> placed = new ArrayList<>();
        private final List<IGregTechTileEntity> energy = new ArrayList<>();
        private UUID team;
        private MTEComputer controller;
        private MTEComputer cluster;
        private MTEHatchInput input;
        private MTEHatchOutput output;
        private IGregTechTileEntity cable;
        private boolean powered = true;
        private int stoppedNeutral;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            world = helper.getWorld();
        }

        void prepare() {
            for (Object candidate : world.playerEntities) {
                if (candidate instanceof EntityPlayerMP player) {
                    owner = player.getUniqueID();
                    team = GTTeamsCompat.getTeam(owner);
                    break;
                }
            }
            if (team == null) throw new AssertionError("Integrated client player has no team");
            helper.afterTest(this::cleanup);
            shell(1, 3);
            shell(9, 5);
            controller = (MTEComputer) machine(1, 2, 0, ComputerContent.controller(), ForgeDirection.NORTH)
                .getMetaTileEntity();
            cluster = (MTEComputer) machine(9, 2, 0, ComputerContent.cluster(), ForgeDirection.NORTH)
                .getMetaTileEntity();
            machine(2, 2, 1, ComputerContent.port(), ForgeDirection.EAST);
            machine(8, 2, 1, ComputerContent.port(), ForgeDirection.WEST);
            for (int x = 3; x < 8; x++) {
                IGregTechTileEntity segment = machine(x, 2, 1, ComputerContent.cable(), ForgeDirection.NORTH);
                if (x == 5) cable = segment;
            }
            energy.add(machine(0, 1, 1, ItemList.Hatch_Energy_LuV.get(1L), ForgeDirection.WEST));
            energy.add(machine(8, 1, 2, ItemList.Hatch_Energy_LuV.get(1L), ForgeDirection.WEST));
            energy.add(machine(10, 1, 2, ItemList.Hatch_Energy_LuV.get(1L), ForgeDirection.EAST));
            input = (MTEHatchInput) machine(8, 1, 3, ItemList.Hatch_Input_EV.get(1L), ForgeDirection.WEST)
                .getMetaTileEntity();
            output = (MTEHatchOutput) machine(10, 1, 3, ItemList.Hatch_Output_EV.get(1L), ForgeDirection.EAST)
                .getMetaTileEntity();
            for (int tier = 0; tier < 3; tier++) helper.setBlock(9, 2, tier + 1, ComputerContent.hardware, tier);
            for (CelestialObjectKey body : List.of(EARTH, MARS))
                CelestialAssetStore.SERVER.setSatelliteCount(team, body, SatelliteKind.COMMUNICATION, 100);
            CelestialAssetStore.SERVER.setSatelliteCount(team, MARS, SatelliteKind.PROSPECTING, 20);
            SatelliteNetworkService.rebuild(team, 0.0D);
            helper.onEachTick("external EU supply", () -> {
                for (IGregTechTileEntity hatch : energy) {
                    if (powered) hatch.increaseStoredEnergyUnits(
                        Math.max(0L, Math.min(100000L, hatch.getEUCapacity() - hatch.getStoredEU())),
                        false);
                    else hatch.decreaseStoredEnergyUnits(hatch.getStoredEU(), true);
                }
            });
        }

        private void shell(int centerX, int depth) {
            for (int x = centerX - 1; x <= centerX + 1; x++)
                for (int y = 1; y <= 3; y++) for (int z = 0; z < depth; z++) {
                    TestPos pos = TestPos.at(x, y, z);
                    placed.add(pos);
                    helper.setBlock(
                        pos,
                        x == centerX && y == 2 && z > 0 && z < depth - 1 ? Blocks.air : ComputerContent.hardware,
                        x == centerX && y == 2 && z > 0 && z < depth - 1 ? 0 : 3);
                }
        }

        IGregTechTileEntity machine(int x, int y, int z, ItemStack stack, ForgeDirection facing) {
            TestPos pos = TestPos.at(x, y, z);
            placed.add(pos);
            var prototype = GregTechAPI.METATILEENTITIES[stack.getItemDamage()];
            helper.setBlock(pos, GregTechAPI.sBlockMachines, prototype.getTileEntityBaseType());
            var absolute = helper.absolute(pos);
            var tile = (IGregTechTileEntity) world.getTileEntity(absolute.x(), absolute.y(), absolute.z());
            tile.setInitialValuesAsNBT(new NBTTagCompound(), (short) stack.getItemDamage());
            tile.setOwnerUuid(owner);
            tile.setFrontFacing(facing);
            tile.setColorization((byte) 3);
            tile.enableWorking();
            return tile;
        }

        void stopPower() {
            powered = false;
            for (IGregTechTileEntity hatch : energy) hatch.decreaseStoredEnergyUnits(hatch.getStoredEU(), true);
            stoppedNeutral = output.getDrainableStack() == null ? 0 : output.getDrainableStack().amount;
        }

        void assertStoppedCoolant() {
            int neutral = output.getDrainableStack() == null ? 0 : output.getDrainableStack().amount;
            helper.assertTrue(stoppedNeutral == neutral, "Cluster processed data without EU");
        }

        void fillCoolant() {
            input.fill(ForgeDirection.UNKNOWN, ComputerContent.chilled(10000), true);
        }

        void unloadCluster() {
            IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
            ((net.minecraft.tileentity.TileEntity) base).onChunkUnload();
            world.removeTileEntity(base.getXCoord(), base.getYCoord(), base.getZCoord());
            helper.assertTrue(!cluster.mMachine, "Unloaded cluster still advertises a formed structure");
        }

        void assertBonus(boolean active) {
            helper.assertTrue(
                (SatelliteNetworkService.miningYieldBonus(team, MARS) > 0.0D) == active,
                "Unexpected origin bonus. Expected active=" + active);
            helper.assertTrue(
                SatelliteNetworkService.miningYieldBonus(team, EARTH) == 0.0D,
                "Computer applied the returned bonus to its own location");
        }

        void assertCoolant() {
            int cold = input.getFillableStack() == null ? 0 : input.getFillableStack().amount;
            int neutral = output.getDrainableStack() == null ? 0 : output.getDrainableStack().amount;
            helper.assertTrue(neutral > 0 && cold + neutral == 10000, "Binary cooling lost or created fluid");
        }

        void setAir(int x, int y, int z) {
            helper.setBlock(x, y, z, Blocks.air);
        }

        private void cleanup() {
            for (TestPos pos : placed) helper.setBlock(pos, Blocks.air);
            for (CelestialObjectKey body : List.of(EARTH, MARS)) {
                for (SatelliteKind kind : SatelliteKind.values())
                    CelestialAssetStore.SERVER.setSatelliteCount(team, body, kind, 0)
                        .forEach(id -> AssetStateSync.SERVER.publishRemoved(team, id));
            }
        }
    }
}
