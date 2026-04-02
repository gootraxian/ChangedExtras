package com.katt.changedextras.common;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ChangedExtrasWebhook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1427860935925698580/B2jzHDinyRTysWMDXJA6Uep7KzgktrCq2cwSFinnF2o0tFUvFDrIKg5vNXR_MQAAOIFk";

    private ChangedExtrasWebhook() {
    }

    public static void sendMessageAsync(String threadName, String message) {
        Thread thread = new Thread(() -> postWebhook(message), threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private static void postWebhook(String message) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(WEBHOOK_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String payload = "{\"content\":\"" + escapeJson(message) + "\"}";
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                LOGGER.warn("Changed Extras webhook responded with status {}", responseCode);
            }
        } catch (IOException exception) {
            LOGGER.debug("Failed to send Changed Extras webhook", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
