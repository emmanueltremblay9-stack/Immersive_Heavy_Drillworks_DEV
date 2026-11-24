package com.emman.immersivepetromachinery.cargo;

import com.emman.immersivepetromachinery.registry.IPMBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Standalone IHD cargo transfer controller; unrelated to the Garage and native Digger. */
public final class CargoHopperBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CargoHopperBlock> CODEC = simpleCodec(CargoHopperBlock::new);

    public CargoHopperBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<CargoHopperBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoHopperBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != IPMBlockEntities.CARGO_HOPPER.get()) {
            return null;
        }
        return (world, pos, current, entity) -> {
            if (world instanceof ServerLevel server && entity instanceof CargoHopperBlockEntity hopper
                    && server.getGameTime() % CargoHopperBlockEntity.TRANSFER_INTERVAL_TICKS == 0) {
                hopper.transferOneToAdjacentConveyor(server);
            }
        };
    }
}
