package com.katt.changedextras.common;

import com.katt.changedextras.ChangedExtras;
import net.minecraft.world.level.GameRules;

public final class ChangedExtrasGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> LATEX_SPAWN_IN_DAY = GameRules.register(
            "changedextrasLatexSpawnInDay",
            GameRules.Category.SPAWNING,
            GameRules.BooleanValue.create(false)
    );

    public static final GameRules.Key<GameRules.BooleanValue> CONEKAT_MALE_NATURAL_SPAWNS = GameRules.register(
            "changedextrasConekatMaleNaturalSpawns",
            GameRules.Category.SPAWNING,
            GameRules.BooleanValue.create(true)
    );

    public static final GameRules.Key<GameRules.BooleanValue> CONEKAT_FEMALE_NATURAL_SPAWNS = GameRules.register(
            "changedextrasConekatFemaleNaturalSpawns",
            GameRules.Category.SPAWNING,
            GameRules.BooleanValue.create(true)
    );

    public static final GameRules.Key<GameRules.BooleanValue> WHITE_CAT_NATURAL_SPAWNS = GameRules.register(
            "changedextrasWhiteCatNaturalSpawns",
            GameRules.Category.SPAWNING,
            GameRules.BooleanValue.create(true)
    );

    public static final GameRules.Key<GameRules.BooleanValue> ARTIST_NATURAL_SPAWNS = GameRules.register(
            "changedextrasArtistNaturalSpawns",
            GameRules.Category.SPAWNING,
            GameRules.BooleanValue.create(true)
    );

    public static final GameRules.Key<GameRules.BooleanValue> LATEX_EQUIPMENT_ENABLED = GameRules.register(
            "changedextrasLatexEquipmentEnabled",
            GameRules.Category.SPAWNING,
            GameRules.BooleanValue.create(true)
    );

    public static final GameRules.Key<GameRules.BooleanValue> SMART_LATEX_AI_ENABLED = GameRules.register(
            "changedextrasSmartLatexAiEnabled",
            GameRules.Category.MOBS,
            GameRules.BooleanValue.create(false)
    );

    public static final GameRules.Key<GameRules.IntegerValue> LATEX_TOOL_CHANCE = GameRules.register(
            "changedextrasLatexToolChance",
            GameRules.Category.SPAWNING,
            GameRules.IntegerValue.create(15)
    );

    public static final GameRules.Key<GameRules.IntegerValue> LATEX_ARMOR_CHANCE = GameRules.register(
            "changedextrasLatexArmorChance",
            GameRules.Category.SPAWNING,
            GameRules.IntegerValue.create(30)
    );

    private ChangedExtrasGameRules() {
    }

    public static void bootstrap() {
        ChangedExtras.class.getName();
    }

    public static boolean isSmartLatexAiEnabled(GameRules gameRules) {
        return gameRules.getBoolean(SMART_LATEX_AI_ENABLED);
    }

    public static float getToolChance(GameRules gameRules) {
        return clampPercent(gameRules.getInt(LATEX_TOOL_CHANCE)) / 100.0F;
    }

    public static float getArmorChance(GameRules gameRules) {
        return clampPercent(gameRules.getInt(LATEX_ARMOR_CHANCE)) / 100.0F;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}