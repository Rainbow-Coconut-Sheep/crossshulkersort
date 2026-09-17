package com.yeyang.crossshulkersort.mixin;

import net.minecraft.client.gui.screen.ingame.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// NOTE (1.15.1 branch): AbstractContainerScreen, not ContainerScreen (renamed in 1.15.2).
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("x")
    int crossshulkersort$getLeftPos();

    @Accessor("y")
    int crossshulkersort$getTopPos();
}
