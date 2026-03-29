package com.pppopipupu.wish;

import com.pppopipupu.wish.datagen.WishRecipeProvider;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.arguments.StringArgumentType;

@Mod(Wish.MODID)
public class Wish {
    public static final String MODID = "wish";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<WishScroll> WISH_SCROLL = ITEMS.registerItem("wish_scroll", WishScroll::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wish"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> WISH_SCROLL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(WISH_SCROLL.get());
            })
            .build());

    public Wish(IEventBus modEventBus, ModContainer modContainer) {

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::gatherData);

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public void registerPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar(MODID).playToServer(com.pppopipupu.wish.WishPayload.TYPE, com.pppopipupu.wish.WishPayload.STREAM_CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
                if (player.getMainHandItem().getItem() instanceof WishScroll) {
                    if(!player.isCreative())
                        player.getMainHandItem().shrink(1);
                    WishCommand.executeWish(player.createCommandSourceStack(), payload.wishText());
                } else if (player.getOffhandItem().getItem() instanceof WishScroll) {
                    if(!player.isCreative())
                        player.getOffhandItem().shrink(1);
                    WishCommand.executeWish(player.createCommandSourceStack(), payload.wishText());
                }
            });
        });
    }

    public void gatherData(net.neoforged.neoforge.data.event.GatherDataEvent event) {
        event.getGenerator().addProvider(
                event.includeServer(),
                (DataProvider.Factory<WishRecipeProvider>) (PackOutput packOutput) -> new WishRecipeProvider(packOutput, event.getLookupProvider())
        );
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("wish")
                .requires(source -> source.hasPermission(3))
                .then(Commands.argument("wish_text", StringArgumentType.greedyString())
                .executes(new WishCommand())));
    }

}
