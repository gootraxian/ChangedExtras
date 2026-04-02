package com.katt.changedextras.mixin;

import com.katt.changedextras.client.ArtistTintManager;
import com.katt.changedextras.common.LatexCuddleHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.CustomLatexEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancedHumanoidRenderer.class)
public class AdvancedHumanoidRendererMixin {
    @Inject(method = "render(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), remap = false)
    private void changedextras$applyCustomLatexTint(ChangedEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof CustomLatexEntity)) {
            return;
        }

        if (ArtistTintManager.getTexture(entity) != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        Integer tint = ArtistTintManager.getTint(entity);
        if (tint == null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }

        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(red, green, blue, 1.0F);
    }

    @Inject(method = "render(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"), remap = false)
    private void changedextras$resetCustomLatexTint(ChangedEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof CustomLatexEntity) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Inject(method = "setupRotations(Lnet/ltxprogrammer/changed/entity/ChangedEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("TAIL"), remap = false)
    private void changedextras$applyCuddleRoll(ChangedEntity entity, PoseStack poseStack, float bob, float bodyRotation, float partialTick, CallbackInfo ci) {
        if (!LatexCuddleHelper.shouldCuddle(entity)) {
            return;
        }

        Player owner = LatexCuddleHelper.getTamingOwner(entity);
        if (owner == null) {
            return;
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(LatexCuddleHelper.getCuddleYaw(owner) - bodyRotation));
        poseStack.translate(0.0D, 0.14D, 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(88.0F));
    }
}
