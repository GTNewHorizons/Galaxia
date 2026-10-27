package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;
import static com.gtnewhorizons.galaxia.gt5.LunarMaterials.spaceGradeAlloy;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.gt5.CraterMaterials;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;
import com.ruling_0.materiallib.api.MaterialLibAPI;
import com.ruling_0.materiallib.api.Shape;

import codechicken.nei.recipe.GuiCraftingRecipe;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.materials.FluidShapes;
import gregtech.api.enums.materials.Materials;
import gregtech.api.enums.materials.Shapes;
import gregtech.api.enums.materials.TEBlockShapes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.material.GTMaterialProperties;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.recipe.RecipeEnvironmentRequirements;
import gregtech.api.recipe.RecipeMaps;
import gregtech.common.blocks.ItemMachines;
import gregtech.nei.GTNEIDefaultHandler;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech", "materiallib" })
public final class SpaceGradeAlloyGameTests {

    private SpaceGradeAlloyGameTests() {}

    @GameTest(timeoutTicks = 1000)
    public static void scandiumFamilyUsesPhysicalAtmosphereAndDisplaysBothOutcomes(GameTestHelper helper) {
        CastingFixture fixture = new CastingFixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("one Sc input registration selects the authored atmospheric outcome", () -> {
                fixture.build(helper);
                ItemStack dust = MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 4);
                var family = RecipeMaps.blastFurnaceRecipes.findRecipeQuery()
                    .items(dust)
                    .voltage(1920)
                    .find();
                long registrations = RecipeMaps.blastFurnaceRecipes.getAllRecipes()
                    .stream()
                    .filter(
                        recipe -> Arrays.stream(recipe.mInputs)
                            .anyMatch(input -> input.isItemEqual(dust)))
                    .count();
                if (family == null || !family.isRecipeFamily() || registrations != 1 || family.mOutputs.length != 0) {
                    throw new AssertionError("Sc melting is not one family or an ordinary bypass remains");
                }
                if (!RecipeEnvironments.ATMOSPHERE.isSatisfied(fixture.machine())) {
                    throw new AssertionError("The native overworld controller is not atmospheric");
                }
                var outcome = family.selectRecipe(fixture.machine());
                assertScandiumOutcome(outcome, 2, true);
                if (outcome.getLogicalRecipe() != family || outcome.mDuration != 800
                    || outcome.mEUt != 1920
                    || outcome.getMetadataOrDefault(COIL_HEAT, 0) != 3650) {
                    throw new AssertionError("Selected Sc outcome lost its family or processing requirements");
                }
                outcome.mOutputs[0].stackSize = 64;
                assertScandiumOutcome(family.selectRecipe(fixture.machine()), 2, true);
                if (family.isRecipeInputEqual(true, new FluidStack[0], dust) || dust.stackSize != 4) {
                    throw new AssertionError("Unselected family consumed its common input");
                }
                var cooling = RecipeMaps.vacuumFreezerRecipes.findRecipeQuery()
                    .items(MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingotHot, 1))
                    .voltage(1920)
                    .find();
                if (cooling == null || cooling.mOutputs.length != 1
                    || !cooling.mOutputs[0].isItemEqual(MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingot, 1))) {
                    throw new AssertionError("Native Sc ingot cooling disappeared");
                }
            })
            .server("the same family selects four hot ingots on Luna", () -> {
                fixture.moveToMoon(helper);
                var family = RecipeMaps.blastFurnaceRecipes.findRecipeQuery()
                    .items(MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 4))
                    .voltage(1920)
                    .find();
                assertScandiumOutcome(family.selectRecipe(fixture.machine()), 4, false);
            })
            .server("return from Luna after checking Sc conditions", fixture::returnHome)
            .awaitClient(
                "client has returned from Luna",
                client -> {
                    if (Minecraft.getMinecraft().thePlayer.dimension != fixture.home)
                        throw new AssertionError("Client still on Luna");
                })
            .client("NEI exposes both Sc outcomes with their environmental requirements", client -> {
                var handler = new GTNEIDefaultHandler(RecipeMaps.blastFurnaceRecipes.getDefaultRecipeCategory());
                ItemStack dust = MaterialLibAPI.getStack(Materials.Scandium, Shapes.dust, 4);
                long displayed = handler.getCache()
                    .stream()
                    .filter(
                        entry -> entry.mRecipe.getLogicalRecipe()
                            .isRecipeFamily())
                    .filter(entry -> entry.mRecipe.mInputs.length == 1 && entry.mRecipe.mInputs[0].isItemEqual(dust))
                    .count();
                if (displayed != 2) throw new AssertionError("NEI did not display both Sc family outcomes");
                GuiCraftingRecipe
                    .openRecipeGui("item", MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingotHot, 1));
            })
            .awaitClient("Sc recipe page is open", client -> {
                if (!(Minecraft.getMinecraft().currentScreen instanceof GuiCraftingRecipe))
                    throw new AssertionError("Sc NEI page not open");
            })
            .capture("scandium-family-outcomes")
            .succeed();
    }

    private static void assertScandiumOutcome(gregtech.api.util.GTRecipe outcome, int ingots, boolean oxide) {
        if (outcome == null || outcome.mOutputs.length != (oxide ? 2 : 1)
            || !outcome.mOutputs[0].isItemEqual(MaterialLibAPI.getStack(Materials.Scandium, Shapes.ingotHot, 1))
            || outcome.mOutputs[0].stackSize != ingots
            || (oxide && (!outcome.mOutputs[1]
                .isItemEqual(MaterialLibAPI.getStack(CraterMaterials.scandiumOxide, Shapes.dust, 1))
                || outcome.mOutputs[1].stackSize != 5))) {
            throw new AssertionError("Sc family selected the wrong effective outcome");
        }
    }

    /**
     * Product contract: the same native casting operation fails in atmosphere and completes on Luna with its mold
     * intact.
     */
    @GameTest(timeoutTicks = 1000)
    public static void nativeFoilCastingRequiresNoAtmosphere(GameTestHelper helper) {
        CastingFixture fixture = new CastingFixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("an atmospheric solidifier rejects the alloy before consuming inputs", () -> {
                fixture.build(helper);
                if (NO_ATMOSPHERE.isSatisfied(fixture.machine()))
                    throw new AssertionError("Atmospheric test location is not atmospheric");
                fixture.machine()
                    .checkRecipe();
                if (fixture.machine().mMaxProgresstime != 0 || fixture.machine()
                    .getFillableStack().amount != 144 || fixture.mold().stackSize != 1) {
                    throw new AssertionError("Atmospheric casting started or consumed inputs");
                }
            })
            .server("the same casting recipe starts in a native machine on Luna", () -> {
                fixture.moveToMoon(helper);
                if (!NO_ATMOSPHERE.isSatisfied(fixture.machine()))
                    throw new AssertionError("Lunar test location does not satisfy No Atmosphere");
                fixture.machine()
                    .checkRecipe();
                if (fixture.machine().mMaxProgresstime <= 0) throw new AssertionError("Casting did not start on Luna");
            })
            .awaitServer("native GT completes four foils and retains the casting mold", fixture::assertCompleted)
            .server("return from Luna after casting", fixture::returnHome)
            .awaitClient("client has returned from Luna", client -> {
                if (Minecraft.getMinecraft().thePlayer.dimension != fixture.home) {
                    throw new AssertionError("Client has not returned from Luna");
                }
            })
            .succeed();
    }

    /**
     * Integration contract: canonical material forms and native catalyst consumption preserve the accepted foil
     * balance.
     */
    @GameTest(timeoutTicks = 300)
    public static void canonicalFormsAndNonconsumedFoilMold(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .server("all required alloy forms share the canonical ore identities", () -> {
                for (Shape shape : List
                    .of(Shapes.ingot, Shapes.plate, Shapes.stick, Shapes.foil, TEBlockShapes.frameGt)) {
                    ItemStack stack = MaterialLibAPI.getStack(spaceGradeAlloy, shape, 1);
                    String oreName = shape.getName() + "SpaceGradeAlloy";
                    // Forge's ArrayList wrapper does not expose its entries through the Java 8 spliterator.
                    if (java.util.Arrays.stream(OreDictionary.getOreIDs(stack))
                        .noneMatch(id -> oreName.equals(OreDictionary.getOreName(id)))) {
                        throw new AssertionError("Missing canonical alloy form " + oreName);
                    }
                }
                var environments = spaceGradeAlloy.getProperty(GTMaterialProperties.MOLTEN_PROCESSING_ENVIRONMENTS);
                if (!environments.equals(RecipeEnvironmentRequirements.of(NO_ATMOSPHERE))) {
                    throw new AssertionError("Alloy molten processing does not require No Atmosphere");
                }
                for (var map : List.of(RecipeMaps.benderRecipes, RecipeMaps.extruderRecipes)) {
                    if (map.getAllRecipes()
                        .stream()
                        .anyMatch(recipe -> {
                            for (ItemStack input : recipe.mInputs) {
                                var material = MaterialLibAPI.lookupStack(input);
                                if (material != null && material.material() == spaceGradeAlloy) return true;
                            }
                            return false;
                        })) throw new AssertionError("An unrestricted direct forming route bypasses alloy casting");
                }
            })
            .server("manual crafting cannot bypass alloy casting", () -> {
                for (var recipe : CraftingManager.getInstance()
                    .getRecipeList()) {
                    var output = MaterialLibAPI.lookupStack(recipe.getRecipeOutput());
                    if (output != null && output.material() == spaceGradeAlloy) {
                        throw new AssertionError("Manual crafting bypasses alloy casting");
                    }
                }
            })
            .server("the three native EBF recipes retain their accepted heat, inputs and molten yields", () -> {
                assertAlloyFeed(10, Materials.Iron, Shapes.ingot, 1, Materials.Silicon, Shapes.dust, 288, 800, 3600);
                assertAlloyFeed(5, Materials.Gold, Shapes.ingot, 2, Materials.Cobalt, Shapes.ingot, 576, 1600, 4500);
                assertAlloyFeed(
                    10,
                    Materials.Aluminium,
                    Shapes.ingot,
                    1,
                    Materials.Scandium,
                    Shapes.ingot,
                    1584,
                    4400,
                    5400);
            })
            .server("native recipe matching consumes molten alloy and preserves the thin-strip mold", () -> {
                ItemStack mold = new ItemStack(GalaxiaItemList.THIN_STRIP_MOLD.getItem());
                FluidStack molten = MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, 144);
                var recipe = RecipeMaps.fluidSolidifierRecipes.findRecipeQuery()
                    .items(mold)
                    .fluids(molten)
                    .voltage(120)
                    .find();
                ItemStack expected = MaterialLibAPI.getStack(spaceGradeAlloy, Shapes.foil, 4);
                if (recipe == null || recipe.mOutputs.length != 1
                    || !recipe.mOutputs[0].isItemEqual(expected)
                    || recipe.mOutputs[0].stackSize != expected.stackSize) {
                    throw new AssertionError("Native solidifier lookup did not select the four-foil casting recipe");
                }
                if (!recipe.isRecipeInputEqual(true, false, new FluidStack[] { molten }, mold)) {
                    throw new AssertionError("Native GT refused the foil casting inputs");
                }
                if (molten.amount != 0 || mold.stackSize != 1) {
                    throw new AssertionError(
                        "Foil casting did not consume exactly the molten alloy while retaining its mold");
                }
            })
            .succeed();
    }

    private static void assertAlloyFeed(int firstCount, com.ruling_0.materiallib.api.Material first, Shape firstShape,
        int secondCount, com.ruling_0.materiallib.api.Material second, Shape secondShape, int volume, int duration,
        int heat) {
        var recipe = RecipeMaps.blastFurnaceRecipes.findRecipeQuery()
            .items(
                MaterialLibAPI.getStack(first, firstShape, firstCount),
                MaterialLibAPI.getStack(second, secondShape, secondCount))
            .voltage(480)
            .find();
        FluidStack expected = MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, volume);
        if (recipe == null || recipe.mFluidOutputs.length != 1
            || !recipe.mFluidOutputs[0].isFluidEqual(expected)
            || recipe.mFluidOutputs[0].amount != volume
            || recipe.mEUt != 480
            || recipe.mDuration != duration
            || recipe.getMetadataOrDefault(COIL_HEAT, 0) != heat
            || !recipe.getEnvironmentRequirements()
                .equals(RecipeEnvironmentRequirements.of(NO_ATMOSPHERE))) {
            throw new AssertionError("Native EBF alloy recipe does not match the accepted feed for " + first);
        }
    }

    private static final class CastingFixture {

        private EntityPlayerMP player;
        private World world;
        private int x, z;
        private final int y = 200;
        private int home;
        private double oldX, oldY, oldZ;

        void build(GameTestHelper helper) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            home = player.dimension;
            oldX = player.posX;
            oldY = player.posY;
            oldZ = player.posZ;
            WorldSettings.GameType gameType = player.theItemInWorldManager.getGameType();
            player.setGameType(WorldSettings.GameType.CREATIVE);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            helper.afterTest(() -> {
                if (world != null) world.setBlockToAir(x, y, z);
                returnHome();
                player.setGameType(gameType);
            });
            world = helper.getWorld();
            x = (((int) player.posX + 64) >> 4 << 4) + 8;
            z = ((int) player.posZ >> 4 << 4) + 8;
            player.setPositionAndUpdate(x + 0.5, y + 2, z - 3.5);
            place();
        }

        void moveToMoon(GameTestHelper helper) {
            world.setBlockToAir(x, y, z);
            int moon = DimensionEnum.MOON.getId();
            OrbitTestSupport.moveTo(player, moon, x + 0.5, y + 2, z - 3.5);
            world = MinecraftServer.getServer()
                .worldServerForDimension(moon);
            place();
        }

        void returnHome() {
            OrbitTestSupport.moveTo(player, home, oldX, oldY, oldZ);
        }

        void place() {
            ItemStack stack = ItemList.Machine_MV_FluidSolidifier.get(1);
            world.setBlockToAir(x, y, z);
            ((ItemMachines) stack.getItem()).placeBlockAt(
                stack,
                player,
                world,
                x,
                y,
                z,
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                stack.getItemDamage());
            tile().disableWorking();
            tile().setInventorySlotContents(
                machine().getInputSlot(),
                new ItemStack(GalaxiaItemList.THIN_STRIP_MOLD.getItem()));
            machine().setFillableStack(MaterialLibAPI.getFluidStack(spaceGradeAlloy, FluidShapes.fluidMolten, 144));
            charge();
        }

        void charge() {
            var tile = tile();
            tile.increaseStoredEnergyUnits(tile.getEUCapacity() - tile.getStoredEU(), true);
        }

        void assertCompleted() {
            charge();
            ItemStack output = tile().getStackInSlot(machine().getOutputSlot());
            ItemStack expected = MaterialLibAPI.getStack(spaceGradeAlloy, Shapes.foil, 4);
            if (output == null || !output.isItemEqual(expected) || output.stackSize != expected.stackSize) {
                throw new AssertionError("Native lunar casting has not produced four foils");
            }
            if (mold() == null || mold().stackSize != 1) throw new AssertionError("Native casting consumed the mold");
        }

        ItemStack mold() {
            return tile().getStackInSlot(machine().getInputSlot());
        }

        MTEBasicMachine machine() {
            return (MTEBasicMachine) tile().getMetaTileEntity();
        }

        IGregTechTileEntity tile() {
            return (IGregTechTileEntity) world.getTileEntity(x, y, z);
        }
    }
}
