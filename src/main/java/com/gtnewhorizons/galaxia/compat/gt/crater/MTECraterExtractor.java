package com.gtnewhorizons.galaxia.compat.gt.crater;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.galaxia.compat.VisualProspectingCompat;
import com.gtnewhorizons.galaxia.gt5.CraterMaterials;
import com.gtnewhorizons.galaxia.gt5.CraterRecipes;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterDeposits;

import cpw.mods.fml.common.Loader;
import gregtech.api.casing.Casings;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.shutdown.ShutDownReason;

/** Native GT operation lifecycle over a shared crater deposit. */
public final class MTECraterExtractor extends MTEExtendedPowerMultiBlockBase<MTECraterExtractor>
    implements ISurvivalConstructable {

    public static final int ID = 23057;
    private static IStructureDefinition<MTECraterExtractor> structure;
    private String crater = "";
    private String pendingOperation = "";

    public MTECraterExtractor() {
        super(ID, "multimachine.crater_extractor", "Crater Extractor");
    }

    private MTECraterExtractor(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTECraterExtractor(mName);
    }

    @Override
    public IStructureDefinition<MTECraterExtractor> getStructureDefinition() {
        if (structure == null) {
            structure = StructureDefinition.<MTECraterExtractor>builder()
                .addShape(
                    "main",
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C-C", "CCC" }, { "HHH", "HHH", "HHH" } }))
                .addElement('C', Casings.SolidSteelMachineCasing.asElement())
                .addElement(
                    'H',
                    buildHatchAdder(MTECraterExtractor.class)
                        .atLeast(Energy, Maintenance, InputHatch, OutputHatch, OutputBus)
                        .casingIndex(Casings.SolidSteelMachineCasing.textureId)
                        .hint(1)
                        .buildAndChain(Casings.SolidSteelMachineCasing.asElement()))
                .build();
        }
        return structure;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece("main", 1, 1, 0, errors)) return;
        checkHasEnergyHatch(errors);
        checkHasMaintenanceHatch(errors);
        checkHasAnyOutput(errors);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        var tile = getBaseMetaTileEntity();
        CraterDeposits deposits = CraterDeposits.get(tile.getWorld());
        if (crater.isEmpty()) {
            String found = deposits.find(tile.getXCoord(), tile.getZCoord());
            if (found == null) return CheckRecipeResultRegistry.NO_RECIPE;
            crater = found;
        }
        var snapshot = deposits.inspect(crater);
        if (snapshot == null) return CheckRecipeResultRegistry.NO_RECIPE;
        if (getMaxInputVoltage() < 480) return CheckRecipeResultRegistry.insufficientPower(480);
        long requiredFluid = snapshot.remainingReserve() == 0 ? snapshot.nextFrackingFluid() : 16000;
        boolean wet = requiredFluid <= Integer.MAX_VALUE
            && depleteInput(new FluidStack(ItemList.sDrillingFluid, (int) requiredFluid), true);
        var operation = deposits.begin(crater, wet, tile.getWorld().rand.nextInt(4) == 0);
        if (operation == null) return CheckRecipeResultRegistry.NO_RECIPE;
        ItemStack[] items = wet ? new ItemStack[0] : CraterRecipes.solids(snapshot.profile());
        FluidStack[] fluids = wet
            ? new FluidStack[] { new FluidStack(CraterMaterials.enriched(snapshot.profile()), 16000) }
            : new FluidStack[0];
        if (!canOutputAll(items, fluids)) {
            deposits.finish(operation.id(), false);
            return wet ? CheckRecipeResultRegistry.FLUID_OUTPUT_FULL : CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        }
        if (wet && !depleteInput(new FluidStack(ItemList.sDrillingFluid, (int) operation.drillingFluid()))) {
            deposits.finish(operation.id(), false);
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        pendingOperation = operation.id();
        var overclock = new OverclockCalculator().setRecipeEUt(480)
            .setEUt(getMaxInputVoltage())
            .setDuration(operation.fracking() ? 200 : 100)
            .calculate();
        lEUt = -overclock.getConsumption();
        mMaxProgresstime = overclock.getDuration();
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        mEfficiencyIncrease = 10000;
        mOutputItems = items;
        mOutputFluids = fluids;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    protected void outputAfterRecipe() {
        finishOperation(true);
    }

    @Override
    public void stopMachine(ShutDownReason reason) {
        finishOperation(false);
        super.stopMachine(reason);
    }

    @Override
    public void onRemoval() {
        finishOperation(false);
        super.onRemoval();
    }

    private void finishOperation(boolean success) {
        if (pendingOperation.isEmpty()) return;
        var tile = getBaseMetaTileEntity();
        var deposits = CraterDeposits.get(tile.getWorld());
        if (deposits.finish(pendingOperation, success) && success && Loader.isModLoaded("visualprospecting")) {
            VisualProspectingCompat.update(tile.getWorld(), deposits.inspect(crater));
        }
        pendingOperation = "";
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setString("crater", crater);
        nbt.setString("craterOperation", pendingOperation);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        crater = nbt.getString("crater");
        pendingOperation = nbt.getString("craterOperation");
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece("main", stack, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment environment) {
        return mMachine ? -1 : survivalBuildPiece("main", stack, 1, 1, 0, budget, environment, false, true);
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder()
            .addMachineType(StatCollector.translateToLocal("galaxia.crater.extractor.machine_type"))
            .addInfo(StatCollector.translateToLocal("galaxia.crater.extractor.shared"))
            .addInfo(StatCollector.translateToLocal("galaxia.crater.extractor.fluid"))
            .beginStructureBlock(3, 3, 3, true)
            .addController(StatCollector.translateToLocal("gt.mbtt.structure.front_center_2nd_layer"))
            .addCasingInfoMin(Casings.SolidSteelMachineCasing.getLocalizedName(), 16, false)
            .addEnergyHatch(StatCollector.translateToLocal("galaxia.crater.extractor.bottom_layer"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("galaxia.crater.extractor.bottom_layer"), 1)
            .addInputHatch(StatCollector.translateToLocal("galaxia.crater.extractor.bottom_layer"), 1)
            .addOutputHatch(StatCollector.translateToLocal("galaxia.crater.extractor.bottom_layer"), 1)
            .addOutputBus(StatCollector.translateToLocal("galaxia.crater.extractor.bottom_layer"), 1)
            .addAir(StatCollector.translateToLocal("gt.mbtt.structure.interior"))
            .toolTipFinisher();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        return new ITexture[] { Casings.SolidSteelMachineCasing.getCasingTexture() };
    }
}
