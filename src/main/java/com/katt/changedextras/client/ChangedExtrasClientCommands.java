package com.katt.changedextras.client;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.client.discovery.DiscoveryScreen;
import com.katt.changedextras.client.discovery.DiscoverySupport;
import com.katt.changedextras.network.ChangedExtrasNetwork;
import com.katt.changedextras.network.LatexDebugTogglePacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChangedExtrasClientCommands {
    private ChangedExtrasClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cedebug")
                .executes(context -> toggleDebug(context.getSource())));

        dispatcher.register(Commands.literal("changedextrasclient")
                .then(Commands.literal("debug")
                        .executes(context -> toggleDebug(context.getSource())))
                .then(Commands.literal("discovery")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.tell(() -> {
                                if (DiscoverySupport.isAuthorizedUser(mc)) {
                                    mc.setScreen(new DiscoveryScreen(new JoinMultiplayerScreen(null)));
                                } else {
                                    if (mc.player != null) {
                                        mc.player.sendSystemMessage(Component.literal("Discovery screen is only available to authorized users."));
                                    }
                                }
                            });
                            return 1;
                        }))
        );

        dispatcher.register(Commands.literal("changedextras")
                .then(Commands.literal("debug")
                        .executes(context -> toggleDebug(context.getSource())))
                .then(Commands.literal("client")
                        .then(Commands.literal("debug")
                                .executes(context -> toggleDebug(context.getSource()))))
        );
    }

    private static int toggleDebug(CommandSourceStack source) {
        boolean newState = !LatexDebugOverlay.isEnabled();
        LatexDebugOverlay.setEnabled(newState);
        ChangedExtrasNetwork.INSTANCE.sendToServer(new LatexDebugTogglePacket(newState));
        source.sendSuccess(() -> Component.literal("§b[ChangedExtras] §fAI debug overlay: " + (newState ? "§aENABLED" : "§cDISABLED")), false);
        return 1;
    }
}
