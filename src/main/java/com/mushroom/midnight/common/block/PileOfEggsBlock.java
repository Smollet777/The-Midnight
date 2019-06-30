package com.mushroom.midnight.common.block;

import com.google.common.collect.Lists;
import com.mushroom.midnight.common.entity.creature.StingerEntity;
import com.mushroom.midnight.common.registry.MidnightSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Random;

@SuppressWarnings({ "WeakerAccess", "deprecation" })
public abstract class PileOfEggsBlock extends Block {
    protected static final VoxelShape bound_one_egg = makeCuboidShape(0.1875d, 0d, 0.1875d, 0.75d, 0.4375d, 0.75d);
    protected static final VoxelShape bound_several_eggs = makeCuboidShape(0.0625d, 0d, 0.0625d, 0.9375d, 0.4375d, 0.9375d);
    public static final IntegerProperty EGGS = IntegerProperty.create("eggs", 1, 4);

    protected PileOfEggsBlock() {
        super(Properties.create(Material.ROCK).sound(MidnightSounds.PILE_OF_EGGS).hardnessAndResistance(-1f, 0f));
        setDefaultState(getStateContainer().getBaseState().with(EGGS, 1));
        //setCreativeTab(MidnightItemGroups.DECORATION);
    }

    protected abstract MobEntity createEntityForEgg(World world, BlockPos pos, BlockState state);

    @Override
    public boolean onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() == Item.getItemFromBlock(this)) {
            if (state.get(EGGS) < 4 && !player.getCooldownTracker().hasCooldown(stack.getItem())) {
                player.getCooldownTracker().setCooldown(stack.getItem(), 10);
                if (!player.world.isRemote) {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    world.setBlockState(pos, state.with(EGGS, state.get(EGGS) + 1));
                    world.playSound(null, pos, this.soundType.getPlaceSound(), SoundCategory.BLOCKS, (this.soundType.getVolume() + 1f) / 2f, this.soundType.getPitch() * 0.8f);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        if (canTrample(entity)) {
            onTrample(world, pos, entity, 0.05f);
        }
    }

    @Override
    public void onFallenUpon(World world, BlockPos pos, Entity entity, float fallDistance) {
        super.onFallenUpon(world, pos, entity, fallDistance);
        if (canTrample(entity)) {
            onTrample(world, pos, entity, 1f);
        }
    }

    protected boolean canTrample(Entity entity) {
        return !(entity instanceof StingerEntity);
    }

    protected void onTrample(World world, BlockPos pos, Entity entity, float chance) {
        if (!world.isRemote && canTrample(entity) && (chance >= 1f || world.rand.nextFloat() <= chance)) {
            breakEggs(world, pos, world.getBlockState(pos));
        }
    }

    protected void breakEggs(World world, BlockPos pos, BlockState state) {
        if (world.isRemote) { return; }
        world.playSound(null, pos, MidnightSounds.PILE_OF_EGGS.getBreakSound(), SoundCategory.BLOCKS, 0.7F, 0.9F + world.rand.nextFloat() * 0.2F);
        int eggs = state.get(EGGS);
        if (eggs <= 1) {
            world.destroyBlock(pos, false);
        } else {
            world.setBlockState(pos, state.with(EGGS, --eggs), 2);
            world.playEvent(2001, pos, getStateId(state));
        }
        if (harvesters.get() != null) {
            ItemStack stack = harvesters.get().getHeldItemMainhand();
            int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack);
            ArrayList<ItemStack> drops = Lists.newArrayList(new ItemStack(this));
            float chance = ForgeEventFactory.fireBlockHarvesting(drops, world, pos, state, fortune, (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack) > 0 ? 0.6f : 0.2f + (0.1f * fortune)), false, harvesters.get());
            if (world.rand.nextFloat() <= chance) {
                drops.forEach(c -> spawnAsEntity(world, pos, c));
                return;
            }
        }

        MobEntity creature;
        try {
            creature = createEntityForEgg(world, pos, state);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        creature.setPositionAndRotation(pos.getX() + world.rand.nextFloat(), pos.getY() + 0.45f, pos.getZ() + world.rand.nextFloat(), world.rand.nextFloat() * 360f, 0f);
        world.addEntity(creature);
    }

    @Override
    public void harvestBlock(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable TileEntity tile, ItemStack stack) {
        player.addStat(StatsList.getBlockStats(this));
        player.addExhaustion(0.005F);
        harvesters.set(player);
        breakEggs(world, pos, state);
        harvesters.set(null);
    }

    @Override
    public boolean isReplaceable(BlockState state, BlockItemUseContext useContext) {
        return false;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isFullCube(BlockState state) {
        return false;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return getShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return (state.get(EGGS) > 1 ? bound_several_eggs : bound_one_egg);
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, BlockState state, BlockPos pos, Direction face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(EGGS);
    }

    @Override
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }
}
