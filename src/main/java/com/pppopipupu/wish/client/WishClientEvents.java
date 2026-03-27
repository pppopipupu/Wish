package com.pppopipupu.wish.client;

import com.pppopipupu.wish.Wish;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = Wish.MODID, value = Dist.CLIENT)
public class WishClientEvents {
    private static final List<WishLightningEffect> activeEffects = new ArrayList<>();
    private static int regenCounter = 0;

    public static void spawnLightning(Vec3 position) {
        activeEffects.add(new WishLightningEffect(position, 60));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        regenCounter++;
        Iterator<WishLightningEffect> it = activeEffects.iterator();
        while (it.hasNext()) {
            WishLightningEffect effect = it.next();
            effect.tick();
            if (effect.isExpired()) {
                it.remove();
            } else if (regenCounter % 3 == 0) {
                effect.regenerateBranches();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (activeEffects.isEmpty()) return;

        Camera camera = event.getCamera();
        for (WishLightningEffect effect : activeEffects) {
            WishLightningRenderer.render(effect, event.getPoseStack(), camera);
        }
    }
}
