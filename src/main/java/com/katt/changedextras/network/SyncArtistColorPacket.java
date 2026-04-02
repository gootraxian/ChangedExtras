package com.katt.changedextras.network;

import com.katt.changedextras.client.ArtistTintManager;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncArtistColorPacket {
    private final int entityId;
    private final int color;
    private final String texturePath;
    private final boolean enabled;
    private final int uvX;
    private final int uvY;
    private final boolean customUvEnabled;

    public SyncArtistColorPacket(int entityId, int color, String texturePath, boolean enabled, int uvX, int uvY, boolean customUvEnabled) {
        this.entityId = entityId;
        this.color = color;
        this.texturePath = texturePath;
        this.enabled = enabled;
        this.uvX = uvX;
        this.uvY = uvY;
        this.customUvEnabled = customUvEnabled;
    }

    public static void encode(SyncArtistColorPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.color);
        buf.writeUtf(msg.texturePath, 1024);
        buf.writeBoolean(msg.enabled);
        buf.writeInt(msg.uvX);
        buf.writeInt(msg.uvY);
        buf.writeBoolean(msg.customUvEnabled);
    }

    public static SyncArtistColorPacket decode(FriendlyByteBuf buf) {
        return new SyncArtistColorPacket(buf.readInt(), buf.readInt(), buf.readUtf(1024), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(SyncArtistColorPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.enabled) {
                ArtistTintManager.setAppearance(msg.entityId, msg.color, msg.texturePath, msg.uvX, msg.uvY, msg.customUvEnabled);
                mirrorToPlayerVariant(msg);
            } else {
                ArtistTintManager.clearTint(msg.entityId);
                clearMirroredPlayerVariant(msg.entityId);
            }
        }));
        ctx.setPacketHandled(true);
    }

    private static void mirrorToPlayerVariant(SyncArtistColorPacket msg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(msg.entityId);
        if (!(entity instanceof Player player)) {
            return;
        }

        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.getChangedEntity() == null) {
            return;
        }

        ArtistTintManager.setAppearance(variant.getChangedEntity().getId(), msg.color, msg.texturePath, msg.uvX, msg.uvY, msg.customUvEnabled);
    }

    private static void clearMirroredPlayerVariant(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof Player player)) {
            return;
        }

        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.getChangedEntity() == null) {
            return;
        }

        ArtistTintManager.clearTint(variant.getChangedEntity().getId());
    }
}
