package com.katt.changedextras.mixin;

import com.katt.changedextras.client.ArtistTintManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    @Inject(method = "render", at = @At("HEAD"))
    private void changedextras$applyArtistTint(T entity, float entityYaw, float partialTicks, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof ChangedEntity && ArtistTintManager.getTexture(entity) != null) {
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

    @Inject(method = "render", at = @At("TAIL"))
    private void changedextras$resetArtistTint(T entity, float entityYaw, float partialTicks, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void changedextras$setupPlayerDeathRotation(T entity, PoseStack poseStack, float bob, float bodyRotation, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof Player) || entity.deathTime <= 0) {
            return;
        }

        if (entity.isFullyFrozen()) {
            bodyRotation += (float)(Math.cos(entity.tickCount * 3.25D) * Math.PI * 0.4000000059604645D);
        }

        if (!entity.hasPose(Pose.SLEEPING)) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRotation));
        }

        float fallProgress = ((entity.deathTime + partialTick - 1.0F) / 20.0F) * 1.6F;
        fallProgress = Mth.sqrt(fallProgress);
        if (fallProgress > 1.0F) {
            fallProgress = 1.0F;
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(-fallProgress * 90.0F));
        ci.cancel();
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void changedextras$removePlayerDeathRedOverlay(LivingEntity entity, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (!(entity instanceof Player) || entity.deathTime <= 0) {
            return;
        }

        cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress), OverlayTexture.v(false)));
    }
}
