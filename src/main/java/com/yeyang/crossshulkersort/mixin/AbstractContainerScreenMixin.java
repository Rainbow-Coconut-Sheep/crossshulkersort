package com.yeyang.crossshulkersort.mixin;

import com.yeyang.crossshulkersort.CrossShulkerSortClient;
import com.yeyang.crossshulkersort.gui.QSortButton;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class AbstractContainerScreenMixin<T extends ScreenHandler> {

    @Inject(method = "init", at = @At("TAIL"))
    private void crossshulkersort$addSortButton(CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            AbstractContainerScreenAccessor self = (AbstractContainerScreenAccessor) this;
            int x = CrossShulkerSortClient.config().buttonX;
            int y = CrossShulkerSortClient.config().buttonY;
            QSortButton button = new QSortButton(
                    self.crossshulkersort$getLeftPos() + x,
                    self.crossshulkersort$getTopPos() + y,
                    self.crossshulkersort$getLeftPos(),
                    self.crossshulkersort$getTopPos());
            ((ScreenInvoker) (Object) this).crossshulkersort$addRenderableWidget(button);
        }
    }
}
