package com.katt.changedextras.client.hud;

import com.katt.changedextras.ChangedExtras;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.katt.changedextras.ChangedExtras.MODID, value = Dist.CLIENT)
public class InfectionOverlay {
    private static final float FINAL_STAGE_START = 0.9F;
    private static final int FADE_OUT_TICKS = 100;
    private static final ResourceLocation CONEKAT_MALE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "conekat_male");
    private static final ResourceLocation CONEKAT_FEMALE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "conekat_female");

    private static boolean wasFullyTransfurred = false;
    private static boolean fadeDismissed = false;
    private static long fullTransfurTick = -1L;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        if (Minecraft.getInstance().screen != null) return;

        renderOverlay(Minecraft.getInstance(), event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        renderOverlay(mc, event.getGuiGraphics());
    }

    private static void renderOverlay(Minecraft mc, GuiGraphics guiGraphics) {
        if (mc.player == null || mc.level == null) return;

        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(mc.player);
        if (variant != null && isConeKat(variant)) {
            resetFadeState();
            return;
        }

        float progress = ProcessTransfur.getPlayerTransfurProgress(mc.player);
        float tolerance = Math.max(0.0001F, (float) ProcessTransfur.getEntityTransfurTolerance(mc.player));
        float dangerLevel = Mth.clamp(progress / tolerance, 0.0F, 1.0F);

        boolean isFullyTransfurred = dangerLevel >= 1.0F;
        long nowTick = mc.level.getGameTime();

        if (isFullyTransfurred) {
            if (!wasFullyTransfurred && !fadeDismissed) {
                wasFullyTransfurred = true;
                fullTransfurTick = nowTick;
            }
        } else {
            resetFadeState();
        }

        if (fadeDismissed) return;

        float alphaFraction;
        if (wasFullyTransfurred) {
            long ticksSinceFull = nowTick - fullTransfurTick;
            if (ticksSinceFull >= FADE_OUT_TICKS) {
                wasFullyTransfurred = false;
                fadeDismissed = true;
                return;
            }
            alphaFraction = 1.0F - Mth.clamp(ticksSinceFull / (float) FADE_OUT_TICKS, 0.0F, 1.0F);
        } else {
            if (dangerLevel <= FINAL_STAGE_START) return;
            alphaFraction = Mth.clamp((dangerLevel - FINAL_STAGE_START) / (1.0F - FINAL_STAGE_START), 0.0F, 1.0F);
        }

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int alpha = Math.max(0, Math.min(255, Math.round(alphaFraction * 255.0F)));
        int rgb = getTransfurColor(variant) & 0x00FFFFFF;
        int color = (alpha << 24) | rgb;

        guiGraphics.fill(0, 0, w, h, color);
    }

    private static void resetFadeState() {
        wasFullyTransfurred = false;
        fadeDismissed = false;
        fullTransfurTick = -1L;
    }

    private static int getTransfurColor(TransfurVariantInstance<?> variant) {
        if (variant == null) return 0xFFFFFF;
        return variant.getTransfurColor().toInt();
    }

    private static boolean isConeKat(TransfurVariantInstance<?> variant) {
        ResourceLocation formId = variant.getFormId();
        return CONEKAT_MALE.equals(formId) || CONEKAT_FEMALE.equals(formId);
    }
}