package com.emman.immersivepetromachinery.recovery;

import com.emman.immersivepetromachinery.ImmersivePetroMachinery;
import com.emman.immersivepetromachinery.block.entity.DockingControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Refuse ordinary destruction while controller-owned state has no safe drop or migration path. */
@EventBusSubscriber(modid = ImmersivePetroMachinery.MOD_ID)
public final class RecoveryProtectionEvents {
    private RecoveryProtectionEvents() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getLevel().getBlockEntity(event.getPos()) instanceof DockingControllerBlockEntity controller
                && controller.hasProtectedStateForDestruction()) {
            event.setCanceled(true);
            Component message = controller.hasRecoveryQuarantine()
                    ? Component.translatable("message.immersive_petro_machinery.recovery_protected")
                    : Component.translatable(
                            "message.immersive_petro_machinery.controller_protected",
                            controller.protectedFormedStateSummary());
            event.getPlayer().displayClientMessage(message, false);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        event.getAffectedBlocks().removeIf(pos ->
                event.getLevel().getBlockEntity(pos) instanceof DockingControllerBlockEntity controller
                        && controller.hasProtectedStateForDestruction());
    }
}
