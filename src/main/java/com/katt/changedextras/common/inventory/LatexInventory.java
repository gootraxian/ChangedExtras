package com.katt.changedextras.common.inventory;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class LatexInventory extends ItemStackHandler {

    public static final int HOTBAR_SLOTS = 9;
    public static final int MAIN_SLOTS = 9;
    public static final int TOTAL_SLOTS = HOTBAR_SLOTS + MAIN_SLOTS; // 18 slots (9 hotbar + 9 storage)

    public LatexInventory() {
        super(TOTAL_SLOTS);
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
    }

    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < getSlots(); i++) {
            stack = insertItem(i, stack, false);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    public int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int countTag(TagKey<Item> tag) {
        int count = 0;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.is(tag)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean hasItem(Item item) {
        return countItem(item) > 0;
    }

    public boolean hasTag(TagKey<Item> tag) {
        return countTag(tag) > 0;
    }

    public boolean consumeItem(Item item, int amount) {
        if (countItem(item) < amount) return false;
        int needed = amount;
        for (int i = 0; i < getSlots() && needed > 0; i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(stack.getCount(), needed);
                stack.shrink(take);
                needed -= take;
                if (stack.isEmpty()) {
                    setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
        return needed == 0;
    }

    public boolean consumeTag(TagKey<Item> tag, int amount) {
        if (countTag(tag) < amount) return false;
        int needed = amount;
        for (int i = 0; i < getSlots() && needed > 0; i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.is(tag)) {
                int take = Math.min(stack.getCount(), needed);
                stack.shrink(take);
                needed -= take;
                if (stack.isEmpty()) {
                    setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
        return needed == 0;
    }

    public boolean tryCraftPlanks() {
        if (hasTag(ItemTags.LOGS)) {
            if (consumeTag(ItemTags.LOGS, 1)) {
                addItem(new ItemStack(Items.OAK_PLANKS, 4));
                return true;
            }
        }
        return false;
    }

    public boolean tryCraftSticks() {
        if (countTag(ItemTags.PLANKS) >= 2) {
            if (consumeTag(ItemTags.PLANKS, 2)) {
                addItem(new ItemStack(Items.STICK, 4));
                return true;
            }
        } else if (hasTag(ItemTags.LOGS)) {
            tryCraftPlanks();
            return tryCraftSticks();
        }
        return false;
    }

    public boolean tryCraftWoodenPickaxe() {
        ensureSticks(2);
        ensurePlanks(3);
        if (countTag(ItemTags.PLANKS) >= 3 && countItem(Items.STICK) >= 2) {
            if (consumeTag(ItemTags.PLANKS, 3) && consumeItem(Items.STICK, 2)) {
                addItem(new ItemStack(Items.WOODEN_PICKAXE));
                return true;
            }
        }
        return false;
    }

    public boolean tryCraftStonePickaxe() {
        ensureSticks(2);
        int cobbleCount = countItem(Items.COBBLESTONE) + countItem(Items.COBBLED_DEEPSLATE);
        if (cobbleCount >= 3 && countItem(Items.STICK) >= 2) {
            int takeCobble = 3;
            if (countItem(Items.COBBLESTONE) >= 3) {
                consumeItem(Items.COBBLESTONE, 3);
            } else {
                int fromCobble = Math.min(countItem(Items.COBBLESTONE), takeCobble);
                if (fromCobble > 0) consumeItem(Items.COBBLESTONE, fromCobble);
                consumeItem(Items.COBBLED_DEEPSLATE, takeCobble - fromCobble);
            }
            consumeItem(Items.STICK, 2);
            addItem(new ItemStack(Items.STONE_PICKAXE));
            return true;
        }
        return false;
    }

    public boolean tryCraftWoodenSword() {
        ensureSticks(1);
        ensurePlanks(2);
        if (countTag(ItemTags.PLANKS) >= 2 && countItem(Items.STICK) >= 1) {
            if (consumeTag(ItemTags.PLANKS, 2) && consumeItem(Items.STICK, 1)) {
                addItem(new ItemStack(Items.WOODEN_SWORD));
                return true;
            }
        }
        return false;
    }

    public boolean tryCraftStoneSword() {
        ensureSticks(1);
        int cobbleCount = countItem(Items.COBBLESTONE) + countItem(Items.COBBLED_DEEPSLATE);
        if (cobbleCount >= 2 && countItem(Items.STICK) >= 1) {
            if (countItem(Items.COBBLESTONE) >= 2) {
                consumeItem(Items.COBBLESTONE, 2);
            } else {
                int fromCobble = Math.min(countItem(Items.COBBLESTONE), 2);
                if (fromCobble > 0) consumeItem(Items.COBBLESTONE, fromCobble);
                consumeItem(Items.COBBLED_DEEPSLATE, 2 - fromCobble);
            }
            consumeItem(Items.STICK, 1);
            addItem(new ItemStack(Items.STONE_SWORD));
            return true;
        }
        return false;
    }

    public boolean tryCraftWoodenAxe() {
        ensureSticks(2);
        ensurePlanks(3);
        if (countTag(ItemTags.PLANKS) >= 3 && countItem(Items.STICK) >= 2) {
            if (consumeTag(ItemTags.PLANKS, 3) && consumeItem(Items.STICK, 2)) {
                addItem(new ItemStack(Items.WOODEN_AXE));
                return true;
            }
        }
        return false;
    }

    private void ensurePlanks(int count) {
        while (countTag(ItemTags.PLANKS) < count && hasTag(ItemTags.LOGS)) {
            tryCraftPlanks();
        }
    }

    private void ensureSticks(int count) {
        while (countItem(Items.STICK) < count && (countTag(ItemTags.PLANKS) >= 2 || hasTag(ItemTags.LOGS))) {
            tryCraftSticks();
        }
    }

    public void tryAutoCraftNeededTools() {
        boolean hasPick = hasToolOfType(PickaxeItem.class);
        boolean hasSword = hasToolOfType(SwordItem.class);
        boolean hasAxe = hasToolOfType(AxeItem.class);

        if (!hasPick) {
            if (!tryCraftStonePickaxe()) {
                tryCraftWoodenPickaxe();
            }
        }
        if (!hasSword) {
            if (!tryCraftStoneSword()) {
                tryCraftWoodenSword();
            }
        }
        if (!hasAxe) {
            tryCraftWoodenAxe();
        }
        if (!hasToolOfType(ShieldItem.class)) {
            tryCraftShield();
        }
        if (!hasToolOfType(BowItem.class) && !hasToolOfType(CrossbowItem.class)) {
            tryCraftBow();
        }
        if (hasToolOfType(BowItem.class) && countItem(Items.ARROW) < 16) {
            tryCraftArrows();
        }
    }

    public boolean tryCraftShield() {
        ensurePlanks(6);
        if (countTag(ItemTags.PLANKS) >= 6 && countItem(Items.IRON_INGOT) >= 1) {
            if (consumeTag(ItemTags.PLANKS, 6) && consumeItem(Items.IRON_INGOT, 1)) {
                addItem(new ItemStack(Items.SHIELD));
                return true;
            }
        }
        return false;
    }

    public boolean tryCraftBow() {
        ensureSticks(3);
        if (countItem(Items.STICK) >= 3 && countItem(Items.STRING) >= 3) {
            if (consumeItem(Items.STICK, 3) && consumeItem(Items.STRING, 3)) {
                addItem(new ItemStack(Items.BOW));
                return true;
            }
        }
        return false;
    }

    public boolean tryCraftArrows() {
        ensureSticks(1);
        if (countItem(Items.STICK) >= 1 && countItem(Items.FEATHER) >= 1 && countItem(Items.FLINT) >= 1) {
            if (consumeItem(Items.STICK, 1) && consumeItem(Items.FEATHER, 1) && consumeItem(Items.FLINT, 1)) {
                addItem(new ItemStack(Items.ARROW, 4));
                return true;
            }
        }
        return false;
    }

    public ItemStack findShield() {
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ShieldItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack findRangedWeapon() {
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack findArrow() {
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ArrowItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean hasToolOfType(Class<?> toolClass) {
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && toolClass.isInstance(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    public ItemStack findBestFood() {
        ItemStack bestFood = ItemStack.EMPTY;
        int maxNutrition = 0;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.isEdible() && stack.getItem().getFoodProperties() != null) {
                int nutrition = stack.getItem().getFoodProperties().getNutrition();
                if (nutrition > maxNutrition) {
                    maxNutrition = nutrition;
                    bestFood = stack;
                }
            }
        }
        return bestFood;
    }

    public ItemStack findBestWeapon() {
        ItemStack best = ItemStack.EMPTY;
        float bestDamage = 0.0F;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty()) {
                float dmg = getWeaponDamage(stack);
                if (dmg > bestDamage) {
                    bestDamage = dmg;
                    best = stack;
                }
            }
        }
        return best;
    }

    private float getWeaponDamage(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem sword) {
            return 4.0F + sword.getDamage();
        }
        if (stack.getItem() instanceof AxeItem axe) {
            return 3.0F + axe.getAttackDamage();
        }
        if (stack.getItem() instanceof TieredItem) {
            return 2.0F;
        }
        return 0.0F;
    }

    public ItemStack findBestToolFor(BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1.0F;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    best = stack;
                }
            }
        }
        return best;
    }

    public ItemStack findBuildingBlock() {
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                BlockState defaultState = blockItem.getBlock().defaultBlockState();
                if (defaultState.isSolidRender(null, net.minecraft.core.BlockPos.ZERO) || !defaultState.isAir()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public void dropAll(Mob mob) {
        if (mob.level().isClientSide) return;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty()) {
                mob.spawnAtLocation(stack.copy());
                setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
