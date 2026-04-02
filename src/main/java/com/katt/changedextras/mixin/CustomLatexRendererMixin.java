package com.katt.changedextras.mixin;

import com.katt.changedextras.client.ArtistTintManager;
import net.ltxprogrammer.changed.client.renderer.CustomLatexRenderer;
import net.ltxprogrammer.changed.entity.beast.CustomLatexEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CustomLatexRenderer.class)
public class CustomLatexRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/ltxprogrammer/changed/entity/beast/CustomLatexEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedextras$overrideCustomLatexTexture(CustomLatexEntity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation override = ArtistTintManager.getTexture(entity);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
