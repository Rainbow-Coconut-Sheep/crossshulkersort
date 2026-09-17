package com.yeyang.crossshulkersort.mixin;

import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// NOTE (1.15 branch): HandledScreen did not exist yet.
@Mixin(ContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("x")
    int crossshulkersort$getLeftPos();

    @Accessor("y")
    int crossshulkersort$getTopPos();
}
