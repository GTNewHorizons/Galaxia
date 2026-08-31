package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Integration contract for the shared immutable recipe-book wire format. */
final class RecipeBookWireCodecTest {

    private static final ModuleInstance.ID MODULE_ID = new ModuleInstance.ID(
        UUID.fromString("20000000-0000-0000-0000-000000000002"));

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    @Test
    void privateAndGroupOwnersRoundTripWithTheirTypedIdentity() {
        List<RecipeBookOwner> owners = List
            .of(new RecipeBookOwner.Private(MODULE_ID), new RecipeBookOwner.Group(new SettingsGroup.ID(17)));

        for (RecipeBookOwner owner : owners) {
            ByteBuf buffer = Unpooled.buffer();
            RecipeBookWireCodec.writeOwner(buffer, owner);

            assertEquals(owner, RecipeBookWireCodec.readOwner(buffer));
            assertFalse(buffer.isReadable());
        }
    }

    @Test
    void completeBookRoundTripsWithoutDroppingOrderedRecipesOrSnapshotMetadata() {
        RecipeBook expected = completeBook("Diamond wash");
        ByteBuf buffer = Unpooled.buffer();

        RecipeBookWireCodec.writeBook(buffer, expected);

        assertEquals(expected, RecipeBookWireCodec.readBook(buffer));
        assertFalse(buffer.isReadable());
    }

    @Test
    void malformedLengthDelimitedBooksAndOwnersNeverReturnAValue() {
        ByteBuf negativeLength = Unpooled.buffer()
            .writeInt(-1);
        ByteBuf oversizedLength = Unpooled.buffer()
            .writeInt(RecipeBookWireCodec.MAX_BOOK_BYTES + 1);
        ByteBuf truncated = Unpooled.buffer()
            .writeInt(8)
            .writeZero(7);
        ByteBuf invalidEnums = framed(
            Unpooled.buffer()
                .writeByte(255)
                .writeByte(0)
                .writeInt(0));
        ByteBuf trailing = Unpooled.buffer();
        RecipeBookWireCodec.writeBook(trailing, RecipeBook.empty());
        trailing.setInt(0, trailing.getInt(0) + 1);
        trailing.writeByte(0);

        for (ByteBuf malformed : List.of(negativeLength, oversizedLength, truncated, invalidEnums, trailing)) {
            assertThrows(RuntimeException.class, () -> RecipeBookWireCodec.readBook(malformed));
        }

        assertThrows(
            RuntimeException.class,
            () -> RecipeBookWireCodec.readOwner(
                Unpooled.buffer()
                    .writeByte(255)));
        assertThrows(
            RuntimeException.class,
            () -> RecipeBookWireCodec.readOwner(
                Unpooled.buffer()
                    .writeByte(0)
                    .writeLong(0L)
                    .writeLong(0L)));
        assertThrows(
            RuntimeException.class,
            () -> RecipeBookWireCodec.readOwner(
                Unpooled.buffer()
                    .writeByte(1)
                    .writeInt(0)));
    }

    @Test
    void encoderRejectsOversizedBookAndDisplayNameBeforeWritingDestination() {
        ByteBuf destination = Unpooled.buffer();
        RecipeBook oversizedName = completeBook("x".repeat(RecipeBookWireCodec.MAX_DISPLAY_NAME_BYTES + 1));

        assertThrows(RuntimeException.class, () -> RecipeBookWireCodec.writeBook(destination, oversizedName));
        assertEquals(0, destination.writerIndex());
    }

    @Test
    void impossibleCountsInvalidStackMetadataUnresolvedFluidsAndIncompleteSnapshotsAreRejected() {
        ByteBuf impossibleItemCount = recipePayloadPrefix(1L).writeInt(65);

        ByteBuf invalidItemMetadata = recipePayloadPrefix(1L);
        invalidItemMetadata.writeInt(1);
        invalidItemMetadata.writeInt(net.minecraft.item.Item.getIdFromItem(Items.diamond));
        invalidItemMetadata.writeInt(-1);
        invalidItemMetadata.writeInt(1);

        ByteBuf unresolvedFluid = recipePayloadPrefix(1L);
        unresolvedFluid.writeInt(-1);
        unresolvedFluid.writeInt(-1);
        unresolvedFluid.writeInt(-1);
        unresolvedFluid.writeInt(1);
        writeUtf8(unresolvedFluid, "missing:recipe_wire_fluid");
        unresolvedFluid.writeInt(1);

        long emptySnapshotHash = RecipeSnapshot.computeContentHash(null, null, null, null, null, null, 100, 32);
        ByteBuf incompleteSnapshot = recipePayloadPrefix(emptySnapshotHash);
        for (int i = 0; i < 6; i++) incompleteSnapshot.writeInt(-1);
        incompleteSnapshot.writeByte(1);
        incompleteSnapshot.writeLong(0L);
        incompleteSnapshot.writeByte(0);
        incompleteSnapshot.writeByte(1);
        incompleteSnapshot.writeShort(0);

        for (ByteBuf malformed : List
            .of(impossibleItemCount, invalidItemMetadata, unresolvedFluid, incompleteSnapshot)) {
            assertThrows(RuntimeException.class, () -> RecipeBookWireCodec.readBook(framed(malformed)));
        }
    }

    private static RecipeBook completeBook(String displayName) {
        ItemStack input = new ItemStack(Items.diamond, 2, 3);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("wire", "item-metadata");
        input.setTagCompound(itemTag);

        FluidStack fluidOutput = new FluidStack(FluidRegistry.WATER, 1_000);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("wire", "fluid-metadata");
        fluidOutput.tag = fluidTag;

        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            42,
            new ItemStack[] { input },
            new ItemStack[] { new ItemStack(Items.emerald, 1, 0) },
            new FluidStack[] { new FluidStack(FluidRegistry.LAVA, 144) },
            new FluidStack[] { fluidOutput },
            new int[] { 7_500 },
            new int[] { 5_000 },
            200,
            480);
        SavedRecipe first = new SavedRecipe(snapshot, true, 64L, (byte) 4, (byte) 3, displayName);
        SavedRecipe second = new SavedRecipe(snapshot, false, 0L, (byte) 1, (byte) 1, "Second");
        return new RecipeBook(List.of(first, second), RecipeSchedulerMode.ORDER, NotDoablePolicy.BACK_TO_BEGINNING);
    }

    private static ByteBuf framed(ByteBuf payload) {
        ByteBuf framed = Unpooled.buffer();
        framed.writeInt(payload.readableBytes());
        framed.writeBytes(payload);
        return framed;
    }

    private static ByteBuf recipePayloadPrefix(long contentHash) {
        return Unpooled.buffer()
            .writeByte(RecipeSchedulerMode.PRIORITY.ordinal())
            .writeByte(NotDoablePolicy.SKIP.ordinal())
            .writeInt(1)
            .writeByte(1)
            .writeInt(0)
            .writeLong(contentHash)
            .writeInt(100)
            .writeInt(32);
    }

    private static void writeUtf8(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }
}
