package com.pppopipupu.wish.client;

import com.pppopipupu.wish.WishPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class WishScreen extends Screen {
    private EditBox editBox;

    public WishScreen() {
        super(Component.translatable("wish.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.editBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 20, 200, 20, Component.translatable("wish.screen.input"));
        this.addRenderableWidget(this.editBox);

        this.addRenderableWidget(Button.builder(Component.translatable("wish.screen.confirm"), b -> {
            String text = this.editBox.getValue();
            if (!text.isEmpty()) {
                PacketDistributor.sendToServer(new WishPayload(text));
                this.minecraft.setScreen(null);
            }
        }).bounds(this.width / 2 - 50, this.height / 2 + 10, 100, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
