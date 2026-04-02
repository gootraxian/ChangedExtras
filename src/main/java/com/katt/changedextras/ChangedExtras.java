package com.katt.changedextras;

import com.katt.changedextras.client.renderer.accessory.DyeableClothingRenderer;
import com.katt.changedextras.client.ClientEventHandler;
import com.katt.changedextras.client.particle.JackpotSmokeParticleProvider;
import com.katt.changedextras.common.ChangedExtrasGameRules;
import com.katt.changedextras.common.ServerUsageWebhookNotifier;
import com.katt.changedextras.entity.ModEntities;
import com.katt.changedextras.entity.ModTransfurVariants;
import com.katt.changedextras.init.ChangedExtrasAbilities;
import com.katt.changedextras.init.ChangedExtrasParticles;
import com.katt.changedextras.init.ChangedExtrasSounds;
import com.katt.changedextras.item.ArtistBrushItem;
import com.katt.changedextras.item.ArtistSketchItem;
import com.katt.changedextras.item.LongSleeveShirt;
import com.katt.changedextras.network.ChangedExtrasNetwork;
import com.katt.changedextras.network.DiscoveryNetwork;
import com.katt.changedextras.network.JackpotStatePacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.ltxprogrammer.changed.item.LatexSyringe;
import net.ltxprogrammer.changed.item.SimpleSpawnEggItem;
import net.ltxprogrammer.changed.item.Syringe;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import net.ltxprogrammer.changed.client.renderer.accessory.SimpleClothingRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorModel;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.UUID;

@Mod(ChangedExtras.MODID)
public class ChangedExtras {

    private static final String ICECREAM_STREAK_TAG = "changedextras.icecream_streak";
    private static final String SPECIAL_VARIANT_GIVEN_TAG = "changedextras.special_variant_given";
    private static final UUID SPECIAL_PLAYER_UUID = UUID.fromString("70080b3e-8cf3-46f3-922e-7b3a32269935");

    public static final String MODID = "changedextras";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> ICECREAM_BLOCK =
            BLOCKS.register("icecream_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    public static final RegistryObject<Item> ICECREAM_BLOCK_ITEM =
            ITEMS.register("icecream_block", () -> new BlockItem(ICECREAM_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> ICECREAM_ITEM =
            ITEMS.register("icecream", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .alwaysEat().nutrition(5).saturationMod(0.6f).build())));

    public static final RegistryObject<LatexSyringe> CONEKAT_MALE_SYRINGE =
            ITEMS.register("conekat_male_syringe", () -> new LatexSyringe(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<LatexSyringe> CONEKAT_FEMALE_SYRINGE =
            ITEMS.register("conekat_female_syringe", () -> new LatexSyringe(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<LatexSyringe> WHITE_CAT_SYRINGE =
            ITEMS.register("white_cat_syringe", () -> new LatexSyringe(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> THE_PALETTE =
            ITEMS.register("the_palette", () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> ARTIST_BRUSH =
            ITEMS.register("artist_brush", () -> new ArtistBrushItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // The Player-Locked Katt Syringe
    public static final RegistryObject<LatexSyringe> KATT_SYRINGE =
            ITEMS.register("katt_syringe", () -> new LatexSyringe(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<SimpleSpawnEggItem> CONEKAT_MALE_SPAWN_EGG =
            ITEMS.register("conekat_male_spawn_egg",
                    () -> new SimpleSpawnEggItem(ModEntities.CONEKAT_MALE, 0xE3D2BF, 0x5A3A2E, new Item.Properties()));
    public static final RegistryObject<SimpleSpawnEggItem> CONEKAT_FEMALE_SPAWN_EGG =
            ITEMS.register("conekat_female_spawn_egg",
                    () -> new SimpleSpawnEggItem(ModEntities.CONEKAT_FEMALE, 0xE3D2BF, 0xC86A7B, new Item.Properties()));
    public static final RegistryObject<SimpleSpawnEggItem> WHITE_CAT_SPAWN_EGG =
            ITEMS.register("white_cat_spawn_egg",
                    () -> new SimpleSpawnEggItem(ModEntities.WHITE_CAT, 0xF6F3F3, 0xF1CF6E, new Item.Properties()));
    public static final RegistryObject<Item> ARTIST_SPAWN_EGG =
            ITEMS.register("sketch",
                    () -> new ArtistSketchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> LONG_SLEEVE_SHIRT = ITEMS.register("long_sleeve_shirt",
            () -> new LongSleeveShirt());

    public static final RegistryObject<CreativeModeTab> SYRINGES_TAB =
            CREATIVE_MODE_TABS.register("changedextras_syringes", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.changedextras.changedextras_syringes"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> createVariantSyringeStack(CONEKAT_MALE_SYRINGE.get(), "conekat_male"))
                    .displayItems((parameters, output) -> {
                        output.accept(createVariantSyringeStack(CONEKAT_MALE_SYRINGE.get(), "conekat_male"));
                        output.accept(createVariantSyringeStack(CONEKAT_FEMALE_SYRINGE.get(), "conekat_female"));
                        output.accept(createVariantSyringeStack(WHITE_CAT_SYRINGE.get(), "white_cat"));
                        output.accept(createVariantSyringeStack(KATT_SYRINGE.get(), "katt"));
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> MOBS_TAB =
            CREATIVE_MODE_TABS.register("changedextras_mobs", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.changedextras.changedextras_mobs"))
                    .withTabsBefore(SYRINGES_TAB.getKey())
                    .icon(() -> CONEKAT_MALE_SPAWN_EGG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CONEKAT_MALE_SPAWN_EGG.get());
                        output.accept(CONEKAT_FEMALE_SPAWN_EGG.get());
                        output.accept(WHITE_CAT_SPAWN_EGG.get());
                        output.accept(ARTIST_SPAWN_EGG.get());
                    })
                    .build());

    public ChangedExtras(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        DiscoveryNetwork.bootstrap();

        modEventBus.addListener(this::commonSetup);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        ModEntities.REGISTRY.register(modEventBus);
        ModTransfurVariants.REGISTRY.register(modEventBus);
        ChangedExtrasAbilities.REGISTRY.register(modEventBus);
        ChangedExtrasSounds.REGISTRY.register(modEventBus);
        ChangedExtrasParticles.REGISTRY.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ChangedExtrasGameRules.bootstrap();
        ChangedExtrasNetwork.register();
        LOGGER.info("[Changed Extras] Loaded in!");
    }

    private static ItemStack createVariantSyringeStack(Item syringeItem, String variantId) {
        return Syringe.setPureVariant(
                new ItemStack(syringeItem),
                ResourceLocation.fromNamespaceAndPath(MODID, variantId));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ICECREAM_BLOCK_ITEM.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(THE_PALETTE.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ARTIST_BRUSH.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Changed Extras] Server starting");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerUsageWebhookNotifier.notifyServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!player.getUUID().equals(SPECIAL_PLAYER_UUID)) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean("changedextras.received_starter_kit")) {

            ItemStack starterSyringe = createVariantSyringeStack(KATT_SYRINGE.get(), "katt");

            if (!player.getInventory().add(starterSyringe)) {
                player.drop(starterSyringe, false);
            }

            data.putBoolean("changedextras.received_starter_kit", true);
        }
    }

    @SubscribeEvent
    public void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        LivingEntity user = event.getEntity();

        // Lock the Katt Syringe to the special UUID
        if (stack.is(KATT_SYRINGE.get())) {
            if (!user.getUUID().equals(SPECIAL_PLAYER_UUID)) {
                if (user instanceof net.minecraft.world.entity.player.Player player) {
                    player.displayClientMessage(Component.translatable("message.changedextras.locked_syringe")
                            .withStyle(ChatFormatting.RED), true);
                }
                event.setCanceled(true);
                return;
            }
            Syringe.setPureVariant(stack, ResourceLocation.fromNamespaceAndPath(MODID, "katt"));
        }

        // Standard variant forcing for other syringes
        if (stack.is(WHITE_CAT_SYRINGE.get())) {
            Syringe.setPureVariant(stack, ResourceLocation.fromNamespaceAndPath(MODID, "white_cat"));
        } else if (stack.is(CONEKAT_MALE_SYRINGE.get())) {
            Syringe.setPureVariant(stack, ResourceLocation.fromNamespaceAndPath(MODID, "conekat_male"));
        } else if (stack.is(CONEKAT_FEMALE_SYRINGE.get())) {
            Syringe.setPureVariant(stack, ResourceLocation.fromNamespaceAndPath(MODID, "conekat_female"));
        }
    }

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }

        ItemStack stack = event.getItem();
        if (!stack.isEdible()) {
            return;
        }

        if (stack.is(ICECREAM_ITEM.get())) {
            int streak = player.getPersistentData().getInt(ICECREAM_STREAK_TAG) + 1;
            if (streak >= 3) {
                player.getPersistentData().putInt(ICECREAM_STREAK_TAG, 0);
                ProcessTransfur.setPlayerTransfurVariant(
                        player,
                        ModTransfurVariants.CONEKATS.getRandomVariant(player.getRandom())
                );
            } else {
                player.getPersistentData().putInt(ICECREAM_STREAK_TAG, streak);
            }
            return;
        }
        player.getPersistentData().putInt(ICECREAM_STREAK_TAG, 0);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        assignSpecialVariant(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        assignSpecialVariant(event.getEntity());
    }

    private static void assignSpecialVariant(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!SPECIAL_PLAYER_UUID.equals(serverPlayer.getUUID())) return;

        CompoundTag persistentData = serverPlayer.getPersistentData();
        if (!persistentData.getBoolean(SPECIAL_VARIANT_GIVEN_TAG)) {
            ProcessTransfur.setPlayerTransfurVariant(serverPlayer, ModTransfurVariants.KATT.get());
            persistentData.putBoolean(SPECIAL_VARIANT_GIVEN_TAG, true);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            AccessoryLayer.registerRenderer(
                    ChangedExtras.LONG_SLEEVE_SHIRT.get(),
                    SimpleClothingRenderer.of(ArmorModel.CLOTHING_INNER, EquipmentSlot.CHEST)
            );

            MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
        }

        @SubscribeEvent
        public static void onRegisterColorHandlers(RegisterColorHandlersEvent.Item event) {
            AccessoryLayer.registerRenderer(
                    ChangedExtras.LONG_SLEEVE_SHIRT.get(),
                    DyeableClothingRenderer.of(ArmorModel.CLOTHING_INNER, EquipmentSlot.CHEST)
            );
        }

        @SubscribeEvent
        public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
            JackpotSmokeParticleProvider.register(event);
        }
    }
}
