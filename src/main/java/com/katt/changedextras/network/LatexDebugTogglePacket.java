package com.katt.changedextras.network;

import com.katt.changedextras.client.LatexDebugOverlay;
import com.katt.changedextras.common.debug.LatexDebugManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LatexDebugTogglePacket {
    private final boolean enabled;

    public LatexDebugTogglePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(LatexDebugTogglePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
    }

    public static LatexDebugTogglePacket decode(FriendlyByteBuf buf) {
        return new LatexDebugTogglePacket(buf.readBoolean());
    }

    public static void handle(LatexDebugTogglePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isServer()) {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    LatexDebugManager.toggle(player);
                }
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LatexDebugOverlay.setEnabled(msg.enabled));
            }
        });
        ctx.setPacketHandled(true);
    }
}
