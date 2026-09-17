package com.yeyang.crossshulkersort.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int crossshulkersort$getLeftPos();

    @Accessor("topPos")
    int crossshulkersort$getTopPos();

    @Accessor("menu")
    AbstractContainerMenu crossshulkersort$getMenu();
}
