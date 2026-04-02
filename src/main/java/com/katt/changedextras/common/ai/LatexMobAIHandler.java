package com.katt.changedextras.common.ai;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.Config;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Collections;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexMobAIHandler {
    private static final Set<ChangedEntity> INSTALLED_MOBS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private LatexMobAIHandler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ChangedEntity mob)) return;
        ensureSmartAiInstalled(mob);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)) return;
        if (mob.level().isClientSide()) return;
        if (!Config.smartLatexAiEnabled) return;

        ensureSmartAiInstalled(mob);
        LatexMind mind = LatexMindStore.get(mob);
        mind.tick(mob);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (!(event.getObject() instanceof ChangedEntity)) return;

        event.addCapability(
                ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "latex_inventory"),
                new com.katt.changedextras.common.inventory.LatexInventoryProvider()
        );
    }

    private static void ensureSmartAiInstalled(ChangedEntity mob) {
        if (!Config.smartLatexAiEnabled || INSTALLED_MOBS.contains(mob)) {
            return;
        }

        INSTALLED_MOBS.add(mob);
        removeConflictingLookGoals(mob);
        mob.setCanPickUpLoot(true);

        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() < 40.0D) {
            followRange.setBaseValue(40.0D);
        }

        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.24D);
        }
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
}
