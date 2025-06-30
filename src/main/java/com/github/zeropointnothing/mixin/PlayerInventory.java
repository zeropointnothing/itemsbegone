package com.github.zeropointnothing.mixin;

import com.github.zeropointnothing.ItemsBegone;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.entity.player.PlayerInventory.class)
public class PlayerInventory {
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("TAIL"))
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = this.player;

        ItemsBegone.checkInventory(player);
    }
}
