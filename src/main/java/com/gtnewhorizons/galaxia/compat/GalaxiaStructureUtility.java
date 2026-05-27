package com.gtnewhorizons.galaxia.compat;

import static com.gtnewhorizon.structurelib.StructureLib.LOGGER;
import static com.gtnewhorizon.structurelib.StructureLib.PANIC_MODE;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.ICustomBlockSetting;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.structure.adders.ITileAdder;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;
import com.gtnewhorizons.galaxia.compat.gt.GalaxiaGTAttachmentRegistration;
import com.gtnewhorizons.galaxia.compat.gt.MTEStationPlug;
import com.gtnewhorizons.galaxia.compat.structure.IExtendedStructureElement;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

// TODO: This probably could be upstreamed, don't know if IExtendedStructureElement would be accepted though, but I
// need something for efficiently selecting the IStructureElement to perform the block check and normal
// IStructureElement can't query the valid block
public class GalaxiaStructureUtility {

    public static <T> IExtendedStructureElement<T> ofTileAdderCheckHints(ITileAdder<T> iTileAdder, Block hintBlock,
        int hintMeta) {
        if (iTileAdder == null || hintBlock == null) {
            throw new IllegalArgumentException();
        }
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return hintBlock;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                // This used to check if it's a GT tile. Since this is now an standalone mod we no longer do this
                return iTileAdder.apply(t, tileEntity);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block worldBlock = world.getBlock(x, y, z);
                return hintBlock == worldBlock && hintMeta == worldBlock.getDamageValue(world, x, y, z);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return false;
            }
        };
    }

    public static <T> IExtendedStructureElement<T> ofTileAdderCheckHintsAnyMeta(ITileAdder<T> iTileAdder,
        Block hintBlock, int hintMeta) {
        if (iTileAdder == null || hintBlock == null) {
            throw new IllegalArgumentException();
        }
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return hintBlock;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                // This used to check if it's a GT tile. Since this is now an standalone mod we no longer do this
                return couldBeValid(t, world, x, y, z, null) && iTileAdder.apply(t, tileEntity);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block worldBlock = world.getBlock(x, y, z);
                return hintBlock == worldBlock;
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return false;
            }
        };
    }

    public static <T> IExtendedStructureElement<T> ofBlock(Block block, int meta) {
        return ofBlock(block, meta, block, meta);
    }

    public static <T> IExtendedStructureElement<T> ofBlockAnyMeta(Block block) {
        return ofBlockAnyMeta(block, block, 0);
    }

    public static <T> IExtendedStructureElement<T> ofBlockAnyMeta(Block block, int defaultMeta) {
        return ofBlockAnyMeta(block, block, defaultMeta);
    }

    /**
     * Accept a block. Spawn hint/autoplace using another.
     *
     * @param block        accepted block
     * @param meta         accepted meta
     * @param defaultBlock hint block
     * @param defaultMeta  hint meta
     */
    public static <T> IExtendedStructureElement<T> ofBlock(Block block, int meta, Block defaultBlock, int defaultMeta) {
        if (block == null || defaultBlock == null) {
            throw new IllegalArgumentException();
        }
        if (block == Blocks.air) {
            if (PANIC_MODE) {
                throw new IllegalArgumentException("ofBlock() does not accept air. use isAir() instead");
            } else {
                LOGGER.warn("ofBlock() does not accept air. use isAir() instead");
                return new IExtendedStructureElement<T>() {

                    @Override
                    public Block getValidBlock() {
                        return Blocks.air;
                    }

                    @Override
                    public boolean check(T t, World world, int x, int y, int z) {
                        return StructureUtility.isAir()
                            .check(t, world, x, y, z);
                    }

                    @Override
                    public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                        return StructureUtility.isAir()
                            .spawnHint(t, world, x, y, z, trigger);
                    }

                    @Override
                    public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                        return StructureUtility.isAir()
                            .placeBlock(t, world, x, y, z, trigger);
                    }
                };
            }
        }
        if (block instanceof ICustomBlockSetting) {
            return new IExtendedStructureElement<T>() {

                @Override
                public Block getValidBlock() {
                    return block;
                }

                @Override
                public boolean check(T t, World world, int x, int y, int z) {
                    Block worldBlock = world.getBlock(x, y, z);
                    return block == worldBlock && meta == worldBlock.getDamageValue(world, x, y, z);
                }

                @Override
                public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                    return check(t, world, x, y, z);
                }

                @Override
                public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                    ((ICustomBlockSetting) defaultBlock).setBlock(world, x, y, z, defaultMeta);
                    return true;
                }

                @Override
                public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                    StructureLibAPI.hintParticle(world, x, y, z, defaultBlock, defaultMeta);
                    return true;
                }

                @Override
                public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
                    AutoPlaceEnvironment env) {
                    return BlocksToPlace.create(block, meta);
                }
            };
        } else {
            return new IExtendedStructureElement<T>() {

                @Override
                public Block getValidBlock() {
                    return block;
                }

                @Override
                public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger,
                    AutoPlaceEnvironment env) {
                    BlocksToPlace e = getBlocksToPlace(t, world, x, y, z, trigger, env);
                    IItemSource source = env.getSource();
                    EntityPlayer actor = env.getActor();
                    Consumer<IChatComponent> chatter = env.getChatter();
                    if (check(t, world, x, y, z)) return PlaceResult.SKIP;
                    if (e.getStacks() == null) {
                        ItemStack taken = source.takeOne(e.getPredicate(), true);
                        return StructureUtility.survivalPlaceBlock(
                            taken,
                            ItemStackPredicate.NBTMode.EXACT,
                            taken.stackTagCompound,
                            false,
                            world,
                            x,
                            y,
                            z,
                            source,
                            actor,
                            chatter);
                    }
                    for (ItemStack stack : e.getStacks()) {
                        if (!source.takeOne(stack, true)) {
                            IChatComponent name = new ChatComponentText(stack.getDisplayName());
                            name.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW));
                            env.getChatter()
                                .accept(new ChatComponentTranslation("structurelib.autoplace.missing_block", name));
                            continue;
                        }
                        return StructureUtility.survivalPlaceBlock(
                            stack,
                            ItemStackPredicate.NBTMode.EXACT,
                            stack.stackTagCompound,
                            false,
                            world,
                            x,
                            y,
                            z,
                            source,
                            actor,
                            chatter);
                    }
                    return PlaceResult.REJECT;
                }

                @Override
                public boolean check(T t, World world, int x, int y, int z) {
                    Block worldBlock = world.getBlock(x, y, z);
                    return block == worldBlock && meta == worldBlock.getDamageValue(world, x, y, z);
                }

                @Override
                public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                    return check(t, world, x, y, z);
                }

                @Override
                public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                    world.setBlock(x, y, z, defaultBlock, defaultMeta, 2);
                    return true;
                }

                @Override
                public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                    StructureLibAPI.hintParticle(world, x, y, z, defaultBlock, defaultMeta);
                    return true;
                }

                @Override
                public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
                    AutoPlaceEnvironment env) {
                    return BlocksToPlace.create(block, meta);
                }
            };
        }
    }

    public static <T> IExtendedStructureElement<T> ofBlockAnyMeta(Block block, Block defaultBlock, int defaultMeta) {
        if (block == null || defaultBlock == null) {
            throw new IllegalArgumentException();
        }
        if (block instanceof ICustomBlockSetting) {
            return new IExtendedStructureElement<T>() {

                @Override
                public Block getValidBlock() {
                    return block;
                }

                @Override
                public boolean check(T t, World world, int x, int y, int z) {
                    return block == world.getBlock(x, y, z);
                }

                @Override
                public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                    return check(t, world, x, y, z);
                }

                @Override
                public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                    ((ICustomBlockSetting) defaultBlock).setBlock(world, x, y, z, defaultMeta);
                    return true;
                }

                @Override
                public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                    StructureLibAPI.hintParticle(world, x, y, z, defaultBlock, defaultMeta);
                    return true;
                }

                @Override
                public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
                    AutoPlaceEnvironment env) {
                    // there is getSubItems on ItemBlock, but it's client only
                    return BlocksToPlace.create(defaultBlock, defaultMeta);
                }
            };
        } else {
            return new IExtendedStructureElement<T>() {

                @Override
                public Block getValidBlock() {
                    return block;
                }

                @Override
                public boolean check(T t, World world, int x, int y, int z) {
                    return block == world.getBlock(x, y, z);
                }

                @Override
                public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                    return check(t, world, x, y, z);
                }

                @Override
                public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                    world.setBlock(x, y, z, defaultBlock, defaultMeta, 2);
                    return true;
                }

                @Override
                public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                    StructureLibAPI.hintParticle(world, x, y, z, defaultBlock, defaultMeta);
                    return true;
                }

                @Override
                public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
                    AutoPlaceEnvironment env) {
                    // there is getSubItems on ItemBlock, but it's client only
                    return BlocksToPlace.create(defaultBlock, defaultMeta);
                }
            };
        }
    }

    @FunctionalInterface
    public interface BlockPosConsumer<T> {

        void accept(T t, int x, int y, int z);
    }

    public static <T> IExtendedStructureElement<T> ofBlockPosAdderNoMetaForceCheck(BlockPosConsumer<T> consumer,
        Block block, int hintMeta) {
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return block;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                if (block == world.getBlock(x, y, z)) {
                    consumer.accept(t, x, y, z);
                    return true;
                }
                return false;
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                return check(t, world, x, y, z);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, block, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return false;
            }
        };
    }

    public static <T> IExtendedStructureElement<T> stationHatchAdder(Block hintBlock, int hintMeta, int mteId,
        BlockPosConsumer<T> consumer) {
        if (hintBlock == null || consumer == null) throw new IllegalArgumentException();
        return new IExtendedStructureElement<T>() {

            @Override
            public Block getValidBlock() {
                return GregTechAPI.sBlockMachines;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof IGregTechTileEntity gtTE && gtTE.getMetaTileEntity() instanceof MTEStationPlug) {
                    consumer.accept(t, x, y, z);
                    return true;
                }
                return false;
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                Block block = world.getBlock(x, y, z);
                return GregTechAPI.sBlockMachines == block
                    && GalaxiaGTAttachmentRegistration.plugId.contains(block.getDamageValue(world, x, y, z));
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                StructureLibAPI.hintParticle(world, x, y, z, hintBlock, hintMeta);
                return true;
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                world.setBlock(x, y, z, hintBlock, hintMeta, 2);
                return true;
            }
        };
    }
}
