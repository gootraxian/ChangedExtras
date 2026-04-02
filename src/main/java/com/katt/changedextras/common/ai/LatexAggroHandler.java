package com.katt.changedextras.common.ai;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.Config;
import com.katt.changedextras.common.LatexCuddleHelper;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexAggroHandler {
    private LatexAggroHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob) || mob.level().isClientSide()) {
            return;
        }

        if (!Config.smartLatexAiEnabled) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        if (attacker instanceof Player player && LatexCuddleHelper.isTamingOwner(mob, player)) {
            mob.setTarget(null);
            LatexMindStore.get(mob).clearTarget();
            return;
        }

        if (!isValidRetaliationTarget(attacker)) {
            return;
        }

        mob.setTarget(attacker);
        LatexMind mind = LatexMindStore.get(mob);
        mind.remember(attacker, mob.tickCount, mob.hasLineOfSight(attacker));
    }

    private static boolean isValidRetaliationTarget(LivingEntity attacker) {
        if (!attacker.isAlive() || attacker instanceof ChangedEntity) {
            return false;
        }

        if (attacker instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }

        return ProcessTransfur.getEntityTransfurTolerance(attacker) > 0.0D;
    }
}
