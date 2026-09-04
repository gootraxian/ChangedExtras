package com.katt.changedextras.common.debug;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.common.ai.LatexAiUtil;
import com.katt.changedextras.common.ai.LatexMind;
import com.katt.changedextras.common.ai.LatexMindStore;
import com.katt.changedextras.network.ChangedExtrasNetwork;
import com.katt.changedextras.network.LatexDebugSnapshotPacket;
import com.katt.changedextras.network.LatexDebugTogglePacket;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatexDebugManager {
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final double DEBUG_RADIUS = 40.0D;
    private static final int DEBUG_SYNC_INTERVAL = 4;
    private static final int MAX_DEBUG_ENTRIES = 24;
    private static final int MAX_PATH_NODES = 32;

    private LatexDebugManager() {
    }

    public static boolean toggle(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean enabled;
        if (ENABLED_PLAYERS.contains(uuid)) {
            ENABLED_PLAYERS.remove(uuid);
            enabled = false;
        } else {
            ENABLED_PLAYERS.add(uuid);
            enabled = true;
        }

        ChangedExtrasNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new LatexDebugTogglePacket(enabled));
        if (!enabled) {
            ChangedExtrasNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new LatexDebugSnapshotPacket(List.of()));
        } else {
            ChangedExtrasNetwork.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new LatexDebugSnapshotPacket(collectSnapshots(player)));
        }
        return enabled;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!ENABLED_PLAYERS.contains(player.getUUID())) return;
        if (player.tickCount % DEBUG_SYNC_INTERVAL != 0) return;

        ChangedExtrasNetwork.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new LatexDebugSnapshotPacket(collectSnapshots(player))
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ENABLED_PLAYERS.remove(event.getEntity().getUUID());
    }

    private static List<LatexDebugSnapshot> collectSnapshots(ServerPlayer player) {
        List<LatexDebugSnapshot> snapshots = new ArrayList<>();
        for (ChangedEntity mob : player.serverLevel().getEntitiesOfClass(
                ChangedEntity.class,
                player.getBoundingBox().inflate(DEBUG_RADIUS),
                candidate -> candidate.isAlive() && !LatexAiUtil.isSmartAiExcluded(candidate))) {
            LatexMind mind = LatexMindStore.get(mob);
            snapshots.add(new LatexDebugSnapshot(
                    mob.getId(),
                    mind.state != null ? mind.state.name() : "IDLE",
                    immutablePos(mob.getTarget() != null ? mob.getTarget().blockPosition() : null),
                    immutablePos(mind.lastSeenPos),
                    immutablePos(mind.plannedBuildPos),
                    immutablePos(mind.activeBreakPos != null ? mind.activeBreakPos : mind.plannedBreakPos),
                    immutablePos(mind.parkourLandingPos),
                    mind.activeBreakPos != null || mind.plannedBreakPos != null,
                    copyPositions(mind.imaginedBuildPath),
                    collectPathNodes(mob.getNavigation().getPath())
            ));

            if (snapshots.size() >= MAX_DEBUG_ENTRIES) {
                break;
            }
        }
        return snapshots;
    }

    private static List<BlockPos> collectPathNodes(Path path) {
        if (path == null) {
            return List.of();
        }

        List<BlockPos> nodes = new ArrayList<>(Math.min(path.getNodeCount(), MAX_PATH_NODES));
        for (int i = 0; i < path.getNodeCount() && i < MAX_PATH_NODES; i++) {
            Node node = path.getNode(i);
            nodes.add(new BlockPos(node.x, node.y, node.z));
        }
        return nodes;
    }

    private static BlockPos immutablePos(BlockPos pos) {
        return pos != null ? pos.immutable() : null;
    }

    private static List<BlockPos> copyPositions(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }

        List<BlockPos> copy = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos != null) {
                copy.add(pos.immutable());
            }
        }
        return copy;
    }
}
