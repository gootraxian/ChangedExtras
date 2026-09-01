package com.katt.changedextras.item;

import com.katt.changedextras.ChangedExtras;
import com.katt.changedextras.entity.ModTransfurVariants;
import com.katt.changedextras.entity.beasts.JammerEntity;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.item.ClothingItem;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class JammerHeadphonesItem extends ClothingItem {
    private static final String TIMER_TAG = "changedextras.jammer_headphones_ticks";
    private static final int TRANSFUR_TICKS = 200;

    public JammerHeadphonesItem(Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation getTexture(ItemStack stack, net.minecraft.world.entity.Entity entity) {
        return ResourceLocation.fromNamespaceAndPath(ChangedExtras.MODID, "textures/models/jammer_headphones.png");
    }

    @Override
    public boolean allowedInSlot(ItemStack stack, LivingEntity wearer, AccessorySlotType slotType) {
        return slotType.getEquivalentSlot() == net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    @Override
    public void accessoryRemoved(AccessorySlotContext<?> slotContext) {
        slotContext.wearer().getPersistentData().remove(TIMER_TAG);
    }

    @Override
    public void accessoryTick(AccessorySlotContext<?> slotContext) {
        if (!(slotContext.wearer() instanceof ServerPlayer player)) {
            return;
        }

        if (ProcessTransfur.isPlayerTransfurred(player)) {
            player.getPersistentData().remove(TIMER_TAG);
            return;
        }

        int ticks = player.getPersistentData().getInt(TIMER_TAG) + 1;
        if (ticks < TRANSFUR_TICKS) {
            player.getPersistentData().putInt(TIMER_TAG, ticks);
            return;
        }

        var instance = ProcessTransfur.setPlayerTransfurVariant(player, ModTransfurVariants.JAMMER.get());
        if (instance != null) {
            ChangedSounds.broadcastSound(player, ChangedSounds.TRANSFUR_BY_LATEX, 1.0F, 1.0F);
            if (instance.getChangedEntity() instanceof JammerEntity jammer) {
                jammer.setVip(player.getRandom().nextInt(30) == 0);
            }
        }
        player.getPersistentData().remove(TIMER_TAG);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        BlockPos pos = placeContext.getClickedPos();
        BlockState state = ChangedExtras.JAMMER_HEADPHONES_BLOCK.get().defaultBlockState();

        if (!state.canSurvive(level, pos) || !level.getBlockState(pos).canBeReplaced(placeContext)) {
            return super.useOn(context);
        }

        if (!level.isClientSide) {
            level.setBlock(pos, state, 11);
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
