package com.katt.changedextras.entity;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.beasts.ArtistEntity;
import com.katt.changedextras.entity.beasts.ConeKatFemaleEntity;
import com.katt.changedextras.entity.beasts.ConeKatMaleEntity;
import com.katt.changedextras.entity.beasts.JammerEntity;
import com.katt.changedextras.entity.beasts.KattEntity;
import com.katt.changedextras.entity.beasts.WhiteCatEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ChangedExtras.MODID);

    public static final RegistryObject<EntityType<ConeKatMaleEntity>> CONEKAT_MALE = REGISTRY.register("conekat_male",
            () -> EntityType.Builder.of(ConeKatMaleEntity::new, MobCategory.MONSTER)
                    .clientTrackingRange(10)
                    .sized(0.7F, 1.93F)
                    .build("conekat_male"));

    public static final RegistryObject<EntityType<ConeKatFemaleEntity>> CONEKAT_FEMALE = REGISTRY.register("conekat_female",
            () -> EntityType.Builder.of(ConeKatFemaleEntity::new, MobCategory.MONSTER)
                    .clientTrackingRange(10)
                    .sized(0.7F, 1.93F)
                    .build("conekat_female"));

    public static final RegistryObject<EntityType<WhiteCatEntity>> WHITE_CAT = REGISTRY.register("white_cat",
            () -> EntityType.Builder.of(WhiteCatEntity::new, MobCategory.MONSTER)
                    .clientTrackingRange(10)
                    .sized(0.7F, 1.93F)
                    .build("white_cat"));

    public static final RegistryObject<EntityType<KattEntity>> KATT = REGISTRY.register("katt",
            () -> EntityType.Builder.of(KattEntity::new, MobCategory.MONSTER)
                    .clientTrackingRange(10)
                    .sized(0.7F, 1.93F)
                    .build("katt"));

    public static final RegistryObject<EntityType<JammerEntity>> JAMMER = REGISTRY.register("jammer",
            () -> EntityType.Builder.of(JammerEntity::new, MobCategory.MONSTER)
                    .clientTrackingRange(10)
                    .sized(0.7F, 1.93F)
                    .build("jammer"));

    public static final RegistryObject<EntityType<ArtistEntity>> ARTIST = REGISTRY.register("artist",
            () -> EntityType.Builder.of(ArtistEntity::new, MobCategory.MONSTER)
                    .clientTrackingRange(12)
                    .sized(0.7F, 1.93F)
                    .build("artist"));
}
