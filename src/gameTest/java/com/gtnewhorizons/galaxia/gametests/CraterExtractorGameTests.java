package com.gtnewhorizons.galaxia.gametests;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.compat.gt.crater.MTECraterExtractor;
import com.gtnewhorizons.galaxia.gt5.LunarMaterials;
import com.gtnewhorizons.galaxia.gt5.LunarRecipes;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterDeposits;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterProfile;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterSize;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.VoidingMode;
import gregtech.api.enums.materials.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.material.MaterialUtils;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class CraterExtractorGameTests {

    private CraterExtractorGameTests() {}

    /** Integration contract: dry extraction emits the selected minerals and can consume the last finite portion. */
    @GameTest(timeoutTicks = 800)
    public static void nativeDryExtractionStopsAtDepletion(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("build the extractor without drilling fluid", () -> {
                fixture.build(helper);
                if (!fixture.machine()
                    .checkProcessing()
                    .wasSuccessful()) throw new AssertionError("Dry extraction did not start");
            })
            .awaitServer("native GT emits the minerals from the final portion", () -> {
                fixture.charge();
                var bus = fixture.tile(1, -1, 0);
                for (ItemStack expected : LunarRecipes.solids(CraterProfile.IRON_SILICA)) {
                    int amount = 0;
                    for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                        ItemStack actual = bus.getStackInSlot(slot);
                        if (actual != null && actual.isItemEqual(expected)) amount += actual.stackSize;
                    }
                    if (amount != expected.stackSize) throw new AssertionError("Incorrect dry mineral output");
                }
                fixture.assertReserve(0, 0);
            })
            .server("a depleted crater cannot be mined dry", () -> {
                if (fixture.machine()
                    .checkProcessing()
                    .wasSuccessful()) throw new AssertionError("Dry extraction continued after depletion");
                fixture.assertReserve(0, 0);
            })
            .succeed();
    }

    /** Integration contract: native GT completion and failure settle the shared deposit exactly once. */
    @GameTest(timeoutTicks = 1600)
    public static void nativeExtractionFailureReloadAndFracking(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("build the steel extractor with standard hatches", () -> fixture.build(helper))
            .server("start wet extraction", fixture::startWet)
            .awaitServer("native GT tick advances the operation", fixture::assertRunning)
            .awaitServer("native power loss aborts without depleting the crater", () -> {
                if (fixture.machine().mMaxProgresstime != 0) throw new AssertionError("The hatch still supplies power");
                if (fixture.tile(0, 0, 0)
                    .getLastShutDownReason() != ShutDownReasonRegistry.POWER_LOSS) {
                    throw new AssertionError("The native machine did not report power loss");
                }
                fixture.assertReserve(1, 0);
                fixture.assertOutput(0);
            })
            .server("restart and round-trip the active machine and deposit saves", () -> {
                fixture.startWet();
                NBTTagCompound machine = new NBTTagCompound();
                fixture.machine()
                    .saveNBTData(machine);
                NBTTagCompound deposit = new NBTTagCompound();
                fixture.deposits()
                    .writeToNBT(deposit);
                fixture.deposits()
                    .readFromNBT(deposit);
                fixture.machine()
                    .loadNBTData(machine);
            })
            .awaitServer("completion outputs one portion and depletes the finite reserve", () -> {
                fixture.assertOutput(16000);
                fixture.assertReserve(0, 0);
            })
            .server("start fracking the depleted crater", fixture::startWet)
            .awaitServer("fracking completes with one shared history increment", () -> {
                fixture.assertOutput(32000);
                fixture.assertReserve(0, 1);
            })
            .server("the next fracking portion needs the increased fluid price", () -> {
                fixture.input().mFluid = new FluidStack(ItemList.sDrillingFluid, 16000);
                if (fixture.machine()
                    .checkProcessing()
                    .wasSuccessful()) {
                    throw new AssertionError("Fracking accepted less than the current shared price");
                }
                if (fixture.input().mFluid.amount != 16000) throw new AssertionError("Failed admission consumed fluid");
                fixture.assertReserve(0, 1);
            })
            .server("blocked outputs preserve input and shared history", () -> {
                int price = (int) fixture.deposits()
                    .inspect(fixture.crater)
                    .nextFrackingFluid();
                fixture.machine()
                    .setVoidingMode(VoidingMode.VOID_NONE);
                fixture.output().mFluid = MaterialUtils.fluid(Materials.Water, 32000);
                fixture.input().mFluid = new FluidStack(ItemList.sDrillingFluid, price);
                if (fixture.machine()
                    .checkProcessing() != CheckRecipeResultRegistry.FLUID_OUTPUT_FULL) {
                    throw new AssertionError("Extractor did not protect its blocked output");
                }
                if (fixture.input().mFluid.amount != price) throw new AssertionError("Blocked output consumed input");
                fixture.assertReserve(0, 1);
            })
            .server("removing an active extractor releases its pending operation", () -> {
                fixture.output().mFluid = null;
                if (!fixture.machine()
                    .checkProcessing()
                    .wasSuccessful()) throw new AssertionError("Fracking did not restart");
                NBTTagCompound state = new NBTTagCompound();
                fixture.machine()
                    .saveNBTData(state);
                String operation = state.getString("craterOperation");
                if (fixture.deposits()
                    .operation(operation) == null) throw new AssertionError("No pending operation");
                fixture.world.setBlockToAir(fixture.x, fixture.y, fixture.z);
                if (fixture.deposits()
                    .operation(operation) != null) throw new AssertionError("Removal leaked a reservation");
                fixture.assertReserve(0, 1);
            })
            .succeed();
    }

    private static final class Fixture {

        private World world;
        private EntityPlayerMP player;
        private int x, z;
        private final int y = 120;
        private String crater;

        void build(GameTestHelper helper) {
            world = helper.getWorld();
            player = (EntityPlayerMP) world.playerEntities.get(0);
            x = (((int) player.posX + 64) >> 4 << 4) + 8;
            z = ((int) player.posZ >> 4 << 4) + 8;
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            player.setPositionAndUpdate(x + 0.5, y + 3, z - 5.5);
            helper.afterTest(() -> {
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = 0; dz <= 2; dz++) {
                    world.setBlockToAir(x + dx, y + dy, z + dz);
                }
            });
            place(0, 0, 0, new ItemStack(GregTechAPI.sBlockMachines, 1, MTECraterExtractor.ID));
            tile(0, 0, 0).setFrontFacing(ForgeDirection.NORTH);
            tile(0, 0, 0).disableWorking();
            machine().construct(new ItemStack(Blocks.dirt), false);
            place(-1, -1, 0, ItemList.Hatch_Input_HV.get(1));
            place(0, -1, 0, ItemList.Hatch_Output_HV.get(1));
            place(1, -1, 0, ItemList.Hatch_Output_Bus_HV.get(1));
            place(-1, -1, 1, ItemList.Hatch_Maintenance.get(1));
            place(0, -1, 1, ItemList.Hatch_Energy_HV.get(1));
            machine().mWrench = machine().mScrewdriver = machine().mSoftMallet = machine().mHardHammer = machine().mSolderingTool = machine().mCrowbar = true;
            machine().mStartUpCheck = 0;
            if (!machine().checkStructure(true, tile(0, 0, 0)))
                throw new AssertionError("Extractor structure did not form");
            var energy = tile(0, -1, 1);
            energy.increaseStoredEnergyUnits(energy.getEUCapacity(), true);
            crater = "qa:" + x + ":" + z;
            deposits().register(crater, x, z, 16, CraterSize.SMALL, CraterProfile.IRON_SILICA, 1);
        }

        void startWet() {
            input().mFluid = new FluidStack(ItemList.sDrillingFluid, 16000);
            if (!machine().checkProcessing()
                .wasSuccessful()) throw new AssertionError("Wet extraction did not start");
            if (input().mFluid != null && input().mFluid.amount != 0)
                throw new AssertionError("Drilling fluid not consumed");
        }

        void assertRunning() {
            if (machine().mProgresstime <= 0) throw new AssertionError("Extractor has not advanced a native GT tick");
            assertReserve(1, 0);
        }

        void assertReserve(long remaining, long fracking) {
            var state = deposits().inspect(crater);
            if (state.remainingReserve() != remaining || state.completedFracking() != fracking) {
                throw new AssertionError("Incorrect shared crater accounting: " + state);
            }
        }

        void assertOutput(int amount) {
            if (amount > 0) charge();
            var output = output();
            FluidStack fluid = output.getFluid();
            if (amount == 0 && fluid == null) return;
            if (fluid == null || fluid.amount != amount
                || fluid.getFluid() != LunarMaterials.enriched(CraterProfile.IRON_SILICA)) {
                throw new AssertionError(
                    "Incorrect extraction output: " + fluid
                        + ", progress="
                        + machine().mProgresstime
                        + "/"
                        + machine().mMaxProgresstime
                        + ", formed="
                        + machine().mMachine
                        + ", efficiency="
                        + machine().mEfficiency
                        + ", energy="
                        + tile(0, -1, 1).getStoredEU()
                        + ", shutdown="
                        + tile(0, 0, 0).getLastShutDownReason()
                        + ", crater="
                        + deposits().inspect(crater));
            }
        }

        CraterDeposits deposits() {
            return CraterDeposits.get(world);
        }

        void charge() {
            var energy = tile(0, -1, 1);
            energy.increaseStoredEnergyUnits(energy.getEUCapacity() - energy.getStoredEU(), true);
        }

        MTECraterExtractor machine() {
            return (MTECraterExtractor) tile(0, 0, 0).getMetaTileEntity();
        }

        MTEHatchInput input() {
            return (MTEHatchInput) tile(-1, -1, 0).getMetaTileEntity();
        }

        MTEHatchOutput output() {
            return (MTEHatchOutput) tile(0, -1, 0).getMetaTileEntity();
        }

        IGregTechTileEntity tile(int dx, int dy, int dz) {
            return (IGregTechTileEntity) world.getTileEntity(x + dx, y + dy, z + dz);
        }

        void place(int dx, int dy, int dz, ItemStack stack) {
            world.setBlockToAir(x + dx, y + dy, z + dz);
            ((ItemMachines) stack.getItem()).placeBlockAt(
                stack,
                player,
                world,
                x + dx,
                y + dy,
                z + dz,
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                stack.getItemDamage());
        }
    }
}
