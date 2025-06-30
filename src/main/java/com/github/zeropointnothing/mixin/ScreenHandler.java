package com.github.zeropointnothing.mixin;

import com.github.zeropointnothing.ConfigLoader;
import com.github.zeropointnothing.ItemsBegone;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(net.minecraft.screen.ScreenHandler.class)
public abstract class ScreenHandler {
    @Shadow @Final private List<ScreenHandlerListener> listeners;

    // TAIL event fires AFTER player clicks, allowing us to monitor PICK UP events
    // to be more specific, it utilizes the fact that the item is in the user's cursor at this point
    @Inject(method = "onSlotClick", at = @At("TAIL"), cancellable = true)
    private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        String team = ItemsBegone.getTeam(player);

        ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
        ItemsBegone.LOGGER.info("onSlotClick fired. {}", cursorStack.toString());

        boolean blacklist = ItemsBegone.isBlacklisted(cursorStack, team);

        if (blacklist) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            if (!ConfigLoader.CONFIG.delete_on_deny) {
                player.currentScreenHandler.getSlot(slotIndex).insertStack(cursorStack);
            }
            ci.cancel();
        }
    }

    // HEAD event fires BEFORE player clicks, allowing us to monitor PUT DOWN events/shift-clicking
    // to be more specific, it utilizes the fact that the item is likely still in its slot at this point
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void onSlotClickAfter(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        String team = ItemsBegone.getTeam(player);
        ItemStack cursorStack;

        try {
            cursorStack = player.currentScreenHandler.getSlot(slotIndex).getStack();
        } catch (IndexOutOfBoundsException e) {
            return;
        }
        ItemsBegone.LOGGER.info("onSlotClick (after) fired. {}", cursorStack.toString());

        boolean blacklist = ItemsBegone.isBlacklisted(cursorStack, team);

        if (blacklist) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            if (!ConfigLoader.CONFIG.delete_on_deny) {
                player.currentScreenHandler.getSlot(slotIndex).insertStack(cursorStack);
            }
            ci.cancel();
        }
    }
}
