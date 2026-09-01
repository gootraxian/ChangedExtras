package com.katt.changedextras.entity;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.beasts.AbstractConeKatEntity;
import com.katt.changedextras.entity.beasts.AbstractWhiteCatEntity;
import com.katt.changedextras.entity.beasts.ArtistEntity;
import com.katt.changedextras.entity.beasts.KattEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    @SubscribeEvent
    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CONEKAT_MALE.get(), AbstractConeKatEntity.createAttributes().build());
        event.put(ModEntities.CONEKAT_FEMALE.get(), AbstractConeKatEntity.createAttributes().build());
        event.put(ModEntities.WHITE_CAT.get(), AbstractWhiteCatEntity.createAttributes().build());
        event.put(ModEntities.JAMMER.get(), AbstractWhiteCatEntity.createAttributes().build());
        event.put(ModEntities.ARTIST.get(), ArtistEntity.createAttributes().build());

        // Fixed the builder chain here
        event.put(ModEntities.KATT.get(), KattEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)         // 20 Hearts
                .add(Attributes.MOVEMENT_SPEED, 0.27D)    // Slightly faster than a normal player
                .build());
    }
}
