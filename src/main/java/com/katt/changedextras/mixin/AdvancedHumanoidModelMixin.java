package com.katt.changedextras.mixin;

import com.katt.changedextras.common.LatexCuddleHelper;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.TorsoedModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancedHumanoidModel.class)
public abstract class AdvancedHumanoidModelMixin<T extends ChangedEntity> {
    @Inject(method = "setupAnim(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFF)V", at = @At("TAIL"), remap = false)
    private void changedextras$applyCuddlePose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!LatexCuddleHelper.shouldCuddle(entity)) {
            return;
        }

        AdvancedHumanoidModel<T> model = (AdvancedHumanoidModel<T>)(Object)this;
        ModelPart head = ((HeadedModel)model).getHead();
        ModelPart torso = ((TorsoedModel)model).getTorso();
        ModelPart rightArm = model.getArm(HumanoidArm.RIGHT);
        ModelPart leftArm = model.getArm(HumanoidArm.LEFT);
        ModelPart rightLeg = model.getLeg(HumanoidArm.RIGHT);
        ModelPart leftLeg = model.getLeg(HumanoidArm.LEFT);
        if (head == null || torso == null || rightArm == null || leftArm == null || rightLeg == null || leftLeg == null) {
            return;
        }

        float rollWave = (float)Math.sin(ageInTicks * 0.22F) * 0.08F;
        torso.zRot = 1.15F + rollWave;
        torso.xRot = 0.2F;
        torso.yRot = 0.0F;

        head.xRot = 0.35F;
        head.yRot = -0.18F + rollWave * 0.3F;
        head.zRot = 1.05F + rollWave * 0.5F;

        rightArm.xRot = -1.4F;
        rightArm.yRot = -0.2F;
        rightArm.zRot = 0.45F;
        leftArm.xRot = -1.15F;
        leftArm.yRot = 0.3F;
        leftArm.zRot = -0.3F;

        rightLeg.xRot = -1.25F + rollWave;
        rightLeg.yRot = 0.1F;
        rightLeg.zRot = 0.22F;
        leftLeg.xRot = -1.45F - rollWave;
        leftLeg.yRot = -0.08F;
        leftLeg.zRot = -0.18F;
    }
}
