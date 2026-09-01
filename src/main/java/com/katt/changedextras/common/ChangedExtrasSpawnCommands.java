package com.katt.changedextras.common;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.ModTransfurVariants;
import com.katt.changedextras.entity.beasts.JammerEntity;
import com.katt.changedextras.network.ChangedExtrasNetwork;
import com.katt.changedextras.network.OpenLatexSpawnControlScreenPacket;
import com.katt.changedextras.network.SyncVisorPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.ltxprogrammer.changed.entity.robot.Exoskeleton;
import net.ltxprogrammer.changed.item.ExoskeletonItem;
import net.ltxprogrammer.changed.process.ProcessTransfur;
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
import java.util.Optional;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChangedExtrasSpawnCommands {
    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType NO_EXOSKELETON = new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Target has no exoskeleton entity or equipped exoskeleton."));
    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType NO_JAMMER = new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Target is not currently a Jammer."));

    private ChangedExtrasSpawnCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("changedextras")
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
                                                )))))));
    }

    private static int openScreen(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChangedExtrasNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenLatexSpawnControlScreenPacket(
                        source.getServer().getGameRules().getBoolean(ChangedExtrasGameRules.LATEX_SPAWN_IN_DAY),
                        LatexSpawnRegistry.buildEntries(source.getServer())
                )
        );
        return 1;
    }

    private static int setJammerVip(CommandSourceStack source, Collection<ServerPlayer> targets, boolean vip) throws CommandSyntaxException {
        int updated = 0;
        for (ServerPlayer target : targets) {
            if (applyJammerVip(target, vip)) {
                updated++;
            }
        }

        if (updated == 0) {
            throw NO_JAMMER.create();
        }

        int affected = updated;
        source.sendSuccess(() -> Component.literal((vip ? "Set" : "Removed") + " Jammer VIP for " + affected + " player(s)."), true);
        return updated;
    }

    private static boolean applyJammerVip(ServerPlayer player, boolean vip) {
        return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .filter(instance -> instance.is(ModTransfurVariants.JAMMER.get()))
                .map(instance -> {
                    if (instance.getChangedEntity() instanceof JammerEntity jammer) {
                        jammer.setVip(vip);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private static int setOwnVisorStyle(CommandSourceStack source, ExoskeletonVisorStyle.Pattern pattern) throws CommandSyntaxException {
        return setVisorStyle(source, java.util.List.of(source.getEntityOrException()), pattern);
    }

    private static int setVisorStyle(CommandSourceStack source, Collection<? extends Entity> targets, ExoskeletonVisorStyle.Pattern pattern) throws CommandSyntaxException {
        ExoskeletonVisorStyle.Data data = parseStyle(pattern);

        int updated = 0;
        for (Entity target : targets) {
            if (applyVisorStyle(target, data)) {
                updated++;
            }
        }

        if (updated == 0) {
            throw NO_EXOSKELETON.create();
        }

        String formatted = describeStyle(data);
        int affected = updated;
        source.sendSuccess(() -> Component.literal("Set exoskeleton visor style to " + formatted + " for " + affected + " target(s)."), true);
        return updated;
    }

    private static boolean applyVisorStyle(Entity target, ExoskeletonVisorStyle.Data data) {
        if (target instanceof Exoskeleton exoskeleton) {
            ExoskeletonVisorColorHolder holder = (ExoskeletonVisorColorHolder)exoskeleton;
            holder.changedextras$setVisorPattern(data.pattern());
            holder.changedextras$setVisorColor(data.primaryColor());
            holder.changedextras$setVisorSecondaryColor(data.secondaryColor());
            holder.changedextras$setCustomVisorColors(data.customColors());
            // Also write to persistent data and item stack so the data survives respawn/reload
            ExoskeletonVisorStyle.write(exoskeleton.getPersistentData(), data);
            net.ltxprogrammer.changed.entity.robot.Exoskeleton.getEntityExoskeleton(exoskeleton).ifPresent(pair -> ExoskeletonVisorStyle.write(pair.getFirst(), data));
            // Broadcast to clients
            SyncVisorPacket.broadcast(exoskeleton, data);
            return true;
        }

        if (!(target instanceof LivingEntity living)) {
            return false;
        }

        Optional<Pair<ItemStack, ExoskeletonItem<?>>> optional = Exoskeleton.getEntityExoskeleton(living);
        if (optional.isEmpty()) {
            return false;
        }

        ExoskeletonVisorStyle.write(optional.get().getFirst(), data);
        ExoskeletonVisorStyle.write(living.getPersistentData(), data);
        // Immediately broadcast so clients don't need to wait for the next server tick
        SyncVisorPacket.broadcast(living, data);
        return true;
    }

    private static ExoskeletonVisorStyle.Data parseStyle(ExoskeletonVisorStyle.Pattern pattern) {
        ExoskeletonVisorStyle.Pattern resolved = pattern == null ? ExoskeletonVisorStyle.Pattern.PATTERN2 : pattern;
        return new ExoskeletonVisorStyle.Data(
                resolved,
                ExoskeletonVisorColor.DEFAULT_COLOR,
                ExoskeletonVisorColor.DEFAULT_COLOR,
                false
        );
    }

    private static String describeStyle(ExoskeletonVisorStyle.Data data) {
        return switch (data.pattern()) {
            case PATTERN1 -> "hypnosis";
            case PATTERN2 -> "default";
        };
    }
}
