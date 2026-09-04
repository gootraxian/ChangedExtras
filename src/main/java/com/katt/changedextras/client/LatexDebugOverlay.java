package com.katt.changedextras.client;

import com.katt.changedextras.common.debug.LatexDebugSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LatexDebugOverlay {
    private static final Map<Integer, LatexDebugSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static volatile boolean enabled;

    private LatexDebugOverlay() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getSnapshotCount() {
        return SNAPSHOTS.size();
    }

    public static void setEnabled(boolean enabled) {
        LatexDebugOverlay.enabled = enabled;
        if (!enabled) {
            SNAPSHOTS.clear();
        }
    }

    public static void updateSnapshots(List<LatexDebugSnapshot> snapshots) {
        SNAPSHOTS.clear();
        for (LatexDebugSnapshot snapshot : snapshots) {
            SNAPSHOTS.put(snapshot.entityId(), snapshot);
        }
    }

    public static void clear() {
        SNAPSHOTS.clear();
        enabled = false;
    }

    public static void render(RenderLevelStageEvent event) {
        if (!enabled) {
            return;
        }

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                && event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || SNAPSHOTS.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        for (LatexDebugSnapshot snapshot : SNAPSHOTS.values()) {
            Entity entity = minecraft.level.getEntity(snapshot.entityId());
            if (entity instanceof ChangedEntity changedEntity) {
                LevelRenderer.renderLineBox(poseStack, lines, changedEntity.getBoundingBox(), 0.2F, 0.85F, 1.0F, 0.95F);

                // Render continuous path lines connecting mob to path nodes
                renderConnectedPath(poseStack, lines, changedEntity, snapshot.pathNodes());

                // Target line
                if (snapshot.targetPos() != null) {
                    renderTargetTraceLine(poseStack, lines, changedEntity.getEyePosition(), snapshot.targetPos());
                }

                // Parkour arc
                if (snapshot.parkourPos() != null) {
                    renderParkourArc(poseStack, lines, changedEntity.position(), snapshot.parkourPos());
                }
            }

            renderMarker(poseStack, lines, snapshot.targetPos(), 1.0F, 0.2F, 0.2F);
            renderMarker(poseStack, lines, snapshot.lastSeenPos(), 1.0F, 0.85F, 0.2F);
            renderMarker(poseStack, lines, snapshot.buildPos(), 0.2F, 1.0F, 0.35F);
            renderMarker(poseStack, lines, snapshot.breakPos(), 1.0F, 0.45F, 0.1F);
            renderMarker(poseStack, lines, snapshot.parkourPos(), 1.0F, 0.7F, 0.0F);

            for (BlockPos imaginedPos : snapshot.imaginedBuildPath()) {
                renderImaginedBuildNode(poseStack, lines, imaginedPos);
            }
        }
        poseStack.popPose();
        buffer.endBatch(RenderType.lines());

        // Render floating text labels above mobs
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (LatexDebugSnapshot snapshot : SNAPSHOTS.values()) {
            Entity entity = minecraft.level.getEntity(snapshot.entityId());
            if (entity instanceof ChangedEntity changedEntity) {
                renderEntityDebugLabel(minecraft, poseStack, buffer, snapshot, changedEntity);
            }
        }
        poseStack.popPose();
        buffer.endBatch();
    }

    private static void renderConnectedPath(PoseStack poseStack, VertexConsumer lines, ChangedEntity entity, List<BlockPos> pathNodes) {
        if (pathNodes.isEmpty()) return;

        Vec3 previous = entity.position().add(0.0D, 0.15D, 0.0D);
        for (int i = 0; i < pathNodes.size(); i++) {
            BlockPos nodePos = pathNodes.get(i);
            Vec3 current = Vec3.atBottomCenterOf(nodePos).add(0.0D, 0.15D, 0.0D);

            // Path line connecting previous point to current node
            renderSegment(poseStack, lines, previous, current, 0.15F, 0.95F, 0.4F, 0.95F);

            // Glowing flat pad at each node
            renderPathPad(poseStack, lines, nodePos, i == 0 ? 0.2F : 0.1F, 0.9F, 0.5F);

            previous = current;
        }
    }

    private static void renderTargetTraceLine(PoseStack poseStack, VertexConsumer lines, Vec3 from, BlockPos targetPos) {
        Vec3 to = Vec3.atCenterOf(targetPos);
        renderSegment(poseStack, lines, from, to, 1.0F, 0.2F, 0.2F, 0.7F);
    }

    private static void renderParkourArc(PoseStack poseStack, VertexConsumer lines, Vec3 from, BlockPos landing) {
        Vec3 to = Vec3.atBottomCenterOf(landing).add(0.0D, 0.1D, 0.0D);
        Vec3 delta = to.subtract(from);
        int steps = 12;
        Vec3 prev = from;

        for (int i = 1; i <= steps; i++) {
            double fraction = (double) i / steps;
            double arcY = Math.sin(fraction * Math.PI) * 0.75D;
            Vec3 cur = from.add(delta.scale(fraction)).add(0.0D, arcY, 0.0D);
            renderSegment(poseStack, lines, prev, cur, 1.0F, 0.75F, 0.1F, 0.9F);
            prev = cur;
        }
    }

    private static void renderSegment(PoseStack poseStack, VertexConsumer lines, Vec3 from, Vec3 to, float r, float g, float b, float a) {
        var pose = poseStack.last().pose();
        var normal = poseStack.last().normal();
        float nx = (float)(to.x - from.x);
        float ny = (float)(to.y - from.y);
        float nz = (float)(to.z - from.z);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-4F) {
            nx /= len; ny /= len; nz /= len;
        } else {
            nx = 0; ny = 1; nz = 0;
        }

        lines.vertex(pose, (float) from.x, (float) from.y, (float) from.z).color(r, g, b, a).normal(normal, nx, ny, nz).endVertex();
        lines.vertex(pose, (float) to.x, (float) to.y, (float) to.z).color(r, g, b, a).normal(normal, nx, ny, nz).endVertex();
    }

    private static void renderMarker(PoseStack poseStack, VertexConsumer lines, BlockPos pos, float red, float green, float blue) {
        if (pos == null) return;
        LevelRenderer.renderLineBox(
                poseStack, lines,
                pos.getX() + 0.02D, pos.getY() + 0.02D, pos.getZ() + 0.02D,
                pos.getX() + 0.98D, pos.getY() + 0.98D, pos.getZ() + 0.98D,
                red, green, blue, 0.95F
        );
    }

    private static void renderPathPad(PoseStack poseStack, VertexConsumer lines, BlockPos pos, float r, float g, float b) {
        LevelRenderer.renderLineBox(
                poseStack, lines,
                pos.getX() + 0.15D, pos.getY() + 0.02D, pos.getZ() + 0.15D,
                pos.getX() + 0.85D, pos.getY() + 0.18D, pos.getZ() + 0.85D,
                r, g, b, 0.85F
        );
    }

    private static void renderImaginedBuildNode(PoseStack poseStack, VertexConsumer lines, BlockPos pos) {
        LevelRenderer.renderLineBox(
                poseStack, lines,
                pos.getX() + 0.08D, pos.getY() + 0.08D, pos.getZ() + 0.08D,
                pos.getX() + 0.92D, pos.getY() + 0.92D, pos.getZ() + 0.92D,
                0.1F, 0.95F, 0.95F, 0.75F
        );
    }

    private static void renderEntityDebugLabel(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                               LatexDebugSnapshot snapshot, ChangedEntity entity) {
        Font font = minecraft.font;
        String line1 = "§b" + entity.getDisplayName().getString() + " §7[§e" + snapshot.stateName() + "§7]";
        String line2 = "§fPath: §a" + snapshot.pathNodes().size() + " nodes"
                + (snapshot.buildPos() != null ? " §7| §6Build" : "")
                + (snapshot.breakPos() != null ? " §7| §cBreak" : "");

        poseStack.pushPose();
        poseStack.translate(entity.getX(), entity.getY() + entity.getBbHeight() + 0.65D, entity.getZ());
        poseStack.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = 0.02F;
        poseStack.scale(-scale, -scale, scale);

        font.drawInBatch(line1, -font.width(line1) / 2.0F, 0.0F, 0xFFFFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        font.drawInBatch(line2, -font.width(line2) / 2.0F, 10.0F, 0xFFFFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        poseStack.popPose();
    }
}
