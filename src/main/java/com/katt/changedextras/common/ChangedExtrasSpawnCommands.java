package com.katt.changedextras.common;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.common.debug.LatexDebugManager;
import com.katt.changedextras.entity.beasts.JammerEntity;
import com.katt.changedextras.network.ChangedExtrasNetwork;
import com.katt.changedextras.network.OpenLatexSpawnControlScreenPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ltxprogrammer.changed.item.ExoskeletonItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChangedExtrasSpawnCommands {
    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType NO_EXOSKELETON = new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Target has no equipped exoskeleton."));

    private ChangedExtrasSpawnCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cedebug")
                .executes(context -> toggleDebug(context.getSource())));

        dispatcher.register(Commands.literal("changedextras")
                .then(Commands.literal("debug")
                        .executes(context -> toggleDebug(context.getSource())))
                .then(Commands.literal("client")
                        .then(Commands.literal("debug")
                                .executes(context -> toggleDebug(context.getSource()))))
                .then(Commands.literal("spawns")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> openScreen(context.getSource())))
                .then(Commands.literal("visorstyle")
                        .then(Commands.literal("hypnosis")
                                .executes(context -> setOwnVisorStyle(context.getSource(), ExoskeletonVisorStyle.Pattern.PATTERN1)))
                        .then(Commands.literal("default")
                                .executes(context -> setOwnVisorStyle(context.getSource(), ExoskeletonVisorStyle.Pattern.PATTERN2))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawns")
                                .executes(context -> openScreen(context.getSource())))
                        .then(Commands.literal("jammer")
                                .then(Commands.literal("vip")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.literal("set")
                                                        .executes(context -> setJammerVip(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                true
                                                        )))
                                                .then(Commands.literal("remove")
                                                        .executes(context -> setJammerVip(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                false
                                                        ))))))
                        .then(Commands.literal("visorstyle")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.literal("hypnosis")
                                                .executes(context -> setVisorStyle(
                                                        context.getSource(),
                                                        EntityArgument.getEntities(context, "targets"),
                                                        ExoskeletonVisorStyle.Pattern.PATTERN1
                                                )))
                                        .then(Commands.literal("default")
                                                .executes(context -> setVisorStyle(
                                                        context.getSource(),
                                                        EntityArgument.getEntities(context, "targets"),
                                                        ExoskeletonVisorStyle.Pattern.PATTERN2
                                                ))))))
        );
    }

    private static int toggleDebug(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = LatexDebugManager.toggle(player);
        source.sendSuccess(() -> Component.literal("§b[ChangedExtras] §fAI debug overlay: " + (enabled ? "§aENABLED" : "§cDISABLED")), false);
        return 1;
    }

    private static int openScreen(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean allowDaySpawns = player.serverLevel().getGameRules().getBoolean(ChangedExtrasGameRules.LATEX_SPAWN_IN_DAY);
        List<LatexSpawnVariantEntry> entries = LatexSpawnRegistry.buildEntries(player.serverLevel().getServer());
        ChangedExtrasNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenLatexSpawnControlScreenPacket(allowDaySpawns, entries));
        return 1;
    }

    private static int setOwnVisorStyle(CommandSourceStack source, ExoskeletonVisorStyle.Pattern pattern) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = applyVisorStyle(player, pattern);
        if (!success) {
            throw NO_EXOSKELETON.create();
        }

        source.sendSuccess(() -> Component.literal("Exoskeleton visor style set to " + pattern.id() + "."), false);
        return 1;
    }

    private static int setVisorStyle(CommandSourceStack source, Collection<? extends Entity> targets, ExoskeletonVisorStyle.Pattern pattern) throws CommandSyntaxException {
        int count = 0;
        for (Entity target : targets) {
            if (applyVisorStyle(target, pattern)) {
                count++;
            }
        }

        if (count == 0) {
            throw NO_EXOSKELETON.create();
        }

        int finalCount = count;
        source.sendSuccess(() -> Component.literal("Exoskeleton visor style set to " + pattern.id() + " for " + finalCount + " target(s)."), true);
        return count;
    }

    private static boolean applyVisorStyle(Entity target, ExoskeletonVisorStyle.Pattern pattern) {
        if (target instanceof LivingEntity living) {
            ItemStack stack = findEquippedExoskeleton(living);
            if (!stack.isEmpty()) {
                ExoskeletonVisorStyle.Data current = ExoskeletonVisorStyle.read(stack);
                ExoskeletonVisorStyle.write(stack, new ExoskeletonVisorStyle.Data(pattern, current.primaryColor(), current.secondaryColor(), current.customColors()));
                return true;
            }
        }
        return false;
    }

    private static ItemStack findEquippedExoskeleton(LivingEntity living) {
        for (ItemStack itemStack : living.getArmorSlots()) {
            if (itemStack.getItem() instanceof ExoskeletonItem) {
                return itemStack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int setJammerVip(CommandSourceStack source, Collection<ServerPlayer> targets, boolean vip) {
        int count = 0;
        for (ServerPlayer player : targets) {
            player.getPersistentData().putBoolean(JammerEntity.VIP_TAG, vip);
            count++;
        }

        int finalCount = count;
        source.sendSuccess(() -> Component.literal((vip ? "Granted" : "Revoked") + " Jammer VIP for " + finalCount + " player(s)."), true);
        return count;
    }
}
