package com.katt.changedextras.events;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.common.ArtistBrushTargetValidator;
import com.katt.changedextras.item.ArtistBrushItem;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArtistBrushSelectionHandler {
    private ArtistBrushSelectionHandler() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || player.level().isClientSide()) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ArtistBrushItem)) {
            return;
        }

        Entity target = event.getTarget();
        if (!(target instanceof Player) && !(target instanceof ChangedEntity)) {
            return;
        }
        if (!ArtistBrushTargetValidator.isAllowedTarget(target)) {
            player.displayClientMessage(Component.translatable("message.changedextras.artist_brush.custom_latex_only"), true);
            event.setCanceled(true);
            return;
        }

        ArtistBrushItem.setSelectedTarget(
                stack,
                target.getName().getString(),
                target.getStringUUID(),
                target instanceof Player ? "player" : "changed_entity"
        );
        player.displayClientMessage(Component.translatable("message.changedextras.artist_brush.selected", target.getName()), true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || player.level().isClientSide()) {
            return;
        }

        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof ArtistBrushItem)) {
            return;
        }
        if (!ArtistBrushTargetValidator.isAllowedTarget(player)) {
            player.displayClientMessage(Component.translatable("message.changedextras.artist_brush.custom_latex_only"), true);
            event.setCanceled(true);
            return;
        }

        ArtistBrushItem.setSelectedTarget(
                stack,
                player.getName().getString(),
                player.getStringUUID(),
                "self"
        );
        player.displayClientMessage(Component.translatable("message.changedextras.artist_brush.selected_self"), true);
        event.setCanceled(true);
    }
}
