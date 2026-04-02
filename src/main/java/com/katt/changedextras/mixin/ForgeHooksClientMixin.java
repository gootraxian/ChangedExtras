package com.katt.changedextras.mixin;

import com.katt.changedextras.client.discovery.DetectedServerWebhookNotifier;
import com.katt.changedextras.client.discovery.ChangedExtrasServerMarker;
import com.katt.changedextras.client.discovery.DiscoverySupport;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeHooksClient.class)
public class ForgeHooksClientMixin {
    @Inject(method = "processForgeListPingData", at = @At("HEAD"), remap = false)
    private static void changedextras$captureMarker(ServerStatus status, ServerData target, CallbackInfo ci) {
        boolean hasChangedExtras = DiscoverySupport.hasChangedExtras(status.forgeData().orElse(null));
        ((ChangedExtrasServerMarker) target).changedextras$setHasChangedExtras(hasChangedExtras);
        if (hasChangedExtras) {
            DetectedServerWebhookNotifier.onDetected(target);
        }
    }
}
