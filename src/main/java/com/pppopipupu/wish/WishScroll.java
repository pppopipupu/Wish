package com.pppopipupu.wish;

import com.pppopipupu.wish.client.WishClientEvents;
import com.pppopipupu.wish.client.WishScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class WishScroll extends Item {
    public WishScroll(Item.Properties properties) {
        super(properties);
    }
    
    public WishScroll() {
        super(new Item.Properties());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (level.isClientSide) {
            WishClientEvents.spawnLightning(new Vec3(player.getX(), player.getY(), player.getZ()));
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int count) {
        if (livingEntity instanceof Player player) {
            int ticksUsing = this.getUseDuration(stack, livingEntity) - count;
            if (ticksUsing >= 60) {
                player.stopUsingItem();
                if (level.isClientSide) {
                    openWishScreen();
                }
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    private void openWishScreen() {
        Minecraft.getInstance().setScreen(new WishScreen());
    }
}
