package com.katt.changedextras.common.ai;

import com.katt.changedextras.entity.beasts.ArtistEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Set;

public final class LatexAiUtil {
    private static final TagKey<EntityType<?>> CHANGED_HUMANOIDS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "humanoids"));
    private static final TagKey<EntityType<?>> CHANGED_LATEXES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "latexes"));

    private static final Set<String> SMART_AI_EXCLUDED_FORMS = Set.of(
            "changedextras:artist",
            "changed_addon:form_experiment_009",
            "changed_addon:form_experiment_10"
    );

    public enum LatexAlignment {
        WHITE,
        DARK,
        OTHER,
        NONE
    }

    private LatexAiUtil() {
    }

    public static boolean isSmartAiExcluded(ChangedEntity mob) {
        if (mob instanceof ArtistEntity) {
            return true;
        }

        String identity = getEntityIdentity(mob);
        return SMART_AI_EXCLUDED_FORMS.contains(identity)
                || identity.contains("experiment_009")
                || identity.contains("experiment_10");
    }

    public static boolean isPlayerTransfurred(Player player) {
        return ProcessTransfur.isPlayerTransfurred(player) || ProcessTransfur.getPlayerTransfurVariant(player) != null;
    }

    public static LatexAlignment getAlignment(@Nullable LivingEntity entity) {
        if (entity == null) {
            return LatexAlignment.NONE;
        }

        if (entity instanceof ChangedEntity changedEntity) {
            return classifyAlignment(getEntityIdentity(changedEntity));
        }

        if (entity instanceof Player player) {
            if (isPlayerTransfurred(player)) {
                return classifyAlignment(getPlayerFormId(player));
            }
            return LatexAlignment.NONE;
        }

        if (entity.getType().is(CHANGED_LATEXES)) {
            ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            return entityTypeId != null ? classifyAlignment(entityTypeId.toString()) : LatexAlignment.OTHER;
        }

        return LatexAlignment.NONE;
    }

    public static boolean isSameLatexType(LivingEntity a, LivingEntity b) {
        LatexAlignment alignA = getAlignment(a);
        LatexAlignment alignB = getAlignment(b);
        if (alignA == LatexAlignment.NONE || alignA == LatexAlignment.OTHER) {
            return false;
        }
        return alignA == alignB;
    }

    public static boolean areHostileLatexFactions(LivingEntity a, LivingEntity b) {
        LatexAlignment alignA = getAlignment(a);
        LatexAlignment alignB = getAlignment(b);
        if (alignA == LatexAlignment.NONE || alignB == LatexAlignment.NONE) {
            return false;
        }
        if (alignA == LatexAlignment.OTHER || alignB == LatexAlignment.OTHER) {
            return false;
        }
        return (alignA == LatexAlignment.WHITE && alignB == LatexAlignment.DARK)
                || (alignA == LatexAlignment.DARK && alignB == LatexAlignment.WHITE);
    }

    public static boolean isTransfurrable(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            return !isPlayerTransfurred(player);
        }
        if (entity instanceof Villager) {
            return true;
        }
        if (entity.getType().is(CHANGED_HUMANOIDS)) {
            return true;
        }
        return false;
    }

    @Nullable
    public static String getEntityFormId(ChangedEntity entity) {
        TransfurVariant<?> variant = TransfurVariant.findEntityTransfurVariant(entity);
        if (variant == null || variant.getFormId() == null) {
            return null;
        }

        return variant.getFormId().toString();
    }

    @Nullable
    public static String getPlayerFormId(Player player) {
        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null || variant.getFormId() == null) {
            return null;
        }

        return variant.getFormId().toString();
    }

    private static String getEntityIdentity(ChangedEntity entity) {
        String formId = getEntityFormId(entity);
        if (formId != null) {
            return formId;
        }

        ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return entityTypeId != null ? entityTypeId.toString() : "";
    }

    private static LatexAlignment classifyAlignment(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return LatexAlignment.OTHER;
        }

        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("white") || normalized.contains("light") || normalized.contains("pure_white")) {
            return LatexAlignment.WHITE;
        }
        if (normalized.contains("dark") || normalized.contains("black")) {
            return LatexAlignment.DARK;
        }

        return LatexAlignment.OTHER;
    }
}
