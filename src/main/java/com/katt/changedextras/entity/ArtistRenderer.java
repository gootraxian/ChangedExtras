package com.katt.changedextras.entity;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.beasts.ArtistEntity;
import com.katt.changedextras.entity.model.ArtistEntityModel;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleWolfModel;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class ArtistRenderer extends AdvancedHumanoidRenderer<ArtistEntity, ArtistEntityModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "textures/entity/artist/artist.png");

    public ArtistRenderer(EntityRendererProvider.Context context) {
        super(context, new ArtistEntityModel(context.bakeLayer(ArtistEntityModel.LAYER_LOCATION)), ArmorLatexMaleWolfModel.MODEL_SET, 0.5f);
        this.addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        this.addLayer(CustomEyesLayer.<ArtistEntityModel, ArtistEntity>builder(this, context.getModelSet())
                .withSclera(CustomEyesLayer.fixedColorGlowing(Color3.fromInt(0xFFF4FB)))
                .withLeftIris(CustomEyesLayer.fixedColorGlowing(Color3.fromInt(0xFF5AB3)))
                .withRightIris(CustomEyesLayer.fixedColorGlowing(Color3.fromInt(0xFF5AB3)))
                .build());
        this.addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(ArtistEntity entity) {
        return TEXTURE;
    }
}
