package com.katt.changedextras.client.discovery;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DetectedServerWebhookNotifier {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED_SERVERS = ConcurrentHashMap.newKeySet();
    private static volatile boolean loadedFromDisk;

    private DetectedServerWebhookNotifier() {
    }

    public static void onDetected(ServerData serverData) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DiscoverySupport.isAuthorizedUser(minecraft) || serverData == null) {
            return;
        }

        String address = normalizeAddress(serverData.ip);
        if (address.isEmpty()) {
            return;
        }

        ensureLoaded(minecraft);
        if (!REPORTED_SERVERS.add(address)) {
            return;
        }

        persistAddress(minecraft, address);
    }

    private static void ensureLoaded(Minecraft minecraft) {
        if (loadedFromDisk) {
            return;
        }

        synchronized (DetectedServerWebhookNotifier.class) {
            if (loadedFromDisk) {
                return;
            }

            Path path = getStoragePath(minecraft);
            if (Files.exists(path)) {
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String normalized = normalizeAddress(line);
                        if (!normalized.isEmpty()) {
                            REPORTED_SERVERS.add(normalized);
                        }
                    }
                } catch (IOException exception) {
                    LOGGER.debug("Failed to load detected Changed Extras servers", exception);
                }
            }

            loadedFromDisk = true;
        }
    }

    private static void persistAddress(Minecraft minecraft, String address) {
        Path path = getStoragePath(minecraft);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, address + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.debug("Failed to store detected Changed Extras server address {}", address, exception);
        }
    }

    private static Path getStoragePath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath()
                .resolve("config")
                .resolve("changedextras_detected_servers.txt");
    }

    private static String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase();
    }
}
