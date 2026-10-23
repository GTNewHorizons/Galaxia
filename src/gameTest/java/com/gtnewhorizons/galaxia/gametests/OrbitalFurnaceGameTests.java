package com.gtnewhorizons.galaxia.gametests;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.gt5.OrbitalItems;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.GTMod;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.pollution.Pollution;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = "gregtech")
public final class OrbitalFurnaceGameTests {

    private OrbitalFurnaceGameTests() {}

    /** Product contract: a formed, powered orbital EBF finishes real SiGe and fluoride recipes without pollution. */
    @GameTest(timeoutTicks = 8000)
    public static void orbitalRecipesCompleteThroughNativeFurnace(GameTestHelper helper) {
        Furnace f = new Furnace();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(3500)
            .server("build and service a complete orbital blast furnace", () -> f.build(helper))
            .server("supply seed and molten SiGe through native inputs", () -> {
                f.tile(-1, 0, 0)
                    .setInventorySlotContents(0, OrbitalItems.SIGE_SEED.get(1));
                ((MTEHatchInput) f.tile(-1, 0, 2)
                    .getMetaTileEntity()).setFillableStack(OrbitalItems.Melt.SIGE.get(1008));
                f.tile(0, 0, 0)
                    .enableWorking();
            })
            .awaitServer("native energy and ticks produce the monocrystal in the output bus", () -> {
                f.charge();
                ItemStack output = f.tile(1, 0, 0)
                    .getStackInSlot(0);
                if (output == null || !output.isItemEqual(OrbitalItems.SIGE_MONOCRYSTAL.get(1))
                    || output.stackSize != 1)
                    throw new AssertionError(
                        "Waiting for native SiGe growth: " + f.machine()
                            .getCheckRecipeResult());
                if (f.tile(-1, 0, 0)
                    .getStackInSlot(0) != null
                    || ((MTEHatchInput) f.tile(-1, 0, 2)
                        .getMetaTileEntity()).getFillableStack() != null)
                    throw new AssertionError("Native growth did not consume the supplied seed and melt");
                f.tile(1, 0, 0)
                    .setInventorySlotContents(0, null);
            })
            .server(
                "supply two fluoride glass batches",
                () -> f.tile(-1, 0, 0)
                    .setInventorySlotContents(0, OrbitalItems.FLUORIDE_GLASS_MIX.get(2)))
            .awaitServer("native fluid output contains both completed glass batches", () -> {
                f.charge();
                var output = ((MTEHatchOutput) f.tile(1, 0, 2)
                    .getMetaTileEntity()).getFluid();
                if (output == null || !output.isFluidEqual(OrbitalItems.Melt.FLUORIDE_GLASS.get(288))
                    || output.amount != 288)
                    throw new AssertionError(
                        "Waiting for native glassmaking: " + f.machine()
                            .getCheckRecipeResult());
                if (f.tile(-1, 0, 0)
                    .getStackInSlot(0) != null) throw new AssertionError("Glassmaking left unconsumed feedstock");
                if (Pollution.getPollution(f.tile(0, 0, 0)) != f.pollution)
                    throw new AssertionError("Orbital manufacturing accumulated chunk pollution");
            })
            .succeed();
    }

    private static final class Furnace {

        private World world;
        private EntityPlayerMP player;
        private int x, z, pollution;
        private final int y = 160;
        private ForgeChunkManager.Ticket ticket;

        private void build(GameTestHelper helper) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            int home = player.dimension;
            double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
            WorldSettings.GameType mode = player.theItemInWorldManager.getGameType();
            boolean oldFlight = player.capabilities.isFlying, oldAllowFlight = player.capabilities.allowFlying;
            boolean oldPollution = GTMod.proxy.mPollution;
            x = (((int) player.posX + 80) >> 4 << 4) + 8;
            z = (((int) player.posZ + 80) >> 4 << 4) + 8;
            helper.afterTest(() -> {
                if (world != null) {
                    for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 3; dy++)
                        for (int dz = 0; dz <= 2; dz++) world.setBlockToAir(x + dx, y + dy, z + dz);
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -5; dz <= -3; dz++) world.setBlockToAir(x + dx, y, z + dz);
                }
                OrbitTestSupport.moveTo(player, home, oldX, oldY, oldZ);
                player.setGameType(mode);
                player.capabilities.isFlying = oldFlight;
                player.capabilities.allowFlying = oldAllowFlight;
                player.sendPlayerAbilities();
                GTMod.proxy.mPollution = oldPollution;
                if (ticket != null) ForgeChunkManager.releaseTicket(ticket);
            });
            player.setGameType(WorldSettings.GameType.CREATIVE);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            OrbitTestSupport.moveTo(player, OrbitTestSupport.overworldOrbit(), x, y + 2, z - 4);
            world = MinecraftServer.getServer()
                .worldServerForDimension(OrbitTestSupport.overworldOrbit());
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -5; dz <= -3; dz++) world.setBlock(x + dx, y, z + dz, Blocks.bedrock);
            player.capabilities.isFlying = false;
            player.motionX = player.motionY = player.motionZ = 0;
            player.playerNetServerHandler.setPlayerLocation(x + 0.5, y + 1, z - 3.5, 0, 0);
            player.sendPlayerAbilities();
            ticket = ForgeChunkManager.requestTicket(Galaxia.instance, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) throw new AssertionError("Could not keep the native furnace chunk loaded");
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(x >> 4, z >> 4));
            GTMod.proxy.mPollution = true;
            place(0, 0, 0, ItemList.Machine_Multi_BlastFurnace.get(1));
            tile(0, 0, 0).disableWorking();
            machine().construct(new ItemStack(Blocks.stone, 1), false);
            place(-1, 0, 0, ItemList.Hatch_Input_Bus_HV.get(1));
            place(1, 0, 0, ItemList.Hatch_Output_Bus_HV.get(1));
            place(-1, 0, 1, ItemList.Hatch_Energy_EV.get(1));
            place(1, 0, 1, ItemList.Hatch_Maintenance.get(1));
            place(-1, 0, 2, ItemList.Hatch_Input_HV.get(1));
            place(1, 0, 2, ItemList.Hatch_Output_HV.get(1));
            place(-1, 3, 1, ItemList.Hatch_Output_HV.get(1));
            place(0, 3, 1, ItemList.Hatch_Muffler_LV.get(1));
            tile(0, 3, 1).setFrontFacing(ForgeDirection.UP);
            machine().mWrench = machine().mScrewdriver = machine().mSoftMallet = machine().mHardHammer = machine().mSolderingTool = machine().mCrowbar = true;
            if (!machine().checkStructure(true, tile(0, 0, 0)))
                throw new AssertionError("Native EBF structure did not form");
            charge();
            pollution = Pollution.getPollution(tile(0, 0, 0));
        }

        private void place(int dx, int dy, int dz, ItemStack stack) {
            world.setBlockToAir(x + dx, y + dy, z + dz);
            if (!((ItemMachines) stack.getItem()).placeBlockAt(
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
                stack.getItemDamage())) throw new AssertionError("Native machine placement failed");
            tile(dx, dy, dz).setFrontFacing(ForgeDirection.NORTH);
        }

        private void charge() {
            if (player.isDead || player.getHealth() <= 0 || player.posY < y - 1)
                throw new IllegalStateException("The furnace test player did not remain alive on the platform");
            var energy = tile(-1, 0, 1);
            energy.increaseStoredEnergyUnits(energy.getEUCapacity() - energy.getStoredEU(), true);
        }

        private IGregTechTileEntity tile(int dx, int dy, int dz) {
            return (IGregTechTileEntity) world.getTileEntity(x + dx, y + dy, z + dz);
        }

        private MTEElectricBlastFurnace machine() {
            return (MTEElectricBlastFurnace) tile(0, 0, 0).getMetaTileEntity();
        }
    }
}
