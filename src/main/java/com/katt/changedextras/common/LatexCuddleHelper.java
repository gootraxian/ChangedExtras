package com.katt.changedextras.common;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public final class LatexCuddleHelper {
    private LatexCuddleHelper() {
    }

    @Nullable
    public static Player getTamingOwner(ChangedEntity entity) {
        if (!(entity instanceof TamableLatexEntity tamable) || !tamable.isTame()) {
            return null;
        }

        UUID ownerUuid = tamable.getOwnerUUID();
        if (ownerUuid == null) {
            return null;
        }

        Level level = entity.level();
        for (Player player : level.players()) {
            if (ownerUuid.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }

    public static boolean shouldCuddle(ChangedEntity entity) {
        Player owner = getTamingOwner(entity);
        if (owner == null || !owner.isSleeping()) {
            return false;
        }

        return entity.distanceToSqr(getCuddlePosition(owner, entity)) <= 2.25D;
    }

    public static boolean isTamingOwner(ChangedEntity entity, Player player) {
        Player owner = getTamingOwner(entity);
        return owner != null && owner == player;
    }

    public static Vec3 getCuddlePosition(Player owner, ChangedEntity entity) {
        BlockPos bedPos = owner.getSleepingPos().orElse(owner.blockPosition());
        Direction bedDir = owner.getBedOrientation() != null ? owner.getBedOrientation() : Direction.SOUTH;

        Vec3 center = Vec3.atBottomCenterOf(bedPos).add(0.0D, 0.42D, 0.0D);
        Vec3 foot = Vec3.atLowerCornerOf(bedDir.getOpposite().getNormal()).scale(0.68D);
        Direction sideDir = bedDir.getClockWise();
        double sideOffset = (entity.getId() & 1) == 0 ? 0.28D : -0.28D;
        Vec3 side = Vec3.atLowerCornerOf(sideDir.getNormal()).scale(sideOffset);
        return center.add(foot).add(side);
    }

    public static float getCuddleYaw(Player owner) {
        Direction bedDir = owner.getBedOrientation() != null ? owner.getBedOrientation() : Direction.SOUTH;
        return switch (bedDir) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
