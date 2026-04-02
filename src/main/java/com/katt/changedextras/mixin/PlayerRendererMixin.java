package com.katt.changedextras.mixin;

import com.katt.changedextras.client.ArtistTintManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void changedextras$overridePlayerTexture(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation override = ArtistTintManager.getTexture(player);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
