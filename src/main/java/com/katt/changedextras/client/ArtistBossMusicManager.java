package com.katt.changedextras.client;

import com.katt.changedextras.entity.beasts.ArtistEntity;
import com.katt.changedextras.init.ChangedExtrasSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ArtistBossMusicManager {
    private static final double MAX_THEME_DISTANCE_SQR = 48.0D * 48.0D;

    private static Integer activeArtistId = null;
    private static SoundInstance loopingSound = null;

    private ArtistBossMusicManager() {
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            stop(mc);
            return;
        }

        ArtistEntity artist = findActiveArtist(mc);
        if (artist == null) {
            stop(mc);
            return;
        }

        if (loopingSound == null || activeArtistId == null || activeArtistId != artist.getId()) {
            start(mc, artist);
        }
    }

    public static void clear(Minecraft mc) {
        stop(mc);
        activeArtistId = null;
    }

    private static ArtistEntity findActiveArtist(Minecraft mc) {
        ArtistEntity bestArtist = null;
        double bestDistance = Double.MAX_VALUE;

        for (ArtistEntity artist : mc.level.getEntitiesOfClass(ArtistEntity.class, mc.player.getBoundingBox().inflate(48.0D))) {
            if (!artist.isAlive()) {
                continue;
            }

            double distance = artist.distanceToSqr(mc.player);
            if (distance > MAX_THEME_DISTANCE_SQR) {
                continue;
            }

            boolean activeFight = artist.getHealth() < artist.getMaxHealth()
                    || mc.player.getLastHurtByMob() == artist
                    || mc.player.getLastHurtMob() == artist;
            if (!activeFight) {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestArtist = artist;
            }
        }

        return bestArtist;
    }

    private static void start(Minecraft mc, ArtistEntity artist) {
        stop(mc);
        activeArtistId = artist.getId();
        loopingSound = new SimpleSoundInstance(
                ChangedExtrasSounds.ARTIST_THEME.get().getLocation(),
                SoundSource.RECORDS,
                1.0f, 1.0f,
                RandomSource.create(),
                true,
                0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0,
                true
        );
        mc.getSoundManager().play(loopingSound);
    }

    private static void stop(Minecraft mc) {
        if (loopingSound != null) {
            mc.getSoundManager().stop(loopingSound);
            loopingSound = null;
        }
        activeArtistId = null;
    }
}
