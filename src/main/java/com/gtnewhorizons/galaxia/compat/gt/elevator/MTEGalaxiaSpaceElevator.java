package com.gtnewhorizons.galaxia.compat.gt.elevator;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTUtility.validMTEList;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.TileElevatorCable;
import com.gtnewhorizons.galaxia.registry.block.special.TileElevatorDock;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionTeleport;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.CycleButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.SoundResource;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;
import gtnhintergalactic.gui.IG_UITextures;
import gtnhintergalactic.tile.multi.elevator.ElevatorUtil;
import tectech.thing.gui.TecTechUITextures;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

/**
 * Space elevator that serves the station above it. Taken from the GTNH Intergalactic elevator without its project
 * modules and without Galacticraft, it uses the same casings, motors and cable.
 * <p>
 * Items and fluids put into its input buses and hatches climb the cable to the elevator dock above it, in the orbit of
 * the same planet and the same chunk column. Players ride up to that dock. From motor tier
 * {@value #TRAVEL_ANYWHERE_TIER} the elevator also carries players to any Galaxia dimension
 */
public class MTEGalaxiaSpaceElevator extends TTMultiblockBase implements ISurvivalConstructable {

    public static final int ID = 23052;
    /** Motor tier from which players can travel to any dimension */
    public static final int TRAVEL_ANYWHERE_TIER = 3;

    /** Index of the Space Elevator base casing */
    private static final int CASING_INDEX_BASE = 32 * 128;
    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int STRUCTURE_PIECE_MAIN_VERT_OFFSET = 39;
    private static final int STRUCTURE_PIECE_MAIN_HOR_OFFSET = 17;
    private static final int STRUCTURE_PIECE_MAIN_DEPTH_OFFSET = 14;
    /** Chunks around the cable's chunk the dock above may be in */
    public static final int DOCK_CHUNK_REACH = 1;
    /** Ticks between two deliveries to the dock */
    private static final int TRANSFER_INTERVAL = 20;
    /** Items and litres one motor tier carries per delivery */
    private static final int ITEMS_PER_TIER = 64;
    private static final int FLUID_PER_TIER = 16_000;

    /** Motor tier of the elevator */
    protected int motorTier = 0;
    /** Index into {@link DimensionTeleport#destinations()} for travel from tier {@value #TRAVEL_ANYWHERE_TIER} */
    private int travelTarget = 0;
    /** Cable of the elevator, shown while the structure is complete */
    private TileElevatorCable cable;
    /** Dock found above at the last delivery, null when there is none */
    private TileElevatorDock dock;
    private String dockStatus = "";

    /**
     * Structure definition of this machine. Built on first use, the casings it is made of come into being after this
     * machine is registered
     */
    private static final class Structure {

        // spotless:off
        private static final IStructureDefinition<MTEGalaxiaSpaceElevator> DEFINITION =
        StructureDefinition.<MTEGalaxiaSpaceElevator>builder()
            .addShape(STRUCTURE_PIECE_MAIN, new String[][] {
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "               FF FF               ", "               AAAAA               " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "               D   D               ", "            FFFFF FFFFF            ", "            AAAAAAAAAAA            " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "            DDDDE EDDDD            ", "          FFFFFFF FFFFFFF          ", "          AAAAAAAAAAAAAAA          " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "          DD   DE ED   DD          ", "         FFFFFFD   DFFFFFF         ", "        AAAAAAAAAAAAAAAAAAA        " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               D   D               ", "         FFF           FFF         ", "       AAAAAAAAAAAAAAAAAAAAA       " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "      AAAAAAAAAAAAAAAAAAAAAAA      " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "                                   ", "     AAAAAAAAAAAAAAAAAAAAAAAAA     " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "              HDE EDH              ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "    AAAAAAAAAAAAAAAAAAAAAAAAAAA    " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "            HH DE ED HH            ", "                                   ", "                                   ", "                                   ", "                                   ", "               D D D               ", "               D D D               ", "               D D D               ", "               D D D               ", "   AAAAAAAAAAAAD D DAAAAAAAAAAAA   " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "         E               E         ", "         EHH           HHE         ", "         E               E         ", "         E               E         ", "         E               E         ", "         E               E         ", "         E     D D D     E         ", "         E     D D D     E         ", "         E     D D D     E         ", "   FF    E     D D D     E    FF   ", "   AAAAAAAAAAAAD D DAAAAAAAAAAAA   " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "          E             E          ", "          E             E          ", "          E             E          ", "          E             E          ", "          E             E          ", "         HE             EH         ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "   D                           D   ", "  FFF          F F F          FFF  ", "  AAAAAAAAAAAAA     AAAAAAAAAAAAA  " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "           E           E           ", "           E           E           ", "           E           E           ", "           E           E           ", "           E           E           ", "                                   ", "                                   ", "         H               H         ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "   D                           D   ", "  FFF          F F F          FFF  ", "  AAAAAAAAAAAAA     AAAAAAAAAAAAA  " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "             HH     HH             ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "        H                 H        ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "  D                             D  ", " FFF          FFFFFFF          FFF ", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                FFF                ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "            HE       EH            ", "             E       E             ", "             E       E             ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "        H                 H        ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "  D                             D  ", " FFF         FFFFFFFFF         FFF ", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA " },
                {"                FFF                ", "                 E                 ", "                 E                 ", "                 E                 ", "                 E                 ", "                 E                 ", "               F   F               ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "                                   ", "                                   ", "                FFF                ", "                                   ", "                                   ", "            H         H            ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                FFF                ", "                                   ", "                                   ", "                                   ", "                                   ", "       H                   H       ", "                                   ", "                                   ", "                                   ", "                                   ", "                XXX                ", "                X~X                ", "  D             XXX             D  ", " FFF        FFFFFFFFFFF        FFF ", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA " },
                {"               F D F               ", "                 D                 ", "                 D                 ", "                 D                 ", "                 D                 ", "                 D                 ", "              F  D  F              ", "                 D                 ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "           DD    D    DD           ", "           D     D     D           ", "           D     D     D           ", "           D   F D F   D           ", "           D     C     D           ", "           D     C     D           ", "           D     C     D           ", "           D     C     D           ", "          DD     C     DD          ", "          D      C      D          ", "          D      C      D          ", "          D      C      D          ", "         DD      C      DD         ", "         D     FDCDF     D         ", "         D      DCD      D         ", "        DD      DCD      DD        ", "        D       DCD       D        ", "        D       DCD       D        ", "       DD      DDCDD      DD       ", "       D       D C D       D       ", "       D       D C D       D       ", "      DD       D C D       DD      ", "      D        D C D        D      ", "     DD DD     XDCDX     DD DD     ", "    DD  DD     XDCDX     DD  DD    ", " DDDD   DD     XDCDX     DD   DDDD ", "FFFD    DDFFFFFDDDDDFFFFFDD    DFFF", "AAAAAAAADD  AAAXXXXXAAA  DDAAAAAAAA" },
                {"              F     F              ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "             F       F             ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "           EE         EE           ", "           EE         EE           ", "           E           E           ", "           E           E           ", "           E  F     F  E           ", "           E           E           ", "           E           E           ", "           E           E           ", "          EE           EE          ", "          EE           EE          ", "          E             E          ", "          E             E          ", "         EE             EE         ", "         EE             EE         ", "         E    FD   DF    E         ", "        EE     D   D     EE        ", "        EE     D   D     EE        ", "        E      D   D      E        ", "       EE      D   D      EE       ", "       EE      D   D      EE       ", "       E                   E       ", "      EE                   EE      ", "      EE                   EE      ", "     EE                     EE     ", "    EEE       XD   DX       EEE    ", "   EEE        XD   DX        EEE   ", "  EE          XD   DX          EE  ", "FFF         FFFDDDDDFFF         FFF", "AAAAAAAA    AAAXXXXXAAA    AAAAAAAA" },
                {"              FD   DF              ", "              ED   DE              ", "              ED   DE              ", "              ED   DE              ", "              ED   DE              ", "              ED   DE              ", "             F D   D F             ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D   D               ", "               D B D               ", "              FD - DF              ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "              FC - CF              ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "               C - C               ", "        DD    XC - CX    DD        ", "        DD    XC - CX    DD        ", "        DD    XC - CX    DD        ", "        DDFFFFFDDDDDFFFFFDD        ", "AAAAAAAADD  AAAXXXXXAAA  DDAAAAAAAA" },
                {"              F     F              ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "             F       F             ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "           EE         EE           ", "           EE         EE           ", "           E           E           ", "           E           E           ", "           E  F     F  E           ", "           E           E           ", "           E           E           ", "           E           E           ", "          EE           EE          ", "          EE           EE          ", "          E             E          ", "          E             E          ", "         EE             EE         ", "         EE             EE         ", "         E    FD   DF    E         ", "        EE     D   D     EE        ", "        EE     D   D     EE        ", "        E      D   D      E        ", "       EE      D   D      EE       ", "       EE      D   D      EE       ", "       E                   E       ", "      EE                   EE      ", "      EE                   EE      ", "     EE                     EE     ", "    EEE       XD   DX       EEE    ", "   EEE        XD   DX        EEE   ", "  EE          XD   DX          EE  ", "FFF         FFFDDDDDFFF         FFF", "AAAAAAAA    AAAXXXXXAAA    AAAAAAAA" },
                {"               F D F               ", "                 D                 ", "                 D                 ", "                 D                 ", "                 D                 ", "                 D                 ", "              F  D  F              ", "                 D                 ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "            D    D    D            ", "           DD    D    DD           ", "           D     D     D           ", "           D     D     D           ", "           D   F D F   D           ", "           D     C     D           ", "           D     C     D           ", "           D     C     D           ", "           D     C     D           ", "          DD     C     DD          ", "          D      C      D          ", "          D      C      D          ", "          D      C      D          ", "         DD      C      DD         ", "         D     FDCDF     D         ", "         D      DCD      D         ", "        DD      DCD      DD        ", "        D       DCD       D        ", "        D       DCD       D        ", "       DD      DDCDD      DD       ", "       D       D C D       D       ", "       D       D C D       D       ", "      DD       D C D       DD      ", "      D        D C D        D      ", "     DD DD     XDCDX     DD DD     ", "    DD  DD     XDCDX     DD  DD    ", " DDDD   DD     XDCDX     DD   DDDD ", "FFFD    DDFFFFFDDDDDFFFFFDD    DFFF", "AAAAAAAADD  AAAXXXXXAAA  DDAAAAAAAA" },
                {"                FFF                ", "                 E                 ", "                 E                 ", "                 E                 ", "                 E                 ", "                 E                 ", "               F   F               ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "              E     E              ", "                                   ", "                                   ", "                FFF                ", "                                   ", "                                   ", "            H         H            ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                FFF                ", "                                   ", "                                   ", "                                   ", "                                   ", "       H                   H       ", "                                   ", "                                   ", "                                   ", "                                   ", "                XXX                ", "                XXX                ", "  D             XXX             D  ", " FFF        FFFFFFFFFFF        FFF ", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                FFF                ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "             E       E             ", "            HE       EH            ", "             E       E             ", "             E       E             ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "        H                 H        ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "  D                             D  ", " FFF         FFFFFFFFF         FFF ", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "             HH     HH             ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "            E         E            ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "        H                 H        ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "  D                             D  ", " FFF          FFFFFFF          FFF ", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "           E           E           ", "           E           E           ", "           E           E           ", "           E           E           ", "           E           E           ", "                                   ", "                                   ", "         H               H         ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "   D                           D   ", "  FFF          F F F          FFF  ", "  AAAAAAAAAAAAA     AAAAAAAAAAAAA  " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "          E             E          ", "          E             E          ", "          E             E          ", "          E             E          ", "          E             E          ", "         HE             EH         ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "   D                           D   ", "  FFF          F F F          FFF  ", "  AAAAAAAAAAAAA     AAAAAAAAAAAAA  " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "         E               E         ", "         EHH           HHE         ", "         E               E         ", "         E               E         ", "         E               E         ", "         E               E         ", "         E     D D D     E         ", "         E     D D D     E         ", "         E     D D D     E         ", "   FF    E     D D D     E    FF   ", "   AAAAAAAAAAAAD D DAAAAAAAAAAAA   " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "            HH DE ED HH            ", "                                   ", "                                   ", "                                   ", "                                   ", "               D D D               ", "               D D D               ", "               D D D               ", "               D D D               ", "   AAAAAAAAAAAAD D DAAAAAAAAAAAA   " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "              HDE EDH              ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "    AAAAAAAAAAAAAAAAAAAAAAAAAAA    " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "                                   ", "     AAAAAAAAAAAAAAAAAAAAAAAAA     " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               DE ED               ", "                                   ", "                                   ", "      AAAAAAAAAAAAAAAAAAAAAAA      " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "               DE ED               ", "               D   D               ", "         FFF           FFF         ", "       AAAAAAAAAAAAAAAAAAAAA       " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                E E                ", "          DD   DE ED   DD          ", "         FFFFFFD   DFFFFFF         ", "        AAAAAAAAAAAAAAAAAAA        " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "            DDDDE EDDDD            ", "          FFFFFFF FFFFFFF          ", "          AAAAAAAAAAAAAAA          " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "               D   D               ", "            FFFFF FFFFF            ", "            AAAAAAAAAAA            " },
                {"                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "                                   ", "               FF FF               ", "               AAAAA               " }
            })
            .addElement('E', StructureUtility.ofBlock(GregTechAPI.sBlockCasingsSE, 1)) // Support Structure
            .addElement('B', ElevatorUtil.ofBlockAdder(MTEGalaxiaSpaceElevator::addCable, GalaxiaBlocksEnum.ELEVATOR_CABLE.get(), 0))
            .addElement('X', classicHatches(CASING_INDEX_BASE, 1, GregTechAPI.sBlockCasingsSE, 0))
            .addElement('H', GTStructureUtility.ofFrame(Materials.Neutronium)) // Neutronium frame boxes
            .addElement('F', StructureUtility.ofBlock(GregTechAPI.sBlockCasingsSE, 2)) // Internal Structure
            .addElement(
                'C',
                GTStructureChannels.SE_MOTOR.use(StructureUtility.ofBlocksTiered(
                    ElevatorUtil.motorTierConverter(),
                    ElevatorUtil.getMotorTiers(),
                    0,
                    MTEGalaxiaSpaceElevator::setMotorTier,
                    MTEGalaxiaSpaceElevator::getMotorTier))) // Motors
            .addElement('A', StructureUtility.ofBlock(GregTechAPI.sBlockCasingsDyson, 9)) // Concrete
            .addElement('D', StructureUtility.ofBlock(GregTechAPI.sBlockCasingsSE, 0)) // Base Casing
            .build();
        // spotless:on
    }

    public MTEGalaxiaSpaceElevator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        useLongPower = true;
    }

    protected MTEGalaxiaSpaceElevator(String aName) {
        super(aName);
        useLongPower = true;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEGalaxiaSpaceElevator(this.mName);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        motorTier = aNBT.getInteger("motorTier");
        travelTarget = aNBT.getInteger("travelTarget");
        super.loadNBTData(aNBT);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setInteger("motorTier", motorTier);
        aNBT.setInteger("travelTarget", travelTarget);
        super.saveNBTData(aNBT);
    }

    // region Structure

    @Override
    public IStructureDefinition<? extends TTMultiblockBase> getStructure_EM() {
        return Structure.DEFINITION;
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        // The elevator should only be buildable upright
        return IAlignmentLimits.Builder.allowAll()
            .deny(ForgeDirection.DOWN)
            .deny(ForgeDirection.UP)
            .deny(Rotation.UPSIDE_DOWN)
            .deny(Rotation.CLOCKWISE)
            .deny(Rotation.COUNTER_CLOCKWISE)
            .build();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            hintsOnly,
            STRUCTURE_PIECE_MAIN_HOR_OFFSET,
            STRUCTURE_PIECE_MAIN_VERT_OFFSET,
            STRUCTURE_PIECE_MAIN_DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            STRUCTURE_PIECE_MAIN_HOR_OFFSET,
            STRUCTURE_PIECE_MAIN_VERT_OFFSET,
            STRUCTURE_PIECE_MAIN_DEPTH_OFFSET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        motorTier = 0;
        cable = null;
        checkPiece(
            STRUCTURE_PIECE_MAIN,
            STRUCTURE_PIECE_MAIN_HOR_OFFSET,
            STRUCTURE_PIECE_MAIN_VERT_OFFSET,
            STRUCTURE_PIECE_MAIN_DEPTH_OFFSET,
            errors);
        if (cable != null) cable.setShouldRender(errors.isEmpty());
    }

    /**
     * Accepts the cable block. The cable must see the sky, nothing may block the way up
     */
    public boolean addCable(Block block, int meta, World world, int x, int y, int z) {
        if (block != GalaxiaBlocksEnum.ELEVATOR_CABLE.get() || world == null) return false;
        if (!world.canBlockSeeTheSky(x, y + 1, z)) return false;
        if (!(world.getTileEntity(x, y, z) instanceof TileElevatorCable found)) return false;
        cable = found;
        return true;
    }

    @Override
    public void onRemoval() {
        if (cable != null) cable.setShouldRender(false);
        super.onRemoval();
    }

    public void setMotorTier(int tier) {
        motorTier = tier;
    }

    public int getMotorTier() {
        return motorTier;
    }

    // endregion

    // region Deliveries

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (mEfficiency < 0) mEfficiency = 0;
        fixAllIssues();
        if (aTick % TRANSFER_INTERVAL != 0) return;

        dock = findDock();
        dockStatus = describeDock();
        if (dock == null || !mMachine || motorTier <= 0 || !aBaseMetaTileEntity.isAllowedToWork()) return;
        dock.markLinked();
        if (deliver() && cable != null && cable.getAnimation() == TileElevatorCable.ClimberAnimation.NO_ANIMATION) {
            // The climber carries the delivery up the cable
            cable.startAnimation(TileElevatorCable.ClimberAnimation.DELIVER_ANIMATION);
        }
    }

    /**
     * The dock in the orbit of this planet nearest above the cable, anywhere in the cable's chunk or the chunks around
     * it, roughly the ground the elevator stands on. Empty until the structure has found its cable
     */
    public TileElevatorDock findDock() {
        if (cable == null) return null;
        World world = getBaseMetaTileEntity().getWorld();
        int dimension = world.provider.dimensionId;
        if (OrbitDimensions.isOrbit(dimension)) return null;
        OrbitDimensions.Orbit orbit = GalaxiaCelestialAPI.findByDimension(dimension)
            .flatMap(body -> OrbitDimensions.ofBody(body.key()))
            .orElse(null);
        if (orbit == null) return null;
        WorldServer orbitWorld = MinecraftServer.getServer()
            .worldServerForDimension(orbit.dimensionId());
        if (orbitWorld == null) return null;
        TileElevatorDock nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        int cableChunkX = cable.xCoord >> 4, cableChunkZ = cable.zCoord >> 4;
        for (int chunkX = cableChunkX - DOCK_CHUNK_REACH; chunkX <= cableChunkX + DOCK_CHUNK_REACH; chunkX++) {
            for (int chunkZ = cableChunkZ - DOCK_CHUNK_REACH; chunkZ <= cableChunkZ + DOCK_CHUNK_REACH; chunkZ++) {
                // Deliveries go up while nobody is in orbit, so the chunks are loaded on demand
                Chunk chunk = orbitWorld.theChunkProviderServer.loadChunk(chunkX, chunkZ);
                for (Object te : chunk.chunkTileEntityMap.values()) {
                    if (!(te instanceof TileElevatorDock found) || found.isInvalid()) continue;
                    int dx = found.xCoord - cable.xCoord, dz = found.zCoord - cable.zCoord;
                    int distance = dx * dx + dz * dz;
                    if (distance >= nearestDistance) continue;
                    nearest = found;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    /** Cable of the formed structure, null while it is not formed */
    public TileElevatorCable getCable() {
        return cable;
    }

    private String describeDock() {
        if (dock == null) return "galaxia.elevator.no_dock";
        return "galaxia.elevator.dock|" + dock.xCoord + "|" + dock.yCoord + "|" + dock.zCoord;
    }

    /**
     * Sends items and fluids from the input buses and hatches up to the dock, as much as the motors carry
     *
     * @return Boolean : True => something went up
     */
    private boolean deliver() {
        boolean delivered = false;
        int items = ITEMS_PER_TIER * motorTier;
        for (MTEHatchInputBus bus : validMTEList(mInputBusses)) {
            for (int slot = 0; slot < bus.getSizeInventory() && items > 0; slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack == null || stack.stackSize <= 0) continue;
                ItemStack sent = stack.copy();
                sent.stackSize = Math.min(stack.stackSize, items);
                int accepted = sent.stackSize - dock.insertItem(sent);
                if (accepted <= 0) continue;
                bus.decrStackSize(slot, accepted);
                items -= accepted;
                delivered = true;
            }
            bus.updateSlots();
        }

        int fluid = FLUID_PER_TIER * motorTier;
        for (MTEHatchInput hatch : validMTEList(mInputHatches)) {
            FluidStack stored = hatch.getFluid();
            if (stored == null || stored.amount <= 0 || fluid <= 0) continue;
            FluidStack sent = stored.copy();
            sent.amount = Math.min(stored.amount, fluid);
            int accepted = dock.fill(ForgeDirection.UNKNOWN, sent, true);
            if (accepted <= 0) continue;
            hatch.drain(accepted, true);
            fluid -= accepted;
            delivered = true;
        }
        return delivered;
    }

    /** Carries a player up to the dock above */
    public void rideToDock(EntityPlayerMP player) {
        if (dock == null || !mMachine || motorTier <= 0) return;
        DimensionTeleport.teleport(
            player,
            dock.getWorldObj().provider.dimensionId,
            dock.xCoord + 0.5,
            dock.yCoord + 1,
            dock.zCoord + 0.5);
    }

    /** Carries a player to the chosen dimension, only strong enough motors reach beyond the planet's own orbit */
    private void travel(EntityPlayerMP player) {
        if (!mMachine || motorTier < TRAVEL_ANYWHERE_TIER) return;
        List<DimensionTeleport.Destination> destinations = DimensionTeleport.destinations();
        if (destinations.isEmpty()) return;
        DimensionTeleport.Destination target = destinations.get(Math.floorMod(travelTarget, destinations.size()));
        DimensionTeleport.teleport(player, target.dimension(), player.posX, 128, player.posZ);
    }

    // endregion

    // region Recipe

    /**
     * Keep the machine going while enabled, it runs no recipes
     */
    @Override
    public @NotNull CheckRecipeResult checkProcessing_EM() {
        if (getBaseMetaTileEntity().isAllowedToWork()) {
            mEfficiencyIncrease = 10000;
            mMaxProgresstime = 20;
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        mEfficiencyIncrease = 0;
        mMaxProgresstime = 0;
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    protected SoundResource getActivitySoundLoop() {
        return SoundResource.TECTECH_MACHINES_FX_WHOOUM;
    }

    // endregion

    // region Client

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("galaxia.elevator.type"))
            .addInfo(StatCollector.translateToLocal("galaxia.elevator.desc1"))
            .addInfo(StatCollector.translateToLocal("galaxia.elevator.desc2"))
            .addInfo(StatCollector.translateToLocal("galaxia.elevator.desc3"))
            .addInfo(StatCollector.translateToLocalFormatted("galaxia.elevator.desc4", TRAVEL_ANYWHERE_TIER))
            .beginStructureBlock(35, 43, 35, false)
            .addController("Front center of central column, 4th layer")
            .addCasing("620", StatCollector.translateToLocal("gt.blockcasings.ig.1.name"), false)
            .addCasing("88", StatCollector.translateToLocal("ig.elevator.structure.Motor"), true)
            .addCasing("56", StatCollector.translateToLocal("ig.elevator.structure.FrameNeutronium"), false)
            .addCasing("1", StatCollector.translateToLocal("gt.blockcasings.ig.cable.name"), false)
            .addEnergyHatch("1+", "Any bottom center casing", 1)
            .addInputBus("0+", "Any bottom center casing", 1)
            .addInputHatch("0+", "Any bottom center casing", 1)
            .addSubChannel(GTStructureChannels.SE_MOTOR)
            .toolTipFinisher("Galaxia");
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX_BASE),
                new TTRenderedExtendedFacingTexture(aActive ? TTMultiblockBase.ScreenON : TTMultiblockBase.ScreenOFF) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX_BASE) };
    }

    @Override
    public boolean doesBindPlayerInventory() {
        return false;
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        screenElements.setSynced(false)
            .setSpace(0);
        screenElements
            .widget(
                new TextWidget(GTUtility.trans("138", "Incomplete Structure.")).setDefaultColor(COLOR_TEXT_WHITE.get())
                    .setEnabled(widget -> !mMachine))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> mMachine, val -> mMachine = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> motorTier, val -> motorTier = val))
            .widget(new FakeSyncWidget.StringSyncer(() -> dockStatus, val -> dockStatus = val));
        screenElements.widget(
            TextWidget
                .dynamicString(() -> StatCollector.translateToLocalFormatted("galaxia.elevator.motor_tier", motorTier))
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(widget -> mMachine));
        screenElements.widget(
            TextWidget.dynamicString(MTEGalaxiaSpaceElevator.this::dockLine)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(widget -> mMachine));
    }

    private String dockLine() {
        if (dockStatus.isEmpty()) return "";
        String[] parts = dockStatus.split("\\|");
        if (parts.length == 4) {
            return StatCollector.translateToLocalFormatted(parts[0], parts[1], parts[2], parts[3]);
        }
        return StatCollector.translateToLocal(parts[0]);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);

        // Ride up to the station dock above
        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (widget.getContext()
                .isClient()) return;
            EntityPlayer player = widget.getContext()
                .getPlayer();
            if (player instanceof EntityPlayerMP playerMP) rideToDock(playerMP);
        })
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { TecTechUITextures.BUTTON_STANDARD_16x16,
                    IG_UITextures.OVERLAY_BUTTON_PLANET_TELEPORT })
            .setPos(174, 156)
            .setSize(16, 16)
            .addTooltip(StatCollector.translateToLocal("galaxia.elevator.ride"))
            .setTooltipShowUpDelay(TOOLTIP_DELAY));

        // Travel anywhere from the strongest motors on
        List<DimensionTeleport.Destination> destinations = DimensionTeleport.destinations();
        builder.widget(
            new CycleButtonWidget().setLength(Math.max(1, destinations.size()))
                .setGetter(() -> Math.floorMod(travelTarget, Math.max(1, destinations.size())))
                .setSetter(val -> travelTarget = val)
                .setPlayClickSound(true)
                .setTextureGetter(state -> TecTechUITextures.BUTTON_STANDARD_16x16)
                .dynamicTooltip(
                    () -> List.of(
                        StatCollector.translateToLocalFormatted(
                            "galaxia.elevator.travel_target",
                            destinations.isEmpty() ? "-"
                                : destinations.get(Math.floorMod(travelTarget, destinations.size()))
                                    .name())))
                .setPos(138, 156)
                .setSize(16, 16)
                .setEnabled(widget -> motorTier >= TRAVEL_ANYWHERE_TIER));
        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (widget.getContext()
                .isClient()) return;
            EntityPlayer player = widget.getContext()
                .getPlayer();
            if (player instanceof EntityPlayerMP playerMP) travel(playerMP);
        })
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { TecTechUITextures.BUTTON_STANDARD_16x16,
                    IG_UITextures.OVERLAY_BUTTON_PLANET_TELEPORT })
            .setPos(156, 156)
            .setSize(16, 16)
            .addTooltip(StatCollector.translateToLocal("galaxia.elevator.travel"))
            .setTooltipShowUpDelay(TOOLTIP_DELAY)
            .setEnabled(widget -> motorTier >= TRAVEL_ANYWHERE_TIER));
    }

    @Override
    protected ButtonWidget createSafeVoidButton() {
        return null;
    }

    // endregion

    @Override
    public boolean willExplodeInRain() {
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean doRandomMaintenanceDamage() {
        return true;
    }
}
