package com.katt.changedextras.common;

import com.katt.changedextras.item.ArtistBrushItem;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.CustomLatexEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ArtistBrushTargetValidator {
    private static final ResourceLocation CUSTOM_LATEX_FORM_ID = ResourceLocation.tryParse(ArtistBrushItem.TARGET_FORM_ID);

    private ArtistBrushTargetValidator() {
    }

    public static boolean isAllowedTarget(Entity entity) {
        if (entity instanceof CustomLatexEntity) {
            return true;
        }

        if (entity instanceof Player player) {
            TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
            return variant != null && variant.getChangedEntity() instanceof CustomLatexEntity;
        }

        if (entity instanceof ChangedEntity changedEntity) {
            TransfurVariant<?> variant = TransfurVariant.getEntityVariant(changedEntity);
            return variant != null && variant.getFormId().equals(CUSTOM_LATEX_FORM_ID);
        }

        return false;
    }
}
