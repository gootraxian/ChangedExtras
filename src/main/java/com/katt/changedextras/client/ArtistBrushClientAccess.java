package com.katt.changedextras.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class ArtistBrushClientAccess {
    private ArtistBrushClientAccess() {
    }

    public static void openEditor(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ItemStack stack = minecraft.player.getItemInHand(hand);
        minecraft.setScreen(new ArtistBrushScreen(hand, stack));
    }
}
