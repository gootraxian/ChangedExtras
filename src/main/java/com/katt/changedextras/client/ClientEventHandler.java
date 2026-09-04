package com.katt.changedextras.client;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.init.ChangedExtrasParticles;
import com.katt.changedextras.network.JackpotClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEventHandler {

    @Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientParticleHandler {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused()) return;

            ArtistBossMusicManager.tick(mc);

            for (Player player : mc.level.players()) {
                if (JackpotSoundManager.isActive(player.getUUID())) {
                    spawnJackpotParticles(player);
                }
            }
        }

        @SubscribeEvent
        public static void onClientDisconnect(net.minecraftforge.event.level.LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                JackpotSoundManager.clear(Minecraft.getInstance());
                JackpotClientHandler.stopAllEffects();
                ArtistBossMusicManager.clear(Minecraft.getInstance());
                ArtistTintManager.clearAll();
                LatexDebugOverlay.clear();
            }
        }

        private static void spawnJackpotParticles(Player player) {
            Level level = player.level();
            RandomSource random = player.getRandom();

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            for (int i = 0; i < 5; i++) {
                double ox = (random.nextDouble() - 0.5) * 1.2;
                double oy = 0.5 + random.nextDouble() * 1.5;
                double oz = (random.nextDouble() - 0.5) * 1.2;
                
                level.addParticle(
                        ChangedExtrasParticles.JACKPOT_AURA.get(),
                        px + ox,
                        py + oy,
                        pz + oz,
                        (random.nextDouble() - 0.5) * 0.02,
                        0.05 + random.nextDouble() * 0.02,
                        (random.nextDouble() - 0.5) * 0.02
                );
            }
        }
    }

    @Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientDebugHandler {
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            LatexDebugOverlay.render(event);
        }

        @SubscribeEvent
        public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
            if (!LatexDebugOverlay.isEnabled()) return;
            if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            String text = "§b[ChangedExtras Debug] §aACTIVE §7| Tracked AI: §f" + LatexDebugOverlay.getSnapshotCount();
            event.getGuiGraphics().drawString(mc.font, text, 8, 8, 0xFFFFFFFF, true);
        }
    }
}
