package com.katt.changedextras.client.hud;

import com.katt.changedextras.ChangedExtras;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
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
    private static final ResourceLocation CONEKAT_MALE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "conekat_male");
    private static final ResourceLocation CONEKAT_FEMALE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "conekat_female");

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
        if (variant != null && isConeKat(variant)) return;

        float progress = ProcessTransfur.getPlayerTransfurProgress(mc.player);
        float tolerance = Math.max(0.0001F, (float) ProcessTransfur.getEntityTransfurTolerance(mc.player));
        float dangerLevel = Mth.clamp(progress / tolerance, 0.0F, 1.0F);
        if (dangerLevel <= FINAL_STAGE_START) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float fadeProgress = Mth.clamp((dangerLevel - FINAL_STAGE_START) / (1.0F - FINAL_STAGE_START), 0.0F, 1.0F);
        int alpha = Math.max(0, Math.min(255, Math.round(fadeProgress * 255.0F)));
        int color = (alpha << 24) | 0x00FFFFFF;
        guiGraphics.fill(0, 0, w, h, color);
    }

    private static boolean isConeKat(TransfurVariantInstance<?> variant) {
        ResourceLocation formId = variant.getFormId();
        return CONEKAT_MALE.equals(formId) || CONEKAT_FEMALE.equals(formId);
    }
}
