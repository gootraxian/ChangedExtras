package com.katt.changedextras.common.ai;

import com.katt.changedextras.common.LatexCuddleHelper;
import com.katt.changedextras.common.inventory.LatexInventory;
import com.katt.changedextras.common.inventory.LatexInventoryProvider;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LatexBrain {
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> CHANGED_HUMANOIDS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "humanoids"));
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> CHANGED_LATEXES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "latexes"));

    private static final double ATTACK_RANGE = 2.9D;
    private static final double SEARCH_REACH = 1.75D;
    private static final double LOOT_RANGE = 10.0D;
    private static final double PICKUP_RANGE = 2.2D;

    // Movement speed constants calibrated to Minecraft player speeds:
    // Walking: ~4.317 blocks/s | Sprinting: ~5.612 blocks/s | Sprint-Jumping: ~7.127 blocks/s
    private static final double PLAYER_WALK_SPEED = 1.00D;
    private static final double PLAYER_SPRINT_SPEED = 1.30D;
    private static final double PLAYER_SNEAK_SPEED = 0.55D;
    private static final double PLAYER_WATCH_SPEED = 1.00D;
    private static final double PLAYER_BREAK_SPEED = 1.00D;
    private static final double PLAYER_BUILD_SPEED = 1.00D;
    private static final double PLAYER_SEARCH_SPEED = 1.00D;
    private static final double PLAYER_LOOT_SPEED = 1.00D;
    private static final double PLAYER_RETREAT_SPEED = 1.30D;
    private static final double TOWER_ALIGN_SPEED = 0.65D;

    private static final double MAX_VISIBLE_TARGET_RANGE = 14.0D;
    private static final double MAX_REMEMBERED_TARGET_RANGE = 20.0D;
    private static final double HARD_TARGET_DROP_RANGE = 24.0D;
    private static final double BREAK_PATH_STEP = 0.5D;
    private static final int BREAK_PATH_SCAN_BLOCKS = 16;
    private static final int IMAGINARY_PATH_SCAN_BLOCKS = 12;
    private static final int IMAGINARY_PATH_MAX_BLOCKS = 8;
    private static final int ALT_PATH_SEARCH_MAX_RADIUS = 8;
    private static final int TERRAIN_COMMIT_TICKS = 10;
    private static final int BUILD_BLOCK_RESERVE = 4;
    private static final int SEARCH_TIMEOUT = 80;
    private static final int ATTACK_COOLDOWN = 10;
    private static final int DECISION_INTERVAL_TICKS = 4;
    private static final int PATH_CACHE_TICKS = 8;
    private static final int TERRAIN_CACHE_TICKS = 6;
    private static final double BREAK_PREFERENCE_PENALTY = 18.0D;

    // Ranged combat (bow) tuning
    private static final double BOW_MIN_RANGE = ATTACK_RANGE + 0.35D;
    private static final double BOW_MAX_RANGE = 15.0D;
    private static final double BOW_KITE_DISTANCE = 5.0D;
    private static final int BOW_CHARGE_TICKS_FULL = 20;
    private static final int RANGED_ATTACK_COOLDOWN = 20;
    private static final float ARROW_VELOCITY = 1.6F;
    private static final float ARROW_INACCURACY = 8.0F;

    // Shield blocking tuning
    private static final double SHIELD_BLOCK_RANGE = ATTACK_RANGE + 1.5D;
    private static final double PROJECTILE_WATCH_RANGE = 8.0D;

    // Curiosity tuning - keeps a whole nearby horde from all fixating on the same passerby at once
    private static final int CURIOSITY_TRIGGER_CHANCE_DENOM = 12;
    private static final int CURIOSITY_MAX_ALLIES_PER_TARGET = 2;
    private static final double CURIOSITY_HORDE_SCAN_RADIUS = 16.0D;

    public enum State {
        IDLE,
        CHASE,
        PARKOUR,
        ATTACK,
        RANGED_ATTACK,
        BREAK,
        BUILD,
        SEARCH,
        LOOT,
        REPOSITION,
        WATCH_TARGET,
        SURVIVE,
        CURIOSITY
    }

    private record TerrainPlan(@Nullable BlockPos breakPos, @Nullable BlockPos buildPos, List<BlockPos> imaginedBuildPath,
                               double breakCost, double buildCost) {}
    private record WaterBucketSource(ItemStack stack, @Nullable InteractionHand hand, int inventorySlot) {}

    private State state = State.IDLE;
    private int thinkCooldown = 0;
    private double lastAttackDistance = Double.NaN;

    @Nullable
    private Path cachedChasePath;
    @Nullable
    private BlockPos cachedChaseTargetPos;
    @Nullable
    private java.util.UUID cachedChaseTargetId;
    private int cachedChasePathTick = Integer.MIN_VALUE;

    public void tick(ChangedEntity mob, LatexMind mind) {
        tickCooldowns(mind);
        equipBestArmor(mob, mind);

        // Cap runaway velocities to strict player sprint-jump limit (~0.36 blocks/tick = ~7.13 blocks/s)
        if (!mind.isParkouring && mob.hurtTime <= 0) {
            Vec3 vel = mob.getDeltaMovement();
            double hSpeedSqr = vel.x * vel.x + vel.z * vel.z;
            double maxAllowedSpeed = 0.40D;
            if (hSpeedSqr > maxAllowedSpeed * maxAllowedSpeed) {
                double scale = maxAllowedSpeed / Math.sqrt(hSpeedSqr);
                mob.setDeltaMovement(vel.x * scale, vel.y, vel.z * scale);
            }
        }

        if (mob.tickCount % 40 == 0) {
            mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).ifPresent(LatexInventory::tryAutoCraftNeededTools);
        }

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

        if (thinkCooldown-- <= 0 || mind.isParkouring) {
            thinkCooldown = DECISION_INTERVAL_TICKS;
            state = decideState(mob, mind, target);
            mind.state = state;
        }

        if (state != State.CHASE && mind.isSprintJumping) {
            mind.isSprintJumping = false;
            mind.sprintJumpTicks = 0;
        }

        if (state != State.RANGED_ATTACK && mind.bowChargeTicks > 0) {
            if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof BowItem) {
                mob.stopUsingItem();
            }
            mind.bowChargeTicks = 0;
        }

        manageShieldBlocking(mob, mind, target, state);
        manageSwimming(mob, mind, target);

        switch (state) {
            case SURVIVE -> survive(mob, mind, target);
            case CURIOSITY -> curiosity(mob, mind);
            case PARKOUR -> parkour(mob, mind, target);
            case CHASE -> chase(mob, mind, target);
            case ATTACK -> attack(mob, target);
            case RANGED_ATTACK -> rangedAttack(mob, mind, target);
            case BREAK -> breakObstacle(mob, mind, target);
            case BUILD -> buildTowardTarget(mob, mind, target);
            case SEARCH -> search(mob, mind);
            case LOOT -> loot(mob);
            case REPOSITION -> reposition(mob, mind, target);
            case WATCH_TARGET -> watchTarget(mob, target);
            default -> idle(mob, mind);
        }
    }

    private void tickCooldowns(LatexMind mind) {
        if (mind.attackCooldown > 0) mind.attackCooldown--;
        if (mind.breakCooldown > 0) mind.breakCooldown--;
        if (mind.buildCooldown > 0) mind.buildCooldown--;
        if (mind.clutchCooldown > 0) mind.clutchCooldown--;
        if (mind.jumpCooldown > 0) mind.jumpCooldown--;
        if (mind.parkourCooldown > 0) mind.parkourCooldown--;
        if (mind.lootScanCooldown > 0) mind.lootScanCooldown--;
        if (mind.equipmentScanCooldown > 0) mind.equipmentScanCooldown--;
        if (mind.allyAlertCooldown > 0) mind.allyAlertCooldown--;
        if (mind.recentBreakTicks > 0) mind.recentBreakTicks--;
        if (mind.enrageTicks > 0) mind.enrageTicks--;
        if (mind.curiosityCooldown > 0) mind.curiosityCooldown--;
        if (mind.healCooldown > 0) mind.healCooldown--;
        if (mind.rangedAttackCooldown > 0) mind.rangedAttackCooldown--;

        if (mind.combatStrafeTimer > 0) {
            mind.combatStrafeTimer--;
        } else {
            mind.combatStrafeTimer = 18 + (int)(Math.random() * 14.0D);
            mind.combatStrafeDir = Math.random() < 0.5D ? 1 : -1;
        }
    }

    private State decideState(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        if (mob.getHealth() < mob.getMaxHealth() * 0.35F || mind.isHiding) {
            return State.SURVIVE;
        }

        if (mind.isParkouring) {
            return State.PARKOUR;
        }

        if (target != null && target.isAlive()) {
            double distance = mob.distanceTo(target);
            boolean hasDirectHitPath = mob.hasLineOfSight(target) && hasDirectUnobstructedHitRay(mob, target);
            boolean hasReachablePath = hasReachablePath(mob, mind, target);

            if (shouldRetreat(mob, target)) {
                return State.REPOSITION;
            }

            boolean hasBow = hasUsableBow(mob);
            boolean inBowRange = hasDirectHitPath && distance > BOW_MIN_RANGE && distance <= BOW_MAX_RANGE;

            // Keep committing to a shot already being drawn as long as the target hasn't closed to melee range
            if (mind.bowChargeTicks > 0 && hasBow && hasDirectHitPath && distance > ATTACK_RANGE) {
                return State.RANGED_ATTACK;
            }

            if (hasBow && inBowRange && mind.rangedAttackCooldown <= 0) {
                return State.RANGED_ATTACK;
            }

            // Attack ONLY if there is direct, unobstructed line of sight and within reach
            if (hasDirectHitPath && distance <= ATTACK_RANGE + 0.35D) {
                return State.ATTACK;
            }

            if (hasReachablePath) {
                mind.plannedBuildPos = null;
                mind.plannedBreakPos = null;
                return State.CHASE;
            }

            if (mind.parkourCooldown <= 0 && mob.onGround()) {
                BlockPos parkourLanding = findParkourJump(mob, target);
                if (parkourLanding != null) {
                    mind.isParkouring = true;
                    mind.parkourLandingPos = parkourLanding;
                    mind.parkourTakeoffPos = mob.blockPosition();
                    mind.parkourTicks = 0;
                    return State.PARKOUR;
                }
            }

            if (shouldTowerUp(mob, target) && shouldBuild(mob, mind, target)) {
                return State.BUILD;
            }

            if (!mind.pathFailed && mind.stuckTicks < 6) {
                return State.CHASE;
            }

            State terrainState = chooseTerrainAction(mob, mind, target);
            if (terrainState != null) {
                return terrainState;
            }

            if (hasNearbyFistMineableDropBlock(mob, target)
                    && mind.noPathTicks < TERRAIN_COMMIT_TICKS
                    && mind.stuckTicks < 6) {
                return State.CHASE;
            }

            return State.CHASE;
        }

        if (mind.remembersRecentTarget(mob) && mind.searchTicks < SEARCH_TIMEOUT) {
            return State.SEARCH;
        }

        if (hasInterestingLootNearby(mob)) {
            return State.LOOT;
        }

        if (mind.curiosityTargetId != null) {
            // Already engaged with someone - see it through instead of re-rolling every decision tick.
            if (findCuriosityTarget(mob, mind) != null) {
                return State.CURIOSITY;
            }
        } else if (mind.curiosityCooldown <= 0) {
            LivingEntity curiosityCandidate = findCuriosityTarget(mob, mind);
            if (curiosityCandidate != null
                    && mob.getRandom().nextInt(CURIOSITY_TRIGGER_CHANCE_DENOM) == 0
                    && !tooManyAlliesAlreadyCurious(mob, curiosityCandidate)) {
                return State.CURIOSITY;
            }
            // Didn't act on it this time - space out the next roll instead of checking every tick.
            mind.curiosityCooldown = 30 + mob.getRandom().nextInt(60);
        }

        return State.IDLE;
    }

    private void idle(ChangedEntity mob, LatexMind mind) {
        mob.setSprinting(false);
        mob.setShiftKeyDown(false);
        mob.setXxa(0.0F);
        mob.setZza(0.0F);
        mind.isSprintJumping = false;
        mind.sprintJumpTicks = 0;
    }

    private void survive(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity threat) {
        mob.setXxa(0.0F);
        if (mind.healCooldown <= 0) {
            tryEatFood(mob, mind);
        }

        if (mob.getHealth() >= mob.getMaxHealth() * 0.65F) {
            mind.isHiding = false;
            mob.setShiftKeyDown(false);
            return;
        }

        if (threat != null && threat.isAlive()) {
            Vec3 away = mob.position().subtract(threat.position());
            if (away.lengthSqr() < 0.001D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }

            Vec3 retreatPos = mob.position().add(away.normalize().scale(8.0D));
            boolean cliff = isDangerousCliffAhead(mob);
            mob.setShiftKeyDown(cliff);
            mob.setSprinting(!cliff);
            double speed = cliff ? PLAYER_SNEAK_SPEED : PLAYER_RETREAT_SPEED;
            mob.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, speed);

            Vec3 retreatDir = away.normalize();
            if (!cliff && canSprintJumpInDirection(mob, retreatDir) && mind.jumpCooldown <= 0) {
                mob.getJumpControl().jump();
                mind.jumpCooldown = 10;
            }

            if (mob.distanceTo(threat) < 4.0D && mob.distanceTo(threat) > 1.8D && mind.buildCooldown <= 0) {
                tryPlaceDefensiveBarricade(mob, mind, threat);
            }
        } else {
            mob.setSprinting(false);
            mob.setShiftKeyDown(true);
            mind.isHiding = true;
        }
    }

    private void tryPlaceDefensiveBarricade(ChangedEntity mob, LatexMind mind, LivingEntity threat) {
        ItemStack blockStack = findBestBuildingBlock(mob);
        if (blockStack.isEmpty() || !(blockStack.getItem() instanceof BlockItem blockItem)) return;

        BlockPos behindPos = mob.blockPosition().relative(mob.getDirection().getOpposite());
        BlockState placeState = blockItem.getBlock().defaultBlockState();

        if (canPlaceBlockAt(mob, behindPos, placeState)) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, blockStack);
            mob.swing(InteractionHand.MAIN_HAND);
            if (mob.level().setBlock(behindPos, placeState, 3)) {
                consumeOneMatchingItem(mob, blockStack);
                mind.buildCooldown = 12;
            }
        }
    }

    private void tryEatFood(ChangedEntity mob, LatexMind mind) {
        if (mob.getHealth() >= mob.getMaxHealth()) return;

        mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).ifPresent(inv -> {
            ItemStack food = inv.findBestFood();
            if (!food.isEmpty() && food.getItem().getFoodProperties() != null) {
                int nutrition = food.getItem().getFoodProperties().getNutrition();
                food.shrink(1);
                mob.heal(nutrition * 2.5F + 2.0F);
                mob.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
                mind.healCooldown = 40;
            }
        });

        if (mind.healCooldown <= 0) {
            ItemStack main = mob.getMainHandItem();
            if (main.isEdible() && main.getItem().getFoodProperties() != null) {
                int nutrition = main.getItem().getFoodProperties().getNutrition();
                main.shrink(1);
                mob.heal(nutrition * 2.5F + 2.0F);
                mob.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
                mind.healCooldown = 40;
            }
        }
    }

    private void curiosity(ChangedEntity mob, LatexMind mind) {
        mob.setXxa(0.0F);
        LivingEntity curiousTarget = findCuriosityTarget(mob, mind);
        if (curiousTarget == null || !curiousTarget.isAlive()) {
            mind.curiosityTicks = 0;
            mind.curiosityCooldown = 120;
            mind.curiosityTargetId = null;
            mob.setShiftKeyDown(false);
            return;
        }

        mind.curiosityTargetId = curiousTarget.getUUID();
        double dist = mob.distanceTo(curiousTarget);
        mob.getLookControl().setLookAt(curiousTarget, 20.0F, 20.0F);

        if (dist > 3.8D) {
            mob.getNavigation().moveTo(curiousTarget, PLAYER_WALK_SPEED);
            mob.setShiftKeyDown(false);
        } else {
            mob.getNavigation().stop();
            faceTarget(mob, curiousTarget, 15.0F);
            mind.curiosityTicks++;

            if (mind.curiosityTicks < 30) {
                mob.setShiftKeyDown((mind.curiosityTicks / 8) % 2 == 0);
            } else if (mind.curiosityTicks < 55) {
                mob.setShiftKeyDown(false);
                if (mind.curiosityTicks % 20 == 0 && mob.onGround()) {
                    mob.getJumpControl().jump();
                }
            } else {
                mob.setShiftKeyDown(false);
                mind.curiosityTicks = 0;
                mind.curiosityCooldown = 240 + mob.getRandom().nextInt(200);
                mind.curiosityTargetId = null;
            }
        }
    }

    @Nullable
    private LivingEntity findCuriosityTarget(ChangedEntity mob, LatexMind mind) {
        if (mind.curiosityTargetId != null) {
            for (LivingEntity entity : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(10.0D))) {
                if (entity.getUUID().equals(mind.curiosityTargetId) && entity.isAlive() && mob.hasLineOfSight(entity)) {
                    return entity;
                }
            }
        }

        for (LivingEntity entity : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(10.0D))) {
            if (entity == mob || !entity.isAlive() || !mob.hasLineOfSight(entity)) continue;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            if (isValidAggroTarget(mob, entity, mind)) continue;

            if (entity instanceof Animal || entity instanceof AmbientCreature || entity instanceof Player || entity instanceof Villager) {
                return entity;
            }
        }

        return null;
    }

    private boolean tooManyAlliesAlreadyCurious(ChangedEntity mob, LivingEntity candidate) {
        int curiousCount = 0;
        AABB box = mob.getBoundingBox().inflate(CURIOSITY_HORDE_SCAN_RADIUS);
        for (ChangedEntity ally : mob.level().getEntitiesOfClass(ChangedEntity.class, box, e -> e != mob && e.isAlive())) {
            LatexMind allyMind = LatexMindStore.get(ally);
            if (allyMind.state == State.CURIOSITY && candidate.getUUID().equals(allyMind.curiosityTargetId)) {
                curiousCount++;
                if (curiousCount >= CURIOSITY_MAX_ALLIES_PER_TARGET) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==========================================
    // REFINED REALISTIC PARKOUR SYSTEM
    // ==========================================

    private void parkour(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        mob.setXxa(0.0F);
        if (mind.parkourLandingPos == null) {
            cancelParkour(mind, 20);
            return;
        }

        BlockPos landingPos = mind.parkourLandingPos;
        Vec3 landingCenter = Vec3.atBottomCenterOf(landingPos);
        Vec3 flatDelta = landingCenter.subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
        double flatDist = flatDelta.length();
        double heightDelta = landingPos.getY() - mob.blockPosition().getY();

        faceLocation(mob, landingCenter, 25.0F);
        mob.getLookControl().setLookAt(landingCenter.x, landingCenter.y + 0.6D, landingCenter.z, 25.0F, 25.0F);

        if (mob.onGround() && mind.parkourTicks == 0) {
            mob.setSprinting(true);
            mob.getNavigation().moveTo(landingCenter.x, mob.getY(), landingCenter.z, PLAYER_SPRINT_SPEED);

            boolean atEdge = isNearEdgeInDirection(mob, flatDelta) || (heightDelta > 0 && flatDist <= 2.5D) || flatDist <= 1.4D;
            if (atEdge && mind.jumpCooldown <= 0) {
                Vec3 dir = flatDist > 0.001D ? flatDelta.normalize() : mob.getForward();

                double vSpeed = heightDelta >= 2 ? 0.54D : (heightDelta == 1 ? 0.48D : 0.42D);
                double hSpeed = Math.min(0.36D, Math.max(0.24D, flatDist * 0.08D + 0.08D));

                mob.getJumpControl().jump();
                mob.setDeltaMovement(dir.x * hSpeed, vSpeed, dir.z * hSpeed);
                mob.hasImpulse = true;
                mob.hurtMarked = true;
                mind.parkourTicks = 1;
                mind.jumpCooldown = 15;
            }
        } else if (!mob.onGround()) {
            mind.parkourTicks++;

            if (flatDist <= 0.45D) {
                Vec3 curVel = mob.getDeltaMovement();
                mob.setDeltaMovement(curVel.x * 0.5D, curVel.y, curVel.z * 0.5D);
            } else {
                Vec3 airDir = flatDelta.normalize();
                Vec3 curVel = mob.getDeltaMovement();
                double curH = Math.sqrt(curVel.x * curVel.x + curVel.z * curVel.z);
                double targetH = Math.min(curH, 0.35D);
                mob.setDeltaMovement(airDir.x * targetH, curVel.y, airDir.z * targetH);
            }
        } else if (mob.onGround() && mind.parkourTicks >= 3) {
            Vec3 cur = mob.getDeltaMovement();
            mob.setDeltaMovement(cur.x * 0.1D, cur.y, cur.z * 0.1D);
            cancelParkour(mind, 20);
        }

        if (mind.parkourTicks > 30) {
            cancelParkour(mind, 35);
        }
    }

    private void cancelParkour(LatexMind mind, int cooldown) {
        mind.isParkouring = false;
        mind.parkourLandingPos = null;
        mind.parkourTakeoffPos = null;
        mind.parkourTicks = 0;
        mind.parkourCooldown = cooldown;
    }

    private boolean isNearEdgeInDirection(ChangedEntity mob, Vec3 direction) {
        if (direction.lengthSqr() < 0.001D) return false;
        Vec3 stepAhead = mob.position().add(direction.normalize().scale(0.38D));
        BlockPos belowAhead = BlockPos.containing(stepAhead.x, mob.getY() - 0.5D, stepAhead.z);
        BlockState state = mob.level().getBlockState(belowAhead);
        return state.isAir() || state.canBeReplaced() || state.getCollisionShape(mob.level(), belowAhead).isEmpty();
    }

    private boolean isDangerousCliffInDirection(ChangedEntity mob, Vec3 direction) {
        if (!mob.onGround()) return false;
        if (direction.lengthSqr() < 0.001D) return false;
        Vec3 ahead = mob.position().add(direction.normalize().scale(0.85D));
        BlockPos checkPos = BlockPos.containing(ahead);

        // Check if there is a 3+ block drop in front
        for (int depth = 0; depth <= 3; depth++) {
            BlockPos dropCheck = checkPos.below(depth);
            BlockState state = mob.level().getBlockState(dropCheck);
            if (!state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(mob.level(), dropCheck).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isDangerousCliffAhead(ChangedEntity mob) {
        if (!mob.onGround()) return false;
        Vec3 forward = mob.getForward();
        return isDangerousCliffInDirection(mob, forward);
    }

    private boolean canSprintJumpInDirection(ChangedEntity mob, Vec3 direction) {
        if (!mob.onGround() || mob.isInWater() || mob.isInLava() || mob.hurtTime > 0) return false;
        if (direction.lengthSqr() < 0.001D) return false;
        Vec3 normDir = direction.normalize();

        if (isDangerousCliffInDirection(mob, normDir)) return false;

        // Check 1.2m and 2.2m ahead for solid ground and clearance
        for (double step : new double[]{1.2D, 2.2D}) {
            Vec3 checkPos = mob.position().add(normDir.scale(step));
            BlockPos feetPos = BlockPos.containing(checkPos.x, mob.getY() + 0.1D, checkPos.z);
            BlockPos headPos = feetPos.above();
            BlockPos groundPos = feetPos.below();

            // Ground check: must not be a deep chasm (2+ block drop)
            BlockState groundState = mob.level().getBlockState(groundPos);
            if (groundState.isAir() || groundState.canBeReplaced() || groundState.getCollisionShape(mob.level(), groundPos).isEmpty()) {
                BlockState groundBelow = mob.level().getBlockState(groundPos.below());
                if (groundBelow.isAir() || groundBelow.canBeReplaced() || groundBelow.getCollisionShape(mob.level(), groundPos.below()).isEmpty()) {
                    return false;
                }
            }

            // Headroom check: no low ceiling or full wall
            BlockState headState = mob.level().getBlockState(headPos);
            if (!headState.isAir() && !headState.canBeReplaced() && !headState.getCollisionShape(mob.level(), headPos).isEmpty()) {
                return false;
            }

            // Feet check: if blocked, ensure it is at most a 1-block step up
            BlockState feetState = mob.level().getBlockState(feetPos);
            if (!feetState.isAir() && !feetState.canBeReplaced() && !feetState.getCollisionShape(mob.level(), feetPos).isEmpty()) {
                BlockState aboveHead = mob.level().getBlockState(feetPos.above(2));
                if (!aboveHead.isAir() && !aboveHead.canBeReplaced() && !aboveHead.getCollisionShape(mob.level(), feetPos.above(2)).isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean canOpenTerrainSprintJump(ChangedEntity mob, LivingEntity target) {
        if (!mob.onGround() || isDangerousCliffAhead(mob)) return false;
        double dist = mob.distanceTo(target);
        if (dist < 3.2D || dist > 20.0D) return false;
        if (!mob.hasLineOfSight(target)) return false;

        Vec3 toTarget = target.position().subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
        if (toTarget.lengthSqr() < 0.001D) return false;
        Vec3 jumpDir = toTarget.normalize();

        // Must be reasonably facing target (within ~60 degrees) before initiating jump
        Vec3 forward = mob.getForward().multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() > 0.001D) {
            Vec3 normFwd = forward.normalize();
            if (normFwd.dot(jumpDir) < 0.5D) {
                return false;
            }
        }

        return canSprintJumpInDirection(mob, jumpDir);
    }

    @Nullable
    private BlockPos findParkourJump(ChangedEntity mob, @Nullable LivingEntity target) {
        Vec3 dest = target != null ? target.position() : (mindHasLastSeen(mob) ? Vec3.atCenterOf(LatexMindStore.get(mob).lastSeenPos) : null);
        if (dest == null) return null;

        BlockPos mobPos = mob.blockPosition();
        Vec3 toDest = dest.subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
        double desiredDist = toDest.length();
        if (desiredDist < 1.4D) return null;

        BlockPos bestLanding = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int dy = -3; dy <= 2; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double hDistSqr = dx * dx + dz * dz;
                    if (hDistSqr < 1.0D || hDistSqr > 18.0D) continue;

                    BlockPos rawCandidate = mobPos.offset(dx, dy, dz);
                    BlockPos candidateLanding = resolveStandableLanding(mob, rawCandidate);
                    if (candidateLanding == null) continue;
                    if (candidateLanding.equals(mobPos)) continue;

                    if (!hasClearJumpTrajectory(mob, candidateLanding)) continue;

                    Vec3 candCenter = Vec3.atCenterOf(candidateLanding);
                    double distToDest = candCenter.distanceToSqr(dest);
                    if (distToDest > mob.distanceToSqr(dest) + 3.0D && target != null && mob.distanceTo(target) > 3.0D) continue;

                    Vec3 candOffset = candCenter.subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                    double alignment = toDest.normalize().dot(candOffset);
                    if (alignment < 0.1D && desiredDist > 3.0D) continue;

                    int landingDy = candidateLanding.getY() - mobPos.getY();
                    double score = distToDest - alignment * 6.0D + Math.abs(landingDy) * 1.5D;
                    if (score < bestScore) {
                        bestScore = score;
                        bestLanding = candidateLanding.immutable();
                    }
                }
            }
        }

        return bestLanding;
    }

    @Nullable
    private BlockPos resolveStandableLanding(ChangedEntity mob, BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        if (!state.isAir() && !state.getCollisionShape(mob.level(), pos).isEmpty()) {
            BlockState aboveFeet = mob.level().getBlockState(pos.above());
            BlockState head = mob.level().getBlockState(pos.above(2));
            if ((aboveFeet.isAir() || aboveFeet.canBeReplaced()) && (head.isAir() || head.canBeReplaced())) {
                return pos.above();
            }
        }

        BlockState belowState = mob.level().getBlockState(pos.below());
        if (!belowState.isAir() && !belowState.getCollisionShape(mob.level(), pos.below()).isEmpty()) {
            BlockState feet = mob.level().getBlockState(pos);
            BlockState head = mob.level().getBlockState(pos.above());
            if ((feet.isAir() || feet.canBeReplaced()) && (head.isAir() || head.canBeReplaced())) {
                return pos;
            }
        }

        return null;
    }

    private boolean mindHasLastSeen(ChangedEntity mob) {
        LatexMind mind = LatexMindStore.get(mob);
        return mind != null && mind.lastSeenPos != null;
    }

    private boolean hasClearJumpTrajectory(ChangedEntity mob, BlockPos landing) {
        Vec3 start = mob.getEyePosition();
        Vec3 end = Vec3.atCenterOf(landing).add(0.0D, 0.6D, 0.0D);
        Vec3 delta = end.subtract(start);
        int steps = Math.max(3, (int)Math.ceil(delta.length() / 0.5D));

        for (int i = 1; i < steps; i++) {
            double fraction = (double) i / steps;
            double arcY = Math.sin(fraction * Math.PI) * 0.75D;
            Vec3 point = start.add(delta.scale(fraction)).add(0.0D, arcY, 0.0D);
            BlockPos blockAt = BlockPos.containing(point);
            BlockState state = mob.level().getBlockState(blockAt);
            if (!state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(mob.level(), blockAt).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void faceLocation(ChangedEntity mob, Vec3 targetCenter, float maxTurnStep) {
        Vec3 delta = targetCenter.subtract(mob.getEyePosition());
        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDist < 0.01D) {
            return;
        }

        float desiredYaw = (float)(Mth.atan2(delta.z, delta.x) * (180.0F / Math.PI)) - 90.0F;
        float desiredPitch = (float)(-(Mth.atan2(delta.y, horizontalDist) * (180.0F / Math.PI)));

        float smoothedYaw = rotlerp(mob.getYRot(), desiredYaw, maxTurnStep);
        float smoothedPitch = rotlerp(mob.getXRot(), desiredPitch, maxTurnStep);

        mob.setYRot(smoothedYaw);
        mob.setXRot(smoothedPitch);
        mob.setYBodyRot(rotlerp(mob.yBodyRot, smoothedYaw, maxTurnStep * 0.8F));
        mob.yHeadRot = rotlerp(mob.yHeadRot, smoothedYaw, maxTurnStep + 2.0F);
        mob.yHeadRotO = mob.yHeadRot;
        mob.xRotO = mob.getXRot();
    }

    private void faceTargetEye(ChangedEntity mob, Vec3 targetCenter, float maxTurnStep) {
        Vec3 delta = targetCenter.subtract(mob.getEyePosition());
        double horizontalDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDist < 0.01D) {
            return;
        }

        float desiredYaw = (float)(Mth.atan2(delta.z, delta.x) * (180.0F / Math.PI)) - 90.0F;
        float desiredPitch = (float)(-(Mth.atan2(delta.y, horizontalDist) * (180.0F / Math.PI)));

        float smoothedPitch = rotlerp(mob.getXRot(), desiredPitch, maxTurnStep);
        float smoothedHeadYaw = rotlerp(mob.yHeadRot, desiredYaw, maxTurnStep);

        mob.setXRot(smoothedPitch);
        mob.yHeadRot = smoothedHeadYaw;
        mob.yHeadRotO = mob.yHeadRot;
        mob.xRotO = mob.getXRot();

        if (mob.distanceToSqr(targetCenter) < 16.0D || (mob.getTarget() != null && mob.hasLineOfSight(mob.getTarget()))) {
            mob.setYRot(rotlerp(mob.getYRot(), desiredYaw, maxTurnStep * 0.7F));
            mob.setYBodyRot(rotlerp(mob.yBodyRot, desiredYaw, maxTurnStep * 0.7F));
        }
    }

    private void chase(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        if (target == null) return;

        equipBestCombatTool(mob);
        mob.setXxa(0.0F); // No strafing while chasing across paths

        boolean cliff = isDangerousCliffAhead(mob);
        mob.setShiftKeyDown(cliff);

        double dist = mob.distanceTo(target);
        boolean sprint = !cliff && (mind.isEnraged() || dist > 3.5D);
        mob.setSprinting(sprint);
        double speed = sprint ? PLAYER_SPRINT_SPEED : resolveChaseSpeed(target, mind);

        Path reachPath = resolveChasePath(mob, target);
        if (reachPath != null) {
            mob.getNavigation().moveTo(reachPath, speed);
        } else {
            mob.getNavigation().moveTo(target, speed);
        }

        // Stare directly at the player/target while chasing
        faceTarget(mob, target, 40.0F);
        mob.getLookControl().setLookAt(target, 40.0F, 40.0F);

        Vec3 toTarget = target.position().subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 jumpDir = toTarget.lengthSqr() > 0.001D ? toTarget.normalize() : mob.getForward();

        // Standard sprint-jumping cadence
        if (sprint && canOpenTerrainSprintJump(mob, target) && mind.jumpCooldown <= 0) {
            float targetYaw = (float)(Mth.atan2(jumpDir.z, jumpDir.x) * (180.0F / Math.PI)) - 90.0F;
            mob.setYRot(targetYaw);
            mob.setYBodyRot(targetYaw);
            mob.yHeadRot = targetYaw;
            mob.yHeadRotO = targetYaw;
            mob.yRotO = targetYaw;

            mob.getJumpControl().jump();

            // Set forward horizontal velocity directly towards the target at calibrated speed
            double hSpeed = 0.28D;
            mob.setDeltaMovement(jumpDir.x * hSpeed, mob.getDeltaMovement().y, jumpDir.z * hSpeed);
            mob.hasImpulse = true;
            mind.jumpCooldown = 9;
            mind.isSprintJumping = true;
            mind.sprintJumpTicks = 0;
        } else if (mob.horizontalCollision && mob.onGround() && mind.jumpCooldown <= 0) {
            mob.getJumpControl().jump();
            mob.setDeltaMovement(jumpDir.x * 0.20D, mob.getDeltaMovement().y, jumpDir.z * 0.20D);
            mob.hasImpulse = true;
            mind.jumpCooldown = 10;
        }

        // Mid-air sprint jump stabilization: keep forward momentum stably pointed at the player
        if (mind.isSprintJumping) {
            if (mob.hurtTime > 0 || mob.isInWater() || mob.isInLava() || mob.horizontalCollision || dist <= 2.8D) {
                mind.isSprintJumping = false;
                mind.sprintJumpTicks = 0;
            } else if (!mob.onGround()) {
                mind.sprintJumpTicks++;
                if (mind.sprintJumpTicks > 15) {
                    mind.isSprintJumping = false;
                    mind.sprintJumpTicks = 0;
                } else {
                    Vec3 curVel = mob.getDeltaMovement();
                    Vec3 airToTarget = target.position().subtract(mob.position()).multiply(1.0D, 0.0D, 1.0D);
                    if (airToTarget.lengthSqr() > 0.001D) {
                        Vec3 airDir = airToTarget.normalize();
                        double curH = Math.sqrt(curVel.x * curVel.x + curVel.z * curVel.z);
                        double targetH = Math.min(Math.max(curH, 0.30D), 0.35D);
                        mob.setDeltaMovement(airDir.x * targetH, curVel.y, airDir.z * targetH);
                        mob.hasImpulse = true;

                        float airYaw = (float)(Mth.atan2(airDir.z, airDir.x) * (180.0F / Math.PI)) - 90.0F;
                        mob.setYRot(airYaw);
                        mob.setYBodyRot(airYaw);
                    }
                    faceTarget(mob, target, 45.0F);
                    mob.getLookControl().setLookAt(target, 45.0F, 45.0F);
                }
            } else if (mob.onGround() && mind.sprintJumpTicks >= 2) {
                mind.isSprintJumping = false;
                mind.sprintJumpTicks = 0;
            }
        }
    }

    private void watchTarget(ChangedEntity mob, @Nullable LivingEntity target) {
        mob.setSprinting(false);
        mob.setShiftKeyDown(false);
        mob.setZza(0.0F);
        mob.setXxa(0.0F);

        if (target != null) {
            moveNearWatchPosition(mob, target);
            faceTarget(mob, target, 30.0F);
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        } else {
            mob.getNavigation().stop();
        }
    }

    private void moveNearWatchPosition(ChangedEntity mob, LivingEntity target) {
        Vec3 toMob = mob.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (toMob.lengthSqr() < 0.001D) {
            toMob = new Vec3(1.0D, 0.0D, 0.0D);
        }

        double distance = Math.sqrt(toMob.lengthSqr());
        if (distance > 3.5D) {
            Path path = createReachPath(mob, target);
            if (path != null) {
                mob.getNavigation().moveTo(path, PLAYER_WATCH_SPEED);
            } else {
                mob.getNavigation().moveTo(target, PLAYER_WATCH_SPEED);
            }
        } else {
            mob.getNavigation().stop();
        }
    }

    private void attack(ChangedEntity mob, @Nullable LivingEntity target) {
        if (target == null) return;

        equipBestCombatTool(mob);
        LatexMind mind = LatexMindStore.get(mob);

        double distance = mob.distanceTo(target);
        boolean hasDirectHitPath = mob.hasLineOfSight(target) && hasDirectUnobstructedHitRay(mob, target);

        // Never swing or attack if there is a solid wall/obstacle blocking the direct line!
        if (!hasDirectHitPath || distance > ATTACK_RANGE + 0.35D) {
            lastAttackDistance = Double.NaN;
            chase(mob, mind, target);
            return;
        }

        // If the target is actively pulling away faster than our combat footwork tracks,
        // there's no attack to dodge around - just keep closing the gap at full speed.
        boolean targetRetreating = !Double.isNaN(lastAttackDistance) && distance > lastAttackDistance + 0.02D;
        lastAttackDistance = distance;
        if (targetRetreating) {
            chase(mob, mind, target);
            return;
        }

        faceTarget(mob, target, 30.0F);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        boolean cliff = isDangerousCliffAhead(mob);
        mob.setShiftKeyDown(cliff);

        // Strafing ONLY happens if strictly within 2.0 blocks of the player AND NOT at a cliff edge
        if (!cliff && distance <= 2.0D) {
            mob.setXxa((float)(mind.combatStrafeDir * 0.35F));
            mob.setZza(distance > 1.4D ? 0.25F : -0.10F);
        } else {
            mob.setXxa(0.0F);
            mob.setZza(distance > 2.0D ? 0.35F : 0.0F);
        }

        if (mind.attackCooldown > 0) return;

        // Player-like Critical Jump Hit: Only jump on wide ground with no ledges
        if (mob.onGround() && !cliff && mob.getRandom().nextFloat() < 0.65F) {
            mob.getJumpControl().jump();
        }

        mob.swing(InteractionHand.MAIN_HAND);
        if (mob.distanceTo(target) <= ATTACK_RANGE + 0.15D && hasDirectHitPath) {
            mob.doHurtTarget(target);

            // Spawn crit particles on jumping strike
            if (!mob.onGround() && mob.getDeltaMovement().y < 0.0D && mob.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.1D);
            }

            Vec3 knock = target.position().subtract(mob.position()).normalize().scale(0.25D);
            target.push(knock.x, 0.08D, knock.z);
            mind.attackCooldown = ATTACK_COOLDOWN;
        }
    }

    private void rangedAttack(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        mob.setXxa(0.0F);

        if (target == null || !target.isAlive()) {
            mob.stopUsingItem();
            mind.bowChargeTicks = 0;
            return;
        }

        equipBowForRangedAttack(mob);
        setBestShieldOffhand(mob);
        if (!(mob.getMainHandItem().getItem() instanceof BowItem)) {
            // Lost access to a bow mid-decision (e.g. it broke) - fall back to melee.
            mind.bowChargeTicks = 0;
            chase(mob, mind, target);
            return;
        }

        faceTarget(mob, target, 30.0F);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distance = mob.distanceTo(target);
        boolean hasDirectHitPath = mob.hasLineOfSight(target) && hasDirectUnobstructedHitRay(mob, target);

        // Kite: back off if the target closes in, drift closer if it's fleeing out of range
        if (distance < BOW_KITE_DISTANCE) {
            Vec3 away = mob.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 0.001D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 retreatPos = mob.position().add(away.normalize().scale(5.0D));
            boolean cliff = isDangerousCliffAhead(mob);
            mob.setShiftKeyDown(cliff);
            mob.getNavigation().moveTo(retreatPos.x, retreatPos.y, retreatPos.z, cliff ? PLAYER_SNEAK_SPEED : PLAYER_RETREAT_SPEED);
        } else if (distance > BOW_MAX_RANGE - 2.0D) {
            mob.setShiftKeyDown(false);
            mob.getNavigation().moveTo(target, PLAYER_WALK_SPEED);
        } else {
            mob.setShiftKeyDown(false);
            mob.getNavigation().stop();
        }

        if (!hasDirectHitPath) {
            // Can't see the target to loose an arrow right now - stop drawing and wait for a clear shot.
            if (mob.isUsingItem()) {
                mob.stopUsingItem();
            }
            mind.bowChargeTicks = 0;
            return;
        }

        if (!mob.isUsingItem()) {
            mob.startUsingItem(InteractionHand.MAIN_HAND);
        }

        mind.bowChargeTicks++;

        if (mind.bowChargeTicks >= BOW_CHARGE_TICKS_FULL) {
            fireArrow(mob, target);
            mob.stopUsingItem();
            mind.bowChargeTicks = 0;
            mind.rangedAttackCooldown = RANGED_ATTACK_COOLDOWN;
        }
    }

    private void fireArrow(ChangedEntity mob, LivingEntity target) {
        ItemStack bowStack = mob.getMainHandItem();
        ItemStack arrowStack = findArrowStack(mob);
        boolean consumesAmmo = !arrowStack.isEmpty();
        ItemStack projectileStack = consumesAmmo ? arrowStack : new ItemStack(Items.ARROW);

        AbstractArrow arrow;
        if (projectileStack.getItem() instanceof ArrowItem arrowItem) {
            arrow = arrowItem.createArrow(mob.level(), projectileStack, mob);
        } else {
            arrow = new Arrow(mob.level(), mob);
        }

        arrow.setPos(mob.getX(), mob.getEyeY() - 0.1D, mob.getZ());

        double dx = target.getX() - mob.getX();
        double dy = target.getY(0.3333D) - arrow.getY();
        double dz = target.getZ() - mob.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontalDist * 0.2D, dz, ARROW_VELOCITY, ARROW_INACCURACY);

        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bowStack);
        if (power > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + power * 0.5D + 0.5D);
        }

        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bowStack);
        if (punch > 0) {
            arrow.setKnockback(punch);
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bowStack) > 0) {
            arrow.setSecondsOnFire(100);
        }

        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARROW_SHOOT, mob.getSoundSource(),
                1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
        mob.level().addFreshEntity(arrow);
        mob.swing(InteractionHand.MAIN_HAND);

        if (consumesAmmo) {
            consumeOneMatchingItem(mob, arrowStack.copyWithCount(1));
        }
    }

    private boolean hasUsableBow(ChangedEntity mob) {
        return !findBestBow(mob).isEmpty() && !findArrowStack(mob).isEmpty();
    }

    private ItemStack findBestBow(ChangedEntity mob) {
        if (mob.getMainHandItem().getItem() instanceof BowItem) {
            return mob.getMainHandItem();
        }
        if (mob.getOffhandItem().getItem() instanceof BowItem) {
            return mob.getOffhandItem();
        }

        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.getItem() instanceof BowItem) {
                    return slotStack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findArrowStack(ChangedEntity mob) {
        if (mob.getOffhandItem().getItem() instanceof ArrowItem) {
            return mob.getOffhandItem();
        }

        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.getItem() instanceof ArrowItem) {
                    return slotStack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private void equipBowForRangedAttack(ChangedEntity mob) {
        if (mob.getMainHandItem().getItem() instanceof BowItem) {
            return;
        }

        if (mob.getOffhandItem().getItem() instanceof BowItem) {
            ItemStack bow = mob.getOffhandItem().copy();
            ItemStack main = mob.getMainHandItem().copy();
            mob.setItemInHand(InteractionHand.OFF_HAND, main);
            mob.setItemInHand(InteractionHand.MAIN_HAND, bow);
            return;
        }

        ItemStack main = mob.getMainHandItem();
        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.getItem() instanceof BowItem) {
                    inv.setStackInSlot(i, main.copy());
                    mob.setItemInHand(InteractionHand.MAIN_HAND, slotStack.copy());
                    return;
                }
            }
        }
    }

    private void manageShieldBlocking(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target, State currentState) {
        if (!(mob.getOffhandItem().getItem() instanceof ShieldItem)) {
            return;
        }

        // Never hide behind the shield while lining up or loosing an arrow of our own.
        if (currentState == State.RANGED_ATTACK) {
            stopShieldingIfIdle(mob);
            return;
        }

        boolean aboutToSwing = currentState == State.ATTACK && mind.attackCooldown <= 1;
        if (aboutToSwing) {
            stopShieldingIfIdle(mob);
            return;
        }

        boolean inMeleeExchange = target != null && target.isAlive()
                && mob.distanceToSqr(target) <= SHIELD_BLOCK_RANGE * SHIELD_BLOCK_RANGE
                && mob.hasLineOfSight(target);
        boolean incomingProjectile = hasIncomingProjectile(mob);

        if (inMeleeExchange || incomingProjectile) {
            if (!mob.isUsingItem()) {
                mob.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else {
            stopShieldingIfIdle(mob);
        }
    }

    private boolean hasIncomingProjectile(ChangedEntity mob) {
        AABB box = mob.getBoundingBox().inflate(PROJECTILE_WATCH_RANGE);
        for (Projectile projectile : mob.level().getEntitiesOfClass(Projectile.class, box)) {
            if (projectile.getOwner() == mob) continue;

            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 0.01D) continue;

            Vec3 toMob = mob.position().subtract(projectile.position());
            if (toMob.lengthSqr() > PROJECTILE_WATCH_RANGE * PROJECTILE_WATCH_RANGE) continue;

            if (velocity.normalize().dot(toMob.normalize()) > 0.85D) {
                return true;
            }
        }
        return false;
    }

    private void manageSwimming(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        boolean inWater = mob.isInWater();
        if (mob.isSwimming() != inWater) {
            mob.setSwimming(inWater);
        }

        if (!inWater) {
            return;
        }

        Vec3 aimPos = null;
        if (target != null && target.isAlive()) {
            aimPos = target.position();
        } else if (mind.lastSeenPos != null) {
            aimPos = Vec3.atCenterOf(mind.lastSeenPos);
        }

        if (aimPos == null) {
            return;
        }

        // The ground navigator steers horizontally fine once water is de-penalized, but it doesn't
        // reliably dive or surface toward a submerged target - nudge vertical velocity directly.
        double deltaY = aimPos.y - mob.getY();
        if (Math.abs(deltaY) > 0.35D) {
            double vSpeed = Mth.clamp(deltaY * 0.1D, -0.16D, 0.16D);
            Vec3 vel = mob.getDeltaMovement();
            mob.setDeltaMovement(vel.x, vSpeed, vel.z);
            mob.hasImpulse = true;
        }
    }

    private boolean hasDirectUnobstructedHitRay(ChangedEntity mob, LivingEntity target) {
        HitResult hit = mob.level().clip(new ClipContext(
                mob.getEyePosition(),
                target.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mob
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void breakObstacle(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        mob.setXxa(0.0F);
        if (target == null) return;
        if (hasReachablePath(mob, mind, target)) {
            resetBlockBreaking(mob, mind);
            mind.plannedBreakPos = null;
            chase(mob, mind, target);
            return;
        }

        BlockPos pos = mind.activeBreakPos;
        if (pos == null) {
            pos = mind.plannedBreakPos != null ? mind.plannedBreakPos : findBreakTarget(mob, target);
        }
        if (pos == null) {
            resetBlockBreaking(mob, mind);
            mind.plannedBreakPos = null;
            stopShieldingIfIdle(mob);
            chase(mob, mind, target);
            return;
        }

        BlockState state = mob.level().getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mob.level(), pos) < 0 || mind.breakCooldown > 0 || !canBreakBlock(mob, pos, state)) {
            resetBlockBreaking(mob, mind);
            mind.plannedBreakPos = null;
            stopShieldingIfIdle(mob);
            chase(mob, mind, target);
            return;
        }

        if (mind.activeBreakPos == null || !mind.activeBreakPos.equals(pos)) {
            resetBlockBreaking(mob, mind);
            mind.activeBreakPos = pos.immutable();
        }

        Vec3 breakCenter = Vec3.atCenterOf(pos);
        if (mob.distanceToSqr(breakCenter) > 6.25D) {
            mob.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, PLAYER_BREAK_SPEED);
        } else {
            mob.getNavigation().stop();
            mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
        }

        faceBlock(mob, pos, 10.0F);

        ItemStack tool = findBestMiningTool(mob, state);
        if (!tool.isEmpty()) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, tool);
        }

        mob.stopUsingItem();

        mind.blockBreakTicks++;
        if (mind.blockBreakTicks % 4 == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
        }

        mind.blockBreakProgress += getBreakProgressPerTick(mob, pos, state, tool);

        if (mob.level() instanceof ServerLevel server) {
            int stage = Math.min(9, Math.max(0, Mth.floor(mind.blockBreakProgress * 10.0F)));
            server.destroyBlockProgress(mob.getId(), pos, stage);
        }

        if (mind.blockBreakProgress >= 1.0F) {
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level().destroyBlock(pos, true, mob);
            resetBlockBreaking(mob, mind);
            mind.breakCooldown = 8;
            mind.recentBreakTicks = 20;
            mind.plannedBreakPos = null;
            stopShieldingIfIdle(mob);
        }
    }

    private void resetBlockBreaking(ChangedEntity mob, LatexMind mind) {
        if (mob.level() instanceof ServerLevel server && mind.activeBreakPos != null) {
            server.destroyBlockProgress(mob.getId(), mind.activeBreakPos, -1);
        }

        mind.activeBreakPos = null;
        mind.blockBreakTicks = 0;
        mind.blockBreakProgress = 0.0F;
    }

    private void faceBlock(ChangedEntity mob, BlockPos pos, float maxTurnStep) {
        Vec3 center = Vec3.atCenterOf(pos);
        faceLocation(mob, center, maxTurnStep);
    }

    private float getBreakProgressPerTick(ChangedEntity mob, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(mob.level(), pos);
        if (hardness <= 0.0F) {
            return 1.0F;
        }

        float speed = 1.0F;
        if (!tool.isEmpty()) {
            speed = Math.max(1.0F, tool.getDestroySpeed(state));
        }

        if (canBreakBlock(mob, pos, state)) {
            return Math.max(0.08F, speed / (hardness * 8.0F));
        }

        return Math.max(0.03F, speed / (hardness * 24.0F));
    }

    private void buildTowardTarget(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        mob.setXxa(0.0F);
        if (target == null) return;

        boolean towerMode = shouldTowerUp(mob, target);
        if (!towerMode) {
            pruneImaginedBuildPath(mob, mind);
        }

        BlockPos placePos = towerMode
                ? findBuildPlacement(mob, target)
                : (!mind.imaginedBuildPath.isEmpty()
                   ? mind.imaginedBuildPath.get(0)
                   : (mind.plannedBuildPos != null ? mind.plannedBuildPos : findBuildPlacement(mob, target)));
        if (placePos == null) {
            mind.plannedBuildPos = null;
            mind.imaginedBuildPath.clear();
            chase(mob, mind, target);
            return;
        }

        ItemStack blockStack = findBestBuildingBlock(mob);
        if (blockStack.isEmpty()) {
            ItemEntity source = findBestBlockPickup(mob);
            if (source != null) {
                mob.getNavigation().moveTo(source, PLAYER_BUILD_SPEED);
                if (mob.distanceTo(source) < PICKUP_RANGE) {
                    pickUpItemToHands(mob, source);
                }
            } else {
                mind.plannedBuildPos = null;
                chase(mob, mind, target);
            }
            return;
        }

        if (!(blockStack.getItem() instanceof BlockItem blockItem)) {
            mind.plannedBuildPos = null;
            mind.imaginedBuildPath.clear();
            chase(mob, mind, target);
            return;
        }

        BlockState placeState = blockItem.getBlock().defaultBlockState();
        boolean towerPlacement = towerMode && isTowerPlacement(mob, target, placePos);
        boolean canPlace = towerPlacement ? canPlaceTowerBlockAt(mob, placePos, placeState) : canPlaceBlockAt(mob, placePos, placeState);
        if (!canPlace) {
            if (!towerMode && !mind.imaginedBuildPath.isEmpty() && placePos.equals(mind.imaginedBuildPath.get(0))) {
                mind.imaginedBuildPath.remove(0);
            }
            mind.plannedBuildPos = null;
            chase(mob, mind, target);
            return;
        }

        double placementSpeed = isCautiousPlacement(mob, placePos) ? PLAYER_SNEAK_SPEED : PLAYER_BUILD_SPEED;
        if (!moveNearPlacement(mob, placePos)) {
            mob.getNavigation().moveTo(placePos.getX() + 0.5D, placePos.getY(), placePos.getZ() + 0.5D, placementSpeed);
            return;
        }

        if (towerPlacement) {
            if (!handleTowerBuildStep(mob, mind, target, blockStack, placeState)) {
                mind.plannedBuildPos = null;
                chase(mob, mind, target);
            }
            return;
        }

        mob.setItemInHand(InteractionHand.MAIN_HAND, blockStack);
        faceBlock(mob, placePos, 15.0F);
        mob.swing(InteractionHand.MAIN_HAND);

        if (mob.level().setBlock(placePos, placeState, 3)) {
            SoundType sound = placeState.getSoundType();
            mob.level().playSound(null, placePos, sound.getPlaceSound(), mob.getSoundSource(), sound.getVolume(), sound.getPitch());
            consumeOneMatchingItem(mob, blockStack);
            mind.buildCooldown = 10;
            if (!mind.imaginedBuildPath.isEmpty() && placePos.equals(mind.imaginedBuildPath.get(0))) {
                mind.imaginedBuildPath.remove(0);
            }
            mind.plannedBuildPos = mind.imaginedBuildPath.isEmpty() ? null : mind.imaginedBuildPath.get(0);
            if (towerPlacement) {
                mind.jumpCooldown = 8;
            } else if (target.getY() > mob.getY() + 1.2D && mob.onGround()) {
                mob.getJumpControl().jump();
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
        mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
        mob.getLookControl().setLookAt(target, 8.0F, 8.0F);

        if (horizontalOffsetSqr > 0.03D) {
            mob.getNavigation().moveTo(center.x, mob.getY(), center.z, TOWER_ALIGN_SPEED);
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
            mob.getJumpControl().jump();
            mind.jumpCooldown = 4;
        }

        return true;
    }

    private void search(ChangedEntity mob, LatexMind mind) {
        mob.setXxa(0.0F);
        if (mind.lastSeenPos == null) return;

        Path searchPath = mob.getNavigation().createPath(mind.lastSeenPos, 0);
        if (searchPath != null) {
            mob.getNavigation().moveTo(searchPath, PLAYER_SEARCH_SPEED);
        } else {
            mob.getNavigation().moveTo(
                    mind.lastSeenPos.getX() + 0.5D,
                    mind.lastSeenPos.getY(),
                    mind.lastSeenPos.getZ() + 0.5D,
                    PLAYER_SEARCH_SPEED
            );
        }

        Vec3 lastSeenCenter = Vec3.atCenterOf(mind.lastSeenPos);
        faceTargetEye(mob, lastSeenCenter, 20.0F);
        mob.getLookControl().setLookAt(lastSeenCenter.x, lastSeenCenter.y, lastSeenCenter.z, 20.0F, 20.0F);

        if (mob.blockPosition().closerThan(mind.lastSeenPos, SEARCH_REACH)) {
            mind.searchTicks = SEARCH_TIMEOUT;
        } else if (mind.stuckTicks > 10) {
            mind.searchTicks += 5;
        }
    }

    private void loot(ChangedEntity mob) {
        mob.setXxa(0.0F);
        ItemEntity item = findBestItemEntity(mob);
        if (item == null) return;

        mob.getNavigation().moveTo(item, PLAYER_LOOT_SPEED);
        mob.getLookControl().setLookAt(item, 25.0F, 25.0F);

        if (mob.distanceTo(item) < PICKUP_RANGE) {
            pickUpItemToHands(mob, item);
        }
    }

    private void reposition(ChangedEntity mob, LatexMind mind, @Nullable LivingEntity target) {
        mob.setXxa(0.0F);
        if (target == null) return;
        mob.setSprinting(false);

        Vec3 away = mob.position().subtract(target.position());
        if (away.lengthSqr() < 0.001D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 retreat = mob.position().add(away.normalize().scale(4.0D));
        mob.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, PLAYER_WALK_SPEED);
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

    private double resolveChaseSpeed(LivingEntity target, LatexMind mind) {
        if (mind.isEnraged()) {
            return PLAYER_SPRINT_SPEED;
        }
        return target.isSprinting() ? PLAYER_SPRINT_SPEED : PLAYER_WALK_SPEED;
    }

    private void faceTarget(ChangedEntity mob, LivingEntity target, float maxTurnStep) {
        Vec3 eyePos = target.getEyePosition();
        faceTargetEye(mob, eyePos, maxTurnStep);
        if (mob.hasLineOfSight(target)) {
            Vec3 delta = eyePos.subtract(mob.getEyePosition());
            float desiredYaw = (float)(Mth.atan2(delta.z, delta.x) * (180.0F / Math.PI)) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), desiredYaw, maxTurnStep));
            mob.setYBodyRot(rotlerp(mob.yBodyRot, desiredYaw, maxTurnStep));
        }
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

        mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).ifPresent(inv -> {
            ItemStack remaining = inv.addItem(stack.copy());
            if (remaining.isEmpty()) {
                stack.setCount(0);
                itemEntity.discard();
            } else {
                stack.setCount(remaining.getCount());
                itemEntity.setItem(remaining);
            }
        });
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

    @Nullable
    private LivingEntity resolveTarget(ChangedEntity mob, LatexMind mind) {
        LivingEntity grudgeTarget = findGrudgeTarget(mob, mind);
        if (grudgeTarget != null) {
            mob.setTarget(grudgeTarget);
            mind.targetId = grudgeTarget.getUUID();
            mind.triggerEnrage(240);
            return grudgeTarget;
        }

        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null && isValidAggroTarget(mob, currentTarget, mind)) {
            if (mind.targetId == null || !mind.targetId.equals(currentTarget.getUUID())) {
                mind.targetId = currentTarget.getUUID();
            }
            return currentTarget;
        }
        if (currentTarget != null) {
            mob.setTarget(null);
        }

        LivingEntity remembered = findRememberedTarget(mob, mind);
        if (remembered != null) {
            mob.setTarget(remembered);
            return remembered;
        }

        LivingEntity visibleTarget = findVisibleTarget(mob, mind);
        if (visibleTarget != null) {
            mob.setTarget(visibleTarget);
            mind.targetId = visibleTarget.getUUID();
            return visibleTarget;
        }

        return null;
    }

    @Nullable
    private LivingEntity findGrudgeTarget(ChangedEntity mob, LatexMind mind) {
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(MAX_REMEMBERED_TARGET_RANGE))) {
            if (candidate != mob && candidate.isAlive() && mind.isGrudgeKiller(candidate.getUUID(), mob.tickCount)) {
                if (mob.hasLineOfSight(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Nullable
    private Path resolveChasePath(ChangedEntity mob, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        boolean stale = cachedChasePath == null
                || mob.getNavigation().isDone()
                || mob.tickCount - cachedChasePathTick > PATH_CACHE_TICKS
                || cachedChaseTargetPos == null
                || !cachedChaseTargetPos.closerThan(targetPos, 2.0D)
                || !target.getUUID().equals(cachedChaseTargetId);

        if (stale) {
            cachedChasePath = createReachPath(mob, target);
            cachedChaseTargetPos = targetPos.immutable();
            cachedChaseTargetId = target.getUUID();
            cachedChasePathTick = mob.tickCount;
        }

        return cachedChasePath;
    }

    @Nullable
    private Path createReachPath(ChangedEntity mob, LivingEntity target) {
        Path directPath = mob.getNavigation().createPath(target, 0);
        if (directPath != null && directPath.canReach()) {
            return directPath;
        }

        Path approachPath = createNearbyReachPath(mob, target);
        if (approachPath != null) {
            return approachPath;
        }

        // If no full path exists, check if direct partial path brings us closer to target
        if (directPath != null && directPath.getNodeCount() > 0) {
            Node endNode = directPath.getEndNode();
            if (endNode != null) {
                double endDist = endNode.asBlockPos().distSqr(target.blockPosition());
                double mobDist = mob.blockPosition().distSqr(target.blockPosition());
                if (endDist < mobDist - 1.5D) {
                    return directPath;
                }
            }
        }

        return null;
    }

    @Nullable
    private Path createNearbyReachPath(ChangedEntity mob, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        Path bestPath = null;
        double bestDistSqr = Double.POSITIVE_INFINITY;

        for (int radius = 1; radius <= ALT_PATH_SEARCH_MAX_RADIUS; radius++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }

                        BlockPos candidate = targetPos.offset(dx, dy, dz);
                        if (!isStandableTargetPosition(mob, candidate)) {
                            continue;
                        }

                        double distToTarget = candidate.distSqr(targetPos);
                        if (distToTarget >= bestDistSqr) {
                            continue;
                        }

                        Path candidatePath = mob.getNavigation().createPath(candidate, 0);
                        if (candidatePath != null && candidatePath.canReach()) {
                            bestDistSqr = distToTarget;
                            bestPath = candidatePath;
                            if (distToTarget <= 2.25D) {
                                return bestPath;
                            }
                        }
                    }
                }
            }
        }

        return bestPath;
    }

    private boolean isStandableTargetPosition(ChangedEntity mob, BlockPos pos) {
        BlockState feet = mob.level().getBlockState(pos);
        BlockState head = mob.level().getBlockState(pos.above());
        if ((!feet.isAir() && !feet.canBeReplaced()) || (!head.isAir() && !head.canBeReplaced())) {
            return false;
        }

        BlockPos below = pos.below();
        BlockState support = mob.level().getBlockState(below);
        return !support.isAir() && !support.getCollisionShape(mob.level(), below).isEmpty();
    }

    private boolean hasReachablePath(ChangedEntity mob, LatexMind mind, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        BlockPos sourcePos = mob.blockPosition();
        if (mind.cachedPathTargetPos != null
                && mind.cachedPathSourcePos != null
                && mob.tickCount - mind.cachedPathTick <= PATH_CACHE_TICKS
                && mind.cachedPathTargetPos.closerThan(targetPos, 2.0D)
                && mind.cachedPathSourcePos.closerThan(sourcePos, 2.0D)) {
            return mind.cachedReachablePath;
        }

        mind.cachedPathTick = mob.tickCount;
        mind.cachedPathTargetPos = targetPos.immutable();
        mind.cachedPathSourcePos = sourcePos.immutable();
        mind.cachedReachablePath = createReachPath(mob, target) != null;
        return mind.cachedReachablePath;
    }

    @Nullable
    private State chooseTerrainAction(ChangedEntity mob, LatexMind mind, LivingEntity target) {
        BlockPos targetPos = target.blockPosition();
        BlockPos sourcePos = mob.blockPosition();
        if (mind.cachedTerrainState != null
                && mind.cachedTerrainTargetPos != null
                && mind.cachedTerrainSourcePos != null
                && mob.tickCount - mind.cachedTerrainTick <= TERRAIN_CACHE_TICKS
                && mind.cachedTerrainTargetPos.closerThan(targetPos, 2.0D)
                && mind.cachedTerrainSourcePos.closerThan(sourcePos, 2.0D)) {
            mind.plannedBreakPos = mind.cachedTerrainBreakPos;
            mind.plannedBuildPos = mind.cachedTerrainBuildPos;
            mind.imaginedBuildPath = new ArrayList<>(mind.cachedImaginedBuildPath);
            return mind.cachedTerrainState;
        }

        TerrainPlan plan = analyzeTerrainPlan(mob, mind, target);
        BlockPos breakPos = plan.breakPos();
        BlockPos buildPos = plan.buildPos();
        List<BlockPos> imaginedBuildPath = plan.imaginedBuildPath();
        double breakCost = plan.breakCost();
        double buildCost = plan.buildCost();

        mind.plannedBreakPos = Double.isFinite(breakCost) ? breakPos : null;
        mind.plannedBuildPos = Double.isFinite(buildCost) ? buildPos : null;
        mind.imaginedBuildPath = new ArrayList<>(imaginedBuildPath);

        if (!Double.isFinite(breakCost) && !Double.isFinite(buildCost)) {
            cacheTerrainPlan(mob, mind, targetPos, sourcePos, null, null, List.of(), null);
            return null;
        }

        State chosen;
        if (Double.isFinite(buildCost) && !Double.isFinite(breakCost)) {
            chosen = State.BUILD;
        } else if (Double.isFinite(buildCost) && mind.recentBreakTicks > 0) {
            chosen = State.BUILD;
        } else if (Double.isFinite(buildCost) && buildCost <= breakCost + BREAK_PREFERENCE_PENALTY) {
            chosen = State.BUILD;
        } else if (!Double.isFinite(buildCost) && Double.isFinite(breakCost)) {
            chosen = State.BREAK;
        } else {
            chosen = buildCost < breakCost ? State.BUILD : State.BREAK;
        }
        cacheTerrainPlan(mob, mind, targetPos, sourcePos, mind.plannedBreakPos, mind.plannedBuildPos, mind.imaginedBuildPath, chosen);
        return chosen;
    }

    private void cacheTerrainPlan(ChangedEntity mob, LatexMind mind, BlockPos targetPos, BlockPos sourcePos,
                                  @Nullable BlockPos breakPos, @Nullable BlockPos buildPos, List<BlockPos> imaginedBuildPath,
                                  @Nullable State chosen) {
        mind.cachedTerrainTick = mob.tickCount;
        mind.cachedTerrainTargetPos = targetPos.immutable();
        mind.cachedTerrainSourcePos = sourcePos.immutable();
        mind.cachedTerrainBreakPos = breakPos != null ? breakPos.immutable() : null;
        mind.cachedTerrainBuildPos = buildPos != null ? buildPos.immutable() : null;
        mind.cachedImaginedBuildPath = new ArrayList<>(imaginedBuildPath);
        mind.cachedTerrainState = chosen;
    }

    private TerrainPlan analyzeTerrainPlan(ChangedEntity mob, LatexMind mind, LivingEntity target) {
        BlockPos immediateBreak = findPlannedBreakTarget(mob, target);
        List<BlockPos> imaginedBuildPath = shouldBuild(mob, mind, target)
                ? buildImaginaryBuildPath(mob, target, defaultBuildState(mob))
                : List.of();
        BlockPos immediateBuild = imaginedBuildPath.isEmpty() ? findBuildPlacement(mob, target) : imaginedBuildPath.get(0);
        double breakCost = estimateBreakCost(mob, immediateBreak);
        double buildCost = estimateBuildCost(mob, target, immediateBuild, imaginedBuildPath);
        return new TerrainPlan(immediateBreak, immediateBuild, imaginedBuildPath, breakCost, buildCost);
    }

    private boolean hasNearbyFistMineableDropBlock(ChangedEntity mob, LivingEntity target) {
        if (!mob.hasLineOfSight(target)) {
            return false;
        }

        BlockPos targetPos = target.blockPosition();
        BlockPos sourcePos = mob.blockPosition();
        if (targetPos.getY() >= sourcePos.getY()) {
            return false;
        }

        Vec3 start = mob.getEyePosition();
        Vec3 end = target.getEyePosition();
        Vec3 step = end.subtract(start).normalize().scale(0.5D);
        Vec3 cursor = start;
        int maxSteps = Math.min(8, (int)Math.ceil(mob.distanceTo(target) / 0.5D));

        for (int i = 0; i < maxSteps; i++) {
            cursor = cursor.add(step);
            BlockPos pos = BlockPos.containing(cursor);
            BlockState state = mob.level().getBlockState(pos);
            if (state.isAir()) continue;

            float hardness = state.getDestroySpeed(mob.level(), pos);
            if (hardness >= 0.0F && hardness <= 0.6F && !state.requiresCorrectToolForDrops()) {
                return true;
            }
        }

        return false;
    }

    private void pruneImaginedBuildPath(ChangedEntity mob, LatexMind mind) {
        if (mind.imaginedBuildPath.isEmpty()) return;

        BlockPos head = mind.imaginedBuildPath.get(0);
        BlockState state = mob.level().getBlockState(head);
        if (!state.isAir() && !state.canBeReplaced()) {
            mind.imaginedBuildPath.remove(0);
        }
    }

    private List<BlockPos> buildImaginaryBuildPath(ChangedEntity mob, LivingEntity target, @Nullable BlockState defaultState) {
        if (defaultState == null) return List.of();

        List<BlockPos> path = new ArrayList<>();
        BlockPos current = mob.blockPosition();
        BlockPos destination = target.blockPosition();
        Set<BlockPos> visited = new HashSet<>();

        for (int step = 0; step < IMAGINARY_PATH_SCAN_BLOCKS && path.size() < IMAGINARY_PATH_MAX_BLOCKS; step++) {
            if (current.closerThan(destination, 1.8D)) {
                break;
            }

            BlockPos next = chooseNextImaginedBridgePos(mob, current, destination, visited);
            if (next == null) {
                break;
            }

            visited.add(next);
            BlockPos placePos = next.below();
            BlockState belowState = mob.level().getBlockState(placePos);
            if (belowState.isAir() || belowState.canBeReplaced()) {
                path.add(placePos);
            }
            current = next;
        }

        return path;
    }

    @Nullable
    private BlockPos chooseNextImaginedBridgePos(ChangedEntity mob, BlockPos current, BlockPos destination, Set<BlockPos> visited) {
        Vec3 desired = Vec3.atCenterOf(destination).subtract(Vec3.atCenterOf(current)).multiply(1.0D, 0.0D, 1.0D);
        if (desired.lengthSqr() < 1.0E-6D) {
            return null;
        }

        Direction primary = Direction.getNearest(desired.x, 0.0D, desired.z);
        BlockPos candidate = current.relative(primary);
        if (!visited.contains(candidate) && canStepOntoImagined(mob, candidate)) {
            return candidate;
        }

        for (Direction alternate : Direction.Plane.HORIZONTAL) {
            if (alternate == primary) continue;
            BlockPos altPos = current.relative(alternate);
            if (!visited.contains(altPos) && canStepOntoImagined(mob, altPos)) {
                return altPos;
            }
        }

        return null;
    }

    private boolean canStepOntoImagined(ChangedEntity mob, BlockPos pos) {
        BlockState feet = mob.level().getBlockState(pos);
        BlockState head = mob.level().getBlockState(pos.above());
        return (feet.isAir() || feet.canBeReplaced()) && (head.isAir() || head.canBeReplaced());
    }

    @Nullable
    private BlockPos findPlannedBreakTarget(ChangedEntity mob, LivingEntity target) {
        BlockPos rayBreak = findBreakObstacleRay(mob, target);
        if (rayBreak != null) return rayBreak;
        return findBreakTarget(mob, target);
    }

    @Nullable
    private BlockPos findBreakObstacleRay(ChangedEntity mob, LivingEntity target) {
        Vec3 start = mob.getEyePosition();
        Vec3 end = target.getEyePosition();
        Vec3 line = end.subtract(start);
        double distance = line.length();
        if (distance < 0.001D) return null;

        Vec3 step = line.normalize().scale(BREAK_PATH_STEP);
        Vec3 cursor = start;
        int maxSteps = Math.min(BREAK_PATH_SCAN_BLOCKS, (int)Math.ceil(distance / BREAK_PATH_STEP));

        for (int i = 0; i < maxSteps; i++) {
            cursor = cursor.add(step);
            BlockPos pos = BlockPos.containing(cursor);
            BlockState state = mob.level().getBlockState(pos);
            if (!state.isAir() && canBreakBlock(mob, pos, state)) {
                return pos.immutable();
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findBreakTarget(ChangedEntity mob, LivingEntity target) {
        Vec3 eyePos = mob.getEyePosition();
        Vec3 targetEyePos = target.getEyePosition();

        BlockHitResult hit = mob.level().clip(new ClipContext(
                eyePos,
                targetEyePos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mob
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mob.level().getBlockState(pos);
            if (canBreakBlock(mob, pos, state)) {
                return pos;
            }
        }

        Direction dir = mob.getDirection();
        BlockPos inFrontFeet = mob.blockPosition().relative(dir);
        BlockPos inFrontHead = inFrontFeet.above();

        BlockState feetState = mob.level().getBlockState(inFrontFeet);
        if (!feetState.isAir() && canBreakBlock(mob, inFrontFeet, feetState)) {
            return inFrontFeet;
        }

        BlockState headState = mob.level().getBlockState(inFrontHead);
        if (!headState.isAir() && canBreakBlock(mob, inFrontHead, headState)) {
            return inFrontHead;
        }

        return null;
    }

    @Nullable
    private BlockPos findBuildPlacement(ChangedEntity mob, LivingEntity target) {
        if (shouldTowerUp(mob, target)) {
            return mob.blockPosition();
        }

        Direction facing = mob.getDirection();
        BlockPos inFront = mob.blockPosition().relative(facing);

        if (target.getY() > mob.getY() + 1.2D) {
            BlockPos stepUp = inFront;
            if (mob.level().getBlockState(stepUp).isAir()) {
                return stepUp;
            }
            BlockPos aboveInFront = inFront.above();
            if (mob.level().getBlockState(aboveInFront).isAir()) {
                return aboveInFront;
            }
        }

        BlockPos belowInFront = inFront.below();
        if (mob.level().getBlockState(belowInFront).isAir()) {
            return belowInFront;
        }

        return null;
    }

    private boolean canPlaceBlockAt(ChangedEntity mob, BlockPos pos, BlockState placeState) {
        if (!mob.level().getBlockState(pos).canBeReplaced()) return false;
        if (!placeState.canSurvive(mob.level(), pos)) return false;

        AABB mobBox = mob.getBoundingBox();
        AABB blockBox = new AABB(pos);
        if (mobBox.intersects(blockBox)) return false;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = mob.level().getBlockState(neighbor);
            if (!neighborState.isAir() && !neighborState.canBeReplaced()) {
                return true;
            }
        }

        return false;
    }

    private boolean canPlaceTowerBlockAt(ChangedEntity mob, BlockPos pos, BlockState placeState) {
        if (!mob.level().getBlockState(pos).canBeReplaced()) return false;
        if (!placeState.canSurvive(mob.level(), pos)) return false;

        BlockPos below = pos.below();
        BlockState belowState = mob.level().getBlockState(below);
        return !belowState.isAir() && !belowState.canBeReplaced();
    }

    private boolean isCautiousPlacement(ChangedEntity mob, BlockPos placePos) {
        return placePos.getY() < mob.blockPosition().getY();
    }

    private boolean moveNearPlacement(ChangedEntity mob, BlockPos placePos) {
        Vec3 target = Vec3.atCenterOf(placePos);
        return mob.position().distanceToSqr(target) <= 9.0D;
    }

    @Nullable
    private BlockPos findWaterClutchPos(ChangedEntity mob) {
        int maxDepth = Math.min(12, Math.max(2, (int)Math.ceil(mob.fallDistance)));
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
        ItemStack best = offScore > mainScore ? off : main;
        float bestScore = Math.max(mainScore, offScore);

        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (!slotStack.isEmpty()) {
                    float score = miningScore(slotStack, state);
                    if (score > bestScore && score > 1.0F) {
                        bestScore = score;
                        inv.setStackInSlot(i, main.copy());
                        mob.setItemInHand(InteractionHand.MAIN_HAND, slotStack.copy());
                        best = slotStack;
                    }
                }
            }
        }

        if (bestScore <= 1.0F) {
            return ItemStack.EMPTY;
        }
        return best;
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
        ItemStack best = combatScore(off) > combatScore(main) ? off : main;
        double bestScore = combatScore(best);

        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (!slotStack.isEmpty()) {
                    double score = combatScore(slotStack);
                    if (score > bestScore) {
                        bestScore = score;
                        inv.setStackInSlot(i, main.copy());
                        mob.setItemInHand(InteractionHand.MAIN_HAND, slotStack.copy());
                        best = slotStack;
                    }
                }
            }
        }
        return best;
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
        ItemStack best = offScore > mainScore ? off : main;
        double bestScore = Math.max(mainScore, offScore);

        if (bestScore <= 0.0D) {
            var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
            if (optInv.isPresent()) {
                LatexInventory inv = optInv.get();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack slotStack = inv.getStackInSlot(i);
                    if (!slotStack.isEmpty()) {
                        double score = buildScore(mob, slotStack);
                        if (score > 0.0D) {
                            inv.setStackInSlot(i, main.copy());
                            mob.setItemInHand(InteractionHand.MAIN_HAND, slotStack.copy());
                            return slotStack;
                        }
                    }
                }
            }
            return ItemStack.EMPTY;
        }
        return best;
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
        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (buildScore(mob, slotStack) > 0.0D) {
                    count += slotStack.getCount();
                }
            }
        }
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

    private double estimateBuildCost(ChangedEntity mob, LivingEntity target, @Nullable BlockPos buildPos, List<BlockPos> imaginedBuildPath) {
        if (buildPos == null) return Double.POSITIVE_INFINITY;
        int blocksAvailable = countUsableBuildingBlocks(mob);
        int blocksNeeded = imaginedBuildPath.isEmpty() ? estimateBuildBlocksNeeded(mob, target, buildPos) : imaginedBuildPath.size();
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

        mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).ifPresent(inv -> {
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (ItemStack.isSameItemSameTags(slotStack, targetStack)) {
                    slotStack.shrink(1);
                    if (slotStack.isEmpty()) {
                        inv.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    return;
                }
            }
        });
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

    private void stopShieldingIfIdle(ChangedEntity mob) {
        if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof ShieldItem) {
            mob.stopUsingItem();
        }
    }

    private void equipBestArmor(ChangedEntity mob, LatexMind mind) {
        if (mind.equipmentScanCooldown > 0) return;
        mind.equipmentScanCooldown = 20;

        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack equipped = mob.getItemBySlot(slot);
            ItemStack best = findBetterArmor(mob, slot, equipped);
            if (!best.isEmpty()) {
                mob.setItemSlot(slot, best.copyWithCount(1));
                consumeOneMatchingItem(mob, best);
            }
        }
    }

    private ItemStack findBetterArmor(ChangedEntity mob, EquipmentSlot slot, ItemStack current) {
        ItemStack best = ItemStack.EMPTY;
        int currentArmor = armorValue(current);

        for (ItemStack candidate : List.of(mob.getMainHandItem(), mob.getOffhandItem())) {
            if (candidate.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot) {
                int value = armorValue(candidate);
                if (value > currentArmor) {
                    currentArmor = value;
                    best = candidate;
                }
            }
        }

        var optInv = mob.getCapability(LatexInventoryProvider.LATEX_INVENTORY).resolve();
        if (optInv.isPresent()) {
            LatexInventory inv = optInv.get();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot) {
                    int value = armorValue(slotStack);
                    if (value > currentArmor) {
                        currentArmor = value;
                        best = slotStack;
                    }
                }
            }
        }

        return best;
    }

    private int armorValue(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getDefense();
        }
        return 0;
    }

    private boolean hasInterestingLootNearby(ChangedEntity mob) {
        return findBestItemEntity(mob) != null;
    }

    @Nullable
    private ItemEntity findBestItemEntity(ChangedEntity mob) {
        ItemEntity best = null;
        double bestScore = 0.0D;

        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(LOOT_RANGE))) {
            ItemStack stack = item.getItem();
            double utility = itemUtilityScore(stack);
            if (utility <= 0.0D) continue;

            double score = utility - mob.distanceToSqr(item) * 0.08D;
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }

        return best;
    }

    @Nullable
    private LivingEntity findVisibleTarget(ChangedEntity mob, LatexMind mind) {
        LivingEntity bestTarget = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double range = Math.min(Math.max(12.0D, followRange), MAX_VISIBLE_TARGET_RANGE);

        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range))) {
            if (!isValidVisibleTarget(mob, candidate, mind) || !mob.hasLineOfSight(candidate)) {
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

        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double range = Math.min(Math.max(16.0D, followRange + 6.0D), MAX_REMEMBERED_TARGET_RANGE);
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range))) {
            if (mind.targetId.equals(candidate.getUUID()) && isValidAggroTarget(mob, candidate, mind)) {
                return candidate;
            }
        }

        if (mob.level() instanceof ServerLevel server) {
            Player player = server.getPlayerByUUID(mind.targetId);
            if (player != null && isValidAggroTarget(mob, player, mind)) {
                return player;
            }
        }

        return null;
    }

    private boolean isValidAggroTarget(ChangedEntity mob, LivingEntity target, LatexMind mind) {
        if (target == mob || !target.isAlive()) {
            return false;
        }
        if (mob.distanceToSqr(target) > HARD_TARGET_DROP_RANGE * HARD_TARGET_DROP_RANGE) {
            return false;
        }

        if (mind.isGrudgeKiller(target.getUUID(), mob.tickCount)) {
            return true;
        }

        if (target instanceof ChangedEntity otherLatex) {
            if (LatexAiUtil.isSameLatexType(mob, otherLatex)) {
                return false;
            }
            if (LatexAiUtil.areHostileLatexFactions(mob, otherLatex)) {
                return true;
            }
            return mind.isRetaliationTarget(mob, otherLatex);
        }

        if (target instanceof Player player) {
            if (LatexCuddleHelper.isTamingOwner(mob, player)) {
                return false;
            }
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            if (!LatexAiUtil.isPlayerTransfurred(player)) {
                return true;
            }
            if (LatexAiUtil.isSameLatexType(mob, player)) {
                return false;
            }
            return LatexAiUtil.areHostileLatexFactions(mob, player) || mind.isRetaliationTarget(mob, player);
        }

        if (target instanceof Villager) {
            return true;
        }

        return isHumanoidTransfurTarget(target);
    }

    private boolean isValidVisibleTarget(ChangedEntity mob, LivingEntity target, LatexMind mind) {
        return isValidAggroTarget(mob, target, mind);
    }

    private boolean isHumanoidTransfurTarget(LivingEntity target) {
        if (target.getType().is(CHANGED_LATEXES)) {
            return false;
        }

        return target.getType().is(CHANGED_HUMANOIDS)
                || target instanceof Zombie
                || target instanceof AbstractSkeleton
                || target instanceof Villager
                || target instanceof Pillager
                || target instanceof Evoker;
    }

    private boolean shouldDropTarget(ChangedEntity mob, LatexMind mind) {
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null) {
            double distanceSqr = mob.distanceToSqr(currentTarget);
            if (distanceSqr > HARD_TARGET_DROP_RANGE * HARD_TARGET_DROP_RANGE) {
                return true;
            }
            if (!mob.hasLineOfSight(currentTarget) && distanceSqr > MAX_REMEMBERED_TARGET_RANGE * MAX_REMEMBERED_TARGET_RANGE) {
                return true;
            }
        }
        if (currentTarget instanceof Player playerTarget && (!playerTarget.isAlive() || playerTarget.isCreative() || playerTarget.isSpectator())) {
            return true;
        }

        if (mind.targetId != null && mob.level() instanceof ServerLevel server) {
            Player rememberedPlayer = server.getPlayerByUUID(mind.targetId);
            return rememberedPlayer != null && (!rememberedPlayer.isAlive() || rememberedPlayer.isCreative() || rememberedPlayer.isSpectator());
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