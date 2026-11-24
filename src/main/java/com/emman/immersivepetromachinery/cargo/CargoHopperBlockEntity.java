package com.emman.immersivepetromachinery.cargo;

import blusunrize.immersiveengineering.common.blocks.metal.ConveyorBeltBlockEntity;
import blusunrize.immersiveengineering.common.blocks.metal.ConveyorBlock;
import blusunrize.immersiveengineering.common.blocks.metal.conveyors.BasicConveyor;
import com.emman.immersivepetromachinery.registry.IPMBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Independent cargo backend, delivering to the real adjacent standard IE belt.
 *
 * R61 model geometry, animations, UVs, cargo balancing, and safe removal
 * handling are separate acceptance gates and not claimed by this class.
 */
public final class CargoHopperBlockEntity extends BlockEntity {
    public static final int BUFFER_SLOTS = 9; // provisional internal test capacity
    public static final int TRANSFER_INTERVAL_TICKS = 8; // provisional test throughput
    private static final String INVENTORY_KEY = "R61Inventory";

    private final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            CargoHopperBlockEntity.this.setChanged();
        }
    };

    private final IItemHandler insertOnly = new IItemHandler() {
        @Override
        public int getSlots() {
            return buffer.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return buffer.getStackInSlot(slot).copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return buffer.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return buffer.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return buffer.isItemValid(slot, stack);
        }
    };

    public CargoHopperBlockEntity(BlockPos pos, BlockState state) {
        super(IPMBlockEntities.CARGO_HOPPER.get(), pos, state);
    }

    /** Insertion from the top or non-output sides only; no external extraction. */
    @Nullable
    public IItemHandler inputCapability(@Nullable Direction side) {
        if (side == Direction.DOWN || (side != null
                && side == getBlockState().getValue(CargoHopperBlock.FACING))) {
            return null;
        }
        return insertOnly;
    }

    public ItemStackHandler getBufferForDiagnostics() {
        return buffer;
    }

    /**
     * Tries one item per call. Invalid orientation, no adjacent standard IE
     * conveyor, powered/blocked output, or unloaded output keeps the item in
     * the local buffer. This does not modify any native IE implementation.
     */
    public boolean transferOneToAdjacentConveyor(ServerLevel level) {
        if (level != getLevel()) {
            return false;
        }
        Direction facing = getBlockState().getValue(CargoHopperBlock.FACING);
        BlockPos targetPos = worldPosition.relative(facing);
        if (!level.hasChunkAt(targetPos)) {
            return false;
        }
        BlockState targetState = level.getBlockState(targetPos);
        if (!(targetState.getBlock() instanceof ConveyorBlock target)
                || target.getType() != BasicConveyor.TYPE
                || targetState.getValue(ConveyorBlock.FACING) != facing
                || !(level.getBlockEntity(targetPos) instanceof ConveyorBeltBlockEntity<?> belt)
                || belt.getConveyorInstance() == null
                || belt.getConveyorInstance().isBlocked()) {
            return false;
        }

        IItemHandler receiver = level.getCapability(
                Capabilities.ItemHandler.BLOCK, targetPos, facing.getOpposite());
        if (receiver == null || receiver.getSlots() < 1) {
            return false;
        }
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stored = buffer.getStackInSlot(slot);
            if (stored.isEmpty()) {
                continue;
            }
            ItemStack offered = stored.copyWithCount(1);
            ItemStack simulatedRemainder = receiver.insertItem(0, offered.copy(), true);
            if (!simulatedRemainder.isEmpty()) {
                return false;
            }
            ItemStack actualRemainder = receiver.insertItem(0, offered.copy(), false);
            if (!actualRemainder.isEmpty()) {
                return false;
            }
            buffer.extractItem(slot, 1, false);
            return true;
        }
        return false;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INVENTORY_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag saved = tag.getCompound(INVENTORY_KEY);
            if (saved.getInt("Size") == BUFFER_SLOTS) {
                buffer.deserializeNBT(registries, saved);
            }
            // Legacy or malformed NBT requires a separately accepted
            // quarantine/migration contract before player release.
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(INVENTORY_KEY, buffer.serializeNBT(registries));
    }
}
