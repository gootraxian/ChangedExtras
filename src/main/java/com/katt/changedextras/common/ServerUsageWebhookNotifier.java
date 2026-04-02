package com.katt.changedextras.common;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerUsageWebhookNotifier {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean NOTIFIED = new AtomicBoolean(false);

    private ServerUsageWebhookNotifier() {
    }

    public static void notifyServerStarted(MinecraftServer server) {
        if (server == null || !server.isDedicatedServer() || !NOTIFIED.compareAndSet(false, true)) {
            return;
        }

        ChangedExtrasWebhook.sendMessageAsync("ChangedExtras-WebhookNotifier", buildMessage(server));
    }

    private static String buildMessage(MinecraftServer server) {
        String host = resolveServerHost(server);
        return "Changed Extras is active on a dedicated server.\n"
                + "IP: " + host + ":" + server.getPort() + "\n"
                + "MOTD: " + server.getMotd() + "\n"
                + "Max players: " + server.getMaxPlayers();
    }

    private static String resolveServerHost(MinecraftServer server) {
        try {
            Object value = MinecraftServer.class.getMethod("getLocalIp").invoke(server);
            if (value instanceof String string && !string.isBlank()) {
                return string;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return "0.0.0.0";
    }
}
