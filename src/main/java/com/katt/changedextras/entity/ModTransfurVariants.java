package com.katt.changedextras.entity;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.beasts.ConeKatFemaleEntity;
import com.katt.changedextras.entity.beasts.ConeKatMaleEntity;
import com.katt.changedextras.entity.beasts.JammerEntity;
import com.katt.changedextras.entity.beasts.KattEntity;
import com.katt.changedextras.entity.beasts.ArtistEntity;
import com.katt.changedextras.entity.beasts.WhiteCatEntity;
import com.katt.changedextras.init.ChangedExtrasAbilities;
import net.ltxprogrammer.changed.entity.variant.GenderedPair;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModTransfurVariants {
    public static final DeferredRegister<TransfurVariant<?>> REGISTRY =
            ChangedRegistry.TRANSFUR_VARIANT.createDeferred(ChangedExtras.MODID);

    public static final RegistryObject<TransfurVariant<ConeKatMaleEntity>> CONEKAT_MALE =
            REGISTRY.register("conekat_male", () -> TransfurVariant.Builder.of(ModEntities.CONEKAT_MALE)
                    .nightVision()
                    .addAbility(ChangedAbilities.TOGGLE_NIGHT_VISION)
                    .addAbility(ChangedAbilities.SWITCH_GENDER)
                    .addAbility(ChangedExtrasAbilities.CLAWS)
                    .build());

    public static final RegistryObject<TransfurVariant<ConeKatFemaleEntity>> CONEKAT_FEMALE =
            REGISTRY.register("conekat_female",
                    () -> TransfurVariant.Builder.of(ModEntities.CONEKAT_FEMALE)
                            .nightVision()
                            .addAbility(ChangedAbilities.TOGGLE_NIGHT_VISION)
                            .addAbility(ChangedAbilities.SWITCH_GENDER)
                            .addAbility(ChangedExtrasAbilities.CLAWS)
                            .build());

    public static final GenderedPair<ConeKatMaleEntity, ConeKatFemaleEntity> CONEKATS =
            ChangedTransfurVariants.Gendered.registerPair(CONEKAT_MALE, CONEKAT_FEMALE);

    public static final RegistryObject<TransfurVariant<WhiteCatEntity>> WHITE_CAT =
            REGISTRY.register("white_cat",
                    () -> TransfurVariant.Builder.of(ModEntities.WHITE_CAT)
                            .nightVision()
                            .addAbility(ChangedAbilities.SWITCH_TRANSFUR_MODE)
                            .addAbility(ChangedAbilities.GRAB_ENTITY_ABILITY)
                            .addAbility(ChangedExtrasAbilities.CLAWS)
                            .addAbility(ChangedAbilities.TOGGLE_NIGHT_VISION)
                            .build());

    public static final RegistryObject<TransfurVariant<KattEntity>> KATT =
            REGISTRY.register("katt",
                    () -> TransfurVariant.Builder.of(ModEntities.KATT)
                            .nightVision()
                            .addAbility(ChangedAbilities.GRAB_ENTITY_ABILITY)
                            .addAbility(ChangedAbilities.TOGGLE_NIGHT_VISION)
                            .addAbility(ChangedAbilities.SWITCH_TRANSFUR_MODE)
                            .addAbility(ChangedExtrasAbilities.JACKPOT_AURA)
                            .addAbility(ChangedExtrasAbilities.CLAWS)
                            .addAbility(ChangedAbilities.HYPNOSIS)
                            .reducedFall(true)
                            .extraJumps(2)

                            .build());

    public static final RegistryObject<TransfurVariant<JammerEntity>> JAMMER =
            REGISTRY.register("jammer",
                    () -> TransfurVariant.Builder.of(ModEntities.JAMMER)
                            .nightVision()
                            .addAbility(ChangedAbilities.GRAB_ENTITY_ABILITY)
                            .addAbility(ChangedAbilities.TOGGLE_NIGHT_VISION)
                            .addAbility(ChangedExtrasAbilities.CLAWS)
                            .build());

    public static final RegistryObject<TransfurVariant<ArtistEntity>> ARTIST =
            REGISTRY.register("artist",
                    () -> TransfurVariant.Builder.of(ModEntities.ARTIST)
                            .nightVision()
                            .addAbility(ChangedExtrasAbilities.PAINT_BALL)
                            .addAbility(ChangedExtrasAbilities.SWING)
                            .addAbility(ChangedExtrasAbilities.PUNCTURE)
                            .build());

}
