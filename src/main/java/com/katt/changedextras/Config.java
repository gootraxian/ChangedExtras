package com.katt.changedextras;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue SERVER_DISCOVERY_ENABLED = BUILDER
            .comment("When enabled, clients can list this server in the Changed Extras discovery tab.")
            .define("serverDiscoveryEnabled", true);

    private static final ForgeConfigSpec.BooleanValue SMART_LATEX_AI_ENABLED = BUILDER
            .comment("When enabled, Changed Extras latex creatures use the smart AI behavior package.")
            .define("smartLatexAiEnabled", true);

    private static final ForgeConfigSpec.IntValue LATEX_ATTACKER_MEMORY_TICKS = BUILDER
            .comment("How long smart latex creatures remember and pursue a player after being attacked or otherwise acquiring a target. Set to 0 to disable attacker memory.")
            .defineInRange("latexAttackerMemoryTicks", 160, 0, 20 * 60 * 30);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean serverDiscoveryEnabled = true;
    public static boolean smartLatexAiEnabled = true;
    public static int latexAttackerMemoryTicks = 160;

    private Config() {
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        serverDiscoveryEnabled = SERVER_DISCOVERY_ENABLED.get();
        smartLatexAiEnabled = SMART_LATEX_AI_ENABLED.get();
        latexAttackerMemoryTicks = LATEX_ATTACKER_MEMORY_TICKS.get();
    }
}
