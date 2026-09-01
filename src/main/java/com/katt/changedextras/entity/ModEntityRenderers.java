package com.katt.changedextras.entity;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.model.ConeKatFemaleEntityModel;
import com.katt.changedextras.entity.model.ConeKatMaleEntityModel;
import com.katt.changedextras.entity.model.ArtistEntityModel;
import com.katt.changedextras.entity.model.KattEntityModel;
import com.katt.changedextras.entity.model.WhiteCatEntityModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.ltxprogrammer.changed.init.ChangedEntityRenderers.registerHumanoid;

@Mod.EventBusSubscriber(modid = ChangedExtras.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModEntityRenderers {
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        registerHumanoid(event, ModEntities.CONEKAT_MALE.get(),
                context -> new ModEntityRenderer<>(context, new ConeKatMaleEntityModel(context.bakeLayer(ConeKatMaleEntityModel.LAYER_LOCATION))));
        registerHumanoid(event, ModEntities.CONEKAT_FEMALE.get(),
                context -> new ModEntityRenderer<>(context, new ConeKatFemaleEntityModel(context.bakeLayer(ConeKatFemaleEntityModel.LAYER_LOCATION))));
        registerHumanoid(event, ModEntities.WHITE_CAT.get(),
                context -> new WhiteCatRenderer<>(context, new WhiteCatEntityModel<>(context.bakeLayer(WhiteCatEntityModel.LAYER_LOCATION))));
        registerHumanoid(event, ModEntities.ARTIST.get(),
                ArtistRenderer::new);
        registerHumanoid(event, ModEntities.KATT.get(),
                KattRenderer::new);
        registerHumanoid(event, ModEntities.JAMMER.get(),
                JammerRenderer::new);
    }
}
