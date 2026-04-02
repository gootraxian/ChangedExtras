package com.katt.changedextras.common.ai;

import com.katt.changedextras.Config;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public final class LatexMind {
    @Nullable public UUID targetId;
    @Nullable public BlockPos lastSeenPos;
    @Nullable public Vec3 lastSeenEye;
    @Nullable public BlockPos plannedBuildPos;
    @Nullable public BlockPos plannedBreakPos;

    public int lastSeenTick = -10000;
    public int stuckTicks = 0;
    public int lostSightTicks = 0;
    public int chaseTime = 0;
    public int noPathTicks = 0;
    public int blockBreakTicks = 0;
    public int breakCooldown = 0;
    public int buildCooldown = 0;
    public int clutchCooldown = 0;
    public int lootScanCooldown = 0;
    public int equipmentScanCooldown = 0;
    public int searchTicks = 0;
    public int jumpCooldown = 0;
    public int allyAlertCooldown = 0;

    public boolean hasLOS = false;
    public boolean pathFailed = false;

    public final LatexBrain brain = new LatexBrain();
    public Vec3 lastSeenTargetEyePos;
    public BlockPos lastSeenTargetPos;
    public int attackCooldown;

    public void tick(ChangedEntity mob) {
        brain.tick(mob, this);
    }

    public void remember(LivingEntity target, int tick, boolean los) {
        targetId = target.getUUID();
        lastSeenPos = target.blockPosition();
        lastSeenEye = target.getEyePosition();
        lastSeenTick = tick;
        hasLOS = los;
        lostSightTicks = 0;
        searchTicks = 0;
        lastSeenTargetPos = lastSeenPos;
        lastSeenTargetEyePos = lastSeenEye;
        plannedBuildPos = null;
        plannedBreakPos = null;
    }

    public boolean remembersRecentTarget(ChangedEntity mob) {
        int memoryTicks = Config.latexAttackerMemoryTicks;
        return memoryTicks > 0 && lastSeenPos != null && mob.tickCount - lastSeenTick <= memoryTicks;
    }

    public void clearTarget() {
        targetId = null;
        lastSeenPos = null;
        lastSeenEye = null;
        lastSeenTargetPos = null;
        lastSeenTargetEyePos = null;
        lastSeenTick = -10000;
        lostSightTicks = 0;
        searchTicks = 0;
        chaseTime = 0;
        noPathTicks = 0;
        hasLOS = false;
        pathFailed = false;
        plannedBuildPos = null;
        plannedBreakPos = null;
    }
}
