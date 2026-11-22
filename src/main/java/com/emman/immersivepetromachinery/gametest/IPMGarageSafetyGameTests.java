package com.emman.immersivepetromachinery.gametest;

import com.emman.immersivepetromachinery.ImmersivePetroMachinery;
import com.emman.immersivepetromachinery.block.DockingControllerBlock;
import com.emman.immersivepetromachinery.block.entity.DockingControllerBlockEntity;
import com.emman.immersivepetromachinery.docking.TunnelDiggerDockingDetector;
import com.emman.immersivepetromachinery.manual.IPMManualMultiblocks;
import com.emman.immersivepetromachinery.menu.DockingMaintenanceMenu;
import com.emman.immersivepetromachinery.menu.DockingMaintenanceSnapshot;
import com.emman.immersivepetromachinery.mining.IndustrialDiggerMiningProfile;
import com.emman.immersivepetromachinery.multiblock.formed.DockingGantryFormedState;
import com.emman.immersivepetromachinery.registry.IPMBlocks;
import com.emman.immersivepetromachinery.registry.IPMCreativeTabs;
import com.emman.immersivepetromachinery.registry.IPMFormedMultiblocks;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ImmersivePetroMachinery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IPMGarageSafetyGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEMPLATE = "empty";
    private static final BlockPos CONTROLLER_POS = new BlockPos(1, 1, 1);
    private static final ResourceLocation DIESEL_ID = ResourceLocation.fromNamespaceAndPath("immersivepetroleum", "diesel");
    private static final ResourceLocation LUBRICANT_ID =
            ResourceLocation.fromNamespaceAndPath("immersivepetroleum", "lubricant");
    private static final ResourceLocation DOCKING_GANTRY_ID =
            ResourceLocation.fromNamespaceAndPath(ImmersivePetroMachinery.MOD_ID, "docking_gantry");

    private IPMGarageSafetyGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void normal_mode_formation_disabled_does_not_replace_controller(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK1.get()));

        helper.assertFalse(
                IPMManualMultiblocks.isFormedShellCreationEnabledForRuntime(),
                "formed shell creation must be disabled for default runtime"
        );
        helper.assertValueEqual(
                IPMManualMultiblocks.FormedShellCreationDecision.DISABLED,
                IPMManualMultiblocks.evaluateFormedShellCreation(false, controller),
                "normal mode should stop before formed shell creation"
        );
        assertControllerUnreplaced(helper);
        helper.assertTrue(controller.hasProtectedStateForFormation(), "test controller should contain protected state");
        helper.assertValueEqual(
                1,
                controller.getGarageUpgradeItemCountForDiagnostics(),
                "upgrade inventory must remain intact"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void protected_state_blocks_experimental_formation(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK1.get()));

        helper.assertValueEqual(
                IPMManualMultiblocks.FormedShellCreationDecision.BLOCKED_PROTECTED_STATE,
                IPMManualMultiblocks.evaluateFormedShellCreation(true, controller),
                "experimental formation must block protected controller state"
        );
        assertControllerUnreplaced(helper);
        helper.assertValueEqual(
                1,
                controller.getGarageUpgradeItemCountForDiagnostics(),
                "protected upgrade item must remain intact"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void formed_diagnostics_reset_when_nbt_is_missing(GameTestHelper helper) {
        DockingGantryFormedState state = new DockingGantryFormedState();
        state.recordDiagnostics(
                helper.absolutePos(CONTROLLER_POS),
                Direction.EAST,
                true,
                new BlockPos(1, 0, -2)
        );

        CompoundTag saved = new CompoundTag();
        state.writeSaveNBT(saved, helper.getLevel().registryAccess());
        helper.assertTrue(saved.contains("Diagnostics"), "setup should write formed diagnostics");

        state.readSaveNBT(new CompoundTag(), helper.getLevel().registryAccess());

        helper.assertTrue(state.getMasterPosition() == null, "missing diagnostics NBT should clear master position");
        helper.assertValueEqual(Direction.NORTH, state.getOrientation(), "missing diagnostics NBT should reset orientation");
        helper.assertFalse(state.isFormedValid(), "missing diagnostics NBT should clear stale formed-valid flag");
        helper.assertTrue(
                state.getControllerRelativeClickPosition() == null,
                "missing diagnostics NBT should clear click position"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void formed_diagnostics_reset_when_diagnostics_tag_is_incomplete(GameTestHelper helper) {
        DockingGantryFormedState state = new DockingGantryFormedState();
        state.recordDiagnostics(
                helper.absolutePos(CONTROLLER_POS),
                Direction.EAST,
                true,
                new BlockPos(1, 0, -2)
        );

        CompoundTag malformed = new CompoundTag();
        CompoundTag diagnostics = new CompoundTag();
        diagnostics.putInt("MasterX", 8);
        diagnostics.putString("Orientation", Direction.EAST.getName());
        diagnostics.putBoolean("FormedValid", true);
        malformed.put("Diagnostics", diagnostics);

        state.readSaveNBT(malformed, helper.getLevel().registryAccess());

        helper.assertTrue(state.getMasterPosition() == null, "incomplete diagnostics NBT should clear master position");
        helper.assertValueEqual(Direction.NORTH, state.getOrientation(), "incomplete diagnostics NBT should reset orientation");
        helper.assertFalse(state.isFormedValid(), "incomplete diagnostics NBT should clear stale formed-valid flag");
        helper.assertTrue(
                state.getControllerRelativeClickPosition() == null,
                "incomplete diagnostics NBT should clear click position"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void formed_diagnostics_reset_when_diagnostics_field_types_are_invalid(GameTestHelper helper) {
        DockingGantryFormedState state = new DockingGantryFormedState();
        state.recordDiagnostics(
                helper.absolutePos(CONTROLLER_POS),
                Direction.EAST,
                true,
                new BlockPos(1, 0, -2)
        );

        CompoundTag malformed = new CompoundTag();
        CompoundTag diagnostics = new CompoundTag();
        diagnostics.putString("MasterX", "8");
        diagnostics.putInt("MasterY", 9);
        diagnostics.putInt("MasterZ", 10);
        diagnostics.putString("Orientation", Direction.EAST.getName());
        diagnostics.putBoolean("FormedValid", true);
        diagnostics.putInt("ClickX", 1);
        diagnostics.putInt("ClickY", 0);
        diagnostics.putInt("ClickZ", -2);
        malformed.put("Diagnostics", diagnostics);

        state.readSaveNBT(malformed, helper.getLevel().registryAccess());

        helper.assertTrue(state.getMasterPosition() == null, "wrong-typed diagnostics NBT should clear master position");
        helper.assertValueEqual(Direction.NORTH, state.getOrientation(), "wrong-typed diagnostics NBT should reset orientation");
        helper.assertFalse(state.isFormedValid(), "wrong-typed diagnostics NBT should clear stale formed-valid flag");
        helper.assertTrue(
                state.getControllerRelativeClickPosition() == null,
                "wrong-typed diagnostics NBT should clear click position"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void formed_docking_gantry_registered_for_resource_coverage(GameTestHelper helper) {
        helper.assertTrue(
                BuiltInRegistries.BLOCK.containsKey(DOCKING_GANTRY_ID),
                "formed docking gantry block should be registered for block resources"
        );
        helper.assertTrue(
                BuiltInRegistries.ITEM.containsKey(DOCKING_GANTRY_ID),
                "formed docking gantry item should be registered for item and loot resources"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void creative_tab_exposes_formed_docking_gantry_item(GameTestHelper helper) {
        helper.assertTrue(
                IPMCreativeTabs.mainTabItemsForDiagnostics().stream()
                        .anyMatch(stack -> stack.is(IPMFormedMultiblocks.DOCKING_GANTRY.blockItem().get())),
                "main creative tab should expose the formed Industrial Driller Garage item"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void legacy_survey_console_registry_shell_is_hidden_and_inert(GameTestHelper helper) {
        BlockPos surveyPos = CONTROLLER_POS.above();
        helper.setBlock(surveyPos, IPMBlocks.SURVEY_CONSOLE.get());
        List<ItemEntity> itemEntities = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8.0D)
        );

        helper.assertTrue(
                IPMBlocks.SURVEY_CONSOLE.get().getClass() == net.minecraft.world.level.block.Block.class,
                "legacy survey console registry shell must remain a plain inert block"
        );
        helper.assertFalse(
                IPMCreativeTabs.mainTabItemsForDiagnostics().stream()
                        .anyMatch(stack -> stack.is(IPMBlocks.SURVEY_CONSOLE.get().asItem())),
                "legacy survey console registry shell must stay hidden from the creative tab"
        );
        helper.assertTrue(itemEntities.isEmpty(), "legacy survey console registry shell must not create item outputs");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_persists_basic_nbt(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK2.get()));
        controller.getGarageUpgradeInventory().setStackInSlot(1, new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get()));

        DockingControllerBlockEntity loaded = saveAndReload(helper, controller);

        helper.assertValueEqual(
                2,
                loaded.getGarageUpgradeItemCountForDiagnostics(),
                "garage upgrade inventory should persist through NBT"
        );
        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(1).is(IPMBlocks.CORE_SAMPLING_UPGRADE.get()),
                "core sampling upgrade should survive NBT reload"
        );
        helper.assertValueEqual(
                IndustrialDiggerMiningProfile.MK2,
                loaded.getInstalledMiningProfileForDiagnostics().orElse(null),
                "MK2 bore head should survive NBT reload as selected mining profile"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_migrates_legacy_core_sampling_slot(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get()));

        DockingControllerBlockEntity loaded = saveAndReload(helper, controller);

        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(0).isEmpty(),
                "legacy core sampling upgrade should move out of bore head slot"
        );
        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(1).is(IPMBlocks.CORE_SAMPLING_UPGRADE.get()),
                "legacy core sampling upgrade should load into core sampling slot"
        );
        helper.assertFalse(
                loaded.getInstalledMiningProfileForDiagnostics().isPresent(),
                "migrated core sampling upgrade should not select a mining profile"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_restores_legacy_short_nbt_size(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get()));

        CompoundTag saved = controller.saveWithFullMetadata(registries);
        saved.getCompound("GarageUpgrades").putInt("Size", 1);

        DockingControllerBlockEntity loaded = new DockingControllerBlockEntity(
                helper.absolutePos(CONTROLLER_POS),
                IPMBlocks.DOCKING_CONTROLLER.get()
                        .defaultBlockState()
                        .setValue(DockingControllerBlock.FACING, Direction.NORTH)
        );
        loaded.loadWithComponents(saved, registries);

        helper.assertValueEqual(
                DockingControllerBlockEntity.GARAGE_UPGRADE_SLOTS,
                loaded.getGarageUpgradeInventory().getSlots(),
                "legacy short NBT size should restore the full garage upgrade slot count"
        );
        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(0).isEmpty(),
                "legacy short NBT should still migrate core sampling out of the bore head slot"
        );
        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(1).is(IPMBlocks.CORE_SAMPLING_UPGRADE.get()),
                "legacy short NBT should restore the core sampling slot before normalization"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_quarantines_oversized_nbt_size(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag saved = controller.saveWithFullMetadata(registries);
        CompoundTag upgrades = saved.getCompound("GarageUpgrades");
        upgrades.putInt("Size", DockingControllerBlockEntity.GARAGE_UPGRADE_SLOTS + 1);
        ListTag items = upgrades.getList("Items", Tag.TAG_COMPOUND);
        CompoundTag hidden = new CompoundTag();
        hidden.putInt("Slot", DockingControllerBlockEntity.GARAGE_UPGRADE_SLOTS);
        items.add(new ItemStack(IPMBlocks.BORE_HEAD_MK1.get(), 4).save(registries, hidden));
        upgrades.put("Items", items);
        assertQuarantinedRoundTrip(helper, saved, "GarageUpgrades");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_preserves_excess_counts(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        var registries = helper.getLevel().registryAccess();
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK2.get(), 5));
        controller.getGarageUpgradeInventory().setStackInSlot(1, new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get(), 3));
        assertQuarantinedRoundTrip(helper, controller.saveWithFullMetadata(registries), "GarageUpgrades");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_preserves_negative_nbt_size(GameTestHelper helper) {
        var controller = placeController(helper);
        var registries = helper.getLevel().registryAccess();
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK2.get()));
        var saved = controller.saveWithFullMetadata(registries);
        saved.getCompound("GarageUpgrades").putInt("Size", -1);
        assertQuarantinedRoundTrip(helper, saved, "GarageUpgrades");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_inventory_recovers_crossed_legacy_upgrade_slots(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get()));
        controller.getGarageUpgradeInventory().setStackInSlot(1, new ItemStack(IPMBlocks.BORE_HEAD_MK1.get()));

        DockingControllerBlockEntity loaded = saveAndReload(helper, controller);

        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(0).is(IPMBlocks.BORE_HEAD_MK1.get()),
                "crossed legacy bore head should load into the bore head slot"
        );
        helper.assertTrue(
                loaded.getGarageUpgradeInventory().getStackInSlot(1).is(IPMBlocks.CORE_SAMPLING_UPGRADE.get()),
                "crossed legacy core sampling upgrade should load into the core sampling slot"
        );
        helper.assertValueEqual(
                2,
                loaded.getGarageUpgradeItemCountForDiagnostics(),
                "crossed legacy upgrades should both survive load normalization"
        );
        helper.assertValueEqual(
                IndustrialDiggerMiningProfile.MK1,
                loaded.getInstalledMiningProfileForDiagnostics().orElse(null),
                "recovered MK1 bore head should select MK1 mining profile"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void soft_lock_name_clears_when_locked_uuid_is_missing(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag saved = controller.saveWithFullMetadata(registries);
        saved.putString("LockedTunnelDiggerName", "Ghost Tunnel Digger");
        saved.remove("LockedTunnelDigger");

        DockingControllerBlockEntity loaded = new DockingControllerBlockEntity(
                helper.absolutePos(CONTROLLER_POS),
                IPMBlocks.DOCKING_CONTROLLER.get()
                        .defaultBlockState()
                        .setValue(DockingControllerBlock.FACING, Direction.NORTH)
        );
        loaded.loadWithComponents(saved, registries);

        helper.assertFalse(
                loaded.hasSoftLockedDiggerForDiagnostics(),
                "missing locked digger UUID should leave controller unlocked"
        );
        helper.assertValueEqual(
                "Tunnel Digger",
                loaded.getLockedTunnelDiggerName(),
                "missing locked digger UUID should clear stale locked name"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void industrial_digger_mining_profiles_define_mk_sizes(GameTestHelper helper) {
        assertProfile(helper, IndustrialDiggerMiningProfile.MK1, 5, 25);
        assertProfile(helper, IndustrialDiggerMiningProfile.MK2, 7, 49);
        assertProfile(helper, IndustrialDiggerMiningProfile.MK3, 9, 81);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void bore_head_slot_selects_industrial_digger_profile(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);

        helper.assertTrue(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.BORE_HEAD_SLOT,
                        new ItemStack(IPMBlocks.BORE_HEAD_MK3.get())
                ),
                "bore head slot should accept MK3 bore head"
        );
        helper.assertFalse(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.BORE_HEAD_SLOT,
                        new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get())
                ),
                "bore head slot should reject core sampling upgrade"
        );

        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK3.get()));
        helper.assertValueEqual(
                IndustrialDiggerMiningProfile.MK3,
                controller.getInstalledMiningProfileForDiagnostics().orElse(null),
                "MK3 bore head should select MK3 mining profile"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_slots_reject_mismatched_items(GameTestHelper helper) {
        helper.assertTrue(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.BORE_HEAD_SLOT,
                        new ItemStack(IPMBlocks.BORE_HEAD_MK1.get())
                ),
                "bore head slot should accept bore heads"
        );
        helper.assertFalse(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.CORE_SAMPLING_SLOT,
                        new ItemStack(IPMBlocks.BORE_HEAD_MK1.get())
                ),
                "core sampling slot should reject bore heads"
        );
        helper.assertTrue(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.CORE_SAMPLING_SLOT,
                        new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get())
                ),
                "core sampling slot should accept core sampling upgrade"
        );
        helper.assertFalse(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.FUEL_SYSTEM_SLOT,
                        new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get())
                ),
                "fuel system slot should not accept the core sampling upgrade"
        );
        helper.assertFalse(
                DockingControllerBlockEntity.isValidGarageUpgrade(
                        DockingControllerBlockEntity.UTILITY_SLOT,
                        new ItemStack(IPMBlocks.BORE_HEAD_MK2.get())
                ),
                "reserved utility slot should reject bore heads"
        );
        helper.assertFalse(
                DockingControllerBlockEntity.isValidGarageUpgrade(-1, new ItemStack(IPMBlocks.CORE_SAMPLING_UPGRADE.get())),
                "invalid slot index should reject upgrade items"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maintenance_snapshot_keeps_locked_digger_name_when_detection_changes(GameTestHelper helper) {
        UUID lockedDiggerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID detectedDiggerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TunnelDiggerDockingDetector.Candidate detectedDigger = new TunnelDiggerDockingDetector.Candidate(
                detectedDiggerId,
                "Detected Tunnel Digger",
                new TunnelDiggerDockingDetector.LocalPosition(0.0D, 0.0D, -4.0D),
                Direction.NORTH,
                0.0D,
                true,
                true
        );
        TunnelDiggerDockingDetector.DetectionResult detection = new TunnelDiggerDockingDetector.DetectionResult(
                TunnelDiggerDockingDetector.DetectionStatus.ALIGNED,
                1,
                detectedDigger,
                new AABB(BlockPos.ZERO)
        );

        DockingMaintenanceSnapshot snapshot = DockingMaintenanceSnapshot.create(
                CONTROLLER_POS,
                true,
                "garage valid",
                detection,
                lockedDiggerId,
                "Locked Tunnel Digger",
                0,
                0,
                "empty",
                0,
                0,
                "empty",
                null
        );

        helper.assertValueEqual(
                "Locked Tunnel Digger",
                snapshot.lockedTunnelDiggerText(),
                "locked digger display should not be replaced by a different detected candidate"
        );
        helper.assertValueEqual(
                lockedDiggerId,
                snapshot.lockedTunnelDigger(),
                "snapshot should retain the locked digger UUID"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maintenance_snapshot_normalizes_malformed_numeric_state(GameTestHelper helper) {
        DockingMaintenanceSnapshot snapshot = new DockingMaintenanceSnapshot(
                CONTROLLER_POS,
                true,
                "garage valid",
                DockingMaintenanceSnapshot.DetectionDisplayStatus.ALIGNED,
                "aligned",
                true,
                null,
                "",
                -25,
                -1,
                "immersivepetroleum:diesel",
                20_000,
                16_000,
                "immersivepetroleum:lubricant",
                "",
                -9,
                -81,
                -40
        );

        helper.assertFalse(snapshot.softLocked(), "snapshot should not report soft lock without a UUID");
        helper.assertValueEqual("none", snapshot.lockedTunnelDiggerText(), "missing locked UUID should show no digger");
        helper.assertValueEqual(0, snapshot.fuelAmount(), "negative fuel amount should clamp to zero");
        helper.assertValueEqual(0, snapshot.fuelCapacity(), "negative fuel capacity should clamp to zero");
        helper.assertValueEqual("empty", snapshot.fuelFluidId(), "empty fuel amount should report empty fluid");
        helper.assertValueEqual(16_000, snapshot.lubricantAmount(), "lubricant amount should clamp to capacity");
        helper.assertValueEqual(16_000, snapshot.lubricantCapacity(), "valid lubricant capacity should survive");
        helper.assertValueEqual("No bore head", snapshot.miningProfileName(), "blank profile should use safe display text");
        helper.assertValueEqual(0, snapshot.miningFootprintWidth(), "negative mining width should clamp to zero");
        helper.assertValueEqual(0, snapshot.miningBlocksPerLayer(), "disabled mining profile should clear block count");
        helper.assertValueEqual(0, snapshot.miningCycleTicks(), "disabled mining profile should clear cycle time");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maintenance_snapshot_labels_positive_amount_without_fluid_id_as_unknown(GameTestHelper helper) {
        DockingMaintenanceSnapshot snapshot = new DockingMaintenanceSnapshot(
                CONTROLLER_POS,
                true,
                "garage valid",
                DockingMaintenanceSnapshot.DetectionDisplayStatus.ALIGNED,
                "aligned",
                false,
                null,
                "none",
                500,
                16_000,
                "",
                250,
                16_000,
                "empty",
                "MK1",
                5,
                25,
                80
        );

        helper.assertValueEqual("unknown", snapshot.fuelFluidId(), "positive fuel amount must not display as empty");
        helper.assertValueEqual(
                "unknown",
                snapshot.lubricantFluidId(),
                "positive lubricant amount must not display as empty"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maintenance_snapshot_clears_mining_metrics_when_profile_name_is_missing(GameTestHelper helper) {
        DockingMaintenanceSnapshot snapshot = new DockingMaintenanceSnapshot(
                CONTROLLER_POS,
                true,
                "garage valid",
                DockingMaintenanceSnapshot.DetectionDisplayStatus.ALIGNED,
                "aligned",
                false,
                null,
                "none",
                0,
                0,
                "empty",
                0,
                0,
                "empty",
                "",
                5,
                25,
                80
        );

        helper.assertValueEqual("No bore head", snapshot.miningProfileName(), "blank profile should use safe display text");
        helper.assertValueEqual(0, snapshot.miningFootprintWidth(), "missing mining profile should clear footprint");
        helper.assertValueEqual(0, snapshot.miningBlocksPerLayer(), "missing mining profile should clear block count");
        helper.assertValueEqual(0, snapshot.miningCycleTicks(), "missing mining profile should clear cycle time");
        helper.assertValueEqual("No bore head", snapshot.miningProfileText(), "missing profile should not display dimensions");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maintenance_snapshot_normalizes_blocks_per_layer_to_square_footprint(GameTestHelper helper) {
        DockingMaintenanceSnapshot snapshot = new DockingMaintenanceSnapshot(
                CONTROLLER_POS,
                true,
                "garage valid",
                DockingMaintenanceSnapshot.DetectionDisplayStatus.ALIGNED,
                "aligned",
                false,
                null,
                "none",
                0,
                0,
                "empty",
                0,
                0,
                "empty",
                "MK1",
                5,
                999,
                80
        );

        helper.assertValueEqual(25, snapshot.miningBlocksPerLayer(), "5x5 footprint should report 25 blocks/layer");
        helper.assertValueEqual(
                "MK1 5x5 (25 blocks/layer)",
                snapshot.miningProfileText(),
                "mining profile text should not expose malformed block counts"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maintenance_menu_seeds_client_bore_slot_from_snapshot_profile(GameTestHelper helper) {
        DockingMaintenanceSnapshot snapshot = DockingMaintenanceSnapshot.create(
                CONTROLLER_POS,
                true,
                "garage valid",
                null,
                null,
                "none",
                0,
                0,
                "empty",
                0,
                0,
                "empty",
                IndustrialDiggerMiningProfile.MK2
        );
        ItemStackHandler clientUpgradeInventory =
                DockingMaintenanceMenu.createClientUpgradeInventoryForDiagnostics(snapshot);
        DockingMaintenanceMenu menu = new DockingMaintenanceMenu(
                1,
                new Inventory(null),
                clientUpgradeInventory,
                snapshot
        );

        helper.assertTrue(
                clientUpgradeInventory.getStackInSlot(DockingControllerBlockEntity.BORE_HEAD_SLOT)
                        .is(IPMBlocks.BORE_HEAD_MK2.get()),
                "client menu should seed the bore head slot from the server snapshot before slot sync"
        );
        helper.assertValueEqual(
                "MK2 7x7 (49 blocks/layer)",
                menu.selectedMiningProfileText(),
                "initial client render should show the server-selected mining profile"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fluid_tank_persists_basic_nbt_if_safe(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        Fluid diesel = BuiltInRegistries.FLUID.get(DIESEL_ID);
        Fluid lubricant = BuiltInRegistries.FLUID.get(LUBRICANT_ID);
        if (diesel == Fluids.EMPTY || lubricant == Fluids.EMPTY) {
            LOGGER.warn(
                    "Skipping IPM fluid persistence diagnostic: required fluids unavailable, diesel={}, lubricant={}",
                    diesel,
                    lubricant
            );
            helper.succeed();
            return;
        }

        int fuelFilled = controller.getFuelPortHandler().fill(
                new FluidStack(diesel, 500),
                IFluidHandler.FluidAction.EXECUTE
        );
        int lubricantFilled = controller.getLubricantPortHandler().fill(
                new FluidStack(lubricant, 250),
                IFluidHandler.FluidAction.EXECUTE
        );
        helper.assertValueEqual(500, fuelFilled, "diesel fill should be accepted");
        helper.assertValueEqual(250, lubricantFilled, "lubricant fill should be accepted");

        DockingControllerBlockEntity loaded = saveAndReload(helper, controller);

        helper.assertValueEqual(500, loaded.getFuelAmountForDiagnostics(), "fuel tank should persist through NBT");
        helper.assertValueEqual(
                250,
                loaded.getLubricantAmountForDiagnostics(),
                "lubricant tank should persist through NBT"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fluid_tanks_clear_invalid_loaded_fluid_nbt(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag saved = controller.saveWithFullMetadata(registries);
        putSavedFluid(saved, "FuelTank", new FluidStack(Fluids.WATER, 500), registries);
        putSavedFluid(saved, "LubricantTank", new FluidStack(Fluids.WATER, 250), registries);

        DockingControllerBlockEntity loaded = loadControllerFromTag(helper, saved, registries);

        helper.assertValueEqual(0, loaded.getFuelAmountForDiagnostics(), "invalid loaded fuel should be cleared");
        helper.assertValueEqual(0, loaded.getLubricantAmountForDiagnostics(), "invalid loaded lubricant should be cleared");
        helper.assertValueEqual("empty", loaded.getFuelFluidId(), "invalid loaded fuel id should reset to empty");
        helper.assertValueEqual("empty", loaded.getLubricantFluidId(), "invalid loaded lubricant id should reset to empty");
        assertQuarantinedRoundTrip(helper, saved, "FuelTank");
        assertQuarantinedRoundTrip(helper, saved, "LubricantTank");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fluid_tanks_clamp_overcapacity_loaded_fluid_nbt(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        Fluid diesel = BuiltInRegistries.FLUID.get(DIESEL_ID);
        Fluid lubricant = BuiltInRegistries.FLUID.get(LUBRICANT_ID);
        if (diesel == Fluids.EMPTY || lubricant == Fluids.EMPTY) {
            LOGGER.warn(
                    "Skipping IPM overcapacity fluid NBT diagnostic: required fluids unavailable, diesel={}, lubricant={}",
                    diesel,
                    lubricant
            );
            helper.succeed();
            return;
        }

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag saved = controller.saveWithFullMetadata(registries);
        putSavedFluid(saved, "FuelTank", new FluidStack(diesel, controller.getFuelCapacity() + 1_000), registries);
        putSavedFluid(
                saved,
                "LubricantTank",
                new FluidStack(lubricant, controller.getLubricantCapacity() + 1_000),
                registries
        );

        DockingControllerBlockEntity loaded = loadControllerFromTag(helper, saved, registries);

        helper.assertValueEqual(0, loaded.getFuelAmount(), "overcapacity fuel is quarantined, not clamped");
        helper.assertValueEqual(0, loaded.getLubricantAmount(), "overcapacity lubricant is quarantined, not clamped");
        assertQuarantinedRoundTrip(helper, saved, "FuelTank");
        assertQuarantinedRoundTrip(helper, saved, "LubricantTank");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void recovery_preserves_unknown_and_malformed_tags(GameTestHelper helper) {
        var controller = placeController(helper);
        var registries = helper.getLevel().registryAccess();
        var saved = controller.saveWithFullMetadata(registries);
        saved.putString("FuelTank", "opaque-unknown-tank-schema");
        saved.getCompound("GarageUpgrades").putString("FutureField", "must survive");
        assertQuarantinedRoundTrip(helper, saved, "FuelTank");
        assertQuarantinedRoundTrip(helper, saved, "GarageUpgrades");
        saved.putString("RecoveryQuarantine", "future-recovery-schema");
        assertQuarantinedRoundTrip(helper, saved, "RecoveryQuarantine");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void recovery_preserves_duplicate_and_unknown_item_entries(GameTestHelper helper) {
        var controller = placeController(helper);
        var registries = helper.getLevel().registryAccess();
        controller.getGarageUpgradeInventory().setStackInSlot(0, new ItemStack(IPMBlocks.BORE_HEAD_MK1.get()));
        var saved = controller.saveWithFullMetadata(registries);
        var items = saved.getCompound("GarageUpgrades").getList("Items", Tag.TAG_COMPOUND);
        items.add(items.getCompound(0).copy());
        assertQuarantinedRoundTrip(helper, saved, "GarageUpgrades");
        items.remove(1);
        items.getCompound(0).putString("id", "missing_test_mod:unresolved_item");
        assertQuarantinedRoundTrip(helper, saved, "GarageUpgrades");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void recovery_blocks_break_without_protecting_empty_controller(GameTestHelper helper) {
        var controller = placeController(helper);
        var level = helper.getLevel();
        var pos = helper.absolutePos(CONTROLLER_POS);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var normal = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(normal);
        helper.assertFalse(normal.isCanceled(), "ordinary empty controller remains breakable");
        var saved = controller.saveWithFullMetadata(level.registryAccess());
        saved.putString("FuelTank", "unresolved-test-data");
        controller.loadWithComponents(saved, level.registryAccess());
        var protectedBreak = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(protectedBreak);
        helper.assertTrue(protectedBreak.isCanceled(), "quarantined controller break must be cancelled");
        helper.assertTrue(controller.hasRecoveryQuarantine(), "break attempt must not consume recovery data");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void recovery_survives_explosion_destruction_phase(GameTestHelper helper) {
        var controller = placeController(helper);
        var level = helper.getLevel();
        var pos = helper.absolutePos(CONTROLLER_POS);
        var neighbor = pos.above();
        level.setBlock(neighbor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var saved = controller.saveWithFullMetadata(level.registryAccess());
        saved.putString("FuelTank", "unresolved-explosion-data");
        controller.loadWithComponents(saved, level.registryAccess());
        var before = controller.saveWithFullMetadata(level.registryAccess()).get("RecoveryQuarantine").copy();
        var explosion = new net.minecraft.world.level.Explosion(level, null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F, false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY,
                new java.util.ArrayList<>(java.util.List.of(pos, neighbor)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ExplosionEvent.Detonate(level, explosion, new java.util.ArrayList<>()));
        helper.assertFalse(explosion.getToBlow().contains(pos), "protected controller removed from destruction list");
        helper.assertTrue(explosion.getToBlow().contains(neighbor), "unrelated explosion targets preserved");
        explosion.finalizeExplosion(false);
        helper.assertTrue(level.getBlockEntity(pos) == controller, "controller must survive destruction phase");
        helper.assertTrue(level.isEmptyBlock(neighbor), "ordinary neighbor must still be destroyed");
        helper.assertTrue(before.equals(controller.saveWithFullMetadata(level.registryAccess()).get("RecoveryQuarantine")),
                "explosion must not mutate recovery payload");
        helper.succeed();
    }


    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void garage_upgrade_blocks_normal_break_without_drops(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.getGarageUpgradeInventory().setStackInSlot(
                DockingControllerBlockEntity.BORE_HEAD_SLOT,
                new ItemStack(IPMBlocks.BORE_HEAD_MK1.get()));

        assertProtectedControllerBreakBlocked(helper, controller,
                "a controller with a normal bore-head upgrade must refuse player breaking");
        helper.assertTrue(
                controller.getGarageUpgradeInventory().getStackInSlot(DockingControllerBlockEntity.BORE_HEAD_SLOT)
                        .is(IPMBlocks.BORE_HEAD_MK1.get()),
                "blocked breaking must retain the installed bore head");
        helper.assertValueEqual(1,
                controller.getGarageUpgradeInventory().getStackInSlot(DockingControllerBlockEntity.BORE_HEAD_SLOT)
                        .getCount(),
                "blocked breaking must not duplicate the installed bore head");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fuel_tank_blocks_normal_break_without_drops(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        Fluid diesel = BuiltInRegistries.FLUID.get(DIESEL_ID);
        helper.assertTrue(diesel != Fluids.EMPTY, "Immersive Petroleum diesel must be registered for this test");
        helper.assertValueEqual(500,
                controller.getFuelPortHandler().fill(new FluidStack(diesel, 500), IFluidHandler.FluidAction.EXECUTE),
                "fuel fill should be accepted");

        assertProtectedControllerBreakBlocked(helper, controller,
                "a controller with stored fuel must refuse player breaking");
        helper.assertValueEqual(500, controller.getFuelAmountForDiagnostics(),
                "blocked breaking must retain the exact fuel amount");
        helper.assertValueEqual("immersivepetroleum:diesel", controller.getFuelFluidId(),
                "blocked breaking must retain the exact fuel identity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void lubricant_tank_blocks_normal_break_without_drops(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        Fluid lubricant = BuiltInRegistries.FLUID.get(LUBRICANT_ID);
        helper.assertTrue(lubricant != Fluids.EMPTY, "Immersive Petroleum lubricant must be registered for this test");
        helper.assertValueEqual(250,
                controller.getLubricantPortHandler().fill(
                        new FluidStack(lubricant, 250), IFluidHandler.FluidAction.EXECUTE),
                "lubricant fill should be accepted");

        assertProtectedControllerBreakBlocked(helper, controller,
                "a controller with stored lubricant must refuse player breaking");
        helper.assertValueEqual(250, controller.getLubricantAmountForDiagnostics(),
                "blocked breaking must retain the exact lubricant amount");
        helper.assertValueEqual("immersivepetroleum:lubricant", controller.getLubricantFluidId(),
                "blocked breaking must retain the exact lubricant identity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void soft_locked_controller_blocks_normal_break_without_drops(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        controller.softLock(UUID.fromString("00000000-0000-0000-0000-000000000123"), "Test Digger");

        assertProtectedControllerBreakBlocked(helper, controller,
                "a controller soft-locked to a Digger must refuse player breaking");
        helper.assertTrue(controller.isSoftLocked(), "blocked breaking must retain the Digger soft lock");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void normal_controller_contents_survive_explosion_without_drops(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        var level = helper.getLevel();
        var pos = helper.absolutePos(CONTROLLER_POS);
        var neighbor = pos.above();
        level.setBlock(neighbor, Blocks.GLASS.defaultBlockState(), 3);
        installNormalControllerContents(helper, controller);

        var explosion = new net.minecraft.world.level.Explosion(level, null,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F, false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY,
                new java.util.ArrayList<>(java.util.List.of(pos, neighbor)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ExplosionEvent.Detonate(level, explosion, new java.util.ArrayList<>()));

        helper.assertFalse(explosion.getToBlow().contains(pos),
                "a controller with normal Garage contents must be removed from the explosion destruction list");
        helper.assertTrue(explosion.getToBlow().contains(neighbor),
                "unrelated explosion targets must remain in the destruction list");
        explosion.finalizeExplosion(false);

        assertControllerUnreplaced(helper);
        assertNormalControllerContents(helper, controller, "after explosion");
        assertNoControllerDrops(helper);
        helper.assertTrue(level.isEmptyBlock(neighbor), "unrelated glass must still be destroyed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void normal_controller_contents_survive_save_reload_without_duplication(GameTestHelper helper) {
        DockingControllerBlockEntity controller = placeController(helper);
        installNormalControllerContents(helper, controller);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        CompoundTag saved = controller.saveWithFullMetadata(registries);
        CompoundTag original = saved.copy();
        DockingControllerBlockEntity loaded = loadControllerFromTag(helper, saved, registries);
        helper.assertTrue(saved.equals(original), "loading must not mutate the source controller NBT");
        assertNormalControllerContents(helper, loaded, "after first reload");

        CompoundTag secondSave = loaded.saveWithFullMetadata(registries);
        loaded.loadWithComponents(secondSave, registries);
        assertNormalControllerContents(helper, loaded, "after repeated load on the same instance");

        DockingControllerBlockEntity reloaded = loadControllerFromTag(
                helper, loaded.saveWithFullMetadata(registries), registries);
        assertNormalControllerContents(helper, reloaded, "after second fresh reload");
        helper.succeed();
    }

    private static void assertQuarantinedRoundTrip(GameTestHelper helper, CompoundTag source, String key) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag original = source.copy();
        var loaded = loadControllerFromTag(helper, source, registries);
        var firstSave = loaded.saveWithFullMetadata(registries);
        ListTag archive = firstSave.getList("RecoveryQuarantine", Tag.TAG_COMPOUND);
        boolean found = false;
        for (Tag element : archive) {
            CompoundTag entry = (CompoundTag) element;
            if (key.equals(entry.getString("Source")) && source.get(key).equals(entry.get("Raw"))) found = true;
        }
        helper.assertTrue(found, "raw source must survive quarantine without truncation: " + key);
        helper.assertTrue(source.equals(original), "load must not mutate supplied source NBT");
        helper.assertTrue(loaded.hasProtectedStateForFormation(), "quarantine must prevent destructive formation");
        if (key.equals("GarageUpgrades")) helper.assertValueEqual(0, loaded.getGarageUpgradeItemCount(), "quarantine must not duplicate active upgrades");
        if (key.equals("FuelTank")) helper.assertValueEqual(0, loaded.getFuelAmount(), "quarantine must not duplicate active fuel");
        if (key.equals("LubricantTank")) helper.assertValueEqual(0, loaded.getLubricantAmount(), "quarantine must not duplicate active lubricant");
        loaded.loadWithComponents(firstSave, registries);
        CompoundTag secondSave = loaded.saveWithFullMetadata(registries);
        helper.assertTrue(archive.equals(secondSave.get("RecoveryQuarantine")), "repeated load/save must not replay or grow quarantine");
    }


    private static void assertProtectedControllerBreakBlocked(
            GameTestHelper helper,
            DockingControllerBlockEntity controller,
            String assertion
    ) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CONTROLLER_POS);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var event = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                level, pos, level.getBlockState(pos), player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), assertion);
        helper.assertTrue(level.getBlockEntity(pos) == controller,
                "blocked breaking must retain the same Docking Controller block entity");
        assertNoControllerDrops(helper);
    }

    private static void assertNoControllerDrops(GameTestHelper helper) {
        var pos = helper.absolutePos(CONTROLLER_POS);
        helper.assertTrue(
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0)).isEmpty(),
                "protected controller contents must not be materialized as item drops");
    }

    private static void installNormalControllerContents(
            GameTestHelper helper,
            DockingControllerBlockEntity controller
    ) {
        Fluid diesel = BuiltInRegistries.FLUID.get(DIESEL_ID);
        Fluid lubricant = BuiltInRegistries.FLUID.get(LUBRICANT_ID);
        helper.assertTrue(diesel != Fluids.EMPTY, "Immersive Petroleum diesel must be registered for this test");
        helper.assertTrue(lubricant != Fluids.EMPTY, "Immersive Petroleum lubricant must be registered for this test");

        controller.getGarageUpgradeInventory().setStackInSlot(
                DockingControllerBlockEntity.BORE_HEAD_SLOT,
                new ItemStack(IPMBlocks.BORE_HEAD_MK1.get()));
        helper.assertValueEqual(500,
                controller.getFuelPortHandler().fill(new FluidStack(diesel, 500), IFluidHandler.FluidAction.EXECUTE),
                "normal fuel contents should be accepted");
        helper.assertValueEqual(250,
                controller.getLubricantPortHandler().fill(
                        new FluidStack(lubricant, 250), IFluidHandler.FluidAction.EXECUTE),
                "normal lubricant contents should be accepted");
    }

    private static void assertNormalControllerContents(
            GameTestHelper helper,
            DockingControllerBlockEntity controller,
            String context
    ) {
        ItemStack boreHead = controller.getGarageUpgradeInventory()
                .getStackInSlot(DockingControllerBlockEntity.BORE_HEAD_SLOT);
        helper.assertTrue(boreHead.is(IPMBlocks.BORE_HEAD_MK1.get()),
                context + ": bore-head upgrade identity must be preserved");
        helper.assertValueEqual(1, boreHead.getCount(),
                context + ": bore-head upgrade must remain a single item");
        helper.assertValueEqual(500, controller.getFuelAmountForDiagnostics(),
                context + ": fuel amount must remain exact");
        helper.assertValueEqual("immersivepetroleum:diesel", controller.getFuelFluidId(),
                context + ": fuel identity must remain exact");
        helper.assertValueEqual(250, controller.getLubricantAmountForDiagnostics(),
                context + ": lubricant amount must remain exact");
        helper.assertValueEqual("immersivepetroleum:lubricant", controller.getLubricantFluidId(),
                context + ": lubricant identity must remain exact");
        helper.assertFalse(controller.hasRecoveryQuarantine(),
                context + ": valid normal contents must not regress into quarantine");
        helper.assertTrue(controller.hasProtectedStateForDestruction(),
                context + ": valid normal contents must remain protected from destruction");
    }

    private static DockingControllerBlockEntity placeController(GameTestHelper helper) {
        BlockState state = IPMBlocks.DOCKING_CONTROLLER.get()
                .defaultBlockState()
                .setValue(DockingControllerBlock.FACING, Direction.NORTH);
        helper.setBlock(CONTROLLER_POS, state);
        BlockEntity blockEntity = helper.getBlockEntity(CONTROLLER_POS);
        helper.assertTrue(
                blockEntity instanceof DockingControllerBlockEntity,
                "Docking Controller block entity should be present"
        );
        return (DockingControllerBlockEntity) blockEntity;
    }

    private static void assertProfile(
            GameTestHelper helper,
            IndustrialDiggerMiningProfile profile,
            int expectedWidth,
            int expectedBlocks
    ) {
        helper.assertValueEqual(expectedWidth, profile.footprintWidth(), profile.id() + " width");
        helper.assertValueEqual(expectedBlocks, profile.blocksPerLayer(), profile.id() + " block count");
        List<BlockPos> targets = profile.targetPositions(BlockPos.ZERO, Direction.NORTH);
        helper.assertValueEqual(expectedBlocks, targets.size(), profile.id() + " target count");
        helper.assertValueEqual(
                expectedBlocks,
                targets.stream().distinct().toList().size(),
                profile.id() + " unique target count"
        );
        helper.assertTrue(
                targets.contains(new BlockPos(-profile.radius(), -profile.radius(), -1)),
                profile.id() + " should include lower-left face corner"
        );
        helper.assertTrue(
                targets.contains(new BlockPos(profile.radius(), profile.radius(), -1)),
                profile.id() + " should include upper-right face corner"
        );
    }

    private static void assertControllerUnreplaced(GameTestHelper helper) {
        helper.assertBlockPresent(IPMBlocks.DOCKING_CONTROLLER.get(), CONTROLLER_POS);
        helper.assertBlockNotPresent(Blocks.AIR, CONTROLLER_POS);
        helper.assertTrue(
                helper.getBlockEntity(CONTROLLER_POS) instanceof DockingControllerBlockEntity,
                "Docking Controller block entity should remain present"
        );
    }

    private static DockingControllerBlockEntity saveAndReload(
            GameTestHelper helper,
            DockingControllerBlockEntity controller
    ) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag saved = controller.saveWithFullMetadata(registries);
        return loadControllerFromTag(helper, saved, registries);
    }

    private static DockingControllerBlockEntity loadControllerFromTag(
            GameTestHelper helper,
            CompoundTag saved,
            HolderLookup.Provider registries
    ) {
        DockingControllerBlockEntity loaded = new DockingControllerBlockEntity(
                helper.absolutePos(CONTROLLER_POS),
                IPMBlocks.DOCKING_CONTROLLER.get()
                        .defaultBlockState()
                        .setValue(DockingControllerBlock.FACING, Direction.NORTH)
        );
        loaded.loadWithComponents(saved, registries);
        return loaded;
    }

    private static void putSavedFluid(
            CompoundTag saved,
            String tankTagName,
            FluidStack fluidStack,
            HolderLookup.Provider registries
    ) {
        CompoundTag tankTag = new CompoundTag();
        tankTag.put("Fluid", fluidStack.save(registries));
        saved.put(tankTagName, tankTag);
    }
}
