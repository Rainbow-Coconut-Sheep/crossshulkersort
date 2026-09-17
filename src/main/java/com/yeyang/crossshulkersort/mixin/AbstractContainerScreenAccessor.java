package com.yeyang.crossshulkersort.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("x")
    int crossshulkersort$getLeftPos();

    @Accessor("y")
    int crossshulkersort$getTopPos();
}
