package com.katt.changedextras.common;

import com.katt.changedextras.item.ArtistBrushItem;
import com.katt.changedextras.network.SyncArtistColorPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

public final class ArtistAppearanceStorage {
    private static final String APPEARANCE_TAG = "changedextras_artist_appearance";
    private static final String ENABLED_TAG = "enabled";
    private static final String COLOR_TAG = "color";
    private static final String TEXTURE_PATH_TAG = "texturePath";
    private static final String UV_X_TAG = "uvX";
    private static final String UV_Y_TAG = "uvY";
    private static final String CUSTOM_UV_ENABLED_TAG = "customUvEnabled";

    private ArtistAppearanceStorage() {
    }

    public static void saveAppearance(Entity entity, int color, String texturePath, int uvX, int uvY, boolean customUvEnabled) {
        CompoundTag tag = entity.getPersistentData().getCompound(APPEARANCE_TAG);
        tag.putBoolean(ENABLED_TAG, true);
        tag.putInt(COLOR_TAG, color);
        tag.putString(TEXTURE_PATH_TAG, texturePath == null ? "" : texturePath);
        tag.putInt(UV_X_TAG, uvX);
        tag.putInt(UV_Y_TAG, uvY);
        tag.putBoolean(CUSTOM_UV_ENABLED_TAG, customUvEnabled);
        entity.getPersistentData().put(APPEARANCE_TAG, tag);
    }

    public static void syncAppearance(Entity entity) {
        AppearanceData appearance = getAppearance(entity);
        if (appearance == null) {
            return;
        }

        com.katt.changedextras.network.ChangedExtrasNetwork.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                appearance.toPacket(entity.getId())
        );
    }

    public static void syncAppearanceToPlayer(Entity entity, ServerPlayer player) {
        AppearanceData appearance = getAppearance(entity);
        if (appearance == null) {
            return;
        }

        com.katt.changedextras.network.ChangedExtrasNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                appearance.toPacket(entity.getId())
        );
    }

    public static AppearanceData getAppearance(Entity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(APPEARANCE_TAG);
        if (!tag.getBoolean(ENABLED_TAG)) {
            return null;
        }

        return new AppearanceData(
                tag.getInt(COLOR_TAG),
                tag.getString(TEXTURE_PATH_TAG),
                tag.getInt(UV_X_TAG),
                tag.getInt(UV_Y_TAG),
                tag.getBoolean(CUSTOM_UV_ENABLED_TAG)
        );
    }

    public record AppearanceData(int color, String texturePath, int uvX, int uvY, boolean customUvEnabled) {
        public SyncArtistColorPacket toPacket(int entityId) {
            return new SyncArtistColorPacket(entityId, color, texturePath, true, uvX, uvY, customUvEnabled);
        }
    }
}
