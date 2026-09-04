package com.katt.changedextras.common.ai;

import com.katt.changedextras.Config;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LatexMind {
    @Nullable public UUID targetId;
    @Nullable public UUID retaliationTargetId;
    @Nullable public BlockPos lastSeenPos;
    @Nullable public Vec3 lastSeenEye;
    @Nullable public BlockPos plannedBuildPos;
    @Nullable public BlockPos plannedBreakPos;
    @Nullable public BlockPos activeBreakPos;
    @Nullable public BlockPos cachedPathTargetPos;
    @Nullable public BlockPos cachedPathSourcePos;
    @Nullable public BlockPos cachedTerrainTargetPos;
    @Nullable public BlockPos cachedTerrainSourcePos;
    @Nullable public BlockPos cachedTerrainBreakPos;
    @Nullable public BlockPos cachedTerrainBuildPos;
    @Nullable public LatexBrain.State cachedTerrainState;
    public List<BlockPos> imaginedBuildPath = new ArrayList<>();
    public List<BlockPos> cachedImaginedBuildPath = new ArrayList<>();

    // Grudge memory of ally killers (UUID -> tick expire timestamp)
    public final Map<UUID, Integer> grudgeKillers = new HashMap<>();
    public int enrageTicks = 0;

    // Curiosity memory
    @Nullable public UUID curiosityTargetId;
    public int curiosityTicks = 0;
    public int curiosityCooldown = 0;
    public int curiositySubState = 0;

    // Survival / hiding state
    public boolean isHiding = false;
    @Nullable public BlockPos hidePos;
    public int hideTicks = 0;
    public int healCooldown = 0;

    // Parkour state
    public boolean isParkouring = false;
    @Nullable public BlockPos parkourLandingPos = null;
    @Nullable public BlockPos parkourTakeoffPos = null;
    public int parkourTicks = 0;
    public int parkourCooldown = 0;

    // Sprint jumping state
    public boolean isSprintJumping = false;
    public int sprintJumpTicks = 0;

    // Bow cooldown
    public int bowCooldown = 0;

    // Ranged (bow) attack state
    public int bowChargeTicks = 0;
    public int rangedAttackCooldown = 0;

    // Current AI state
    public LatexBrain.State state = LatexBrain.State.IDLE;

    // Combat strafe timing
    public int combatStrafeDir = 1; // 1 = left, -1 = right
    public int combatStrafeTimer = 0;

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
    public int recentBreakTicks = 0;
    public int cachedPathTick = -10000;
    public int cachedTerrainTick = -10000;
    public int retaliationExpireTick = -10000;

    public boolean hasLOS = false;
    public boolean pathFailed = false;
    public boolean cachedReachablePath = false;

    public final LatexBrain brain = new LatexBrain();
    public Vec3 lastSeenTargetEyePos;
    public BlockPos lastSeenTargetPos;
    public int attackCooldown;
    public float blockBreakProgress;

    public void tick(ChangedEntity mob) {
        brain.tick(mob, this);
    }

    public void remember(LivingEntity target, int tick, boolean los) {
        boolean targetChanged = targetId == null || !targetId.equals(target.getUUID());
        targetId = target.getUUID();
        lastSeenPos = target.blockPosition();
        lastSeenEye = target.getEyePosition();
        lastSeenTick = tick;
        hasLOS = los;
        lostSightTicks = 0;
        searchTicks = 0;
        lastSeenTargetPos = lastSeenPos;
        lastSeenTargetEyePos = lastSeenEye;
        if (targetChanged) {
            plannedBuildPos = null;
            plannedBreakPos = null;
            activeBreakPos = null;
            blockBreakProgress = 0.0F;
            imaginedBuildPath.clear();
            invalidateNavigationCaches();
        }
    }

    public void markRetaliationTarget(LivingEntity target, int tick) {
        retaliationTargetId = target.getUUID();
        retaliationExpireTick = tick + Math.max(20, Config.latexAttackerMemoryTicks);
    }

    public boolean isRetaliationTarget(ChangedEntity mob, LivingEntity target) {
        return retaliationTargetId != null
                && retaliationTargetId.equals(target.getUUID())
                && mob.tickCount <= retaliationExpireTick;
    }

    public void addGrudgeKiller(UUID killerId, int expireTick) {
        grudgeKillers.put(killerId, expireTick);
    }

    public boolean isGrudgeKiller(UUID killerId, int currentTick) {
        Integer expireTick = grudgeKillers.get(killerId);
        if (expireTick == null) {
            return false;
        }
        if (currentTick > expireTick) {
            grudgeKillers.remove(killerId);
            return false;
        }
        return true;
    }

    public void triggerEnrage(int ticks) {
        this.enrageTicks = Math.max(this.enrageTicks, ticks);
    }

    public boolean isEnraged() {
        return enrageTicks > 0;
    }

    public boolean remembersRecentTarget(ChangedEntity mob) {
        int memoryTicks = Config.latexAttackerMemoryTicks;
        return memoryTicks > 0 && lastSeenPos != null && mob.tickCount - lastSeenTick <= memoryTicks;
    }

    public void clearTarget() {
        targetId = null;
        retaliationTargetId = null;
        lastSeenPos = null;
        lastSeenEye = null;
        lastSeenTargetPos = null;
        lastSeenTargetEyePos = null;
        lastSeenTick = -10000;
        lostSightTicks = 0;
        searchTicks = 0;
        chaseTime = 0;
        noPathTicks = 0;
        recentBreakTicks = 0;
        hasLOS = false;
        pathFailed = false;
        plannedBuildPos = null;
        plannedBreakPos = null;
        activeBreakPos = null;
        blockBreakProgress = 0.0F;
        imaginedBuildPath.clear();
        isParkouring = false;
        parkourLandingPos = null;
        parkourTakeoffPos = null;
        parkourTicks = 0;
        isSprintJumping = false;
        sprintJumpTicks = 0;
        bowCooldown = 0;
        bowChargeTicks = 0;
        state = LatexBrain.State.IDLE;
        invalidateNavigationCaches();
    }

    public void invalidateNavigationCaches() {
        cachedPathTargetPos = null;
        cachedPathSourcePos = null;
        cachedTerrainTargetPos = null;
        cachedTerrainSourcePos = null;
        cachedTerrainBreakPos = null;
        cachedTerrainBuildPos = null;
        cachedTerrainState = null;
        cachedImaginedBuildPath.clear();
        cachedPathTick = -10000;
        cachedTerrainTick = -10000;
        cachedReachablePath = false;
        retaliationExpireTick = -10000;
    }
}