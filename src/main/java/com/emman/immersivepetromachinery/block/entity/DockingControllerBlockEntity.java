package com.emman.immersivepetromachinery.block.entity;

import com.emman.immersivepetromachinery.mining.IndustrialDiggerMiningProfile;
import com.emman.immersivepetromachinery.fluid.DockFluidFilters;
import com.emman.immersivepetromachinery.fluid.FillOnlyFluidHandler;
import com.emman.immersivepetromachinery.registry.IPMBlockEntities;
import com.emman.immersivepetromachinery.registry.IPMBlocks;
import com.emman.immersivepetromachinery.registry.IPMTags;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class DockingControllerBlockEntity extends BlockEntity {
    public static final int BORE_HEAD_SLOT = 0;
    public static final int CORE_SAMPLING_SLOT = 1;
    public static final int FUEL_SYSTEM_SLOT = 2;
    public static final int LUBRICANT_SYSTEM_SLOT = 3;
    public static final int UTILITY_SLOT = 4;
    public static final int GARAGE_UPGRADE_SLOTS = 5;

    private static final String LOCKED_TUNNEL_DIGGER_TAG = "LockedTunnelDigger";
    private static final String LOCKED_TUNNEL_DIGGER_NAME_TAG = "LockedTunnelDiggerName";
    private static final String FUEL_TANK_TAG = "FuelTank";
    private static final String LUBRICANT_TANK_TAG = "LubricantTank";
    private static final String GARAGE_UPGRADES_TAG = "GarageUpgrades";
    private static final String RECOVERY_TAG = "RecoveryQuarantine";
    private ListTag recoveryQuarantine = new ListTag();
    private static final int DOCK_TANK_CAPACITY = 16_000;
    private static final int GARAGE_UPGRADE_SLOT_LIMIT = 1;

    @Nullable
    private UUID lockedTunnelDigger;
    private String lockedTunnelDiggerName = "";
    private final FluidTank fuelTank = new FluidTank(DOCK_TANK_CAPACITY, DockFluidFilters::isFuel) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank lubricantTank = new FluidTank(DOCK_TANK_CAPACITY, DockFluidFilters::isLubricant) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler garageUpgradeInventory = new ItemStackHandler(GARAGE_UPGRADE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isValidGarageUpgrade(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return GARAGE_UPGRADE_SLOT_LIMIT;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IFluidHandler fuelPortHandler = new FillOnlyFluidHandler(fuelTank);
    private final IFluidHandler lubricantPortHandler = new FillOnlyFluidHandler(lubricantTank);

    public DockingControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(IPMBlockEntities.DOCKING_CONTROLLER.get(), pos, blockState);
    }

    public boolean isSoftLocked() {
        return lockedTunnelDigger != null;
    }

    public Optional<UUID> getLockedTunnelDigger() {
        return Optional.ofNullable(lockedTunnelDigger);
    }

    public String getLockedTunnelDiggerName() {
        return lockedTunnelDiggerName.isBlank() ? "Tunnel Digger" : lockedTunnelDiggerName;
    }

    public void softLock(UUID tunnelDiggerUuid, String tunnelDiggerName) {
        String displayName = tunnelDiggerName.isBlank() ? "Tunnel Digger" : tunnelDiggerName;
        if (!tunnelDiggerUuid.equals(lockedTunnelDigger)) {
            lockedTunnelDigger = tunnelDiggerUuid;
            lockedTunnelDiggerName = displayName;
            setChanged();
        } else if (!displayName.equals(lockedTunnelDiggerName)) {
            lockedTunnelDiggerName = displayName;
            setChanged();
        }
    }

    public boolean clearSoftLock() {
        if (lockedTunnelDigger == null) {
            return false;
        }

        lockedTunnelDigger = null;
        lockedTunnelDiggerName = "";
        setChanged();
        return true;
    }

    public IFluidHandler getFuelPortHandler() {
        return fuelPortHandler;
    }

    public IFluidHandler getLubricantPortHandler() {
        return lubricantPortHandler;
    }

    public int getFuelAmount() {
        return fuelTank.getFluidAmount();
    }

    public int getFuelCapacity() {
        return fuelTank.getCapacity();
    }

    public String getFuelFluidId() {
        return DockFluidFilters.fluidId(fuelTank.getFluid());
    }

    public int getLubricantAmount() {
        return lubricantTank.getFluidAmount();
    }

    public int getLubricantCapacity() {
        return lubricantTank.getCapacity();
    }

    public String getLubricantFluidId() {
        return DockFluidFilters.fluidId(lubricantTank.getFluid());
    }

    public ItemStackHandler getGarageUpgradeInventory() {
        return garageUpgradeInventory;
    }

    public Optional<IndustrialDiggerMiningProfile> getInstalledMiningProfile() {
        return IndustrialDiggerMiningProfile.fromBoreHead(garageUpgradeInventory.getStackInSlot(BORE_HEAD_SLOT));
    }

    public Optional<IndustrialDiggerMiningProfile> getInstalledMiningProfileForDiagnostics() {
        return getInstalledMiningProfile();
    }

    public boolean hasRecoveryQuarantine() {
        return !recoveryQuarantine.isEmpty();
    }

    public boolean hasProtectedFormedState() {
        return lockedTunnelDigger != null
                || fuelTank.getFluidAmount() > 0
                || lubricantTank.getFluidAmount() > 0
                || hasGarageUpgradeItems()
                || !recoveryQuarantine.isEmpty();
    }

    public boolean hasProtectedStateForFormation() {
        return hasProtectedFormedState();
    }

    /**
     * Controller-owned state has no safe block-item drop path; refuse ordinary destruction instead of losing it.
     */
    public boolean hasProtectedStateForDestruction() {
        return hasProtectedFormedState();
    }

    public String protectedFormedStateSummary() {
        StringBuilder summary = new StringBuilder();
        appendProtectedState(summary, lockedTunnelDigger != null, "locked Tunnel Digger");
        appendProtectedState(summary, fuelTank.getFluidAmount() > 0, "fuel tank");
        appendProtectedState(summary, lubricantTank.getFluidAmount() > 0, "lubricant tank");
        appendProtectedState(summary, hasGarageUpgradeItems(), "garage upgrades");
        appendProtectedState(summary, !recoveryQuarantine.isEmpty(), "quarantined save data");
        return summary.isEmpty() ? "none" : summary.toString();
    }

    public int getGarageUpgradeItemCount() {
        int count = 0;
        for (int slot = 0; slot < garageUpgradeInventory.getSlots(); slot++) {
            if (!garageUpgradeInventory.getStackInSlot(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public int getGarageUpgradeItemCountForDiagnostics() {
        return getGarageUpgradeItemCount();
    }

    public int getFuelAmountForDiagnostics() {
        return fuelTank.getFluidAmount();
    }

    public int getLubricantAmountForDiagnostics() {
        return lubricantTank.getFluidAmount();
    }

    public boolean hasSoftLockedDiggerForDiagnostics() {
        return lockedTunnelDigger != null;
    }

    public static boolean isValidGarageUpgrade(int slot, ItemStack stack) {
        if (slot < 0 || slot >= GARAGE_UPGRADE_SLOTS) {
            return false;
        }

        if (stack.isEmpty()) {
            return true;
        }

        return switch (slot) {
            case BORE_HEAD_SLOT -> stack.is(IPMTags.BORE_HEADS);
            case CORE_SAMPLING_SLOT -> stack.is(IPMBlocks.CORE_SAMPLING_UPGRADE.get());
            case FUEL_SYSTEM_SLOT, LUBRICANT_SYSTEM_SLOT, UTILITY_SLOT -> stack.is(IPMTags.DIGGER_UPGRADES)
                    && !stack.is(IPMTags.BORE_HEADS)
                    && !stack.is(IPMBlocks.CORE_SAMPLING_UPGRADE.get());
            default -> false;
        };
    }

    private boolean hasGarageUpgradeItems() {
        for (int slot = 0; slot < garageUpgradeInventory.getSlots(); slot++) {
            if (!garageUpgradeInventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void appendProtectedState(StringBuilder summary, boolean present, String label) {
        if (!present) {
            return;
        }

        if (!summary.isEmpty()) {
            summary.append(", ");
        }
        summary.append(label);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lockedTunnelDigger = tag.hasUUID(LOCKED_TUNNEL_DIGGER_TAG)
                ? tag.getUUID(LOCKED_TUNNEL_DIGGER_TAG)
                : null;
        lockedTunnelDiggerName = lockedTunnelDigger == null ? "" : tag.getString(LOCKED_TUNNEL_DIGGER_NAME_TAG);
        recoveryQuarantine = new ListTag();
        Tag existingRecovery = tag.get(RECOVERY_TAG);
        if (existingRecovery instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND)) {
            recoveryQuarantine = list.copy();
        } else if (existingRecovery != null) {
            quarantine(RECOVERY_TAG, existingRecovery);
        }
        loadTankPreserving(registries, tag.get(FUEL_TANK_TAG), fuelTank, true, FUEL_TANK_TAG);
        loadTankPreserving(registries, tag.get(LUBRICANT_TANK_TAG), lubricantTank, false, LUBRICANT_TANK_TAG);
        // Reset on every load, including repeated loadWithComponents on the same instance.
        for (int slot = 0; slot < GARAGE_UPGRADE_SLOTS; slot++) {
            garageUpgradeInventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        Tag rawUpgrades = tag.get(GARAGE_UPGRADES_TAG);
        if (rawUpgrades != null) {
            if (canLoadUpgradesWithoutLoss(registries, rawUpgrades)) {
                loadGarageUpgradeInventory(registries, (CompoundTag) rawUpgrades);
            } else {
                quarantine(GARAGE_UPGRADES_TAG, rawUpgrades);
            }
        }
    }

    /** Opaque, non-operational archive. Never automatically restore or expose via capabilities. */
    private void quarantine(String source, Tag raw) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Source", source);
        entry.put("Raw", raw.copy());
        recoveryQuarantine.add(entry);
        setChanged();
    }

    private void loadTankPreserving(HolderLookup.Provider registries, @Nullable Tag raw,
            FluidTank tank, boolean fuel, String source) {
        tank.setFluid(FluidStack.EMPTY);
        if (raw == null) return;
        if (!(raw instanceof CompoundTag compound)) {
            quarantine(source, raw);
            return;
        }
        try {
            tank.readFromNBT(registries, compound);
            FluidStack fluid = tank.getFluid();
            CompoundTag encoded = new CompoundTag();
            tank.writeToNBT(registries, encoded);
            boolean valid = fluid.isEmpty() || (fuel ? DockFluidFilters.isFuel(fluid) : DockFluidFilters.isLubricant(fluid));
            if (valid && fluid.getAmount() <= tank.getCapacity() && encoded.equals(compound)) return;
        } catch (RuntimeException malformedData) {
            // Preserve the original even if a codec cannot read a missing mod/component.
        }
        tank.setFluid(FluidStack.EMPTY);
        quarantine(source, raw);
    }

    private boolean canLoadUpgradesWithoutLoss(HolderLookup.Provider registries, Tag raw) {
        if (!(raw instanceof CompoundTag compound)) return false;
        if (!compound.getAllKeys().stream().allMatch(key -> key.equals("Size") || key.equals("Items"))) return false;
        if (compound.contains("Size") && (!compound.contains("Size", Tag.TAG_INT)
                || compound.getInt("Size") < 0 || compound.getInt("Size") > GARAGE_UPGRADE_SLOTS)) return false;
        if (!compound.contains("Items")) return true;
        if (!(compound.get("Items") instanceof ListTag items)
                || (!items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND)) return false;
        boolean[] seen = new boolean[GARAGE_UPGRADE_SLOTS];
        try {
            for (Tag element : items) {
                CompoundTag itemTag = (CompoundTag) element;
                if (!itemTag.contains("Slot", Tag.TAG_INT)) return false;
                int slot = itemTag.getInt("Slot");
                if (slot < 0 || slot >= GARAGE_UPGRADE_SLOTS || seen[slot]) return false;
                seen[slot] = true;
                Optional<ItemStack> parsed = ItemStack.parse(registries, itemTag);
                if (parsed.isEmpty() || parsed.get().isEmpty()
                        || parsed.get().getCount() > GARAGE_UPGRADE_SLOT_LIMIT) return false;
                CompoundTag canonical = new CompoundTag();
                canonical.putInt("Slot", slot);
                if (!parsed.get().save(registries, canonical).equals(itemTag)) return false;
            }
        } catch (RuntimeException malformedData) {
            return false;
        }
        return true;
    }

    private void loadGarageUpgradeInventory(HolderLookup.Provider registries, CompoundTag upgradesTag) {
        int serializedSlots = upgradesTag.contains("Size", Tag.TAG_INT)
                ? upgradesTag.getInt("Size")
                : GARAGE_UPGRADE_SLOTS;
        CompoundTag safeTag = upgradesTag.copy();
        safeTag.putInt("Size", GARAGE_UPGRADE_SLOTS);
        garageUpgradeInventory.deserializeNBT(registries, safeTag);
        normalizeLoadedGarageUpgradeSlots();

        if (serializedSlots != GARAGE_UPGRADE_SLOTS) {
            setChanged();
        }
    }

    private void normalizeLoadedGarageUpgradeSlots() {
        for (int slot = 0; slot < garageUpgradeInventory.getSlots(); slot++) {
            ItemStack stack = garageUpgradeInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (isValidGarageUpgrade(slot, stack)) {
                continue;
            }

            if (moveLoadedGarageUpgradeToEmptyValidSlot(slot, stack)) {
                continue;
            }

            swapLoadedGarageUpgradeWithInvalidSlot(slot, stack);
        }
    }

    private boolean moveLoadedGarageUpgradeToEmptyValidSlot(int sourceSlot, ItemStack stack) {
        ItemStack limitedStack = stack;
        for (int targetSlot = 0; targetSlot < garageUpgradeInventory.getSlots(); targetSlot++) {
            if (targetSlot == sourceSlot
                    || !garageUpgradeInventory.getStackInSlot(targetSlot).isEmpty()
                    || !isValidGarageUpgrade(targetSlot, limitedStack)) {
                continue;
            }

            garageUpgradeInventory.setStackInSlot(targetSlot, limitedStack.copy());
            garageUpgradeInventory.setStackInSlot(sourceSlot, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private boolean swapLoadedGarageUpgradeWithInvalidSlot(int sourceSlot, ItemStack stack) {
        ItemStack limitedStack = stack;
        for (int targetSlot = 0; targetSlot < garageUpgradeInventory.getSlots(); targetSlot++) {
            ItemStack targetStack = garageUpgradeInventory.getStackInSlot(targetSlot);
            ItemStack limitedTargetStack = targetStack;
            if (targetSlot == sourceSlot
                    || targetStack.isEmpty()
                    || isValidGarageUpgrade(targetSlot, targetStack)
                    || !isValidGarageUpgrade(targetSlot, limitedStack)
                    || !isValidGarageUpgrade(sourceSlot, limitedTargetStack)) {
                continue;
            }

            garageUpgradeInventory.setStackInSlot(targetSlot, limitedStack.copy());
            garageUpgradeInventory.setStackInSlot(sourceSlot, limitedTargetStack.copy());
            return true;
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!recoveryQuarantine.isEmpty()) {
            tag.put(RECOVERY_TAG, recoveryQuarantine.copy());
        }

        if (lockedTunnelDigger != null) {
            tag.putUUID(LOCKED_TUNNEL_DIGGER_TAG, lockedTunnelDigger);
            if (!lockedTunnelDiggerName.isBlank()) {
                tag.putString(LOCKED_TUNNEL_DIGGER_NAME_TAG, lockedTunnelDiggerName);
            }
        }

        CompoundTag fuelTag = new CompoundTag();
        fuelTank.writeToNBT(registries, fuelTag);
        if (!fuelTag.isEmpty()) {
            tag.put(FUEL_TANK_TAG, fuelTag);
        }

        CompoundTag lubricantTag = new CompoundTag();
        lubricantTank.writeToNBT(registries, lubricantTag);
        if (!lubricantTag.isEmpty()) {
            tag.put(LUBRICANT_TANK_TAG, lubricantTag);
        }

        CompoundTag upgradesTag = garageUpgradeInventory.serializeNBT(registries);
        if (!upgradesTag.isEmpty()) {
            tag.put(GARAGE_UPGRADES_TAG, upgradesTag);
        }
    }
}
