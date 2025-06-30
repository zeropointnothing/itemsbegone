package com.github.zeropointnothing.mixin;

import com.github.zeropointnothing.ItemsBegone;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.screen.slot.Slot.class)
public class Slot {
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        ItemsBegone.checkInventory(player);
    }
}
