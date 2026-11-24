package com.emman.immersivepetromachinery.cargo;

import blusunrize.immersiveengineering.api.tool.conveyor.ConveyorHandler;
import blusunrize.immersiveengineering.common.blocks.metal.ConveyorBlock;
import blusunrize.immersiveengineering.common.blocks.metal.conveyors.BasicConveyor;
import com.emman.immersivepetromachinery.ImmersivePetroMachinery;
import com.emman.immersivepetromachinery.registry.IPMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** GameTest diagnostics; deliberately no Garage, native Digger or IEDCTR dependencies. */
@GameTestHolder(ImmersivePetroMachinery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CargoHopperGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    private CargoHopperGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aligned_standard_ie_conveyor_receives_one_item(GameTestHelper helper) {
        CargoHopperBlockEntity hopper = placeHopper(helper, Direction.EAST, 2);
        placeStandardConveyor(helper, Direction.EAST, Direction.EAST);
        helper.assertTrue(hopper.transferOneToAdjacentConveyor(helper.getLevel()), "Aligned IE conveyor should accept");
        helper.assertValueEqual(1, hopper.getBufferForDiagnostics().getStackInSlot(0).getCount(), "Stored remainder");
        helper.assertValueEqual(1, nearbyIron(helper), "One emitted item, no duplication");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blocked_output_holds_buffer_then_resumes(GameTestHelper helper) {
        CargoHopperBlockEntity hopper = placeHopper(helper, Direction.EAST, 2);
        helper.assertFalse(hopper.transferOneToAdjacentConveyor(helper.getLevel()), "Missing belt must block");
        placeStandardConveyor(helper, Direction.EAST, Direction.NORTH);
        helper.assertFalse(hopper.transferOneToAdjacentConveyor(helper.getLevel()), "Wrong direction must block");
        helper.assertValueEqual(2, hopper.getBufferForDiagnostics().getStackInSlot(0).getCount(), "Blocked inventory");
        helper.assertValueEqual(0, nearbyIron(helper), "Blocked emission");
        placeStandardConveyor(helper, Direction.EAST, Direction.EAST);
        helper.assertTrue(hopper.transferOneToAdjacentConveyor(helper.getLevel()), "Restored output must resume");
        helper.assertValueEqual(1, hopper.getBufferForDiagnostics().getStackInSlot(0).getCount(), "After recovery");
        helper.assertValueEqual(1, nearbyIron(helper), "Exactly one item after recovery");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void all_four_horizontal_rotations_preserve_alignment(GameTestHelper helper) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            CargoHopperBlockEntity hopper = placeHopper(helper, direction, 1);
            placeStandardConveyor(helper, direction, direction);
            helper.assertTrue(hopper.transferOneToAdjacentConveyor(helper.getLevel()), "Rotation " + direction);
            helper.assertTrue(hopper.getBufferForDiagnostics().getStackInSlot(0).isEmpty(), "Empty after " + direction);
            helper.assertValueEqual(1, nearbyIron(helper), "Output count for " + direction);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(MACHINE)).inflate(3))
                    .forEach(ItemEntity::discard);
            helper.getLevel().removeBlock(helper.absolutePos(MACHINE).relative(direction), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ordinary_inventory_nbt_round_trip_is_lossless(GameTestHelper helper) {
        CargoHopperBlockEntity hopper = placeHopper(helper, Direction.SOUTH, 5);
        ServerLevel level = helper.getLevel();
        CompoundTag saved = hopper.saveWithFullMetadata(level.registryAccess());
        CargoHopperBlockEntity reloaded = new CargoHopperBlockEntity(hopper.getBlockPos(), hopper.getBlockState());
        reloaded.loadWithComponents(saved, level.registryAccess());
        helper.assertValueEqual(5, reloaded.getBufferForDiagnostics().getStackInSlot(0).getCount(), "Reload count");
        helper.assertTrue(reloaded.getBufferForDiagnostics().getStackInSlot(0).is(Items.IRON_INGOT), "Reload ID");
        helper.assertValueEqual(0, nearbyIron(helper), "No entity created by save/load");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void input_only_side_capability_cannot_bypass_belt(GameTestHelper helper) {
        CargoHopperBlockEntity hopper = placeHopper(helper, Direction.NORTH, 0);
        helper.assertTrue(hopper.inputCapability(Direction.UP) != null, "Top input exists");
        helper.assertTrue(hopper.inputCapability(Direction.NORTH) == null, "Front capability is absent");
        helper.assertTrue(hopper.inputCapability(Direction.DOWN) == null, "Bottom capability is absent");
        var handler = hopper.inputCapability(Direction.UP);
        helper.assertTrue(handler != null, "Input still exists");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(),
                "Insert two items");
        helper.assertTrue(handler.extractItem(0, 2, false).isEmpty(), "Extraction denied");
        helper.assertValueEqual(2, hopper.getBufferForDiagnostics().getStackInSlot(0).getCount(), "Buffer unchanged");
        helper.succeed();
    }

    private static CargoHopperBlockEntity placeHopper(GameTestHelper helper, Direction output, int iron) {
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = IPMBlocks.CARGO_HOPPER.get().defaultBlockState()
                .setValue(CargoHopperBlock.FACING, output);
        helper.getLevel().setBlockAndUpdate(pos, state);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof CargoHopperBlockEntity hopper)) {
            throw new IllegalStateException("No R61 Cargo Hopper block entity");
        }
        if (iron > 0) {
            hopper.getBufferForDiagnostics().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, iron));
        }
        return hopper;
    }

    private static void placeStandardConveyor(GameTestHelper helper, Direction output, Direction beltFacing) {
        BlockPos belt = helper.absolutePos(MACHINE).relative(output);
        BlockState standardIeBelt = ConveyorHandler.getBlock(BasicConveyor.TYPE).defaultBlockState()
                .setValue(ConveyorBlock.FACING, beltFacing);
        helper.getLevel().setBlockAndUpdate(belt, standardIeBelt);
    }

    private static int nearbyIron(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(helper.absolutePos(MACHINE)).inflate(3))
                .stream().filter(item -> item.getItem().is(Items.IRON_INGOT))
                .mapToInt(item -> item.getItem().getCount()).sum();
    }
}
