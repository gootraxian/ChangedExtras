package com.katt.changedextras.init;

import com.katt.changedextras.ChangedExtras;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ChangedExtrasSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ChangedExtras.MODID);

    /**
     * The looping song that plays when Jackpot is active.
     * Provide your audio file at:
     *   src/main/resources/assets/changedextras/sounds/jackpot_song.ogg
     */
    public static final RegistryObject<SoundEvent> JACKPOT_SONG =
            REGISTRY.register("jackpot_song", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "jackpot_song")));

    /**
     * Short "disc stop" sound when Jackpot ends.
     * Provide your audio file at:
     *   src/main/resources/assets/changedextras/sounds/jackpot_stop.ogg
     */
    public static final RegistryObject<SoundEvent> JACKPOT_STOP =
            REGISTRY.register("jackpot_stop", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "jackpot_stop")));

    /**
     * The looping song that plays during the Artist boss fight.
     * Provide your audio file at:
     *   src/main/resources/assets/changedextras/sounds/artist_theme.ogg
     */
    public static final RegistryObject<SoundEvent> ARTIST_THEME =
            REGISTRY.register("artist_theme", () ->
                    SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "artist_theme")));

    private ChangedExtrasSounds() {}
}
