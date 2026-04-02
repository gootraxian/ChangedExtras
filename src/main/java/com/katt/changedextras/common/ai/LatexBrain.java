package com.katt.changedextras.common.ai;

import com.katt.changedextras.common.LatexCuddleHelper;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

public class LatexBrain {
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> CHANGED_HUMANOIDS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "humanoids"));
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> CHANGED_LATEXES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "latexes"));
    private static final double ATTACK_RANGE = 2.9D;
    private static final double CLOSE_PRESSURE_RANGE = 7.5D;
    private static final double SEARCH_REACH = 1.75D;
    private static final double LOOT_RANGE = 10.0D;
    private static final double PICKUP_RANGE = 2.2D;
    private static final float DIRECT_WALK_SPEED = 2.05F;
    private static final float DIRECT_RUN_SPEED = 2.35F;
    private static final double SPRINT_JUMP_BOOST = 0.9D;
    private static final double BREAK_PATH_STEP = 0.5D;
    private static final int BREAK_PATH_SCAN_BLOCKS = 16;
    private static final int TERRAIN_COMMIT_TICKS = 10;
    private static final int BUILD_BLOCK_RESERVE = 4;
    private static final int SEARCH_TIMEOUT = 80;
    private static final int BLOCK_BREAK_COMMIT = 18;
    private static final int ATTACK_COOLDOWN = 10;

    private enum State {
        IDLE,
        CHASE,
        ATTACK,
        BREAK,
        BUILD,
        SEARCH,
        LOOT,
        REPOSITION
    }

    private record TerrainPlan(@Nullable BlockPos breakPos, @Nullable BlockPos buildPos, double breakCost, double buildCost) {}
    private record WaterBucketSource(ItemStack stack, @Nullable InteractionHand hand, int inventorySlot) {}

    private State state = State.IDLE;
    private int thinkCooldown = 0;

    public void tick(ChangedEntity mob, LatexMind mind) {
        tickCooldowns(mind);
        equipBestArmor(mob, mind);
        stopShieldingIfIdle(mob);

        if (tryWaterClutch(mob, mind)) {
            return;
        }

        if (shouldDropTarget(mob, mind)) {
            mob.setTarget(null);
            mind.clearTarget();
        }

        LivingEntity target = resolveTarget(mob, mind);
        if (target != null && target.isAlive()) {
            boolean los = mob.hasLineOfSight(target);
            mind.remember(target, mob.tickCount, los);
            if (!los) {
                mind.lostSightTicks++;
            }
            mind.chaseTime++;
        } else if (mind.remembersRecentTarget(mob)) {
            mind.lostSightTicks++;
            mind.searchTicks++;
        } else {
            mind.clearTarget();
        }

        updateStuck(mob, mind);

        if (thinkCooldown-- <= 0) {
            thinkCooldown = 2;
            state = decideState(mob, mind, target);
        }

        switch (state) {
            case CHASE -> chase(mob, target);
            case ATTACK -> attack(mob, target);
            case BREAK -> breakObstacle(mob, mind, target);
            case BUILD -> buildTowardTarget(mob, mind, target);
            case SEARCH -> search(mob, mind);
            case LOOT -> loot(mob);
            case REPOSITION -> reposition(mob, mind, target);
            default -> idle(mob);
        }
    }

    private void tickCooldowns(LatexMind mind) {
        if (mind.attackCooldown > 0) mind.attackCooldown--;
        if (mind.breakCooldown > 0) mind.breakCooldown--;
        if (mind.buildCooldown > 0) mind.buildCooldown--;
        if (mind.clutchCooldown > 0) mind.clutchCooldown--;
        if (mind.jumpCooldown > 0) mind.jumpCooldown--;
        if (mind.lootScanCooldown > 0) mind.lootScanCooldown--;
        if (mind.equipmentScanCooldown > 0) mind.equipmentScanCooldown--;
        if (mind.allyAlertCooldown > 0) mind.allyAlertCooldown--;
    }

    private State decideState(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        if (target != null && target.isAlive()) {
            double distance = mob.distanceTo(target);
            Path path = createReachPath(mob, target);
            boolean hasReachablePath = path != null;

            if (shouldRetreat(mob, target)) {
                return State.REPOSITION;
            }

            if (mind.hasLOS && distance <= ATTACK_RANGE + 0.35D) {
                return State.ATTACK;
            }

            if (shouldTowerUp(mob, target) && shouldBuild(mob, mind, target)) {
                return State.BUILD;
            }

            if (hasReachablePath) {
                mind.plannedBuildPos = null;
                mind.plannedBreakPos = null;
                return State.CHASE;
            }

            if (mind.noPathTicks < TERRAIN_COMMIT_TICKS && mind.stuckTicks < 6) {
                return State.CHASE;
            }

            State terrainState = chooseTerrainAction(mob, mind, target);
            if (terrainState != null) {
                return terrainState;
            }

            return State.CHASE;
        }

        if (mind.remembersRecentTarget(mob) && mind.searchTicks < SEARCH_TIMEOUT) {
            return State.SEARCH;
        }

        return hasInterestingLootNearby(mob) ? State.LOOT : State.IDLE;
    }

    private void idle(ChangedEntity mob) {
        if (mob.getNavigation().isDone()) {
            mob.getMoveControl().strafe(0.0F, 0.0F);
        }
    }

    private void chase(ChangedEntity mob, @Nullable LivingEntity target) {
        if (target == null) return;

        equipBestCombatTool(mob);
        if (canUseDirectChase(mob, target)) {
            applyDirectChaseMovement(mob, target, target.isSprinting() ? DIRECT_RUN_SPEED : DIRECT_WALK_SPEED);
        } else {
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            double speed = target.isSprinting() ? 1.9D : 1.7D;
            Path path = createReachPath(mob, target);
            if (path != null) {
                mob.getNavigation().moveTo(path, speed);
            } else {
                mob.getNavigation().moveTo(target, 1.55D);
            }
            mob.getLookControl().setLookAt(target, 10.0F, 10.0F);
        }
        smartJump(mob, target);
        maybeAlertNearbyLatex(mob, target);
    }

    private void attack(ChangedEntity mob, @Nullable LivingEntity target) {
        if (target == null) return;

        equipBestCombatTool(mob);
        mob.getMoveControl().strafe(0.0F, 0.0F);

        double distance = mob.distanceTo(target);
        mob.getLookControl().setLookAt(target, 16.0F, 16.0F);

        if (distance > ATTACK_RANGE + 0.35D) {
            chase(mob, target);
            return;
        }

        mob.getNavigation().stop();
        applyDirectChaseMovement(mob, target, target.isSprinting() ? DIRECT_RUN_SPEED : DIRECT_WALK_SPEED);

        LatexMind mind = LatexMindStore.get(mob);
        if (mind.attackCooldown > 0) return;

        mob.swing(InteractionHand.MAIN_HAND);
        if (mob.distanceTo(target) <= ATTACK_RANGE + 0.15D) {
            if (target instanceof Player playerTarget && ProcessTransfur.getPlayerTransfurVariant(playerTarget) != null) {
                mob.doHurtTarget(target);
            } else if (!mob.tryTransfurTarget(target)) {
                mob.doHurtTarget(target);
            }

            Vec3 knock = target.position().subtract(mob.position()).normalize().scale(0.25D);
            target.push(knock.x, 0.08D, knock.z);
            mind.attackCooldown = ATTACK_COOLDOWN;
            maybeAlertNearbyLatex(mob, target);
        }
    }

    private void breakObstacle(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        if (target == null) return;

        BlockPos pos = mind.plannedBreakPos != null ? mind.plannedBreakPos : findBreakTarget(mob, target);
        if (pos == null) {
            mind.blockBreakTicks = 0;
            mind.plannedBreakPos = null;
            stopShieldingIfIdle(mob);
            chase(mob, target);
            return;
        }

        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mob.level(), pos) < 0 || mind.breakCooldown > 0 || !canBreakBlock(mob, pos, state)) {
            mind.blockBreakTicks = 0;
            mind.plannedBreakPos = null;
            stopShieldingIfIdle(mob);
            chase(mob, target);
            return;
        }

        mob.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.78D);
        mob.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);

        ItemStack tool = findBestMiningTool(mob, state);
        if (!tool.isEmpty()) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, tool);
        }

        setBestShieldOffhand(mob);
        raiseShieldIfPossible(mob);

        mind.blockBreakTicks++;

        if (mob.level() instanceof ServerLevel server && mind.blockBreakTicks % 4 == 0) {
            int stage = Math.min(9, (mind.blockBreakTicks * 10) / BLOCK_BREAK_COMMIT);
            server.destroyBlockProgress(mob.getId(), pos, stage);
        }

        if (mind.blockBreakTicks >= BLOCK_BREAK_COMMIT) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level().destroyBlock(pos, true, mob);
            mind.blockBreakTicks = 0;
            mind.breakCooldown = 8;
            mind.plannedBreakPos = null;
            stopShieldingIfIdle(mob);
        }
    }

    private void buildTowardTarget(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        if (target == null) return;

        boolean towerMode = shouldTowerUp(mob, target);
        BlockPos placePos = towerMode ? findBuildPlacement(mob, target) :
                (mind.plannedBuildPos != null ? mind.plannedBuildPos : findBuildPlacement(mob, target));
        if (placePos == null) {
            mind.plannedBuildPos = null;
            chase(mob, target);
            return;
        }

        ItemStack blockStack = findBestBuildingBlock(mob);
        if (blockStack.isEmpty()) {
            ItemEntity source = findBestBlockPickup(mob);
            if (source != null) {
                mob.getNavigation().moveTo(source, 0.82D);
                if (mob.distanceTo(source) < PICKUP_RANGE) {
                    pickUpItemToHands(mob, source);
                }
            } else {
                mind.plannedBuildPos = null;
                chase(mob, target);
            }
            return;
        }

        if (!(blockStack.getItem() instanceof BlockItem blockItem)) {
            mind.plannedBuildPos = null;
            chase(mob, target);
            return;
        }

        BlockState placeState = blockItem.getBlock().defaultBlockState();
        boolean towerPlacement = towerMode && isTowerPlacement(mob, target, placePos);
        boolean canPlace = towerPlacement ? canPlaceTowerBlockAt(mob, placePos, placeState) : canPlaceBlockAt(mob, placePos, placeState);
        if (!canPlace) {
            mind.plannedBuildPos = null;
            chase(mob, target);
            return;
        }

        if (!moveNearPlacement(mob, placePos)) {
            mob.getNavigation().moveTo(placePos.getX() + 0.5D, placePos.getY(), placePos.getZ() + 0.5D, 0.82D);
            return;
        }

        if (towerPlacement) {
            if (!handleTowerBuildStep(mob, mind, target, blockStack, placeState)) {
                mind.plannedBuildPos = null;
                chase(mob, target);
            }
            return;
        }

        mob.setItemInHand(InteractionHand.MAIN_HAND, blockStack);
        mob.getLookControl().setLookAt(placePos.getX() + 0.5D, placePos.getY() + 0.5D, placePos.getZ() + 0.5D);
        mob.swing(InteractionHand.MAIN_HAND);

        if (mob.level().setBlock(placePos, placeState, 3)) {
            SoundType sound = placeState.getSoundType();
            mob.level().playSound(null, placePos, sound.getPlaceSound(), mob.getSoundSource(), sound.getVolume(), sound.getPitch());
            consumeOneMatchingItem(mob, blockStack);
            mind.buildCooldown = 10;
            mind.plannedBuildPos = null;
            if (towerPlacement) {
                mind.jumpCooldown = 8;
            } else if (target.getY() > mob.getY() + 1.2D && mob.onGround()) {
                mob.setDeltaMovement(0.0D, 0.42D, 0.0D);
                mob.hasImpulse = true;
                mind.jumpCooldown = 8;
            }
        }
    }

    private boolean handleTowerBuildStep(ChangedEntity mob, LatexMind mind, LivingEntity target, ItemStack blockStack, BlockState placeState) {
        BlockPos towerPos = mob.blockPosition();
        Vec3 center = Vec3.atBottomCenterOf(towerPos);
        double dx = center.x - mob.getX();
        double dz = center.z - mob.getZ();
        double horizontalOffsetSqr = dx * dx + dz * dz;

        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0.0F, 0.0F);
        mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
        mob.getLookControl().setLookAt(target, 8.0F, 8.0F);

        if (horizontalOffsetSqr > 0.03D) {
            mob.getNavigation().moveTo(center.x, mob.getY(), center.z, 0.35D);
            return true;
        }

        if (!canPlaceTowerBlockAt(mob, towerPos, placeState)) {
            return false;
        }

        if (mob.level().getBlockState(towerPos).canBeReplaced()) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, blockStack);
            mob.swing(InteractionHand.MAIN_HAND);
            if (mob.level().setBlock(towerPos, placeState, 3)) {
                SoundType sound = placeState.getSoundType();
                mob.level().playSound(null, towerPos, sound.getPlaceSound(), mob.getSoundSource(), sound.getVolume(), sound.getPitch());
                consumeOneMatchingItem(mob, blockStack);
                mind.buildCooldown = 6;
                mind.plannedBuildPos = null;
            }
        }

        if (mob.onGround() && mind.jumpCooldown == 0) {
            mob.setDeltaMovement(0.0D, 0.42D, 0.0D);
            mob.hasImpulse = true;
            mind.jumpCooldown = 4;
        }

        return true;
    }

    private void search(ChangedEntity mob, LatexMind mind) {
        if (mind.lastSeenPos == null) return;

        mob.getNavigation().moveTo(
                mind.lastSeenPos.getX() + 0.5D,
                mind.lastSeenPos.getY(),
                mind.lastSeenPos.getZ() + 0.5D,
                0.8D
        );

        if (mob.blockPosition().closerThan(mind.lastSeenPos, SEARCH_REACH)) {
            mind.searchTicks = SEARCH_TIMEOUT;
        } else if (mind.stuckTicks > 10) {
            mind.searchTicks += 5;
        }
    }

    private void loot(ChangedEntity mob) {
        ItemEntity item = findBestItemEntity(mob);
        if (item == null) return;

        mob.getNavigation().moveTo(item, 0.82D);
        mob.getLookControl().setLookAt(item, 25.0F, 25.0F);

        if (mob.distanceTo(item) < PICKUP_RANGE) {
            pickUpItemToHands(mob, item);
        }
    }

    private void reposition(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        if (target == null) return;

        Vec3 away = mob.position().subtract(target.position());
        if (away.lengthSqr() < 0.001D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 retreat = mob.position().add(away.normalize().scale(4.0D));
        mob.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 0.9D);
        if (mind.jumpCooldown == 0 && mob.isEyeInFluid(FluidTags.WATER)) {
            mob.setDeltaMovement(0.0D, 0.42D, 0.0D);
            mob.hasImpulse = true;
            mind.jumpCooldown = 8;
        }
    }

    private boolean shouldRetreat(ChangedEntity mob, LivingEntity target) {
        return mob.getHealth() < mob.getMaxHealth() * 0.25F
                && mob.distanceTo(target) < 3.0D
                && !mob.hasLineOfSight(target);
    }

    private boolean canUseDirectChase(ChangedEntity mob, LivingEntity target) {
        return mob.hasLineOfSight(target)
                && Math.abs(target.getY() - mob.getY()) < 1.8D
                && !mob.horizontalCollision
                && mob.distanceTo(target) < 18.0D;
    }

    private void applyDirectChaseMovement(ChangedEntity mob, LivingEntity target, float moveSpeed) {
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0.0F, 0.0F);

        float desiredYaw = (float)(Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * (180.0F / Math.PI)) - 90.0F;
        float smoothedYaw = rotlerp(mob.getYRot(), desiredYaw, 8.0F);

        mob.setYRot(smoothedYaw);
        mob.setYBodyRot(smoothedYaw);
        mob.yHeadRot = rotlerp(mob.yHeadRot, smoothedYaw, 10.0F);
        mob.yHeadRotO = mob.yHeadRot;

        mob.setSpeed(moveSpeed);
        mob.setZza(1.0F);
        mob.setXxa(0.0F);
        mob.getLookControl().setLookAt(target, 8.0F, 8.0F);
    }

    private float rotlerp(float current, float target, float maxStep) {
        float delta = Mth.wrapDegrees(target - current);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return current + delta;
    }

    private boolean shouldBuild(ChangedEntity mob, LatexMind mind, LivingEntity target) {
        if (mind.buildCooldown > 0) return false;
        int blockCount = countUsableBuildingBlocks(mob);
        if (blockCount <= 0) return false;
        BlockPos buildPos = findBuildPlacement(mob, target);
        if (buildPos == null) return false;
        int requiredBlocks = estimateBuildBlocksNeeded(mob, target, buildPos);
        return blockCount - requiredBlocks >= BUILD_BLOCK_RESERVE || requiredBlocks <= 1;
    }

    private boolean shouldTowerUp(ChangedEntity mob, LivingEntity target) {
        double verticalGap = target.getY() - mob.getY();
        double horizontalDistSqr = mob.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).lengthSqr();
        return verticalGap > 1.15D && horizontalDistSqr <= 6.25D;
    }

    private boolean isTowerPlacement(ChangedEntity mob, LivingEntity target, BlockPos placePos) {
        return shouldTowerUp(mob, target) && placePos.equals(mob.blockPosition());
    }

    private void pickUpItemToHands(ChangedEntity mob, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        if (tryEquipArmorOrOffhand(mob, stack, itemEntity)) {
            return;
        }

        if (tryMergeIntoHand(mob, InteractionHand.MAIN_HAND, stack)) {
            if (stack.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(stack);
            return;
        }

        if (tryMergeIntoHand(mob, InteractionHand.OFF_HAND, stack)) {
            if (stack.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(stack);
            return;
        }
    }

    private boolean tryEquipArmorOrOffhand(ChangedEntity mob, ItemStack stack, ItemEntity itemEntity) {
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            ItemStack equipped = mob.getItemBySlot(slot);
            if (equipped.isEmpty() || armorValue(stack) > armorValue(equipped)) {
                mob.setItemSlot(slot, stack.copyWithCount(1));
                stack.shrink(1);
                if (stack.isEmpty()) itemEntity.discard();
                else itemEntity.setItem(stack);
                return true;
            }
        }

        if (stack.getItem() instanceof ShieldItem && mob.getOffhandItem().isEmpty()) {
            mob.setItemSlot(EquipmentSlot.OFFHAND, stack.copyWithCount(1));
            stack.shrink(1);
            if (stack.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(stack);
            return true;
        }

        return false;
    }

    private boolean tryMergeIntoHand(ChangedEntity mob, InteractionHand hand, ItemStack stack) {
        ItemStack held = mob.getItemInHand(hand);
        if (held.isEmpty()) {
            mob.setItemInHand(hand, stack.copy());
            stack.setCount(0);
            return true;
        }

        if (ItemStack.isSameItemSameTags(held, stack) && held.getCount() < held.getMaxStackSize()) {
            int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
            held.grow(moved);
            stack.shrink(moved);
            return moved > 0;
        }

        if (itemUtilityScore(stack) > itemUtilityScore(held)) {
            mob.setItemInHand(hand, stack.copy());
            stack.setCount(0);
            return true;
        }

        return false;
    }

    private boolean tryWaterClutch(ChangedEntity mob, LatexMind mind) {
        if (mind.clutchCooldown > 0) return false;
        if (mob.onGround() || mob.isInWater() || mob.isInLava()) return false;
        if (mob.fallDistance < 6.0F || mob.getDeltaMovement().y > -0.9D) return false;

        WaterBucketSource waterBucket = findWaterBucket(mob);
        if (waterBucket == null || waterBucket.stack().isEmpty()) return false;

        BlockPos landingPos = findWaterClutchPos(mob);
        if (landingPos == null) return false;

        mob.getNavigation().stop();
        equipWaterBucketForClutch(mob, waterBucket);
        mob.getLookControl().setLookAt(landingPos.getX() + 0.5D, landingPos.getY() + 0.5D, landingPos.getZ() + 0.5D);
        mob.swing(InteractionHand.MAIN_HAND);

        if (mob.level().setBlock(landingPos, Blocks.WATER.defaultBlockState(), 3)) {
            consumeWaterBucketSource(mob, waterBucket);
            mob.fallDistance = 0.0F;
            mind.clutchCooldown = 40;
            return true;
        }

        return false;
    }

    private void smartJump(ChangedEntity mob, LivingEntity target) {
        LatexMind mind = LatexMindStore.get(mob);
        if (mind.jumpCooldown > 0 || !mob.onGround()) return;

        if (canSprintJumpChase(mob, target)) {
            Vec3 flat = target.position().subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
            if (flat.lengthSqr() > 0.001D) {
                Vec3 forward = flat.normalize().scale(SPRINT_JUMP_BOOST);
                mob.setDeltaMovement(forward.x, 0.42D, forward.z);
                mob.hasImpulse = true;
                mind.jumpCooldown = 10;
                return;
            }
        }

        boolean needsJump = mob.horizontalCollision
                || (target.getY() - mob.getY() > 1.1D && mob.distanceTo(target) < 3.25D);

        if (needsJump) {
            mob.setDeltaMovement(0.0D, 0.42D, 0.0D);
            mob.hasImpulse = true;
            mind.jumpCooldown = 16;
        }
    }

    private boolean canSprintJumpChase(ChangedEntity mob, LivingEntity target) {
        return canUseDirectChase(mob, target)
                && Math.abs(target.getY() - mob.getY()) <= 1.5D
                && mob.distanceTo(target) > 3.0D
                && mob.distanceTo(target) < 16.0D
                && !mob.horizontalCollision;
    }

    @Nullable
    private LivingEntity resolveTarget(ChangedEntity mob, LatexMind mind) {
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null && isValidAggroTarget(mob, currentTarget)) {
            if (mind.targetId == null || !mind.targetId.equals(currentTarget.getUUID())) {
                mind.targetId = currentTarget.getUUID();
            }
            return currentTarget;
        }

        LivingEntity remembered = findRememberedTarget(mob, mind);
        if (remembered != null) {
            mob.setTarget(remembered);
            return remembered;
        }

        LivingEntity visibleTarget = findVisibleTarget(mob);
        if (visibleTarget != null) {
            mob.setTarget(visibleTarget);
            mind.targetId = visibleTarget.getUUID();
            return visibleTarget;
        }

        return null;
    }

    @Nullable
    private Path createReachPath(ChangedEntity mob, LivingEntity target) {
        Path path = mob.getNavigation().createPath(target, 0);
        if (path == null) return null;
        return path.canReach() ? path : null;
    }

    @Nullable
    private State chooseTerrainAction(ChangedEntity mob, LatexMind mind, LivingEntity target) {
        TerrainPlan plan = analyzeTerrainPlan(mob, mind, target);
        BlockPos breakPos = plan.breakPos();
        BlockPos buildPos = plan.buildPos();
        double breakCost = plan.breakCost();
        double buildCost = plan.buildCost();

        mind.plannedBreakPos = Double.isFinite(breakCost) ? breakPos : null;
        mind.plannedBuildPos = Double.isFinite(buildCost) ? buildPos : null;

        if (!Double.isFinite(breakCost) && !Double.isFinite(buildCost)) {
            return null;
        }

        if (buildCost < breakCost) {
            return State.BUILD;
        }

        return State.BREAK;
    }

    private TerrainPlan analyzeTerrainPlan(ChangedEntity mob, LatexMind mind, LivingEntity target) {
        BlockPos immediateBreak = findPlannedBreakTarget(mob, target);
        BlockPos immediateBuild = shouldBuild(mob, mind, target) ? findImmediateBuildPlacement(mob, target) : null;

        double breakCost = estimateBreakCost(mob, immediateBreak);
        double buildCost = estimateBuildCost(mob, target, immediateBuild);

        if (Double.isFinite(breakCost) || Double.isFinite(buildCost)) {
            return new TerrainPlan(immediateBreak, immediateBuild, breakCost, buildCost);
        }

        BlockPos fallbackBreak = findRaycastBreakTarget(mob, target);
        BlockPos fallbackBuild = shouldBuild(mob, mind, target) ? findFallbackBuildPlacement(mob, target) : null;
        return new TerrainPlan(
                fallbackBreak,
                fallbackBuild,
                estimateBreakCost(mob, fallbackBreak),
                estimateBuildCost(mob, target, fallbackBuild)
        );
    }

    @Nullable
    private BlockPos findBreakTarget(ChangedEntity mob, LivingEntity target) {
        BlockPos immediate = findPlannedBreakTarget(mob, target);
        if (immediate != null) {
            return immediate;
        }
        return findRaycastBreakTarget(mob, target);
    }

    @Nullable
    private BlockPos findPlannedBreakTarget(ChangedEntity mob, LivingEntity target) {
        BlockPos corridorBlock = findBreakBlockAlongImaginaryPath(mob, target);
        if (corridorBlock != null) {
            return corridorBlock;
        }

        return findImmediateBreakTarget(mob, target);
    }

    @Nullable
    private BlockPos findImmediateBreakTarget(ChangedEntity mob, LivingEntity target) {
        Direction direction = horizontalDirectionToward(mob, target);
        BlockPos origin = mob.blockPosition();
        BlockPos[] candidates = new BlockPos[] {
                origin.relative(direction),
                origin.relative(direction).above(),
                origin.relative(direction).above(2),
                origin.relative(direction, 2),
                origin.relative(direction, 2).above()
        };

        for (BlockPos candidate : candidates) {
            if (isBreakCandidate(mob, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findBreakBlockAlongImaginaryPath(ChangedEntity mob, LivingEntity target) {
        Vec3 start = Vec3.atBottomCenterOf(mob.blockPosition());
        Vec3 end = Vec3.atBottomCenterOf(target.blockPosition());
        Vec3 delta = end.subtract(start);
        double horizontalLength = delta.multiply(1.0D, 0.0D, 1.0D).length();
        if (horizontalLength < 0.5D) {
            return null;
        }

        int samples = Math.min((int) Math.ceil(horizontalLength / BREAK_PATH_STEP), BREAK_PATH_SCAN_BLOCKS * 2);
        BlockPos best = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            double x = Mth.lerp(t, start.x, end.x);
            double y = Mth.lerp(t, start.y, end.y);
            double z = Mth.lerp(t, start.z, end.z);
            BlockPos base = BlockPos.containing(x, y, z);

            BlockPos[] corridor = new BlockPos[] {
                    base,
                    base.above(),
                    base.above(2),
                    base.below()
            };

            for (BlockPos candidate : corridor) {
                if (!isPathMiningCandidate(mob, candidate, base)) {
                    continue;
                }

                double dist = mob.distanceToSqr(Vec3.atCenterOf(candidate));
                if (dist < bestDist) {
                    best = candidate;
                    bestDist = dist;
                }
            }

            if (best != null && bestDist <= 12.25D) {
                return best;
            }
        }

        return best;
    }

    private boolean isPathMiningCandidate(ChangedEntity mob, BlockPos candidate, BlockPos corridorBase) {
        BlockState state = mob.level().getBlockState(candidate);
        if (state.isAir() || state.getDestroySpeed(mob.level(), candidate) < 0.0F) {
            return false;
        }
        if (mob.distanceToSqr(Vec3.atCenterOf(candidate)) > 25.0D) {
            return false;
        }

        boolean blocksFeetSpace = candidate.equals(corridorBase) || candidate.equals(corridorBase.above());
        boolean blocksHeadSpace = candidate.equals(corridorBase.above(2));
        boolean blocksFloorTransition = candidate.equals(corridorBase.below()) && mob.level().getBlockState(corridorBase).isAir();

        return (blocksFeetSpace || blocksHeadSpace || blocksFloorTransition) && canBreakBlock(mob, candidate, state);
    }

    private boolean isBreakCandidate(ChangedEntity mob, BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mob.level(), pos) < 0.0F) {
            return false;
        }
        if (mob.distanceToSqr(Vec3.atCenterOf(pos)) > 12.25D) {
            return false;
        }
        return canBreakBlock(mob, pos, state);
    }

    @Nullable
    private BlockPos findRaycastBreakTarget(ChangedEntity mob, LivingEntity target) {
        BlockHitResult hit = mob.level().clip(new ClipContext(
                mob.getEyePosition(),
                target.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mob
        ));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mob.level(), pos) < 0) {
            return null;
        }

        if (Block.isShapeFullBlock(state.getCollisionShape(mob.level(), pos))) {
            return pos;
        }

        return mob.distanceToSqr(Vec3.atCenterOf(pos)) <= 9.0D && canBreakBlock(mob, pos, state) ? pos : null;
    }

    @Nullable
    private BlockPos findBuildPlacement(ChangedEntity mob, LivingEntity target) {
        BlockPos immediate = findImmediateBuildPlacement(mob, target);
        if (immediate != null) {
            return immediate;
        }
        return findFallbackBuildPlacement(mob, target);
    }

    @Nullable
    private BlockPos findImmediateBuildPlacement(ChangedEntity mob, LivingEntity target) {
        BlockState buildState = defaultBuildState(mob);
        if (buildState == null) {
            return null;
        }

        if (shouldTowerUp(mob, target) && canPlaceTowerBlockAt(mob, mob.blockPosition(), buildState)) {
            return mob.blockPosition();
        }

        Direction direction = horizontalDirectionToward(mob, target);
        BlockPos origin = mob.blockPosition();
        BlockPos front = origin.relative(direction);
        BlockPos frontBelow = front.below();
        BlockPos secondFrontBelow = origin.relative(direction, 2).below();

        if (target.getY() > mob.getY() + 0.8D && canPlaceBlockAt(mob, front, buildState)) {
            return front;
        }

        if (mob.onGround() && mob.level().getBlockState(frontBelow).canBeReplaced() && canPlaceBlockAt(mob, frontBelow, buildState)) {
            return frontBelow;
        }

        if (mob.onGround() && mob.level().getBlockState(secondFrontBelow).canBeReplaced() && canPlaceBlockAt(mob, secondFrontBelow, buildState)) {
            return secondFrontBelow;
        }

        return null;
    }

    @Nullable
    private BlockPos findFallbackBuildPlacement(ChangedEntity mob, LivingEntity target) {
        Direction direction = horizontalDirectionToward(mob, target);
        BlockPos front = mob.blockPosition().relative(direction);
        BlockPos bridge = front.below();

        if (target.getY() > mob.getY() + 1.2D) {
            BlockPos step = front;
            if (canPlaceBlockAt(mob, step, defaultBuildState(mob))) {
                return step;
            }
        }

        if (mob.onGround() && canPlaceBlockAt(mob, bridge, defaultBuildState(mob))) {
            return bridge;
        }

        Path path = mob.getNavigation().createPath(target, 0);
        if (path == null && target.getY() > mob.getY() + 1.2D) {
            BlockPos scaffold = mob.blockPosition().relative(direction);
            if (canPlaceBlockAt(mob, scaffold, defaultBuildState(mob))) {
                return scaffold;
            }
        }

        return null;
    }

    private Direction horizontalDirectionToward(ChangedEntity mob, LivingEntity target) {
        Vec3 delta = target.position().subtract(mob.position());
        if (Math.abs(delta.x) > Math.abs(delta.z)) {
            return delta.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return delta.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean moveNearPlacement(ChangedEntity mob, BlockPos placePos) {
        return mob.distanceToSqr(placePos.getX() + 0.5D, placePos.getY() + 0.5D, placePos.getZ() + 0.5D) <= 6.25D;
    }

    private boolean canPlaceBlockAt(ChangedEntity mob, BlockPos pos, @Nullable BlockState placeState) {
        if (placeState == null) return false;
        if (!mob.level().getBlockState(pos).canBeReplaced()) return false;
        if (!mob.level().getFluidState(pos).isEmpty()) return false;
        if (!mob.level().getWorldBorder().isWithinBounds(pos)) return false;

        BlockPos below = pos.below();
        BlockState support = mob.level().getBlockState(below);
        if (support.isAir() || support.getCollisionShape(mob.level(), below).isEmpty()) {
            return false;
        }

        return mob.level().isUnobstructed(placeState, pos, net.minecraft.world.phys.shapes.CollisionContext.of(mob));
    }

    private boolean canPlaceTowerBlockAt(ChangedEntity mob, BlockPos pos, @Nullable BlockState placeState) {
        if (placeState == null) return false;
        if (!mob.level().getBlockState(pos).canBeReplaced()) return false;
        if (!mob.level().getFluidState(pos).isEmpty()) return false;
        if (!mob.level().getWorldBorder().isWithinBounds(pos)) return false;

        BlockPos below = pos.below();
        BlockState support = mob.level().getBlockState(below);
        if (support.isAir() || support.getCollisionShape(mob.level(), below).isEmpty()) {
            return false;
        }

        if (!mob.level().isUnobstructed(placeState, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
            return false;
        }

        return mob.level().getEntities(mob, new AABB(pos)).isEmpty();
    }

    @Nullable
    private BlockPos findWaterClutchPos(ChangedEntity mob) {
        int maxDepth = Math.max(4, Math.min(12, (int) Math.ceil(mob.fallDistance)));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int depth = 1; depth <= maxDepth; depth++) {
            cursor.set(mob.getX(), mob.getY() - depth, mob.getZ());
            BlockState state = mob.level().getBlockState(cursor);
            if (state.isAir() || state.canBeReplaced()) {
                BlockState below = mob.level().getBlockState(cursor.below());
                if (!below.isAir() && !below.getCollisionShape(mob.level(), cursor.below()).isEmpty()) {
                    return cursor.immutable();
                }
                continue;
            }

            if (!state.getCollisionShape(mob.level(), cursor).isEmpty()) {
                BlockPos above = cursor.above();
                if (mob.level().getBlockState(above).canBeReplaced()) {
                    return above;
                }
                return null;
            }
        }

        return null;
    }

    @Nullable
    private BlockState defaultBuildState(ChangedEntity mob) {
        ItemStack stack = findBestBuildingBlock(mob);
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        return blockItem.getBlock().defaultBlockState();
    }

    private ItemStack findBestMiningTool(ChangedEntity mob, BlockState state) {
        ItemStack main = mob.getMainHandItem();
        ItemStack off = mob.getOffhandItem();
        float mainScore = miningScore(main, state);
        float offScore = miningScore(off, state);
        if (offScore <= 1.0F && mainScore <= 1.0F) {
            return ItemStack.EMPTY;
        }
        return offScore > mainScore ? off : main;
    }

    @Nullable
    private WaterBucketSource findWaterBucket(ChangedEntity mob) {
        if (mob.getMainHandItem().is(Items.WATER_BUCKET)) {
            return new WaterBucketSource(mob.getMainHandItem(), InteractionHand.MAIN_HAND, -1);
        }

        if (mob.getOffhandItem().is(Items.WATER_BUCKET)) {
            return new WaterBucketSource(mob.getOffhandItem(), InteractionHand.OFF_HAND, -1);
        }
        return null;
    }

    private void equipBestCombatTool(ChangedEntity mob) {
        ItemStack best = findBestCombatTool(mob);
        if (!best.isEmpty()) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, best);
        }
        setBestShieldOffhand(mob);
    }

    private ItemStack findBestCombatTool(ChangedEntity mob) {
        ItemStack main = mob.getMainHandItem();
        ItemStack off = mob.getOffhandItem();
        return combatScore(off) > combatScore(main) ? off : main;
    }

    private double combatScore(ItemStack stack) {
        if (stack.isEmpty()) return 0.0D;

        double score = 1.0D;
        if (stack.getItem() instanceof SwordItem sword) {
            score += 8.0D + sword.getDamage();
        } else if (stack.getItem() instanceof TieredItem tiered) {
            score += 5.0D + tiered.getTier().getAttackDamageBonus() * 2.0D + tiered.getTier().getLevel();
        } else if (stack.getItem() instanceof TridentItem) {
            score += 9.0D;
        } else if (stack.getMaxDamage() > 0) {
            score += 2.5D;
        }

        score += EnchantmentHelper.getDamageBonus(stack, net.minecraft.world.entity.MobType.UNDEFINED);
        return score;
    }

    private double itemUtilityScore(ItemStack stack) {
        return Math.max(combatScore(stack), Math.max(buildScore(null, stack), stack.is(Items.WATER_BUCKET) ? 8.0D : 0.0D));
    }

    private float miningScore(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1.0F;
        return stack.getDestroySpeed(state) + toolMaterialScore(stack) * 0.1F;
    }

    private boolean canBreakBlock(ChangedEntity mob, BlockPos pos, BlockState state) {
        ItemStack tool = findBestMiningTool(mob, state);
        if (!tool.isEmpty() && tool.getDestroySpeed(state) > 1.0F) {
            return true;
        }

        if (!state.requiresCorrectToolForDrops()) {
            float hardness = state.getDestroySpeed(mob.level(), pos);
            return hardness >= 0.0F && hardness <= 1.5F;
        }

        return false;
    }

    private float toolMaterialScore(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tiered) {
            return tiered.getTier().getSpeed() + tiered.getTier().getLevel();
        }
        return 0.0F;
    }

    private double buildScore(@Nullable ChangedEntity mob, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return 0.0D;
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return 0.0D;
        if (mob != null && !state.isCollisionShapeFullBlock(mob.level(), BlockPos.ZERO)) return 0.0D;
        return stack.getCount() + 1.0D;
    }

    private ItemStack findBestBuildingBlock(ChangedEntity mob) {
        ItemStack main = mob.getMainHandItem();
        ItemStack off = mob.getOffhandItem();
        double mainScore = buildScore(mob, main);
        double offScore = buildScore(mob, off);
        if (mainScore <= 0.0D && offScore <= 0.0D) {
            return ItemStack.EMPTY;
        }
        return offScore > mainScore ? off : main;
    }

    @Nullable
    private ItemEntity findBestBlockPickup(ChangedEntity mob) {
        ItemEntity best = null;
        double bestScore = 0.0D;

        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(LOOT_RANGE))) {
            ItemStack stack = item.getItem();
            double score = buildScore(mob, stack) - mob.distanceToSqr(item) * 0.05D;
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }

        return best;
    }

    private int countUsableBuildingBlocks(ChangedEntity mob) {
        int count = 0;
        if (buildScore(mob, mob.getMainHandItem()) > 0.0D) count += mob.getMainHandItem().getCount();
        if (buildScore(mob, mob.getOffhandItem()) > 0.0D) count += mob.getOffhandItem().getCount();
        ItemEntity nearby = findBestBlockPickup(mob);
        if (nearby != null) count += nearby.getItem().getCount();
        return count;
    }

    private int estimateBuildBlocksNeeded(ChangedEntity mob, LivingEntity target, @Nullable BlockPos buildPos) {
        if (buildPos == null) return Integer.MAX_VALUE;
        if (buildPos.equals(mob.blockPosition()) && shouldTowerUp(mob, target)) {
            return Math.max(1, Math.min(8, target.blockPosition().getY() - mob.blockPosition().getY()));
        }
        int verticalGap = Math.max(0, target.blockPosition().getY() - mob.blockPosition().getY());
        if (buildPos.getY() >= mob.blockPosition().getY()) {
            return Math.max(1, Math.min(3, verticalGap));
        }
        return 1;
    }

    private double estimateBuildCost(ChangedEntity mob, LivingEntity target, @Nullable BlockPos buildPos) {
        if (buildPos == null) return Double.POSITIVE_INFINITY;
        int blocksAvailable = countUsableBuildingBlocks(mob);
        int blocksNeeded = estimateBuildBlocksNeeded(mob, target, buildPos);
        if (blocksAvailable < blocksNeeded) return Double.POSITIVE_INFINITY;
        if (blocksNeeded > 1 && blocksAvailable - blocksNeeded < BUILD_BLOCK_RESERVE) return Double.POSITIVE_INFINITY;
        double verticalPenalty = buildPos.getY() >= mob.blockPosition().getY() ? 4.0D : 0.0D;
        return blocksNeeded * 10.0D + verticalPenalty + mob.distanceToSqr(Vec3.atCenterOf(buildPos)) * 0.25D;
    }

    private double estimateBreakCost(ChangedEntity mob, @Nullable BlockPos breakPos) {
        if (breakPos == null) return Double.POSITIVE_INFINITY;
        BlockState state = mob.level().getBlockState(breakPos);
        float hardness = state.getDestroySpeed(mob.level(), breakPos);
        if (hardness < 0.0F) return Double.POSITIVE_INFINITY;
        if (!canBreakBlock(mob, breakPos, state)) return Double.POSITIVE_INFINITY;
        ItemStack tool = findBestMiningTool(mob, state);
        float speed = tool.isEmpty() ? 1.0F : Math.max(1.0F, tool.getDestroySpeed(state));
        double directnessBonus = mob.blockPosition().closerThan(breakPos, 2.5D) ? -2.0D : 0.0D;
        return hardness * 16.0D / speed + mob.distanceToSqr(Vec3.atCenterOf(breakPos)) * 0.2D + directnessBonus;
    }

    private void consumeOneMatchingItem(ChangedEntity mob, ItemStack targetStack) {
        if (ItemStack.isSameItemSameTags(mob.getMainHandItem(), targetStack)) {
            mob.getMainHandItem().shrink(1);
            if (mob.getMainHandItem().isEmpty()) {
                mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            return;
        }

        if (ItemStack.isSameItemSameTags(mob.getOffhandItem(), targetStack)) {
            mob.getOffhandItem().shrink(1);
            if (mob.getOffhandItem().isEmpty()) {
                mob.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            return;
        }
    }

    private void equipWaterBucketForClutch(ChangedEntity mob, WaterBucketSource source) {
        if (source.hand() == InteractionHand.OFF_HAND) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, source.stack());
        }
    }

    private void consumeWaterBucketSource(ChangedEntity mob, WaterBucketSource source) {
        if (source.hand() == InteractionHand.OFF_HAND) {
            mob.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET));
        }
        mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    }

    private void setBestShieldOffhand(ChangedEntity mob) {
        ItemStack currentOffhand = mob.getOffhandItem();
        if (currentOffhand.getItem() instanceof ShieldItem) return;

        if (mob.getMainHandItem().getItem() instanceof ShieldItem) {
            ItemStack shield = mob.getMainHandItem().copy();
            mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mob.setItemInHand(InteractionHand.OFF_HAND, shield);
        }
    }

    private ItemStack findBestShield(ChangedEntity mob) {
        if (mob.getMainHandItem().getItem() instanceof ShieldItem) return mob.getMainHandItem();
        if (mob.getOffhandItem().getItem() instanceof ShieldItem) return mob.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private void raiseShieldIfPossible(ChangedEntity mob) {
        if (mob.getOffhandItem().getItem() instanceof ShieldItem) {
            mob.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    private void stopShieldingIfIdle(ChangedEntity mob) {
        if (mob.isUsingItem() && !(mob.getOffhandItem().getItem() instanceof ShieldItem && state == State.BREAK)) {
            mob.stopUsingItem();
        }
    }

    private boolean hasInterestingLootNearby(ChangedEntity mob) {
        return findBestItemEntity(mob) != null;
    }

    @Nullable
    private ItemEntity findBestItemEntity(ChangedEntity mob) {
        double bestScore = 0.0D;
        ItemEntity best = null;

        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(LOOT_RANGE))) {
            if (!item.isAlive() || item.getItem().isEmpty()) continue;

            double score = scorePickup(mob, item.getItem()) - mob.distanceToSqr(item) * 0.05D;
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }

        return best;
    }

    private double scorePickup(ChangedEntity mob, ItemStack stack) {
        double score = stack.getCount() * 0.05D;

        if (stack.getItem() instanceof ArmorItem armor) {
            score += 8.0D + armor.getDefense() * 2.0D
                    + EnchantmentHelper.getTagEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, stack);
            ItemStack current = mob.getItemBySlot(armor.getEquipmentSlot());
            if (current.isEmpty()) score += 6.0D;
            else score += Math.max(0.0D, armorValue(stack) - armorValue(current));
            return score;
        }

        if (stack.getItem() instanceof TieredItem tiered) {
            score += 6.0D + tiered.getTier().getLevel() * 3.0D + tiered.getTier().getSpeed() * 0.2D;
            return score;
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (state.isCollisionShapeFullBlock(mob.level(), BlockPos.ZERO)) {
                score += 4.0D + Math.min(64, stack.getCount()) * 0.2D;
            }
            return score;
        }

        if (stack.getItem() instanceof ShieldItem) {
            return score + 6.0D;
        }

        if (stack.is(Items.WATER_BUCKET)) {
            return score + 8.0D;
        }

        score += stack.getMaxDamage() > 0 ? 1.5D : 0.0D;
        return score;
    }

    private void autoEquipFromGround(ChangedEntity mob, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof ArmorItem armor)) return;

        EquipmentSlot slot = armor.getEquipmentSlot();
        ItemStack equipped = mob.getItemBySlot(slot);
        if (equipped.isEmpty() || armorValue(stack) > armorValue(equipped)) {
            mob.setItemSlot(slot, stack.copyWithCount(1));
            stack.shrink(1);
            if (stack.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(stack);
        }
    }

    private void equipBestArmor(ChangedEntity mob, LatexMind mind) {
        if (mind.equipmentScanCooldown > 0) return;
        mind.equipmentScanCooldown = 40;
    }

    private double armorValue(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return 0.0D;
        return armor.getDefense() * 2.0D
                + armor.getToughness()
                + EnchantmentHelper.getTagEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, stack) * 1.5D;
    }

    private void maybeAlertNearbyLatex(ChangedEntity mob, LivingEntity target) {
        LatexMind mind = LatexMindStore.get(mob);
        if (mind.allyAlertCooldown > 0) return;
        mind.allyAlertCooldown = 30;

        var box = mob.getBoundingBox().inflate(10.0D);
        for (ChangedEntity ally : mob.level().getEntitiesOfClass(ChangedEntity.class, box, entity -> entity != mob && entity.isAlive())) {
            LatexMind allyMind = LatexMindStore.get(ally);
            ally.setTarget(target);
            allyMind.remember(target, ally.tickCount, ally.hasLineOfSight(target));
        }
    }

    @Nullable
    private LivingEntity findVisibleTarget(ChangedEntity mob) {
        double range = Math.max(16.0D, mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
        LivingEntity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range))) {
            if (!isValidAggroTarget(mob, candidate) || !mob.hasLineOfSight(candidate)) {
                continue;
            }

            double distance = mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestTarget = candidate;
            }
        }

        return bestTarget;
    }

    @Nullable
    private LivingEntity findRememberedTarget(ChangedEntity mob, LatexMind mind) {
        if (mind.targetId == null) {
            return null;
        }

        double range = Math.max(24.0D, mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) + 16.0D);
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range))) {
            if (mind.targetId.equals(candidate.getUUID()) && isValidAggroTarget(mob, candidate)) {
                return candidate;
            }
        }

        if (mob.level() instanceof ServerLevel server) {
            Player player = server.getPlayerByUUID(mind.targetId);
            if (player != null && isValidAggroTarget(mob, player)) {
                return player;
            }
        }

        return null;
    }

    private boolean isValidAggroTarget(ChangedEntity mob, LivingEntity target) {
        if (target == mob || !target.isAlive() || target instanceof ChangedEntity) {
            return false;
        }

        if (target instanceof Player player) {
            if (LatexCuddleHelper.isTamingOwner(mob, player)) {
                return false;
            }
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            return true;
        }

        if (target.getType().is(CHANGED_LATEXES)) {
            return false;
        }

        return target.getType().is(CHANGED_HUMANOIDS);
    }

    private boolean shouldDropTarget(ChangedEntity mob, LatexMind mind) {
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget instanceof Player playerTarget && !playerTarget.isAlive()) {
            return true;
        }

        if (mind.targetId != null && mob.level() instanceof ServerLevel server) {
            Player rememberedPlayer = server.getPlayerByUUID(mind.targetId);
            return rememberedPlayer != null && !rememberedPlayer.isAlive();
        }

        return false;
    }

    private void updateStuck(ChangedEntity mob, LatexMind mind) {
        if (mob.horizontalCollision && mob.onGround()) {
            mind.stuckTicks++;
        } else {
            mind.stuckTicks = Math.max(0, mind.stuckTicks - 1);
        }

        if (mob.getNavigation().isDone() && mind.hasLOS && mind.remembersRecentTarget(mob) && mind.stuckTicks < 3) {
            mind.pathFailed = false;
            mind.noPathTicks = 0;
        } else if (mob.getNavigation().isDone() && mind.remembersRecentTarget(mob)) {
            mind.noPathTicks++;
            mind.pathFailed = mind.noPathTicks > 12;
        } else {
            mind.noPathTicks = 0;
            mind.pathFailed = false;
        }
    }
}
