package com.katt.changedextras.network;

import com.katt.changedextras.client.LatexDebugOverlay;
import com.katt.changedextras.common.debug.LatexDebugSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LatexDebugSnapshotPacket {
    private final List<LatexDebugSnapshot> snapshots;

    public LatexDebugSnapshotPacket(List<LatexDebugSnapshot> snapshots) {
        this.snapshots = List.copyOf(snapshots);
    }

    public static void encode(LatexDebugSnapshotPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.snapshots.size());
        for (LatexDebugSnapshot snapshot : msg.snapshots) {
            buf.writeVarInt(snapshot.entityId());
            buf.writeUtf(snapshot.stateName(), 32);
            writeNullablePos(buf, snapshot.targetPos());
            writeNullablePos(buf, snapshot.lastSeenPos());
            writeNullablePos(buf, snapshot.buildPos());
            writeNullablePos(buf, snapshot.breakPos());
            writeNullablePos(buf, snapshot.parkourPos());
            buf.writeBoolean(snapshot.requiresBreak());
            buf.writeVarInt(snapshot.imaginedBuildPath().size());
            for (BlockPos pos : snapshot.imaginedBuildPath()) {
                buf.writeBlockPos(pos);
            }
            buf.writeVarInt(snapshot.pathNodes().size());
            for (BlockPos node : snapshot.pathNodes()) {
                buf.writeBlockPos(node);
            }
        }
    }

    public static LatexDebugSnapshotPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<LatexDebugSnapshot> snapshots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int entityId = buf.readVarInt();
            String stateName = buf.readUtf(32);
            BlockPos targetPos = readNullablePos(buf);
            BlockPos lastSeenPos = readNullablePos(buf);
            BlockPos buildPos = readNullablePos(buf);
            BlockPos breakPos = readNullablePos(buf);
            BlockPos parkourPos = readNullablePos(buf);
            boolean requiresBreak = buf.readBoolean();
            int imaginedCount = buf.readVarInt();
            List<BlockPos> imagined = new ArrayList<>(imaginedCount);
            for (int imaginedIndex = 0; imaginedIndex < imaginedCount; imaginedIndex++) {
                imagined.add(buf.readBlockPos());
            }
            int nodeCount = buf.readVarInt();
            List<BlockPos> nodes = new ArrayList<>(nodeCount);
            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
                nodes.add(buf.readBlockPos());
            }
            snapshots.add(new LatexDebugSnapshot(entityId, stateName, targetPos, lastSeenPos, buildPos, breakPos, parkourPos, requiresBreak, imagined, nodes));
        }
        return new LatexDebugSnapshotPacket(snapshots);
    }

    public static void handle(LatexDebugSnapshotPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LatexDebugOverlay.updateSnapshots(msg.snapshots)));
        ctx.setPacketHandled(true);
    }

    private static void writeNullablePos(FriendlyByteBuf buf, BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }

    private static BlockPos readNullablePos(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }
}
