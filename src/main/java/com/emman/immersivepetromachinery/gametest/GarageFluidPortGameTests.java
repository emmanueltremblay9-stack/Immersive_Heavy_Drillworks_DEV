package com.emman.immersivepetromachinery.gametest;

import com.emman.immersivepetromachinery.ImmersivePetroMachinery;
import com.emman.immersivepetromachinery.block.DockingControllerBlock;
import com.emman.immersivepetromachinery.block.entity.DockingControllerBlockEntity;
import com.emman.immersivepetromachinery.registry.IPMBlocks;
import com.emman.immersivepetromachinery.fluid.DockFluidPortAccess;
import com.emman.immersivepetromachinery.multiblock.DockingPlatformValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ImmersivePetroMachinery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarageFluidPortGameTests {
    // The GameTest structure block is one block below the template origin.
    private static final BlockPos CONTROLLER = new BlockPos(5, 3, 12);
    private static final int TRANSFER_MB = 25;

    private GarageFluidPortGameTests() {
    }

    @GameTest(template = "multiblocks/docking_platform", timeoutTicks = 180)
    public static void cached_ie_pipe_route_tracks_garage_validity_and_removal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = helper.absolutePos(CONTROLLER);
        Direction facing = level.getBlockState(controller).getValue(DockingControllerBlock.FACING);
        BlockPos port = DockFluidPortAccess.fuelPortPos(controller, facing);
        BlockPos pipe = port.relative(facing.getOpposite());
        BlockPos required = DockingPlatformValidator.toWorld(controller, facing, 5, 7, -10);
        BlockState original = level.getBlockState(required);
        BlockState originalPipeSite = level.getBlockState(pipe);
        Block pipeBlock = BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath("immersiveengineering", "fluid_pipe"))
                .orElseThrow();
        Fluid diesel = BuiltInRegistries.FLUID
                .getOptional(ResourceLocation.fromNamespaceAndPath("immersivepetroleum", "diesel"))
                .orElseThrow();

        DockingPlatformValidator.ValidationResult layout = DockingPlatformValidator.validate(level, controller, facing);
        helper.assertTrue(layout.valid(), layout.summary());
        level.scheduleTick(controller, level.getBlockState(controller).getBlock(), 1);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(controller).getValue(DockingControllerBlock.VALID), "Garage is not assembled"))
                .thenExecute(() -> level.setBlockAndUpdate(pipe, pipeBlock.defaultBlockState()))
                .thenExecuteAfter(2, () -> {
                    helper.assertValueEqual(feedPipe(helper, pipe, facing, diesel), TRANSFER_MB, "Initial feed");
                    level.removeBlock(required, false);
                })
                .thenWaitUntil(() -> helper.assertFalse(
                        level.getBlockState(controller).getValue(DockingControllerBlock.VALID), "Garage is still valid"))
                .thenExecute(() -> {
                    helper.assertValueEqual(feedPipe(helper, pipe, facing, diesel), 0, "Feed after structural failure");
                    level.setBlockAndUpdate(required, original);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(controller).getValue(DockingControllerBlock.VALID), "Garage did not revalidate"))
                .thenExecute(() -> {
                    helper.assertValueEqual(feedPipe(helper, pipe, facing, diesel), TRANSFER_MB, "Feed after restoration");
                    level.removeBlock(controller, false);
                    helper.assertValueEqual(feedPipe(helper, pipe, facing, diesel), 0, "Feed after controller removal");
                    level.setBlockAndUpdate(pipe, originalPipeSite);
                })
                .thenSucceed();
    }


    @GameTest(template = "multiblocks/docking_platform", timeoutTicks = 180)
    public static void garage_fuel_and_lubricant_ports_are_fill_only_on_all_faces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerPos = helper.absolutePos(CONTROLLER);
        BlockState controllerState = level.getBlockState(controllerPos);
        Direction facing = controllerState.getValue(DockingControllerBlock.FACING);
        BlockPos fuelPort = DockFluidPortAccess.fuelPortPos(controllerPos, facing);
        BlockPos lubricantPort = DockFluidPortAccess.lubricantPortPos(controllerPos, facing);
        var rawController = level.getBlockEntity(controllerPos);
        helper.assertTrue(rawController instanceof DockingControllerBlockEntity,
                "Docking Controller block entity must exist");
        DockingControllerBlockEntity controller = (DockingControllerBlockEntity) rawController;

        Fluid diesel = BuiltInRegistries.FLUID
                .getOptional(ResourceLocation.fromNamespaceAndPath("immersivepetroleum", "diesel"))
                .orElseThrow();
        Fluid gasoline = BuiltInRegistries.FLUID
                .getOptional(ResourceLocation.fromNamespaceAndPath("immersivepetroleum", "gasoline"))
                .orElseThrow();
        Fluid lubricant = BuiltInRegistries.FLUID
                .getOptional(ResourceLocation.fromNamespaceAndPath("immersivepetroleum", "lubricant"))
                .orElseThrow();

        DockingPlatformValidator.ValidationResult layout =
                DockingPlatformValidator.validate(level, controllerPos, facing);
        helper.assertTrue(layout.valid(), layout.summary());
        helper.assertTrue(level.getBlockState(fuelPort).is(IPMBlocks.FUEL_PORT.get()),
                "Fuel Port block must be present at its documented position");
        helper.assertTrue(level.getBlockState(lubricantPort).is(IPMBlocks.LUBRICANT_PORT.get()),
                "Lubricant Port block must be present at its documented position");
        level.scheduleTick(controllerPos, controllerState.getBlock(), 1);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(controllerPos).getValue(DockingControllerBlock.VALID),
                        "Garage is not assembled"))
                .thenExecute(() -> {
                    helper.assertValueEqual(16_000, controller.getFuelCapacity(),
                            "controller fuel capacity is one 16,000 mB tank");
                    helper.assertValueEqual(16_000, controller.getLubricantCapacity(),
                            "controller lubricant capacity is one 16,000 mB tank");

                    IFluidHandler fuelNorth = requirePortHandler(helper, level, fuelPort, Direction.NORTH, "fuel north");
                    IFluidHandler lubricantNorth =
                            requirePortHandler(helper, level, lubricantPort, Direction.NORTH, "lubricant north");

                    helper.assertTrue(fuelNorth.isFluidValid(0, new FluidStack(diesel, 1)),
                            "diesel must be valid for Fuel Port");
                    helper.assertTrue(fuelNorth.isFluidValid(0, new FluidStack(gasoline, 1)),
                            "gasoline must be individually valid for Fuel Port");
                    helper.assertFalse(fuelNorth.isFluidValid(0, new FluidStack(lubricant, 1)),
                            "lubricant must be rejected by Fuel Port");
                    helper.assertFalse(lubricantNorth.isFluidValid(0, new FluidStack(diesel, 1)),
                            "diesel must be rejected by Lubricant Port");
                    helper.assertTrue(lubricantNorth.isFluidValid(0, new FluidStack(lubricant, 1)),
                            "lubricant must be valid for Lubricant Port");

                    helper.assertValueEqual(1_000,
                            fuelNorth.fill(new FluidStack(diesel, 1_000), IFluidHandler.FluidAction.SIMULATE),
                            "Fuel Port simulation must report the available fill");
                    helper.assertValueEqual(0, controller.getFuelAmountForDiagnostics(),
                            "simulated fuel fill must not mutate the tank");
                    helper.assertValueEqual(1_000,
                            fuelNorth.fill(new FluidStack(diesel, 1_000), IFluidHandler.FluidAction.EXECUTE),
                            "Fuel Port execution must accept diesel");
                    helper.assertValueEqual(1_000, controller.getFuelAmountForDiagnostics(),
                            "executed fuel fill must update the single controller tank");

                    helper.assertValueEqual(0,
                            requirePortHandler(helper, level, fuelPort, Direction.SOUTH, "fuel south")
                                    .fill(new FluidStack(gasoline, 1), IFluidHandler.FluidAction.EXECUTE),
                            "a second accepted fuel identity must not mix with diesel already in the tank");
                    helper.assertValueEqual(1_000, controller.getFuelAmountForDiagnostics(),
                            "rejected fuel mixing must preserve the existing diesel amount");
                    helper.assertValueEqual("immersivepetroleum:diesel", controller.getFuelFluidId(),
                            "rejected fuel mixing must preserve the existing fluid identity");
                    helper.assertValueEqual(0,
                            fuelNorth.fill(new FluidStack(lubricant, 1), IFluidHandler.FluidAction.EXECUTE),
                            "Fuel Port must reject lubricant without mutation");

                    helper.assertValueEqual(1_000,
                            lubricantNorth.fill(new FluidStack(lubricant, 1_000), IFluidHandler.FluidAction.SIMULATE),
                            "Lubricant Port simulation must report the available fill");
                    helper.assertValueEqual(0, controller.getLubricantAmountForDiagnostics(),
                            "simulated lubricant fill must not mutate the tank");
                    helper.assertValueEqual(1_000,
                            lubricantNorth.fill(new FluidStack(lubricant, 1_000), IFluidHandler.FluidAction.EXECUTE),
                            "Lubricant Port execution must accept lubricant");
                    helper.assertValueEqual(0,
                            requirePortHandler(helper, level, lubricantPort, Direction.SOUTH, "lubricant south")
                                    .fill(new FluidStack(diesel, 1), IFluidHandler.FluidAction.EXECUTE),
                            "Lubricant Port must reject diesel without mutation");
                    helper.assertValueEqual(1_000, controller.getLubricantAmountForDiagnostics(),
                            "rejected lubricant mixing must preserve the existing lubricant amount");

                    fillRemainingAcrossFaces(helper, level, fuelPort, diesel,
                            controller.getFuelAmountForDiagnostics(), "fuel");
                    fillRemainingAcrossFaces(helper, level, lubricantPort, lubricant,
                            controller.getLubricantAmountForDiagnostics(), "lubricant");

                    assertAllFacesSeeSameFillOnlyTank(helper, level, fuelPort, 16_000,
                            "immersivepetroleum:diesel", "fuel");
                    assertAllFacesSeeSameFillOnlyTank(helper, level, lubricantPort, 16_000,
                            "immersivepetroleum:lubricant", "lubricant");

                    // The current physical port blocks are not BucketPickup blocks; verify an empty bucket
                    // cannot withdraw the stored contents through the normal player item-use path.
                    helper.assertFalse(level.getBlockState(fuelPort).getBlock()
                                    instanceof net.minecraft.world.level.block.BucketPickup,
                            "Fuel Port must not expose a vanilla bucket-pickup interaction");
                    helper.assertFalse(level.getBlockState(lubricantPort).getBlock()
                                    instanceof net.minecraft.world.level.block.BucketPickup,
                            "Lubricant Port must not expose a vanilla bucket-pickup interaction");
                    var player = FakePlayerFactory.getMinecraft(level);
                    player.setPos(fuelPort.getX() + 0.5, fuelPort.getY() + 0.5, fuelPort.getZ() + 0.5);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    Items.BUCKET.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(fuelPort), Direction.UP, fuelPort, false)));
                    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET),
                            "using an empty bucket on Fuel Port must not consume or fill the bucket");
                    helper.assertValueEqual(16_000, controller.getFuelAmountForDiagnostics(),
                            "empty-bucket use must not withdraw fuel");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    Items.BUCKET.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(lubricantPort), Direction.UP, lubricantPort, false)));
                    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET),
                            "using an empty bucket on Lubricant Port must not consume or fill the bucket");
                    helper.assertValueEqual(16_000, controller.getLubricantAmountForDiagnostics(),
                            "empty-bucket use must not withdraw lubricant");

                    // Save/load the actual controller state twice; the source tag must remain untouched and
                    // the exact amounts/identities must not grow, disappear, or enter quarantine.
                    var registries = level.registryAccess();
                    CompoundTag saved = controller.saveWithFullMetadata(registries);
                    CompoundTag original = saved.copy();
                    DockingControllerBlockEntity loaded = new DockingControllerBlockEntity(
                            controllerPos, controllerState);
                    loaded.loadWithComponents(saved, registries);
                    helper.assertTrue(saved.equals(original), "loading must not mutate the source controller NBT");
                    assertReloadedTankState(helper, loaded, "after first NBT reload");

                    CompoundTag savedAgain = loaded.saveWithFullMetadata(registries);
                    DockingControllerBlockEntity loadedAgain = new DockingControllerBlockEntity(
                            controllerPos, controllerState);
                    loadedAgain.loadWithComponents(savedAgain, registries);
                    assertReloadedTankState(helper, loadedAgain, "after second NBT reload");

                    helper.assertTrue(controller.hasProtectedStateForDestruction(),
                            "a controller with stored fuel/lubricant must remain protected until a safe drain path exists");
                })
                .thenSucceed();
    }

    private static IFluidHandler requirePortHandler(
            GameTestHelper helper, ServerLevel level, BlockPos port, Direction side, String label) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, port, side);
        helper.assertTrue(handler != null, label + " capability must be available");
        return handler;
    }

    private static void fillRemainingAcrossFaces(
            GameTestHelper helper, ServerLevel level, BlockPos port, Fluid fluid, int current, String label) {
        Direction[] faces = {Direction.SOUTH, Direction.WEST, Direction.EAST};
        int remaining = 16_000 - current;
        int each = remaining / faces.length;
        int extra = remaining % faces.length;
        for (int i = 0; i < faces.length; i++) {
            int amount = each + (i < extra ? 1 : 0);
            IFluidHandler handler = requirePortHandler(helper, level, port, faces[i], label + " " + faces[i]);
            helper.assertValueEqual(amount,
                    handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.SIMULATE),
                    label + " simulated partial fill through " + faces[i]);
            helper.assertValueEqual(current, handler.getFluidInTank(0).getAmount(),
                    label + " simulation must leave the shared tank unchanged");
            helper.assertValueEqual(amount,
                    handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE),
                    label + " executed partial fill through " + faces[i]);
            current += amount;
            helper.assertValueEqual(current, handler.getFluidInTank(0).getAmount(),
                    label + " all faces must address one shared controller tank");
        }
        helper.assertValueEqual(16_000, current, label + " total shared capacity");
        IFluidHandler fullFace = requirePortHandler(helper, level, port, Direction.DOWN, label + " down");
        helper.assertValueEqual(0,
                fullFace.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.SIMULATE),
                label + " full tank must reject simulated over-capacity fill");
        helper.assertValueEqual(0,
                fullFace.fill(new FluidStack(fluid, 1), IFluidHandler.FluidAction.EXECUTE),
                label + " full tank must reject executed over-capacity fill");
        helper.assertValueEqual(16_000, fullFace.getFluidInTank(0).getAmount(),
                label + " over-capacity attempts must not change the tank");
    }

    private static void assertAllFacesSeeSameFillOnlyTank(
            GameTestHelper helper, ServerLevel level, BlockPos port, int amount, String fluidId, String label) {
        for (Direction side : Direction.values()) {
            IFluidHandler handler = requirePortHandler(helper, level, port, side, label + " " + side);
            helper.assertValueEqual(1, handler.getTanks(), label + " tank count on " + side);
            helper.assertValueEqual(16_000, handler.getTankCapacity(0), label + " capacity on " + side);
            FluidStack visible = handler.getFluidInTank(0);
            helper.assertValueEqual(amount, visible.getAmount(), label + " amount visible on " + side);
            helper.assertValueEqual(fluidId, BuiltInRegistries.FLUID.getKey(visible.getFluid()).toString(),
                    label + " identity visible on " + side);
            helper.assertTrue(handler.drain(1_000, IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                    label + " amount-drain simulation must be rejected on " + side);
            helper.assertTrue(handler.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                    label + " amount-drain execution must be rejected on " + side);
            helper.assertTrue(handler.drain(new FluidStack(visible.getFluid(), 1_000),
                            IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                    label + " resource-drain simulation must be rejected on " + side);
            helper.assertTrue(handler.drain(new FluidStack(visible.getFluid(), 1_000),
                            IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                    label + " resource-drain execution must be rejected on " + side);
            helper.assertValueEqual(amount, handler.getFluidInTank(0).getAmount(),
                    label + " all drain attempts must preserve amount on " + side);
        }
        IFluidHandler unsided = level.getCapability(Capabilities.FluidHandler.BLOCK, port, null);
        helper.assertTrue(unsided != null, label + " unsided capability must be available");
        helper.assertValueEqual(16_000, unsided.getTankCapacity(0), label + " unsided capacity");
        helper.assertValueEqual(amount, unsided.getFluidInTank(0).getAmount(), label + " unsided amount");
        helper.assertTrue(unsided.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                label + " unsided drain must be rejected");
        helper.assertValueEqual(amount, unsided.getFluidInTank(0).getAmount(),
                label + " unsided drain must not mutate the tank");
    }

    private static void assertReloadedTankState(
            GameTestHelper helper, DockingControllerBlockEntity loaded, String context) {
        helper.assertValueEqual(16_000, loaded.getFuelAmountForDiagnostics(), context + ": exact fuel amount");
        helper.assertValueEqual("immersivepetroleum:diesel", loaded.getFuelFluidId(),
                context + ": exact fuel identity");
        helper.assertValueEqual(16_000, loaded.getLubricantAmountForDiagnostics(),
                context + ": exact lubricant amount");
        helper.assertValueEqual("immersivepetroleum:lubricant", loaded.getLubricantFluidId(),
                context + ": exact lubricant identity");
        helper.assertFalse(loaded.hasRecoveryQuarantine(), context + ": valid tanks must not enter quarantine");
        helper.assertTrue(loaded.getFuelPortHandler().drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                context + ": loaded fuel handler remains fill-only");
        helper.assertTrue(loaded.getLubricantPortHandler().drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                context + ": loaded lubricant handler remains fill-only");
    }

    private static int feedPipe(GameTestHelper helper, BlockPos pipe, Direction facing, Fluid diesel) {
        IFluidHandler inlet = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pipe, facing.getOpposite());
        helper.assertTrue(inlet != null, "Missing IE pipe capability");
        return inlet.fill(new FluidStack(diesel, TRANSFER_MB), IFluidHandler.FluidAction.EXECUTE);
    }
}
