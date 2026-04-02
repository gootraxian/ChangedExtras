package com.katt.changedextras.mixin;

import com.katt.changedextras.client.ArtistTintManager;
import com.katt.changedextras.entity.beasts.ArtistEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.EyeStyle;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomEyesLayer.class)
public class CustomEyesLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFFF)V", at = @At("HEAD"), remap = false)
    private void changedextras$resetEyesTint(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ChangedEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof ArtistEntity) {
            BasicPlayerInfo info = entity.getBasicPlayerInfo();
            if (info.getEyeStyle() != EyeStyle.TALL) {
                info.setEyeStyle(EyeStyle.TALL);
            }
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFFF)V", at = @At("TAIL"), remap = false)
    private void changedextras$restoreBodyTint(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ChangedEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        Integer tint = ArtistTintManager.getTint(entity.getId());
        if (tint == null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(red, green, blue, 1.0F);
    }
}
