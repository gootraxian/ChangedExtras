package com.katt.changedextras.network;

import com.katt.changedextras.common.ArtistAppearanceStorage;
import com.katt.changedextras.common.ArtistBrushTargetValidator;
import com.katt.changedextras.item.ArtistBrushItem;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public class SaveArtistBrushPacket {
    private final InteractionHand hand;
    private final String texturePath;
    private final String hexColor;

    public SaveArtistBrushPacket(InteractionHand hand, String texturePath, String hexColor) {
        this.hand = hand;
        this.texturePath = texturePath;
        this.hexColor = hexColor;
    }

    public static void encode(SaveArtistBrushPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeUtf(msg.texturePath);
        buf.writeUtf(msg.hexColor, 16);
    }

    public static SaveArtistBrushPacket decode(FriendlyByteBuf buf) {
        return new SaveArtistBrushPacket(
                buf.readEnum(InteractionHand.class),
                buf.readUtf(),
                buf.readUtf(16)
        );
    }

    public static void handle(SaveArtistBrushPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getItemInHand(msg.hand);
            if (!(stack.getItem() instanceof ArtistBrushItem)) {
                return;
            }

            CompoundTag brushData = ArtistBrushItem.getOrCreateBrushData(stack);
            brushData.putString(ArtistBrushItem.TEXTURE_PATH_TAG, msg.texturePath);
            brushData.putString(ArtistBrushItem.HEX_COLOR_TAG, msg.hexColor);
            brushData.putString(ArtistBrushItem.TARGET_FORM_TAG, ArtistBrushItem.TARGET_FORM_ID);

            String targetUuid = brushData.getString(ArtistBrushItem.SELECTED_TARGET_UUID_TAG);
            if (!targetUuid.isBlank()) {
                Set<Entity> targets = resolveTargets(player, brushData, targetUuid);
                if (!targets.isEmpty() && targets.stream().allMatch(ArtistBrushTargetValidator::isAllowedTarget)) {
                    int color = parseColor(msg.hexColor);
                    for (Entity target : targets) {
                        ArtistAppearanceStorage.saveAppearance(target, color, msg.texturePath, 0, 0, false);
                        ArtistAppearanceStorage.syncAppearance(target);
                    }
                } else if (!targets.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.changedextras.artist_brush.custom_latex_only"), true);
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    private static Set<Entity> resolveTargets(ServerPlayer player, CompoundTag brushData, String targetUuid) {
        LinkedHashSet<Entity> targets = new LinkedHashSet<>();
        java.util.UUID uuid;
        try {
            uuid = java.util.UUID.fromString(targetUuid);
        } catch (IllegalArgumentException ignored) {
            return targets;
        }

        String targetType = brushData.getString(ArtistBrushItem.SELECTED_TARGET_TYPE_TAG);
        if ("self".equals(targetType)) {
            addPlayerTargets(targets, player);
            return targets;
        }

        if ("player".equals(targetType)) {
            ServerPlayer targetPlayer = player.server.getPlayerList().getPlayer(uuid);
            if (targetPlayer != null) {
                addPlayerTargets(targets, targetPlayer);
                return targets;
            }
        }

        Entity target = player.serverLevel().getEntity(uuid);
        if (target != null) {
            targets.add(target);
            return targets;
        }

        ServerPlayer targetPlayer = player.server.getPlayerList().getPlayer(uuid);
        if (targetPlayer != null) {
            addPlayerTargets(targets, targetPlayer);
        }

        return targets;
    }

    private static void addPlayerTargets(Set<Entity> targets, ServerPlayer player) {
        targets.add(player);
        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant != null && variant.getChangedEntity() != null) {
            targets.add(variant.getChangedEntity());
        }
    }

    private static int parseColor(String hexColor) {
        String normalized = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            return 0xFFFFFF;
        }
    }
}
