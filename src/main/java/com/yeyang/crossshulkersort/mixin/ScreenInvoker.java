package com.yeyang.crossshulkersort.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenInvoker {

    // NOTE (1.15 branch): no addDrawableChild/ClickableWidget yet - addButton
    // takes AbstractButtonWidget.
    @Invoker("addButton")
    <T extends AbstractButtonWidget> T crossshulkersort$addRenderableWidget(T widget);
}
