package com.katt.changedextras.client.renderer.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

public class JammerHeadphonesRenderer implements AccessoryRenderer {
    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            AccessorySlotContext<T> slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffer,
            int light, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {
        ModelPart head = getHeadPart(renderLayerParent.getModel());
        if (head == null) {
            return;
        }

        poseStack.pushPose();
        head.translateAndRotate(poseStack);
        poseStack.translate(0.0D, -0.24D, 0.0D);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                slotContext.stack(),
                ItemDisplayContext.HEAD,
                light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                slotContext.wearer().level(),
                0
        );
        poseStack.popPose();
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void renderFirstPersonOnArms(
            AccessorySlotContext<T> slotContext, PoseStack matrixStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource renderTypeBuffer,
            int light, HumanoidArm arm, PartPose armPose, float partialTicks) {
    }

    private static ModelPart getHeadPart(EntityModel<?> model) {
        if (model instanceof AdvancedHumanoidModel<?> advancedModel) {
            return advancedModel.getHead();
        }
        if (model instanceof HumanoidModel<?> humanoidModel) {
            return humanoidModel.head;
        }
        return null;
    }
}
