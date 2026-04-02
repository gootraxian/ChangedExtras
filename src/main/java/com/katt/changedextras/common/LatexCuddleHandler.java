package com.katt.changedextras.common;

import com.katt.changedextras.ChangedExtras;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexCuddleHandler {
    private static final String CUDDLE_SESSION_TAG = "changedextras.cuddle_session_checked";
    private static final String CUDDLE_NOTICE_TAG = "changedextras.cuddle_transfur_notice";

    private LatexCuddleHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (!player.isSleeping()) {
            if (player.getPersistentData().getBoolean(CUDDLE_NOTICE_TAG)) {
                player.displayClientMessage(Component.literal(
                        "You wake up, \"This isnt my body..?!\" you say, as you hear your fluffy friend speak: \"Huh? wait... IM SO SORRY!\""
                ), false);
                player.getPersistentData().remove(CUDDLE_NOTICE_TAG);
            }
            player.getPersistentData().remove(CUDDLE_SESSION_TAG);
            return;
        }

        ChangedEntity cuddler = findClosestCuddler(player);
        if (cuddler == null) {
            return;
        }

        Vec3 cuddlePos = LatexCuddleHelper.getCuddlePosition(player, cuddler);
        PathNavigation navigation = cuddler.getNavigation();
        navigation.stop();
        cuddler.setTarget(null);
        cuddler.setDeltaMovement(Vec3.ZERO);
        cuddler.moveTo(cuddlePos.x, cuddlePos.y, cuddlePos.z, LatexCuddleHelper.getCuddleYaw(player), 0.0F);
        cuddler.setYBodyRot(LatexCuddleHelper.getCuddleYaw(player));
        cuddler.setYHeadRot(LatexCuddleHelper.getCuddleYaw(player));

        if ((player.tickCount & 15) == 0 && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART,
                    cuddlePos.x,
                    cuddlePos.y + 0.55D,
                    cuddlePos.z,
                    2,
                    0.18D,
                    0.12D,
                    0.18D,
                    0.0D);
        }

        if (!player.getPersistentData().getBoolean(CUDDLE_SESSION_TAG)) {
            player.getPersistentData().putBoolean(CUDDLE_SESSION_TAG, true);
            tryCuddleTransfur(player, cuddler);
        }
    }

    private static ChangedEntity findClosestCuddler(ServerPlayer player) {
        ChangedEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (ChangedEntity entity : player.level().getEntitiesOfClass(ChangedEntity.class, player.getBoundingBox().inflate(12.0D))) {
            if (!(entity instanceof TamableLatexEntity)) {
                continue;
            }

            Player owner = LatexCuddleHelper.getTamingOwner(entity);
            if (owner != player) {
                continue;
            }

            double distance = entity.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    private static void tryCuddleTransfur(ServerPlayer player, ChangedEntity cuddler) {
        if (ProcessTransfur.isPlayerTransfurred(player) || player.getRandom().nextFloat() >= 0.05F) {
            return;
        }

        TransfurVariant<?> variant = cuddler.getSelfVariant();
        if (variant == null) {
            variant = ProcessTransfur.getEntityVariant(cuddler).orElse(null);
        }

        if (variant == null) {
            return;
        }

        ProcessTransfur.setPlayerTransfurVariant(player, variant);
        player.getPersistentData().putBoolean(CUDDLE_NOTICE_TAG, true);
    }
}
