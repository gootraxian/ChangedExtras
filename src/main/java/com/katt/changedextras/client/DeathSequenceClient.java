package com.katt.changedextras.client;

import com.katt.changedextras.ChangedExtras;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.katt.changedextras.Config;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, value = Dist.CLIENT)
public final class DeathSequenceClient {
    private static final int HUMAN_DEATH_MESSAGE_COUNT = 7;
    private static final int INFECTED_DEATH_MESSAGE_COUNT = 7;
    private static boolean allowNextVanillaDeathScreen;

    private DeathSequenceClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isDeadOrDying()) {
            return;
        }

        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @SubscribeEvent
    public static void onOpenScreen(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen) || allowNextVanillaDeathScreen) {
            allowNextVanillaDeathScreen = false;
            return;
        }

        if (!Config.useCustomDeathScreen) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Component causeOfDeath = minecraft.player.getCombatTracker().getDeathMessage();
        boolean hardcore = minecraft.level.getLevelData().isHardcore();
        event.setNewScreen(new DelayedDeathScreen(causeOfDeath, getRandomDeathMessage(), hardcore));
    }

    public static void openVanillaDeathScreen(Component causeOfDeath, Component overlayMessage, boolean hardcore) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DelayedDeathScreen)) {
            return;
        }

        allowNextVanillaDeathScreen = true;
        minecraft.setScreen(new StyledDeathScreen(causeOfDeath, overlayMessage, hardcore));
    }

    private static Component getRandomDeathMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return Component.empty();
        }

        boolean infected = ProcessTransfur.isPlayerTransfurred(minecraft.player);
        String keyPrefix = infected ? "message.changedextras.death_overlay.infected." : "message.changedextras.death_overlay.human.";
        int salt = infected ? 0x1FEC7ED : 0x0A11CE;
        int count = infected ? INFECTED_DEATH_MESSAGE_COUNT : HUMAN_DEATH_MESSAGE_COUNT;

        long seed = minecraft.level.getGameTime();
        seed ^= minecraft.player.getUUID().getMostSignificantBits();
        seed ^= minecraft.player.getUUID().getLeastSignificantBits();
        seed ^= salt;

        return Component.translatable(keyPrefix + (Math.floorMod(seed, count) + 1));
    }
}
