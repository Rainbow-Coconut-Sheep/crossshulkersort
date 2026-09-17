package com.yeyang.crossshulkersort.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenInvoker {

    // NOTE (<=1.16 branch): no addDrawableChild yet - addButton takes ClickableWidget.
    @Invoker("addButton")
    <T extends ClickableWidget> T crossshulkersort$addRenderableWidget(T widget);
}
