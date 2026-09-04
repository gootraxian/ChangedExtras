package com.katt.changedextras.common.ai;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.common.ChangedExtrasGameRules;
import com.katt.changedextras.entity.beasts.ArtistEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexMobAIHandler {
    // Standard player-calibrated base movement speed, nudged up (was 0.23D, then 0.28D, then 0.35D)
    private static final double LATEX_PLAYER_BASE_SPEED = 0.38D;

    private static final Set<ChangedEntity> INSTALLED_MOBS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private LatexMobAIHandler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ChangedEntity mob)) return;
        if (mob instanceof ArtistEntity artist) {
            if (artist.isNoAi()) {
                artist.setNoAi(false);
            }
            return;
        }
        if (ChangedExtrasGameRules.isSmartLatexAiEnabled(event.getLevel().getGameRules())) {
            ensureSmartAiInstalled(mob);
        } else {
            installDefaultGoals(mob);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ChangedEntity mob)) return;
        if (mob instanceof ArtistEntity) return;

        boolean smartEnabled = ChangedExtrasGameRules.isSmartLatexAiEnabled(mob.level().getGameRules());
        if (!smartEnabled) {
            if (INSTALLED_MOBS.contains(mob)) {
                INSTALLED_MOBS.remove(mob);
                installDefaultGoals(mob);
            }
            return;
        }

        if (!INSTALLED_MOBS.contains(mob)) {
            ensureSmartAiInstalled(mob);
        }

        LatexMind mind = LatexMindStore.get(mob);
        mind.tick(mob);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob) {
            INSTALLED_MOBS.remove(mob);
            LatexMindStore.forget(mob);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity victim) {
            INSTALLED_MOBS.remove(victim);
            LatexMindStore.forget(victim);

            if (event.getSource().getEntity() instanceof LivingEntity killer) {
                alertNearbyAlliesOfMurder(victim, killer);
            }
        }
    }

    private static void alertNearbyAlliesOfMurder(ChangedEntity victim, LivingEntity killer) {
        double alertRadius = 18.0D;
        for (ChangedEntity ally : victim.level().getEntitiesOfClass(ChangedEntity.class, victim.getBoundingBox().inflate(alertRadius))) {
            if (ally != victim && LatexAiUtil.isSameLatexType(victim, ally)) {
                LatexMind allyMind = LatexMindStore.get(ally);
                allyMind.addGrudgeKiller(killer.getUUID(), ally.tickCount + 1200); // 60-second grudge
                allyMind.triggerEnrage(240);
                ally.setTarget(killer);
            }
        }
    }

    private static void ensureSmartAiInstalled(ChangedEntity mob) {
        if (INSTALLED_MOBS.contains(mob)) return;

        removeConflictingLookGoals(mob);
        removeConflictingCombatGoals(mob);
        installTargetShareGoal(mob);
        mob.setCanPickUpLoot(true);

        // Let the navigator route straight through water instead of detouring around it or refusing
        // to path once a target wades/swims in - latex creatures should be able to pursue into water.
        mob.getNavigation().setCanFloat(true);
        mob.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        mob.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);

        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() < 40.0D) {
            followRange.setBaseValue(40.0D);
        }

        AttributeInstance movementSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(LATEX_PLAYER_BASE_SPEED);
        }

        INSTALLED_MOBS.add(mob);
    }

    private static void removeConflictingLookGoals(ChangedEntity mob) {
        GoalSelector goals = getSelector(mob, "goalSelector");
        if (goals != null) {
            goals.removeAllGoals(goal ->
                    goal instanceof RandomLookAroundGoal
                            || goal instanceof LookAtPlayerGoal
                            || goal instanceof WaterAvoidingRandomStrollGoal
            );
        }
    }

    private static void removeConflictingCombatGoals(ChangedEntity mob) {
        GoalSelector goals = getSelector(mob, "goalSelector");
        if (goals != null) {
            goals.removeAllGoals(goal -> goal instanceof MeleeAttackGoal);
        }

        GoalSelector targetSelector = getSelector(mob, "targetSelector");
        if (targetSelector != null) {
            targetSelector.removeAllGoals(goal ->
                    goal instanceof HurtByTargetGoal
                            || goal instanceof NearestAttackableTargetGoal<?>);
        }
    }

    private static void installTargetShareGoal(ChangedEntity mob) {
        GoalSelector targetSelector = getSelector(mob, "targetSelector");
        if (targetSelector != null) {
            targetSelector.addGoal(2, new ShareTargetGoal(mob, 12.0D, 10));
        }
    }

    @Nullable
    private static GoalSelector getSelector(ChangedEntity mob, String fieldName) {
        try {
            Field field = net.minecraft.world.entity.Mob.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (GoalSelector) field.get(mob);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void installDefaultGoals(ChangedEntity mob) {
        GoalSelector goals = getSelector(mob, "goalSelector");
        if (goals != null) {
            goals.addGoal(5, new WaterAvoidingRandomStrollGoal(mob, 1.0D));
            goals.addGoal(6, new RandomLookAroundGoal(mob));
            goals.addGoal(6, new LookAtPlayerGoal(mob, Player.class, 8.0F));
            goals.addGoal(4, new MeleeAttackGoal(mob, 1.0D, false));
        }

        GoalSelector targetSelector = getSelector(mob, "targetSelector");
        if (targetSelector != null) {
            targetSelector.addGoal(1, new HurtByTargetGoal(mob));
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
        }

        AttributeInstance movementSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(LATEX_PLAYER_BASE_SPEED);
        }
    }
}