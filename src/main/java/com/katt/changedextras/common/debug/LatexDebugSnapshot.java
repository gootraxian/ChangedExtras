package com.katt.changedextras.common.debug;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

public record LatexDebugSnapshot(
        int entityId,
        String stateName,
        @Nullable BlockPos targetPos,
        @Nullable BlockPos lastSeenPos,
        @Nullable BlockPos buildPos,
        @Nullable BlockPos breakPos,
        @Nullable BlockPos parkourPos,
        boolean requiresBreak,
        List<BlockPos> imaginedBuildPath,
        List<BlockPos> pathNodes
) {
    public LatexDebugSnapshot {
        imaginedBuildPath = List.copyOf(imaginedBuildPath);
        pathNodes = List.copyOf(pathNodes);
    }
}
